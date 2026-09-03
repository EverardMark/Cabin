package handlers

import (
	"context"
	"net/http"
	"strings"

	"cabin/internal/auth"
	"cabin/internal/config"
	"cabin/internal/middleware"
	"cabin/internal/models"
	"cabin/internal/storage"
	"cabin/internal/store"
	"cabin/internal/verify"
)

// Server holds dependencies and wires up the HTTP routes.
type Server struct {
	cfg      *config.Config
	users    *store.UserStore
	listings *store.ListingStore
	chat     *store.ChatStore
	viewings *store.ViewingStore
	reviews  *store.ReviewStore
	searches *store.SearchStore
	tokens   *auth.TokenService
	uploads  *storage.LocalStorage
	verifier *verify.Service
	worker   *verify.Worker
}

// Deps bundles what the HTTP layer needs. Grouping them keeps the constructor
// readable now that the API covers messaging, viewings, reviews and search.
type Deps struct {
	Config   *config.Config
	Users    *store.UserStore
	Listings *store.ListingStore
	Chat     *store.ChatStore
	Viewings *store.ViewingStore
	Reviews  *store.ReviewStore
	Searches *store.SearchStore
	Tokens   *auth.TokenService
	Uploads  *storage.LocalStorage
	Verifier *verify.Service
	Worker   *verify.Worker
}

func NewServer(d Deps) *Server {
	return &Server{
		cfg:      d.Config,
		users:    d.Users,
		listings: d.Listings,
		chat:     d.Chat,
		viewings: d.Viewings,
		reviews:  d.Reviews,
		searches: d.Searches,
		tokens:   d.Tokens,
		uploads:  d.Uploads,
		verifier: d.Verifier,
		worker:   d.Worker,
	}
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

	// Profile & identity verification
	mux.Handle("PATCH /api/v1/me", s.requireAuth(http.HandlerFunc(s.handleUpdateProfile)))
	mux.Handle("POST /api/v1/me/verification", s.requireAuth(http.HandlerFunc(s.handleRequestVerification)))
	mux.Handle("GET /api/v1/me/listings", s.requireAuth(http.HandlerFunc(s.handleMyListings)))
	mux.Handle("GET /api/v1/me/summary", s.requireAuth(http.HandlerFunc(s.handleMySummary)))

	// Listings (public reads)
	mux.HandleFunc("GET /api/v1/listings", s.handleListListings)
	mux.HandleFunc("GET /api/v1/listings/{id}", s.handleGetListing)
	mux.HandleFunc("GET /api/v1/listings/{id}/price-comparison", s.handlePriceComparison)
	mux.HandleFunc("GET /api/v1/feature-plans", s.handleFeaturePlans)
	mux.HandleFunc("GET /api/v1/users/{id}", s.handleGetUser)
	mux.HandleFunc("GET /api/v1/users/{id}/reviews", s.handleListReviews)

	// Listings (authenticated writes)
	mux.Handle("POST /api/v1/listings", s.requireAuth(http.HandlerFunc(s.handleCreateListing)))
	mux.Handle("PUT /api/v1/listings/{id}", s.requireAuth(http.HandlerFunc(s.handleUpdateListing)))
	mux.Handle("DELETE /api/v1/listings/{id}", s.requireAuth(http.HandlerFunc(s.handleDeleteListing)))
	mux.Handle("POST /api/v1/listings/{id}/images", s.requireAuth(http.HandlerFunc(s.handleUploadImage)))
	mux.Handle("POST /api/v1/listings/{id}/confirm", s.requireAuth(http.HandlerFunc(s.handleConfirmListing)))
	mux.Handle("POST /api/v1/listings/{id}/report", s.requireAuth(http.HandlerFunc(s.handleReportListing)))
	mux.Handle("POST /api/v1/listings/{id}/feature", s.requireAuth(http.HandlerFunc(s.handleFeatureListing)))
	mux.Handle("DELETE /api/v1/listings/{id}/images/{imageId}", s.requireAuth(http.HandlerFunc(s.handleDeleteImage)))
	mux.Handle("PUT /api/v1/listings/{id}/images/order", s.requireAuth(http.HandlerFunc(s.handleReorderImages)))

	// Messaging
	mux.Handle("POST /api/v1/listings/{id}/conversations", s.requireAuth(http.HandlerFunc(s.handleStartConversation)))
	mux.Handle("GET /api/v1/conversations", s.requireAuth(http.HandlerFunc(s.handleListConversations)))
	mux.Handle("GET /api/v1/conversations/{id}/messages", s.requireAuth(http.HandlerFunc(s.handleListMessages)))
	mux.Handle("POST /api/v1/conversations/{id}/messages", s.requireAuth(http.HandlerFunc(s.handleSendMessage)))

	// Viewing appointments
	mux.Handle("POST /api/v1/listings/{id}/viewings", s.requireAuth(http.HandlerFunc(s.handleRequestViewing)))
	mux.Handle("GET /api/v1/viewings", s.requireAuth(http.HandlerFunc(s.handleListViewings)))
	mux.Handle("PATCH /api/v1/viewings/{id}", s.requireAuth(http.HandlerFunc(s.handleUpdateViewing)))

	// Reviews
	mux.Handle("POST /api/v1/users/{id}/reviews", s.requireAuth(http.HandlerFunc(s.handleCreateReview)))

	// Saved searches
	mux.Handle("GET /api/v1/me/searches", s.requireAuth(http.HandlerFunc(s.handleListSavedSearches)))
	mux.Handle("POST /api/v1/me/searches", s.requireAuth(http.HandlerFunc(s.handleCreateSavedSearch)))
	mux.Handle("DELETE /api/v1/me/searches/{id}", s.requireAuth(http.HandlerFunc(s.handleDeleteSavedSearch)))
	mux.Handle("GET /api/v1/me/searches/{id}/results", s.requireAuth(http.HandlerFunc(s.handleRunSavedSearch)))

	// Moderation
	mux.Handle("GET /api/v1/admin/reports", s.requireAdmin(http.HandlerFunc(s.handleListReports)))
	mux.Handle("POST /api/v1/admin/reports/{id}/resolve", s.requireAdmin(http.HandlerFunc(s.handleResolveReport)))
	mux.Handle("POST /api/v1/admin/listings/{id}/reverify", s.requireAdmin(http.HandlerFunc(s.handleReverifyListing)))

	// Uploaded images. A dedicated handler rather than http.FileServer so the
	// directory index is not served — a FileServer would list every upload.
	mux.HandleFunc("GET /uploads/{name}", s.handleServeUpload)

	return middleware.Chain(mux, middleware.Recover, middleware.Logger, middleware.CORS)
}

func (s *Server) handleHealth(w http.ResponseWriter, r *http.Request) {
	writeJSON(w, http.StatusOK, map[string]any{
		"status":       "ok",
		"service":      "cabin-api",
		"ai_review":    s.verifier.Enabled(),
		"review_model": s.verifier.ModelName(),
	})
}

// handleServeUpload serves one uploaded file by name. Names are generated
// UUIDs, and the path is rejected if it contains any separator, so a request
// cannot escape the upload directory or enumerate it.
func (s *Server) handleServeUpload(w http.ResponseWriter, r *http.Request) {
	name := r.PathValue("name")
	if name == "" || strings.ContainsAny(name, `/\`) || strings.Contains(name, "..") {
		writeError(w, http.StatusBadRequest, "invalid file name")
		return
	}
	path, err := s.uploads.Path(name)
	if err != nil {
		writeError(w, http.StatusNotFound, "file not found")
		return
	}
	http.ServeFile(w, r, path)
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

// requireAdmin is requireAuth plus an admin role check.
func (s *Server) requireAdmin(next http.Handler) http.Handler {
	return s.requireAuth(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		user, err := s.users.GetByID(userIDFrom(r.Context()))
		if err != nil {
			writeError(w, http.StatusUnauthorized, "account not found")
			return
		}
		if !user.IsAdmin() {
			writeError(w, http.StatusForbidden, "admin access required")
			return
		}
		next.ServeHTTP(w, r)
	}))
}

// userIDFrom returns the authenticated user id set by requireAuth.
func userIDFrom(ctx context.Context) string {
	id, _ := ctx.Value(userIDKey).(string)
	return id
}

// currentUser loads the authenticated account.
func (s *Server) currentUser(r *http.Request) (*models.User, error) {
	return s.users.GetByID(userIDFrom(r.Context()))
}
