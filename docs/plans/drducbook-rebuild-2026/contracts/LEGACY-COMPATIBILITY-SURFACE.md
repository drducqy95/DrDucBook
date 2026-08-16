# Legacy compatibility surface

## Decision

DrDucBook keeps the compatibility island in `io.legado.app` instead of moving it to a new Gradle module in Phase 01. This preserves serialized names and runtime behavior with less risk while all new product implementation starts in `com.drducbook.app`.

## Owned surfaces

| Surface | Historical package/contract | Owner and coverage |
|---|---|---|
| Legado sources | `BookSource`, `RssSource`, `HttpTTS`, rule entities | Source JSON and JavaScript fields; `CompatibilityCorpusTest` |
| Rhino bridge | `JsExtensions`, `NativeBaseSource`, registered rule wrappers | Source script runtime; compatibility corpus and R8 rules |
| VBook | executor, adapter, importer, inspector | Six plugin kinds in `compat/vbook`; compatibility corpus |
| Public provider | `${applicationId}.readerProvider`, legacy route/payload schema | `reader-provider.json`, manifest test, device test |
| Web API | Existing HTTP/WebSocket paths and payloads | `web-service.json`; Phase 06 owns implementation changes |
| Legacy deep links | `legado://`, `yuedu://` | Chooser aliases only; DrDucBook owns `drducbook://` |

## Dependency boundary

- Compatibility code may use existing Legado domain/data/runtime code.
- Compatibility code must not read Supabase sessions, OAuth tokens, Google Drive tokens, or deployment secrets.
- New product code may call compatibility facades where required; compatibility code must not depend on feature UI in `com.drducbook.app`.
- R8 rules are scoped in `app/legacy-compat-rules.pro`. A package-wide `io.legado.app.**` keep rule is forbidden.

## Release verification

1. Run `CompatibilityCorpusTest` against the production runtime.
2. Build the minified release APK.
3. Run `scripts/compat/verify-legacy-abi.ps1` with the release APK.
4. Confirm every class in `app/legacy-compat-abi.txt` remains present by historical name.
