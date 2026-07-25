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

	hash, err := auth.HashPassword(req.Password)
	if err != nil {
		writeError(w, http.StatusInternalServerError, "could not process password")
		return
	}

	user := &models.User{
		ID:           uuid.NewString(),
		Email:        req.Email,
		Name:         req.Name,
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

func (s *Server) handleMe(w http.ResponseWriter, r *http.Request) {
	user, err := s.users.GetByID(userIDFrom(r.Context()))
	if err != nil {
		writeError(w, http.StatusNotFound, "user not found")
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{"user": user})
}
