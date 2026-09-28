---
title: Session Secret HTTP Basic Auth for Localhost WebDAV
type: decision
slug: session-secret-http-basic-auth-for-localhost-webdav
category: decisions
created: 2026-09-12T12:12:46
updated: 2026-09-12T12:12:46
status: active
source: /plan
tags: ["webdav", "security", "auth", "p34", "auto"]
---

# Session Secret HTTP Basic Auth for Localhost WebDAV

Context: Prevent unauthorized apps on device from accessing localhost WebDAV proxy. Rationale: Random 32-byte hex secret generated on service start, passed to Go server and client via HTTP Basic Auth. Alternatives: No auth on localhost. Impact: Rejects requests without valid secret with HTTP 401.
