package handlers

import (
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
	if !contains(models.Roles, req.Role) {
		writeError(w, http.StatusBadRequest, "role must be one of: "+strings.Join(models.Roles, ", "))
		return
	}

	hash, err := auth.HashPassword(req.Password)
	if err != nil {
		writeError(w, http.StatusInternalServerError, "could not process password")
		return
	}

	user := &models.User{
		ID:           uuid.NewString(),
		Email:        req.Email,
		Name:         req.Name,
		Role:         req.Role,
		PasswordHash: hash,
		CreatedAt:    time.Now().UTC(),
	}
	if err := s.users.Create(user); err != nil {
		if err == store.ErrEmailTaken {
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
	} else if err != store.ErrNotFound {
		return nil, err
	}

	if user, err := s.users.GetByEmail(claims.Email); err == nil {
		if linkErr := s.users.SetGoogleID(user.ID, claims.Sub); linkErr != nil {
			return nil, linkErr
		}
		user.GoogleID = claims.Sub
		return user, nil
	} else if err != store.ErrNotFound {
		return nil, err
	}

	name := claims.Name
	if name == "" {
		name = claims.Email
	}
	user := &models.User{
		ID:        uuid.NewString(),
		Email:     claims.Email,
		Name:      name,
		Role:      "user", // social sign-ups start as regular users
		GoogleID:  claims.Sub,
		CreatedAt: time.Now().UTC(),
	}
	if err := s.users.Create(user); err != nil {
		return nil, err
	}
	return user, nil
}

func (s *Server) handleMe(w http.ResponseWriter, r *http.Request) {
	user, err := s.users.GetByID(userIDFrom(r.Context()))
	if err != nil {
		writeError(w, http.StatusNotFound, "user not found")
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{"user": user})
}
