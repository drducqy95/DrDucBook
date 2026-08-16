# Compatibility corpus

This directory freezes the Phase 00 compatibility baseline for DrDucBook.

- Every payload is synthetic, contains no account, cookie, token, or copyrighted book content.
- Network locations use the reserved `.test` domain and must never be contacted by tests.
- Fixtures are released as CC0-1.0 and may be redistributed with the test suite.
- `provenance.json` records one SHA-256 checksum for every compatibility payload.
- `CompatibilityCorpusTest` parses Legado sources, inspects and executes VBook scripts, and validates the public contracts.

The corpus is a behavioral floor. Later phases may add fields or routes, but cannot silently remove a recorded legacy contract.
