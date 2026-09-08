package store

import (
	"database/sql"
	"time"

	"cabin/internal/models"
)

// ChatStore backs in-app messaging between an inquirer and a listing owner —
// the survey's second-most requested feature (59% of respondents).
type ChatStore struct {
	db *sql.DB
}

func NewChatStore(db *sql.DB) *ChatStore { return &ChatStore{db: db} }

// summaryColumns is the joined public view of a user, aliased as `s`.
const summaryColumns = `s.id, s.name, s.email, s.role, s.verification_status, s.rating_avg, s.rating_count`

func scanSummary(sc rowScanner, dst *models.UserSummary) error {
	return sc.Scan(&dst.ID, &dst.Name, &dst.Email, &dst.Role, &dst.VerificationStatus,
		&dst.RatingAvg, &dst.RatingCount)
}

// StartConversation returns the existing thread for (listing, inquirer) or
// creates one. An owner cannot open a thread against their own listing.
func (s *ChatStore) StartConversation(id, listingID, inquirerID, ownerID string) (*models.Conversation, error) {
	existing, err := s.findByListingInquirer(listingID, inquirerID)
	if err == nil {
		return existing, nil
	}
	if err != ErrNotFound {
		return nil, err
	}

	now := time.Now().UTC()
	if _, err := s.db.Exec(
		`INSERT INTO conversations (id, listing_id, inquirer_id, owner_id, last_message_at, created_at)
		 VALUES (?,?,?,?,?,?)`,
		id, listingID, inquirerID, ownerID, "", now.Format(time.RFC3339),
	); err != nil {
		return nil, err
	}
	return &models.Conversation{
		ID: id, ListingID: listingID, InquirerID: inquirerID, OwnerID: ownerID, CreatedAt: now,
	}, nil
}

func (s *ChatStore) findByListingInquirer(listingID, inquirerID string) (*models.Conversation, error) {
	row := s.db.QueryRow(
		`SELECT id, listing_id, inquirer_id, owner_id, last_message_at, created_at
		 FROM conversations WHERE listing_id = ? AND inquirer_id = ?`, listingID, inquirerID)
	return scanConversation(row)
}

// GetByID loads a single conversation.
func (s *ChatStore) GetByID(id string) (*models.Conversation, error) {
	row := s.db.QueryRow(
		`SELECT id, listing_id, inquirer_id, owner_id, last_message_at, created_at
		 FROM conversations WHERE id = ?`, id)
	return scanConversation(row)
}

// ForUser lists a user's conversations, most recently active first, with the
// counterparty, the last message and the unread count attached.
func (s *ChatStore) ForUser(userID string) ([]models.Conversation, error) {
	rows, err := s.db.Query(
		`SELECT id, listing_id, inquirer_id, owner_id, last_message_at, created_at
		 FROM conversations WHERE inquirer_id = ? OR owner_id = ?
		 ORDER BY CASE WHEN last_message_at <> '' THEN last_message_at ELSE created_at END DESC`,
		userID, userID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	out := []models.Conversation{}
	for rows.Next() {
		c, err := scanConversation(rows)
		if err != nil {
			return nil, err
		}
		out = append(out, *c)
	}
	if err := rows.Err(); err != nil {
		return nil, err
	}

	// Enrich after draining, so the single SQLite connection is free.
	for i := range out {
		otherID := out[i].OwnerID
		if otherID == userID {
			otherID = out[i].InquirerID
		}
		if sum, err := s.summary(otherID); err == nil {
			out[i].Counterparty = sum
		}
		if msg, err := s.lastMessage(out[i].ID); err == nil {
			out[i].LastMessage = msg
		}
		n, err := s.unreadCount(out[i].ID, userID)
		if err != nil {
			return nil, err
		}
		out[i].UnreadCount = n
	}
	return out, nil
}

func (s *ChatStore) summary(userID string) (*models.UserSummary, error) {
	var sum models.UserSummary
	err := scanSummary(s.db.QueryRow(
		`SELECT `+summaryColumns+` FROM users s WHERE s.id = ?`, userID), &sum)
	if err != nil {
		return nil, err
	}
	return &sum, nil
}

func (s *ChatStore) lastMessage(conversationID string) (*models.Message, error) {
	row := s.db.QueryRow(
		`SELECT id, conversation_id, sender_id, body, read_at, created_at
		 FROM messages WHERE conversation_id = ? ORDER BY created_at DESC LIMIT 1`, conversationID)
	return scanMessage(row)
}

func (s *ChatStore) unreadCount(conversationID, readerID string) (int, error) {
	var n int
	err := s.db.QueryRow(
		`SELECT COUNT(*) FROM messages WHERE conversation_id = ? AND sender_id <> ? AND read_at = ''`,
		conversationID, readerID).Scan(&n)
	return n, err
}

// UnreadTotal counts every unread message addressed to a user.
func (s *ChatStore) UnreadTotal(userID string) (int, error) {
	var n int
	err := s.db.QueryRow(
		`SELECT COUNT(*) FROM messages m JOIN conversations c ON c.id = m.conversation_id
		 WHERE (c.inquirer_id = ? OR c.owner_id = ?) AND m.sender_id <> ? AND m.read_at = ''`,
		userID, userID, userID).Scan(&n)
	return n, err
}

// Messages returns a conversation's messages, oldest first.
func (s *ChatStore) Messages(conversationID string, limit int) ([]models.Message, error) {
	if limit <= 0 || limit > 500 {
		limit = 200
	}
	rows, err := s.db.Query(
		`SELECT id, conversation_id, sender_id, body, read_at, created_at
		 FROM messages WHERE conversation_id = ? ORDER BY created_at ASC LIMIT ?`,
		conversationID, limit)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	out := []models.Message{}
	for rows.Next() {
		m, err := scanMessage(rows)
		if err != nil {
			return nil, err
		}
		out = append(out, *m)
	}
	return out, rows.Err()
}

// Send appends a message and bumps the conversation's activity timestamp.
func (s *ChatStore) Send(m *models.Message) error {
	if m.CreatedAt.IsZero() { // seed data sets its own timeline
		m.CreatedAt = time.Now().UTC()
	}
	stamp := m.CreatedAt.Format(time.RFC3339)
	if _, err := s.db.Exec(
		`INSERT INTO messages (id, conversation_id, sender_id, body, read_at, created_at)
		 VALUES (?,?,?,?,'',?)`,
		m.ID, m.ConversationID, m.SenderID, m.Body, stamp,
	); err != nil {
		return err
	}
	_, err := s.db.Exec(`UPDATE conversations SET last_message_at = ? WHERE id = ?`, stamp, m.ConversationID)
	return err
}

// MarkRead marks every message the reader did not send as read.
func (s *ChatStore) MarkRead(conversationID, readerID string) error {
	_, err := s.db.Exec(
		`UPDATE messages SET read_at = ? WHERE conversation_id = ? AND sender_id <> ? AND read_at = ''`,
		time.Now().UTC().Format(time.RFC3339), conversationID, readerID)
	return err
}

func scanConversation(sc rowScanner) (*models.Conversation, error) {
	var c models.Conversation
	var lastMessageAt, created string
	if err := sc.Scan(&c.ID, &c.ListingID, &c.InquirerID, &c.OwnerID, &lastMessageAt, &created); err != nil {
		if err == sql.ErrNoRows {
			return nil, ErrNotFound
		}
		return nil, err
	}
	c.CreatedAt, _ = time.Parse(time.RFC3339, created)
	c.LastMessageAt = parseOptionalTime(lastMessageAt)
	return &c, nil
}

func scanMessage(sc rowScanner) (*models.Message, error) {
	var m models.Message
	var readAt, created string
	if err := sc.Scan(&m.ID, &m.ConversationID, &m.SenderID, &m.Body, &readAt, &created); err != nil {
		if err == sql.ErrNoRows {
			return nil, ErrNotFound
		}
		return nil, err
	}
	m.CreatedAt, _ = time.Parse(time.RFC3339, created)
	m.ReadAt = parseOptionalTime(readAt)
	return &m, nil
}
