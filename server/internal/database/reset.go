package database

import (
	"database/sql"
	"fmt"
)

// Reset empties every application table so the demo data set can be loaded
// again from scratch. Children go first so foreign keys never complain; the
// schema itself is left in place. Only ever run this against a demo or
// staging database.
func Reset(db *sql.DB) error {
	tables := []string{
		"messages",
		"conversations",
		"viewing_requests",
		"reviews",
		"saved_searches",
		"listing_reports",
		"listing_images",
		"listings",
		"phone_verifications",
		"users",
	}
	for _, t := range tables {
		if _, err := db.Exec("DELETE FROM " + t); err != nil {
			return fmt.Errorf("clear %s: %w", t, err)
		}
	}
	return nil
}
