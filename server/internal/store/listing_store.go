package store

import (
	"database/sql"
	"encoding/json"
	"sort"
	"strings"
	"time"

	"cabin/internal/models"
)

// ListingStore provides access to the listings and listing_images tables.
type ListingStore struct {
	db *sql.DB
}

func NewListingStore(db *sql.DB) *ListingStore {
	return &ListingStore{db: db}
}

// ListingFilter describes the query parameters for browsing/searching listings.
type ListingFilter struct {
	Query        string
	City         string
	PropertyType string
	ListingType  string
	Status       string
	UserID       string // restrict to a single owner
	MinPrice     *int64
	MaxPrice     *int64
	MinBedrooms  *int
	MinBathrooms *float64
	MinArea      *int

	// VerifiedOnly keeps only listings that passed review — the survey's most
	// requested feature (79% of respondents).
	VerifiedOnly bool
	// IncludeRejected surfaces listings the reviewer rejected. Off by default so
	// scam listings never appear in normal browsing; used by moderation views.
	IncludeRejected bool
	// ExcludeStale drops listings whose availability has not been confirmed
	// within models.StaleAfter.
	ExcludeStale bool

	// Bounding box for map search. All four must be set to take effect.
	MinLat, MaxLat, MinLng, MaxLng *float64
	// NearLat/NearLng enable distance ordering (Sort == "distance").
	NearLat, NearLng *float64

	// CreatedAfter restricts to listings posted after a moment — used by saved
	// search alerts to count new matches.
	CreatedAfter *time.Time

	Sort     string // "recent" (default), "oldest", "price_asc", "price_desc", "trusted", "distance"
	Page     int
	PageSize int
}

// listingColumns is the shared SELECT list joining the owner's public fields.
const listingColumns = `l.id, l.user_id, l.title, l.description, l.price, l.currency,
	l.property_type, l.listing_type, l.bedrooms, l.bathrooms, l.area_sqft,
	l.address, l.city, l.state, l.zip_code, l.latitude, l.longitude, l.status,
	l.verification_status, l.verification_score, l.verification_summary, l.verification_flags,
	l.verification_model, l.verified_at, l.last_confirmed_at, l.report_count, l.view_count,
	l.created_at, l.updated_at,
	u.name, u.email, u.role, u.verification_status, u.rating_avg, u.rating_count`

// Create inserts a new listing, stamping created/updated timestamps. New
// listings start in "pending" verification until the reviewer has seen them.
func (s *ListingStore) Create(l *models.Listing) error {
	now := time.Now().UTC()
	l.CreatedAt = now
	l.UpdatedAt = now
	if l.VerificationStatus == "" {
		l.VerificationStatus = models.VerificationPending
	}
	l.LastConfirmedAt = &now

	_, err := s.db.Exec(`INSERT INTO listings
		(id, user_id, title, description, price, currency, property_type, listing_type,
		 bedrooms, bathrooms, area_sqft, address, city, state, zip_code, latitude, longitude,
		 status, verification_status, last_confirmed_at, created_at, updated_at)
		VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)`,
		l.ID, l.UserID, l.Title, l.Description, l.Price, l.Currency, l.PropertyType, l.ListingType,
		l.Bedrooms, l.Bathrooms, l.AreaSqft, l.Address, l.City, l.State, l.ZipCode,
		nullFloat(l.Latitude), nullFloat(l.Longitude), l.Status, l.VerificationStatus,
		now.Format(time.RFC3339), l.CreatedAt.Format(time.RFC3339), l.UpdatedAt.Format(time.RFC3339),
	)
	return err
}

// GetByID returns a single listing with its owner summary and images.
func (s *ListingStore) GetByID(id string) (*models.Listing, error) {
	row := s.db.QueryRow(
		`SELECT `+listingColumns+` FROM listings l JOIN users u ON u.id = l.user_id WHERE l.id = ?`, id)
	l, err := scanListing(row)
	if err != nil {
		return nil, err
	}
	imgs, err := s.imagesFor([]string{l.ID})
	if err != nil {
		return nil, err
	}
	if list := imgs[l.ID]; list != nil {
		l.Images = list
	}
	return l, nil
}

// buildWhere turns a filter into a WHERE fragment and its arguments.
func (f ListingFilter) buildWhere() (string, []any) {
	where := []string{"1=1"}
	args := []any{}

	if f.Query != "" {
		where = append(where, "(l.title LIKE ? OR l.description LIKE ? OR l.city LIKE ? OR l.address LIKE ?)")
		like := "%" + f.Query + "%"
		args = append(args, like, like, like, like)
	}
	if f.City != "" {
		where = append(where, "l.city = ?")
		args = append(args, f.City)
	}
	if f.PropertyType != "" {
		where = append(where, "l.property_type = ?")
		args = append(args, f.PropertyType)
	}
	if f.ListingType != "" {
		where = append(where, "l.listing_type = ?")
		args = append(args, f.ListingType)
	}
	if f.Status != "" {
		where = append(where, "l.status = ?")
		args = append(args, f.Status)
	}
	if f.UserID != "" {
		where = append(where, "l.user_id = ?")
		args = append(args, f.UserID)
	}
	if f.MinPrice != nil {
		where = append(where, "l.price >= ?")
		args = append(args, *f.MinPrice)
	}
	if f.MaxPrice != nil {
		where = append(where, "l.price <= ?")
		args = append(args, *f.MaxPrice)
	}
	if f.MinBedrooms != nil {
		where = append(where, "l.bedrooms >= ?")
		args = append(args, *f.MinBedrooms)
	}
	if f.MinBathrooms != nil {
		where = append(where, "l.bathrooms >= ?")
		args = append(args, *f.MinBathrooms)
	}
	if f.MinArea != nil {
		where = append(where, "l.area_sqft >= ?")
		args = append(args, *f.MinArea)
	}

	// Trust filters.
	if f.VerifiedOnly {
		where = append(where, "l.verification_status = ?")
		args = append(args, models.VerificationVerified)
	} else if !f.IncludeRejected {
		// Rejected listings are hidden from ordinary browsing.
		where = append(where, "l.verification_status <> ?")
		args = append(args, models.VerificationRejected)
	}
	if f.ExcludeStale {
		cutoff := time.Now().UTC().Add(-models.StaleAfter).Format(time.RFC3339)
		// Fall back to created_at for listings that were never confirmed.
		where = append(where, "(CASE WHEN l.last_confirmed_at <> '' THEN l.last_confirmed_at ELSE l.created_at END) >= ?")
		args = append(args, cutoff)
	}

	// Map bounding box.
	if f.MinLat != nil && f.MaxLat != nil && f.MinLng != nil && f.MaxLng != nil {
		where = append(where, "l.latitude IS NOT NULL AND l.longitude IS NOT NULL")
		where = append(where, "l.latitude BETWEEN ? AND ? AND l.longitude BETWEEN ? AND ?")
		args = append(args, *f.MinLat, *f.MaxLat, *f.MinLng, *f.MaxLng)
	}

	if f.CreatedAfter != nil {
		where = append(where, "l.created_at > ?")
		args = append(args, f.CreatedAfter.UTC().Format(time.RFC3339))
	}

	return strings.Join(where, " AND "), args
}

// orderBy renders the ORDER BY clause and any arguments it needs.
func (f ListingFilter) orderBy() (string, []any) {
	switch f.Sort {
	case "oldest":
		return "l.created_at ASC", nil
	case "price_asc":
		return "l.price ASC", nil
	case "price_desc":
		return "l.price DESC", nil
	case "trusted":
		// Most-trustworthy first, then most recent.
		return "l.verification_score DESC, l.created_at DESC", nil
	case "distance":
		if f.NearLat != nil && f.NearLng != nil {
			// Squared planar distance: a good ordering approximation at city scale
			// and portable across SQLite and MySQL.
			expr := "((l.latitude - ?) * (l.latitude - ?) + (l.longitude - ?) * (l.longitude - ?)) ASC"
			return expr, []any{*f.NearLat, *f.NearLat, *f.NearLng, *f.NearLng}
		}
		return "l.created_at DESC", nil
	default:
		return "l.created_at DESC", nil
	}
}

// List returns a page of listings matching the filter, plus the total count
// (ignoring pagination) for building pagination UI.
func (s *ListingStore) List(f ListingFilter) ([]models.Listing, int, error) {
	whereSQL, args := f.buildWhere()

	var total int
	if err := s.db.QueryRow(`SELECT COUNT(*) FROM listings l WHERE `+whereSQL, args...).Scan(&total); err != nil {
		return nil, 0, err
	}

	order, orderArgs := f.orderBy()

	page := f.Page
	if page < 1 {
		page = 1
	}
	size := f.PageSize
	if size < 1 || size > 100 {
		size = 20
	}
	offset := (page - 1) * size

	q := `SELECT ` + listingColumns + ` FROM listings l JOIN users u ON u.id = l.user_id
		WHERE ` + whereSQL + ` ORDER BY ` + order + ` LIMIT ? OFFSET ?`
	qArgs := append(append([]any{}, args...), orderArgs...)
	qArgs = append(qArgs, size, offset)

	rows, err := s.db.Query(q, qArgs...)
	if err != nil {
		return nil, 0, err
	}
	defer rows.Close()

	listings := []models.Listing{}
	var ids []string
	for rows.Next() {
		l, err := scanListing(rows)
		if err != nil {
			return nil, 0, err
		}
		listings = append(listings, *l)
		ids = append(ids, l.ID)
	}
	if err := rows.Err(); err != nil {
		return nil, 0, err
	}
	// rows are fully drained here, releasing the single DB connection before
	// we run the images query below.

	imgs, err := s.imagesFor(ids)
	if err != nil {
		return nil, 0, err
	}
	for i := range listings {
		if list := imgs[listings[i].ID]; list != nil {
			listings[i].Images = list
		}
	}
	return listings, total, nil
}

// Count returns how many listings match a filter, without loading them.
func (s *ListingStore) Count(f ListingFilter) (int, error) {
	whereSQL, args := f.buildWhere()
	var n int
	err := s.db.QueryRow(`SELECT COUNT(*) FROM listings l WHERE `+whereSQL, args...).Scan(&n)
	return n, err
}

// Update writes all mutable fields of an existing listing. Editing a listing
// sends it back for review, since the reviewed content has changed.
func (s *ListingStore) Update(l *models.Listing, reverify bool) error {
	l.UpdatedAt = time.Now().UTC()
	status := l.VerificationStatus
	if reverify {
		status = models.VerificationPending
		l.VerificationStatus = status
	}
	res, err := s.db.Exec(`UPDATE listings SET
		title=?, description=?, price=?, currency=?, property_type=?, listing_type=?,
		bedrooms=?, bathrooms=?, area_sqft=?, address=?, city=?, state=?, zip_code=?,
		latitude=?, longitude=?, status=?, verification_status=?, updated_at=?
		WHERE id=?`,
		l.Title, l.Description, l.Price, l.Currency, l.PropertyType, l.ListingType,
		l.Bedrooms, l.Bathrooms, l.AreaSqft, l.Address, l.City, l.State, l.ZipCode,
		nullFloat(l.Latitude), nullFloat(l.Longitude), l.Status, status,
		l.UpdatedAt.Format(time.RFC3339), l.ID,
	)
	if err != nil {
		return err
	}
	if n, _ := res.RowsAffected(); n == 0 {
		return ErrNotFound
	}
	return nil
}

// Delete removes a listing (and, via ON DELETE CASCADE, its images).
func (s *ListingStore) Delete(id string) error {
	res, err := s.db.Exec(`DELETE FROM listings WHERE id = ?`, id)
	if err != nil {
		return err
	}
	if n, _ := res.RowsAffected(); n == 0 {
		return ErrNotFound
	}
	return nil
}

// ConfirmAvailability records that the owner says the listing is still
// available, clearing the "stale" marker buyers complained about.
func (s *ListingStore) ConfirmAvailability(id string) error {
	res, err := s.db.Exec(`UPDATE listings SET last_confirmed_at = ?, updated_at = ? WHERE id = ?`,
		time.Now().UTC().Format(time.RFC3339), time.Now().UTC().Format(time.RFC3339), id)
	if err != nil {
		return err
	}
	if n, _ := res.RowsAffected(); n == 0 {
		return ErrNotFound
	}
	return nil
}

// IncrementViews bumps the view counter. Failures are not worth surfacing.
func (s *ListingStore) IncrementViews(id string) {
	_, _ = s.db.Exec(`UPDATE listings SET view_count = view_count + 1 WHERE id = ?`, id)
}

// --- verification ---

// PendingVerification returns listings waiting for review, oldest first.
func (s *ListingStore) PendingVerification(limit int) ([]models.Listing, error) {
	if limit <= 0 {
		limit = 20
	}
	f := ListingFilter{Status: "", Page: 1, PageSize: limit, Sort: "oldest", IncludeRejected: true}
	whereSQL, args := f.buildWhere()
	whereSQL += " AND l.verification_status = ?"
	args = append(args, models.VerificationPending)

	rows, err := s.db.Query(`SELECT `+listingColumns+` FROM listings l JOIN users u ON u.id = l.user_id
		WHERE `+whereSQL+` ORDER BY l.created_at ASC LIMIT ?`, append(args, limit)...)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	out := []models.Listing{}
	var ids []string
	for rows.Next() {
		l, err := scanListing(rows)
		if err != nil {
			return nil, err
		}
		out = append(out, *l)
		ids = append(ids, l.ID)
	}
	if err := rows.Err(); err != nil {
		return nil, err
	}
	// The reviewer weighs photo count, so images must be loaded.
	imgs, err := s.imagesFor(ids)
	if err != nil {
		return nil, err
	}
	for i := range out {
		if list := imgs[out[i].ID]; list != nil {
			out[i].Images = list
		}
	}
	return out, nil
}

// ApplyVerification stores a reviewer's verdict for a listing.
func (s *ListingStore) ApplyVerification(listingID, status string, score int, summary string, flags []string, model string) error {
	if flags == nil {
		flags = []string{}
	}
	encoded, err := json.Marshal(flags)
	if err != nil {
		return err
	}
	verifiedAt := ""
	if status == models.VerificationVerified {
		verifiedAt = time.Now().UTC().Format(time.RFC3339)
	}
	res, err := s.db.Exec(`UPDATE listings SET
		verification_status=?, verification_score=?, verification_summary=?,
		verification_flags=?, verification_model=?, verified_at=?
		WHERE id=?`,
		status, score, summary, string(encoded), model, verifiedAt, listingID)
	if err != nil {
		return err
	}
	if n, _ := res.RowsAffected(); n == 0 {
		return ErrNotFound
	}
	return nil
}

// RequeueVerification puts a listing back in the review queue.
func (s *ListingStore) RequeueVerification(listingID string) error {
	_, err := s.db.Exec(`UPDATE listings SET verification_status = ? WHERE id = ?`,
		models.VerificationPending, listingID)
	return err
}

// --- reports ---

// AddReport records a scam / misleading-listing report and bumps the listing's
// report counter. Three open reports send a listing back for re-review.
func (s *ListingStore) AddReport(r *models.ListingReport) error {
	r.CreatedAt = time.Now().UTC()
	if r.Status == "" {
		r.Status = "open"
	}
	if _, err := s.db.Exec(
		`INSERT INTO listing_reports (id, listing_id, reporter_id, reason, details, status, created_at)
		 VALUES (?,?,?,?,?,?,?)`,
		r.ID, r.ListingID, r.ReporterID, r.Reason, r.Details, r.Status, r.CreatedAt.Format(time.RFC3339),
	); err != nil {
		return err
	}
	if _, err := s.db.Exec(
		`UPDATE listings SET report_count = report_count + 1 WHERE id = ?`, r.ListingID); err != nil {
		return err
	}

	var open int
	if err := s.db.QueryRow(
		`SELECT COUNT(*) FROM listing_reports WHERE listing_id = ? AND status = 'open'`,
		r.ListingID).Scan(&open); err != nil {
		return err
	}
	if open >= 3 {
		return s.RequeueVerification(r.ListingID)
	}
	return nil
}

// HasReported reports whether a user already filed a report on a listing.
func (s *ListingStore) HasReported(listingID, reporterID string) (bool, error) {
	var n int
	err := s.db.QueryRow(
		`SELECT COUNT(*) FROM listing_reports WHERE listing_id = ? AND reporter_id = ?`,
		listingID, reporterID).Scan(&n)
	return n > 0, err
}

// Reports lists reports, newest first, optionally filtered by status.
func (s *ListingStore) Reports(status string, limit int) ([]models.ListingReport, error) {
	if limit <= 0 || limit > 200 {
		limit = 50
	}
	q := `SELECT r.id, r.listing_id, r.reporter_id, r.reason, r.details, r.status, r.resolution,
		r.created_at, r.resolved_at, u.name, u.email, u.role, u.verification_status, u.rating_avg, u.rating_count
		FROM listing_reports r JOIN users u ON u.id = r.reporter_id`
	args := []any{}
	if status != "" {
		q += ` WHERE r.status = ?`
		args = append(args, status)
	}
	q += ` ORDER BY r.created_at DESC LIMIT ?`
	args = append(args, limit)

	rows, err := s.db.Query(q, args...)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	out := []models.ListingReport{}
	for rows.Next() {
		var r models.ListingReport
		var reporter models.UserSummary
		var created, resolved string
		if err := rows.Scan(&r.ID, &r.ListingID, &r.ReporterID, &r.Reason, &r.Details, &r.Status,
			&r.Resolution, &created, &resolved, &reporter.Name, &reporter.Email, &reporter.Role,
			&reporter.VerificationStatus, &reporter.RatingAvg, &reporter.RatingCount); err != nil {
			return nil, err
		}
		r.CreatedAt, _ = time.Parse(time.RFC3339, created)
		r.ResolvedAt = parseOptionalTime(resolved)
		reporter.ID = r.ReporterID
		r.Reporter = &reporter
		out = append(out, r)
	}
	return out, rows.Err()
}

// ResolveReport closes a report with an outcome.
func (s *ListingStore) ResolveReport(reportID, status, resolution string) error {
	res, err := s.db.Exec(`UPDATE listing_reports SET status = ?, resolution = ?, resolved_at = ? WHERE id = ?`,
		status, resolution, time.Now().UTC().Format(time.RFC3339), reportID)
	if err != nil {
		return err
	}
	if n, _ := res.RowsAffected(); n == 0 {
		return ErrNotFound
	}
	return nil
}

// --- price comparison ---

// Comparables returns listings similar to l — same city, listing type and
// property type, within a bedroom of it — for the price comparison view.
func (s *ListingStore) Comparables(l *models.Listing, limit int) ([]models.Listing, error) {
	if limit <= 0 || limit > 50 {
		limit = 12
	}
	rows, err := s.db.Query(`SELECT `+listingColumns+`
		FROM listings l JOIN users u ON u.id = l.user_id
		WHERE l.id <> ? AND l.city = ? AND l.listing_type = ? AND l.property_type = ?
		  AND l.status = 'active' AND l.verification_status <> ? AND l.price > 0
		  AND l.bedrooms BETWEEN ? AND ?
		ORDER BY l.created_at DESC LIMIT ?`,
		l.ID, l.City, l.ListingType, l.PropertyType, models.VerificationRejected,
		l.Bedrooms-1, l.Bedrooms+1, limit)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	out := []models.Listing{}
	for rows.Next() {
		c, err := scanListing(rows)
		if err != nil {
			return nil, err
		}
		out = append(out, *c)
	}
	return out, rows.Err()
}

// PriceComparison summarises how a listing's price compares to its comparables.
func (s *ListingStore) PriceComparison(l *models.Listing) (*models.PriceComparison, error) {
	comps, err := s.Comparables(l, 25)
	if err != nil {
		return nil, err
	}
	pc := &models.PriceComparison{
		ListingID:   l.ID,
		Price:       l.Price,
		SampleSize:  len(comps),
		Comparables: comps,
		Verdict:     "insufficient_data",
	}
	if l.AreaSqft > 0 {
		pc.PricePerSqft = float64(l.Price) / float64(l.AreaSqft)
	}
	// Fewer than three comparables cannot support a market claim.
	if len(comps) < 3 {
		return pc, nil
	}

	prices := make([]int64, 0, len(comps))
	perSqft := make([]float64, 0, len(comps))
	for i := range comps {
		prices = append(prices, comps[i].Price)
		if comps[i].AreaSqft > 0 {
			perSqft = append(perSqft, float64(comps[i].Price)/float64(comps[i].AreaSqft))
		}
	}
	sort.Slice(prices, func(a, b int) bool { return prices[a] < prices[b] })
	pc.Min = prices[0]
	pc.Max = prices[len(prices)-1]
	pc.Median = medianInt64(prices)
	pc.MedianPerSqft = medianFloat(perSqft)

	if pc.Median > 0 {
		pc.PercentDiff = (float64(l.Price) - float64(pc.Median)) / float64(pc.Median) * 100
		switch {
		case pc.PercentDiff <= -15:
			pc.Verdict = "below_market"
		case pc.PercentDiff >= 15:
			pc.Verdict = "above_market"
		default:
			pc.Verdict = "at_market"
		}
	}
	return pc, nil
}

func medianInt64(v []int64) int64 {
	if len(v) == 0 {
		return 0
	}
	mid := len(v) / 2
	if len(v)%2 == 1 {
		return v[mid]
	}
	return (v[mid-1] + v[mid]) / 2
}

func medianFloat(v []float64) float64 {
	if len(v) == 0 {
		return 0
	}
	sort.Float64s(v)
	mid := len(v) / 2
	if len(v)%2 == 1 {
		return v[mid]
	}
	return (v[mid-1] + v[mid]) / 2
}

// --- images ---

// AddImage attaches an image to a listing, appending it after existing images.
func (s *ListingStore) AddImage(img *models.ListingImage) error {
	img.CreatedAt = time.Now().UTC()

	var count int
	if err := s.db.QueryRow(
		`SELECT COUNT(*) FROM listing_images WHERE listing_id = ?`, img.ListingID).Scan(&count); err != nil {
		return err
	}
	img.Position = count

	_, err := s.db.Exec(
		`INSERT INTO listing_images (id, listing_id, url, sort_order, created_at) VALUES (?,?,?,?,?)`,
		img.ID, img.ListingID, img.URL, img.Position, img.CreatedAt.Format(time.RFC3339),
	)
	return err
}

// CountAll returns the total number of listings (used to decide whether to seed).
func (s *ListingStore) CountAll() (int, error) {
	var n int
	err := s.db.QueryRow(`SELECT COUNT(*) FROM listings`).Scan(&n)
	return n, err
}

// imagesFor returns images grouped by listing id, in one query to avoid N+1.
func (s *ListingStore) imagesFor(ids []string) (map[string][]models.ListingImage, error) {
	result := map[string][]models.ListingImage{}
	if len(ids) == 0 {
		return result, nil
	}

	placeholders := make([]string, len(ids))
	args := make([]any, len(ids))
	for i, id := range ids {
		placeholders[i] = "?"
		args[i] = id
	}

	q := `SELECT id, listing_id, url, sort_order, created_at FROM listing_images
		WHERE listing_id IN (` + strings.Join(placeholders, ",") + `)
		ORDER BY sort_order ASC, created_at ASC`

	rows, err := s.db.Query(q, args...)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	for rows.Next() {
		var img models.ListingImage
		var created string
		if err := rows.Scan(&img.ID, &img.ListingID, &img.URL, &img.Position, &created); err != nil {
			return nil, err
		}
		img.CreatedAt, _ = time.Parse(time.RFC3339, created)
		result[img.ListingID] = append(result[img.ListingID], img)
	}
	return result, rows.Err()
}

func scanListing(sc rowScanner) (*models.Listing, error) {
	var l models.Listing
	var lat, lng sql.NullFloat64
	var created, updated, verifiedAt, lastConfirmed, flagsJSON string
	var ownerName, ownerEmail, ownerRole, ownerVerification string
	var ownerRating float64
	var ownerRatingCount int

	err := sc.Scan(
		&l.ID, &l.UserID, &l.Title, &l.Description, &l.Price, &l.Currency,
		&l.PropertyType, &l.ListingType, &l.Bedrooms, &l.Bathrooms, &l.AreaSqft,
		&l.Address, &l.City, &l.State, &l.ZipCode, &lat, &lng, &l.Status,
		&l.VerificationStatus, &l.VerificationScore, &l.VerificationSummary, &flagsJSON,
		&l.VerificationModel, &verifiedAt, &lastConfirmed, &l.ReportCount, &l.ViewCount,
		&created, &updated,
		&ownerName, &ownerEmail, &ownerRole, &ownerVerification, &ownerRating, &ownerRatingCount,
	)
	if err != nil {
		if err == sql.ErrNoRows {
			return nil, ErrNotFound
		}
		return nil, err
	}

	if lat.Valid {
		v := lat.Float64
		l.Latitude = &v
	}
	if lng.Valid {
		v := lng.Float64
		l.Longitude = &v
	}
	l.CreatedAt, _ = time.Parse(time.RFC3339, created)
	l.UpdatedAt, _ = time.Parse(time.RFC3339, updated)
	l.VerifiedAt = parseOptionalTime(verifiedAt)
	l.LastConfirmedAt = parseOptionalTime(lastConfirmed)

	l.VerificationFlags = []string{}
	if flagsJSON != "" {
		_ = json.Unmarshal([]byte(flagsJSON), &l.VerificationFlags)
		if l.VerificationFlags == nil {
			l.VerificationFlags = []string{}
		}
	}

	l.Owner = &models.UserSummary{
		ID:                 l.UserID,
		Name:               ownerName,
		Email:              ownerEmail,
		Role:               ownerRole,
		VerificationStatus: ownerVerification,
		RatingAvg:          ownerRating,
		RatingCount:        ownerRatingCount,
	}
	l.Images = []models.ListingImage{}
	return &l, nil
}

// parseOptionalTime turns a stored timestamp into a pointer, treating the empty
// string (our "not set" marker) as nil.
func parseOptionalTime(s string) *time.Time {
	if strings.TrimSpace(s) == "" {
		return nil
	}
	t, err := time.Parse(time.RFC3339, s)
	if err != nil {
		return nil
	}
	return &t
}

func nullFloat(f *float64) any {
	if f == nil {
		return nil
	}
	return *f
}
