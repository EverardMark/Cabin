package models

import "time"

// Allowed enum values for listing fields.
var (
	PropertyTypes = []string{"house", "apartment", "condo", "townhouse", "land"}
	ListingTypes  = []string{"sale", "rent"}
	Statuses      = []string{"active", "pending", "sold", "rented", "inactive"}
	// Roles: "user" covers owners, buyers and renters — all of whom may post,
	// since 36% of surveyed respondents are owners/sellers and nobody wanted an
	// agents-only marketplace. "agent" marks a real estate professional (an
	// extra badge, not extra posting rights); "admin" moderates reports.
	Roles = []string{"user", "agent", "admin"}

	// ReportReasons are the accepted values for a listing report.
	ReportReasons = []string{"fake_listing", "scam", "wrong_price", "already_taken", "misleading_photos", "duplicate", "offensive", "other"}
)

const (
	// RoleAgent marks a real estate professional.
	RoleAgent = "agent"
	// RoleAdmin may moderate reports and override verification decisions.
	RoleAdmin = "admin"
)

// Verification states shared by users and listings.
const (
	VerificationUnverified = "unverified" // users only: never submitted
	VerificationPending    = "pending"    // awaiting automated review
	VerificationVerified   = "verified"   // passed review
	VerificationFlagged    = "flagged"    // passed with concerns; shown with a warning
	VerificationRejected   = "rejected"   // failed review; hidden from default browse
)

// StaleAfter is how long a listing may go unconfirmed before it is treated as
// stale. "Outdated listings" was one of the most common free-text complaints.
const StaleAfter = 30 * 24 * time.Hour

// User is a registered account. PasswordHash is never serialized to JSON.
type User struct {
	ID           string `json:"id"`
	Email        string `json:"email"`
	Name         string `json:"name"`
	Role         string `json:"role"`
	Phone        string `json:"phone"`
	Bio          string `json:"bio"`
	LicenseNo    string `json:"license_no"`
	PasswordHash string `json:"-"`
	GoogleID     string `json:"-"`

	EmailVerified      bool       `json:"email_verified"`
	PhoneVerified      bool       `json:"phone_verified"`
	VerificationStatus string     `json:"verification_status"`
	VerificationScore  int        `json:"verification_score"`
	VerificationNotes  string     `json:"verification_notes"`
	VerifiedAt         *time.Time `json:"verified_at,omitempty"`

	RatingAvg   float64 `json:"rating_avg"`
	RatingCount int     `json:"rating_count"`

	CreatedAt time.Time `json:"created_at"`
}

// IsAgent reports whether the user is a real estate professional.
func (u *User) IsAgent() bool { return u.Role == RoleAgent }

// IsAdmin reports whether the user may moderate content.
func (u *User) IsAdmin() bool { return u.Role == RoleAdmin }

// IsVerified reports whether the account passed identity verification.
func (u *User) IsVerified() bool { return u.VerificationStatus == VerificationVerified }

// UserSummary is the public subset of a user, embedded in listings so a card
// can show who is behind a listing without a second request.
type UserSummary struct {
	ID                 string  `json:"id"`
	Name               string  `json:"name"`
	Email              string  `json:"email"`
	Role               string  `json:"role"`
	VerificationStatus string  `json:"verification_status"`
	RatingAvg          float64 `json:"rating_avg"`
	RatingCount        int     `json:"rating_count"`
}

// Summary returns the public view of the user.
func (u *User) Summary() UserSummary {
	return UserSummary{
		ID:                 u.ID,
		Name:               u.Name,
		Email:              u.Email,
		Role:               u.Role,
		VerificationStatus: u.VerificationStatus,
		RatingAvg:          u.RatingAvg,
		RatingCount:        u.RatingCount,
	}
}

// Listing is a real estate posting.
type Listing struct {
	ID           string   `json:"id"`
	UserID       string   `json:"user_id"`
	Title        string   `json:"title"`
	Description  string   `json:"description"`
	Price        int64    `json:"price"` // whole currency units (e.g. pesos)
	Currency     string   `json:"currency"`
	PropertyType string   `json:"property_type"`
	ListingType  string   `json:"listing_type"`
	Bedrooms     int      `json:"bedrooms"`
	Bathrooms    float64  `json:"bathrooms"`
	AreaSqft     int      `json:"area_sqft"`
	Address      string   `json:"address"`
	City         string   `json:"city"`
	State        string   `json:"state"`
	ZipCode      string   `json:"zip_code"`
	Latitude     *float64 `json:"latitude,omitempty"`
	Longitude    *float64 `json:"longitude,omitempty"`
	Status       string   `json:"status"`

	// Verification is produced by the automated reviewer in internal/verify.
	VerificationStatus  string     `json:"verification_status"`
	VerificationScore   int        `json:"verification_score"`
	VerificationSummary string     `json:"verification_summary"`
	VerificationFlags   []string   `json:"verification_flags"`
	VerificationModel   string     `json:"verification_model,omitempty"`
	VerifiedAt          *time.Time `json:"verified_at,omitempty"`

	LastConfirmedAt *time.Time `json:"last_confirmed_at,omitempty"`
	// FeaturedUntil is when paid promotion expires. Promotion only takes effect
	// while the listing is also verified — see IsFeatured.
	FeaturedUntil *time.Time `json:"featured_until,omitempty"`
	ReportCount   int        `json:"report_count"`
	ViewCount     int        `json:"view_count"`

	Images    []ListingImage `json:"images"`
	Owner     *UserSummary   `json:"owner,omitempty"`
	CreatedAt time.Time      `json:"created_at"`
	UpdatedAt time.Time      `json:"updated_at"`
}

// IsFeatured reports whether the listing should get promoted placement.
//
// Promotion is deliberately gated on verification: paying must buy reach, never
// credibility. A listing that has not passed screening gets no boost no matter
// what its owner paid, so the marketplace can never amplify a scam.
func (l *Listing) IsFeatured() bool {
	return l.FeaturedUntil != nil &&
		l.FeaturedUntil.After(time.Now()) &&
		l.VerificationStatus == VerificationVerified
}

// IsStale reports whether the owner has not confirmed availability recently.
// Listings fall back to their creation date when never explicitly confirmed.
func (l *Listing) IsStale() bool {
	ref := l.CreatedAt
	if l.LastConfirmedAt != nil && l.LastConfirmedAt.After(ref) {
		ref = *l.LastConfirmedAt
	}
	return time.Since(ref) > StaleAfter
}

// ListingImage is a photo attached to a listing.
type ListingImage struct {
	ID        string    `json:"id"`
	ListingID string    `json:"listing_id"`
	URL       string    `json:"url"`
	Position  int       `json:"position"`
	CreatedAt time.Time `json:"created_at"`
}

// ListingReport is a user-submitted scam / misleading-listing report.
type ListingReport struct {
	ID         string       `json:"id"`
	ListingID  string       `json:"listing_id"`
	ReporterID string       `json:"reporter_id"`
	Reason     string       `json:"reason"`
	Details    string       `json:"details"`
	Status     string       `json:"status"` // open | reviewing | upheld | dismissed
	Resolution string       `json:"resolution,omitempty"`
	Listing    *Listing     `json:"listing,omitempty"`
	Reporter   *UserSummary `json:"reporter,omitempty"`
	CreatedAt  time.Time    `json:"created_at"`
	ResolvedAt *time.Time   `json:"resolved_at,omitempty"`
}

// Conversation is a per-listing chat thread between an inquirer and the owner.
type Conversation struct {
	ID            string       `json:"id"`
	ListingID     string       `json:"listing_id"`
	InquirerID    string       `json:"inquirer_id"`
	OwnerID       string       `json:"owner_id"`
	Listing       *Listing     `json:"listing,omitempty"`
	Counterparty  *UserSummary `json:"counterparty,omitempty"`
	LastMessage   *Message     `json:"last_message,omitempty"`
	UnreadCount   int          `json:"unread_count"`
	LastMessageAt *time.Time   `json:"last_message_at,omitempty"`
	CreatedAt     time.Time    `json:"created_at"`
}

// Message is a single chat message.
type Message struct {
	ID             string     `json:"id"`
	ConversationID string     `json:"conversation_id"`
	SenderID       string     `json:"sender_id"`
	Body           string     `json:"body"`
	ReadAt         *time.Time `json:"read_at,omitempty"`
	CreatedAt      time.Time  `json:"created_at"`
}

// ViewingStatuses are the accepted states of a viewing request.
var ViewingStatuses = []string{"requested", "confirmed", "declined", "cancelled", "completed"}

// ViewingRequest is a scheduled property viewing.
type ViewingRequest struct {
	ID           string       `json:"id"`
	ListingID    string       `json:"listing_id"`
	RequesterID  string       `json:"requester_id"`
	OwnerID      string       `json:"owner_id"`
	ScheduledFor time.Time    `json:"scheduled_for"`
	Status       string       `json:"status"`
	Note         string       `json:"note"`
	ResponseNote string       `json:"response_note"`
	Listing      *Listing     `json:"listing,omitempty"`
	Requester    *UserSummary `json:"requester,omitempty"`
	Owner        *UserSummary `json:"owner,omitempty"`
	CreatedAt    time.Time    `json:"created_at"`
	UpdatedAt    time.Time    `json:"updated_at"`
}

// Review is a rating left for an owner or agent.
type Review struct {
	ID            string       `json:"id"`
	SubjectUserID string       `json:"subject_user_id"`
	AuthorID      string       `json:"author_id"`
	ListingID     string       `json:"listing_id,omitempty"`
	Rating        int          `json:"rating"` // 1..5
	Comment       string       `json:"comment"`
	Author        *UserSummary `json:"author,omitempty"`
	CreatedAt     time.Time    `json:"created_at"`
}

// SavedSearch stores a browse filter so the user can re-run it and be alerted
// to new matches.
type SavedSearch struct {
	ID            string    `json:"id"`
	UserID        string    `json:"user_id"`
	Name          string    `json:"name"`
	Query         string    `json:"query"` // raw query string, e.g. "city=Muntinlupa&max_price=3000000"
	AlertsEnabled bool      `json:"alerts_enabled"`
	NewMatches    int       `json:"new_matches"`
	LastAlertedAt time.Time `json:"last_alerted_at"`
	CreatedAt     time.Time `json:"created_at"`
}

// FeaturePlan is a paid promotion package for a single listing.
//
// Priced per listing rather than per month: the surveyed supply side is mostly
// private owners with one property, and their acceptable spend clustered at
// PHP 500-1,000 (agents skewed higher, at PHP 1,000-3,000). These are starting
// points to validate against real conversions, not researched prices.
type FeaturePlan struct {
	ID    string `json:"id"`
	Label string `json:"label"`
	Days  int    `json:"days"`
	Price int64  `json:"price"` // in whole pesos
}

// FeaturePlans are the promotion packages offered to posters.
var FeaturePlans = []FeaturePlan{
	{ID: "spotlight_7", Label: "Spotlight — 7 days", Days: 7, Price: 299},
	{ID: "spotlight_14", Label: "Spotlight — 14 days", Days: 14, Price: 499},
	{ID: "spotlight_30", Label: "Spotlight — 30 days", Days: 30, Price: 899},
}

// FeaturePlanByID looks up a plan, reporting whether it exists.
func FeaturePlanByID(id string) (FeaturePlan, bool) {
	for _, p := range FeaturePlans {
		if p.ID == id {
			return p, true
		}
	}
	return FeaturePlan{}, false
}

// PriceComparison summarises comparable listings so a buyer can tell whether a
// price is reasonable — 46% of respondents asked for price comparison tools.
type PriceComparison struct {
	ListingID     string  `json:"listing_id"`
	Price         int64   `json:"price"`
	SampleSize    int     `json:"sample_size"`
	Median        int64   `json:"median"`
	Min           int64   `json:"min"`
	Max           int64   `json:"max"`
	PricePerSqft  float64 `json:"price_per_sqft"`
	MedianPerSqft float64 `json:"median_per_sqft"`
	// Verdict is one of "below_market", "at_market", "above_market", or
	// "insufficient_data" when there are too few comparables to judge.
	Verdict     string    `json:"verdict"`
	PercentDiff float64   `json:"percent_diff"`
	Comparables []Listing `json:"comparables"`
}
