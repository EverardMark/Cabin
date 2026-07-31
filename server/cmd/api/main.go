// Command api is the Cabin REST API server entrypoint. It loads configuration,
// opens and migrates the database, wires the stores/handlers together, optionally
// seeds demo data, and serves HTTP with graceful shutdown.
package main

import (
	"context"
	"errors"
	"log"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"cabin/internal/auth"
	"cabin/internal/config"
	"cabin/internal/database"
	"cabin/internal/handlers"
	"cabin/internal/seed"
	"cabin/internal/storage"
	"cabin/internal/store"
)

func main() {
	cfg := config.Load()

	// For sqlite the DSN is a file path; for mysql it's a full DSN string.
	dsn := cfg.DBPath
	if cfg.DBDriver == "mysql" {
		dsn = cfg.MySQLDSN
	}

	db, err := database.Open(cfg.DBDriver, dsn)
	if err != nil {
		log.Fatalf("open database: %v", err)
	}
	defer db.Close()

	if err := database.Migrate(db, cfg.DBDriver); err != nil {
		log.Fatalf("migrate database: %v", err)
	}

	users := store.NewUserStore(db)
	listings := store.NewListingStore(db)
	tokens := auth.NewTokenService(cfg.JWTSecret)

	uploads, err := storage.NewLocal(cfg.UploadDir)
	if err != nil {
		log.Fatalf("init upload storage: %v", err)
	}

	if cfg.Seed {
		if err := seed.Run(users, listings); err != nil {
			log.Fatalf("seed data: %v", err)
		}
	}

	srv := handlers.NewServer(cfg, users, listings, tokens, uploads)
	httpServer := &http.Server{
		Addr:              ":" + cfg.Port,
		Handler:           srv.Handler(),
		ReadHeaderTimeout: 10 * time.Second, // bound slow-header (Slowloris) reads
	}

	// Serve in a goroutine so main can block on either a fatal listen error or
	// an OS shutdown signal.
	serverErr := make(chan error, 1)
	go func() {
		log.Printf("cabin-api listening on http://localhost:%s (env=%s, db=%s)", cfg.Port, cfg.Env, cfg.DBDriver)
		if err := httpServer.ListenAndServe(); err != nil && !errors.Is(err, http.ErrServerClosed) {
			serverErr <- err
		}
	}()

	stop := make(chan os.Signal, 1)
	signal.Notify(stop, os.Interrupt, syscall.SIGTERM)

	select {
	case err := <-serverErr:
		log.Fatalf("server error: %v", err)
	case sig := <-stop:
		log.Printf("received %s, shutting down...", sig)
	}

	// Give in-flight requests up to 10s to finish before forcing the connection closed.
	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()
	if err := httpServer.Shutdown(ctx); err != nil {
		log.Printf("graceful shutdown failed, forcing close: %v", err)
		_ = httpServer.Close()
	}
	log.Println("shutdown complete")
}
