package sms

import (
	"context"
	"net/http"
	"net/http/httptest"
	"net/url"
	"testing"
)

// The gateway sender is verified against a stub, not against a live provider —
// so this proves the request shape, not that any particular gateway accepts it.
func TestHTTPSenderPostsGatewayForm(t *testing.T) {
	var got struct {
		method, contentType string
		form                url.Values
	}
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		_ = r.ParseForm()
		got.method = r.Method
		got.contentType = r.Header.Get("Content-Type")
		got.form = r.PostForm
		w.WriteHeader(http.StatusOK)
	}))
	defer srv.Close()

	s := &HTTPSender{URL: srv.URL, APIKey: "test-key", SenderName: "Cabin"}
	if err := s.Send(context.Background(), "+63 917 555 0134", "123456 is your code"); err != nil {
		t.Fatalf("Send: %v", err)
	}

	if got.method != http.MethodPost {
		t.Errorf("method = %s, want POST", got.method)
	}
	if got.contentType != "application/x-www-form-urlencoded" {
		t.Errorf("content-type = %q", got.contentType)
	}
	for field, want := range map[string]string{
		"apikey":     "test-key",
		"number":     "+63 917 555 0134",
		"message":    "123456 is your code",
		"sendername": "Cabin",
	} {
		if v := got.form.Get(field); v != want {
			t.Errorf("form[%s] = %q, want %q", field, v, want)
		}
	}
	if s.Simulated() {
		t.Error("a real gateway must not report itself as simulated")
	}
}

func TestHTTPSenderReportsGatewayFailure(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		w.WriteHeader(http.StatusUnauthorized)
	}))
	defer srv.Close()

	s := &HTTPSender{URL: srv.URL, APIKey: "bad"}
	if err := s.Send(context.Background(), "+63 917 555 0134", "hi"); err == nil {
		t.Error("a 401 from the gateway must surface as an error, not a silent success")
	}
}

// No gateway configured must yield the simulated sender, so local development
// works with zero setup — and so the API knows not to echo codes in production.
func TestNewFallsBackToLogSender(t *testing.T) {
	s := New("", "", "")
	if !s.Simulated() {
		t.Error("no gateway URL should give a simulated sender")
	}
	if s.Name() != "log" {
		t.Errorf("Name() = %q, want log", s.Name())
	}
	if got := New("https://example.test/send", "k", "Cabin"); got.Simulated() {
		t.Error("a configured gateway must not be simulated")
	}
}
