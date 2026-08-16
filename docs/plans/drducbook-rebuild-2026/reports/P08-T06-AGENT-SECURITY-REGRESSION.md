# P08.T06 - Agent security va regression tests

## Muc tieu

Dong gate cho toan bo Agent/tool system sau P08.T01-P08.T05: built-in tools, custom lifecycle, permission denial, cancellation, timeout, audit redaction, backup/restore/lifecycle recreation, malicious scripts, compatibility va minified release.

## Pham vi da xu ly

| Hang muc | Ket qua |
|---|---|
| Built-in tool contract | 41 built-in tool IDs duoc khoa bang snapshot; default safety gate chi mo 20 tool read/safe khi mutation/skill/plugin flags tat. |
| Permission denial/revoke | Broker tiep tuc default-deny mutation neu khong co approval; disabled feature policy chan tool truoc khi tao proposal/chay. |
| Cancellation/timeout/loop | Agent loop guard, run use case va custom JS runtime co regression tests cho round limit, timeout va cancellation. |
| Audit redaction | `AgentAuditSanitizer` redact JSON secret fields, Bearer/Token, query-style `token`, `cookie`, `password`, `secret`; repository sanitize run/proposal/trace/audit truoc khi persist. |
| Custom tool backup/restore lifecycle | Repository recreation giu active approved/enabled version, khong auto-enable latest draft moi. |
| Malicious scripts | Custom manifest/runtime reject Java/Android/process/reflection/path/secret va gia mao VBook/Legado/VBook plugin APIs. |
| VBook/Legado compatibility | Compatibility corpus va VBook importer/identity security tests pass cung P08 gate. |
| Minified release | `:app:assembleAppRelease` pass voi R8/resource shrink/lintVital; tao 4 APK unsigned theo ABI/universal cho `applicationId=com.drducbook.app`. |

## File tac dong

- `app/src/main/java/io/legado/app/domain/agent/AgentAuditSanitizer.kt`
- `app/src/main/java/io/legado/app/data/repository/AiAgentRepository.kt`
- `app/src/main/java/io/legado/app/domain/agenttools/CustomAgentToolManifestParser.kt`
- `app/src/main/java/io/legado/app/data/repository/AiSkillRepository.kt`
- `app/src/test/java/io/legado/app/domain/agent/AgentAuditSanitizerTest.kt`
- `app/src/test/java/io/legado/app/data/repository/AiAgentRepositoryAuditTest.kt`
- `app/src/test/java/io/legado/app/data/repository/CustomAgentToolRepositoryTest.kt`
- `app/src/test/java/io/legado/app/domain/agenttools/CustomAgentToolManifestRuntimeTest.kt`
- `app/src/test/java/io/legado/app/compat/CompatibilityCorpusTest.kt`
- `app/src/test/java/io/legado/app/help/vbook/VbookPluginImporterSecurityTest.kt`
- `app/src/test/java/io/legado/app/data/repository/vbook/VbookImportRepositoryTest.kt`

## Security matrix

| Risk | Evidence |
|---|---|
| Agent chi thay tool safe khi flags tat | `AiToolRepositoryToolCatalogTest.defaultSafetyGatesExplainTwentyEnabledToolsInAppDashboard`: 41 registered, 20 enabled. |
| Unknown/malformed args | `AiToolRepositoryToolCatalogTest.builtInToolArgumentValidatorRejectsUnknownAndMalformedArgs`. |
| Mutation bypass | `AgentPermissionBrokerTest`, `AgentPermissionSecurityTest`, `ExecuteApprovedAgentActionUseCaseTest`. |
| Audit/proposal secret leak | `AgentAuditSanitizerTest.redactsCommonSecretShapes`; `AiAgentRepositoryAuditTest.saveRunAndProposalRedactSensitivePayloadsBeforePersisting`. |
| Custom tool lifecycle restore | `CustomAgentToolRepositoryTest.repositoryRecreationRestoresApprovedStateWithoutAutoEnablingNewDraft`. |
| Malicious custom JS | `CustomAgentToolManifestRuntimeTest` threat cases, including anti-impersonation for VBook/legacy API. |
| Internet/private network abuse | `AiToolRepositoryInternetPageTest`, plugin draft URL/path validator tests. |
| Agent loop/cancellation/timeout | `RunAiAgentUseCaseTest`, `AgentToolLoopGuardTest`, `CustomAgentToolManifestRuntimeTest`. |
| VBook import/install attack | `VbookPluginImporterSecurityTest`, `VbookImportRepositoryTest`. |

## Lenh kiem tra

```powershell
.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.data.repository.AiToolRepositoryToolCatalogTest" --tests "io.legado.app.data.repository.AiToolRepositoryInternetPageTest" --tests "io.legado.app.data.repository.AiToolRepositoryPluginDraftValidatorTest" --tests "io.legado.app.domain.agent.AgentPermissionBrokerTest" --tests "io.legado.app.domain.agent.AgentAuditSanitizerTest" --tests "io.legado.app.domain.agent.AgentToolLoopGuardTest" --tests "io.legado.app.domain.agent.AgentSkillValidatorTest" --tests "io.legado.app.domain.agenttools.CustomAgentToolManifestRuntimeTest" --tests "io.legado.app.data.repository.CustomAgentToolRepositoryTest" --tests "io.legado.app.data.repository.AiAgentRepositoryAuditTest" --tests "io.legado.app.domain.usecase.ExecuteApprovedAgentActionUseCaseTest" --tests "io.legado.app.domain.usecase.RunAiAgentUseCaseTest" --tests "io.legado.app.security.AgentPermissionSecurityTest" --tests "io.legado.app.ui.ai.agent.AgentDashboardStateMapperTest" --tests "io.legado.app.data.repository.AiSkillRepositoryTest" --tests "io.legado.app.compat.CompatibilityCorpusTest" --tests "io.legado.app.help.vbook.VbookPluginImporterSecurityTest" --tests "io.legado.app.data.repository.vbook.VbookImportRepositoryTest" --no-daemon --console=plain
.\gradlew.bat :app:assembleAppRelease --no-daemon --console=plain
```

## Ket qua

- Focused P08.T06 JVM matrix: 81 tests PASS, 0 failures, 0 errors, 0 skipped.
- XML evidence: `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.*.xml` timestamp `2026-07-31 00:18`.
- Minified release: BUILD SUCCESSFUL in 14m 56s; R8 `minifyAppReleaseWithR8`, `lintVitalAppRelease`, resource shrink/optimize va package release pass.
- APK evidence:
  - `app/build/outputs/apk/app/release/app-app-universal-release-unsigned.apk`
  - `app/build/outputs/apk/app/release/app-app-armeabi-v7a-release-unsigned.apk`
  - `app/build/outputs/apk/app/release/app-app-x86_64-release-unsigned.apk`
  - `app/build/outputs/apk/app/release/app-app-arm64-v8a-release-unsigned.apk`
- Release metadata: `applicationId=com.drducbook.app`, `versionCode=32640`, `versionName=3.26.13`.
- R8 mapping evidence: `app/build/outputs/mapping/appRelease/mapping.txt`, `usage.txt`, `seeds.txt`, `configuration.txt`.

## Rui ro con lai

- Custom Agent tool v1 van gioi han `READ`/`NETWORK`; mutation/file/source/authoring phai qua built-in tools va permission broker.
- Connected device regression rieng cho P08.T06 chua duoc chay lai trong task nay; phase gate hien dua vao focused JVM security suite va minified release build.
- Cac feature flags Agent mutation/skill/plugin mac dinh tat de giu 20 tool safe; can UI ro rang hon trong phase sau neu muon nguoi dung tu bat nhom nang cao.
