package config

import (
	"fmt"
	"log"
	"os"
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

func getEnv(key, fallback string) string {
	if v, ok := os.LookupEnv(key); ok && v != "" {
		return v
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
