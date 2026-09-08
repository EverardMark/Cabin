package store

import (
	"database/sql"
	"strings"
	"time"

	"cabin/internal/models"
)

// ReviewStore backs agent and owner ratings — asked for by 39% of respondents,
// and the answer to the "vague feedback from other users" complaint.
type ReviewStore struct {
	db *sql.DB
}

func NewReviewStore(db *sql.DB) *ReviewStore { return &ReviewStore{db: db} }

// Create records a review. A user may review another user only once; the
// unique index turns a second attempt into ErrDuplicate.
func (s *ReviewStore) Create(r *models.Review) error {
	if r.CreatedAt.IsZero() { // seed data sets its own timeline
		r.CreatedAt = time.Now().UTC()
	}
	_, err := s.db.Exec(
		`INSERT INTO reviews (id, subject_user_id, author_id, listing_id, rating, comment, created_at)
		 VALUES (?,?,?,?,?,?,?)`,
		r.ID, r.SubjectUserID, r.AuthorID, r.ListingID, r.Rating, r.Comment,
		r.CreatedAt.Format(time.RFC3339))
	if err != nil {
		msg := strings.ToLower(err.Error())
		if strings.Contains(msg, "unique") || strings.Contains(msg, "duplicate") {
			return ErrDuplicate
		}
		return err
	}
	return nil
}

// ForUser lists the reviews written about a user, newest first.
func (s *ReviewStore) ForUser(subjectID string, limit int) ([]models.Review, error) {
	if limit <= 0 || limit > 200 {
		limit = 50
	}
	rows, err := s.db.Query(
		`SELECT r.id, r.subject_user_id, r.author_id, r.listing_id, r.rating, r.comment, r.created_at,
			`+summaryColumns+`
		 FROM reviews r JOIN users s ON s.id = r.author_id
		 WHERE r.subject_user_id = ? ORDER BY r.created_at DESC LIMIT ?`, subjectID, limit)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	out := []models.Review{}
	for rows.Next() {
		var r models.Review
		var author models.UserSummary
		var comment sql.NullString
		var created string
		if err := rows.Scan(&r.ID, &r.SubjectUserID, &r.AuthorID, &r.ListingID, &r.Rating, &comment,
			&created, &author.ID, &author.Name, &author.Email, &author.Role,
			&author.VerificationStatus, &author.RatingAvg, &author.RatingCount); err != nil {
			return nil, err
		}
		r.Comment = comment.String
		r.CreatedAt, _ = time.Parse(time.RFC3339, created)
		r.Author = &author
		out = append(out, r)
	}
	return out, rows.Err()
}
