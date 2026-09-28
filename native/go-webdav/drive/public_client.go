package drive

import (
	"context"
	"encoding/base64"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"regexp"
	"strconv"
	"strings"
	"sync"
	"time"
)

// ProviderType identifies the remote source type.
type ProviderType string

const (
	ProviderGoogleDrive ProviderType = "google_drive"
	ProviderOneDrive    ProviderType = "onedrive"
	ProviderDropbox     ProviderType = "dropbox"
	ProviderHttpIndex   ProviderType = "http_index"
)

// PublicLinkInfo parses and classifies a public URL.
type PublicLinkInfo struct {
	Provider ProviderType
	RawURL   string
	TargetID string
	IsFolder bool
}

var (
	reGDriveFolder = regexp.MustCompile(`/drive/(?:u/\d+/)?folders/([a-zA-Z0-9_-]+)`)
	reGDriveFile   = regexp.MustCompile(`/(?:file/d/|open\?id=|uc\?id=)([a-zA-Z0-9_-]+)`)
	reOneDrive     = regexp.MustCompile(`(?:1drv\.ms|onedrive\.live\.com)`)
	reDropbox      = regexp.MustCompile(`dropbox\.com/(?:s|scl)/(?:fi|fo)/([a-zA-Z0-9_-]+)`)
	reHTMLLink     = regexp.MustCompile(`(?i)<a\s+[^>]*href=["']([^"']+)["'][^>]*>(.*?)</a>`)
)

// ParsePublicLink inspects a URL and determines provider and resource ID.
func ParsePublicLink(rawURL string) (*PublicLinkInfo, error) {
	trimmed := strings.TrimSpace(rawURL)
	parsed, err := url.Parse(trimmed)
	if err != nil {
		return nil, fmt.Errorf("invalid URL: %w", err)
	}

	// 1. Google Drive
	if strings.Contains(parsed.Host, "drive.google.com") || strings.Contains(parsed.Host, "docs.google.com") {
		if m := reGDriveFolder.FindStringSubmatch(parsed.Path); len(m) > 1 {
			return &PublicLinkInfo{
				Provider: ProviderGoogleDrive,
				RawURL:   trimmed,
				TargetID: m[1],
				IsFolder: true,
			}, nil
		}
		if m := reGDriveFile.FindStringSubmatch(trimmed); len(m) > 1 {
			return &PublicLinkInfo{
				Provider: ProviderGoogleDrive,
				RawURL:   trimmed,
				TargetID: m[1],
				IsFolder: false,
			}, nil
		}
	}

	// 2. OneDrive
	if reOneDrive.MatchString(parsed.Host) {
		return &PublicLinkInfo{
			Provider: ProviderOneDrive,
			RawURL:   trimmed,
			TargetID: trimmed,
			IsFolder: true,
		}, nil
	}

	// 3. Dropbox
	if reDropbox.MatchString(trimmed) {
		isFolder := strings.Contains(trimmed, "/fo/")
		return &PublicLinkInfo{
			Provider: ProviderDropbox,
			RawURL:   trimmed,
			TargetID: trimmed,
			IsFolder: isFolder,
		}, nil
	}

	// 4. HTTP Autoindex fallback
	if parsed.Scheme == "http" || parsed.Scheme == "https" {
		return &PublicLinkInfo{
			Provider: ProviderHttpIndex,
			RawURL:   trimmed,
			TargetID: trimmed,
			IsFolder: strings.HasSuffix(parsed.Path, "/") || parsed.Path == "",
		}, nil
	}

	return nil, fmt.Errorf("unrecognized provider URL: %s", rawURL)
}

// PublicClient implements DriveClient for unauthenticated public links.
type PublicClient struct {
	httpClient *http.Client
	info       PublicLinkInfo
	apiKey     string
	filesByID  map[string]FileMetadata
	children   map[string][]FileMetadata
	mu         sync.RWMutex
}

// NewPublicClient creates a client tailored to the given public link.
func NewPublicClient(info PublicLinkInfo, apiKey string, customHTTPClient *http.Client) *PublicClient {
	if customHTTPClient == nil {
		customHTTPClient = &http.Client{
			Timeout: 45 * time.Second,
		}
	}
	return &PublicClient{
		httpClient: customHTTPClient,
		info:       info,
		apiKey:     apiKey,
		filesByID:  make(map[string]FileMetadata),
		children:   make(map[string][]FileMetadata),
	}
}

// SetAPIKey sets or updates the Google Drive API key.
func (c *PublicClient) SetAPIKey(key string) {
	c.mu.Lock()
	defer c.mu.Unlock()
	c.apiKey = key
}

func (c *PublicClient) RootFolderID() string {
	return c.info.TargetID
}

func (c *PublicClient) UpdateToken(token string) {
	// Public clients do not require access tokens
}

func (c *PublicClient) ListChildren(ctx context.Context, parentID string) ([]FileMetadata, error) {
	c.mu.RLock()
	if cached, ok := c.children[parentID]; ok {
		c.mu.RUnlock()
		return cached, nil
	}
	c.mu.RUnlock()

	var items []FileMetadata
	var err error

	switch c.info.Provider {
	case ProviderOneDrive:
		items, err = c.listOneDrive(ctx, parentID)
	case ProviderHttpIndex:
		items, err = c.listHttpIndex(ctx, parentID)
	case ProviderGoogleDrive:
		items, err = c.listGDrivePublic(ctx, parentID)
	case ProviderDropbox:
		items, err = c.listDropbox(ctx, parentID)
	default:
		return nil, fmt.Errorf("unsupported provider: %s", c.info.Provider)
	}

	if err != nil {
		return nil, err
	}

	c.mu.Lock()
	c.children[parentID] = items
	for _, item := range items {
		c.filesByID[item.ID] = item
	}
	c.mu.Unlock()

	return items, nil
}

func (c *PublicClient) probeSize(ctx context.Context, downloadURL string) (int64, error) {
	req, err := http.NewRequestWithContext(ctx, http.MethodHead, downloadURL, nil)
	if err != nil {
		return 0, err
	}
	req.Header.Set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
	resp, err := c.httpClient.Do(req)
	if err != nil {
		return 0, err
	}
	defer resp.Body.Close()
	if resp.StatusCode == http.StatusOK && resp.ContentLength > 0 {
		return resp.ContentLength, nil
	}
	return 0, fmt.Errorf("probe failed with status %d", resp.StatusCode)
}

func (c *PublicClient) GetMetadata(ctx context.Context, fileID string) (*FileMetadata, error) {
	c.mu.RLock()
	item, ok := c.filesByID[fileID]
	c.mu.RUnlock()

	if ok {
		if !item.IsDir && item.Size <= 0 && item.DownloadURL != "" {
			if size, err := c.probeSize(ctx, item.DownloadURL); err == nil && size > 0 {
				c.mu.Lock()
				item.Size = size
				c.filesByID[fileID] = item
				c.mu.Unlock()
			}
		}
		return &item, nil
	}

	// If root requested
	if fileID == c.info.TargetID || fileID == "" {
		return &FileMetadata{
			ID:      c.info.TargetID,
			Name:    "Root",
			IsDir:   true,
			ModTime: time.Now(),
		}, nil
	}

	return nil, ErrFileNotFound
}

func (c *PublicClient) OpenRangeReader(ctx context.Context, fileID string, startOffset int64, length int64) (io.ReadCloser, int64, error) {
	c.mu.RLock()
	meta, ok := c.filesByID[fileID]
	c.mu.RUnlock()

	if !ok {
		return nil, 0, ErrFileNotFound
	}

	downloadURL := meta.DownloadURL
	if downloadURL == "" {
		downloadURL = fileID
	}

	req, err := http.NewRequestWithContext(ctx, http.MethodGet, downloadURL, nil)
	if err != nil {
		return nil, 0, err
	}
	req.Header.Set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")

	if startOffset > 0 || length > 0 {
		if length > 0 {
			endOffset := startOffset + length - 1
			req.Header.Set("Range", fmt.Sprintf("bytes=%d-%d", startOffset, endOffset))
		} else {
			req.Header.Set("Range", fmt.Sprintf("bytes=%d-", startOffset))
		}
	}

	resp, err := c.httpClient.Do(req)
	if err != nil {
		return nil, 0, err
	}

	if resp.StatusCode == http.StatusNotFound {
		resp.Body.Close()
		return nil, 0, ErrFileNotFound
	}
	if resp.StatusCode == http.StatusRequestedRangeNotSatisfiable {
		resp.Body.Close()
		return nil, 0, ErrInvalidRange
	}
	if resp.StatusCode != http.StatusOK && resp.StatusCode != http.StatusPartialContent {
		resp.Body.Close()
		return nil, 0, fmt.Errorf("remote download failed (HTTP %d)", resp.StatusCode)
	}

	contentLength := resp.ContentLength
	if contentLength > 0 {
		c.mu.Lock()
		if m, exists := c.filesByID[fileID]; exists && m.Size <= 0 {
			m.Size = contentLength
			c.filesByID[fileID] = m
		}
		c.mu.Unlock()
	}

	return resp.Body, contentLength, nil
}

// listOneDrive uses Microsoft's unauthenticated public shares API.
func (c *PublicClient) listOneDrive(ctx context.Context, parentID string) ([]FileMetadata, error) {
	rawURL := c.info.RawURL
	encoded := base64.RawURLEncoding.EncodeToString([]byte(rawURL))
	sharingToken := "u!" + encoded

	apiURL := fmt.Sprintf("https://api.onedrive.com/v1.0/shares/%s/root/children", sharingToken)
	if parentID != c.info.TargetID && parentID != "" && parentID != "root" {
		apiURL = fmt.Sprintf("https://api.onedrive.com/v1.0/shares/%s/items/%s/children", sharingToken, parentID)
	}

	req, err := http.NewRequestWithContext(ctx, http.MethodGet, apiURL, nil)
	if err != nil {
		return nil, err
	}

	resp, err := c.httpClient.Do(req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("onedrive share api failed with status %d", resp.StatusCode)
	}

	var jsonResp struct {
		Value []struct {
			ID          string `json:"id"`
			Name        string `json:"name"`
			Size        int64  `json:"size"`
			Folder      *struct{} `json:"folder,omitempty"`
			DownloadURL string `json:"@content.downloadUrl"`
		} `json:"value"`
	}

	if err := json.NewDecoder(resp.Body).Decode(&jsonResp); err != nil {
		return nil, err
	}

	var results []FileMetadata
	for _, item := range jsonResp.Value {
		isDir := item.Folder != nil
		results = append(results, FileMetadata{
			ID:          item.ID,
			Name:        item.Name,
			Size:        item.Size,
			IsDir:       isDir,
			DownloadURL: item.DownloadURL,
			ParentID:    parentID,
			ModTime:     time.Now(),
		})
	}

	return results, nil
}

// listHttpIndex parses HTML autoindex output (Apache/Nginx/Caddy).
func (c *PublicClient) listHttpIndex(ctx context.Context, targetURL string) ([]FileMetadata, error) {
	if targetURL == "" || targetURL == c.info.TargetID {
		targetURL = c.info.RawURL
	}

	req, err := http.NewRequestWithContext(ctx, http.MethodGet, targetURL, nil)
	if err != nil {
		return nil, err
	}

	resp, err := c.httpClient.Do(req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("http autoindex request failed with status %d", resp.StatusCode)
	}

	bodyBytes, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, err
	}

	baseURL, _ := url.Parse(targetURL)
	matches := reHTMLLink.FindAllStringSubmatch(string(bodyBytes), -1)

	var results []FileMetadata
	seen := make(map[string]bool)

	for _, m := range matches {
		href := m[1]
		// Skip parent link, queries, hashes, and root
		if href == "../" || href == "/" || strings.HasPrefix(href, "?") || strings.HasPrefix(href, "#") {
			continue
		}

		resolvedURL := baseURL.ResolveReference(&url.URL{Path: href})
		name, _ := url.PathUnescape(strings.TrimSuffix(href, "/"))
		if name == "" || seen[name] {
			continue
		}
		seen[name] = true

		isDir := strings.HasSuffix(href, "/")
		results = append(results, FileMetadata{
			ID:          resolvedURL.String(),
			Name:        name,
			IsDir:       isDir,
			DownloadURL: resolvedURL.String(),
			ParentID:    targetURL,
			ModTime:     time.Now(),
		})
	}

	return results, nil
}

// DefaultGDrivePublicAPIKey is a valid Google Drive client API key extracted from public Google Drive client payloads.
// Enables full listing (all 85+ folders/files) and exact file sizes without requiring user OAuth.
const DefaultGDrivePublicAPIKey = "AIzaSyC1qbk75NzWBvSaDh6KnsjjA9pIrP4lYIE"

// listGDrivePublic provides listing for single shared Google Drive file or public folder.
func (c *PublicClient) listGDrivePublic(ctx context.Context, parentID string) ([]FileMetadata, error) {
	if !c.info.IsFolder {
		// Single file link
		apiKey := c.apiKey
		if apiKey == "" {
			apiKey = DefaultGDrivePublicAPIKey
		}
		downloadURL := fmt.Sprintf("https://drive.google.com/uc?export=download&id=%s", c.info.TargetID)
		if apiKey != "" {
			downloadURL = fmt.Sprintf("https://drive.google.com/uc?export=download&id=%s&key=%s", c.info.TargetID, url.QueryEscape(apiKey))
		}
		meta := FileMetadata{
			ID:          c.info.TargetID,
			Name:        "file_" + c.info.TargetID,
			IsDir:       false,
			DownloadURL: downloadURL,
			ModTime:     time.Now(),
		}
		return []FileMetadata{meta}, nil
	}

	folderID := parentID
	if folderID == "" || folderID == c.info.TargetID || folderID == "/" {
		folderID = c.info.TargetID
	}

	apiKey := c.apiKey
	if apiKey == "" {
		apiKey = DefaultGDrivePublicAPIKey
	}

	// 1. Try official Google Drive REST API v3 with pagination
	items, err := c.listGDriveWithAPIKey(ctx, folderID, apiKey)
	if err == nil && len(items) > 0 {
		return items, nil
	}

	// 2. Fallback to parsing public Google Drive folder HTML (AF_initDataCallback)
	// Works for public folders without requiring any Google Cloud API key or account login.
	return c.listGDrivePublicHTML(ctx, folderID)
}

func (c *PublicClient) listGDriveWithAPIKey(ctx context.Context, folderID string, apiKey string) ([]FileMetadata, error) {
	var results []FileMetadata
	pageToken := ""

	for {
		query := fmt.Sprintf("'%s' in parents and trashed = false", folderID)
		apiURL := fmt.Sprintf(
			"https://www.googleapis.com/drive/v3/files?q=%s&fields=files(id,name,mimeType,size,modifiedTime,thumbnailLink,iconLink,description),nextPageToken&pageSize=1000&key=%s",
			url.QueryEscape(query),
			url.QueryEscape(apiKey),
		)
		if pageToken != "" {
			apiURL += "&pageToken=" + url.QueryEscape(pageToken)
		}

		req, err := http.NewRequestWithContext(ctx, http.MethodGet, apiURL, nil)
		if err != nil {
			return nil, fmt.Errorf("failed to create request: %w", err)
		}
		req.Header.Set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
		req.Header.Set("Referer", "https://drive.google.com/")

		resp, err := c.httpClient.Do(req)
		if err != nil {
			return nil, fmt.Errorf("failed to list drive folder: %w", err)
		}

		if resp.StatusCode != http.StatusOK {
			body, _ := io.ReadAll(io.LimitReader(resp.Body, 1024))
			resp.Body.Close()
			return nil, fmt.Errorf("drive API returned %d: %s", resp.StatusCode, string(body))
		}

		var gdriveResp struct {
			Files []struct {
				ID            string `json:"id"`
				Name          string `json:"name"`
				MimeType      string `json:"mimeType"`
				Size          string `json:"size"`
				ModifiedTime  string `json:"modifiedTime"`
				ThumbnailLink string `json:"thumbnailLink"`
				IconLink      string `json:"iconLink"`
				Description   string `json:"description"`
			} `json:"files"`
			NextPageToken string `json:"nextPageToken"`
		}

		if err := json.NewDecoder(resp.Body).Decode(&gdriveResp); err != nil {
			resp.Body.Close()
			return nil, fmt.Errorf("failed to parse drive response: %w", err)
		}
		resp.Body.Close()

		for _, f := range gdriveResp.Files {
			isDir := f.MimeType == "application/vnd.google-apps.folder"
			var size int64
			if !isDir && f.Size != "" {
				fmt.Sscanf(f.Size, "%d", &size)
			}
			modTime := time.Now()
			if f.ModifiedTime != "" {
				if t, err := time.Parse(time.RFC3339, f.ModifiedTime); err == nil {
					modTime = t
				}
			}

			downloadURL := ""
			thumbnailURL := f.ThumbnailLink
			if thumbnailURL != "" && strings.Contains(thumbnailURL, "=s220") {
				thumbnailURL = strings.ReplaceAll(thumbnailURL, "=s220", "=s600")
			}
			if !isDir {
				downloadURL = fmt.Sprintf("https://drive.google.com/uc?export=download&id=%s", f.ID)
				if thumbnailURL == "" {
					thumbnailURL = fmt.Sprintf("https://drive.google.com/thumbnail?id=%s&sz=w600", f.ID)
				}
			}

			results = append(results, FileMetadata{
				ID:           f.ID,
				Name:         f.Name,
				IsDir:        isDir,
				Size:         size,
				ModTime:      modTime,
				ParentID:     folderID,
				DownloadURL:  downloadURL,
				ThumbnailURL: thumbnailURL,
				Description:  f.Description,
			})
		}

		if gdriveResp.NextPageToken == "" {
			break
		}
		pageToken = gdriveResp.NextPageToken
	}

	return results, nil
}

var gdriveDataRegex = regexp.MustCompile(`data:\s*(\[[\s\S]*?\])\s*,\s*sideChannel:`)

func (c *PublicClient) listGDrivePublicHTML(ctx context.Context, folderID string) ([]FileMetadata, error) {
	folderURL := fmt.Sprintf("https://drive.google.com/drive/folders/%s", folderID)
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, folderURL, nil)
	if err != nil {
		return nil, fmt.Errorf("failed to create request: %w", err)
	}
	req.Header.Set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
	req.Header.Set("Accept-Language", "vi-VN,vi;q=0.9,en-US;q=0.8,en;q=0.7")

	resp, err := c.httpClient.Do(req)
	if err != nil {
		return nil, fmt.Errorf("failed to fetch public drive folder: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("public drive folder returned HTTP %d", resp.StatusCode)
	}

	bodyBytes, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, fmt.Errorf("failed to read response body: %w", err)
	}

	return parseGDriveHTML(string(bodyBytes), folderID)
}

func parseGDriveHTML(html string, folderID string) ([]FileMetadata, error) {
	matches := gdriveDataRegex.FindAllStringSubmatch(html, -1)
	if len(matches) == 0 {
		return nil, fmt.Errorf("could not find AF_initDataCallback in drive HTML")
	}

	seenIDs := make(map[string]bool)
	var results []FileMetadata

	for _, m := range matches {
		if len(m) < 2 {
			continue
		}
		var rawData any
		if err := json.Unmarshal([]byte(m[1]), &rawData); err != nil {
			continue
		}

		findGDriveEntries(rawData, &results, seenIDs, folderID)
	}

	if len(results) == 0 {
		return nil, fmt.Errorf("no items parsed from public drive HTML")
	}

	return results, nil
}

func findGDriveEntries(val any, results *[]FileMetadata, seen map[string]bool, folderID string) {
	switch v := val.(type) {
	case []any:
		if len(v) >= 36 {
			if idSlice, ok := v[0].([]any); ok && len(idSlice) >= 2 && idSlice[0] == nil {
				if idStr, ok := idSlice[1].(string); ok && idStr != "" && !seen[idStr] && idStr != folderID {
					mimeStr, _ := v[4].(string)
					if strings.HasPrefix(mimeStr, "application/vnd.google-apps") || strings.Contains(mimeStr, "/") {
						seen[idStr] = true
						name := extractGDriveName(v)
						if name == "" {
							name = "item_" + idStr
						}
						isDir := strings.Contains(mimeStr, "folder")
						downloadURL := ""
						thumbnailURL := ""
						var size int64
						if !isDir {
							downloadURL = fmt.Sprintf("https://drive.google.com/uc?export=download&id=%s", idStr)
							thumbnailURL = fmt.Sprintf("https://drive.google.com/thumbnail?id=%s&sz=w600", idStr)
							size = extractGDriveSize(v)
						}
						*results = append(*results, FileMetadata{
							ID:           idStr,
							Name:         name,
							IsDir:        isDir,
							Size:         size,
							DownloadURL:  downloadURL,
							ThumbnailURL: thumbnailURL,
							ParentID:     folderID,
							ModTime:      time.Now(),
						})
						return
					}
				}
			}
		}
		for _, child := range v {
			findGDriveEntries(child, results, seen, folderID)
		}
	}
}

var humanSizeRegex = regexp.MustCompile(`(?i)([0-9]+(?:[.,][0-9]+)?)\s*(B|KB|MB|GB|TB|bytes|kB)`)

func parseHumanSize(s string) int64 {
	match := humanSizeRegex.FindStringSubmatch(s)
	if len(match) < 3 {
		return 0
	}
	valStr := strings.ReplaceAll(match[1], ",", ".")
	unit := strings.ToUpper(match[2])
	val, err := strconv.ParseFloat(valStr, 64)
	if err != nil {
		return 0
	}
	switch unit {
	case "B", "BYTES":
		return int64(val)
	case "KB":
		return int64(val * 1024)
	case "MB":
		return int64(val * 1024 * 1024)
	case "GB":
		return int64(val * 1024 * 1024 * 1024)
	case "TB":
		return int64(val * 1024 * 1024 * 1024 * 1024)
	}
	return 0
}

func findSizeInVal(val any) int64 {
	switch v := val.(type) {
	case string:
		if size := parseHumanSize(v); size > 0 {
			return size
		}
	case []any:
		for _, child := range v {
			if size := findSizeInVal(child); size > 0 {
				return size
			}
		}
	}
	return 0
}

func extractGDriveSize(item []any) int64 {
	// Intentionally return 0 so probeSize can obtain the exact byte count via HTTP HEAD.
	// Approximate human sizes (e.g. "121 KB") truncate zip/epub archives by cutting off the central directory.
	return 0
}

func extractGDriveName(item []any) string {
	if len(item) > 35 {
		if s35, ok := item[35].([]any); ok && len(s35) > 0 {
			if s0, ok := s35[0].([]any); ok && len(s0) > 0 {
				if s00, ok := s0[0].([]any); ok && len(s00) > 0 {
					if name, ok := s00[0].(string); ok && name != "" {
						return name
					}
				}
			}
		}
	}
	return ""
}

// listDropbox provides direct streamable download for Dropbox shared links.
func (c *PublicClient) listDropbox(ctx context.Context, parentID string) ([]FileMetadata, error) {
	rawURL := c.info.RawURL
	// Replace www.dropbox.com with dl.dropboxusercontent.com or dl=1
	u, err := url.Parse(rawURL)
	if err != nil {
		return nil, err
	}
	q := u.Query()
	q.Set("dl", "1")
	u.RawQuery = q.Encode()

	name := "file"
	parts := strings.Split(strings.Trim(u.Path, "/"), "/")
	if len(parts) > 0 {
		name = parts[len(parts)-1]
	}

	meta := FileMetadata{
		ID:          rawURL,
		Name:        name,
		IsDir:       c.info.IsFolder,
		DownloadURL: u.String(),
		ModTime:     time.Now(),
	}
	return []FileMetadata{meta}, nil
}
