package handlers

import (
	"context"
	"net/http"
	"strings"

	"cabin/internal/auth"
	"cabin/internal/config"
	"cabin/internal/middleware"
	"cabin/internal/storage"
	"cabin/internal/store"
)

// Server holds dependencies and wires up the HTTP routes.
type Server struct {
	cfg      *config.Config
	users    *store.UserStore
	listings *store.ListingStore
	reviews  *store.ReviewStore
	tokens   *auth.TokenService
	uploads  *storage.LocalStorage
}

func NewServer(
	cfg *config.Config,
	users *store.UserStore,
	listings *store.ListingStore,
	reviews *store.ReviewStore,
	tokens *auth.TokenService,
	uploads *storage.LocalStorage,
) *Server {
	return &Server{cfg: cfg, users: users, listings: listings, reviews: reviews, tokens: tokens, uploads: uploads}
}

// Handler builds the full HTTP handler with routes and middleware.
func (s *Server) Handler() http.Handler {
	mux := http.NewServeMux()

	mux.HandleFunc("GET /health", s.handleHealth)

	// Auth
	mux.HandleFunc("POST /api/v1/auth/register", s.handleRegister)
	mux.HandleFunc("POST /api/v1/auth/google", s.handleGoogleAuth)
	mux.HandleFunc("POST /api/v1/auth/login", s.handleLogin)
	mux.Handle("GET /api/v1/auth/me", s.requireAuth(http.HandlerFunc(s.handleMe)))

	// Listings (public reads)
	mux.HandleFunc("GET /api/v1/listings", s.handleListListings)
	mux.HandleFunc("GET /api/v1/listings/{id}", s.handleGetListing)

	// Listings (authenticated writes)
	mux.Handle("POST /api/v1/listings", s.requireAuth(http.HandlerFunc(s.handleCreateListing)))
	mux.Handle("PUT /api/v1/listings/{id}", s.requireAuth(http.HandlerFunc(s.handleUpdateListing)))
	mux.Handle("DELETE /api/v1/listings/{id}", s.requireAuth(http.HandlerFunc(s.handleDeleteListing)))
	mux.Handle("POST /api/v1/listings/{id}/images", s.requireAuth(http.HandlerFunc(s.handleUploadImage)))

	// Current user's listings
	mux.Handle("GET /api/v1/me/listings", s.requireAuth(http.HandlerFunc(s.handleMyListings)))

	// Trust: verification, public profiles, reviews, reports
	mux.Handle("POST /api/v1/me/verification", s.requireAuth(http.HandlerFunc(s.handleRequestVerification)))
	mux.HandleFunc("GET /api/v1/users/{id}", s.handleGetUser)
	mux.HandleFunc("GET /api/v1/users/{id}/reviews", s.handleListReviews)
	mux.Handle("POST /api/v1/users/{id}/reviews", s.requireAuth(http.HandlerFunc(s.handleCreateReview)))
	mux.Handle("POST /api/v1/listings/{id}/report", s.requireAuth(http.HandlerFunc(s.handleReportListing)))

	// Uploaded images
	fileServer := http.FileServer(http.Dir(s.cfg.UploadDir))
	mux.Handle("GET /uploads/", http.StripPrefix("/uploads/", fileServer))

	return middleware.Chain(mux, middleware.Recover, middleware.Logger, middleware.CORS)
}

func (s *Server) handleHealth(w http.ResponseWriter, r *http.Request) {
	writeJSON(w, http.StatusOK, map[string]string{"status": "ok", "service": "cabin-api"})
}

// --- auth middleware & context ---

type ctxKey string

const userIDKey ctxKey = "userID"

// requireAuth validates the Bearer token and injects the user id into context.
func (s *Server) requireAuth(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		const prefix = "Bearer "
		authz := r.Header.Get("Authorization")
		if !strings.HasPrefix(authz, prefix) {
			writeError(w, http.StatusUnauthorized, "missing or invalid Authorization header")
			return
		}
		userID, err := s.tokens.Verify(strings.TrimPrefix(authz, prefix))
		if err != nil {
			writeError(w, http.StatusUnauthorized, "invalid or expired token")
			return
		}
		ctx := context.WithValue(r.Context(), userIDKey, userID)
		next.ServeHTTP(w, r.WithContext(ctx))
	})
}

// userIDFrom returns the authenticated user id set by requireAuth.
func userIDFrom(ctx context.Context) string {
	id, _ := ctx.Value(userIDKey).(string)
	return id
}
