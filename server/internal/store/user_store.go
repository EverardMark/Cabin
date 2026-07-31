package store

import (
	"database/sql"
	"errors"
	"strings"
	"time"

	"cabin/internal/models"
)

// Shared store errors.
var (
	ErrNotFound   = errors.New("not found")
	ErrEmailTaken = errors.New("email already registered")
)

// rowScanner is satisfied by both *sql.Row and *sql.Rows.
type rowScanner interface {
	Scan(dest ...any) error
}

// UserStore provides access to the users table.
type UserStore struct {
	db *sql.DB
}

func NewUserStore(db *sql.DB) *UserStore {
	return &UserStore{db: db}
}

// Create inserts a new user. Returns ErrEmailTaken if the email already exists.
func (s *UserStore) Create(u *models.User) error {
	_, err := s.db.Exec(
		`INSERT INTO users (id, email, name, role, password_hash, google_id, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)`,
		u.ID, u.Email, u.Name, u.Role, u.PasswordHash, u.GoogleID, u.CreatedAt.UTC().Format(time.RFC3339),
	)
	if err != nil {
		// SQLite: "UNIQUE constraint failed"; MySQL: "Error 1062: Duplicate entry".
		msg := strings.ToLower(err.Error())
		if strings.Contains(msg, "unique") || strings.Contains(msg, "duplicate") {
			return ErrEmailTaken
		}
		return err
	}
	return nil
}

// GetByEmail looks up a user by email (case-sensitive as stored).
func (s *UserStore) GetByEmail(email string) (*models.User, error) {
	row := s.db.QueryRow(
		`SELECT id, email, name, role, password_hash, google_id, created_at FROM users WHERE email = ?`, email)
	return scanUser(row)
}

// GetByID looks up a user by id.
func (s *UserStore) GetByID(id string) (*models.User, error) {
	row := s.db.QueryRow(
		`SELECT id, email, name, role, password_hash, google_id, created_at FROM users WHERE id = ?`, id)
	return scanUser(row)
}

// GetByGoogleID looks up a user by their linked Google account id ("sub").
func (s *UserStore) GetByGoogleID(googleID string) (*models.User, error) {
	row := s.db.QueryRow(
		`SELECT id, email, name, role, password_hash, google_id, created_at FROM users WHERE google_id = ?`, googleID)
	return scanUser(row)
}

// SetGoogleID links a Google account id to an existing (e.g. password) user.
func (s *UserStore) SetGoogleID(id, googleID string) error {
	res, err := s.db.Exec(`UPDATE users SET google_id = ? WHERE id = ?`, googleID, id)
	if err != nil {
		return err
	}
	if n, _ := res.RowsAffected(); n == 0 {
		return ErrNotFound
	}
	return nil
}

// Count returns the number of users (used to decide whether to seed).
func (s *UserStore) Count() (int, error) {
	var n int
	err := s.db.QueryRow(`SELECT COUNT(*) FROM users`).Scan(&n)
	return n, err
}

func scanUser(sc rowScanner) (*models.User, error) {
	var u models.User
	var created string
	if err := sc.Scan(&u.ID, &u.Email, &u.Name, &u.Role, &u.PasswordHash, &u.GoogleID, &created); err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return nil, ErrNotFound
		}
		return nil, err
	}
	u.CreatedAt, _ = time.Parse(time.RFC3339, created)
	return &u, nil
}
