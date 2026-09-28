package drive

import (
	"context"
	"encoding/xml"
	"fmt"
	"io"
	"os"
	"path"
	"strings"
	"sync"
	"time"

	"golang.org/x/net/webdav"
)

// DriveFileSystem implements webdav.FileSystem backed by DriveClient.
type DriveFileSystem struct {
	client DriveClient
	cache  *MetadataCache
	mu     sync.RWMutex
}

// NewDriveFileSystem creates a read-only WebDAV filesystem over DriveClient.
func NewDriveFileSystem(client DriveClient) *DriveFileSystem {
	return &DriveFileSystem{
		client: client,
		cache:  NewMetadataCache(5*time.Minute, 1000),
	}
}

// Client returns the underlying DriveClient.
func (fs *DriveFileSystem) Client() DriveClient {
	return fs.client
}

// Mkdir is forbidden on read-only filesystem.
func (fs *DriveFileSystem) Mkdir(ctx context.Context, name string, perm os.FileMode) error {
	return os.ErrPermission
}

// RemoveAll is forbidden on read-only filesystem.
func (fs *DriveFileSystem) RemoveAll(ctx context.Context, name string) error {
	return os.ErrPermission
}

// Rename is forbidden on read-only filesystem.
func (fs *DriveFileSystem) Rename(ctx context.Context, oldName, newName string) error {
	return os.ErrPermission
}

// Stat returns FileInfo for a given WebDAV path.
func (fs *DriveFileSystem) Stat(ctx context.Context, name string) (os.FileInfo, error) {
	meta, err := fs.resolvePath(ctx, name)
	if err != nil {
		return nil, err
	}
	return NewFileInfo(*meta), nil
}

// OpenFile opens a read-only file or directory.
func (fs *DriveFileSystem) OpenFile(ctx context.Context, name string, flag int, perm os.FileMode) (webdav.File, error) {
	// Disallow write/create flags
	if flag != os.O_RDONLY && (flag&os.O_WRONLY != 0 || flag&os.O_RDWR != 0 || flag&os.O_CREATE != 0 || flag&os.O_TRUNC != 0) {
		return nil, os.ErrPermission
	}

	meta, err := fs.resolvePath(ctx, name)
	if err != nil {
		return nil, err
	}

	if meta.IsDir {
		return &driveDirFile{
			fs:     fs,
			ctx:    ctx,
			meta:   *meta,
			readIdx: 0,
		}, nil
	}

	if !meta.IsDir && meta.Size <= 0 {
		if probedMeta, err := fs.client.GetMetadata(ctx, meta.ID); err == nil && probedMeta != nil && probedMeta.Size > 0 {
			meta.Size = probedMeta.Size
		}
	}

	reader := NewDriveFileReader(ctx, fs.client, meta.ID, meta.Size)
	return &driveRegularFile{
		reader: reader,
		meta:   *meta,
	}, nil
}

// resolvePath traverses directory hierarchy from root down to target path.
func (fs *DriveFileSystem) resolvePath(ctx context.Context, rawPath string) (*FileMetadata, error) {
	cleaned := CleanPath(rawPath)

	// Root path
	if cleaned == "/" || cleaned == "." || cleaned == "" {
		return &FileMetadata{
			ID:      fs.client.RootFolderID(),
			Name:    "/",
			IsDir:   true,
			ModTime: time.Now(),
		}, nil
	}

	// Check cache
	if meta, _, found := fs.cache.Get(cleaned); found {
		return meta, nil
	}

	// Split into segments and traverse
	parts := strings.Split(strings.Trim(cleaned, "/"), "/")
	currentID := fs.client.RootFolderID()
	var currentMeta *FileMetadata
	currentPath := ""

	for _, part := range parts {
		currentPath = path.Join(currentPath, part)

		// Check if segment is cached
		if cachedMeta, children, found := fs.cache.Get(currentPath); found {
			currentMeta = cachedMeta
			currentID = cachedMeta.ID
			_ = children
			continue
		}

		// List children of current directory
		children, err := fs.listDirectory(ctx, currentPath, currentID)
		if err != nil {
			return nil, err
		}

		// Find matching child
		var match *FileMetadata
		for i := range children {
			if children[i].Name == part {
				match = &children[i]
				break
			}
		}

		if match == nil {
			return nil, os.ErrNotExist
		}

		currentMeta = match
		currentID = match.ID
	}

	return currentMeta, nil
}

// listDirectory retrieves children from cache or remote client.
func (fs *DriveFileSystem) listDirectory(ctx context.Context, dirPath string, folderID string) ([]FileMetadata, error) {
	if _, children, found := fs.cache.Get(dirPath); found && children != nil {
		return children, nil
	}

	children, err := fs.client.ListChildren(ctx, folderID)
	if err != nil {
		return nil, fmt.Errorf("failed to list children of %s: %w", dirPath, err)
	}

	dirMeta := FileMetadata{
		ID:      folderID,
		Name:    path.Base(dirPath),
		IsDir:   true,
		ModTime: time.Now(),
	}
	fs.cache.Put(dirPath, dirMeta, children)

	// Pre-populate individual children in cache
	for _, c := range children {
		childPath := path.Join(dirPath, c.Name)
		fs.cache.Put(childPath, c, nil)
	}

	return children, nil
}

// driveDirFile represents a directory handle.
type driveDirFile struct {
	fs      *DriveFileSystem
	ctx     context.Context
	meta    FileMetadata
	readIdx int
}

func (f *driveDirFile) Close() error {
	return nil
}

func (f *driveDirFile) Read(p []byte) (int, error) {
	return 0, io.EOF
}

func (f *driveDirFile) Write(p []byte) (int, error) {
	return 0, os.ErrPermission
}

func (f *driveDirFile) Seek(offset int64, whence int) (int64, error) {
	return 0, os.ErrPermission
}

func (f *driveDirFile) Stat() (os.FileInfo, error) {
	return NewFileInfo(f.meta), nil
}

func (f *driveDirFile) Readdir(count int) ([]os.FileInfo, error) {
	cleaned := CleanPath(f.meta.Name)
	children, err := f.fs.listDirectory(f.ctx, cleaned, f.meta.ID)
	if err != nil {
		return nil, err
	}

	if f.readIdx >= len(children) {
		if count > 0 {
			return nil, io.EOF
		}
		return []os.FileInfo{}, nil
	}

	end := len(children)
	if count > 0 && f.readIdx+count < end {
		end = f.readIdx + count
	}

	slice := children[f.readIdx:end]
	f.readIdx = end

	infos := make([]os.FileInfo, len(slice))
	for i, c := range slice {
		infos[i] = NewFileInfo(c)
	}

	return infos, nil
}

func (f *driveDirFile) DeadProps() (map[xml.Name]webdav.Property, error) {
	return nil, nil
}

func (f *driveDirFile) Patch(patches []webdav.Proppatch) ([]webdav.Propstat, error) {
	return nil, os.ErrPermission
}

// driveRegularFile represents a readable file handle.
type driveRegularFile struct {
	reader *DriveFileReader
	meta   FileMetadata
}

func (f *driveRegularFile) Close() error {
	return f.reader.Close()
}

func (f *driveRegularFile) Read(p []byte) (int, error) {
	return f.reader.Read(p)
}

func (f *driveRegularFile) Write(p []byte) (int, error) {
	return 0, os.ErrPermission
}

func (f *driveRegularFile) Seek(offset int64, whence int) (int64, error) {
	return f.reader.Seek(offset, whence)
}

func (f *driveRegularFile) Stat() (os.FileInfo, error) {
	if f.meta.Size <= 0 && f.reader.size > 0 {
		f.meta.Size = f.reader.size
	}
	return NewFileInfo(f.meta), nil
}

func (f *driveRegularFile) Readdir(count int) ([]os.FileInfo, error) {
	return nil, os.ErrPermission
}

func (f *driveRegularFile) DeadProps() (map[xml.Name]webdav.Property, error) {
	props := make(map[xml.Name]webdav.Property)
	if f.meta.ThumbnailURL != "" {
		props[xml.Name{Space: "http://drducbook.app/dav/", Local: "thumbnail"}] = webdav.Property{
			XMLName:  xml.Name{Space: "http://drducbook.app/dav/", Local: "thumbnail"},
			InnerXML: []byte(f.meta.ThumbnailURL),
		}
	}
	if f.meta.Description != "" {
		props[xml.Name{Space: "http://drducbook.app/dav/", Local: "description"}] = webdav.Property{
			XMLName:  xml.Name{Space: "http://drducbook.app/dav/", Local: "description"},
			InnerXML: []byte(f.meta.Description),
		}
	}
	return props, nil
}

func (f *driveRegularFile) Patch(patches []webdav.Proppatch) ([]webdav.Propstat, error) {
	return nil, os.ErrPermission
}
