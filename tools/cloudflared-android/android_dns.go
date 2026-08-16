package main

import (
	"context"
	"net"
	"os"
	"time"
)

// Android does not provide /etc/resolv.conf to ordinary app processes. The stock
// statically linked Linux binary therefore falls back to [::1]:53. The app passes
// the active Android network DNS server through this environment variable.
func init() {
	dnsServer := os.Getenv("CLOUDFLARED_ANDROID_DNS")
	if dnsServer == "" {
		return
	}
	dialer := net.Dialer{Timeout: 10 * time.Second}
	net.DefaultResolver = &net.Resolver{
		PreferGo: true,
		Dial: func(ctx context.Context, network, _ string) (net.Conn, error) {
			return dialer.DialContext(ctx, network, dnsServer)
		},
	}
}
