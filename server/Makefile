.PHONY: run build tidy test clean seed reseed

# Run the API server (creates cabin.db and seeds demo data on first run)
run:
	go run ./cmd/api

# Build a static binary into ./bin/cabin
build:
	go build -o bin/cabin ./cmd/api

# Resolve and tidy module dependencies
tidy:
	go mod tidy

# Run tests
test:
	go test ./...

# Wipe the local database and reload the mock data set (accounts, listings,
# chats, viewings, reviews, saved searches). Stop the server first.
reseed:
	go run ./cmd/api -reseed

# Remove build artifacts and local runtime data
clean:
	rm -rf bin cabin.db cabin.db-shm cabin.db-wal uploads
