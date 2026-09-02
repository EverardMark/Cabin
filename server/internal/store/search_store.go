package store

import (
	"database/sql"
	"time"

	"cabin/internal/models"
)

// SearchStore persists saved searches so buyers can re-run a filter and see how
// many new listings have matched since they last looked — the "saved searches
// and instant alerts" ask from the survey's free-text answers.
type SearchStore struct {
	db *sql.DB
}

func NewSearchStore(db *sql.DB) *SearchStore { return &SearchStore{db: db} }

// Create stores a saved search.
func (s *SearchStore) Create(ss *models.SavedSearch) error {
	ss.CreatedAt = time.Now().UTC()
	ss.LastAlertedAt = ss.CreatedAt
	_, err := s.db.Exec(
		`INSERT INTO saved_searches (id, user_id, name, query_json, alerts_enabled, last_alerted_at, created_at)
		 VALUES (?,?,?,?,?,?,?)`,
		ss.ID, ss.UserID, ss.Name, ss.Query, boolToInt(ss.AlertsEnabled),
		ss.LastAlertedAt.Format(time.RFC3339), ss.CreatedAt.Format(time.RFC3339))
	return err
}

// ForUser lists a user's saved searches, newest first.
func (s *SearchStore) ForUser(userID string) ([]models.SavedSearch, error) {
	rows, err := s.db.Query(
		`SELECT id, user_id, name, query_json, alerts_enabled, last_alerted_at, created_at
		 FROM saved_searches WHERE user_id = ? ORDER BY created_at DESC`, userID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	out := []models.SavedSearch{}
	for rows.Next() {
		var ss models.SavedSearch
		var alerts int
		var lastAlerted, created string
		if err := rows.Scan(&ss.ID, &ss.UserID, &ss.Name, &ss.Query, &alerts, &lastAlerted, &created); err != nil {
			return nil, err
		}
		ss.AlertsEnabled = alerts != 0
		ss.CreatedAt, _ = time.Parse(time.RFC3339, created)
		if t := parseOptionalTime(lastAlerted); t != nil {
			ss.LastAlertedAt = *t
		} else {
			ss.LastAlertedAt = ss.CreatedAt
		}
		out = append(out, ss)
	}
	return out, rows.Err()
}

// GetByID loads one saved search.
func (s *SearchStore) GetByID(id string) (*models.SavedSearch, error) {
	var ss models.SavedSearch
	var alerts int
	var lastAlerted, created string
	err := s.db.QueryRow(
		`SELECT id, user_id, name, query_json, alerts_enabled, last_alerted_at, created_at
		 FROM saved_searches WHERE id = ?`, id,
	).Scan(&ss.ID, &ss.UserID, &ss.Name, &ss.Query, &alerts, &lastAlerted, &created)
	if err != nil {
		if err == sql.ErrNoRows {
			return nil, ErrNotFound
		}
		return nil, err
	}
	ss.AlertsEnabled = alerts != 0
	ss.CreatedAt, _ = time.Parse(time.RFC3339, created)
	if t := parseOptionalTime(lastAlerted); t != nil {
		ss.LastAlertedAt = *t
	} else {
		ss.LastAlertedAt = ss.CreatedAt
	}
	return &ss, nil
}

// Delete removes a saved search.
func (s *SearchStore) Delete(id string) error {
	res, err := s.db.Exec(`DELETE FROM saved_searches WHERE id = ?`, id)
	if err != nil {
		return err
	}
	if n, _ := res.RowsAffected(); n == 0 {
		return ErrNotFound
	}
	return nil
}

// MarkSeen records that the user has just looked at a saved search's results,
// so the "new matches" badge resets.
func (s *SearchStore) MarkSeen(id string) error {
	_, err := s.db.Exec(`UPDATE saved_searches SET last_alerted_at = ? WHERE id = ?`,
		time.Now().UTC().Format(time.RFC3339), id)
	return err
}
