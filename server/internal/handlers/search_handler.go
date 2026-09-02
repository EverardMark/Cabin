package handlers

import (
	"net/http"
	"net/url"
	"strings"

	"cabin/internal/models"
	"cabin/internal/store"

	"github.com/google/uuid"
)

type savedSearchInput struct {
	Name          string `json:"name"`
	Query         string `json:"query"` // e.g. "city=Muntinlupa&max_price=3000000&verified_only=true"
	AlertsEnabled *bool  `json:"alerts_enabled"`
}

// handleListSavedSearches returns the user's saved searches, each annotated
// with how many new listings have matched since they last looked.
func (s *Server) handleListSavedSearches(w http.ResponseWriter, r *http.Request) {
	userID := userIDFrom(r.Context())
	searches, err := s.searches.ForUser(userID)
	if err != nil {
		writeError(w, http.StatusInternalServerError, "could not load saved searches")
		return
	}
	for i := range searches {
		n, err := s.countNewMatches(&searches[i])
		if err != nil {
			writeError(w, http.StatusInternalServerError, "could not count new matches")
			return
		}
		searches[i].NewMatches = n
	}
	writeJSON(w, http.StatusOK, map[string]any{"searches": searches})
}

// countNewMatches counts listings matching a saved search that were posted
// after the user last viewed its results.
func (s *Server) countNewMatches(ss *models.SavedSearch) (int, error) {
	f, err := filterFromSavedQuery(ss.Query)
	if err != nil {
		return 0, nil // a malformed saved query shouldn't break the list
	}
	since := ss.LastAlertedAt
	f.CreatedAfter = &since
	return s.listings.Count(f)
}

func filterFromSavedQuery(q string) (store.ListingFilter, error) {
	values, err := url.ParseQuery(strings.TrimPrefix(q, "?"))
	if err != nil {
		return store.ListingFilter{}, err
	}
	return filterFromQuery(values), nil
}

func (s *Server) handleCreateSavedSearch(w http.ResponseWriter, r *http.Request) {
	userID := userIDFrom(r.Context())

	var in savedSearchInput
	if err := decodeJSON(w, r, &in); err != nil {
		writeError(w, http.StatusBadRequest, "invalid JSON body")
		return
	}
	in.Name = strings.TrimSpace(in.Name)
	in.Query = strings.TrimSpace(strings.TrimPrefix(in.Query, "?"))
	if in.Name == "" {
		writeError(w, http.StatusBadRequest, "name is required")
		return
	}
	if _, err := url.ParseQuery(in.Query); err != nil {
		writeError(w, http.StatusBadRequest, "query must be a valid URL query string")
		return
	}

	alerts := true
	if in.AlertsEnabled != nil {
		alerts = *in.AlertsEnabled
	}
	ss := &models.SavedSearch{
		ID:            uuid.NewString(),
		UserID:        userID,
		Name:          in.Name,
		Query:         in.Query,
		AlertsEnabled: alerts,
	}
	if err := s.searches.Create(ss); err != nil {
		writeError(w, http.StatusInternalServerError, "could not save search")
		return
	}
	writeJSON(w, http.StatusCreated, ss)
}

// savedSearchFor loads a saved search and confirms the caller owns it.
func (s *Server) savedSearchFor(w http.ResponseWriter, r *http.Request) (*models.SavedSearch, bool) {
	ss, err := s.searches.GetByID(r.PathValue("id"))
	if err != nil {
		writeError(w, http.StatusNotFound, "saved search not found")
		return nil, false
	}
	if ss.UserID != userIDFrom(r.Context()) {
		writeError(w, http.StatusNotFound, "saved search not found")
		return nil, false
	}
	return ss, true
}

func (s *Server) handleDeleteSavedSearch(w http.ResponseWriter, r *http.Request) {
	ss, ok := s.savedSearchFor(w, r)
	if !ok {
		return
	}
	if err := s.searches.Delete(ss.ID); err != nil {
		writeError(w, http.StatusInternalServerError, "could not delete saved search")
		return
	}
	w.WriteHeader(http.StatusNoContent)
}

// handleRunSavedSearch replays a saved search and clears its "new" badge.
func (s *Server) handleRunSavedSearch(w http.ResponseWriter, r *http.Request) {
	ss, ok := s.savedSearchFor(w, r)
	if !ok {
		return
	}
	f, err := filterFromSavedQuery(ss.Query)
	if err != nil {
		writeError(w, http.StatusBadRequest, "this saved search has an invalid query")
		return
	}
	listings, total, err := s.listings.List(f)
	if err != nil {
		writeError(w, http.StatusInternalServerError, "could not run saved search")
		return
	}
	if err := s.searches.MarkSeen(ss.ID); err != nil {
		writeError(w, http.StatusInternalServerError, "could not update saved search")
		return
	}
	writeJSON(w, http.StatusOK, listingsResponse{
		Listings: listings, Total: total, Page: f.Page, PageSize: f.PageSize,
	})
}
