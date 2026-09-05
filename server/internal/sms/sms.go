// Package sms delivers one-time codes for phone verification.
//
// Verification is the app's core promise, and a "verified" account whose phone
// number was never proven undermines it. Sending real messages costs money, so
// this mirrors the rest of the app: a no-cost sender for local development, and
// a refusal to pretend in production.
package sms

import (
	"context"
	"fmt"
	"log"
	"net/http"
	"net/url"
	"strings"
	"time"
)

// Sender delivers a message to a phone number.
type Sender interface {
	Send(ctx context.Context, phone, message string) error
	// Name identifies the sender in logs and /health.
	Name() string
	// Simulated reports whether messages are really delivered. When true the
	// API may echo the code back, which must never happen in production.
	Simulated() bool
}

// LogSender writes the message to the server log instead of sending it. It is
// the zero-setup default, so the flow is testable without an SMS account.
type LogSender struct{}

func (LogSender) Name() string    { return "log" }
func (LogSender) Simulated() bool { return true }

func (LogSender) Send(_ context.Context, phone, message string) error {
	log.Printf("[sms:log] to %s: %s", phone, message)
	return nil
}

// HTTPSender posts the form fields used by Semaphore, the common Philippine SMS
// gateway: apikey, number, message and sendername. Other providers that take a
// simple form POST work by pointing SMS_URL at them.
type HTTPSender struct {
	URL        string
	APIKey     string
	SenderName string
	Client     *http.Client
}

func (s *HTTPSender) Name() string    { return "http" }
func (s *HTTPSender) Simulated() bool { return false }

func (s *HTTPSender) Send(ctx context.Context, phone, message string) error {
	form := url.Values{
		"apikey":  {s.APIKey},
		"number":  {phone},
		"message": {message},
	}
	if s.SenderName != "" {
		form.Set("sendername", s.SenderName)
	}
	req, err := http.NewRequestWithContext(ctx, http.MethodPost, s.URL, strings.NewReader(form.Encode()))
	if err != nil {
		return err
	}
	req.Header.Set("Content-Type", "application/x-www-form-urlencoded")

	client := s.Client
	if client == nil {
		client = &http.Client{Timeout: 15 * time.Second}
	}
	resp, err := client.Do(req)
	if err != nil {
		return fmt.Errorf("sms gateway: %w", err)
	}
	defer resp.Body.Close()
	if resp.StatusCode < 200 || resp.StatusCode > 299 {
		return fmt.Errorf("sms gateway returned %d", resp.StatusCode)
	}
	return nil
}

// New builds a Sender from configuration. An empty gatewayURL yields the log
// sender, which is why local development needs no SMS account.
func New(gatewayURL, apiKey, senderName string) Sender {
	if strings.TrimSpace(gatewayURL) == "" {
		return LogSender{}
	}
	return &HTTPSender{URL: gatewayURL, APIKey: apiKey, SenderName: senderName}
}
