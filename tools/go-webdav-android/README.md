# Go WebDAV Android Bridge (`go-webdav.aar`)

This module provides a secure, loopback-only (`127.0.0.1`) WebDAV proxy server implemented in Go and compiled into an Android Archive (`.aar`) using Gomobile. It enables Legado to seamlessly browse and stream remote cloud storage (Google Drive, OneDrive, Dropbox, and generic HTTP directories) through Legado's native WebDAV client.

---

## 1. Architecture Overview

```
Legado WebDAV Client (OkHttp)
       │ HTTP / Basic Auth
       ▼ (127.0.0.1:<random_port>)
Go WebDAV Server (native/go-webdav/server)
       │
       ▼
Virtual Filesystem & Reader (native/go-webdav/drive)
       │
       ├── Google Drive API v3 (drive.file scope / Public)
       ├── OneDrive (Public shared links)
       ├── Dropbox (Public shared links)
       └── HTTP Directory Listing
```

### Key Security & Confinement Guarantees
1. **Loopback Only**: The server explicitly binds to `127.0.0.1:<dynamic_port>`. It does NOT listen on `0.0.0.0` or external network interfaces.
2. **Ephemeral Session Secret**: On startup, a cryptographically secure 32-byte (64 hex characters) random token is generated. Every HTTP request requires Basic Auth (`legado:<secret>`).
3. **Strict Read-Only**: Write operations (`PUT`, `DELETE`, `MKCOL`, `MOVE`, `COPY`, `PROPPATCH`) return `403 Forbidden`.
4. **Lifecycle Bound**: Managed by `DriveWebDavService` as an Android foreground service with `foregroundServiceType="dataSync"`. When user browsing terminates or the service stops, the server gracefully shuts down within 5 seconds.

---

## 2. Directory Structure

```
native/go-webdav/
├── go.mod                 # Module io.legado.gowebdav
├── drive/
│   ├── metadata.go        # Virtual directory & item cache
│   ├── client.go          # Google Drive v3 client with Range request support
│   ├── public_client.go   # Multi-provider public URL resolver
│   ├── reader.go          # io.ReadSeeker implementation with Range seeking
│   └── filesystem.go      # golang.org/x/net/webdav.FileSystem implementation
├── server/
│   ├── config.go          # Server configuration JSON
│   ├── server.go          # WebDAV HTTP handler, Basic Auth & lifecycle
│   └── errors.go          # Error constants
└── bind/
    └── bridge.go          # Gomobile C-shared bridge exported to Java/Kotlin
```

---

## 3. Building the AAR

### Prerequisites
- Go 1.23+ (configured in `D:\Android\go` or in PATH)
- Gomobile (`go install golang.org/x/mobile/cmd/gomobile@latest`)
- Android NDK 26+ (no spaces in the path)

### Build Command
Run the automated PowerShell build script:

```powershell
.\tools\go-webdav-android\build.ps1
```

The script will:
1. Run `go vet ./...` and `go test ./...` in `native/go-webdav`.
2. Execute `gomobile bind` targeting `android/arm`, `android/arm64`, and `android/amd64` (x86_64).
3. Output the generated AAR and SHA256 checksum to `app/libs/go-webdav.aar`.

---

## 4. Android Integration

### Proguard / R8 Keep Rules
In `app/proguard-rules.pro`:
```proguard
-keep class io.legado.app.gowebdav.bind.** { *; }
-keep class go.Seq { *; }
```

### Kotlin Bridge Usage
```kotlin
val bridge = GoBridge()
val port = bridge.start(
    configJson = """{"port": 0, "request_timeout_sec": 10, "read_only": true}""",
    accessToken = googleDriveToken
).getOrThrow()

val basicAuthHeader = Credentials.basic("legado", bridge.sessionSecret)
// Connect via http://127.0.0.1:$port/
```
