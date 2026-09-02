package handlers

import (
	"bytes"
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
	users    *store.UserStore
	listings *store.ListingStore
	viewings *store.ViewingStore
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
	return &testEnv{t: t, handler: srv.Handler(), users: users, listings: listings, viewings: viewings}
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
