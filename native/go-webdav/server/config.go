package server

import (
	"crypto/rand"
	"encoding/hex"
)

// Config represents runtime configuration for the local WebDAV proxy.
type Config struct {
	Port              int    `json:"port"`
	RootFolderID      string `json:"root_folder_id"`
	RequestTimeoutSec int    `json:"request_timeout_sec"`
	ReadOnly          bool   `json:"read_only"`
	SessionSecret     string `json:"session_secret"`
	Mode              string `json:"mode"` // "authenticated" | "public_link"
	PublicResourceURL string `json:"public_resource_url"`
	Provider          string `json:"provider"` // "google_drive" | "onedrive" | "dropbox" | "http"
	GoogleDriveAPIKey string `json:"google_drive_api_key"`
}

// GenerateSessionSecret generates a cryptographically secure 32-byte hex string.
func GenerateSessionSecret() (string, error) {
	bytes := make([]byte, 32)
	if _, err := rand.Read(bytes); err != nil {
		return "", err
	}
	return hex.EncodeToString(bytes), nil
}
