// Package seed loads the mock data set: demo accounts, sample listings across
// the Muntinlupa / Laguna / Cavite corridor, and enough chat, viewing and
// review activity that every screen in the apps has something to show.
package seed

import (
	"fmt"
	"log"
	"time"

	"cabin/internal/auth"
	"cabin/internal/models"
	"cabin/internal/store"

	"github.com/google/uuid"
)

// Demo credentials. All seeded accounts share the same password.
const (
	DemoEmail    = "demo@cabin.app"  // Maria Santos — licensed agent, posts most listings
	OwnerEmail   = "owner@cabin.app" // Ramon Dela Cruz — private owner
	BuyerEmail   = "buyer@cabin.app" // Jonas Reyes — renter/buyer with active viewings
	SecondBuyer  = "ana@cabin.app"   // Ana Villanueva — buyer with a pending request
	AdminEmail   = "admin@cabin.app" // moderator for the report queue
	DemoPassword = "password123"
)

// Stores is everything the seed needs to write.
type Stores struct {
	Users    *store.UserStore
	Listings *store.ListingStore
	Chat     *store.ChatStore
	Viewings *store.ViewingStore
	Reviews  *store.ReviewStore
	Searches *store.SearchStore
}

// Manila time, so seeded appointments land at sensible local hours.
var manila = time.FixedZone("PHT", 8*3600)

type sampleListing struct {
	key          string // stable handle so activity below can reference a listing
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

// Run seeds the mock data set when the database has no users. It is a no-op
// if any users already exist; use the server's -reseed flag to start over.
func Run(s Stores) error {
	count, err := s.Users.Count()
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
		CreatedAt:         now.AddDate(0, -8, 0),
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
		CreatedAt:         now.AddDate(0, -5, 0),
	}

	// Two buyers: one with a history of viewings and reviews, one just starting.
	buyer := &models.User{
		ID: uuid.NewString(), Email: BuyerEmail, Name: "Jonas Reyes",
		Role: "user", PasswordHash: hash,
		Phone:         "+63 920 555 0177",
		Bio:           "Relocating to the south for work; looking for a 2BR near SLEX.",
		EmailVerified: true, PhoneVerified: true,
		VerificationStatus: models.VerificationVerified, VerificationScore: 80,
		VerificationNotes: "Confirmed number and a complete profile.",
		VerifiedAt:        &verifiedAt,
		CreatedAt:         now.AddDate(0, -2, 0),
	}
	buyer2 := &models.User{
		ID: uuid.NewString(), Email: SecondBuyer, Name: "Ana Villanueva",
		Role: "user", PasswordHash: hash,
		Phone:         "+63 915 555 0140",
		EmailVerified: true, PhoneVerified: true,
		VerificationStatus: models.VerificationUnverified,
		CreatedAt:          now.AddDate(0, 0, -6),
	}

	// A moderator for the report queue.
	admin := &models.User{
		ID: uuid.NewString(), Email: AdminEmail, Name: "Cabin Trust & Safety",
		Role: models.RoleAdmin, PasswordHash: hash,
		Phone: "+63 917 555 0100", EmailVerified: true, PhoneVerified: true,
		VerificationStatus: models.VerificationVerified, VerificationScore: 100,
		VerifiedAt: &verifiedAt,
		CreatedAt:  now.AddDate(0, -9, 0),
	}

	for _, u := range []*models.User{agent, owner, buyer, buyer2, admin} {
		if err := s.Users.Create(u); err != nil {
			return fmt.Errorf("create user %s: %w", u.Email, err)
		}
	}

	// Listings, keyed so the activity below can point at them.
	byKey := map[string]*models.Listing{}
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
			// them on first run — including the deliberately bad one.
			VerificationStatus: models.VerificationPending,
		}
		if err := s.Listings.Create(l); err != nil {
			return fmt.Errorf("create listing %q: %w", sm.title, err)
		}
		for _, url := range sm.images {
			img := &models.ListingImage{ID: uuid.NewString(), ListingID: l.ID, URL: url}
			if err := s.Listings.AddImage(img); err != nil {
				return err
			}
		}
		byKey[sm.key] = l
	}

	if err := seedActivity(s, byKey, agent, owner, buyer, buyer2, now); err != nil {
		return err
	}

	log.Printf("seeded mock data: accounts %s, %s, %s, %s, %s (password %s), %d listings, chats, viewings, reviews",
		DemoEmail, OwnerEmail, BuyerEmail, SecondBuyer, AdminEmail, DemoPassword, len(byKey))
	return nil
}

// seedActivity adds the conversations, viewings, reviews and saved searches
// that make the Messages, Viewings and Profile screens worth looking at.
func seedActivity(s Stores, l map[string]*models.Listing, agent, owner, buyer, buyer2 *models.User, now time.Time) error {
	ago := func(d time.Duration) time.Time { return now.Add(-d) }
	day := 24 * time.Hour

	// Next Saturday 10:00 Manila time, for the pending request in the demo.
	local := now.In(manila)
	daysToSat := (int(time.Saturday) - int(local.Weekday()) + 7) % 7
	if daysToSat == 0 {
		daysToSat = 7
	}
	saturday := time.Date(local.Year(), local.Month(), local.Day()+daysToSat, 10, 0, 0, 0, manila)
	at := func(daysAhead int, hour, minute int) time.Time {
		return time.Date(local.Year(), local.Month(), local.Day()+daysAhead, hour, minute, 0, 0, manila)
	}

	// --- Conversations -------------------------------------------------------

	type line struct {
		from *models.User
		body string
		when time.Time
	}
	thread := func(listing *models.Listing, inquirer *models.User, reader *models.User, lines []line) error {
		conv, err := s.Chat.StartConversation(uuid.NewString(), listing.ID, inquirer.ID, listing.UserID)
		if err != nil {
			return err
		}
		for i, ln := range lines {
			// Everything but the final message has been read by the other side,
			// so exactly one thread shows as unread for `reader`.
			if i == len(lines)-1 && reader != nil {
				if err := s.Chat.MarkRead(conv.ID, reader.ID); err != nil {
					return err
				}
			}
			m := &models.Message{
				ID: uuid.NewString(), ConversationID: conv.ID,
				SenderID: ln.from.ID, Body: ln.body, CreatedAt: ln.when,
			}
			if err := s.Chat.Send(m); err != nil {
				return err
			}
		}
		return nil
	}

	// Jonas ↔ Maria about the Alabang condo (Maria's last message is unread on her side).
	if err := thread(l["alabang"], buyer, agent, []line{
		{buyer, "Hi Maria, is the 2BR in Alabang still available? I'm hoping to move by November.", ago(2*day + 3*time.Hour)},
		{agent, "Hi Jonas! Yes, it's still available. When would you like to view?", ago(2*day + 2*time.Hour)},
		{buyer, "Saturday morning if possible.", ago(2*day + 90*time.Minute)},
		{agent, "Saturday 10am works. It's a corner unit on the 14th floor with two balconies and a deeded parking slot — it was screened last week, so you'll see the trust score on the listing.", ago(2*day + 60*time.Minute)},
		{buyer, "Looks good — I've sent a viewing request for Saturday 10am.", ago(45 * time.Minute)},
	}); err != nil {
		return err
	}

	// Jonas ↔ Ramon about the San Pedro townhouse (fully read).
	if err := thread(l["sanpedro"], buyer, nil, []line{
		{buyer, "Good evening! Does the San Pedro townhouse allow a small dog?", ago(9 * day)},
		{owner, "Yes, small pets are fine as long as the garden is kept tidy. Two months deposit, one month advance.", ago(9*day - 2*time.Hour)},
		{buyer, "Perfect, thank you. Viewing went well — I'll confirm by the weekend.", ago(7 * day)},
	}); err != nil {
		return err
	}

	// Maria (as a buyer for a client) ↔ Ramon about the Cainta apartment; Ramon's reply is unread for Maria.
	if err := thread(l["cainta"], agent, agent, []line{
		{agent, "Hi Ramon, I have a client relocating from Pasig who's interested in the Cainta unit. Is Thursday morning open for a viewing?", ago(1 * day)},
		{owner, "Thursday 9:30 is fine. The unit has its own water meter and the market is a five-minute walk.", ago(20 * time.Hour)},
	}); err != nil {
		return err
	}

	// Ana ↔ Maria about the Alabang condo (Ana's message unread for Maria).
	if err := thread(l["alabang"], buyer2, agent, []line{
		{buyer2, "Hello! Is the asking price for the Alabang condo negotiable for a cash buyer?", ago(5 * time.Hour)},
	}); err != nil {
		return err
	}

	// --- Viewings ------------------------------------------------------------

	viewings := []models.ViewingRequest{
		{ // pending: shows Accept / Decline on Maria's Viewings tab
			ListingID: l["alabang"].ID, RequesterID: buyer.ID, OwnerID: agent.ID,
			ScheduledFor: saturday, Status: "requested",
			Note: "Saturday morning if possible.", CreatedAt: ago(45 * time.Minute),
		},
		{ // pending from the second buyer
			ListingID: l["alabang"].ID, RequesterID: buyer2.ID, OwnerID: agent.ID,
			ScheduledFor: at(8, 9, 30), Status: "requested",
			Note: "Can bring a bank pre-approval letter.", CreatedAt: ago(4 * time.Hour),
		},
		{ // confirmed, upcoming
			ListingID: l["makati"].ID, RequesterID: buyer.ID, OwnerID: agent.ID,
			ScheduledFor: at(5, 14, 0), Status: "confirmed",
			ResponseNote: "Confirmed — meet me at the lobby, bring a valid ID for the guard.", CreatedAt: ago(3 * day),
		},
		{ // Maria requesting on Ramon's listing, so the demo account is a requester too
			ListingID: l["cainta"].ID, RequesterID: agent.ID, OwnerID: owner.ID,
			ScheduledFor: at(3, 9, 30), Status: "confirmed",
			Note: "Viewing for a client relocating from Pasig.", ResponseNote: "Thursday 9:30 is fine.",
			CreatedAt: ago(20 * time.Hour),
		},
		{ // completed — unlocks Jonas's review of Maria
			ListingID: l["laspinas"].ID, RequesterID: buyer.ID, OwnerID: agent.ID,
			ScheduledFor: ago(10 * day), Status: "completed",
			Note: "Weekend viewing with my wife.", CreatedAt: ago(14 * day),
		},
		{ // completed — unlocks Jonas's review of Ramon
			ListingID: l["sanpedro"].ID, RequesterID: buyer.ID, OwnerID: owner.ID,
			ScheduledFor: ago(8 * day), Status: "completed",
			CreatedAt: ago(9 * day),
		},
		{ // declined, so the past section has some variety
			ListingID: l["imus"].ID, RequesterID: buyer2.ID, OwnerID: agent.ID,
			ScheduledFor: ago(3 * day), Status: "declined",
			Note: "Any chance of a weekday evening?", ResponseNote: "Evenings don't work for the current occupant, sorry — weekends only.",
			CreatedAt: ago(5 * day),
		},
	}
	for i := range viewings {
		v := viewings[i]
		v.ID = uuid.NewString()
		if err := s.Viewings.Create(&v); err != nil {
			return fmt.Errorf("create viewing: %w", err)
		}
	}

	// --- Reviews (only from completed viewings) -------------------------------

	reviews := []models.Review{
		{SubjectUserID: agent.ID, AuthorID: buyer.ID, ListingID: l["laspinas"].ID, Rating: 5,
			Comment: "Punctual, knew the house inside out, and never pushed. Sent the tax declaration the same day.", CreatedAt: ago(9 * day)},
		{SubjectUserID: owner.ID, AuthorID: buyer.ID, ListingID: l["sanpedro"].ID, Rating: 4,
			Comment: "Straightforward owner, honest about the water pressure upstairs. Would rent from him.", CreatedAt: ago(7 * day)},
		{SubjectUserID: agent.ID, AuthorID: owner.ID, ListingID: "", Rating: 5,
			Comment: "Helped me price the townhouse sensibly. Professional and quick to reply.", CreatedAt: ago(40 * day)},
	}
	for i := range reviews {
		r := reviews[i]
		r.ID = uuid.NewString()
		if err := s.Reviews.Create(&r); err != nil {
			return fmt.Errorf("create review: %w", err)
		}
	}
	for _, id := range []string{agent.ID, owner.ID} {
		if err := s.Users.RefreshRating(id); err != nil {
			return err
		}
	}

	// --- Saved searches --------------------------------------------------------

	searches := []models.SavedSearch{
		{UserID: agent.ID, Name: "Alabang · Condo · for sale",
			Query: "city=Muntinlupa&property_type=condo&listing_type=sale&verified_only=true", AlertsEnabled: true},
		{UserID: buyer.ID, Name: "Rentals under ₱25K",
			Query: "listing_type=rent&max_price=25000&verified_only=true", AlertsEnabled: true},
		{UserID: buyer.ID, Name: "Houses in Cavite",
			Query: "property_type=house&listing_type=sale&q=Cavite&verified_only=true", AlertsEnabled: true},
	}
	for i := range searches {
		ss := searches[i]
		ss.ID = uuid.NewString()
		if err := s.Searches.Create(&ss); err != nil {
			return fmt.Errorf("create saved search: %w", err)
		}
	}
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
			key:          "alabang",
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
			key:          "sanpedro",
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
			key:          "makati",
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
			key:          "laspinas",
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
			key:          "cainta",
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
			key:          "imus",
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
			key:          "calamba",
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
			key:          "filinvest",
			title:        "Furnished 1BR in Filinvest City, Alabang",
			description:  "Fully furnished one-bedroom on the 9th floor of a newer Filinvest City tower: queen bed, sofa, dining set, washer, and a galley kitchen with induction hob. Building amenities include a lap pool, gym and co-working lounge. Two minutes on foot to Festival Mall and the Alabang bus terminals. Association dues included; minimum one-year lease.",
			price:        28_000,
			propertyType: "condo",
			listingType:  "rent",
			bedrooms:     1, bathrooms: 1, areaSqft: 420,
			address: "Corporate Ave, Filinvest City, Unit 9F", city: "Muntinlupa", state: "Metro Manila", zip: "1781",
			lat: 14.4180, lng: 121.0400,
			images: []string{img("cabin-filinvest-1"), img("cabin-filinvest-2")},
		},
		{
			key:          "dasma",
			title:        "Bungalow with Carport in Dasmariñas, Cavite",
			description:  "Single-storey two-bedroom bungalow on a 120 sqm lot in an established subdivision off Governor's Drive. Renovated kitchen and bathroom in 2024, new roof sheets, and a gated carport. Ten minutes to De La Salle University Dasmariñas and the public market. Clean title, taxes paid; owner selling directly.",
			price:        3_650_000,
			propertyType: "house",
			listingType:  "sale",
			bedrooms:     2, bathrooms: 1, areaSqft: 900,
			address: "Phase 2, Brgy. Paliparan I", city: "Dasmariñas", state: "Cavite", zip: "4114",
			lat: 14.3294, lng: 120.9600,
			images:  []string{img("cabin-dasma-1"), img("cabin-dasma-2")},
			byOwner: true,
		},
		{
			key:          "scam",
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
