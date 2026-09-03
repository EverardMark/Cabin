package handlers

import (
	"bytes"
	"database/sql"
	"encoding/json"
	"fmt"
	"io"
	"mime/multipart"
	"net/http"
	"net/http/httptest"
	"path/filepath"
	"strings"
	"testing"
	"time"

	"cabin/internal/auth"
	"cabin/internal/config"
	"cabin/internal/database"
	"cabin/internal/models"
	"cabin/internal/storage"
	"cabin/internal/store"
	"cabin/internal/verify"
)

type testEnv struct {
	t        *testing.T
	handler  http.Handler
	db       *sql.DB
	users    *store.UserStore
	listings *store.ListingStore
	viewings *store.ViewingStore
}

// backdate shifts a listing's creation time so ordering assertions are explicit
// rather than relying on how two same-second rows happen to tie.
func (e *testEnv) backdate(id string, d time.Duration) {
	e.t.Helper()
	when := time.Now().Add(-d).UTC().Format(time.RFC3339)
	if _, err := e.db.Exec(`UPDATE listings SET created_at = ? WHERE id = ?`, when, id); err != nil {
		e.t.Fatalf("backdate: %v", err)
	}
}

func newTestEnv(t *testing.T) *testEnv {
	t.Helper()
	dir := t.TempDir()

	db, err := database.Open("sqlite", filepath.Join(dir, "test.db"))
	if err != nil {
		t.Fatalf("open db: %v", err)
	}
	t.Cleanup(func() { db.Close() })
	if err := database.Migrate(db, "sqlite"); err != nil {
		t.Fatalf("migrate: %v", err)
	}

	uploads, err := storage.NewLocal(filepath.Join(dir, "uploads"))
	if err != nil {
		t.Fatalf("uploads: %v", err)
	}

	users := store.NewUserStore(db)
	listings := store.NewListingStore(db)
	viewings := store.NewViewingStore(db)
	verifier := verify.New("", "") // heuristic reviewer: no network in tests

	srv := NewServer(Deps{
		Config: &config.Config{
			JWTSecret:      "test-secret",
			UploadDir:      filepath.Join(dir, "uploads"),
			MaxUploadBytes: 1 << 20, // 1MB, so the cap is cheap to test
		},
		Users:    users,
		Listings: listings,
		Chat:     store.NewChatStore(db),
		Viewings: viewings,
		Reviews:  store.NewReviewStore(db),
		Searches: store.NewSearchStore(db),
		Tokens:   auth.NewTokenService("test-secret"),
		Uploads:  uploads,
		Verifier: verifier,
		Worker:   verify.NewWorker(verifier, listings, users),
	})
	return &testEnv{t: t, handler: srv.Handler(), db: db, users: users, listings: listings, viewings: viewings}
}

// do issues a JSON request and returns the status and decoded body.
func (e *testEnv) do(method, path, token string, body any) (int, map[string]any) {
	e.t.Helper()
	var rdr io.Reader
	if body != nil {
		raw, _ := json.Marshal(body)
		rdr = bytes.NewReader(raw)
	}
	req := httptest.NewRequest(method, path, rdr)
	req.Header.Set("Content-Type", "application/json")
	if token != "" {
		req.Header.Set("Authorization", "Bearer "+token)
	}
	rec := httptest.NewRecorder()
	e.handler.ServeHTTP(rec, req)

	out := map[string]any{}
	if b := rec.Body.Bytes(); len(b) > 0 && b[0] == '{' {
		_ = json.Unmarshal(b, &out)
	}
	return rec.Code, out
}

// register creates an account and returns its token and id.
func (e *testEnv) register(email, role string) (string, string) {
	e.t.Helper()
	code, body := e.do("POST", "/api/v1/auth/register", "", map[string]any{
		"email": email, "password": "password123", "name": "Test " + role, "role": role,
	})
	if code != http.StatusCreated {
		e.t.Fatalf("register %s: status %d (%v)", email, code, body)
	}
	user, _ := body["user"].(map[string]any)
	return body["token"].(string), user["id"].(string)
}

// createListing posts a listing and returns its id.
func (e *testEnv) createListing(token, title string, overrides map[string]any) string {
	e.t.Helper()
	payload := map[string]any{
		"title":         title,
		"description":   "A complete description of the property with enough detail to be useful to a buyer reading it.",
		"price":         5_000_000,
		"property_type": "house",
		"listing_type":  "sale",
		"bedrooms":      3,
		"bathrooms":     2,
		"area_sqft":     1200,
		"city":          "Muntinlupa",
		"address":       "Brgy. Alabang",
	}
	for k, v := range overrides {
		payload[k] = v
	}
	code, body := e.do("POST", "/api/v1/listings", token, payload)
	if code != http.StatusCreated {
		e.t.Fatalf("create listing: status %d (%v)", code, body)
	}
	return body["id"].(string)
}

// The survey's clearest contradiction: owners are 36% of respondents and
// nobody wanted an agents-only market, but posting was gated behind "agent".
func TestPrivateOwnerCanPostListing(t *testing.T) {
	e := newTestEnv(t)
	token, _ := e.register("owner@example.com", "user")

	code, body := e.do("POST", "/api/v1/listings", token, map[string]any{
		"title":         "Bungalow in Muntinlupa",
		"description":   "Three bedroom bungalow with a garden and carport near Filinvest, clean title.",
		"price":         6_500_000,
		"property_type": "house",
		"listing_type":  "sale",
		"city":          "Muntinlupa",
	})
	if code != http.StatusCreated {
		t.Fatalf("owner could not post: status %d (%v)", code, body)
	}
	if got := body["verification_status"]; got != models.VerificationPending {
		t.Errorf("verification_status = %v, want pending", got)
	}
}

func TestNewListingStartsPendingNotVerified(t *testing.T) {
	e := newTestEnv(t)
	token, _ := e.register("agent@example.com", "agent")
	id := e.createListing(token, "Condo in Alabang", nil)

	l, err := e.listings.GetByID(id)
	if err != nil {
		t.Fatalf("load listing: %v", err)
	}
	if l.VerificationStatus != models.VerificationPending {
		t.Errorf("status = %q, want pending — a badge must be earned, not granted at creation",
			l.VerificationStatus)
	}
}

func TestVerifiedOnlyFilterAndRejectedListingsAreHidden(t *testing.T) {
	e := newTestEnv(t)
	token, _ := e.register("agent@example.com", "agent")

	good := e.createListing(token, "Verified Home", nil)
	flagged := e.createListing(token, "Flagged Home", nil)
	scam := e.createListing(token, "Scam Home", nil)

	must := func(err error) {
		if err != nil {
			t.Fatalf("apply verification: %v", err)
		}
	}
	must(e.listings.ApplyVerification(good, models.VerificationVerified, 95, "Looks good.", nil, "test"))
	must(e.listings.ApplyVerification(flagged, models.VerificationFlagged, 55, "Thin details.", []string{"thin_description"}, "test"))
	must(e.listings.ApplyVerification(scam, models.VerificationRejected, 5, "Scam signals.", []string{"upfront_payment"}, "test"))

	titles := func(path string) []string {
		_, body := e.do("GET", path, "", nil)
		raw, _ := json.Marshal(body["listings"])
		var ls []models.Listing
		_ = json.Unmarshal(raw, &ls)
		out := make([]string, 0, len(ls))
		for _, l := range ls {
			out = append(out, l.Title)
		}
		return out
	}

	def := titles("/api/v1/listings?page_size=50")
	if contains(def, "Scam Home") {
		t.Error("a rejected listing appeared in default browse")
	}
	if !contains(def, "Flagged Home") {
		t.Error("a flagged listing should still be browsable, with a warning")
	}

	onlyVerified := titles("/api/v1/listings?verified_only=true&page_size=50")
	if len(onlyVerified) != 1 || onlyVerified[0] != "Verified Home" {
		t.Errorf("verified_only returned %v, want just [Verified Home]", onlyVerified)
	}
}

func TestEditingListingSendsItBackForReview(t *testing.T) {
	e := newTestEnv(t)
	token, _ := e.register("agent@example.com", "agent")
	id := e.createListing(token, "Condo in Alabang", nil)
	if err := e.listings.ApplyVerification(id, models.VerificationVerified, 95, "ok", nil, "test"); err != nil {
		t.Fatalf("apply: %v", err)
	}

	// Changing only the status must not cost the badge.
	if code, body := e.do("PUT", "/api/v1/listings/"+id, token, map[string]any{"status": "pending"}); code != http.StatusOK {
		t.Fatalf("update status: %d (%v)", code, body)
	}
	l, _ := e.listings.GetByID(id)
	if l.VerificationStatus != models.VerificationVerified {
		t.Errorf("status change reset verification to %q; want it kept", l.VerificationStatus)
	}

	// Changing the price is reviewed content, so the badge must be re-earned.
	if code, _ := e.do("PUT", "/api/v1/listings/"+id, token, map[string]any{"price": 1}); code != http.StatusOK {
		t.Fatalf("update price: %d", code)
	}
	l, _ = e.listings.GetByID(id)
	if l.VerificationStatus != models.VerificationPending {
		t.Errorf("status = %q after a price edit, want pending", l.VerificationStatus)
	}
}

func TestReportRules(t *testing.T) {
	e := newTestEnv(t)
	agentTok, _ := e.register("agent@example.com", "agent")
	buyerTok, _ := e.register("buyer@example.com", "user")
	id := e.createListing(agentTok, "Suspicious Listing", nil)

	if code, body := e.do("POST", "/api/v1/listings/"+id+"/report", buyerTok,
		map[string]any{"reason": "scam", "details": "Asks for a deposit before viewing."}); code != http.StatusCreated {
		t.Fatalf("report: %d (%v)", code, body)
	}
	if code, _ := e.do("POST", "/api/v1/listings/"+id+"/report", buyerTok, map[string]any{"reason": "scam"}); code != http.StatusConflict {
		t.Errorf("duplicate report status = %d, want 409", code)
	}
	if code, _ := e.do("POST", "/api/v1/listings/"+id+"/report", agentTok, map[string]any{"reason": "scam"}); code != http.StatusBadRequest {
		t.Errorf("self-report status = %d, want 400", code)
	}
	if code, _ := e.do("POST", "/api/v1/listings/"+id+"/report", buyerTok, map[string]any{"reason": "not_a_reason"}); code != http.StatusBadRequest {
		t.Errorf("invalid reason status = %d, want 400", code)
	}
}

// Reviews must be earned by a completed viewing, not open to anyone — the
// answer to "vague feedback from other users" in the survey's free text.
func TestReviewRequiresCompletedViewing(t *testing.T) {
	e := newTestEnv(t)
	agentTok, agentID := e.register("agent@example.com", "agent")
	buyerTok, buyerID := e.register("buyer@example.com", "user")
	listingID := e.createListing(agentTok, "Condo in Alabang", nil)

	if code, _ := e.do("POST", "/api/v1/users/"+agentID+"/reviews", buyerTok, map[string]any{"rating": 5}); code != http.StatusForbidden {
		t.Errorf("review without a viewing = %d, want 403", code)
	}

	v := &models.ViewingRequest{
		ID: "viewing-1", ListingID: listingID, RequesterID: buyerID, OwnerID: agentID,
		ScheduledFor: time.Now().Add(24 * time.Hour), Status: "completed",
	}
	if err := e.viewings.Create(v); err != nil {
		t.Fatalf("create viewing: %v", err)
	}

	if code, body := e.do("POST", "/api/v1/users/"+agentID+"/reviews", buyerTok,
		map[string]any{"rating": 5, "comment": "Matched the photos."}); code != http.StatusCreated {
		t.Fatalf("review after viewing = %d (%v)", code, body)
	}
	if code, _ := e.do("POST", "/api/v1/users/"+agentID+"/reviews", buyerTok, map[string]any{"rating": 1}); code != http.StatusConflict {
		t.Errorf("second review = %d, want 409", code)
	}

	// The cached aggregate on the user must move with the review.
	u, _ := e.users.GetByID(agentID)
	if u.RatingCount != 1 || u.RatingAvg != 5 {
		t.Errorf("rating = %.1f from %d, want 5.0 from 1", u.RatingAvg, u.RatingCount)
	}
}

func TestOnlyOwnerCanConfirmViewing(t *testing.T) {
	e := newTestEnv(t)
	agentTok, _ := e.register("agent@example.com", "agent")
	buyerTok, _ := e.register("buyer@example.com", "user")
	listingID := e.createListing(agentTok, "Condo in Alabang", nil)

	when := time.Now().Add(48 * time.Hour).UTC().Format(time.RFC3339)
	code, body := e.do("POST", "/api/v1/listings/"+listingID+"/viewings", buyerTok,
		map[string]any{"scheduled_for": when, "note": "Afternoon please."})
	if code != http.StatusCreated {
		t.Fatalf("request viewing: %d (%v)", code, body)
	}
	viewingID := body["id"].(string)

	if code, _ := e.do("PATCH", "/api/v1/viewings/"+viewingID, buyerTok, map[string]any{"status": "confirmed"}); code != http.StatusForbidden {
		t.Errorf("requester confirming = %d, want 403", code)
	}
	if code, _ := e.do("PATCH", "/api/v1/viewings/"+viewingID, agentTok, map[string]any{"status": "confirmed"}); code != http.StatusOK {
		t.Errorf("owner confirming = %d, want 200", code)
	}
	// A past time is never a valid booking.
	if code, _ := e.do("POST", "/api/v1/listings/"+listingID+"/viewings", buyerTok,
		map[string]any{"scheduled_for": "2020-01-01T10:00:00Z"}); code != http.StatusBadRequest {
		t.Errorf("past booking = %d, want 400", code)
	}
}

func TestConversationIsPrivateToParticipants(t *testing.T) {
	e := newTestEnv(t)
	agentTok, _ := e.register("agent@example.com", "agent")
	buyerTok, _ := e.register("buyer@example.com", "user")
	strangerTok, _ := e.register("stranger@example.com", "user")
	listingID := e.createListing(agentTok, "Condo in Alabang", nil)

	code, body := e.do("POST", "/api/v1/listings/"+listingID+"/conversations", buyerTok, nil)
	if code != http.StatusCreated {
		t.Fatalf("start conversation: %d (%v)", code, body)
	}
	convID := body["id"].(string)

	if code, _ := e.do("GET", "/api/v1/conversations/"+convID+"/messages", strangerTok, nil); code != http.StatusNotFound {
		t.Errorf("stranger reading thread = %d, want 404", code)
	}
	if code, _ := e.do("POST", "/api/v1/conversations/"+convID+"/messages", strangerTok,
		map[string]any{"body": "hello"}); code != http.StatusNotFound {
		t.Errorf("stranger posting to thread = %d, want 404", code)
	}
	if code, _ := e.do("POST", "/api/v1/conversations/"+convID+"/messages", agentTok,
		map[string]any{"body": "Still available."}); code != http.StatusCreated {
		t.Errorf("owner posting to thread = %d, want 201", code)
	}
	// Starting a second thread on the same listing reuses the first.
	_, again := e.do("POST", "/api/v1/listings/"+listingID+"/conversations", buyerTok, nil)
	if again["id"] != convID {
		t.Errorf("second start created a new thread %v, want %v", again["id"], convID)
	}
}

func TestAdminEndpointsRequireAdminRole(t *testing.T) {
	e := newTestEnv(t)
	userTok, _ := e.register("user@example.com", "user")

	if code, _ := e.do("GET", "/api/v1/admin/reports", userTok, nil); code != http.StatusForbidden {
		t.Errorf("non-admin reading reports = %d, want 403", code)
	}
	if code, _ := e.do("GET", "/api/v1/admin/reports", "", nil); code != http.StatusUnauthorized {
		t.Errorf("anonymous reading reports = %d, want 401", code)
	}
	// Admin must not be self-assignable at signup.
	if code, _ := e.do("POST", "/api/v1/auth/register", "", map[string]any{
		"email": "sneaky@example.com", "password": "password123", "name": "Sneaky", "role": "admin",
	}); code != http.StatusForbidden {
		t.Errorf("self-registering as admin = %d, want 403", code)
	}
}

// Regression: ParseMultipartForm's argument is a memory threshold, not a size
// cap, so an oversized upload used to succeed and spill to disk.
func TestUploadIsSizeCapped(t *testing.T) {
	e := newTestEnv(t)
	token, _ := e.register("agent@example.com", "agent")
	id := e.createListing(token, "Condo in Alabang", nil)

	var buf bytes.Buffer
	mw := multipart.NewWriter(&buf)
	part, err := mw.CreateFormFile("image", "big.jpg")
	if err != nil {
		t.Fatalf("form file: %v", err)
	}
	// 2MB against a 1MB configured cap.
	if _, err := part.Write(bytes.Repeat([]byte{0xff}, 2<<20)); err != nil {
		t.Fatalf("write: %v", err)
	}
	mw.Close()

	req := httptest.NewRequest("POST", "/api/v1/listings/"+id+"/images", &buf)
	req.Header.Set("Content-Type", mw.FormDataContentType())
	req.Header.Set("Authorization", "Bearer "+token)
	rec := httptest.NewRecorder()
	e.handler.ServeHTTP(rec, req)

	if rec.Code != http.StatusRequestEntityTooLarge {
		t.Errorf("oversized upload = %d, want 413", rec.Code)
	}
}

func TestUploadRejectsNonImageContent(t *testing.T) {
	e := newTestEnv(t)
	token, _ := e.register("agent@example.com", "agent")
	id := e.createListing(token, "Condo in Alabang", nil)

	var buf bytes.Buffer
	mw := multipart.NewWriter(&buf)
	part, _ := mw.CreateFormFile("image", "evil.jpg")
	fmt.Fprint(part, "<html><script>alert(1)</script></html>")
	mw.Close()

	req := httptest.NewRequest("POST", "/api/v1/listings/"+id+"/images", &buf)
	req.Header.Set("Content-Type", mw.FormDataContentType())
	req.Header.Set("Authorization", "Bearer "+token)
	rec := httptest.NewRecorder()
	e.handler.ServeHTTP(rec, req)

	if rec.Code != http.StatusBadRequest {
		t.Errorf("non-image upload = %d, want 400", rec.Code)
	}
}

// Regression: http.FileServer served a directory index of every upload.
func TestUploadsDirectoryIsNotListable(t *testing.T) {
	e := newTestEnv(t)
	for _, path := range []string{"/uploads/", "/uploads/../test.db"} {
		req := httptest.NewRequest("GET", path, nil)
		rec := httptest.NewRecorder()
		e.handler.ServeHTTP(rec, req)
		if rec.Code == http.StatusOK && strings.Contains(rec.Body.String(), "<a href=") {
			t.Errorf("GET %s returned a directory index", path)
		}
	}
}

// --- featured listings ---

// featureListing marks a listing verified and buys it a promotion slot.
func (e *testEnv) featureListing(token, id, plan string) (int, map[string]any) {
	e.t.Helper()
	return e.do("POST", "/api/v1/listings/"+id+"/feature", token, map[string]any{"plan_id": plan})
}

func TestFeaturedListingSurfacesFirst(t *testing.T) {
	e := newTestEnv(t)
	token, _ := e.register("agent@example.com", "agent")

	older := e.createListing(token, "Older Listing", nil)
	newer := e.createListing(token, "Newer Listing", nil)
	e.backdate(older, 2*time.Hour)
	for _, id := range []string{older, newer} {
		if err := e.listings.ApplyVerification(id, models.VerificationVerified, 90, "ok", nil, "test"); err != nil {
			t.Fatalf("verify: %v", err)
		}
	}

	// Default order is newest-first, so the older listing is normally second.
	if got := e.browseTitles("/api/v1/listings?page_size=50"); got[0] != "Newer Listing" {
		t.Fatalf("baseline order = %v, want Newer Listing first", got)
	}

	if code, body := e.featureListing(token, older, "spotlight_7"); code != http.StatusOK {
		t.Fatalf("feature: %d (%v)", code, body)
	}
	if got := e.browseTitles("/api/v1/listings?page_size=50"); got[0] != "Older Listing" {
		t.Errorf("after promotion order = %v, want Older Listing first", got)
	}

	// Promotion must lead every sort, not just the default one.
	if got := e.browseTitles("/api/v1/listings?sort=price_asc&page_size=50"); got[0] != "Older Listing" {
		t.Errorf("price_asc order = %v, want the promoted listing first", got)
	}
}

// The load-bearing rule: paying buys reach, never credibility. A listing that
// has not passed screening cannot be promoted at any price — otherwise the
// marketplace would sell amplification for scams.
func TestUnverifiedListingCannotBePromoted(t *testing.T) {
	e := newTestEnv(t)
	token, _ := e.register("agent@example.com", "agent")

	pending := e.createListing(token, "Still Screening", nil)
	if code, body := e.featureListing(token, pending, "spotlight_7"); code != http.StatusConflict {
		t.Errorf("promoting a pending listing = %d, want 409 (%v)", code, body)
	}

	rejected := e.createListing(token, "Rejected Listing", nil)
	if err := e.listings.ApplyVerification(rejected, models.VerificationRejected, 5, "scam", nil, "test"); err != nil {
		t.Fatalf("verify: %v", err)
	}
	if code, _ := e.featureListing(token, rejected, "spotlight_7"); code != http.StatusConflict {
		t.Errorf("promoting a rejected listing = %d, want 409", code)
	}
}

// A listing that loses verification after being promoted must lose its boost
// too — paid time keeps running, but the placement stops.
func TestPromotionLapsesWhenVerificationIsLost(t *testing.T) {
	e := newTestEnv(t)
	token, _ := e.register("agent@example.com", "agent")

	promoted := e.createListing(token, "Promoted Listing", nil)
	plain := e.createListing(token, "Plain Listing", nil)
	e.backdate(promoted, 2*time.Hour) // without a boost it would sort second
	for _, id := range []string{promoted, plain} {
		if err := e.listings.ApplyVerification(id, models.VerificationVerified, 90, "ok", nil, "test"); err != nil {
			t.Fatalf("verify: %v", err)
		}
	}
	if code, _ := e.featureListing(token, promoted, "spotlight_30"); code != http.StatusOK {
		t.Fatal("could not promote")
	}
	if got := e.browseTitles("/api/v1/listings?page_size=50"); got[0] != "Promoted Listing" {
		t.Fatalf("order = %v, want the promoted listing first", got)
	}

	// Re-screening knocks it back to flagged; the boost must stop immediately.
	if err := e.listings.ApplyVerification(promoted, models.VerificationFlagged, 50, "concerns", nil, "test"); err != nil {
		t.Fatalf("verify: %v", err)
	}
	if got := e.browseTitles("/api/v1/listings?page_size=50"); got[0] == "Promoted Listing" {
		t.Errorf("order = %v, want the de-verified listing to lose its boost", got)
	}
}

func TestExpiredPromotionDoesNotBoost(t *testing.T) {
	e := newTestEnv(t)
	token, _ := e.register("agent@example.com", "agent")

	expired := e.createListing(token, "Expired Promo", nil)
	newer := e.createListing(token, "Newer Listing", nil)
	e.backdate(expired, 2*time.Hour)
	for _, id := range []string{expired, newer} {
		if err := e.listings.ApplyVerification(id, models.VerificationVerified, 90, "ok", nil, "test"); err != nil {
			t.Fatalf("verify: %v", err)
		}
	}
	if err := e.listings.SetFeatured(expired, time.Now().Add(-24*time.Hour)); err != nil {
		t.Fatalf("set featured: %v", err)
	}
	if got := e.browseTitles("/api/v1/listings?page_size=50"); got[0] != "Newer Listing" {
		t.Errorf("order = %v, want the expired promotion to carry no boost", got)
	}
}

func TestFeatureRules(t *testing.T) {
	e := newTestEnv(t)
	ownerTok, _ := e.register("agent@example.com", "agent")
	otherTok, _ := e.register("someone@example.com", "user")
	id := e.createListing(ownerTok, "A Listing", nil)
	if err := e.listings.ApplyVerification(id, models.VerificationVerified, 90, "ok", nil, "test"); err != nil {
		t.Fatalf("verify: %v", err)
	}

	if code, _ := e.featureListing(otherTok, id, "spotlight_7"); code != http.StatusForbidden {
		t.Errorf("promoting someone else's listing = %d, want 403", code)
	}
	if code, _ := e.featureListing(ownerTok, id, "not_a_plan"); code != http.StatusBadRequest {
		t.Errorf("unknown plan = %d, want 400", code)
	}

	// Buying again while live extends rather than restarting the clock.
	if code, _ := e.featureListing(ownerTok, id, "spotlight_7"); code != http.StatusOK {
		t.Fatal("first purchase failed")
	}
	first, _ := e.listings.GetByID(id)
	if code, _ := e.featureListing(ownerTok, id, "spotlight_7"); code != http.StatusOK {
		t.Fatal("second purchase failed")
	}
	second, _ := e.listings.GetByID(id)
	if !second.FeaturedUntil.After(*first.FeaturedUntil) {
		t.Errorf("second purchase ended at %v, want later than %v", second.FeaturedUntil, first.FeaturedUntil)
	}
}

func TestFeaturePlansAreListed(t *testing.T) {
	e := newTestEnv(t)
	code, body := e.do("GET", "/api/v1/feature-plans", "", nil)
	if code != http.StatusOK {
		t.Fatalf("feature-plans = %d", code)
	}
	plans, _ := body["plans"].([]any)
	if len(plans) != len(models.FeaturePlans) {
		t.Errorf("got %d plans, want %d", len(plans), len(models.FeaturePlans))
	}
	if body["currency"] != "PHP" {
		t.Errorf("currency = %v, want PHP", body["currency"])
	}
}

// browseTitles returns listing titles in the order the API returned them.
func (e *testEnv) browseTitles(path string) []string {
	e.t.Helper()
	_, body := e.do("GET", path, "", nil)
	raw, _ := json.Marshal(body["listings"])
	var ls []models.Listing
	_ = json.Unmarshal(raw, &ls)
	out := make([]string, 0, len(ls))
	for _, l := range ls {
		out = append(out, l.Title)
	}
	return out
}

// --- photo management ---

// uploadPhoto posts a tiny valid PNG and returns the created image id.
func (e *testEnv) uploadPhoto(token, listingID string) string {
	e.t.Helper()
	// 1x1 PNG.
	png := []byte{
		0x89, 'P', 'N', 'G', 0x0d, 0x0a, 0x1a, 0x0a,
		0, 0, 0, 0x0d, 'I', 'H', 'D', 'R', 0, 0, 0, 1, 0, 0, 0, 1, 8, 2, 0, 0, 0,
		0x90, 0x77, 0x53, 0xde,
		0, 0, 0, 0x0c, 'I', 'D', 'A', 'T', 0x08, 0xd7, 0x63, 0xf8, 0xff, 0xff, 0x3f, 0, 5, 0xfe, 2, 0xfe,
		0xa7, 0x35, 0x81, 0x84,
		0, 0, 0, 0, 'I', 'E', 'N', 'D', 0xae, 0x42, 0x60, 0x82,
	}
	var buf bytes.Buffer
	mw := multipart.NewWriter(&buf)
	part, _ := mw.CreateFormFile("image", "photo.png")
	part.Write(png)
	mw.Close()

	req := httptest.NewRequest("POST", "/api/v1/listings/"+listingID+"/images", &buf)
	req.Header.Set("Content-Type", mw.FormDataContentType())
	req.Header.Set("Authorization", "Bearer "+token)
	rec := httptest.NewRecorder()
	e.handler.ServeHTTP(rec, req)
	if rec.Code != http.StatusCreated {
		e.t.Fatalf("upload photo: %d (%s)", rec.Code, rec.Body.String())
	}
	var img models.ListingImage
	_ = json.Unmarshal(rec.Body.Bytes(), &img)
	return img.ID
}

// photoIDs returns a listing's photo ids in display order.
func (e *testEnv) photoIDs(id string) []string {
	e.t.Helper()
	l, err := e.listings.GetByID(id)
	if err != nil {
		e.t.Fatalf("load listing: %v", err)
	}
	out := make([]string, 0, len(l.Images))
	for _, img := range l.Images {
		out = append(out, img.ID)
	}
	return out
}

// A poster stuck with a bad first photo can't fix their thumbnail, and "poor
// photos" was the survey's third-biggest complaint.
func TestOwnerCanDeleteAndReorderPhotos(t *testing.T) {
	e := newTestEnv(t)
	token, _ := e.register("agent@example.com", "agent")
	id := e.createListing(token, "Condo in Alabang", nil)

	a := e.uploadPhoto(token, id)
	b := e.uploadPhoto(token, id)
	c := e.uploadPhoto(token, id)
	if got := e.photoIDs(id); len(got) != 3 || got[0] != a {
		t.Fatalf("initial photos = %v, want [a b c]", got)
	}

	// Promote the third photo to the thumbnail slot.
	if code, body := e.do("PUT", "/api/v1/listings/"+id+"/images/order", token,
		map[string]any{"image_ids": []string{c, a, b}}); code != http.StatusOK {
		t.Fatalf("reorder: %d (%v)", code, body)
	}
	if got := e.photoIDs(id); got[0] != c {
		t.Errorf("after reorder = %v, want %s first", got, c)
	}

	// Delete the middle one; ordering must stay contiguous.
	if code, _ := e.do("DELETE", "/api/v1/listings/"+id+"/images/"+a, token, nil); code != http.StatusOK {
		t.Fatalf("delete photo failed")
	}
	got := e.photoIDs(id)
	if len(got) != 2 || got[0] != c || got[1] != b {
		t.Errorf("after delete = %v, want [c b]", got)
	}
	l, _ := e.listings.GetByID(id)
	for i, img := range l.Images {
		if img.Position != i {
			t.Errorf("photo %d has position %d; positions must stay contiguous", i, img.Position)
		}
	}
}

func TestPhotoManagementIsOwnerOnly(t *testing.T) {
	e := newTestEnv(t)
	ownerTok, _ := e.register("agent@example.com", "agent")
	otherTok, _ := e.register("someone@example.com", "user")
	id := e.createListing(ownerTok, "Condo in Alabang", nil)
	photo := e.uploadPhoto(ownerTok, id)

	if code, _ := e.do("DELETE", "/api/v1/listings/"+id+"/images/"+photo, otherTok, nil); code != http.StatusForbidden {
		t.Errorf("stranger deleting a photo = %d, want 403", code)
	}
	if code, _ := e.do("PUT", "/api/v1/listings/"+id+"/images/order", otherTok,
		map[string]any{"image_ids": []string{photo}}); code != http.StatusForbidden {
		t.Errorf("stranger reordering = %d, want 403", code)
	}

	// Nor can an owner reshuffle photos belonging to a different listing.
	other := e.createListing(ownerTok, "Another Listing", nil)
	if code, _ := e.do("PUT", "/api/v1/listings/"+other+"/images/order", ownerTok,
		map[string]any{"image_ids": []string{photo}}); code != http.StatusBadRequest {
		t.Errorf("cross-listing reorder = %d, want 400", code)
	}
}

// Removing a photo changes what the reviewer judged, so the badge must be
// re-earned rather than inherited.
func TestDeletingPhotoTriggersRescreening(t *testing.T) {
	e := newTestEnv(t)
	token, _ := e.register("agent@example.com", "agent")
	id := e.createListing(token, "Condo in Alabang", nil)
	photo := e.uploadPhoto(token, id)

	if err := e.listings.ApplyVerification(id, models.VerificationVerified, 90, "ok", nil, "test"); err != nil {
		t.Fatalf("verify: %v", err)
	}
	if code, _ := e.do("DELETE", "/api/v1/listings/"+id+"/images/"+photo, token, nil); code != http.StatusOK {
		t.Fatal("delete failed")
	}
	l, _ := e.listings.GetByID(id)
	if l.VerificationStatus != models.VerificationPending {
		t.Errorf("status = %q after removing a photo, want pending", l.VerificationStatus)
	}
}

// --- viewing auto-completion ---

// An owner who behaves badly could block a review of themselves simply by never
// marking the viewing complete. Time closes the loop instead.
func TestPastViewingsAutoComplete(t *testing.T) {
	e := newTestEnv(t)
	agentTok, agentID := e.register("agent@example.com", "agent")
	_, buyerID := e.register("buyer@example.com", "user")
	listingID := e.createListing(agentTok, "Condo in Alabang", nil)

	past := &models.ViewingRequest{
		ID: "v-past", ListingID: listingID, RequesterID: buyerID, OwnerID: agentID,
		ScheduledFor: time.Now().Add(-48 * time.Hour), Status: "confirmed",
	}
	recent := &models.ViewingRequest{
		ID: "v-recent", ListingID: listingID, RequesterID: buyerID, OwnerID: agentID,
		ScheduledFor: time.Now().Add(-2 * time.Hour), Status: "confirmed",
	}
	upcoming := &models.ViewingRequest{
		ID: "v-future", ListingID: listingID, RequesterID: buyerID, OwnerID: agentID,
		ScheduledFor: time.Now().Add(48 * time.Hour), Status: "confirmed",
	}
	for _, v := range []*models.ViewingRequest{past, recent, upcoming} {
		if err := e.viewings.Create(v); err != nil {
			t.Fatalf("create viewing: %v", err)
		}
	}

	n, err := e.viewings.AutoCompleteDue(24 * time.Hour)
	if err != nil {
		t.Fatalf("auto-complete: %v", err)
	}
	if n != 1 {
		t.Errorf("auto-completed %d viewings, want 1 (only the one past its grace period)", n)
	}

	want := map[string]string{"v-past": "completed", "v-recent": "confirmed", "v-future": "confirmed"}
	for id, status := range want {
		got, err := e.viewings.GetByID(id)
		if err != nil {
			t.Fatalf("load %s: %v", id, err)
		}
		if got.Status != status {
			t.Errorf("%s = %q, want %q", id, got.Status, status)
		}
	}
}

// Auto-completion must not resurrect a viewing somebody cancelled or declined.
func TestAutoCompleteIgnoresCancelledViewings(t *testing.T) {
	e := newTestEnv(t)
	agentTok, agentID := e.register("agent@example.com", "agent")
	_, buyerID := e.register("buyer@example.com", "user")
	listingID := e.createListing(agentTok, "Condo in Alabang", nil)

	for _, status := range []string{"cancelled", "declined", "requested"} {
		if err := e.viewings.Create(&models.ViewingRequest{
			ID: "v-" + status, ListingID: listingID, RequesterID: buyerID, OwnerID: agentID,
			ScheduledFor: time.Now().Add(-72 * time.Hour), Status: status,
		}); err != nil {
			t.Fatalf("create: %v", err)
		}
	}
	n, err := e.viewings.AutoCompleteDue(24 * time.Hour)
	if err != nil {
		t.Fatalf("auto-complete: %v", err)
	}
	if n != 0 {
		t.Errorf("auto-completed %d, want 0 — only confirmed viewings should close out", n)
	}
}
