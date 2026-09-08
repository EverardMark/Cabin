package auth

import (
	"context"
	"crypto/rand"
	"crypto/rsa"
	"crypto/sha256"
	"encoding/base64"
	"encoding/hex"
	"encoding/json"
	"math/big"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"

	"github.com/golang-jwt/jwt/v5"
)

// fakeApple stands in for appleid.apple.com: it publishes a JWKS for a
// throwaway RSA key and can mint identity tokens signed with it.
type fakeApple struct {
	key    *rsa.PrivateKey
	server *httptest.Server
}

func newFakeApple(t *testing.T) *fakeApple {
	t.Helper()
	key, err := rsa.GenerateKey(rand.Reader, 2048)
	if err != nil {
		t.Fatal(err)
	}
	f := &fakeApple{key: key}
	f.server = httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		_ = json.NewEncoder(w).Encode(map[string]any{
			"keys": []map[string]string{{
				"kty": "RSA", "kid": "test-kid", "use": "sig", "alg": "RS256",
				"n": base64.RawURLEncoding.EncodeToString(key.N.Bytes()),
				"e": base64.RawURLEncoding.EncodeToString(big.NewInt(int64(key.E)).Bytes()),
			}},
		})
	}))
	t.Cleanup(f.server.Close)

	appleKeysURL = f.server.URL
	appleKeys = &appleKeySet{}
	return f
}

func (f *fakeApple) token(t *testing.T, mutate func(jwt.MapClaims)) string {
	t.Helper()
	sum := sha256.Sum256([]byte("raw-nonce"))
	claims := jwt.MapClaims{
		"iss":              appleIssuer,
		"aud":              "com.example.cabin",
		"sub":              "001234.abcdef",
		"email":            "Someone@PrivateRelay.AppleID.com",
		"email_verified":   "true", // Apple sends strings here
		"is_private_email": true,   // ...and sometimes booleans
		"nonce":            hex.EncodeToString(sum[:]),
		"exp":              time.Now().Add(5 * time.Minute).Unix(),
		"iat":              time.Now().Unix(),
	}
	if mutate != nil {
		mutate(claims)
	}
	tok := jwt.NewWithClaims(jwt.SigningMethodRS256, claims)
	tok.Header["kid"] = "test-kid"
	signed, err := tok.SignedString(f.key)
	if err != nil {
		t.Fatal(err)
	}
	return signed
}

func TestVerifyAppleIdentityToken(t *testing.T) {
	apple := newFakeApple(t)
	ctx := context.Background()
	aud := []string{"com.example.cabin"}

	claims, err := VerifyAppleIdentityToken(ctx, apple.token(t, nil), "raw-nonce", aud)
	if err != nil {
		t.Fatalf("valid token rejected: %v", err)
	}
	if claims.Sub != "001234.abcdef" || claims.Email != "someone@privaterelay.appleid.com" {
		t.Errorf("unexpected claims %+v", claims)
	}
	if !claims.EmailVerified || !claims.IsPrivateEmail {
		t.Errorf("boolean claims not parsed: %+v", claims)
	}

	bad := map[string]struct {
		token string
		nonce string
		aud   []string
	}{
		"wrong nonce":    {apple.token(t, nil), "other-nonce", aud},
		"wrong audience": {apple.token(t, nil), "raw-nonce", []string{"com.other.app"}},
		"wrong issuer":   {apple.token(t, func(c jwt.MapClaims) { c["iss"] = "https://evil.example" }), "raw-nonce", aud},
		"expired":        {apple.token(t, func(c jwt.MapClaims) { c["exp"] = time.Now().Add(-time.Minute).Unix() }), "raw-nonce", aud},
		"missing email":  {apple.token(t, func(c jwt.MapClaims) { delete(c, "email") }), "raw-nonce", aud},
		"tampered":       {apple.token(t, nil) + "x", "raw-nonce", aud},
		"empty token":    {"", "raw-nonce", aud},
		"empty nonce":    {apple.token(t, nil), "", aud},
	}
	for name, tc := range bad {
		if _, err := VerifyAppleIdentityToken(ctx, tc.token, tc.nonce, tc.aud); err == nil {
			t.Errorf("%s: expected rejection", name)
		}
	}
}

func TestVerifyAppleIdentityTokenRejectsUnknownKey(t *testing.T) {
	apple := newFakeApple(t)
	other, _ := rsa.GenerateKey(rand.Reader, 2048)
	sum := sha256.Sum256([]byte("raw-nonce"))
	tok := jwt.NewWithClaims(jwt.SigningMethodRS256, jwt.MapClaims{
		"iss": appleIssuer, "aud": "com.example.cabin", "sub": "x", "email": "x@y.z",
		"nonce": hex.EncodeToString(sum[:]), "exp": time.Now().Add(time.Minute).Unix(),
	})
	tok.Header["kid"] = "test-kid"
	signed, _ := tok.SignedString(other) // right kid, wrong key
	if _, err := VerifyAppleIdentityToken(context.Background(), signed, "raw-nonce", []string{"com.example.cabin"}); err == nil {
		t.Fatal("token signed by a foreign key was accepted")
	}
	_ = apple
}
