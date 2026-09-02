package verify

import (
	"context"
	"log"
	"time"

	"cabin/internal/models"
)

// ListingSource is the slice of the listing store the worker needs.
type ListingSource interface {
	PendingVerification(limit int) ([]models.Listing, error)
	ApplyVerification(listingID string, status string, score int, summary string, flags []string, model string) error
}

// UserSource looks up the poster behind a listing.
type UserSource interface {
	GetByID(id string) (*models.User, error)
}

// Worker reviews listings that are waiting for verification. New listings are
// reviewed as soon as they are posted; a periodic sweep catches anything missed
// (for example a listing created while the process was down).
type Worker struct {
	svc      *Service
	listings ListingSource
	users    UserSource
	queue    chan string
	interval time.Duration
	batch    int
	// timeout bounds a single review so one slow call cannot stall the queue.
	timeout time.Duration
}

// NewWorker builds a verification worker.
func NewWorker(svc *Service, listings ListingSource, users UserSource) *Worker {
	return &Worker{
		svc:      svc,
		listings: listings,
		users:    users,
		queue:    make(chan string, 256),
		interval: 30 * time.Second,
		batch:    20,
		timeout:  90 * time.Second,
	}
}

// Enqueue asks for a listing to be reviewed soon. It never blocks: if the queue
// is full the periodic sweep will pick the listing up instead.
func (w *Worker) Enqueue(listingID string) {
	select {
	case w.queue <- listingID:
	default:
	}
}

// Run processes the queue until ctx is cancelled. Call it in a goroutine.
func (w *Worker) Run(ctx context.Context) {
	log.Printf("verification worker started (reviewer=%s)", w.svc.ModelName())
	ticker := time.NewTicker(w.interval)
	defer ticker.Stop()

	// Sweep once at startup so a restart clears any backlog.
	w.sweep(ctx)

	for {
		select {
		case <-ctx.Done():
			log.Println("verification worker stopped")
			return
		case <-w.queue:
			// A listing was just posted or edited; review whatever is pending.
			w.sweep(ctx)
		case <-ticker.C:
			w.sweep(ctx)
		}
	}
}

// sweep reviews one batch of pending listings.
func (w *Worker) sweep(ctx context.Context) {
	pending, err := w.listings.PendingVerification(w.batch)
	if err != nil {
		log.Printf("verification: load pending: %v", err)
		return
	}
	for i := range pending {
		if ctx.Err() != nil {
			return
		}
		w.review(ctx, &pending[i])
	}
}

func (w *Worker) review(ctx context.Context, l *models.Listing) {
	owner, err := w.users.GetByID(l.UserID)
	if err != nil {
		// Not fatal: review the listing without poster context.
		owner = nil
	}

	reviewCtx, cancel := context.WithTimeout(ctx, w.timeout)
	defer cancel()

	verdict, err := w.svc.ReviewListing(reviewCtx, l, owner)
	if err != nil {
		// Leave the listing pending so the next sweep retries it, but fall back
		// to the heuristic so a buyer is never left with no signal at all.
		log.Printf("verification: review %s failed (%v); applying heuristic", l.ID, err)
		verdict = heuristicReview(l)
		verdict.Model = "heuristic-fallback"
	}

	if err := w.listings.ApplyVerification(
		l.ID, verdict.Status, verdict.Score, verdict.Summary, verdict.Flags, verdict.Model,
	); err != nil {
		log.Printf("verification: save %s: %v", l.ID, err)
		return
	}
	log.Printf("verification: %s -> %s (score=%d, reviewer=%s)", l.ID, verdict.Status, verdict.Score, verdict.Model)
}
