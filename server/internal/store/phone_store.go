package store

import (
	"database/sql"
	"errors"
	"time"
)

// Phone-verification limits. A one-time code is low-entropy by design, so the
// protection comes from short expiry plus tight attempt and send caps.
const (
	CodeTTL        = 10 * time.Minute
	MaxAttempts    = 5
	ResendCooldown = 60 * time.Second
	MaxSendsPerDay = 5
	SendWindow     = 24 * time.Hour
)

// Errors callers translate into HTTP responses.
var (
	ErrCodeExpired   = errors.New("code expired")
	ErrTooManyTries  = errors.New("too many attempts")
	ErrResendTooSoon = errors.New("resend too soon")
	ErrSendLimit     = errors.New("send limit reached")
)

// PhoneStore holds pending phone-verification codes, one per user.
type PhoneStore struct {
	db *sql.DB
}

func NewPhoneStore(db *sql.DB) *PhoneStore { return &PhoneStore{db: db} }

// Challenge is a pending verification.
type Challenge struct {
	UserID          string
	Phone           string
	CodeHash        string
	Attempts        int
	Sends           int
	ExpiresAt       time.Time
	LastSentAt      time.Time
	WindowStartedAt time.Time
}

// Get returns the pending challenge for a user, or ErrNotFound.
func (s *PhoneStore) Get(userID string) (*Challenge, error) {
	var c Challenge
	var expires, lastSent, windowStart string
	err := s.db.QueryRow(
		`SELECT user_id, phone, code_hash, attempts, sends, expires_at, last_sent_at, window_started_at
		 FROM phone_verifications WHERE user_id = ?`, userID,
	).Scan(&c.UserID, &c.Phone, &c.CodeHash, &c.Attempts, &c.Sends, &expires, &lastSent, &windowStart)
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return nil, ErrNotFound
		}
		return nil, err
	}
	c.ExpiresAt, _ = time.Parse(time.RFC3339, expires)
	c.LastSentAt, _ = time.Parse(time.RFC3339, lastSent)
	c.WindowStartedAt, _ = time.Parse(time.RFC3339, windowStart)
	return &c, nil
}

// CanSend reports whether a new code may be sent now, enforcing the cooldown
// and the rolling daily cap. It returns the send count to persist.
func (s *PhoneStore) CanSend(userID string) (sends int, err error) {
	existing, err := s.Get(userID)
	if errors.Is(err, ErrNotFound) {
		return 0, nil
	}
	if err != nil {
		return 0, err
	}
	if time.Since(existing.LastSentAt) < ResendCooldown {
		return 0, ErrResendTooSoon
	}
	// The cap is a rolling window: once it lapses, the count starts over.
	if time.Since(existing.WindowStartedAt) >= SendWindow {
		return 0, nil
	}
	if existing.Sends >= MaxSendsPerDay {
		return 0, ErrSendLimit
	}
	return existing.Sends, nil
}

// Upsert stores a freshly issued code, replacing any pending one.
func (s *PhoneStore) Upsert(userID, phone, codeHash string, priorSends int) error {
	now := time.Now().UTC()
	windowStart := now
	if priorSends > 0 {
		if existing, err := s.Get(userID); err == nil {
			windowStart = existing.WindowStartedAt
		}
	}
	_, err := s.db.Exec(`DELETE FROM phone_verifications WHERE user_id = ?`, userID)
	if err != nil {
		return err
	}
	_, err = s.db.Exec(
		`INSERT INTO phone_verifications
		 (user_id, phone, code_hash, attempts, sends, expires_at, last_sent_at, window_started_at)
		 VALUES (?,?,?,0,?,?,?,?)`,
		userID, phone, codeHash, priorSends+1,
		now.Add(CodeTTL).Format(time.RFC3339),
		now.Format(time.RFC3339),
		windowStart.Format(time.RFC3339),
	)
	return err
}

// RecordAttempt increments the failed-attempt counter.
func (s *PhoneStore) RecordAttempt(userID string) error {
	_, err := s.db.Exec(
		`UPDATE phone_verifications SET attempts = attempts + 1 WHERE user_id = ?`, userID)
	return err
}

// Clear removes a challenge, on success or after too many attempts.
func (s *PhoneStore) Clear(userID string) error {
	_, err := s.db.Exec(`DELETE FROM phone_verifications WHERE user_id = ?`, userID)
	return err
}
