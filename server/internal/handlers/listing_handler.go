package handlers

import (
	"bytes"
	"errors"
	"fmt"
	"io"
	"math"
	"net/http"
	"path/filepath"
	"strconv"
	"strings"
	"time"

	"cabin/internal/models"
	"cabin/internal/store"

	"github.com/google/uuid"
)

// listingInput is the create/update payload. Pointer fields distinguish
// "omitted" from "set to zero value", enabling partial updates.
type listingInput struct {
	Title        *string  `json:"title"`
	Description  *string  `json:"description"`
	Price        *int64   `json:"price"`
	Currency     *string  `json:"currency"`
	PropertyType *string  `json:"property_type"`
	ListingType  *string  `json:"listing_type"`
	Bedrooms     *int     `json:"bedrooms"`
	Bathrooms    *float64 `json:"bathrooms"`
	AreaSqft     *int     `json:"area_sqft"`
	Address      *string  `json:"address"`
	City         *string  `json:"city"`
	State        *string  `json:"state"`
	ZipCode      *string  `json:"zip_code"`
	Latitude     *float64 `json:"latitude"`
	Longitude    *float64 `json:"longitude"`
	Status       *string  `json:"status"`
}

type listingsResponse struct {
	Listings []models.Listing `json:"listings"`
	Total    int              `json:"total"`
	Page     int              `json:"page"`
	PageSize int              `json:"page_size"`
}

// filterFromQuery builds a store filter from URL query parameters. It is shared
// by browse, map search and saved-search replay.
func filterFromQuery(q map[string][]string) store.ListingFilter {
	get := func(k string) string {
		if v, ok := q[k]; ok && len(v) > 0 {
			return strings.TrimSpace(v[0])
		}
		return ""
	}

	page := atoiDefault(get("page"), 1)
	if page < 1 {
		page = 1
	}
	size := atoiDefault(get("page_size"), 20)
	if size < 1 || size > 100 {
		size = 20
	}

	f := store.ListingFilter{
		Query:        get("q"),
		City:         get("city"),
		PropertyType: get("property_type"),
		ListingType:  get("listing_type"),
		Status:       get("status"),
		Sort:         get("sort"),
		Page:         page,
		PageSize:     size,
		// Trust filters: 79% of surveyed users asked to see verified listings only.
		VerifiedOnly: get("verified_only") == "true",
		ExcludeStale: get("exclude_stale") == "true",
	}

	if v := get("min_price"); v != "" {
		if n, err := strconv.ParseInt(v, 10, 64); err == nil {
			f.MinPrice = &n
		}
	}
	if v := get("max_price"); v != "" {
		if n, err := strconv.ParseInt(v, 10, 64); err == nil {
			f.MaxPrice = &n
		}
	}
	if v := get("min_bedrooms"); v != "" {
		if n, err := strconv.Atoi(v); err == nil {
			f.MinBedrooms = &n
		}
	}
	if v := get("min_bathrooms"); v != "" {
		if n, err := strconv.ParseFloat(v, 64); err == nil {
			f.MinBathrooms = &n
		}
	}
	if v := get("min_area"); v != "" {
		if n, err := strconv.Atoi(v); err == nil {
			f.MinArea = &n
		}
	}

	// Map search: an explicit bounding box, or a centre plus radius.
	minLat, okA := parseFloat(get("min_lat"))
	maxLat, okB := parseFloat(get("max_lat"))
	minLng, okC := parseFloat(get("min_lng"))
	maxLng, okD := parseFloat(get("max_lng"))
	if okA && okB && okC && okD {
		f.MinLat, f.MaxLat, f.MinLng, f.MaxLng = &minLat, &maxLat, &minLng, &maxLng
	}
	if lat, ok := parseFloat(get("lat")); ok {
		if lng, ok2 := parseFloat(get("lng")); ok2 {
			f.NearLat, f.NearLng = &lat, &lng
			if radius, ok3 := parseFloat(get("radius_km")); ok3 && radius > 0 {
				// One degree of latitude is ~111km; longitude shrinks with latitude.
				// This bounding box is an approximation, not a true circle.
				dLat := radius / 111.0
				dLng := radius / (111.0 * math.Max(0.01, math.Cos(lat*math.Pi/180)))
				a, b := lat-dLat, lat+dLat
				c, d := lng-dLng, lng+dLng
				f.MinLat, f.MaxLat, f.MinLng, f.MaxLng = &a, &b, &c, &d
			}
			if f.Sort == "" {
				f.Sort = "distance"
			}
		}
	}

	// By default only active listings are browsable. Pass ?all=true or an
	// explicit ?status= to override.
	if f.Status == "" && get("all") != "true" {
		f.Status = "active"
	}
	return f
}

func parseFloat(s string) (float64, bool) {
	if s == "" {
		return 0, false
	}
	v, err := strconv.ParseFloat(s, 64)
	return v, err == nil
}

func (s *Server) handleListListings(w http.ResponseWriter, r *http.Request) {
	f := filterFromQuery(r.URL.Query())
	listings, total, err := s.listings.List(f)
	if err != nil {
		writeError(w, http.StatusInternalServerError, "could not list listings")
		return
	}
	writeJSON(w, http.StatusOK, listingsResponse{
		Listings: listings,
		Total:    total,
		Page:     f.Page,
		PageSize: f.PageSize,
	})
}

func (s *Server) handleGetListing(w http.ResponseWriter, r *http.Request) {
	l, err := s.listings.GetByID(r.PathValue("id"))
	if err != nil {
		if errors.Is(err, store.ErrNotFound) {
			writeError(w, http.StatusNotFound, "listing not found")
			return
		}
		writeError(w, http.StatusInternalServerError, "could not load listing")
		return
	}
	s.listings.IncrementViews(l.ID)
	writeJSON(w, http.StatusOK, l)
}

func (s *Server) handleCreateListing(w http.ResponseWriter, r *http.Request) {
	// Anyone signed in may post. The survey found 36% of respondents are
	// owners/sellers and nobody wanted an agents-only marketplace, so gating
	// posting behind an agent account was blocking the largest group of posters.
	uid := userIDFrom(r.Context())
	if _, err := s.users.GetByID(uid); err != nil {
		writeError(w, http.StatusUnauthorized, "account not found")
		return
	}

	var in listingInput
	if err := decodeJSON(w, r, &in); err != nil {
		writeError(w, http.StatusBadRequest, "invalid JSON body")
		return
	}

	l := &models.Listing{
		ID:                 uuid.NewString(),
		UserID:             uid,
		Currency:           "PHP",
		PropertyType:       "house",
		ListingType:        "sale",
		Status:             "active",
		VerificationStatus: models.VerificationPending,
	}
	if err := applyInput(l, &in); err != nil {
		writeError(w, http.StatusBadRequest, err.Error())
		return
	}
	if err := s.listings.Create(l); err != nil {
		writeError(w, http.StatusInternalServerError, "could not create listing")
		return
	}
	// Review happens off the request path; the listing is "pending" until then.
	s.worker.Enqueue(l.ID)

	full, err := s.listings.GetByID(l.ID)
	if err != nil {
		writeJSON(w, http.StatusCreated, l)
		return
	}
	writeJSON(w, http.StatusCreated, full)
}

func (s *Server) handleUpdateListing(w http.ResponseWriter, r *http.Request) {
	id := r.PathValue("id")
	userID := userIDFrom(r.Context())

	existing, err := s.listings.GetByID(id)
	if err != nil {
		if errors.Is(err, store.ErrNotFound) {
			writeError(w, http.StatusNotFound, "listing not found")
			return
		}
		writeError(w, http.StatusInternalServerError, "could not load listing")
		return
	}
	if existing.UserID != userID {
		writeError(w, http.StatusForbidden, "you do not own this listing")
		return
	}

	before := contentFingerprint(existing)

	var in listingInput
	if err := decodeJSON(w, r, &in); err != nil {
		writeError(w, http.StatusBadRequest, "invalid JSON body")
		return
	}
	if err := applyInput(existing, &in); err != nil {
		writeError(w, http.StatusBadRequest, err.Error())
		return
	}

	// Editing reviewed content sends the listing back through review, so a
	// verified badge can never be inherited by different content.
	reverify := contentFingerprint(existing) != before
	if err := s.listings.Update(existing, reverify); err != nil {
		writeError(w, http.StatusInternalServerError, "could not update listing")
		return
	}
	if reverify {
		s.worker.Enqueue(id)
	}

	full, _ := s.listings.GetByID(id)
	writeJSON(w, http.StatusOK, full)
}

// contentFingerprint captures the listing fields the reviewer judges, so an
// edit that changes only, say, status does not trigger a re-review.
func contentFingerprint(l *models.Listing) string {
	return fmt.Sprintf("%s|%s|%d|%s|%s|%d|%.1f|%d|%s|%s",
		l.Title, l.Description, l.Price, l.PropertyType, l.ListingType,
		l.Bedrooms, l.Bathrooms, l.AreaSqft, l.Address, l.City)
}

func (s *Server) handleDeleteListing(w http.ResponseWriter, r *http.Request) {
	id := r.PathValue("id")
	userID := userIDFrom(r.Context())

	existing, err := s.listings.GetByID(id)
	if err != nil {
		if errors.Is(err, store.ErrNotFound) {
			writeError(w, http.StatusNotFound, "listing not found")
			return
		}
		writeError(w, http.StatusInternalServerError, "could not load listing")
		return
	}
	if existing.UserID != userID {
		writeError(w, http.StatusForbidden, "you do not own this listing")
		return
	}
	if err := s.listings.Delete(id); err != nil {
		writeError(w, http.StatusInternalServerError, "could not delete listing")
		return
	}
	w.WriteHeader(http.StatusNoContent)
}

func (s *Server) handleMyListings(w http.ResponseWriter, r *http.Request) {
	userID := userIDFrom(r.Context())
	listings, total, err := s.listings.List(store.ListingFilter{
		UserID:          userID,
		IncludeRejected: true, // owners must see why their own listing was rejected
		NoFeaturedBoost: true, // your own listings read better in plain date order
		Page:            1,
		PageSize:        100,
	})
	if err != nil {
		writeError(w, http.StatusInternalServerError, "could not load your listings")
		return
	}
	writeJSON(w, http.StatusOK, listingsResponse{Listings: listings, Total: total, Page: 1, PageSize: 100})
}

// handleConfirmListing lets an owner mark a listing as still available, which
// clears the "not confirmed recently" warning buyers see on stale listings.
func (s *Server) handleConfirmListing(w http.ResponseWriter, r *http.Request) {
	id := r.PathValue("id")
	l, err := s.listings.GetByID(id)
	if err != nil {
		writeError(w, http.StatusNotFound, "listing not found")
		return
	}
	if l.UserID != userIDFrom(r.Context()) {
		writeError(w, http.StatusForbidden, "you do not own this listing")
		return
	}
	if err := s.listings.ConfirmAvailability(id); err != nil {
		writeError(w, http.StatusInternalServerError, "could not confirm listing")
		return
	}
	full, _ := s.listings.GetByID(id)
	writeJSON(w, http.StatusOK, full)
}

type reportInput struct {
	Reason  string `json:"reason"`
	Details string `json:"details"`
}

// handleReportListing records a scam / misleading-listing report. 57% of
// surveyed users had hit a scam, and none of the platforms they use let them
// do anything about it.
func (s *Server) handleReportListing(w http.ResponseWriter, r *http.Request) {
	id := r.PathValue("id")
	reporterID := userIDFrom(r.Context())

	listing, err := s.listings.GetByID(id)
	if err != nil {
		writeError(w, http.StatusNotFound, "listing not found")
		return
	}
	if listing.UserID == reporterID {
		writeError(w, http.StatusBadRequest, "you cannot report your own listing")
		return
	}

	var in reportInput
	if err := decodeJSON(w, r, &in); err != nil {
		writeError(w, http.StatusBadRequest, "invalid JSON body")
		return
	}
	in.Reason = strings.ToLower(strings.TrimSpace(in.Reason))
	if !contains(models.ReportReasons, in.Reason) {
		writeError(w, http.StatusBadRequest, "reason must be one of: "+strings.Join(models.ReportReasons, ", "))
		return
	}

	already, err := s.listings.HasReported(id, reporterID)
	if err != nil {
		writeError(w, http.StatusInternalServerError, "could not file report")
		return
	}
	if already {
		writeError(w, http.StatusConflict, "you have already reported this listing")
		return
	}

	report := &models.ListingReport{
		ID:         uuid.NewString(),
		ListingID:  id,
		ReporterID: reporterID,
		Reason:     in.Reason,
		Details:    strings.TrimSpace(in.Details),
	}
	if err := s.listings.AddReport(report); err != nil {
		writeError(w, http.StatusInternalServerError, "could not file report")
		return
	}
	// AddReport re-queues a listing once it passes the report threshold.
	s.worker.Enqueue(id)
	writeJSON(w, http.StatusCreated, report)
}

// handlePriceComparison answers "is this price reasonable?" — 46% of
// respondents asked for price comparison tools.
func (s *Server) handlePriceComparison(w http.ResponseWriter, r *http.Request) {
	l, err := s.listings.GetByID(r.PathValue("id"))
	if err != nil {
		writeError(w, http.StatusNotFound, "listing not found")
		return
	}
	pc, err := s.listings.PriceComparison(l)
	if err != nil {
		writeError(w, http.StatusInternalServerError, "could not build price comparison")
		return
	}
	writeJSON(w, http.StatusOK, pc)
}

func (s *Server) handleUploadImage(w http.ResponseWriter, r *http.Request) {
	id := r.PathValue("id")
	userID := userIDFrom(r.Context())

	listing, err := s.listings.GetByID(id)
	if err != nil {
		if errors.Is(err, store.ErrNotFound) {
			writeError(w, http.StatusNotFound, "listing not found")
			return
		}
		writeError(w, http.StatusInternalServerError, "could not load listing")
		return
	}
	if listing.UserID != userID {
		writeError(w, http.StatusForbidden, "you do not own this listing")
		return
	}

	// ParseMultipartForm's argument is a memory threshold, not a size limit —
	// without MaxBytesReader an arbitrarily large upload would spill to disk.
	limit := s.cfg.MaxUploadBytes
	r.Body = http.MaxBytesReader(w, r.Body, limit)
	if err := r.ParseMultipartForm(limit); err != nil {
		writeError(w, http.StatusRequestEntityTooLarge,
			fmt.Sprintf("upload too large (max %dMB)", limit>>20))
		return
	}
	file, header, err := r.FormFile("image")
	if err != nil {
		writeError(w, http.StatusBadRequest, "missing 'image' file field")
		return
	}
	defer file.Close()

	ext := strings.ToLower(filepath.Ext(header.Filename))
	if !allowedImageExt(ext) {
		writeError(w, http.StatusBadRequest, "unsupported image type (use jpg, png, webp, or gif)")
		return
	}

	// Check the bytes really are an image, not just the file name. Poor and
	// misleading photos were the third-biggest complaint in the survey.
	head := make([]byte, 512)
	n, _ := io.ReadFull(file, head)
	head = head[:n]
	if !strings.HasPrefix(http.DetectContentType(head), "image/") {
		writeError(w, http.StatusBadRequest, "that file is not a valid image")
		return
	}
	body := io.MultiReader(bytes.NewReader(head), file)

	filename := uuid.NewString() + ext
	url, err := s.uploads.Save(filename, body)
	if err != nil {
		writeError(w, http.StatusInternalServerError, "could not save image")
		return
	}

	img := &models.ListingImage{
		ID:        uuid.NewString(),
		ListingID: id,
		URL:       url,
	}
	if err := s.listings.AddImage(img); err != nil {
		writeError(w, http.StatusInternalServerError, "could not record image")
		return
	}
	// Photo count feeds the review, so a new photo is worth a fresh look.
	if err := s.listings.RequeueVerification(id); err == nil {
		s.worker.Enqueue(id)
	}
	writeJSON(w, http.StatusCreated, img)
}

// applyInput applies provided (non-nil) fields onto l, then validates the result.
func applyInput(l *models.Listing, in *listingInput) error {
	if in.Title != nil {
		l.Title = strings.TrimSpace(*in.Title)
	}
	if in.Description != nil {
		l.Description = strings.TrimSpace(*in.Description)
	}
	if in.Price != nil {
		l.Price = *in.Price
	}
	if in.Currency != nil && strings.TrimSpace(*in.Currency) != "" {
		l.Currency = strings.ToUpper(strings.TrimSpace(*in.Currency))
	}
	if in.PropertyType != nil {
		l.PropertyType = strings.ToLower(strings.TrimSpace(*in.PropertyType))
	}
	if in.ListingType != nil {
		l.ListingType = strings.ToLower(strings.TrimSpace(*in.ListingType))
	}
	if in.Bedrooms != nil {
		l.Bedrooms = *in.Bedrooms
	}
	if in.Bathrooms != nil {
		l.Bathrooms = *in.Bathrooms
	}
	if in.AreaSqft != nil {
		l.AreaSqft = *in.AreaSqft
	}
	if in.Address != nil {
		l.Address = strings.TrimSpace(*in.Address)
	}
	if in.City != nil {
		l.City = strings.TrimSpace(*in.City)
	}
	if in.State != nil {
		l.State = strings.TrimSpace(*in.State)
	}
	if in.ZipCode != nil {
		l.ZipCode = strings.TrimSpace(*in.ZipCode)
	}
	if in.Latitude != nil {
		v := *in.Latitude
		l.Latitude = &v
	}
	if in.Longitude != nil {
		v := *in.Longitude
		l.Longitude = &v
	}
	if in.Status != nil && strings.TrimSpace(*in.Status) != "" {
		l.Status = strings.ToLower(strings.TrimSpace(*in.Status))
	}

	if l.Title == "" {
		return errors.New("title is required")
	}
	if l.Price < 0 {
		return errors.New("price must be zero or positive")
	}
	if !contains(models.PropertyTypes, l.PropertyType) {
		return fmt.Errorf("property_type must be one of: %s", strings.Join(models.PropertyTypes, ", "))
	}
	if !contains(models.ListingTypes, l.ListingType) {
		return fmt.Errorf("listing_type must be one of: %s", strings.Join(models.ListingTypes, ", "))
	}
	if !contains(models.Statuses, l.Status) {
		return fmt.Errorf("status must be one of: %s", strings.Join(models.Statuses, ", "))
	}
	if l.Bedrooms < 0 || l.Bathrooms < 0 || l.AreaSqft < 0 {
		return errors.New("bedrooms, bathrooms, and area_sqft must be zero or positive")
	}
	if l.Latitude != nil && (*l.Latitude < -90 || *l.Latitude > 90) {
		return errors.New("latitude must be between -90 and 90")
	}
	if l.Longitude != nil && (*l.Longitude < -180 || *l.Longitude > 180) {
		return errors.New("longitude must be between -180 and 180")
	}
	return nil
}

// --- featured listings (paid promotion) ---

type featureInput struct {
	PlanID string `json:"plan_id"`
}

// handleFeaturePlans lists the promotion packages a poster can buy.
func (s *Server) handleFeaturePlans(w http.ResponseWriter, r *http.Request) {
	writeJSON(w, http.StatusOK, map[string]any{
		"plans":    models.FeaturePlans,
		"currency": "PHP",
		// Clients show this so nobody expects promotion to buy them a badge.
		"note": "Featured placement boosts where your listing appears. It does not affect verification, and a listing that hasn't passed screening is never promoted.",
	})
}

// handleFeatureListing activates paid promotion on the caller's listing.
//
// Promotion is sold per listing, not per month: the surveyed supply side is
// mostly private owners with a single property. Featured listings were the one
// unanimous ask among agents (6/6) and the top ask among owners (58%).
func (s *Server) handleFeatureListing(w http.ResponseWriter, r *http.Request) {
	// Until a gateway is wired up, this endpoint would hand out promotion for
	// free. That is fine for local development and a hard no in production.
	if s.cfg.Env == "production" && s.cfg.PaymentProvider == "" {
		writeError(w, http.StatusNotImplemented,
			"featured listings need a payment provider (set PAYMENT_PROVIDER)")
		return
	}

	id := r.PathValue("id")
	listing, err := s.listings.GetByID(id)
	if err != nil {
		writeError(w, http.StatusNotFound, "listing not found")
		return
	}
	if listing.UserID != userIDFrom(r.Context()) {
		writeError(w, http.StatusForbidden, "you do not own this listing")
		return
	}

	var in featureInput
	if err := decodeJSON(w, r, &in); err != nil {
		writeError(w, http.StatusBadRequest, "invalid JSON body")
		return
	}
	plan, ok := models.FeaturePlanByID(strings.TrimSpace(in.PlanID))
	if !ok {
		writeError(w, http.StatusBadRequest, "unknown plan_id")
		return
	}

	// Selling promotion on an unscreened listing would mean paying to amplify
	// something we have not checked — the exact behaviour that drove surveyed
	// users off other platforms.
	if listing.VerificationStatus != models.VerificationVerified {
		writeError(w, http.StatusConflict,
			"only verified listings can be promoted — wait for screening to finish, or fix the issues it found")
		return
	}

	// Buying again while a promotion is live extends it rather than restarting.
	from := time.Now()
	if listing.FeaturedUntil != nil && listing.FeaturedUntil.After(from) {
		from = *listing.FeaturedUntil
	}
	until := from.AddDate(0, 0, plan.Days)

	if err := s.listings.SetFeatured(id, until); err != nil {
		writeError(w, http.StatusInternalServerError, "could not promote listing")
		return
	}
	full, _ := s.listings.GetByID(id)
	writeJSON(w, http.StatusOK, map[string]any{
		"listing": full,
		"plan":    plan,
		"paid":    s.cfg.PaymentProvider != "",
	})
}

// --- photo management ---

// ownedListing loads a listing and confirms the caller owns it.
func (s *Server) ownedListing(w http.ResponseWriter, r *http.Request) (*models.Listing, bool) {
	listing, err := s.listings.GetByID(r.PathValue("id"))
	if err != nil {
		writeError(w, http.StatusNotFound, "listing not found")
		return nil, false
	}
	if listing.UserID != userIDFrom(r.Context()) {
		writeError(w, http.StatusForbidden, "you do not own this listing")
		return nil, false
	}
	return listing, true
}

// handleDeleteImage removes one photo. Photo count feeds screening, so removing
// one sends the listing back for review.
func (s *Server) handleDeleteImage(w http.ResponseWriter, r *http.Request) {
	listing, ok := s.ownedListing(w, r)
	if !ok {
		return
	}
	imageID := r.PathValue("imageId")
	if err := s.listings.DeleteImage(listing.ID, imageID); err != nil {
		if errors.Is(err, store.ErrNotFound) {
			writeError(w, http.StatusNotFound, "photo not found on this listing")
			return
		}
		writeError(w, http.StatusInternalServerError, "could not delete photo")
		return
	}
	if err := s.listings.RequeueVerification(listing.ID); err == nil {
		s.worker.Enqueue(listing.ID)
	}
	full, _ := s.listings.GetByID(listing.ID)
	writeJSON(w, http.StatusOK, full)
}

type reorderInput struct {
	ImageIDs []string `json:"image_ids"`
}

// handleReorderImages sets the photo order. The first photo is the card
// thumbnail, so being able to promote a better shot matters — "poor photos"
// was the third-biggest complaint in the survey.
func (s *Server) handleReorderImages(w http.ResponseWriter, r *http.Request) {
	listing, ok := s.ownedListing(w, r)
	if !ok {
		return
	}
	var in reorderInput
	if err := decodeJSON(w, r, &in); err != nil {
		writeError(w, http.StatusBadRequest, "invalid JSON body")
		return
	}
	if len(in.ImageIDs) == 0 {
		writeError(w, http.StatusBadRequest, "image_ids is required")
		return
	}
	// Every id must belong to this listing, or a caller could reshuffle someone
	// else's photos by guessing ids.
	for _, id := range in.ImageIDs {
		img, err := s.listings.ImageByID(id)
		if err != nil || img.ListingID != listing.ID {
			writeError(w, http.StatusBadRequest, "one of those photos is not on this listing")
			return
		}
	}
	if err := s.listings.ReorderImages(listing.ID, in.ImageIDs); err != nil {
		writeError(w, http.StatusInternalServerError, "could not reorder photos")
		return
	}
	// Reordering doesn't change what was reviewed, so the badge is kept.
	full, _ := s.listings.GetByID(listing.ID)
	writeJSON(w, http.StatusOK, full)
}
