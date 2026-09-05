package handlers

import (
	"crypto/rand"
	"errors"
	"fmt"
	"math/big"
	"net/http"
	"strings"
	"time"

	"cabin/internal/auth"
	"cabin/internal/store"
)

// --- phone verification ---
//
// A verified badge is only worth something if the contact number behind it was
// actually proven. Before this, `phone_verified` was never set by anything, so
// an account could be verified with a made-up number.

type sendCodeInput struct {
	// Phone optionally updates the number before sending, so a user can correct
	// a typo without a separate profile save.
	Phone string `json:"phone"`
}

type verifyCodeInput struct {
	Code string `json:"code"`
}

// newCode returns a cryptographically random 6-digit code.
func newCode() (string, error) {
	n, err := rand.Int(rand.Reader, big.NewInt(1_000_000))
	if err != nil {
		return "", err
	}
	return fmt.Sprintf("%06d", n.Int64()), nil
}

func (s *Server) handleSendPhoneCode(w http.ResponseWriter, r *http.Request) {
	user, err := s.currentUser(r)
	if err != nil {
		writeError(w, http.StatusUnauthorized, "account not found")
		return
	}

	// Sending for real costs money and needs an account, so production refuses
	// to run the flow against the simulated sender rather than quietly pretending.
	if s.cfg.Env == "production" && s.sms.Simulated() {
		writeError(w, http.StatusNotImplemented,
			"phone verification needs an SMS gateway (set SMS_URL)")
		return
	}

	var in sendCodeInput
	if err := decodeJSON(w, r, &in); err != nil {
		writeError(w, http.StatusBadRequest, "invalid JSON body")
		return
	}
	phone := strings.TrimSpace(in.Phone)
	if phone == "" {
		phone = user.Phone
	}
	if !plausiblePhone(phone) {
		writeError(w, http.StatusBadRequest, "enter a valid mobile number")
		return
	}

	priorSends, err := s.phones.CanSend(user.ID)
	switch {
	case errors.Is(err, store.ErrResendTooSoon):
		writeError(w, http.StatusTooManyRequests, "wait a minute before asking for another code")
		return
	case errors.Is(err, store.ErrSendLimit):
		writeError(w, http.StatusTooManyRequests, "too many codes requested today, try again tomorrow")
		return
	case err != nil:
		writeError(w, http.StatusInternalServerError, "could not send a code")
		return
	}

	code, err := newCode()
	if err != nil {
		writeError(w, http.StatusInternalServerError, "could not generate a code")
		return
	}
	// The code is hashed at rest, so a database read never yields a usable code.
	hash, err := auth.HashPassword(code)
	if err != nil {
		writeError(w, http.StatusInternalServerError, "could not store the code")
		return
	}

	// Persist the number if it changed, and drop any stale verified flag.
	if phone != user.Phone {
		user.Phone = phone
		user.PhoneVerified = false
		if err := s.users.UpdateProfile(user); err != nil {
			writeError(w, http.StatusInternalServerError, "could not save your number")
			return
		}
	}
	if err := s.phones.Upsert(user.ID, phone, hash, priorSends); err != nil {
		writeError(w, http.StatusInternalServerError, "could not store the code")
		return
	}

	message := fmt.Sprintf("%s is your Cabin verification code. It expires in 10 minutes. Never share it.", code)
	if err := s.sms.Send(r.Context(), phone, message); err != nil {
		writeError(w, http.StatusBadGateway, "could not send the code, please try again")
		return
	}

	body := map[string]any{
		"sent_to":    maskPhone(phone),
		"expires_in": int(store.CodeTTL.Seconds()),
	}
	// Only when messages are not really delivered, and never in production:
	// otherwise there is no way to complete the flow locally.
	if s.sms.Simulated() && s.cfg.Env != "production" {
		body["dev_code"] = code
		body["note"] = "No SMS gateway configured — this code is echoed for local development only."
	}
	writeJSON(w, http.StatusOK, body)
}

func (s *Server) handleVerifyPhoneCode(w http.ResponseWriter, r *http.Request) {
	user, err := s.currentUser(r)
	if err != nil {
		writeError(w, http.StatusUnauthorized, "account not found")
		return
	}
	var in verifyCodeInput
	if err := decodeJSON(w, r, &in); err != nil {
		writeError(w, http.StatusBadRequest, "invalid JSON body")
		return
	}
	code := strings.TrimSpace(in.Code)
	if code == "" {
		writeError(w, http.StatusBadRequest, "code is required")
		return
	}

	challenge, err := s.phones.Get(user.ID)
	if err != nil {
		// Same message whether nothing was requested or it already lapsed.
		writeError(w, http.StatusBadRequest, "that code has expired — ask for a new one")
		return
	}
	if time.Now().After(challenge.ExpiresAt) {
		_ = s.phones.Clear(user.ID)
		writeError(w, http.StatusBadRequest, "that code has expired — ask for a new one")
		return
	}
	if challenge.Attempts >= store.MaxAttempts {
		_ = s.phones.Clear(user.ID)
		writeError(w, http.StatusTooManyRequests, "too many wrong codes — ask for a new one")
		return
	}

	if !auth.CheckPassword(challenge.CodeHash, code) {
		_ = s.phones.RecordAttempt(user.ID)
		writeError(w, http.StatusBadRequest, "that code isn't right")
		return
	}

	// The number the code was sent to is the one that becomes verified, even if
	// the profile changed in between.
	if challenge.Phone != user.Phone {
		user.Phone = challenge.Phone
		if err := s.users.UpdateProfile(user); err != nil {
			writeError(w, http.StatusInternalServerError, "could not save your number")
			return
		}
	}
	if err := s.users.MarkPhoneVerified(user.ID); err != nil {
		writeError(w, http.StatusInternalServerError, "could not confirm your number")
		return
	}
	_ = s.phones.Clear(user.ID)

	updated, _ := s.users.GetByID(user.ID)
	writeJSON(w, http.StatusOK, map[string]any{"user": updated})
}

// plausiblePhone accepts the shapes a Philippine mobile number arrives in
// without being strict enough to reject a legitimate international one.
func plausiblePhone(phone string) bool {
	digits := 0
	for _, r := range phone {
		if r >= '0' && r <= '9' {
			digits++
		}
	}
	return digits >= 10 && digits <= 15
}

// maskPhone shows just enough for the user to recognise the number.
func maskPhone(phone string) string {
	digits := []rune{}
	for _, r := range phone {
		if r >= '0' && r <= '9' {
			digits = append(digits, r)
		}
	}
	if len(digits) <= 4 {
		return phone
	}
	return "•••• " + string(digits[len(digits)-4:])
}
