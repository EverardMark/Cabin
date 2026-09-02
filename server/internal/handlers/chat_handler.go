package handlers

import (
	"net/http"
	"strings"

	"cabin/internal/models"
	"cabin/internal/store"

	"github.com/google/uuid"
)

// handleStartConversation opens (or reuses) a chat thread with a listing's
// owner. In-app chat was the survey's second-most requested feature, and it
// keeps the conversation off the messaging apps where scams start.
func (s *Server) handleStartConversation(w http.ResponseWriter, r *http.Request) {
	listingID := r.PathValue("id")
	userID := userIDFrom(r.Context())

	listing, err := s.listings.GetByID(listingID)
	if err != nil {
		writeError(w, http.StatusNotFound, "listing not found")
		return
	}
	if listing.UserID == userID {
		writeError(w, http.StatusBadRequest, "you cannot message yourself about your own listing")
		return
	}
	// A rejected listing is a scam risk; don't help start a conversation on it.
	if listing.VerificationStatus == models.VerificationRejected {
		writeError(w, http.StatusForbidden, "this listing is under review and cannot be contacted")
		return
	}

	conv, err := s.chat.StartConversation(uuid.NewString(), listingID, userID, listing.UserID)
	if err != nil {
		writeError(w, http.StatusInternalServerError, "could not start conversation")
		return
	}
	conv.Listing = listing
	writeJSON(w, http.StatusCreated, conv)
}

func (s *Server) handleListConversations(w http.ResponseWriter, r *http.Request) {
	userID := userIDFrom(r.Context())
	convs, err := s.chat.ForUser(userID)
	if err != nil {
		writeError(w, http.StatusInternalServerError, "could not load conversations")
		return
	}
	// Attach the listing each thread is about, so the list can render cards.
	for i := range convs {
		if l, err := s.listings.GetByID(convs[i].ListingID); err == nil {
			convs[i].Listing = l
		}
	}
	writeJSON(w, http.StatusOK, map[string]any{"conversations": convs})
}

// conversationFor loads a conversation and confirms the caller is a participant.
func (s *Server) conversationFor(w http.ResponseWriter, r *http.Request) (*models.Conversation, bool) {
	conv, err := s.chat.GetByID(r.PathValue("id"))
	if err != nil {
		if err == store.ErrNotFound {
			writeError(w, http.StatusNotFound, "conversation not found")
		} else {
			writeError(w, http.StatusInternalServerError, "could not load conversation")
		}
		return nil, false
	}
	userID := userIDFrom(r.Context())
	if conv.InquirerID != userID && conv.OwnerID != userID {
		// Don't reveal that the thread exists to someone not in it.
		writeError(w, http.StatusNotFound, "conversation not found")
		return nil, false
	}
	return conv, true
}

func (s *Server) handleListMessages(w http.ResponseWriter, r *http.Request) {
	conv, ok := s.conversationFor(w, r)
	if !ok {
		return
	}
	msgs, err := s.chat.Messages(conv.ID, atoiDefault(r.URL.Query().Get("limit"), 200))
	if err != nil {
		writeError(w, http.StatusInternalServerError, "could not load messages")
		return
	}
	// Opening a thread marks the other side's messages as read.
	if err := s.chat.MarkRead(conv.ID, userIDFrom(r.Context())); err != nil {
		writeError(w, http.StatusInternalServerError, "could not update read state")
		return
	}
	writeJSON(w, http.StatusOK, map[string]any{"conversation": conv, "messages": msgs})
}

type messageInput struct {
	Body string `json:"body"`
}

func (s *Server) handleSendMessage(w http.ResponseWriter, r *http.Request) {
	conv, ok := s.conversationFor(w, r)
	if !ok {
		return
	}
	var in messageInput
	if err := decodeJSON(w, r, &in); err != nil {
		writeError(w, http.StatusBadRequest, "invalid JSON body")
		return
	}
	body := strings.TrimSpace(in.Body)
	if body == "" {
		writeError(w, http.StatusBadRequest, "message body is required")
		return
	}
	if len(body) > 4000 {
		writeError(w, http.StatusBadRequest, "message is too long (max 4000 characters)")
		return
	}

	msg := &models.Message{
		ID:             uuid.NewString(),
		ConversationID: conv.ID,
		SenderID:       userIDFrom(r.Context()),
		Body:           body,
	}
	if err := s.chat.Send(msg); err != nil {
		writeError(w, http.StatusInternalServerError, "could not send message")
		return
	}
	writeJSON(w, http.StatusCreated, msg)
}
