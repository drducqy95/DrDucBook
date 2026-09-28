package server

import "errors"

var (
	ErrUnauthorized    = errors.New("AUTH_REQUIRED: authorization missing or invalid")
	ErrTokenExpired    = errors.New("AUTH_EXPIRED: google drive access token has expired")
	ErrNotFound        = errors.New("NOT_FOUND: requested file or folder does not exist")
	ErrRateLimited     = errors.New("RATE_LIMITED: upstream api rate limit exceeded")
	ErrMethodNotAllowed = errors.New("METHOD_NOT_ALLOWED: write operations are forbidden on read-only server")
	ErrServerInternal  = errors.New("SERVER_ERROR: internal proxy error")
)
