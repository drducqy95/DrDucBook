package drive

import (
	"context"
	"fmt"
	"io"
	"net/http"
	"net/http/httptest"
	"os"
	"strconv"
	"strings"
	"testing"
	"time"
)

func TestMetadataCache(t *testing.T) {
	cache := NewMetadataCache(100*time.Millisecond, 10)

	meta := FileMetadata{
		ID:      "id_1",
		Name:    "book.epub",
		Size:    1024,
		IsDir:   false,
		ModTime: time.Now(),
	}

	cache.Put("/books/book.epub", meta, nil)

	gotMeta, _, found := cache.Get("/books/book.epub")
	if !found || gotMeta == nil {
		t.Fatalf("expected item in cache, got not found")
	}
	if gotMeta.ID != "id_1" || gotMeta.Name != "book.epub" {
		t.Errorf("unexpected metadata: %+v", gotMeta)
	}

	// Test CleanPath variation
	gotMeta2, _, found2 := cache.Get("books/book.epub")
	if !found2 || gotMeta2 == nil {
		t.Errorf("expected clean path to match")
	}

	// Test Invalidate
	cache.Invalidate("/books/book.epub")
	_, _, foundAfterInvalidate := cache.Get("/books/book.epub")
	if foundAfterInvalidate {
		t.Errorf("expected item to be invalidated")
	}

	// Test TTL
	cache.Put("/temp.txt", meta, nil)
	time.Sleep(150 * time.Millisecond)
	_, _, foundExpired := cache.Get("/temp.txt")
	if foundExpired {
		t.Errorf("expected item to expire after TTL")
	}
}

func TestGoogleDriveClient_ListAndGet(t *testing.T) {
	ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		auth := r.Header.Get("Authorization")
		if auth != "Bearer test_token" {
			w.WriteHeader(http.StatusUnauthorized)
			return
		}

		if strings.HasPrefix(r.URL.Path, "/files/file_123") {
			w.Header().Set("Content-Type", "application/json")
			_, _ = w.Write([]byte(`{
				"id": "file_123",
				"name": "Story.txt",
				"size": "2048",
				"mimeType": "text/plain",
				"modifiedTime": "2026-09-12T10:00:00Z"
			}`))
			return
		}

		if strings.HasPrefix(r.URL.Path, "/files") {
			w.Header().Set("Content-Type", "application/json")
			_, _ = w.Write([]byte(`{
				"nextPageToken": "",
				"files": [
					{
						"id": "file_123",
						"name": "Story.txt",
						"size": "2048",
						"mimeType": "text/plain",
						"modifiedTime": "2026-09-12T10:00:00Z"
					},
					{
						"id": "doc_native",
						"name": "Google Doc Native",
						"size": "0",
						"mimeType": "application/vnd.google-apps.document",
						"modifiedTime": "2026-09-12T10:00:00Z"
					},
					{
						"id": "folder_456",
						"name": "LightNovels",
						"size": "0",
						"mimeType": "application/vnd.google-apps.folder",
						"modifiedTime": "2026-09-12T10:00:00Z"
					}
				]
			}`))
			return
		}

		w.WriteHeader(http.StatusNotFound)
	}))
	defer ts.Close()

	client := NewGoogleDriveClient("test_token", "root", ts.Client())
	client.SetBaseURL(ts.URL)

	ctx := context.Background()
	children, err := client.ListChildren(ctx, "root")
	if err != nil {
		t.Fatalf("ListChildren failed: %v", err)
	}

	// Native Google Doc should be excluded!
	if len(children) != 2 {
		t.Fatalf("expected 2 children (1 file, 1 folder), got %d", len(children))
	}

	if children[0].Name != "Story.txt" || children[0].IsDir {
		t.Errorf("unexpected first child: %+v", children[0])
	}
	if children[1].Name != "LightNovels" || !children[1].IsDir {
		t.Errorf("unexpected second child: %+v", children[1])
	}

	// Test GetMetadata
	meta, err := client.GetMetadata(ctx, "file_123")
	if err != nil {
		t.Fatalf("GetMetadata failed: %v", err)
	}
	if meta.ID != "file_123" || meta.Size != 2048 {
		t.Errorf("unexpected metadata: %+v", meta)
	}
}

func TestDriveFileReader_RangeSeek(t *testing.T) {
	dummyContent := "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ"
	totalSize := int64(len(dummyContent))

	ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		rangeHeader := r.Header.Get("Range")
		if rangeHeader == "" {
			w.Header().Set("Content-Length", strconv.Itoa(len(dummyContent)))
			_, _ = w.Write([]byte(dummyContent))
			return
		}

		var start, end int64
		end = totalSize - 1
		parts := strings.Split(strings.TrimPrefix(rangeHeader, "bytes="), "-")
		if len(parts) >= 1 && parts[0] != "" {
			start, _ = strconv.ParseInt(parts[0], 10, 64)
		}
		if len(parts) >= 2 && parts[1] != "" {
			end, _ = strconv.ParseInt(parts[1], 10, 64)
		}

		if start >= totalSize {
			w.WriteHeader(http.StatusRequestedRangeNotSatisfiable)
			return
		}

		sub := dummyContent[start : end+1]
		w.Header().Set("Content-Range", fmt.Sprintf("bytes %d-%d/%d", start, end, totalSize))
		w.Header().Set("Content-Length", strconv.Itoa(len(sub)))
		w.WriteHeader(http.StatusPartialContent)
		_, _ = w.Write([]byte(sub))
	}))
	defer ts.Close()

	client := NewGoogleDriveClient("dummy", "root", ts.Client())
	client.SetBaseURL(ts.URL)

	reader := NewDriveFileReader(context.Background(), client, "dummy_id", totalSize)
	defer reader.Close()

	// Read first 5 bytes: "01234"
	buf := make([]byte, 5)
	n, err := reader.Read(buf)
	if err != nil || n != 5 || string(buf) != "01234" {
		t.Fatalf("read 1 failed: %d, %v, got %s", n, err, string(buf))
	}

	// Seek to offset 10 ("A")
	newOff, err := reader.Seek(10, io.SeekStart)
	if err != nil || newOff != 10 {
		t.Fatalf("seek failed: off=%d, err=%v", newOff, err)
	}

	// Read next 5 bytes: "ABCDE"
	buf2 := make([]byte, 5)
	n, err = reader.Read(buf2)
	if err != nil || n != 5 || string(buf2) != "ABCDE" {
		t.Fatalf("read after seek failed: %d, %v, got %s", n, err, string(buf2))
	}
}

func TestPublicLinkParser(t *testing.T) {
	cases := []struct {
		url          string
		wantProvider ProviderType
		wantFolder   bool
	}{
		{
			url:          "https://drive.google.com/drive/folders/1wxyZ123456789",
			wantProvider: ProviderGoogleDrive,
			wantFolder:   true,
		},
		{
			url:          "https://drive.google.com/file/d/1abcDEF987654/view?usp=sharing",
			wantProvider: ProviderGoogleDrive,
			wantFolder:   false,
		},
		{
			url:          "https://1drv.ms/u/s!Alq_SampleFolder",
			wantProvider: ProviderOneDrive,
			wantFolder:   true,
		},
		{
			url:          "https://www.dropbox.com/scl/fo/abc123folder/h?rlkey=xyz",
			wantProvider: ProviderDropbox,
			wantFolder:   true,
		},
		{
			url:          "http://myhost.local/books/",
			wantProvider: ProviderHttpIndex,
			wantFolder:   true,
		},
	}

	for _, c := range cases {
		info, err := ParsePublicLink(c.url)
		if err != nil {
			t.Fatalf("ParsePublicLink(%s) error: %v", c.url, err)
		}
		if info.Provider != c.wantProvider {
			t.Errorf("URL %s: expected provider %s, got %s", c.url, c.wantProvider, info.Provider)
		}
		if info.IsFolder != c.wantFolder {
			t.Errorf("URL %s: expected isFolder %v, got %v", c.url, c.wantFolder, info.IsFolder)
		}
	}
}

func TestDriveFileSystem_ReadOnlyEnforcement(t *testing.T) {
	client := NewGoogleDriveClient("dummy", "root", nil)
	fs := NewDriveFileSystem(client)
	ctx := context.Background()

	if err := fs.Mkdir(ctx, "/newfolder", 0755); err != os.ErrPermission {
		t.Errorf("expected ErrPermission on Mkdir, got %v", err)
	}
	if err := fs.RemoveAll(ctx, "/somefile"); err != os.ErrPermission {
		t.Errorf("expected ErrPermission on RemoveAll, got %v", err)
	}
	if err := fs.Rename(ctx, "/old", "/new"); err != os.ErrPermission {
		t.Errorf("expected ErrPermission on Rename, got %v", err)
	}

	// OpenFile with O_WRONLY must fail
	_, err := fs.OpenFile(ctx, "/test.txt", os.O_WRONLY|os.O_CREATE, 0644)
	if err != os.ErrPermission {
		t.Errorf("expected ErrPermission on OpenFile write, got %v", err)
	}
}

func TestPublicClient_GoogleDrive(t *testing.T) {
	ctx := context.Background()

	// 1. Single file link - no API key needed
	fileInfo := PublicLinkInfo{
		Provider: ProviderGoogleDrive,
		RawURL:   "https://drive.google.com/file/d/sample_file_123/view",
		TargetID: "sample_file_123",
		IsFolder: false,
	}
	clientFile := NewPublicClient(fileInfo, "", nil)
	items, err := clientFile.ListChildren(ctx, "")
	if err != nil {
		t.Fatalf("ListChildren single file failed: %v", err)
	}
	if len(items) != 1 || items[0].ID != "sample_file_123" || items[0].IsDir {
		t.Errorf("unexpected items for single file: %+v", items)
	}

	// 2. Folder link without API key - tests HTML scraping fallback
	tsHTML := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		// Mock AF_initDataCallback with ds:4 containing items
		htmlContent := `
<!DOCTYPE html>
<html>
<body>
<script>
AF_initDataCallback({key: 'ds:4', hash: '2', data:[
	[
		[null, "mock_folder_1"], null, null, null, "application/vnd.google-apps.folder",
		null, null, null, null, null, null, null, null, null, null,
		null, null, null, null, null, null, null, null, null, null,
		null, null, null, null, null, null, null, null, null, null,
		[[["HTML SubFolder", null, 1]]]
	],
	[
		[null, "mock_file_1"], null, null, null, "application/epub+zip",
		null, null, null, null, null, null, null, null, null, null,
		null, null, null, null, null, null, null, null, null, null,
		null, null, null, null, null, null, null, null, null, null,
		[[["HTML Book.epub", null, 1]]]
	]
], sideChannel: {}});
</script>
</body>
</html>`
		w.Header().Set("Content-Type", "text/html; charset=utf-8")
		w.WriteHeader(http.StatusOK)
		w.Write([]byte(htmlContent))
	}))
	defer tsHTML.Close()

	customHTMLClient := &http.Client{
		Transport: roundTripFunc(func(req *http.Request) (*http.Response, error) {
			mockReq, _ := http.NewRequestWithContext(req.Context(), req.Method, tsHTML.URL+req.URL.RequestURI(), req.Body)
			return http.DefaultTransport.RoundTrip(mockReq)
		}),
	}

	folderInfo := PublicLinkInfo{
		Provider: ProviderGoogleDrive,
		RawURL:   "https://drive.google.com/drive/folders/sample_folder_456",
		TargetID: "sample_folder_456",
		IsFolder: true,
	}
	clientNoKey := NewPublicClient(folderInfo, "", customHTMLClient)
	htmlItems, err := clientNoKey.ListChildren(ctx, "")
	if err != nil {
		t.Fatalf("ListChildren with HTML fallback failed: %v", err)
	}
	if len(htmlItems) != 2 {
		t.Fatalf("expected 2 items from HTML fallback, got %d", len(htmlItems))
	}
	if !htmlItems[0].IsDir || htmlItems[0].Name != "HTML SubFolder" {
		t.Errorf("expected first item to be dir 'HTML SubFolder', got: %+v", htmlItems[0])
	}
	if htmlItems[1].IsDir || htmlItems[1].Name != "HTML Book.epub" {
		t.Errorf("expected second item to be file 'HTML Book.epub', got: %+v", htmlItems[1])
	}

	// 3. Folder link with API key and mock server
	ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		apiKey := r.URL.Query().Get("key")
		if apiKey != "test_api_key_xyz" {
			http.Error(w, "invalid key", http.StatusForbidden)
			return
		}
		jsonResp := `{
			"files": [
				{
					"id": "sub_folder_1",
					"name": "SubFolder",
					"mimeType": "application/vnd.google-apps.folder",
					"modifiedTime": "2026-09-12T10:00:00Z"
				},
				{
					"id": "book_file_2",
					"name": "Story.epub",
					"mimeType": "application/epub+zip",
					"size": "2048",
					"modifiedTime": "2026-09-12T10:00:00Z"
				}
			]
		}`
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusOK)
		w.Write([]byte(jsonResp))
	}))
	defer ts.Close()

	// Redirect googleapis.com to mock server using custom Transport
	customClient := &http.Client{
		Transport: roundTripFunc(func(req *http.Request) (*http.Response, error) {
			mockReq, _ := http.NewRequestWithContext(req.Context(), req.Method, ts.URL+req.URL.RequestURI(), req.Body)
			return http.DefaultTransport.RoundTrip(mockReq)
		}),
	}

	clientWithKey := NewPublicClient(folderInfo, "test_api_key_xyz", customClient)
	items, err = clientWithKey.ListChildren(ctx, "")
	if err != nil {
		t.Fatalf("ListChildren with API key failed: %v", err)
	}
	if len(items) != 2 {
		t.Fatalf("expected 2 items, got %d", len(items))
	}
	if !items[0].IsDir || items[0].Name != "SubFolder" {
		t.Errorf("expected first item to be dir SubFolder, got: %+v", items[0])
	}
	if items[1].IsDir || items[1].Name != "Story.epub" || items[1].Size != 2048 {
		t.Errorf("expected second item to be file Story.epub, got: %+v", items[1])
	}
}

type roundTripFunc func(req *http.Request) (*http.Response, error)

func (f roundTripFunc) RoundTrip(req *http.Request) (*http.Response, error) {
	return f(req)
}

func TestParseGDriveHTML_RealCallback(t *testing.T) {
	savedFile := `C:\Users\vanki\.gemini\antigravity\brain\f97d2a91-a8a4-4d72-bbf6-048dd6223996\scratch\cb_ds_4.txt`
	bytes, err := os.ReadFile(savedFile)
	if err != nil {
		t.Skip("skipping real callback test: file not found")
	}

	items, err := parseGDriveHTML(string(bytes), "1i7gSP5wLFuLXVInF7UljNgm50sqnQLLR")
	if err != nil {
		t.Fatalf("parseGDriveHTML failed on real callback: %v", err)
	}

	if len(items) != 50 {
		t.Errorf("expected 50 items from real callback, got %d", len(items))
	}

	// Verify one known folder
	found := false
	for _, item := range items {
		if item.Name == "[Tổng]" && item.IsDir {
			found = true
			break
		}
	}
	if !found {
		t.Errorf("expected to find folder '[Tổng]' in parsed items")
	}
}

func TestPublicClient_RealLiveDriveAPI(t *testing.T) {
	folderInfo := PublicLinkInfo{
		Provider: ProviderGoogleDrive,
		RawURL:   "https://drive.google.com/drive/folders/1KEfJUscLXJbPEzljTiIl6vALghUa-LnG",
		TargetID: "1KEfJUscLXJbPEzljTiIl6vALghUa-LnG",
		IsFolder: true,
	}

	client := NewPublicClient(folderInfo, "AIzaSyC1qbk75NzWBvSaDh6KnsjjA9pIrP4lYIE", &http.Client{Timeout: 30 * time.Second})
	ctx := context.Background()

	items, err := client.ListChildren(ctx, "")
	if err != nil {
		t.Fatalf("ListChildren failed: %v", err)
	}
	if len(items) != 2 {
		t.Fatalf("expected 2 items in (Hệ Thống), got %d", len(items))
	}

	cache := NewMetadataCache(5*time.Minute, 1000)
	_ = cache
	fs := NewDriveFileSystem(client)

	targetFile := items[0]
	f, err := fs.OpenFile(ctx, "/"+targetFile.Name, 0, 0)
	if err != nil {
		t.Fatalf("fs.OpenFile failed: %v", err)
	}
	defer f.Close()

	fi, err := f.Stat()
	if err != nil {
		t.Fatalf("f.Stat failed: %v", err)
	}
	t.Logf("Stat: Name=%s, Size=%d, IsDir=%v", fi.Name(), fi.Size(), fi.IsDir())
	if fi.Size() <= 0 {
		t.Errorf("expected positive file size, got %d", fi.Size())
	}

	buf := make([]byte, 1024)
	n, err := f.Read(buf)
	if err != nil {
		t.Fatalf("f.Read failed: %v", err)
	}
	t.Logf("Read %d bytes (starts with: %q)", n, string(buf[:4]))
	if string(buf[:2]) != "PK" {
		t.Errorf("expected epub (zip PK), got %q", string(buf[:4]))
	}
}


