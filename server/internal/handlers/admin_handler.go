package handlers

import (
	"net/http"
	"strings"
)

// handleListReports shows the moderation queue, open reports first by default.
func (s *Server) handleListReports(w http.ResponseWriter, r *http.Request) {
	status := strings.TrimSpace(r.URL.Query().Get("status"))
	if status == "" {
		status = "open"
	}
	if status == "all" {
		status = ""
	}
	reports, err := s.listings.Reports(status, atoiDefault(r.URL.Query().Get("limit"), 50))
	if err != nil {
		writeError(w, http.StatusInternalServerError, "could not load reports")
		return
	}
	// Attach the reported listing so a moderator can judge without a second call.
	for i := range reports {
		if l, err := s.listings.GetByID(reports[i].ListingID); err == nil {
			reports[i].Listing = l
		}
	}
	writeJSON(w, http.StatusOK, map[string]any{"reports": reports})
}

type resolveReportInput struct {
	Status     string `json:"status"` // upheld | dismissed
	Resolution string `json:"resolution"`
}

// handleResolveReport closes a report. Upholding one also sends the listing
// back for review, so a confirmed bad listing loses its badge.
func (s *Server) handleResolveReport(w http.ResponseWriter, r *http.Request) {
	var in resolveReportInput
	if err := decodeJSON(w, r, &in); err != nil {
		writeError(w, http.StatusBadRequest, "invalid JSON body")
		return
	}
	status := strings.ToLower(strings.TrimSpace(in.Status))
	if status != "upheld" && status != "dismissed" {
		writeError(w, http.StatusBadRequest, "status must be 'upheld' or 'dismissed'")
		return
	}

	reportID := r.PathValue("id")
	if err := s.listings.ResolveReport(reportID, status, strings.TrimSpace(in.Resolution)); err != nil {
		writeError(w, http.StatusNotFound, "report not found")
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{"status": status})
}

// handleReverifyListing puts a listing back in the review queue by hand.
func (s *Server) handleReverifyListing(w http.ResponseWriter, r *http.Request) {
	id := r.PathValue("id")
	if _, err := s.listings.GetByID(id); err != nil {
		writeError(w, http.StatusNotFound, "listing not found")
		return
	}
	if err := s.listings.RequeueVerification(id); err != nil {
		writeError(w, http.StatusInternalServerError, "could not queue listing for review")
		return
	}
	s.worker.Enqueue(id)
	writeJSON(w, http.StatusAccepted, map[string]any{"status": "pending"})
}
