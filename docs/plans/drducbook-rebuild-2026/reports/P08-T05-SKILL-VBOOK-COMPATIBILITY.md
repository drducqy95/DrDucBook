# P08.T05 - Skill/VBook compatibility va lifecycle

## Muc tieu

Giu Agent skill va VBook plugin draft/install la hai lifecycle rieng, khong de custom Agent tool gia mao VBook/legacy source API, va khoa lai compatibility bang fixture/test co provenance.

## Pham vi da xu ly

| Hang muc | Ket qua |
|---|---|
| Agent skill lifecycle | Skill draft van disabled; version moi khong thay active version cho den khi user activate/enable. |
| Skill source provenance | Manifest versioned cua skill moi co `provenance` va `lifecycle` metadata, khong can tang Room schema. |
| Legacy skill fallback | Draft/version cu thieu provenance van enable duoc neu `manifest.json` va `SKILL.md` day du, version valid. |
| VBook public fixtures | Corpus text/comic/audio/video import qua `VbookPluginImporter` va execute bang production `VbookExecutor`; TTS/translator bi giu la service plugin, khong thanh BookSource. |
| VBook registry/install safety | Registry van reject URL khong hop le; install chan archive identity mismatch voi preview. |
| VBook ZIP attack | Importer reject ZIP entry thoat khoi plugin directory. |
| Custom Agent tool boundary | Custom tool manifest/script reject dau hieu `vbook://`, `legado://`, `yuedu://`, `plugin.json`, `vbook_plugins`, va runtime class VBook noi bo. |

## File tac dong

- `app/src/main/java/io/legado/app/data/repository/AiSkillRepository.kt`
- `app/src/main/java/io/legado/app/domain/agenttools/CustomAgentToolManifestParser.kt`
- `app/src/test/java/io/legado/app/data/repository/AiSkillRepositoryTest.kt`
- `app/src/test/java/io/legado/app/domain/agenttools/CustomAgentToolManifestRuntimeTest.kt`
- `app/src/test/java/io/legado/app/compat/CompatibilityCorpusTest.kt`
- `app/src/test/java/io/legado/app/data/repository/vbook/VbookImportRepositoryTest.kt`
- `app/src/test/java/io/legado/app/help/vbook/VbookPluginImporterSecurityTest.kt`

## Compatibility matrix

| Surface | Fixture/test | Gate |
|---|---|---|
| Legado Book/RSS/TTS JSON/JS | `CompatibilityCorpusTest.legadoSourceFixturesParseWithoutLosingJavascriptFields` | Legacy JS fields/cookie/login rules khong bi mat. |
| VBook registry | `CompatibilityCorpusTest.vbookRegistryContainsEverySupportedCompatibilityKind` | 6 plugin kind co stable unique ID. |
| VBook runtime | `CompatibilityCorpusTest.vbookFixturesInspectAndExecuteInsideTheProductionRuntime` | 6 plugin kinds inspect/execute bang runtime production. |
| VBook importer | `CompatibilityCorpusTest.vbookPublicFixturesImportThroughImporterAndRun` | Book source fixtures import/run; service plugins bi reject. |
| VBook ZIP safety | `VbookPluginImporterSecurityTest.importRejectsZipEntriesOutsidePluginDirectory` | Path traversal trong ZIP bi chan. |
| VBook registry install | `VbookImportRepositoryTest.installRejectsDownloadedPluginIdentityMismatch` | Downloaded plugin phai match registry preview identity. |
| Agent skill lifecycle | `AiSkillRepositoryTest.draftIsDisabledAndNewVersionDoesNotReplaceActiveUntilApproved` | Draft/version/rollback dung lifecycle. |
| Old skill fallback | `AiSkillRepositoryTest.legacySkillVersionWithoutProvenanceStillEnablesWhenFilesAreComplete` | Draft cu khong bi break khi them provenance moi. |
| Custom tool boundary | `CustomAgentToolManifestRuntimeTest.customToolCannotPretendToBeVbookOrLegacyApi` | Custom JS khong gia mao VBook/legacy source contract. |

## Lenh kiem tra

```powershell
.\gradlew.bat :app:compileAppDebugKotlin --no-daemon --console=plain
.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.compat.CompatibilityCorpusTest" --tests "io.legado.app.help.vbook.VbookPluginInspectorTest" --tests "io.legado.app.help.vbook.VbookPluginImporterTest" --tests "io.legado.app.help.vbook.VbookPluginImporterSecurityTest" --tests "io.legado.app.data.repository.vbook.VbookImportRepositoryTest" --tests "io.legado.app.data.repository.vbook.VbookRegistryParserTest" --tests "io.legado.app.data.repository.AiToolRepositoryPluginDraftValidatorTest" --tests "io.legado.app.data.repository.AiSkillRepositoryTest" --tests "io.legado.app.domain.agent.AgentSkillValidatorTest" --tests "io.legado.app.domain.agenttools.CustomAgentToolManifestRuntimeTest" --no-daemon --console=plain
```

## Ket qua

- `:app:compileAppDebugKotlin`: BUILD SUCCESSFUL.
- Focused JVM tests: 40 tests PASS, 0 failures, 0 errors, 0 skipped.
- XML evidence: `app/build/test-results/testAppDebugUnitTest/TEST-*.xml`.

## Rui ro con lai

- P08.T06 van can dong security/regression gate rong hon: permission denial, cancellation, timeout, audit, backup/restore, malicious scripts va minified release.
- Custom JS v1 tiep tuc chi mo `READ`/`NETWORK`; mutation/file/source/authoring van phai di qua built-in tools va permission broker.
