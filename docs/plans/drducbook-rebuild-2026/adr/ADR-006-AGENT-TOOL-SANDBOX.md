# ADR-006 - Agent tool sandbox

- Status: Accepted
- Date: 2026-07-29
- Owners: P08

## Context

Repo da co 41 Agent tool definitions. Yeu cau moi can hoan thien tool system va cho Agent tu tao cong cu, nhung khong duoc de generated code tu cap quyen, doc secret hay truy cap Android API tuy y.

## Decision

1. Moi built-in/custom tool co versioned manifest: stable ID, name, description, input/output JSON Schema, permissions, allowed network domains, timeout, max output, author, checksum va lifecycle state.
2. Custom tool runtime duy nhat o v1 la JavaScript trong safe Rhino context. Khong native library, shell, reflection, dynamic class loading, arbitrary Java/Android class access hay direct filesystem.
3. Permission broker cap cac capability nho: `network.http`, `source.read`, `book.read`, `authoring.read`, `authoring.write`, `file.user_selected`, `clipboard.write`. CookieVault, Supabase/Drive sessions, HF secret/ticket, account credentials va unrestricted storage khong bao gio la capability.
4. Network chi HTTP(S), allow-list domain trong manifest, public DNS sau resolve, block localhost/private/link-local/metadata IP, redirect revalidation, response max 5 MiB va timeout toi da 20 giay.
5. Agent co the tao/sua `DRAFT`, sinh schema/tests va yeu cau validate. Chi user co the `APPROVE` va `ENABLE`; update permission/domain/checksum dua tool ve `DRAFT`. Agent khong tu approve, enable hay an audit.
6. Lifecycle: `DRAFT`, `VALIDATED`, `APPROVED`, `ENABLED`, `DISABLED`, `REVOKED`, `QUARANTINED`. Import VBook/skill khong mac nhien tro thanh enabled Agent tool.
7. Validation gom schema, static forbidden-token scan, capability check, deterministic fixture tests, timeout/output tests va malicious corpus. Runtime failure count co circuit breaker, khong tu sua source dang enabled.
8. Audit log chi ghi tool/version, permission decision, timestamps, duration, byte counts va redacted error code. Input/output noi dung mac dinh khong ghi; user co the xoa log.
9. Custom tool source/manifest duoc backup; execution cache, audit payload va secrets bi exclude. Restore tool ve `DISABLED` cho den khi checksum/permission duoc xac nhan tren device moi.

## Public contract

- Manifest schema version `1`; unknown permission bi reject.
- Permission default deny; user denial la final cho invocation do.
- Output phai validate schema truoc khi tra ve Agent.
- Built-in 41 tool contracts co golden snapshot; rename/remove can alias/migration.

## Alternatives

- Cho Agent sinh Kotlin/APK/plugin native: loai bo vi khong co sandbox va release chain.
- Auto-enable tool sau test: loai bo vi vi pham user consent.
- Dung WebView JS: loai bo vi DOM/network/storage surface rong hon can thiet.

## Consequences

- Self-created tool co gioi han nhung co the kiem tra, rollback va dong bo an toan.
- Can permission UI va audit repository rieng.
- Legacy tool goi API truc tiep phai qua adapter/broker.

## Rollback

Kill switch tat toan bo custom tools, built-in tools van hoat dong. Quarantine version loi va phuc hoi version APPROVED truoc theo immutable history; khong sua am tham checksum cu.
