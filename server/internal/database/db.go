package database

import (
	"database/sql"
	"fmt"
	"time"

	_ "github.com/go-sql-driver/mysql" // registered as "mysql"
	_ "modernc.org/sqlite"             // pure-Go SQLite driver, registered as "sqlite"
)

// Open opens a database connection for the given driver ("sqlite" or "mysql").
// For sqlite, dsn is a file path; for mysql, dsn is a full DSN string.
func Open(driver, dsn string) (*sql.DB, error) {
	db, err := sql.Open(driver, dsn)
	if err != nil {
		return nil, fmt.Errorf("open db (%s): %w", driver, err)
	}

	switch driver {
	case "sqlite":
		// SQLite handles a single writer at a time. Serialize through one
		// connection to avoid "database is locked" errors.
		db.SetMaxOpenConns(1)
		db.SetConnMaxLifetime(time.Hour)
		for _, pragma := range []string{
			"PRAGMA journal_mode = WAL;",
			"PRAGMA busy_timeout = 5000;",
			"PRAGMA foreign_keys = ON;",
		} {
			if _, err := db.Exec(pragma); err != nil {
				return nil, fmt.Errorf("apply pragma %q: %w", pragma, err)
			}
		}
	case "mysql":
		db.SetMaxOpenConns(20)
		db.SetMaxIdleConns(5)
		db.SetConnMaxLifetime(time.Hour)
	default:
		return nil, fmt.Errorf("unsupported DB_DRIVER %q (use sqlite or mysql)", driver)
	}

	// Retry the initial connection so we tolerate the DB starting up slightly
	// after the API (e.g. mysql.service coming up in parallel under systemd).
	var pingErr error
	for i := 0; i < 15; i++ {
		if pingErr = db.Ping(); pingErr == nil {
			return db, nil
		}
		time.Sleep(time.Second)
	}
	return nil, fmt.Errorf("ping db (%s): %w", driver, pingErr)
}
