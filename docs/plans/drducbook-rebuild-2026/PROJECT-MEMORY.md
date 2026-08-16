# DrDucBook Project Memory

Last updated: 2026-08-02 12:00 local.

## Workspace

- Main workspace: `D:\Downloads\Archives\legado-with-MD3-main\legado-with-MD3-main`
- App package target: `com.drducbook.app`
- Debug package currently tested on LDPlayer: `com.drducbook.app.debug`
- Device used most recently: `emulator-5554`
- ADB path used: `D:\Android\Android Studio\SDK\Sdk\platform-tools\adb.exe`
- Git status is not available in this workspace right now: `fatal: not a git repository`.

## Latest Session Result

The latest session recovered the full Android debug unit gate after two regressions:

- `LocalAiTranslationPrompt.kt`
  - Fixed local AI prompt style extraction after the default JSON-refiner prompt changed.
  - User custom suffix after `TranslationConstants.DEFAULT_PROMPT` is now kept as compact `STYLE` guidance for local models.
- `FileDocExtensions.kt`
  - Fixed filesystem path and `file://` URI handling for local paths with Windows drive letters.
  - This prevents EPUB export with local comic images from failing before a valid output file is created.

Verification completed:

- Targeted tests PASS:
  - `io.legado.app.domain.model.LocalAiTranslationPromptTest`
  - `io.legado.app.domain.usecase.ExportAuthoringProjectUseCaseTest`
- Full Android debug unit suite PASS:
  - 222 XML files
  - 944 tests
  - 0 failures
  - 0 errors
  - 1 skipped
- `:app:assembleAppDebug` PASS.
- Installed x86_64 debug APK to LDPlayer `emulator-5554` PASS.
- Launched `MainActivity` PASS.
- Latest logcat sample did not show matched startup crash lines.

Latest debug APK hashes:

- `app/build/outputs/apk/app/debug/app-app-x86_64-debug.apk`
  - SHA-256 `566E5F51ADC95AFD3E18D85282611CD9F15378922F4EB778288038D602DFBD90`
- `app/build/outputs/apk/app/debug/app-app-universal-debug.apk`
  - SHA-256 `A45E63380E69F3B010AFE75880F08370475316DC3D9C526A9134766C95D39F06`

Reports/logs already updated:

- `docs/plans/drducbook-rebuild-2026/reports/P11-T01-FULL-BUILD-TEST-MATRIX.md`
- `docs/plans/drducbook-rebuild-2026/reports/P11-T04-PERFORMANCE-A11Y-SECURITY-AUDIT.md`
- `docs/plans/drducbook-rebuild-2026/reports/P11-T08-AI-TRANSLATION-PIPELINE-REWRITE.md`
- `docs/plans/drducbook-rebuild-2026/TASK-MATRIX.md`
- `docs/plans/drducbook-rebuild-2026/PLAN-LOG.md`

## Important Previous Checkpoints

- Agent tool policy alias hotfix is implemented and installed:
  - Provider alias `create_vbook_plugin.draft` is canonicalized to `create_vbook_plugin_draft`.
  - Tool proposal, approval, trace and execution all use canonical names.
  - Startup policy migration enables mutation/skill/plugin tool flags for debug installs.
- Translation/export system has current design:
  - Reader has a translation content page.
  - Finalized translations are stored permanently and only change when the user edits/finalizes again.
  - Non-finalized translated content resolves from cache priority: AI provider, NMT, Quick Translator, Google, ML Kit.
  - Ebook export supports `original`, `translation`, and `both`.
  - SAF export file creation uses MIME type by file extension.
- VBook/Legado compatibility hotfixes already applied:
  - VBook executor supports `Base64`, `btoa`, `atob`, extensionless `load()`, charset decoding and normalized JS/native errors.
  - VBook content parser handles older envelopes and chapter URL aliases.
  - HTML-like VBook chapter content is normalized to reader-safe plain text.
  - Comic/image content paths are handled as image blocks instead of falling back to text reader where possible.
- Media/video work already applied:
  - VBook video source open path routes to integrated player.
  - Embedded browser auto-open was reduced by media open policy.
  - Download management and HLS/direct/DASH paths have focused tests.

## Open Gates

Keep these tasks `IN_PROGRESS` until real runtime evidence exists:

- P10 Supabase/HF/Drive runtime gates:
  - Supabase CLI/Deno/Edge Function runtime/deploy evidence still pending.
  - Supabase Auth runtime with real users still pending.
  - Google Drive OAuth/appDataFolder runtime smoke still pending.
  - Multi-device restore smoke still pending.
- P11 release gates:
  - Release APK ZIP validity is OK, but release APKs are unsigned.
  - Signed production APK/AAB requires production keystore/GitHub secrets.
  - Public HTTPS domain, release signing fingerprint, privacy/support/terms URLs and dashboard evidence are pending.
- P11.T08 live AI gate:
  - Need real chapter translation smoke with actual provider/model.
  - Need confirm OpenCode Free/MiMo models return stable non-empty output through the new pipeline.
- P08 live Agent/tool-call gate:
  - User should retry the chatbot prompt that previously failed on `create_vbook_plugin.draft`.
  - If it still fails, capture chat logcat and provider response trace around tool call.

## Next Recommended Work

1. Smoke test live Agent tool calls in app:
   - Retry creating a VBook plugin draft from chat.
   - Confirm the app shows proposal/approval instead of stream interruption.
2. Smoke test AI provider routing:
   - Check OpenCode Free and MiMo free models with a small prompt.
   - Confirm status is not `EMPTY_OUTPUT`, `AUTHENTICATION`, or `UNKNOWN`.
3. Smoke test translation/export with real user data:
   - Finalize one translated chapter in reader.
   - Export `translation` only and confirm the file appears in the selected folder.
4. Continue P10 runtime gates when tools/credentials are available:
   - Supabase CLI/Deno/Edge Function.
   - Supabase Auth.
   - Google Drive appDataFolder backup/restore.
5. Continue P11 release gates after external inputs are available:
   - Signing secrets.
   - Public domain and metadata URLs.
   - Asset links fingerprint.

## Safety Notes

- Do not print or store provider/HF/Supabase tokens in logs or docs.
- Keep compatibility with Legado and VBook ext/plugin ecosystem as a release requirement.
- Keep the new app installable side-by-side with the old app.
- Prefer compatibility-layer fixes over per-source patches when fixing VBook/Legado source issues.
