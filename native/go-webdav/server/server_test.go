package server

import (
	"encoding/base64"
	"fmt"
	"io"
	"net/http"
	"strings"
	"testing"
	"time"

	"golang.org/x/net/webdav"
)

func TestServerBasicAuthAndReadOnly(t *testing.T) {
	cfg := Config{
		Port:              0, // ephemeral port
		RequestTimeoutSec: 5,
		ReadOnly:          true,
	}

	memFS := webdav.NewMemFS()
	srv, err := NewServer(cfg, memFS)
	if err != nil {
		t.Fatalf("failed to create server: %v", err)
	}

	if err := srv.Start("test-token"); err != nil {
		t.Fatalf("failed to start server: %v", err)
	}
	defer srv.Stop()

	port := srv.Port()
	if port <= 0 {
		t.Fatalf("expected positive port, got %d", port)
	}

	secret := srv.SessionSecret()
	if len(secret) != 64 { // 32 bytes hex = 64 chars
		t.Fatalf("expected 64-char hex secret, got %s", secret)
	}

	baseURL := fmt.Sprintf("http://127.0.0.1:%d/", port)
	client := &http.Client{Timeout: 3 * time.Second}

	// 1. Request without Auth -> 401
	req1, _ := http.NewRequest("OPTIONS", baseURL, nil)
	resp1, err := client.Do(req1)
	if err != nil {
		t.Fatalf("request failed: %v", err)
	}
	defer resp1.Body.Close()
	if resp1.StatusCode != http.StatusUnauthorized {
		t.Errorf("expected 401 Unauthorized, got %d", resp1.StatusCode)
	}
	if !strings.Contains(resp1.Header.Get("WWW-Authenticate"), "Basic") {
		t.Errorf("expected WWW-Authenticate Basic header, got %s", resp1.Header.Get("WWW-Authenticate"))
	}

	// 2. Request with Wrong Secret -> 401
	req2, _ := http.NewRequest("OPTIONS", baseURL, nil)
	req2.Header.Set("Authorization", "Basic "+base64.StdEncoding.EncodeToString([]byte("legado:wrong_secret")))
	resp2, err := client.Do(req2)
	if err != nil {
		t.Fatalf("request failed: %v", err)
	}
	defer resp2.Body.Close()
	if resp2.StatusCode != http.StatusUnauthorized {
		t.Errorf("expected 401 Unauthorized for wrong secret, got %d", resp2.StatusCode)
	}

	// 3. Request with Valid Secret (OPTIONS) -> 200
	req3, _ := http.NewRequest("OPTIONS", baseURL, nil)
	validAuth := "Basic " + base64.StdEncoding.EncodeToString([]byte("legado:"+secret))
	req3.Header.Set("Authorization", validAuth)
	resp3, err := client.Do(req3)
	if err != nil {
		t.Fatalf("request failed: %v", err)
	}
	defer resp3.Body.Close()
	if resp3.StatusCode != http.StatusOK {
		t.Errorf("expected 200 OK for valid auth, got %d", resp3.StatusCode)
	}
	if resp3.Header.Get("DAV") != "1" {
		t.Errorf("expected DAV: 1, got %s", resp3.Header.Get("DAV"))
	}

	// 4. Request with Valid Secret (PROPFIND on root) -> 207 Multi-Status
	req4, _ := http.NewRequest("PROPFIND", baseURL, nil)
	req4.Header.Set("Authorization", validAuth)
	req4.Header.Set("Depth", "0")
	resp4, err := client.Do(req4)
	if err != nil {
		t.Fatalf("request failed: %v", err)
	}
	defer resp4.Body.Close()
	if resp4.StatusCode != http.StatusMultiStatus {
		t.Errorf("expected 207 Multi-Status for PROPFIND, got %d", resp4.StatusCode)
	}
	bodyBytes, _ := io.ReadAll(resp4.Body)
	bodyStr := string(bodyBytes)
	if !strings.Contains(bodyStr, "multistatus") {
		t.Errorf("expected multistatus in body, got %s", bodyStr)
	}

	// 5. Forbidden write operation (PUT) -> 405 Method Not Allowed
	req5, _ := http.NewRequest("PUT", baseURL+"test.txt", strings.NewReader("hello"))
	req5.Header.Set("Authorization", validAuth)
	resp5, err := client.Do(req5)
	if err != nil {
		t.Fatalf("request failed: %v", err)
	}
	defer resp5.Body.Close()
	if resp5.StatusCode != http.StatusMethodNotAllowed {
		t.Errorf("expected 405 Method Not Allowed for PUT, got %d", resp5.StatusCode)
	}
}
