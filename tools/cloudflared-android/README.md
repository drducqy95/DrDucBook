# Cloudflare Tunnel for Android

The app packages cloudflared 2026.5.2 for its supported Android ABIs. The only
source addition is `android_dns.go`, which gives Go's resolver the DNS server
reported by Android because Android app processes do not have `/etc/resolv.conf`.

Build from the matching upstream source archive with Go 1.26.3:

```powershell
./tools/cloudflared-android/build.ps1 `
  -CloudflaredSource ./.codex-tmp/cloudflared-2026.5.2 `
  -GoExecutable ./.codex-tmp/go/bin/go.exe
```

cloudflared is distributed under the Apache License 2.0. Upstream source:
https://github.com/cloudflare/cloudflared/tree/2026.5.2
