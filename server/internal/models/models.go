package models

import "time"

// PropertyType and ListingType allowed values.
var (
	PropertyTypes = []string{"house", "apartment", "condo", "townhouse", "land"}
	ListingTypes  = []string{"sale", "rent"}
	Statuses      = []string{"active", "pending", "sold", "rented", "inactive"}
)

// User is a registered account. PasswordHash is never serialized to JSON.
type User struct {
	ID           string    `json:"id"`
	Email        string    `json:"email"`
	Name         string    `json:"name"`
	PasswordHash string    `json:"-"`
	CreatedAt    time.Time `json:"created_at"`
}

// UserSummary is the public subset of a user, embedded in listings.
type UserSummary struct {
	ID    string `json:"id"`
	Name  string `json:"name"`
	Email string `json:"email"`
}

// Listing is a real estate posting.
type Listing struct {
	ID           string         `json:"id"`
	UserID       string         `json:"user_id"`
	Title        string         `json:"title"`
	Description  string         `json:"description"`
	Price        int64          `json:"price"` // whole currency units (e.g. dollars)
	Currency     string         `json:"currency"`
	PropertyType string         `json:"property_type"`
	ListingType  string         `json:"listing_type"`
	Bedrooms     int            `json:"bedrooms"`
	Bathrooms    float64        `json:"bathrooms"`
	AreaSqft     int            `json:"area_sqft"`
	Address      string         `json:"address"`
	City         string         `json:"city"`
	State        string         `json:"state"`
	ZipCode      string         `json:"zip_code"`
	Latitude     *float64       `json:"latitude,omitempty"`
	Longitude    *float64       `json:"longitude,omitempty"`
	Status       string         `json:"status"`
	Images       []ListingImage `json:"images"`
	Owner        *UserSummary   `json:"owner,omitempty"`
	CreatedAt    time.Time      `json:"created_at"`
	UpdatedAt    time.Time      `json:"updated_at"`
}

// ListingImage is a photo attached to a listing.
type ListingImage struct {
	ID        string    `json:"id"`
	ListingID string    `json:"listing_id"`
	URL       string    `json:"url"`
	Position  int       `json:"position"`
	CreatedAt time.Time `json:"created_at"`
}

// Summary returns the public view of the user.
func (u *User) Summary() UserSummary {
	return UserSummary{ID: u.ID, Name: u.Name, Email: u.Email}
}
