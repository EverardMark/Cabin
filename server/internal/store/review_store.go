package store

import (
	"database/sql"
	"errors"
	"math"
	"strings"
	"time"

	"cabin/internal/models"
)

// ErrDuplicateReview is returned when an author reviews the same subject twice.
var ErrDuplicateReview = errors.New("you have already reviewed this user")

// ReviewStore provides access to the reviews table (ratings of owners/agents).
type ReviewStore struct {
	db *sql.DB
}

func NewReviewStore(db *sql.DB) *ReviewStore {
	return &ReviewStore{db: db}
}

// Add inserts a review. Returns ErrDuplicateReview if the author already
// reviewed this subject.
func (s *ReviewStore) Add(r *models.Review) error {
	r.CreatedAt = time.Now().UTC()
	_, err := s.db.Exec(
		`INSERT INTO reviews (id, subject_user_id, author_user_id, rating, comment, created_at)
		 VALUES (?, ?, ?, ?, ?, ?)`,
		r.ID, r.SubjectID, r.AuthorID, r.Rating, r.Comment, r.CreatedAt.Format(time.RFC3339),
	)
	if err != nil {
		msg := strings.ToLower(err.Error())
		if strings.Contains(msg, "unique") || strings.Contains(msg, "duplicate") {
			return ErrDuplicateReview
		}
		return err
	}
	return nil
}

// ListForSubject returns reviews written about a user, newest first.
func (s *ReviewStore) ListForSubject(subjectID string) ([]models.Review, error) {
	rows, err := s.db.Query(
		`SELECT r.id, r.subject_user_id, r.author_user_id, u.name, r.rating, r.comment, r.created_at
		 FROM reviews r JOIN users u ON u.id = r.author_user_id
		 WHERE r.subject_user_id = ?
		 ORDER BY r.created_at DESC`, subjectID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	reviews := []models.Review{}
	for rows.Next() {
		var rv models.Review
		var created string
		if err := rows.Scan(&rv.ID, &rv.SubjectID, &rv.AuthorID, &rv.AuthorName, &rv.Rating, &rv.Comment, &created); err != nil {
			return nil, err
		}
		rv.CreatedAt, _ = time.Parse(time.RFC3339, created)
		reviews = append(reviews, rv)
	}
	return reviews, rows.Err()
}

// Aggregate returns the average rating (rounded to 1 decimal) and count for a subject.
func (s *ReviewStore) Aggregate(subjectID string) (avg float64, count int, err error) {
	var a sql.NullFloat64
	err = s.db.QueryRow(
		`SELECT AVG(rating), COUNT(*) FROM reviews WHERE subject_user_id = ?`, subjectID,
	).Scan(&a, &count)
	if err != nil {
		return 0, 0, err
	}
	if a.Valid {
		avg = math.Round(a.Float64*10) / 10
	}
	return avg, count, nil
}
