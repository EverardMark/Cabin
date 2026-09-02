package database

import (
	"database/sql"
	_ "embed"
	"fmt"
	"strings"
)

//go:embed schema_sqlite.sql
var schemaSQLite string

//go:embed schema_mysql.sql
var schemaMySQL string

// Migrate applies the schema for the given driver. It is idempotent
// (CREATE ... IF NOT EXISTS + guarded ALTERs), so it is safe to run on every
// startup, including against an existing database from an earlier version.
func Migrate(db *sql.DB, driver string) error {
	schema := schemaSQLite
	if driver == "mysql" {
		schema = schemaMySQL
	}
	for _, stmt := range splitStatements(schema) {
		if _, err := db.Exec(stmt); err != nil {
			return fmt.Errorf("apply schema statement: %w\n--- statement ---\n%s", err, stmt)
		}
	}
	return applyColumnMigrations(db, driver)
}

// applyColumnMigrations adds columns introduced after the initial schema to
// pre-existing tables. Neither SQLite nor MySQL supports "ADD COLUMN IF NOT
// EXISTS" portably, so we run the ALTER and ignore "duplicate column" errors.
func applyColumnMigrations(db *sql.DB, driver string) error {
	mysql := driver == "mysql"
	pick := func(sqliteType, mysqlType string) string {
		if mysql {
			return mysqlType
		}
		return sqliteType
	}
	text := pick("TEXT", "VARCHAR(255)")
	shortText := pick("TEXT", "VARCHAR(64)")
	longText := pick("TEXT", "TEXT")
	role := pick("TEXT", "VARCHAR(20)")
	stamp := pick("TEXT", "VARCHAR(40)")
	boolean := pick("INTEGER", "TINYINT(1)")
	integer := pick("INTEGER", "INT")
	real := pick("REAL", "DOUBLE")

	alters := []string{
		fmt.Sprintf("ALTER TABLE users ADD COLUMN google_id %s NOT NULL DEFAULT ''", text),
		fmt.Sprintf("ALTER TABLE users ADD COLUMN role %s NOT NULL DEFAULT 'user'", role),
		// Trust & profile columns (survey: 86% rate verification "extremely important").
		fmt.Sprintf("ALTER TABLE users ADD COLUMN phone %s NOT NULL DEFAULT ''", shortText),
		fmt.Sprintf("ALTER TABLE users ADD COLUMN bio %s NULL", longText),
		fmt.Sprintf("ALTER TABLE users ADD COLUMN license_no %s NOT NULL DEFAULT ''", shortText),
		fmt.Sprintf("ALTER TABLE users ADD COLUMN email_verified %s NOT NULL DEFAULT 0", boolean),
		fmt.Sprintf("ALTER TABLE users ADD COLUMN phone_verified %s NOT NULL DEFAULT 0", boolean),
		fmt.Sprintf("ALTER TABLE users ADD COLUMN verification_status %s NOT NULL DEFAULT 'unverified'", role),
		fmt.Sprintf("ALTER TABLE users ADD COLUMN verification_score %s NOT NULL DEFAULT 0", integer),
		fmt.Sprintf("ALTER TABLE users ADD COLUMN verification_notes %s NULL", longText),
		fmt.Sprintf("ALTER TABLE users ADD COLUMN verified_at %s NOT NULL DEFAULT ''", stamp),
		fmt.Sprintf("ALTER TABLE users ADD COLUMN rating_avg %s NOT NULL DEFAULT 0", real),
		fmt.Sprintf("ALTER TABLE users ADD COLUMN rating_count %s NOT NULL DEFAULT 0", integer),
		// Listing verification, freshness and moderation counters.
		fmt.Sprintf("ALTER TABLE listings ADD COLUMN verification_status %s NOT NULL DEFAULT 'pending'", role),
		fmt.Sprintf("ALTER TABLE listings ADD COLUMN verification_score %s NOT NULL DEFAULT 0", integer),
		fmt.Sprintf("ALTER TABLE listings ADD COLUMN verification_summary %s NULL", longText),
		fmt.Sprintf("ALTER TABLE listings ADD COLUMN verification_flags %s NULL", longText),
		fmt.Sprintf("ALTER TABLE listings ADD COLUMN verification_model %s NOT NULL DEFAULT ''", shortText),
		fmt.Sprintf("ALTER TABLE listings ADD COLUMN verified_at %s NOT NULL DEFAULT ''", stamp),
		fmt.Sprintf("ALTER TABLE listings ADD COLUMN last_confirmed_at %s NOT NULL DEFAULT ''", stamp),
		fmt.Sprintf("ALTER TABLE listings ADD COLUMN report_count %s NOT NULL DEFAULT 0", integer),
		fmt.Sprintf("ALTER TABLE listings ADD COLUMN view_count %s NOT NULL DEFAULT 0", integer),
	}
	for _, stmt := range alters {
		if _, err := db.Exec(stmt); err != nil {
			if strings.Contains(strings.ToLower(err.Error()), "duplicate column") {
				continue // column already exists — fine
			}
			return fmt.Errorf("apply column migration: %w\n--- statement ---\n%s", err, stmt)
		}
	}
	return nil
}

// splitStatements strips SQL comments and splits a script into individual
// statements. The MySQL driver executes one statement per Exec by default,
// and this works fine for SQLite too.
func splitStatements(script string) []string {
	var b strings.Builder
	for _, line := range strings.Split(script, "\n") {
		if strings.HasPrefix(strings.TrimSpace(line), "--") {
			continue
		}
		b.WriteString(line)
		b.WriteString("\n")
	}
	var out []string
	for _, part := range strings.Split(b.String(), ";") {
		if stmt := strings.TrimSpace(part); stmt != "" {
			out = append(out, stmt)
		}
	}
	return out
}
