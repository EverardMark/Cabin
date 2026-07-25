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
	verifiedType := "INTEGER"
	if driver == "mysql" {
		verifiedType = "TINYINT"
	}
	alters := []string{
		fmt.Sprintf("ALTER TABLE users ADD COLUMN verified %s NOT NULL DEFAULT 0", verifiedType),
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
