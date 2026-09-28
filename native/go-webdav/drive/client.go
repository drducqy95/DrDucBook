package drive

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"strconv"
	"sync"
	"time"
)

var (
	ErrFileNotFound = errors.New("file not found")
	ErrTokenExpired = errors.New("bearer token expired or unauthorized")
	ErrInvalidRange = errors.New("invalid range requested")
)

// DriveClient defines operations required by DriveFileSystem to query remote storage.
type DriveClient interface {
	// ListChildren returns all non-trashed children in a directory.
	ListChildren(ctx context.Context, parentID string) ([]FileMetadata, error)
	// GetMetadata retrieves file metadata by ID.
	GetMetadata(ctx context.Context, fileID string) (*FileMetadata, error)
	// OpenRangeReader opens an HTTP range stream. If length <= 0, reads to end of file.
	OpenRangeReader(ctx context.Context, fileID string, startOffset int64, length int64) (io.ReadCloser, int64, error)
	// RootFolderID returns the root folder identifier.
	RootFolderID() string
	// UpdateToken updates the Bearer token thread-safely.
	UpdateToken(token string)
}

// GoogleDriveClient implements DriveClient using the official Google Drive v3 REST API.
type GoogleDriveClient struct {
	httpClient   *http.Client
	rootFolderID string
	tokenMu      sync.RWMutex
	bearerToken  string
	baseURL      string
}

// GoogleDriveFileJSON matches the JSON schema returned by Drive v3 files API.
type GoogleDriveFileJSON struct {
	ID           string `json:"id"`
	Name         string `json:"name"`
	Size         string `json:"size"`
	MimeType     string `json:"mimeType"`
	ModifiedTime string `json:"modifiedTime"`
	MD5Checksum  string `json:"md5Checksum"`
}

// GoogleDriveListJSON matches the list response.
type GoogleDriveListJSON struct {
	NextPageToken string                `json:"nextPageToken"`
	Files         []GoogleDriveFileJSON `json:"files"`
}

// NewGoogleDriveClient creates a client targeting Google Drive v3.
func NewGoogleDriveClient(token string, rootFolderID string, customHTTPClient *http.Client) *GoogleDriveClient {
	if rootFolderID == "" {
		rootFolderID = "root"
	}
	if customHTTPClient == nil {
		customHTTPClient = &http.Client{
			Timeout: 45 * time.Second,
			Transport: &http.Transport{
				MaxIdleConns:        50,
				MaxIdleConnsPerHost: 20,
				IdleConnTimeout:     90 * time.Second,
			},
		}
	}
	return &GoogleDriveClient{
		httpClient:   customHTTPClient,
		rootFolderID: rootFolderID,
		bearerToken:  token,
		baseURL:      "https://www.googleapis.com/drive/v3",
	}
}

// SetBaseURL allows overriding the API endpoint for unit testing.
func (c *GoogleDriveClient) SetBaseURL(url string) {
	c.baseURL = url
}

func (c *GoogleDriveClient) RootFolderID() string {
	return c.rootFolderID
}

func (c *GoogleDriveClient) UpdateToken(token string) {
	c.tokenMu.Lock()
	defer c.tokenMu.Unlock()
	c.bearerToken = token
}

func (c *GoogleDriveClient) getToken() string {
	c.tokenMu.RLock()
	defer c.tokenMu.RUnlock()
	return c.bearerToken
}

// ListChildren paginates through Drive v3 files.list.
func (c *GoogleDriveClient) ListChildren(ctx context.Context, parentID string) ([]FileMetadata, error) {
	if parentID == "" {
		parentID = c.rootFolderID
	}

	var results []FileMetadata
	pageToken := ""

	for {
		q := fmt.Sprintf("'%s' in parents and trashed = false", parentID)
		params := url.Values{}
		params.Set("q", q)
		params.Set("fields", "nextPageToken,files(id,name,size,mimeType,modifiedTime,md5Checksum)")
		params.Set("pageSize", "1000")
		params.Set("supportsAllDrives", "true")
		params.Set("includeItemsFromAllDrives", "true")
		if pageToken != "" {
			params.Set("pageToken", pageToken)
		}

		reqURL := fmt.Sprintf("%s/files?%s", c.baseURL, params.Encode())
		req, err := http.NewRequestWithContext(ctx, http.MethodGet, reqURL, nil)
		if err != nil {
			return nil, err
		}

		token := c.getToken()
		if token != "" {
			req.Header.Set("Authorization", "Bearer "+token)
		}

		resp, err := c.executeWithRetry(req)
		if err != nil {
			return nil, err
		}

		if resp.StatusCode == http.StatusUnauthorized {
			resp.Body.Close()
			return nil, ErrTokenExpired
		}
		if resp.StatusCode == http.StatusNotFound {
			resp.Body.Close()
			return nil, ErrFileNotFound
		}
		if resp.StatusCode < 200 || resp.StatusCode >= 300 {
			bodyBytes, _ := io.ReadAll(resp.Body)
			resp.Body.Close()
			return nil, fmt.Errorf("drive API list failed (HTTP %d): %s", resp.StatusCode, string(bodyBytes))
		}

		var listResp GoogleDriveListJSON
		err = json.NewDecoder(resp.Body).Decode(&listResp)
		resp.Body.Close()
		if err != nil {
			return nil, fmt.Errorf("failed to decode drive list response: %w", err)
		}

		for _, f := range listResp.Files {
			// Skip Google Docs native types that cannot be read as raw bytes
			if IsGoogleDocsNative(f.MimeType) {
				continue
			}

			var size int64
			if f.Size != "" {
				size, _ = strconv.ParseInt(f.Size, 10, 64)
			}

			modTime, _ := time.Parse(time.RFC3339, f.ModifiedTime)

			results = append(results, FileMetadata{
				ID:          f.ID,
				Name:        f.Name,
				Size:        size,
				ModTime:     modTime,
				IsDir:       f.MimeType == MimeFolder,
				MimeType:    f.MimeType,
				MD5Checksum: f.MD5Checksum,
				ParentID:    parentID,
			})
		}

		pageToken = listResp.NextPageToken
		if pageToken == "" {
			break
		}
	}

	return results, nil
}

// GetMetadata fetches metadata for a specific file or folder.
func (c *GoogleDriveClient) GetMetadata(ctx context.Context, fileID string) (*FileMetadata, error) {
	if fileID == "" {
		fileID = c.rootFolderID
	}

	params := url.Values{}
	params.Set("fields", "id,name,size,mimeType,modifiedTime,md5Checksum")
	params.Set("supportsAllDrives", "true")

	reqURL := fmt.Sprintf("%s/files/%s?%s", c.baseURL, url.PathEscape(fileID), params.Encode())
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, reqURL, nil)
	if err != nil {
		return nil, err
	}

	token := c.getToken()
	if token != "" {
		req.Header.Set("Authorization", "Bearer "+token)
	}

	resp, err := c.executeWithRetry(req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()

	if resp.StatusCode == http.StatusUnauthorized {
		return nil, ErrTokenExpired
	}
	if resp.StatusCode == http.StatusNotFound {
		return nil, ErrFileNotFound
	}
	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		bodyBytes, _ := io.ReadAll(resp.Body)
		return nil, fmt.Errorf("drive API get failed (HTTP %d): %s", resp.StatusCode, string(bodyBytes))
	}

	var f GoogleDriveFileJSON
	if err := json.NewDecoder(resp.Body).Decode(&f); err != nil {
		return nil, fmt.Errorf("failed to decode drive file response: %w", err)
	}

	var size int64
	if f.Size != "" {
		size, _ = strconv.ParseInt(f.Size, 10, 64)
	}
	modTime, _ := time.Parse(time.RFC3339, f.ModifiedTime)

	return &FileMetadata{
		ID:          f.ID,
		Name:        f.Name,
		Size:        size,
		ModTime:     modTime,
		IsDir:       f.MimeType == MimeFolder,
		MimeType:    f.MimeType,
		MD5Checksum: f.MD5Checksum,
	}, nil
}

// OpenRangeReader opens an HTTP range stream on the file content.
func (c *GoogleDriveClient) OpenRangeReader(ctx context.Context, fileID string, startOffset int64, length int64) (io.ReadCloser, int64, error) {
	reqURL := fmt.Sprintf("%s/files/%s?alt=media&supportsAllDrives=true", c.baseURL, url.PathEscape(fileID))
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, reqURL, nil)
	if err != nil {
		return nil, 0, err
	}

	token := c.getToken()
	if token != "" {
		req.Header.Set("Authorization", "Bearer "+token)
	}

	// Range header formatting
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
		return nil, 0, fmt.Errorf("drive range download request error: %w", err)
	}

	if resp.StatusCode == http.StatusUnauthorized {
		resp.Body.Close()
		return nil, 0, ErrTokenExpired
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
		bodyBytes, _ := io.ReadAll(resp.Body)
		resp.Body.Close()
		return nil, 0, fmt.Errorf("drive range download failed (HTTP %d): %s", resp.StatusCode, string(bodyBytes))
	}

	contentLength := resp.ContentLength
	return resp.Body, contentLength, nil
}

// executeWithRetry executes an HTTP request with exponential backoff on 429 and 5xx errors.
func (c *GoogleDriveClient) executeWithRetry(req *http.Request) (*http.Response, error) {
	maxRetries := 3
	backoff := 500 * time.Millisecond

	for attempt := 0; attempt < maxRetries; attempt++ {
		resp, err := c.httpClient.Do(req)
		if err != nil {
			if attempt == maxRetries-1 {
				return nil, err
			}
			time.Sleep(backoff)
			backoff *= 2
			continue
		}

		if resp.StatusCode == http.StatusTooManyRequests || resp.StatusCode >= 500 {
			resp.Body.Close()
			if attempt == maxRetries-1 {
				return nil, fmt.Errorf("drive API server error (HTTP %d) after %d retries", resp.StatusCode, maxRetries)
			}
			time.Sleep(backoff)
			backoff *= 2
			continue
		}

		return resp, nil
	}

	return nil, errors.New("request failed after retries")
}
