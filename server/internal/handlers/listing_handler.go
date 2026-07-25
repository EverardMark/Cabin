package handlers

import (
	"errors"
	"fmt"
	"net/http"
	"path/filepath"
	"strconv"
	"strings"

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

func (s *Server) handleListListings(w http.ResponseWriter, r *http.Request) {
	q := r.URL.Query()

	page := atoiDefault(q.Get("page"), 1)
	if page < 1 {
		page = 1
	}
	size := atoiDefault(q.Get("page_size"), 20)
	if size < 1 || size > 100 {
		size = 20
	}

	f := store.ListingFilter{
		Query:        strings.TrimSpace(q.Get("q")),
		City:         strings.TrimSpace(q.Get("city")),
		PropertyType: strings.TrimSpace(q.Get("property_type")),
		ListingType:  strings.TrimSpace(q.Get("listing_type")),
		Status:       strings.TrimSpace(q.Get("status")),
		Sort:         q.Get("sort"),
		Page:         page,
		PageSize:     size,
	}
	if v := q.Get("min_price"); v != "" {
		if n, err := strconv.ParseInt(v, 10, 64); err == nil {
			f.MinPrice = &n
		}
	}
	if v := q.Get("max_price"); v != "" {
		if n, err := strconv.ParseInt(v, 10, 64); err == nil {
			f.MaxPrice = &n
		}
	}
	if v := q.Get("min_bedrooms"); v != "" {
		if n, err := strconv.Atoi(v); err == nil {
			f.MinBedrooms = &n
		}
	}

	// By default only active listings are browsable. Pass ?all=true or an
	// explicit ?status= to override.
	if f.Status == "" && q.Get("all") != "true" {
		f.Status = "active"
	}

	listings, total, err := s.listings.List(f)
	if err != nil {
		writeError(w, http.StatusInternalServerError, "could not list listings")
		return
	}
	writeJSON(w, http.StatusOK, listingsResponse{
		Listings: listings,
		Total:    total,
		Page:     page,
		PageSize: size,
	})
}

func (s *Server) handleGetListing(w http.ResponseWriter, r *http.Request) {
	l, err := s.listings.GetByID(r.PathValue("id"))
	if err != nil {
		if err == store.ErrNotFound {
			writeError(w, http.StatusNotFound, "listing not found")
			return
		}
		writeError(w, http.StatusInternalServerError, "could not load listing")
		return
	}
	writeJSON(w, http.StatusOK, l)
}

func (s *Server) handleCreateListing(w http.ResponseWriter, r *http.Request) {
	var in listingInput
	if err := decodeJSON(w, r, &in); err != nil {
		writeError(w, http.StatusBadRequest, "invalid JSON body")
		return
	}

	l := &models.Listing{
		ID:           uuid.NewString(),
		UserID:       userIDFrom(r.Context()),
		Currency:     "USD",
		PropertyType: "house",
		ListingType:  "sale",
		Status:       "active",
	}
	if err := applyInput(l, &in); err != nil {
		writeError(w, http.StatusBadRequest, err.Error())
		return
	}
	if err := s.listings.Create(l); err != nil {
		writeError(w, http.StatusInternalServerError, "could not create listing")
		return
	}

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
		if err == store.ErrNotFound {
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

	var in listingInput
	if err := decodeJSON(w, r, &in); err != nil {
		writeError(w, http.StatusBadRequest, "invalid JSON body")
		return
	}
	if err := applyInput(existing, &in); err != nil {
		writeError(w, http.StatusBadRequest, err.Error())
		return
	}
	if err := s.listings.Update(existing); err != nil {
		writeError(w, http.StatusInternalServerError, "could not update listing")
		return
	}

	full, _ := s.listings.GetByID(id)
	writeJSON(w, http.StatusOK, full)
}

func (s *Server) handleDeleteListing(w http.ResponseWriter, r *http.Request) {
	id := r.PathValue("id")
	userID := userIDFrom(r.Context())

	existing, err := s.listings.GetByID(id)
	if err != nil {
		if err == store.ErrNotFound {
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
		UserID:   userID,
		Page:     1,
		PageSize: 100,
	})
	if err != nil {
		writeError(w, http.StatusInternalServerError, "could not load your listings")
		return
	}
	writeJSON(w, http.StatusOK, listingsResponse{Listings: listings, Total: total, Page: 1, PageSize: 100})
}

func (s *Server) handleUploadImage(w http.ResponseWriter, r *http.Request) {
	id := r.PathValue("id")
	userID := userIDFrom(r.Context())

	listing, err := s.listings.GetByID(id)
	if err != nil {
		if err == store.ErrNotFound {
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

	if err := r.ParseMultipartForm(10 << 20); err != nil {
		writeError(w, http.StatusBadRequest, "could not parse upload (max 10MB)")
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

	filename := uuid.NewString() + ext
	url, err := s.uploads.Save(filename, file)
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
	return nil
}
