package bind

import (
	"encoding/json"
	"fmt"
	"sync"

	_ "golang.org/x/mobile/bind"
	"golang.org/x/net/webdav"
	"io.legado.gowebdav/drive"
	"io.legado.gowebdav/server"
)

// Server is the public struct exposed to Kotlin/Java via Gomobile.
type Server struct {
	mu          sync.Mutex
	cfg         server.Config
	srv         *server.Server
	driveClient drive.DriveClient
	lastError   string
}

// NewServer parses JSON config and creates a Gomobile Server instance.
func NewServer(configJSON string) (*Server, error) {
	var cfg server.Config
	if configJSON != "" {
		if err := json.Unmarshal([]byte(configJSON), &cfg); err != nil {
			return nil, fmt.Errorf("invalid config json: %w", err)
		}
	}

	var fs webdav.FileSystem
	var driveClient drive.DriveClient

	switch {
	case cfg.Mode == "mem" || cfg.Provider == "mock":
		fs = webdav.NewMemFS()

	case cfg.PublicResourceURL != "" || cfg.Mode == "public_link":
		info, err := drive.ParsePublicLink(cfg.PublicResourceURL)
		if err != nil {
			return nil, fmt.Errorf("failed to parse public url: %w", err)
		}
		pubClient := drive.NewPublicClient(*info, cfg.GoogleDriveAPIKey, nil)
		driveClient = pubClient
		fs = drive.NewDriveFileSystem(pubClient)

	default:
		// Authenticated Google Drive (default)
		gClient := drive.NewGoogleDriveClient("", cfg.RootFolderID, nil)
		driveClient = gClient
		fs = drive.NewDriveFileSystem(gClient)
	}

	srv, err := server.NewServer(cfg, fs)
	if err != nil {
		return nil, err
	}

	return &Server{
		cfg:         cfg,
		srv:         srv,
		driveClient: driveClient,
	}, nil
}

// Start launches the WebDAV server with the given access token.
func (s *Server) Start(accessToken string) error {
	s.mu.Lock()
	defer s.mu.Unlock()

	if s.driveClient != nil && accessToken != "" {
		s.driveClient.UpdateToken(accessToken)
	}

	if err := s.srv.Start(accessToken); err != nil {
		s.lastError = err.Error()
		return err
	}
	s.lastError = ""
	return nil
}

// UpdateAccessToken updates the active access token.
func (s *Server) UpdateAccessToken(token string) error {
	s.mu.Lock()
	defer s.mu.Unlock()

	if s.driveClient != nil {
		s.driveClient.UpdateToken(token)
	}
	s.srv.UpdateAccessToken(token)
	return nil
}

// Stop terminates the server listener and active connections.
func (s *Server) Stop() error {
	s.mu.Lock()
	defer s.mu.Unlock()

	if err := s.srv.Stop(); err != nil {
		s.lastError = err.Error()
		return err
	}
	return nil
}

// IsRunning returns whether the server is currently serving requests.
func (s *Server) IsRunning() bool {
	s.mu.Lock()
	defer s.mu.Unlock()
	return s.srv.IsRunning()
}

// Port returns the TCP port bound by the server.
func (s *Server) Port() int {
	s.mu.Lock()
	defer s.mu.Unlock()
	return s.srv.Port()
}

// SessionSecret returns the active session secret for Basic Auth.
func (s *Server) SessionSecret() string {
	s.mu.Lock()
	defer s.mu.Unlock()
	return s.srv.SessionSecret()
}

// LastError returns the most recent error message string.
func (s *Server) LastError() string {
	s.mu.Lock()
	defer s.mu.Unlock()
	return s.lastError
}
