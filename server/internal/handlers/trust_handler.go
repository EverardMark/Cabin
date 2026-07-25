package handlers

import (
	"net/http"
	"strings"
	"time"

	"cabin/internal/models"
	"cabin/internal/store"

	"github.com/google/uuid"
)

// handleRequestVerification marks the current user as verified.
//
// For this MVP verification is auto-approved so the trust badge flow is
// demonstrable end-to-end. In production this endpoint would instead accept
// identity/ownership documents and create a *pending* request for review
// (manual or automated KYC) before the verified flag is set.
func (s *Server) handleRequestVerification(w http.ResponseWriter, r *http.Request) {
	userID := userIDFrom(r.Context())
	if err := s.users.SetVerified(userID, true); err != nil {
		writeError(w, http.StatusInternalServerError, "could not update verification")
		return
	}
	user, err := s.users.GetByID(userID)
	if err != nil {
		writeError(w, http.StatusInternalServerError, "could not load user")
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{"user": user, "status": "verified"})
}

type userProfile struct {
	ID          string    `json:"id"`
	Name        string    `json:"name"`
	Verified    bool      `json:"verified"`
	RatingAvg   float64   `json:"rating_avg"`
	RatingCount int       `json:"rating_count"`
	CreatedAt   time.Time `json:"created_at"`
}

// handleGetUser returns a public profile (no email) with trust signals.
func (s *Server) handleGetUser(w http.ResponseWriter, r *http.Request) {
	user, err := s.users.GetByID(r.PathValue("id"))
	if err != nil {
		writeError(w, http.StatusNotFound, "user not found")
		return
	}
	avg, count, err := s.reviews.Aggregate(user.ID)
	if err != nil {
		writeError(w, http.StatusInternalServerError, "could not load profile")
		return
	}
	writeJSON(w, http.StatusOK, userProfile{
		ID:          user.ID,
		Name:        user.Name,
		Verified:    user.Verified,
		RatingAvg:   avg,
		RatingCount: count,
		CreatedAt:   user.CreatedAt,
	})
}

// handleListReviews lists the reviews written about a user.
func (s *Server) handleListReviews(w http.ResponseWriter, r *http.Request) {
	subjectID := r.PathValue("id")
	reviews, err := s.reviews.ListForSubject(subjectID)
	if err != nil {
		writeError(w, http.StatusInternalServerError, "could not load reviews")
		return
	}
	avg, count, _ := s.reviews.Aggregate(subjectID)
	writeJSON(w, http.StatusOK, map[string]any{
		"reviews":      reviews,
		"rating_avg":   avg,
		"rating_count": count,
	})
}

type reviewRequest struct {
	Rating  int    `json:"rating"`
	Comment string `json:"comment"`
}

// handleCreateReview lets an authenticated user rate another user (owner/agent).
func (s *Server) handleCreateReview(w http.ResponseWriter, r *http.Request) {
	subjectID := r.PathValue("id")
	authorID := userIDFrom(r.Context())

	if subjectID == authorID {
		writeError(w, http.StatusBadRequest, "you can't review yourself")
		return
	}
	if _, err := s.users.GetByID(subjectID); err != nil {
		writeError(w, http.StatusNotFound, "user not found")
		return
	}

	var req reviewRequest
	if err := decodeJSON(w, r, &req); err != nil {
		writeError(w, http.StatusBadRequest, "invalid JSON body")
		return
	}
	if req.Rating < 1 || req.Rating > 5 {
		writeError(w, http.StatusBadRequest, "rating must be between 1 and 5")
		return
	}

	review := &models.Review{
		ID:        uuid.NewString(),
		SubjectID: subjectID,
		AuthorID:  authorID,
		Rating:    req.Rating,
		Comment:   strings.TrimSpace(req.Comment),
	}
	if err := s.reviews.Add(review); err != nil {
		if err == store.ErrDuplicateReview {
			writeError(w, http.StatusConflict, "you have already reviewed this user")
			return
		}
		writeError(w, http.StatusInternalServerError, "could not save review")
		return
	}
	if author, err := s.users.GetByID(authorID); err == nil {
		review.AuthorName = author.Name
	}
	writeJSON(w, http.StatusCreated, review)
}

type reportRequest struct {
	Reason string `json:"reason"`
	Detail string `json:"detail"`
}

// handleReportListing records a report against a listing (anti-scam / moderation).
func (s *Server) handleReportListing(w http.ResponseWriter, r *http.Request) {
	listingID := r.PathValue("id")
	reporterID := userIDFrom(r.Context())

	if _, err := s.listings.GetByID(listingID); err != nil {
		if err == store.ErrNotFound {
			writeError(w, http.StatusNotFound, "listing not found")
			return
		}
		writeError(w, http.StatusInternalServerError, "could not load listing")
		return
	}

	var req reportRequest
	if err := decodeJSON(w, r, &req); err != nil {
		writeError(w, http.StatusBadRequest, "invalid JSON body")
		return
	}
	reason := strings.ToLower(strings.TrimSpace(req.Reason))
	if !contains(models.ReportReasons, reason) {
		writeError(w, http.StatusBadRequest, "reason must be one of: "+strings.Join(models.ReportReasons, ", "))
		return
	}

	if err := s.listings.AddReport(uuid.NewString(), listingID, reporterID, reason, strings.TrimSpace(req.Detail)); err != nil {
		writeError(w, http.StatusInternalServerError, "could not submit report")
		return
	}
	writeJSON(w, http.StatusCreated, map[string]string{"status": "reported"})
}
