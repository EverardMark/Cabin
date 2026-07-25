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
		`INSERT INTO users (id, email, name, password_hash, created_at) VALUES (?, ?, ?, ?, ?)`,
		u.ID, u.Email, u.Name, u.PasswordHash, u.CreatedAt.UTC().Format(time.RFC3339),
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
		`SELECT id, email, name, password_hash, verified, created_at FROM users WHERE email = ?`, email)
	return scanUser(row)
}

// GetByID looks up a user by id.
func (s *UserStore) GetByID(id string) (*models.User, error) {
	row := s.db.QueryRow(
		`SELECT id, email, name, password_hash, verified, created_at FROM users WHERE id = ?`, id)
	return scanUser(row)
}

// SetVerified marks (or unmarks) a user as verified.
func (s *UserStore) SetVerified(id string, verified bool) error {
	v := 0
	if verified {
		v = 1
	}
	res, err := s.db.Exec(`UPDATE users SET verified = ? WHERE id = ?`, v, id)
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
	var verified int
	if err := sc.Scan(&u.ID, &u.Email, &u.Name, &u.PasswordHash, &verified, &created); err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return nil, ErrNotFound
		}
		return nil, err
	}
	u.Verified = verified != 0
	u.CreatedAt, _ = time.Parse(time.RFC3339, created)
	return &u, nil
}
