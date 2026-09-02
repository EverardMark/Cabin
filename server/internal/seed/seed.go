package seed

import (
	"log"
	"time"

	"cabin/internal/auth"
	"cabin/internal/models"
	"cabin/internal/store"

	"github.com/google/uuid"
)

// Demo credentials. All seeded accounts share the same password.
const (
	DemoEmail    = "demo@cabin.app"
	OwnerEmail   = "owner@cabin.app"
	AdminEmail   = "admin@cabin.app"
	DemoPassword = "password123"
)

type sampleListing struct {
	title        string
	description  string
	price        int64
	propertyType string
	listingType  string
	bedrooms     int
	bathrooms    float64
	areaSqft     int
	address      string
	city         string
	state        string
	zip          string
	lat, lng     float64
	images       []string
	// byOwner posts the listing from the private-owner account rather than the
	// agent account, exercising the "owners can post too" path.
	byOwner bool
}

// Run seeds demo accounts and sample listings when the database has no users.
// It is a no-op if any users already exist.
func Run(users *store.UserStore, listings *store.ListingStore) error {
	count, err := users.Count()
	if err != nil {
		return err
	}
	if count > 0 {
		return nil
	}

	hash, err := auth.HashPassword(DemoPassword)
	if err != nil {
		return err
	}
	now := time.Now().UTC()
	verifiedAt := now

	// A licensed agent.
	agent := &models.User{
		ID: uuid.NewString(), Email: DemoEmail, Name: "Maria Santos",
		Role: models.RoleAgent, PasswordHash: hash,
		Phone: "+63 917 555 0134", LicenseNo: "PRC-REB-0012345",
		Bio:           "Licensed real estate broker working the Muntinlupa and Alabang corridor since 2016.",
		EmailVerified: true, PhoneVerified: true,
		VerificationStatus: models.VerificationVerified, VerificationScore: 92,
		VerificationNotes: "Licensed broker with a confirmed number and complete profile.",
		VerifiedAt:        &verifiedAt,
		CreatedAt:         now,
	}
	if err := users.Create(agent); err != nil {
		return err
	}

	// A private property owner — the largest group in the survey (36%), and the
	// group the old agent-only posting rule shut out.
	owner := &models.User{
		ID: uuid.NewString(), Email: OwnerEmail, Name: "Ramon Dela Cruz",
		Role: "user", PasswordHash: hash,
		Phone:         "+63 918 555 0192",
		Bio:           "Renting out the family townhouse in San Pedro. Direct owner, no agent fees.",
		EmailVerified: true, PhoneVerified: true,
		VerificationStatus: models.VerificationVerified, VerificationScore: 85,
		VerificationNotes: "Complete profile with a confirmed contact number.",
		VerifiedAt:        &verifiedAt,
		CreatedAt:         now,
	}
	if err := users.Create(owner); err != nil {
		return err
	}

	// A moderator for the report queue.
	admin := &models.User{
		ID: uuid.NewString(), Email: AdminEmail, Name: "Cabin Trust & Safety",
		Role: models.RoleAdmin, PasswordHash: hash,
		Phone: "+63 917 555 0100", EmailVerified: true, PhoneVerified: true,
		VerificationStatus: models.VerificationVerified, VerificationScore: 100,
		VerifiedAt: &verifiedAt,
		CreatedAt:  now,
	}
	if err := users.Create(admin); err != nil {
		return err
	}

	for _, sm := range samples() {
		lat, lng := sm.lat, sm.lng
		posterID := agent.ID
		if sm.byOwner {
			posterID = owner.ID
		}
		l := &models.Listing{
			ID:           uuid.NewString(),
			UserID:       posterID,
			Title:        sm.title,
			Description:  sm.description,
			Price:        sm.price,
			Currency:     "PHP",
			PropertyType: sm.propertyType,
			ListingType:  sm.listingType,
			Bedrooms:     sm.bedrooms,
			Bathrooms:    sm.bathrooms,
			AreaSqft:     sm.areaSqft,
			Address:      sm.address,
			City:         sm.city,
			State:        sm.state,
			ZipCode:      sm.zip,
			Latitude:     &lat,
			Longitude:    &lng,
			Status:       "active",
			// Seeded listings start pending so the verification worker reviews
			// them on first run — including the deliberately bad one below.
			VerificationStatus: models.VerificationPending,
		}
		if err := listings.Create(l); err != nil {
			return err
		}
		for _, url := range sm.images {
			img := &models.ListingImage{
				ID:        uuid.NewString(),
				ListingID: l.ID,
				URL:       url,
			}
			if err := listings.AddImage(img); err != nil {
				return err
			}
		}
	}

	log.Printf("seeded demo accounts (%s, %s, %s — password %s) with sample listings",
		DemoEmail, OwnerEmail, AdminEmail, DemoPassword)
	return nil
}

func img(seed string) string {
	return "https://picsum.photos/seed/" + seed + "/800/600"
}

// samples covers the areas the surveyed users actually live in — Muntinlupa
// and the surrounding Laguna/Cavite/Metro Manila corridor.
func samples() []sampleListing {
	return []sampleListing{
		{
			title:        "Modern 2BR Condo in Alabang",
			description:  "Bright corner unit on the 14th floor of a well-managed Alabang tower, with an unobstructed view toward the Muntinlupa skyline. Fully fitted kitchen with granite counters, split-type aircon in both bedrooms, and a deep balcony. One parking slot included. Walking distance to Alabang Town Center and the South Station bus terminal.",
			price:        8_500_000,
			propertyType: "condo",
			listingType:  "sale",
			bedrooms:     2, bathrooms: 2, areaSqft: 700,
			address: "Alabang-Zapote Rd, Tower 2, Unit 14C", city: "Muntinlupa", state: "Metro Manila", zip: "1780",
			lat: 14.4223, lng: 121.0292,
			images: []string{img("cabin-alabang-1"), img("cabin-alabang-2"), img("cabin-alabang-3")},
		},
		{
			title:        "Townhouse for Rent in San Pedro, Laguna",
			description:  "Two-storey townhouse in a quiet gated subdivision, ten minutes from the SLEX exit. Three bedrooms upstairs, one toilet and bath down, and a small garden at the back. Recently repainted, with a new water heater and screened windows. Family or long-term tenants preferred; minimum one year lease, two months deposit and one month advance.",
			price:        22_000,
			propertyType: "townhouse",
			listingType:  "rent",
			bedrooms:     3, bathrooms: 2, areaSqft: 850,
			address: "Blk 7 Lot 14, Pacita Complex 1", city: "San Pedro", state: "Laguna", zip: "4023",
			lat: 14.3583, lng: 121.0472,
			images:  []string{img("cabin-sanpedro-1"), img("cabin-sanpedro-2")},
			byOwner: true,
		},
		{
			title:        "Studio Unit Near Makati CBD",
			description:  "Fully furnished studio a short ride from Ayala Avenue. Includes bed, wardrobe, two-burner cooktop, refrigerator and a study desk. Building has 24/7 security, a small gym, and reliable fibre internet. Association dues and water are covered; tenant pays electricity.",
			price:        18_500,
			propertyType: "apartment",
			listingType:  "rent",
			bedrooms:     0, bathrooms: 1, areaSqft: 250,
			address: "Chino Roces Ave, Unit 908", city: "Makati", state: "Metro Manila", zip: "1230",
			lat: 14.5547, lng: 121.0244,
			images: []string{img("cabin-makati-1"), img("cabin-makati-2")},
		},
		{
			title:        "Family Home with Garden in Las Piñas",
			description:  "Well-maintained four-bedroom house on a 180 sqm corner lot. Living and dining areas open onto a covered lanai and a mature garden with fruit trees. Carport fits two vehicles. Clean title, updated tax declaration, and no flooding history in the subdivision. Owner is willing to entertain bank financing.",
			price:        11_800_000,
			propertyType: "house",
			listingType:  "sale",
			bedrooms:     4, bathrooms: 3, areaSqft: 1_900,
			address: "BF Resort Village, Talon Dos", city: "Las Piñas", state: "Metro Manila", zip: "1747",
			lat: 14.4445, lng: 120.9939,
			images: []string{img("cabin-laspinas-1"), img("cabin-laspinas-2"), img("cabin-laspinas-3")},
		},
		{
			title:        "Affordable Apartment in Cainta",
			description:  "Second-floor apartment unit along a quiet interior road in Cainta, close to Ortigas Extension. Two bedrooms, tiled floors throughout, and an individual water meter. Tricycle terminal and a public market are both within a five-minute walk. Suitable for a small family or two working professionals sharing.",
			price:        12_000,
			propertyType: "apartment",
			listingType:  "rent",
			bedrooms:     2, bathrooms: 1, areaSqft: 480,
			address: "A. Bonifacio Ave, Unit B", city: "Cainta", state: "Rizal", zip: "1900",
			lat: 14.5786, lng: 121.1222,
			images:  []string{img("cabin-cainta-1")},
			byOwner: true,
		},
		{
			title:        "House and Lot in Imus, Cavite",
			description:  "Three-bedroom single attached house in a completed subdivision with its own clubhouse and basketball court. Provision for a second-floor balcony, and the master bedroom has a walk-in closet. Twenty minutes to the Cavitex exit. Ready for occupancy, complete with fixtures.",
			price:        4_950_000,
			propertyType: "house",
			listingType:  "sale",
			bedrooms:     3, bathrooms: 2, areaSqft: 1_100,
			address: "Anabu II-D, Citta Italia", city: "Imus", state: "Cavite", zip: "4103",
			lat: 14.4297, lng: 120.9367,
			images: []string{img("cabin-imus-1"), img("cabin-imus-2")},
		},
		{
			title:        "Residential Lot in Calamba",
			description:  "Flat 200 sqm residential lot inside a subdivision with concrete roads, drainage, and electric posts already installed. Clean title under the owner's name, real property tax paid to date. Near Calamba Crossing and the future Calamba station of the North-South Commuter Railway.",
			price:        2_400_000,
			propertyType: "land",
			listingType:  "sale",
			bedrooms:     0, bathrooms: 0, areaSqft: 2_150,
			address: "Brgy. Canlubang, Southville", city: "Calamba", state: "Laguna", zip: "4027",
			lat: 14.2117, lng: 121.1653,
			images:  []string{img("cabin-calamba-1")},
			byOwner: true,
		},
		{
			title:        "RUSH!!! 3BR HOUSE AND LOT ONLY 50K!! DIRECT OWNER NO AGENT",
			description:  "RUSH SALE!!! Brand new 3 bedroom house and lot in BGC Taguig for only 50,000 pesos!!! LIMITED SLOTS ONLY!! First come first serve!! Send 5,000 reservation fee now thru GCash to reserve your unit, viewing after payment only. Message me directly on Viber, do not use the app chat. LEGIT SELLER 100 PERCENT NO SCAM!!! HURRY LAST 2 UNITS LEFT TODAY ONLY!!!",
			price:        50_000,
			propertyType: "house",
			listingType:  "sale",
			bedrooms:     3, bathrooms: 2, areaSqft: 1_400,
			address: "", city: "Taguig", state: "Metro Manila", zip: "",
			lat: 14.5507, lng: 121.0512,
			images: []string{},
		},
	}
}
