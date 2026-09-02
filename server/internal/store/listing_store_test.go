package store

import (
	"path/filepath"
	"testing"
	"time"

	"cabin/internal/database"
	"cabin/internal/models"

	"github.com/google/uuid"
)

func newStores(t *testing.T) (*UserStore, *ListingStore) {
	t.Helper()
	db, err := database.Open("sqlite", filepath.Join(t.TempDir(), "test.db"))
	if err != nil {
		t.Fatalf("open db: %v", err)
	}
	t.Cleanup(func() { db.Close() })
	if err := database.Migrate(db, "sqlite"); err != nil {
		t.Fatalf("migrate: %v", err)
	}
	return NewUserStore(db), NewListingStore(db)
}

func newUser(t *testing.T, users *UserStore) *models.User {
	t.Helper()
	u := &models.User{
		ID: uuid.NewString(), Email: uuid.NewString() + "@example.com", Name: "Test Poster",
		Role: "user", PasswordHash: "x", CreatedAt: time.Now().UTC(),
	}
	if err := users.Create(u); err != nil {
		t.Fatalf("create user: %v", err)
	}
	return u
}

func newListing(t *testing.T, listings *ListingStore, userID string, price int64, mut func(*models.Listing)) *models.Listing {
	t.Helper()
	l := &models.Listing{
		ID: uuid.NewString(), UserID: userID, Title: "Condo", Description: "A unit.",
		Price: price, Currency: "PHP", PropertyType: "condo", ListingType: "sale",
		Bedrooms: 2, Bathrooms: 2, AreaSqft: 700, City: "Muntinlupa", Status: "active",
	}
	if mut != nil {
		mut(l)
	}
	if err := listings.Create(l); err != nil {
		t.Fatalf("create listing: %v", err)
	}
	return l
}

func TestPriceComparisonNeedsEnoughComparables(t *testing.T) {
	users, listings := newStores(t)
	u := newUser(t, users)
	target := newListing(t, listings, u.ID, 8_500_000, nil)

	// Two comparables is not a market.
	newListing(t, listings, u.ID, 8_000_000, nil)
	newListing(t, listings, u.ID, 9_000_000, nil)

	pc, err := listings.PriceComparison(target)
	if err != nil {
		t.Fatalf("PriceComparison: %v", err)
	}
	if pc.Verdict != "insufficient_data" {
		t.Errorf("verdict = %q with %d comparables, want insufficient_data", pc.Verdict, pc.SampleSize)
	}
}

func TestPriceComparisonVerdicts(t *testing.T) {
	users, listings := newStores(t)
	u := newUser(t, users)
	for _, p := range []int64{7_800_000, 8_200_000, 8_400_000, 8_900_000, 9_100_000} {
		newListing(t, listings, u.ID, p, nil)
	}

	// Median of the five comparables above, checked once before the subtests
	// below add further listings to the pool.
	target := newListing(t, listings, u.ID, 8_500_000, nil)
	pc, err := listings.PriceComparison(target)
	if err != nil {
		t.Fatalf("PriceComparison: %v", err)
	}
	if pc.Median != 8_400_000 {
		t.Errorf("median = %d, want 8400000", pc.Median)
	}
	if pc.Verdict != "at_market" {
		t.Errorf("verdict = %q (%.1f%%), want at_market", pc.Verdict, pc.PercentDiff)
	}

	// Each case adds its own target, which then joins the comparable pool — so
	// assert the verdict, which stays stable, rather than the exact median.
	cases := []struct {
		name  string
		price int64
		want  string
	}{
		{"well above market", 40_000_000, "above_market"},
		{"well below market", 1_000_000, "below_market"},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			target := newListing(t, listings, u.ID, tc.price, nil)
			pc, err := listings.PriceComparison(target)
			if err != nil {
				t.Fatalf("PriceComparison: %v", err)
			}
			if pc.Verdict != tc.want {
				t.Errorf("verdict = %q (%.1f%% vs median %d), want %q",
					pc.Verdict, pc.PercentDiff, pc.Median, tc.want)
			}
		})
	}
}

func TestExcludeStaleFilter(t *testing.T) {
	users, listings := newStores(t)
	u := newUser(t, users)
	fresh := newListing(t, listings, u.ID, 5_000_000, nil)
	stale := newListing(t, listings, u.ID, 6_000_000, nil)

	// Backdate the stale listing past the staleness window.
	old := time.Now().UTC().Add(-2 * models.StaleAfter).Format(time.RFC3339)
	if _, err := listings.db.Exec(
		`UPDATE listings SET last_confirmed_at = ?, created_at = ? WHERE id = ?`, old, old, stale.ID); err != nil {
		t.Fatalf("backdate: %v", err)
	}

	all, _, err := listings.List(ListingFilter{Page: 1, PageSize: 50})
	if err != nil {
		t.Fatalf("list: %v", err)
	}
	if len(all) != 2 {
		t.Fatalf("unfiltered list returned %d listings, want 2", len(all))
	}

	live, _, err := listings.List(ListingFilter{Page: 1, PageSize: 50, ExcludeStale: true})
	if err != nil {
		t.Fatalf("list: %v", err)
	}
	if len(live) != 1 || live[0].ID != fresh.ID {
		t.Fatalf("exclude_stale returned %d listings, want just the fresh one", len(live))
	}

	// Confirming availability brings it back.
	if err := listings.ConfirmAvailability(stale.ID); err != nil {
		t.Fatalf("confirm: %v", err)
	}
	live, _, err = listings.List(ListingFilter{Page: 1, PageSize: 50, ExcludeStale: true})
	if err != nil {
		t.Fatalf("list: %v", err)
	}
	if len(live) != 2 {
		t.Errorf("after confirming, exclude_stale returned %d, want 2", len(live))
	}
}

func TestBoundingBoxFilter(t *testing.T) {
	users, listings := newStores(t)
	u := newUser(t, users)
	inside := newListing(t, listings, u.ID, 5_000_000, func(l *models.Listing) {
		lat, lng := 14.4223, 121.0292 // Alabang
		l.Latitude, l.Longitude = &lat, &lng
	})
	newListing(t, listings, u.ID, 5_000_000, func(l *models.Listing) {
		lat, lng := 10.3157, 123.8854 // Cebu, far outside
		l.Latitude, l.Longitude = &lat, &lng
	})
	newListing(t, listings, u.ID, 5_000_000, nil) // no coordinates at all

	minLat, maxLat, minLng, maxLng := 14.3, 14.6, 120.9, 121.2
	got, total, err := listings.List(ListingFilter{
		Page: 1, PageSize: 50,
		MinLat: &minLat, MaxLat: &maxLat, MinLng: &minLng, MaxLng: &maxLng,
	})
	if err != nil {
		t.Fatalf("list: %v", err)
	}
	if total != 1 || len(got) != 1 || got[0].ID != inside.ID {
		t.Fatalf("bbox returned %d listings (total %d), want only the Alabang one", len(got), total)
	}
}

func TestReportsRequeueListingAtThreshold(t *testing.T) {
	users, listings := newStores(t)
	owner := newUser(t, users)
	l := newListing(t, listings, owner.ID, 5_000_000, nil)
	if err := listings.ApplyVerification(l.ID, models.VerificationVerified, 95, "ok", nil, "test"); err != nil {
		t.Fatalf("verify: %v", err)
	}

	// Two reports leave the badge alone; the third sends it back for review.
	for i := 0; i < 2; i++ {
		reporter := newUser(t, users)
		if err := listings.AddReport(&models.ListingReport{
			ID: uuid.NewString(), ListingID: l.ID, ReporterID: reporter.ID, Reason: "scam",
		}); err != nil {
			t.Fatalf("add report: %v", err)
		}
	}
	got, _ := listings.GetByID(l.ID)
	if got.VerificationStatus != models.VerificationVerified {
		t.Errorf("status after 2 reports = %q, want still verified", got.VerificationStatus)
	}
	if got.ReportCount != 2 {
		t.Errorf("report_count = %d, want 2", got.ReportCount)
	}

	reporter := newUser(t, users)
	if err := listings.AddReport(&models.ListingReport{
		ID: uuid.NewString(), ListingID: l.ID, ReporterID: reporter.ID, Reason: "scam",
	}); err != nil {
		t.Fatalf("add report: %v", err)
	}
	got, _ = listings.GetByID(l.ID)
	if got.VerificationStatus != models.VerificationPending {
		t.Errorf("status after 3 reports = %q, want pending re-review", got.VerificationStatus)
	}
}

func TestPendingVerificationReturnsImages(t *testing.T) {
	users, listings := newStores(t)
	u := newUser(t, users)
	l := newListing(t, listings, u.ID, 5_000_000, nil)
	if err := listings.AddImage(&models.ListingImage{
		ID: uuid.NewString(), ListingID: l.ID, URL: "/uploads/a.jpg",
	}); err != nil {
		t.Fatalf("add image: %v", err)
	}

	pending, err := listings.PendingVerification(10)
	if err != nil {
		t.Fatalf("pending: %v", err)
	}
	if len(pending) != 1 {
		t.Fatalf("pending = %d, want 1", len(pending))
	}
	// The reviewer weighs photo count, so images must come with the listing.
	if len(pending[0].Images) != 1 {
		t.Errorf("pending listing carried %d images, want 1", len(pending[0].Images))
	}
}
