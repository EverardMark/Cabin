package store

import (
	"database/sql"
	"time"

	"cabin/internal/models"
)

// ViewingStore backs in-app viewing appointments. 57% of surveyed respondents
// asked for scheduling, and "scheduling viewings" was the fourth-largest cause
// of transaction delays.
type ViewingStore struct {
	db *sql.DB
}

func NewViewingStore(db *sql.DB) *ViewingStore { return &ViewingStore{db: db} }

const viewingColumns = `id, listing_id, requester_id, owner_id, scheduled_for, status, note,
	response_note, created_at, updated_at`

// Create records a new viewing request.
func (s *ViewingStore) Create(v *models.ViewingRequest) error {
	now := time.Now().UTC()
	v.CreatedAt = now
	v.UpdatedAt = now
	if v.Status == "" {
		v.Status = "requested"
	}
	_, err := s.db.Exec(
		`INSERT INTO viewing_requests (`+viewingColumns+`) VALUES (?,?,?,?,?,?,?,?,?,?)`,
		v.ID, v.ListingID, v.RequesterID, v.OwnerID, v.ScheduledFor.UTC().Format(time.RFC3339),
		v.Status, v.Note, v.ResponseNote, now.Format(time.RFC3339), now.Format(time.RFC3339),
	)
	return err
}

// GetByID loads one viewing request.
func (s *ViewingStore) GetByID(id string) (*models.ViewingRequest, error) {
	return scanViewing(s.db.QueryRow(`SELECT `+viewingColumns+` FROM viewing_requests WHERE id = ?`, id))
}

// ForUser lists viewings where the user is either side, soonest first.
func (s *ViewingStore) ForUser(userID string) ([]models.ViewingRequest, error) {
	rows, err := s.db.Query(
		`SELECT `+viewingColumns+` FROM viewing_requests
		 WHERE requester_id = ? OR owner_id = ? ORDER BY scheduled_for ASC`, userID, userID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	out := []models.ViewingRequest{}
	for rows.Next() {
		v, err := scanViewing(rows)
		if err != nil {
			return nil, err
		}
		out = append(out, *v)
	}
	return out, rows.Err()
}

// SlotTaken reports whether the owner already has a confirmed viewing that
// overlaps the requested time, so double-booking is caught before it happens.
func (s *ViewingStore) SlotTaken(ownerID string, at time.Time) (bool, error) {
	// Treat a viewing as occupying a one-hour slot.
	from := at.Add(-59 * time.Minute).UTC().Format(time.RFC3339)
	to := at.Add(59 * time.Minute).UTC().Format(time.RFC3339)
	var n int
	err := s.db.QueryRow(
		`SELECT COUNT(*) FROM viewing_requests
		 WHERE owner_id = ? AND status = 'confirmed' AND scheduled_for BETWEEN ? AND ?`,
		ownerID, from, to).Scan(&n)
	return n > 0, err
}

// UpdateStatus moves a viewing through its lifecycle.
func (s *ViewingStore) UpdateStatus(id, status, responseNote string) error {
	res, err := s.db.Exec(
		`UPDATE viewing_requests SET status = ?, response_note = ?, updated_at = ? WHERE id = ?`,
		status, responseNote, time.Now().UTC().Format(time.RFC3339), id)
	if err != nil {
		return err
	}
	if n, _ := res.RowsAffected(); n == 0 {
		return ErrNotFound
	}
	return nil
}

// Reschedule moves a viewing to a new time and returns it to "requested" so the
// other side has to accept the new slot.
func (s *ViewingStore) Reschedule(id string, at time.Time) error {
	res, err := s.db.Exec(
		`UPDATE viewing_requests SET scheduled_for = ?, status = 'requested', updated_at = ? WHERE id = ?`,
		at.UTC().Format(time.RFC3339), time.Now().UTC().Format(time.RFC3339), id)
	if err != nil {
		return err
	}
	if n, _ := res.RowsAffected(); n == 0 {
		return ErrNotFound
	}
	return nil
}

// AutoCompleteDue marks confirmed viewings as completed once their slot is well
// past, and reports how many it moved.
//
// Completion is what unlocks reviews. Leaving it solely in the owner's hands
// gave a badly-behaved owner a one-tap way to block a review of themselves, so
// time closes the loop instead.
func (s *ViewingStore) AutoCompleteDue(grace time.Duration) (int, error) {
	cutoff := time.Now().Add(-grace).UTC().Format(time.RFC3339)
	res, err := s.db.Exec(
		`UPDATE viewing_requests SET status = 'completed', updated_at = ?
		 WHERE status = 'confirmed' AND scheduled_for < ?`,
		time.Now().UTC().Format(time.RFC3339), cutoff)
	if err != nil {
		return 0, err
	}
	n, _ := res.RowsAffected()
	return int(n), nil
}

// CompletedBetween reports whether the two users ever completed a viewing
// together — the precondition for leaving a review.
func (s *ViewingStore) CompletedBetween(requesterID, ownerID string) (bool, error) {
	var n int
	err := s.db.QueryRow(
		`SELECT COUNT(*) FROM viewing_requests
		 WHERE requester_id = ? AND owner_id = ? AND status = 'completed'`,
		requesterID, ownerID).Scan(&n)
	return n > 0, err
}

func scanViewing(sc rowScanner) (*models.ViewingRequest, error) {
	var v models.ViewingRequest
	var scheduled, created, updated string
	var note, responseNote sql.NullString
	if err := sc.Scan(&v.ID, &v.ListingID, &v.RequesterID, &v.OwnerID, &scheduled, &v.Status,
		&note, &responseNote, &created, &updated); err != nil {
		if err == sql.ErrNoRows {
			return nil, ErrNotFound
		}
		return nil, err
	}
	v.Note = note.String
	v.ResponseNote = responseNote.String
	v.ScheduledFor, _ = time.Parse(time.RFC3339, scheduled)
	v.CreatedAt, _ = time.Parse(time.RFC3339, created)
	v.UpdatedAt, _ = time.Parse(time.RFC3339, updated)
	return &v, nil
}
