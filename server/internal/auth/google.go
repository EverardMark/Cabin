package auth

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"net/http"
	"net/url"
	"strconv"
	"strings"
	"time"
)

// GoogleClaims are the fields we consume from a verified Google ID token.
type GoogleClaims struct {
	Sub           string // stable Google account id
	Email         string
	EmailVerified bool
	Name          string
}

// googleTokenInfoURL validates an ID token and returns its claims; Google
// performs the signature/expiry check on its side. This is simple and correct
// for an MVP. A high-traffic deployment should instead verify the JWT locally
// against Google's JWKS (e.g. google.golang.org/api/idtoken) to avoid a network
// round-trip per sign-in.
const googleTokenInfoURL = "https://oauth2.googleapis.com/tokeninfo"

var googleHTTPClient = &http.Client{Timeout: 10 * time.Second}

// VerifyGoogleIDToken validates idToken with Google and confirms its audience
// ("aud") is one of allowedClientIDs, returning the identity claims on success.
func VerifyGoogleIDToken(ctx context.Context, idToken string, allowedClientIDs []string) (*GoogleClaims, error) {
	if strings.TrimSpace(idToken) == "" {
		return nil, errors.New("missing id token")
	}
	endpoint := googleTokenInfoURL + "?" + url.Values{"id_token": {idToken}}.Encode()
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, endpoint, nil)
	if err != nil {
		return nil, err
	}
	resp, err := googleHTTPClient.Do(req)
	if err != nil {
		return nil, fmt.Errorf("contact google: %w", err)
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		return nil, errors.New("google rejected the id token")
	}

	// tokeninfo returns all values as JSON strings.
	var body struct {
		Aud           string `json:"aud"`
		Sub           string `json:"sub"`
		Email         string `json:"email"`
		EmailVerified string `json:"email_verified"`
		Name          string `json:"name"`
		Exp           string `json:"exp"`
		Iss           string `json:"iss"`
	}
	if err := json.NewDecoder(resp.Body).Decode(&body); err != nil {
		return nil, fmt.Errorf("decode google response: %w", err)
	}

	if !audienceAllowed(body.Aud, allowedClientIDs) {
		return nil, errors.New("id token was not issued for this app")
	}
	// iss is "accounts.google.com" or "https://accounts.google.com".
	if strings.TrimPrefix(body.Iss, "https://") != "accounts.google.com" {
		return nil, errors.New("unexpected token issuer")
	}
	if exp, _ := strconv.ParseInt(body.Exp, 10, 64); exp <= time.Now().Unix() {
		return nil, errors.New("id token has expired")
	}
	if body.Sub == "" || body.Email == "" {
		return nil, errors.New("id token missing subject or email")
	}

	return &GoogleClaims{
		Sub:           body.Sub,
		Email:         strings.ToLower(body.Email),
		EmailVerified: body.EmailVerified == "true",
		Name:          body.Name,
	}, nil
}

func audienceAllowed(aud string, allowed []string) bool {
	for _, id := range allowed {
		if aud == id {
			return true
		}
	}
	return false
}
