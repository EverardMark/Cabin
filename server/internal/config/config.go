package config

import (
	"fmt"
	"log"
	"os"
	"strconv"
	"strings"
)

// Config holds all runtime configuration, loaded from environment variables.
type Config struct {
	Port      string
	JWTSecret string
	UploadDir string
	Env       string
	Seed      bool

	// Database
	DBDriver string // "sqlite" (default) or "mysql"
	DBPath   string // SQLite file path (when DBDriver == "sqlite")
	MySQLDSN string // MySQL DSN (when DBDriver == "mysql")

	// GoogleClientIDs are the accepted OAuth client IDs (a token's "aud"):
	// typically the Web, iOS, and Android client IDs, since each platform's
	// SDK issues tokens for a different audience. Empty disables Google sign-in.
	GoogleClientIDs []string

	// AnthropicAPIKey enables Claude-backed listing verification. When empty the
	// verifier falls back to a deterministic rule-based check, so the app still
	// runs with zero setup.
	AnthropicAPIKey string
	// VerifyModel is the Claude model used to review listings.
	VerifyModel string
	// MaxUploadBytes caps a single image upload.
	MaxUploadBytes int64
}

// Load reads configuration from the environment, applying development-friendly
// defaults. It fails fast if required production settings are missing.
func Load() *Config {
	cfg := &Config{
		Port:      getEnv("PORT", "8080"),
		JWTSecret: getEnv("JWT_SECRET", ""),
		UploadDir: getEnv("UPLOAD_DIR", "uploads"),
		Env:       getEnv("ENV", "development"),
		DBDriver:  getEnv("DB_DRIVER", "sqlite"),
		DBPath:    getEnv("DB_PATH", "cabin.db"),

		// Comma-separated list of accepted Google client IDs (Web, iOS, Android).
		GoogleClientIDs: parseCSV(getEnv("GOOGLE_CLIENT_ID", "")),

		AnthropicAPIKey: getEnv("ANTHROPIC_API_KEY", ""),
		VerifyModel:     getEnv("VERIFY_MODEL", "claude-opus-5"),
		MaxUploadBytes:  int64(getEnvInt("MAX_UPLOAD_MB", 10)) << 20,
	}

	isProd := cfg.Env == "production"

	if cfg.JWTSecret == "" {
		if isProd {
			log.Fatal("JWT_SECRET must be set in production")
		}
		cfg.JWTSecret = "dev-insecure-secret-change-me"
		log.Println("WARNING: JWT_SECRET not set; using an insecure development secret")
	}

	if cfg.DBDriver == "mysql" {
		cfg.MySQLDSN = getEnv("MYSQL_DSN", "")
		if cfg.MySQLDSN == "" {
			cfg.MySQLDSN = fmt.Sprintf(
				"%s:%s@tcp(%s:%s)/%s?parseTime=true&charset=utf8mb4&collation=utf8mb4_unicode_ci&loc=UTC",
				getEnv("MYSQL_USER", "cabin"),
				getEnv("MYSQL_PASSWORD", ""),
				getEnv("MYSQL_HOST", "127.0.0.1"),
				getEnv("MYSQL_PORT", "3306"),
				getEnv("MYSQL_DATABASE", "cabin"),
			)
		}
	}

	// Seed defaults to true in development, false in production.
	cfg.Seed = getEnvBool("SEED", !isProd)

	return cfg
}

// parseCSV splits a comma-separated value into trimmed, non-empty items.
func parseCSV(s string) []string {
	var out []string
	for _, part := range strings.Split(s, ",") {
		if p := strings.TrimSpace(part); p != "" {
			out = append(out, p)
		}
	}
	return out
}

func getEnv(key, fallback string) string {
	if v, ok := os.LookupEnv(key); ok && v != "" {
		return v
	}
	return fallback
}

func getEnvInt(key string, fallback int) int {
	if v, ok := os.LookupEnv(key); ok && v != "" {
		if n, err := strconv.Atoi(v); err == nil && n > 0 {
			return n
		}
	}
	return fallback
}

func getEnvBool(key string, fallback bool) bool {
	if v, ok := os.LookupEnv(key); ok {
		switch v {
		case "1", "true", "TRUE", "True", "yes":
			return true
		case "0", "false", "FALSE", "False", "no":
			return false
		}
	}
	return fallback
}
