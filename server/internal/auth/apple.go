package auth

import (
	"context"
	"crypto/rsa"
	"crypto/sha256"
	"encoding/base64"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"math/big"
	"net/http"
	"strings"
	"sync"
	"time"

	"github.com/golang-jwt/jwt/v5"
)

// AppleClaims are the fields we consume from a verified Sign in with Apple
// identity token. Apple only sends the user's name once, in the credential on
// the device, so it is not part of the token.
type AppleClaims struct {
	Sub            string // stable Apple user id, scoped to this developer team
	Email          string // real address or a privaterelay.appleid.com alias
	EmailVerified  bool
	IsPrivateEmail bool
}

const appleIssuer = "https://appleid.apple.com"

// appleKeysURL is Apple's JWKS endpoint. A variable so tests can point it at a
// local server.
var appleKeysURL = "https://appleid.apple.com/auth/keys"

var appleHTTPClient = &http.Client{Timeout: 10 * time.Second}

// VerifyAppleIdentityToken checks the token's RS256 signature against Apple's
// published keys, its issuer, expiry and audience, and that its nonce matches
// the raw nonce the app generated for this attempt (which defeats token replay).
func VerifyAppleIdentityToken(ctx context.Context, identityToken, rawNonce string, audiences []string) (*AppleClaims, error) {
	if strings.TrimSpace(identityToken) == "" {
		return nil, errors.New("missing identity token")
	}
	if strings.TrimSpace(rawNonce) == "" {
		return nil, errors.New("missing nonce")
	}

	var claims appleTokenClaims
	parser := jwt.NewParser(
		jwt.WithValidMethods([]string{jwt.SigningMethodRS256.Alg()}),
		jwt.WithIssuer(appleIssuer),
		jwt.WithExpirationRequired(),
	)
	_, err := parser.ParseWithClaims(identityToken, &claims, func(t *jwt.Token) (any, error) {
		kid, _ := t.Header["kid"].(string)
		if kid == "" {
			return nil, errors.New("token has no key id")
		}
		return appleKeys.publicKey(ctx, kid)
	})
	if err != nil {
		return nil, fmt.Errorf("verify apple token: %w", err)
	}

	if !audienceIn(claims.Audience, audiences) {
		return nil, errors.New("identity token was not issued for this app")
	}
	sum := sha256.Sum256([]byte(rawNonce))
	if claims.Nonce != hex.EncodeToString(sum[:]) {
		return nil, errors.New("nonce mismatch")
	}
	if claims.Subject == "" || claims.Email == "" {
		return nil, errors.New("identity token missing subject or email")
	}

	return &AppleClaims{
		Sub:            claims.Subject,
		Email:          strings.ToLower(claims.Email),
		EmailVerified:  bool(claims.EmailVerified),
		IsPrivateEmail: bool(claims.IsPrivateEmail),
	}, nil
}

// appleTokenClaims is the identity token payload. Apple encodes the boolean
// claims as either JSON booleans or the strings "true"/"false", hence flexBool.
type appleTokenClaims struct {
	jwt.RegisteredClaims
	Email          string   `json:"email"`
	EmailVerified  flexBool `json:"email_verified"`
	IsPrivateEmail flexBool `json:"is_private_email"`
	Nonce          string   `json:"nonce"`
}

type flexBool bool

func (b *flexBool) UnmarshalJSON(data []byte) error {
	switch strings.Trim(string(data), `"`) {
	case "true":
		*b = true
	case "false", "", "null":
		*b = false
	default:
		return fmt.Errorf("unexpected boolean %s", data)
	}
	return nil
}

func audienceIn(aud jwt.ClaimStrings, allowed []string) bool {
	for _, a := range aud {
		if audienceAllowed(a, allowed) {
			return true
		}
	}
	return false
}

// appleKeySet caches Apple's signing keys. Apple rotates them rarely, so an
// unknown key id triggers at most one refetch per minute.
type appleKeySet struct {
	mu        sync.Mutex
	keys      map[string]*rsa.PublicKey
	fetchedAt time.Time
}

var appleKeys = &appleKeySet{}

func (k *appleKeySet) publicKey(ctx context.Context, kid string) (*rsa.PublicKey, error) {
	k.mu.Lock()
	defer k.mu.Unlock()
	if key, ok := k.keys[kid]; ok {
		return key, nil
	}
	if time.Since(k.fetchedAt) < time.Minute && k.keys != nil {
		return nil, errors.New("unknown apple signing key")
	}
	if err := k.refresh(ctx); err != nil {
		return nil, err
	}
	if key, ok := k.keys[kid]; ok {
		return key, nil
	}
	return nil, errors.New("unknown apple signing key")
}

func (k *appleKeySet) refresh(ctx context.Context) error {
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, appleKeysURL, nil)
	if err != nil {
		return err
	}
	resp, err := appleHTTPClient.Do(req)
	if err != nil {
		return fmt.Errorf("fetch apple keys: %w", err)
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		return fmt.Errorf("fetch apple keys: status %d", resp.StatusCode)
	}
	var body struct {
		Keys []struct {
			Kid string `json:"kid"`
			Kty string `json:"kty"`
			N   string `json:"n"`
			E   string `json:"e"`
		} `json:"keys"`
	}
	if err := json.NewDecoder(resp.Body).Decode(&body); err != nil {
		return fmt.Errorf("decode apple keys: %w", err)
	}
	keys := make(map[string]*rsa.PublicKey, len(body.Keys))
	for _, jwk := range body.Keys {
		if jwk.Kty != "RSA" {
			continue
		}
		n, err := base64.RawURLEncoding.DecodeString(jwk.N)
		if err != nil {
			continue
		}
		e, err := base64.RawURLEncoding.DecodeString(jwk.E)
		if err != nil {
			continue
		}
		keys[jwk.Kid] = &rsa.PublicKey{N: new(big.Int).SetBytes(n), E: int(new(big.Int).SetBytes(e).Int64())}
	}
	k.keys = keys
	k.fetchedAt = time.Now()
	return nil
}
