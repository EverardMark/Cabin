package storage

import (
	"fmt"
	"io"
	"os"
	"path/filepath"
	"strings"
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
	base := filepath.Base(filename)
	dst := filepath.Join(s.dir, base)
	f, err := os.Create(dst)
	if err != nil {
		return "", fmt.Errorf("create file: %w", err)
	}
	defer f.Close()

	if _, err := io.Copy(f, r); err != nil {
		// Don't leave a half-written file behind for a failed upload.
		_ = os.Remove(dst)
		return "", fmt.Errorf("write file: %w", err)
	}
	return "/uploads/" + base, nil
}

// Path resolves a stored file name to an on-disk path. It rejects any name
// containing a separator so a request cannot escape the upload directory.
func (s *LocalStorage) Path(name string) (string, error) {
	if name == "" || strings.ContainsAny(name, `/\`) || strings.Contains(name, "..") {
		return "", fmt.Errorf("invalid file name %q", name)
	}
	path := filepath.Join(s.dir, name)
	info, err := os.Stat(path)
	if err != nil || info.IsDir() {
		return "", fmt.Errorf("not found")
	}
	return path, nil
}
