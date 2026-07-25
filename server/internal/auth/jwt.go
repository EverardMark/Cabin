package auth

import (
	"errors"
	"time"

	"github.com/golang-jwt/jwt/v5"
)

// TokenService issues and validates HS256 JWTs.
type TokenService struct {
	secret []byte
	ttl    time.Duration
}

// NewTokenService creates a token service with a 7-day token lifetime.
func NewTokenService(secret string) *TokenService {
	return &TokenService{secret: []byte(secret), ttl: 7 * 24 * time.Hour}
}

// Generate returns a signed token whose subject is the user ID.
func (s *TokenService) Generate(userID string) (string, error) {
	now := time.Now()
	claims := jwt.RegisteredClaims{
		Subject:   userID,
		IssuedAt:  jwt.NewNumericDate(now),
		ExpiresAt: jwt.NewNumericDate(now.Add(s.ttl)),
	}
	token := jwt.NewWithClaims(jwt.SigningMethodHS256, claims)
	return token.SignedString(s.secret)
}

// Verify validates the token and returns the user ID (subject).
func (s *TokenService) Verify(tokenStr string) (string, error) {
	claims := &jwt.RegisteredClaims{}
	token, err := jwt.ParseWithClaims(tokenStr, claims, func(t *jwt.Token) (any, error) {
		if _, ok := t.Method.(*jwt.SigningMethodHMAC); !ok {
			return nil, errors.New("unexpected signing method")
		}
		return s.secret, nil
	})
	if err != nil || !token.Valid {
		return "", errors.New("invalid token")
	}
	if claims.Subject == "" {
		return "", errors.New("token missing subject")
	}
	return claims.Subject, nil
}
