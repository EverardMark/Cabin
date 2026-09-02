package handlers

import (
	"errors"
	"net/http"
	"strings"

	"cabin/internal/models"
	"cabin/internal/store"

	"github.com/google/uuid"
)

type reviewInput struct {
	Rating    int    `json:"rating"`
	Comment   string `json:"comment"`
	ListingID string `json:"listing_id"`
}

// handleCreateReview rates an owner or agent. Reviews are earned, not open:
// only someone who actually completed a viewing with that person may leave one,
// which is the answer to the survey's "vague feedback from other users".
func (s *Server) handleCreateReview(w http.ResponseWriter, r *http.Request) {
	subjectID := r.PathValue("id")
	authorID := userIDFrom(r.Context())

	if subjectID == authorID {
		writeError(w, http.StatusBadRequest, "you cannot review yourself")
		return
	}
	if _, err := s.users.GetByID(subjectID); err != nil {
		writeError(w, http.StatusNotFound, "user not found")
		return
	}

	var in reviewInput
	if err := decodeJSON(w, r, &in); err != nil {
		writeError(w, http.StatusBadRequest, "invalid JSON body")
		return
	}
	if in.Rating < 1 || in.Rating > 5 {
		writeError(w, http.StatusBadRequest, "rating must be between 1 and 5")
		return
	}

	// The reviewer must have completed a viewing hosted by the subject.
	dealt, err := s.viewings.CompletedBetween(authorID, subjectID)
	if err != nil {
		writeError(w, http.StatusInternalServerError, "could not verify your history with this user")
		return
	}
	if !dealt {
		writeError(w, http.StatusForbidden,
			"you can review someone after completing a viewing with them")
		return
	}

	review := &models.Review{
		ID:            uuid.NewString(),
		SubjectUserID: subjectID,
		AuthorID:      authorID,
		ListingID:     strings.TrimSpace(in.ListingID),
		Rating:        in.Rating,
		Comment:       strings.TrimSpace(in.Comment),
	}
	if err := s.reviews.Create(review); err != nil {
		if errors.Is(err, store.ErrDuplicate) {
			writeError(w, http.StatusConflict, "you have already reviewed this user")
			return
		}
		writeError(w, http.StatusInternalServerError, "could not save review")
		return
	}
	// Keep the cached aggregate on the user in step.
	if err := s.users.RefreshRating(subjectID); err != nil {
		writeError(w, http.StatusInternalServerError, "could not update rating")
		return
	}
	writeJSON(w, http.StatusCreated, review)
}

func (s *Server) handleListReviews(w http.ResponseWriter, r *http.Request) {
	subjectID := r.PathValue("id")
	list, err := s.reviews.ForUser(subjectID, atoiDefault(r.URL.Query().Get("limit"), 50))
	if err != nil {
		writeError(w, http.StatusInternalServerError, "could not load reviews")
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{"reviews": list})
}
