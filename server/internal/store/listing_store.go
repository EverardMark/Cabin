package store

import (
	"database/sql"
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
	Sort         string // "recent" (default), "oldest", "price_asc", "price_desc"
	Page         int
	PageSize     int
}

// listingColumns is the shared SELECT list joining the owner's public fields.
const listingColumns = `l.id, l.user_id, l.title, l.description, l.price, l.currency,
	l.property_type, l.listing_type, l.bedrooms, l.bathrooms, l.area_sqft,
	l.address, l.city, l.state, l.zip_code, l.latitude, l.longitude, l.status,
	l.created_at, l.updated_at, u.name, u.email, u.role`

// Create inserts a new listing, stamping created/updated timestamps.
func (s *ListingStore) Create(l *models.Listing) error {
	now := time.Now().UTC()
	l.CreatedAt = now
	l.UpdatedAt = now
	_, err := s.db.Exec(`INSERT INTO listings
		(id, user_id, title, description, price, currency, property_type, listing_type,
		 bedrooms, bathrooms, area_sqft, address, city, state, zip_code, latitude, longitude,
		 status, created_at, updated_at)
		VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)`,
		l.ID, l.UserID, l.Title, l.Description, l.Price, l.Currency, l.PropertyType, l.ListingType,
		l.Bedrooms, l.Bathrooms, l.AreaSqft, l.Address, l.City, l.State, l.ZipCode,
		nullFloat(l.Latitude), nullFloat(l.Longitude), l.Status,
		l.CreatedAt.Format(time.RFC3339), l.UpdatedAt.Format(time.RFC3339),
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

// List returns a page of listings matching the filter, plus the total count
// (ignoring pagination) for building pagination UI.
func (s *ListingStore) List(f ListingFilter) ([]models.Listing, int, error) {
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

	whereSQL := strings.Join(where, " AND ")

	var total int
	if err := s.db.QueryRow(`SELECT COUNT(*) FROM listings l WHERE `+whereSQL, args...).Scan(&total); err != nil {
		return nil, 0, err
	}

	order := "l.created_at DESC"
	switch f.Sort {
	case "oldest":
		order = "l.created_at ASC"
	case "price_asc":
		order = "l.price ASC"
	case "price_desc":
		order = "l.price DESC"
	}

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
	qArgs := append(append([]any{}, args...), size, offset)

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

// Update writes all mutable fields of an existing listing.
func (s *ListingStore) Update(l *models.Listing) error {
	l.UpdatedAt = time.Now().UTC()
	res, err := s.db.Exec(`UPDATE listings SET
		title=?, description=?, price=?, currency=?, property_type=?, listing_type=?,
		bedrooms=?, bathrooms=?, area_sqft=?, address=?, city=?, state=?, zip_code=?,
		latitude=?, longitude=?, status=?, updated_at=?
		WHERE id=?`,
		l.Title, l.Description, l.Price, l.Currency, l.PropertyType, l.ListingType,
		l.Bedrooms, l.Bathrooms, l.AreaSqft, l.Address, l.City, l.State, l.ZipCode,
		nullFloat(l.Latitude), nullFloat(l.Longitude), l.Status, l.UpdatedAt.Format(time.RFC3339),
		l.ID,
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

// Count returns the total number of listings (used to decide whether to seed).
func (s *ListingStore) Count() (int, error) {
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
	var created, updated, ownerName, ownerEmail, ownerRole string

	err := sc.Scan(
		&l.ID, &l.UserID, &l.Title, &l.Description, &l.Price, &l.Currency,
		&l.PropertyType, &l.ListingType, &l.Bedrooms, &l.Bathrooms, &l.AreaSqft,
		&l.Address, &l.City, &l.State, &l.ZipCode, &lat, &lng, &l.Status,
		&created, &updated, &ownerName, &ownerEmail, &ownerRole,
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
	l.Owner = &models.UserSummary{ID: l.UserID, Name: ownerName, Email: ownerEmail, Role: ownerRole}
	l.Images = []models.ListingImage{}
	return &l, nil
}

func nullFloat(f *float64) any {
	if f == nil {
		return nil
	}
	return *f
}
