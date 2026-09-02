package handlers

import (
	"net/http"
	"strings"
	"time"

	"cabin/internal/models"

	"github.com/google/uuid"
)

type viewingInput struct {
	ScheduledFor string `json:"scheduled_for"` // RFC3339
	Note         string `json:"note"`
}

// handleRequestViewing books a property viewing. Scheduling was the survey's
// third-most requested feature and its fourth-largest cause of delays.
func (s *Server) handleRequestViewing(w http.ResponseWriter, r *http.Request) {
	listingID := r.PathValue("id")
	userID := userIDFrom(r.Context())

	listing, err := s.listings.GetByID(listingID)
	if err != nil {
		writeError(w, http.StatusNotFound, "listing not found")
		return
	}
	if listing.UserID == userID {
		writeError(w, http.StatusBadRequest, "you cannot book a viewing on your own listing")
		return
	}
	if listing.VerificationStatus == models.VerificationRejected {
		writeError(w, http.StatusForbidden, "this listing is under review and cannot be booked")
		return
	}

	var in viewingInput
	if err := decodeJSON(w, r, &in); err != nil {
		writeError(w, http.StatusBadRequest, "invalid JSON body")
		return
	}
	at, err := time.Parse(time.RFC3339, strings.TrimSpace(in.ScheduledFor))
	if err != nil {
		writeError(w, http.StatusBadRequest, "scheduled_for must be an RFC3339 timestamp")
		return
	}
	if at.Before(time.Now()) {
		writeError(w, http.StatusBadRequest, "pick a time in the future")
		return
	}

	v := &models.ViewingRequest{
		ID:           uuid.NewString(),
		ListingID:    listingID,
		RequesterID:  userID,
		OwnerID:      listing.UserID,
		ScheduledFor: at,
		Status:       "requested",
		Note:         strings.TrimSpace(in.Note),
	}
	if err := s.viewings.Create(v); err != nil {
		writeError(w, http.StatusInternalServerError, "could not request viewing")
		return
	}
	v.Listing = listing
	writeJSON(w, http.StatusCreated, v)
}

func (s *Server) handleListViewings(w http.ResponseWriter, r *http.Request) {
	userID := userIDFrom(r.Context())
	list, err := s.viewings.ForUser(userID)
	if err != nil {
		writeError(w, http.StatusInternalServerError, "could not load viewings")
		return
	}
	for i := range list {
		if l, err := s.listings.GetByID(list[i].ListingID); err == nil {
			list[i].Listing = l
		}
		if u, err := s.users.GetByID(list[i].RequesterID); err == nil {
			sum := u.Summary()
			list[i].Requester = &sum
		}
		if u, err := s.users.GetByID(list[i].OwnerID); err == nil {
			sum := u.Summary()
			list[i].Owner = &sum
		}
	}
	writeJSON(w, http.StatusOK, map[string]any{"viewings": list})
}

type viewingUpdateInput struct {
	Status       string `json:"status"`
	ResponseNote string `json:"response_note"`
	ScheduledFor string `json:"scheduled_for"` // set to propose a new time
}

// handleUpdateViewing moves a viewing through its lifecycle. The owner accepts
// or declines; either side may cancel or propose a new time; the owner marks it
// completed afterwards, which is what unlocks reviews.
func (s *Server) handleUpdateViewing(w http.ResponseWriter, r *http.Request) {
	userID := userIDFrom(r.Context())
	v, err := s.viewings.GetByID(r.PathValue("id"))
	if err != nil {
		writeError(w, http.StatusNotFound, "viewing not found")
		return
	}
	isOwner := v.OwnerID == userID
	isRequester := v.RequesterID == userID
	if !isOwner && !isRequester {
		writeError(w, http.StatusNotFound, "viewing not found")
		return
	}

	var in viewingUpdateInput
	if err := decodeJSON(w, r, &in); err != nil {
		writeError(w, http.StatusBadRequest, "invalid JSON body")
		return
	}

	// Proposing a new time resets the request for the other side to accept.
	if strings.TrimSpace(in.ScheduledFor) != "" {
		at, err := time.Parse(time.RFC3339, strings.TrimSpace(in.ScheduledFor))
		if err != nil {
			writeError(w, http.StatusBadRequest, "scheduled_for must be an RFC3339 timestamp")
			return
		}
		if at.Before(time.Now()) {
			writeError(w, http.StatusBadRequest, "pick a time in the future")
			return
		}
		if err := s.viewings.Reschedule(v.ID, at); err != nil {
			writeError(w, http.StatusInternalServerError, "could not reschedule viewing")
			return
		}
		updated, _ := s.viewings.GetByID(v.ID)
		writeJSON(w, http.StatusOK, updated)
		return
	}

	status := strings.ToLower(strings.TrimSpace(in.Status))
	if !contains(models.ViewingStatuses, status) {
		writeError(w, http.StatusBadRequest, "status must be one of: "+strings.Join(models.ViewingStatuses, ", "))
		return
	}
	// Only the owner may confirm, decline, or complete a viewing.
	switch status {
	case "confirmed", "declined", "completed":
		if !isOwner {
			writeError(w, http.StatusForbidden, "only the listing owner can do that")
			return
		}
	case "cancelled":
		// Either side may cancel.
	case "requested":
		writeError(w, http.StatusBadRequest, "propose a new time instead of resetting the status")
		return
	}

	if status == "confirmed" {
		taken, err := s.viewings.SlotTaken(v.OwnerID, v.ScheduledFor)
		if err != nil {
			writeError(w, http.StatusInternalServerError, "could not check your schedule")
			return
		}
		if taken {
			writeError(w, http.StatusConflict, "you already have a confirmed viewing at that time")
			return
		}
	}

	if err := s.viewings.UpdateStatus(v.ID, status, strings.TrimSpace(in.ResponseNote)); err != nil {
		writeError(w, http.StatusInternalServerError, "could not update viewing")
		return
	}
	updated, _ := s.viewings.GetByID(v.ID)
	writeJSON(w, http.StatusOK, updated)
}
