package drive

import (
	"context"
	"fmt"
	"io"
	"os"
	"sync"
)

// DriveFileReader implements io.ReadSeekCloser over remote HTTP Range requests.
type DriveFileReader struct {
	ctx          context.Context
	client       DriveClient
	fileID       string
	size         int64
	currOffset   int64
	activeReader io.ReadCloser
	mu           sync.Mutex
	closed       bool
}

// NewDriveFileReader initializes a seekable reader for a remote file.
func NewDriveFileReader(ctx context.Context, client DriveClient, fileID string, size int64) *DriveFileReader {
	return &DriveFileReader{
		ctx:        ctx,
		client:     client,
		fileID:     fileID,
		size:       size,
		currOffset: 0,
	}
}

// Read reads up to len(p) bytes from the remote stream.
func (r *DriveFileReader) Read(p []byte) (int, error) {
	r.mu.Lock()
	defer r.mu.Unlock()

	if r.closed {
		return 0, os.ErrClosed
	}

	if r.size > 0 && r.currOffset >= r.size {
		return 0, io.EOF
	}

	if r.activeReader == nil {
		// Open a new HTTP stream from currOffset to EOF
		rc, contentLength, err := r.client.OpenRangeReader(r.ctx, r.fileID, r.currOffset, 0)
		if err != nil {
			return 0, fmt.Errorf("failed to open range reader at offset %d: %w", r.currOffset, err)
		}
		if r.size <= 0 && contentLength > 0 {
			r.size = contentLength
		} else if contentLength > r.size {
			r.size = contentLength
		}
		r.activeReader = rc
	}

	n, err := r.activeReader.Read(p)
	r.currOffset += int64(n)

	if err != nil {
		// Close stream on EOF or network error so subsequent calls re-establish or terminate
		_ = r.activeReader.Close()
		r.activeReader = nil
	}

	return n, err
}

// Seek sets the offset for the next Read. Any active HTTP stream is closed and reopened on next Read.
func (r *DriveFileReader) Seek(offset int64, whence int) (int64, error) {
	r.mu.Lock()
	defer r.mu.Unlock()

	if r.closed {
		return 0, os.ErrClosed
	}

	var target int64
	switch whence {
	case io.SeekStart:
		target = offset
	case io.SeekCurrent:
		target = r.currOffset + offset
	case io.SeekEnd:
		target = r.size + offset
	default:
		return 0, fmt.Errorf("invalid whence: %d", whence)
	}

	if target < 0 {
		return 0, ErrInvalidRange
	}

	if target == r.currOffset {
		return r.currOffset, nil
	}

	// Invalidate current stream if offset changed
	if r.activeReader != nil {
		_ = r.activeReader.Close()
		r.activeReader = nil
	}

	r.currOffset = target
	return r.currOffset, nil
}

// Close terminates any active HTTP range stream.
func (r *DriveFileReader) Close() error {
	r.mu.Lock()
	defer r.mu.Unlock()

	if r.closed {
		return nil
	}

	r.closed = true
	if r.activeReader != nil {
		err := r.activeReader.Close()
		r.activeReader = nil
		return err
	}

	return nil
}
