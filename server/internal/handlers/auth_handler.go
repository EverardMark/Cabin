package handlers

import (
	"errors"
	"net/http"
	"strings"
	"time"

	"cabin/internal/auth"
	"cabin/internal/models"
	"cabin/internal/store"

	"github.com/google/uuid"
)

type registerRequest struct {
	Email    string `json:"email"`
	Password string `json:"password"`
	Name     string `json:"name"`
	Phone    string `json:"phone"`
	Role     string `json:"role"` // "user" (default) or "agent"
}

type loginRequest struct {
	Email    string `json:"email"`
	Password string `json:"password"`
}

type authResponse struct {
	Token string      `json:"token"`
	User  models.User `json:"user"`
}

func (s *Server) handleRegister(w http.ResponseWriter, r *http.Request) {
	var req registerRequest
	if err := decodeJSON(w, r, &req); err != nil {
		writeError(w, http.StatusBadRequest, "invalid JSON body")
		return
	}
	req.Email = strings.TrimSpace(strings.ToLower(req.Email))
	req.Name = strings.TrimSpace(req.Name)
	req.Phone = strings.TrimSpace(req.Phone)

	if !strings.Contains(req.Email, "@") {
		writeError(w, http.StatusBadRequest, "a valid email is required")
		return
	}
	if len(req.Password) < 6 {
		writeError(w, http.StatusBadRequest, "password must be at least 6 characters")
		return
	}
	if req.Name == "" {
		writeError(w, http.StatusBadRequest, "name is required")
		return
	}
	req.Role = strings.TrimSpace(strings.ToLower(req.Role))
	if req.Role == "" {
		req.Role = "user"
	}
	// Admin accounts are provisioned, never self-selected at signup.
	if req.Role == models.RoleAdmin {
		writeError(w, http.StatusForbidden, "admin accounts cannot be self-registered")
		return
	}
	if !contains(models.Roles, req.Role) {
		writeError(w, http.StatusBadRequest, "role must be one of: user, agent")
		return
	}

	hash, err := auth.HashPassword(req.Password)
	if err != nil {
		writeError(w, http.StatusInternalServerError, "could not process password")
		return
	}

	user := &models.User{
		ID:                 uuid.NewString(),
		Email:              req.Email,
		Name:               req.Name,
		Phone:              req.Phone,
		Role:               req.Role,
		PasswordHash:       hash,
		VerificationStatus: models.VerificationUnverified,
		CreatedAt:          time.Now().UTC(),
	}
	if err := s.users.Create(user); err != nil {
		if errors.Is(err, store.ErrEmailTaken) {
			writeError(w, http.StatusConflict, "email already registered")
			return
		}
		writeError(w, http.StatusInternalServerError, "could not create account")
		return
	}

	token, err := s.tokens.Generate(user.ID)
	if err != nil {
		writeError(w, http.StatusInternalServerError, "could not issue token")
		return
	}
	writeJSON(w, http.StatusCreated, authResponse{Token: token, User: *user})
}

func (s *Server) handleLogin(w http.ResponseWriter, r *http.Request) {
	var req loginRequest
	if err := decodeJSON(w, r, &req); err != nil {
		writeError(w, http.StatusBadRequest, "invalid JSON body")
		return
	}
	req.Email = strings.TrimSpace(strings.ToLower(req.Email))

	user, err := s.users.GetByEmail(req.Email)
	if err != nil || !auth.CheckPassword(user.PasswordHash, req.Password) {
		// Same message whether the email is unknown or the password is wrong.
		writeError(w, http.StatusUnauthorized, "invalid email or password")
		return
	}

	token, err := s.tokens.Generate(user.ID)
	if err != nil {
		writeError(w, http.StatusInternalServerError, "could not issue token")
		return
	}
	writeJSON(w, http.StatusOK, authResponse{Token: token, User: *user})
}

type googleAuthRequest struct {
	IDToken string `json:"id_token"`
}

// handleGoogleAuth signs a user in from a Google ID token. It is disabled
// (501) until GOOGLE_CLIENT_ID is configured. On success it links or creates a
// user and issues our own JWT, so the client session flow is identical to
// email/password login.
func (s *Server) handleGoogleAuth(w http.ResponseWriter, r *http.Request) {
	if len(s.cfg.GoogleClientIDs) == 0 {
		writeError(w, http.StatusNotImplemented, "google sign-in is not configured")
		return
	}
	var req googleAuthRequest
	if err := decodeJSON(w, r, &req); err != nil {
		writeError(w, http.StatusBadRequest, "invalid JSON body")
		return
	}
	if strings.TrimSpace(req.IDToken) == "" {
		writeError(w, http.StatusBadRequest, "id_token is required")
		return
	}

	claims, err := auth.VerifyGoogleIDToken(r.Context(), req.IDToken, s.cfg.GoogleClientIDs)
	if err != nil {
		writeError(w, http.StatusUnauthorized, "could not verify Google account")
		return
	}
	if !claims.EmailVerified {
		writeError(w, http.StatusUnauthorized, "your Google email is not verified")
		return
	}

	user, err := s.findOrCreateGoogleUser(claims)
	if err != nil {
		writeError(w, http.StatusInternalServerError, "could not sign you in")
		return
	}

	token, err := s.tokens.Generate(user.ID)
	if err != nil {
		writeError(w, http.StatusInternalServerError, "could not issue token")
		return
	}
	writeJSON(w, http.StatusOK, authResponse{Token: token, User: *user})
}

// findOrCreateGoogleUser resolves the app account for a verified Google
// identity: (1) an account already linked to this Google id, else (2) an
// existing account with the same email (which we link), else (3) a new
// password-less social account.
func (s *Server) findOrCreateGoogleUser(claims *auth.GoogleClaims) (*models.User, error) {
	if user, err := s.users.GetByGoogleID(claims.Sub); err == nil {
		return user, nil
	} else if !errors.Is(err, store.ErrNotFound) {
		return nil, err
	}

	if user, err := s.users.GetByEmail(claims.Email); err == nil {
		if linkErr := s.users.SetGoogleID(user.ID, claims.Sub); linkErr != nil {
			return nil, linkErr
		}
		user.GoogleID = claims.Sub
		user.EmailVerified = true
		return user, nil
	} else if !errors.Is(err, store.ErrNotFound) {
		return nil, err
	}

	name := claims.Name
	if name == "" {
		name = claims.Email
	}
	user := &models.User{
		ID:                 uuid.NewString(),
		Email:              claims.Email,
		Name:               name,
		Role:               "user",
		GoogleID:           claims.Sub,
		EmailVerified:      true, // Google asserted it
		VerificationStatus: models.VerificationUnverified,
		CreatedAt:          time.Now().UTC(),
	}
	if err := s.users.Create(user); err != nil {
		return nil, err
	}
	return user, nil
}

func (s *Server) handleMe(w http.ResponseWriter, r *http.Request) {
	user, err := s.currentUser(r)
	if err != nil {
		writeError(w, http.StatusNotFound, "user not found")
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{"user": user})
}

// handleGetUser returns another user's public profile.
func (s *Server) handleGetUser(w http.ResponseWriter, r *http.Request) {
	user, err := s.users.GetByID(r.PathValue("id"))
	if err != nil {
		writeError(w, http.StatusNotFound, "user not found")
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{
		"user": map[string]any{
			"id":                  user.ID,
			"name":                user.Name,
			"role":                user.Role,
			"bio":                 user.Bio,
			"verification_status": user.VerificationStatus,
			"rating_avg":          user.RatingAvg,
			"rating_count":        user.RatingCount,
			"created_at":          user.CreatedAt,
		},
	})
}

type profileInput struct {
	Name      *string `json:"name"`
	Phone     *string `json:"phone"`
	Bio       *string `json:"bio"`
	LicenseNo *string `json:"license_no"`
	Role      *string `json:"role"`
}

// handleUpdateProfile edits the signed-in user's own profile.
func (s *Server) handleUpdateProfile(w http.ResponseWriter, r *http.Request) {
	user, err := s.currentUser(r)
	if err != nil {
		writeError(w, http.StatusUnauthorized, "account not found")
		return
	}
	var in profileInput
	if err := decodeJSON(w, r, &in); err != nil {
		writeError(w, http.StatusBadRequest, "invalid JSON body")
		return
	}

	if in.Name != nil {
		if name := strings.TrimSpace(*in.Name); name != "" {
			user.Name = name
		}
	}
	if in.Phone != nil {
		phone := strings.TrimSpace(*in.Phone)
		if phone != user.Phone {
			// A changed number has to be proven again.
			user.PhoneVerified = false
		}
		user.Phone = phone
	}
	if in.Bio != nil {
		user.Bio = strings.TrimSpace(*in.Bio)
	}
	if in.LicenseNo != nil {
		user.LicenseNo = strings.TrimSpace(*in.LicenseNo)
	}
	if in.Role != nil {
		role := strings.ToLower(strings.TrimSpace(*in.Role))
		// Switching between "user" and "agent" is allowed; admin is not.
		if role == "user" || role == models.RoleAgent {
			user.Role = role
		}
	}

	if err := s.users.UpdateProfile(user); err != nil {
		writeError(w, http.StatusInternalServerError, "could not update profile")
		return
	}
	updated, _ := s.users.GetByID(user.ID)
	writeJSON(w, http.StatusOK, map[string]any{"user": updated})
}

// handleRequestVerification submits the signed-in account for identity review.
// The reviewer weighs profile completeness and plausibility; an agent claiming
// a licence gets a stricter look than a private owner.
func (s *Server) handleRequestVerification(w http.ResponseWriter, r *http.Request) {
	user, err := s.currentUser(r)
	if err != nil {
		writeError(w, http.StatusUnauthorized, "account not found")
		return
	}
	if user.VerificationStatus == models.VerificationVerified {
		writeJSON(w, http.StatusOK, map[string]any{"user": user})
		return
	}
	if strings.TrimSpace(user.Phone) == "" {
		writeError(w, http.StatusBadRequest, "add a contact number to your profile before requesting verification")
		return
	}

	verdict, err := s.verifier.ReviewUser(r.Context(), user)
	if err != nil {
		writeError(w, http.StatusBadGateway, "verification is temporarily unavailable, please try again")
		return
	}
	if err := s.users.SetVerification(user.ID, verdict.Status, verdict.Score, verdict.Summary); err != nil {
		writeError(w, http.StatusInternalServerError, "could not save verification result")
		return
	}
	updated, _ := s.users.GetByID(user.ID)
	writeJSON(w, http.StatusOK, map[string]any{"user": updated, "verification": verdict})
}

// handleMySummary powers the home badge counts: unread messages, upcoming
// viewings, and listings that need the owner's attention.
func (s *Server) handleMySummary(w http.ResponseWriter, r *http.Request) {
	userID := userIDFrom(r.Context())

	unread, err := s.chat.UnreadTotal(userID)
	if err != nil {
		writeError(w, http.StatusInternalServerError, "could not build summary")
		return
	}
	viewings, err := s.viewings.ForUser(userID)
	if err != nil {
		writeError(w, http.StatusInternalServerError, "could not build summary")
		return
	}
	upcoming, pending := 0, 0
	now := time.Now()
	for _, v := range viewings {
		if v.Status == "confirmed" && v.ScheduledFor.After(now) {
			upcoming++
		}
		if v.Status == "requested" && v.OwnerID == userID {
			pending++
		}
	}

	mine, _, err := s.listings.List(store.ListingFilter{
		UserID: userID, IncludeRejected: true, Page: 1, PageSize: 100,
	})
	if err != nil {
		writeError(w, http.StatusInternalServerError, "could not build summary")
		return
	}
	needsAttention := 0
	for i := range mine {
		if mine[i].VerificationStatus == models.VerificationRejected ||
			mine[i].VerificationStatus == models.VerificationFlagged ||
			mine[i].IsStale() {
			needsAttention++
		}
	}

	writeJSON(w, http.StatusOK, map[string]any{
		"unread_messages":            unread,
		"upcoming_viewings":          upcoming,
		"pending_viewing_requests":   pending,
		"listings_needing_attention": needsAttention,
	})
}
