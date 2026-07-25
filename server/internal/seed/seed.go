package seed

import (
	"log"
	"time"

	"cabin/internal/auth"
	"cabin/internal/models"
	"cabin/internal/store"

	"github.com/google/uuid"
)

// DemoEmail and DemoPassword are the credentials for the seeded demo account.
const (
	DemoEmail    = "demo@cabin.app"
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
}

// Run seeds a demo account and sample listings when the database has no users.
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
	demo := &models.User{
		ID:           uuid.NewString(),
		Email:        DemoEmail,
		Name:         "Demo Agent",
		PasswordHash: hash,
		CreatedAt:    time.Now().UTC(),
	}
	if err := users.Create(demo); err != nil {
		return err
	}

	for _, sm := range samples() {
		lat, lng := sm.lat, sm.lng
		l := &models.Listing{
			ID:           uuid.NewString(),
			UserID:       demo.ID,
			Title:        sm.title,
			Description:  sm.description,
			Price:        sm.price,
			Currency:     "USD",
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

	log.Printf("seeded demo account (%s / %s) with sample listings", DemoEmail, DemoPassword)
	return nil
}

func img(seed string) string {
	return "https://picsum.photos/seed/" + seed + "/800/600"
}

func samples() []sampleListing {
	return []sampleListing{
		{
			title:        "Modern Downtown Loft",
			description:  "Sun-filled open-concept loft in the heart of downtown with floor-to-ceiling windows, exposed brick, and a chef's kitchen. Walk to restaurants, transit, and nightlife.",
			price:        525000,
			propertyType: "condo",
			listingType:  "sale",
			bedrooms:     2, bathrooms: 2, areaSqft: 1200,
			address: "410 W 4th St, Unit 12B", city: "Austin", state: "TX", zip: "78701",
			lat: 30.2672, lng: -97.7500,
			images: []string{img("cabin-loft-1"), img("cabin-loft-2"), img("cabin-loft-3")},
		},
		{
			title:        "Cozy Lakeside Cabin",
			description:  "Charming three-bedroom cabin steps from the water. Vaulted wood ceilings, a stone fireplace, and a wraparound deck perfect for morning coffee with a lake view.",
			price:        780000,
			propertyType: "house",
			listingType:  "sale",
			bedrooms:     3, bathrooms: 2, areaSqft: 1800,
			address: "88 Pinecrest Rd", city: "South Lake Tahoe", state: "CA", zip: "96150",
			lat: 38.9399, lng: -119.9772,
			images: []string{img("cabin-lake-1"), img("cabin-lake-2")},
		},
		{
			title:        "Sunny 1BR in Brooklyn",
			description:  "Bright, quiet one-bedroom on a tree-lined block. Renovated kitchen and bath, hardwood floors, and a shared roof deck. Close to the L train.",
			price:        2800,
			propertyType: "apartment",
			listingType:  "rent",
			bedrooms:     1, bathrooms: 1, areaSqft: 650,
			address: "123 Bedford Ave, Apt 3", city: "Brooklyn", state: "NY", zip: "11211",
			lat: 40.7178, lng: -73.9571,
			images: []string{img("cabin-bk-1"), img("cabin-bk-2")},
		},
		{
			title:        "Spacious Family Home",
			description:  "Move-in ready 4-bedroom home on a large corner lot. Updated kitchen, two-car garage, and a fenced backyard with mature shade trees in a top-rated school district.",
			price:        649000,
			propertyType: "house",
			listingType:  "sale",
			bedrooms:     4, bathrooms: 3, areaSqft: 2600,
			address: "2201 Meadowbrook Dr", city: "Austin", state: "TX", zip: "78745",
			lat: 30.2100, lng: -97.8000,
			images: []string{img("cabin-home-1"), img("cabin-home-2"), img("cabin-home-3")},
		},
		{
			title:        "Downtown Studio",
			description:  "Efficient studio with skyline views, in-unit laundry, and a fitness center in the building. Utilities included. Ideal for a downtown commuter.",
			price:        1950,
			propertyType: "apartment",
			listingType:  "rent",
			bedrooms:     0, bathrooms: 1, areaSqft: 480,
			address: "700 Pike St, Unit 1804", city: "Seattle", state: "WA", zip: "98101",
			lat: 47.6118, lng: -122.3352,
			images: []string{img("cabin-studio-1")},
		},
		{
			title:        "Mountain View Townhouse",
			description:  "Three-story townhouse with an attached garage, rooftop patio, and unobstructed mountain views. Open living area, quartz counters, and smart-home features throughout.",
			price:        432000,
			propertyType: "townhouse",
			listingType:  "sale",
			bedrooms:     3, bathrooms: 2.5, areaSqft: 1650,
			address: "55 Alpine Way, Unit 7", city: "Denver", state: "CO", zip: "80211",
			lat: 39.7600, lng: -105.0200,
			images: []string{img("cabin-town-1"), img("cabin-town-2")},
		},
		{
			title:        "Development Land Parcel",
			description:  "Half-acre buildable lot with utilities at the street and mountain views. Zoned residential, ready for your custom home. Quiet cul-de-sac near town amenities.",
			price:        185000,
			propertyType: "land",
			listingType:  "sale",
			bedrooms:     0, bathrooms: 0, areaSqft: 21780,
			address: "0 Summit Ridge Ct", city: "Bend", state: "OR", zip: "97703",
			lat: 44.0582, lng: -121.3153,
			images: []string{img("cabin-land-1")},
		},
	}
}
