package drive

import (
	"os"
	"path"
	"strings"
	"sync"
	"time"
)

// Google Drive MIME types
const (
	MimeFolder     = "application/vnd.google-apps.folder"
	MimePrefixApps = "application/vnd.google-apps."
)

// FileMetadata holds the canonical metadata for a remote or cached file.
type FileMetadata struct {
	ID          string    `json:"id"`
	Name        string    `json:"name"`
	Size        int64     `json:"size"`
	ModTime     time.Time `json:"mod_time"`
	IsDir       bool      `json:"is_dir"`
	MimeType    string    `json:"mime_type"`
	MD5Checksum string    `json:"md5_checksum,omitempty"`
	ParentID    string    `json:"parent_id,omitempty"`
	DownloadURL string    `json:"download_url,omitempty"`
	ThumbnailURL string   `json:"thumbnail_url,omitempty"`
	Description  string   `json:"description,omitempty"`
}

// IsGoogleDocsNative returns true for Google Docs/Sheets/Slides that cannot be streamed raw.
func IsGoogleDocsNative(mime string) bool {
	if mime == MimeFolder {
		return false
	}
	return strings.HasPrefix(mime, MimePrefixApps)
}

// FileInfo adapts FileMetadata to the standard os.FileInfo interface.
type FileInfo struct {
	meta FileMetadata
}

// NewFileInfo creates an os.FileInfo from FileMetadata.
func NewFileInfo(meta FileMetadata) os.FileInfo {
	return &FileInfo{meta: meta}
}

func (fi *FileInfo) Name() string {
	return fi.meta.Name
}

func (fi *FileInfo) Size() int64 {
	if fi.meta.IsDir {
		return 0
	}
	return fi.meta.Size
}

func (fi *FileInfo) Mode() os.FileMode {
	if fi.meta.IsDir {
		return os.ModeDir | 0555
	}
	return 0444
}

func (fi *FileInfo) ModTime() time.Time {
	if fi.meta.ModTime.IsZero() {
		return time.Unix(0, 0)
	}
	return fi.meta.ModTime
}

func (fi *FileInfo) IsDir() bool {
	return fi.meta.IsDir
}

func (fi *FileInfo) Sys() any {
	return fi.meta
}

// CacheEntry stores cached metadata and directory children.
type CacheEntry struct {
	Metadata FileMetadata
	Children []FileMetadata
	Expires  time.Time
}

// MetadataCache provides thread-safe in-memory caching with TTL and capacity limits.
type MetadataCache struct {
	mu      sync.RWMutex
	entries map[string]CacheEntry
	ttl     time.Duration
	maxSize int
}

// NewMetadataCache initializes a cache with TTL (default 5 min) and maximum items (default 1000).
func NewMetadataCache(ttl time.Duration, maxSize int) *MetadataCache {
	if ttl <= 0 {
		ttl = 5 * time.Minute
	}
	if maxSize <= 0 {
		maxSize = 1000
	}
	return &MetadataCache{
		entries: make(map[string]CacheEntry),
		ttl:     ttl,
		maxSize: maxSize,
	}
}

// CleanPath standardizes WebDAV paths to ensure consistent lookup.
func CleanPath(p string) string {
	cleaned := path.Clean("/" + strings.TrimSpace(p))
	return cleaned
}

// Get retrieves metadata and children if cached and not expired.
func (c *MetadataCache) Get(p string) (*FileMetadata, []FileMetadata, bool) {
	key := CleanPath(p)
	c.mu.RLock()
	entry, found := c.entries[key]
	c.mu.RUnlock()

	if !found {
		return nil, nil, false
	}

	if time.Now().After(entry.Expires) {
		c.mu.Lock()
		delete(c.entries, key)
		c.mu.Unlock()
		return nil, nil, false
	}

	metaCopy := entry.Metadata
	var childrenCopy []FileMetadata
	if entry.Children != nil {
		childrenCopy = make([]FileMetadata, len(entry.Children))
		copy(childrenCopy, entry.Children)
	}

	return &metaCopy, childrenCopy, true
}

// Put adds or updates a cache entry.
func (c *MetadataCache) Put(p string, meta FileMetadata, children []FileMetadata) {
	key := CleanPath(p)
	c.mu.Lock()
	defer c.mu.Unlock()

	// Simple eviction: clear expired entries if over maxSize
	if len(c.entries) >= c.maxSize {
		now := time.Now()
		for k, v := range c.entries {
			if now.After(v.Expires) {
				delete(c.entries, k)
			}
		}
		// If still full, remove arbitrary item
		if len(c.entries) >= c.maxSize {
			for k := range c.entries {
				delete(c.entries, k)
				break
			}
		}
	}

	var childrenCopy []FileMetadata
	if children != nil {
		childrenCopy = make([]FileMetadata, len(children))
		copy(childrenCopy, children)
	}

	c.entries[key] = CacheEntry{
		Metadata: meta,
		Children: childrenCopy,
		Expires:  time.Now().Add(c.ttl),
	}
}

// Invalidate removes a specific path from the cache.
func (c *MetadataCache) Invalidate(p string) {
	key := CleanPath(p)
	c.mu.Lock()
	delete(c.entries, key)
	c.mu.Unlock()
}

// Clear removes all cached items.
func (c *MetadataCache) Clear() {
	c.mu.Lock()
	c.entries = make(map[string]CacheEntry)
	c.mu.Unlock()
}
