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
	ErrDuplicate  = errors.New("already exists")
)

// rowScanner is satisfied by both *sql.Row and *sql.Rows.
type rowScanner interface {
	Scan(dest ...any) error
}

// userColumns is the shared SELECT list for users.
const userColumns = `id, email, name, role, password_hash, google_id, phone, bio, license_no,
	email_verified, phone_verified, verification_status, verification_score, verification_notes,
	verified_at, rating_avg, rating_count, created_at`

// UserStore provides access to the users table.
type UserStore struct {
	db *sql.DB
}

func NewUserStore(db *sql.DB) *UserStore {
	return &UserStore{db: db}
}

// Create inserts a new user. Returns ErrEmailTaken if the email already exists.
func (s *UserStore) Create(u *models.User) error {
	if u.VerificationStatus == "" {
		u.VerificationStatus = models.VerificationUnverified
	}
	_, err := s.db.Exec(
		`INSERT INTO users (id, email, name, role, password_hash, google_id, phone, bio, license_no,
			email_verified, phone_verified, verification_status, verification_score, verification_notes,
			verified_at, rating_avg, rating_count, created_at)
		 VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)`,
		u.ID, u.Email, u.Name, u.Role, u.PasswordHash, u.GoogleID, u.Phone, u.Bio, u.LicenseNo,
		boolToInt(u.EmailVerified), boolToInt(u.PhoneVerified), u.VerificationStatus,
		u.VerificationScore, u.VerificationNotes, formatOptionalTime(u.VerifiedAt),
		u.RatingAvg, u.RatingCount, u.CreatedAt.UTC().Format(time.RFC3339),
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
	return scanUser(s.db.QueryRow(`SELECT `+userColumns+` FROM users WHERE email = ?`, email))
}

// GetByID looks up a user by id.
func (s *UserStore) GetByID(id string) (*models.User, error) {
	return scanUser(s.db.QueryRow(`SELECT `+userColumns+` FROM users WHERE id = ?`, id))
}

// GetByGoogleID looks up a user by their linked Google account id ("sub").
func (s *UserStore) GetByGoogleID(googleID string) (*models.User, error) {
	return scanUser(s.db.QueryRow(`SELECT `+userColumns+` FROM users WHERE google_id = ?`, googleID))
}

// SetGoogleID links a Google account id to an existing (e.g. password) user.
// A Google sign-in also proves the email address.
func (s *UserStore) SetGoogleID(id, googleID string) error {
	res, err := s.db.Exec(`UPDATE users SET google_id = ?, email_verified = 1 WHERE id = ?`, googleID, id)
	if err != nil {
		return err
	}
	if n, _ := res.RowsAffected(); n == 0 {
		return ErrNotFound
	}
	return nil
}

// UpdateProfile writes the user-editable profile fields.
func (s *UserStore) UpdateProfile(u *models.User) error {
	res, err := s.db.Exec(
		`UPDATE users SET name = ?, phone = ?, bio = ?, license_no = ?, role = ? WHERE id = ?`,
		u.Name, u.Phone, u.Bio, u.LicenseNo, u.Role, u.ID)
	if err != nil {
		return err
	}
	if n, _ := res.RowsAffected(); n == 0 {
		return ErrNotFound
	}
	return nil
}

// SetVerification records the outcome of identity verification for a user.
func (s *UserStore) SetVerification(id, status string, score int, notes string) error {
	verifiedAt := ""
	if status == models.VerificationVerified {
		verifiedAt = time.Now().UTC().Format(time.RFC3339)
	}
	res, err := s.db.Exec(
		`UPDATE users SET verification_status = ?, verification_score = ?, verification_notes = ?, verified_at = ?
		 WHERE id = ?`, status, score, notes, verifiedAt, id)
	if err != nil {
		return err
	}
	if n, _ := res.RowsAffected(); n == 0 {
		return ErrNotFound
	}
	return nil
}

// MarkPhoneVerified flags the user's phone number as confirmed.
func (s *UserStore) MarkPhoneVerified(id string) error {
	_, err := s.db.Exec(`UPDATE users SET phone_verified = 1 WHERE id = ?`, id)
	return err
}

// RefreshRating recomputes a user's cached rating aggregate from the reviews
// table, so listing cards can show it without a join.
func (s *UserStore) RefreshRating(userID string) error {
	var avg sql.NullFloat64
	var count int
	if err := s.db.QueryRow(
		`SELECT AVG(rating), COUNT(*) FROM reviews WHERE subject_user_id = ?`, userID,
	).Scan(&avg, &count); err != nil {
		return err
	}
	_, err := s.db.Exec(`UPDATE users SET rating_avg = ?, rating_count = ? WHERE id = ?`,
		avg.Float64, count, userID)
	return err
}

// Count returns the number of users (used to decide whether to seed).
func (s *UserStore) Count() (int, error) {
	var n int
	err := s.db.QueryRow(`SELECT COUNT(*) FROM users`).Scan(&n)
	return n, err
}

func scanUser(sc rowScanner) (*models.User, error) {
	var u models.User
	var created, verifiedAt string
	var emailVerified, phoneVerified int
	var bio, notes sql.NullString

	if err := sc.Scan(
		&u.ID, &u.Email, &u.Name, &u.Role, &u.PasswordHash, &u.GoogleID, &u.Phone, &bio, &u.LicenseNo,
		&emailVerified, &phoneVerified, &u.VerificationStatus, &u.VerificationScore, &notes,
		&verifiedAt, &u.RatingAvg, &u.RatingCount, &created,
	); err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return nil, ErrNotFound
		}
		return nil, err
	}
	u.Bio = bio.String
	u.VerificationNotes = notes.String
	u.EmailVerified = emailVerified != 0
	u.PhoneVerified = phoneVerified != 0
	u.CreatedAt, _ = time.Parse(time.RFC3339, created)
	u.VerifiedAt = parseOptionalTime(verifiedAt)
	return &u, nil
}

func boolToInt(b bool) int {
	if b {
		return 1
	}
	return 0
}

func formatOptionalTime(t *time.Time) string {
	if t == nil {
		return ""
	}
	return t.UTC().Format(time.RFC3339)
}
