package storage

import (
	"fmt"
	"io"
	"os"
	"path/filepath"
)

// LocalStorage saves uploaded files to a directory on disk and exposes them
// under the /uploads/ URL path.
type LocalStorage struct {
	dir string
}

// NewLocal ensures dir exists and returns a LocalStorage rooted at it.
func NewLocal(dir string) (*LocalStorage, error) {
	if err := os.MkdirAll(dir, 0o755); err != nil {
		return nil, fmt.Errorf("create upload dir: %w", err)
	}
	return &LocalStorage{dir: dir}, nil
}

// Save writes the contents of r to a file named filename and returns the
// public URL path (e.g. "/uploads/<filename>").
func (s *LocalStorage) Save(filename string, r io.Reader) (string, error) {
	dst := filepath.Join(s.dir, filepath.Base(filename))
	f, err := os.Create(dst)
	if err != nil {
		return "", fmt.Errorf("create file: %w", err)
	}
	defer f.Close()

	if _, err := io.Copy(f, r); err != nil {
		return "", fmt.Errorf("write file: %w", err)
	}
	return "/uploads/" + filename, nil
}
