package server

import (
	"context"
	"crypto/subtle"
	"encoding/base64"
	"fmt"
	"net"
	"net/http"
	"strings"
	"sync"
	"time"

	"golang.org/x/net/webdav"
)

// Server encapsulates the read-only WebDAV HTTP server bound to loopback.
type Server struct {
	cfg           Config
	listener      net.Listener
	httpServer    *http.Server
	fs            webdav.FileSystem
	port          int
	sessionSecret string
	mu            sync.RWMutex
	running       bool
	accessToken   string
}

// NewServer initializes a new WebDAV proxy server with the provided configuration.
func NewServer(cfg Config, fs webdav.FileSystem) (*Server, error) {
	secret := cfg.SessionSecret
	if secret == "" {
		generated, err := GenerateSessionSecret()
		if err != nil {
			return nil, fmt.Errorf("failed to generate session secret: %w", err)
		}
		secret = generated
	}

	return &Server{
		cfg:           cfg,
		fs:            fs,
		sessionSecret: secret,
	}, nil
}

// Start binds to 127.0.0.1 and starts serving WebDAV requests in a background goroutine.
func (s *Server) Start(accessToken string) error {
	s.mu.Lock()
	defer s.mu.Unlock()

	if s.running {
		return nil
	}

	s.accessToken = accessToken

	bindAddr := fmt.Sprintf("127.0.0.1:%d", s.cfg.Port)
	listener, err := net.Listen("tcp", bindAddr)
	if err != nil {
		return fmt.Errorf("failed to bind loopback listener at %s: %w", bindAddr, err)
	}

	s.listener = listener
	s.port = listener.Addr().(*net.TCPAddr).Port

	davHandler := &webdav.Handler{
		FileSystem: s.fs,
		LockSystem: webdav.NewMemLS(),
	}

	mux := http.NewServeMux()
	mux.HandleFunc("/", func(w http.ResponseWriter, r *http.Request) {
		s.handleRequest(davHandler, w, r)
	})

	timeout := time.Duration(s.cfg.RequestTimeoutSec) * time.Second
	if timeout <= 0 {
		timeout = 30 * time.Second
	}

	s.httpServer = &http.Server{
		Handler:      mux,
		ReadTimeout:  timeout,
		WriteTimeout: timeout,
	}

	s.running = true

	go func() {
		if err := s.httpServer.Serve(listener); err != nil && err != http.ErrServerClosed {
			s.mu.Lock()
			s.running = false
			s.mu.Unlock()
		}
	}()

	return nil
}

// handleRequest enforces Basic Auth with session secret and read-only method filtering.
func (s *Server) handleRequest(davHandler http.Handler, w http.ResponseWriter, r *http.Request) {
	// 1. Enforce Basic Auth
	authHeader := r.Header.Get("Authorization")
	if !s.validateBasicAuth(authHeader) {
		w.Header().Set("WWW-Authenticate", `Basic realm="DrDucBookWebDAV"`)
		http.Error(w, ErrUnauthorized.Error(), http.StatusUnauthorized)
		return
	}

	// 2. Enforce Read-Only methods
	switch r.Method {
	case "OPTIONS":
		w.Header().Set("DAV", "1")
		w.Header().Set("Allow", "OPTIONS, PROPFIND, GET, HEAD")
		w.Header().Set("MS-Author-Via", "DAV")
		w.WriteHeader(http.StatusOK)
		return

	case "PROPFIND", "GET", "HEAD":
		// Allowed read-only WebDAV methods
		davHandler.ServeHTTP(w, r)
		return

	case "PUT", "DELETE", "MKCOL", "MOVE", "COPY", "PROPPATCH", "LOCK", "UNLOCK":
		w.Header().Set("Allow", "OPTIONS, PROPFIND, GET, HEAD")
		http.Error(w, ErrMethodNotAllowed.Error(), http.StatusMethodNotAllowed)
		return

	default:
		w.Header().Set("Allow", "OPTIONS, PROPFIND, GET, HEAD")
		http.Error(w, "Method Not Allowed", http.StatusMethodNotAllowed)
		return
	}
}

// validateBasicAuth checks if the request header contains valid Basic Auth credentials.
// Expected username: "legado", password: s.sessionSecret.
func (s *Server) validateBasicAuth(authHeader string) bool {
	if authHeader == "" || !strings.HasPrefix(authHeader, "Basic ") {
		return false
	}

	payload, err := base64.StdEncoding.DecodeString(strings.TrimPrefix(authHeader, "Basic "))
	if err != nil {
		return false
	}

	parts := strings.SplitN(string(payload), ":", 2)
	if len(parts) != 2 {
		return false
	}

	userMatch := subtle.ConstantTimeCompare([]byte(parts[0]), []byte("legado")) == 1
	passMatch := subtle.ConstantTimeCompare([]byte(parts[1]), []byte(s.sessionSecret)) == 1

	return userMatch && passMatch
}

// UpdateAccessToken updates the active Google Drive access token thread-safely.
func (s *Server) UpdateAccessToken(token string) {
	s.mu.Lock()
	defer s.mu.Unlock()
	s.accessToken = token
}

// AccessToken returns the current access token.
func (s *Server) AccessToken() string {
	s.mu.RLock()
	defer s.mu.RUnlock()
	return s.accessToken
}

// Port returns the assigned TCP port.
func (s *Server) Port() int {
	s.mu.RLock()
	defer s.mu.RUnlock()
	return s.port
}

// SessionSecret returns the active session secret.
func (s *Server) SessionSecret() string {
	s.mu.RLock()
	defer s.mu.RUnlock()
	return s.sessionSecret
}

// IsRunning returns true if the server is active.
func (s *Server) IsRunning() bool {
	s.mu.RLock()
	defer s.mu.RUnlock()
	return s.running
}

// Stop shuts down the server gracefully.
func (s *Server) Stop() error {
	s.mu.Lock()
	defer s.mu.Unlock()

	if !s.running {
		return nil
	}

	s.running = false
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	if s.httpServer != nil {
		_ = s.httpServer.Shutdown(ctx)
	}
	if s.listener != nil {
		_ = s.listener.Close()
	}

	return nil
}
