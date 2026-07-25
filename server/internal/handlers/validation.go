package handlers

import (
	"regexp"
	"strconv"
	"strings"
)

// phoneSeparators are stripped before validating a mobile number, so users can
// type "+1 (415) 555-1234" and have it normalized to "+14155551234".
var phoneSeparators = strings.NewReplacer(" ", "", "-", "", "(", "", ")", "", ".", "")

// e164Pattern matches a valid E.164 number: a leading "+", a non-zero country
// code digit, then 8–15 digits total.
var e164Pattern = regexp.MustCompile(`^\+[1-9]\d{7,14}$`)

// normalizePhone trims separators, leaving a leading "+" and digits.
func normalizePhone(raw string) string {
	return phoneSeparators.Replace(strings.TrimSpace(raw))
}

// validE164 reports whether s is a syntactically valid E.164 mobile number.
func validE164(s string) bool {
	return e164Pattern.MatchString(s)
}

func contains(list []string, v string) bool {
	for _, x := range list {
		if x == v {
			return true
		}
	}
	return false
}

func atoiDefault(s string, def int) int {
	if s == "" {
		return def
	}
	if n, err := strconv.Atoi(s); err == nil {
		return n
	}
	return def
}

func allowedImageExt(ext string) bool {
	switch ext {
	case ".jpg", ".jpeg", ".png", ".webp", ".gif":
		return true
	default:
		return false
	}
}
