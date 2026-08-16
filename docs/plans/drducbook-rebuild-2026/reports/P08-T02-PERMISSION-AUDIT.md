# P08.T02 - Permission broker va audit log

## Muc tieu

Bao dam mutation tool va du lieu nhay cam cua Agent luon di qua domain permission, co audit log ben vung, co redaction o ca use case va repository truoc khi luu DB.

## Ket qua trien khai

- Mo rong `AgentPermissionBroker`:
  - Capability levels: `READ`, `WRITE`, `NETWORK`, `FILE`, `SOURCE`, `AUTHORING`.
  - Approval scope: `ONE_TIME`, `SESSION`, `ALWAYS`.
  - Reusable grant co the revoke theo `conversationId` va/hoac `toolName`.
  - Default deny: broker moi sau process restart khong co reusable grant nao.
  - Proposal preview duoc sanitize ngay khi tao proposal, khong giu raw cookie/token trong preview.
- Them audit domain model:
  - `AgentAuditRecord` gom request/result/error, risk, capabilities, scope, status, started/finished/duration.
  - `AgentAuditStatus`: `APPROVED`, `DENIED`, `FAILED`, `REJECTED`.
- Them Room storage:
  - Entity `AiAgentAudit`, table `ai_agent_audits`.
  - DAO `observeRecentAudits()` va `insertAudit()`.
  - `AiAgentRepository.saveAudit()` sanitize lan cuoi truoc khi luu.
  - DB version tang `108 -> 109`, co schema `app/schemas/io.legado.app.data.AppDatabase/109.json` va migration `108_109`.
- Approval UI bridge:
  - Chat pending tool card co scope chips: once/session/always.
  - Mac dinh van la `ONE_TIME`.
  - Scope duoc day qua `AiChatIntent.SelectToolApprovalScope`, `AiChatViewModel`, `AiChatGenerationUseCase`, den `ExecuteApprovedAgentActionUseCase`.
  - `AiToolGateway.execute()` nhan `conversationId` de session/always grant duoc ap dung dung scope khi Agent chay tool o vong sau.
- Agent Dashboard:
  - Quan sat audit gan day qua `AiAgentGateway.observeRecentAudits()`.
  - Hien tong so audit va cac record gan day voi tool/status/risk/duration/error da redacted.

## Permission matrix

| Nhom | Vi du tool | Capability | Yeu cau duyet |
|---|---|---|---|
| Read/local | `search_books`, `get_chapter_content`, `recall_memory` | `READ` | Khong |
| Network read | `search_internet`, `fetch_internet_page`, `search_online_books` | `READ`, `NETWORK`, `SOURCE` tuy tool | Khong neu read-only |
| Write local | `save_memory`, `save_ai_artifact`, `save_dictionary_entry` | `READ`, `WRITE`, co the `FILE` | Co |
| Source mutation | `add_book_to_bookshelf`, `update_book`, `download_book_chapters` | `READ`, `WRITE`, `SOURCE`, co the `NETWORK`/`FILE` | Co |
| Authoring mutation | `save_authoring_project`, `delete_authoring_project` | `READ`, `WRITE`, `AUTHORING`, `FILE` | Co |
| Plugin/skill lifecycle | `install_vbook_plugin`, `activate_agent_skill_version`, `rollback_agent_skill` | `READ`, `WRITE`, `SOURCE`/plugin risk | Co |

## Attack va regression coverage

- UI/tool bypass khong co approval van bi `AgentPermissionBroker.requireCanExecute()` chan mutation.
- Changed arguments sau approval bi tu choi, khong tang mutation count, audit `DENIED`.
- Reuse one-time approval bi tu choi.
- Session grant co the revoke.
- Session grant bo qua confirmation va execute duoc trong dung conversation, khong lan sang conversation khac.
- Always grant cho phep goi lai sau approval, nhung revoke xong thi bi chan.
- Process restart tao broker moi khong co reusable grant cu.
- User reject proposal ghi audit `REJECTED`.
- Repository sanitize request/result/error lan cuoi truoc khi luu Room.
- Migration 108 -> 109 tao table/index audit va foreign key check pass.

## File thay doi chinh

- `app/src/main/java/io/legado/app/domain/agent/AgentModels.kt`
- `app/src/main/java/io/legado/app/domain/agent/AgentPermissionBroker.kt`
- `app/src/main/java/io/legado/app/domain/gateway/AiAgentGateway.kt`
- `app/src/main/java/io/legado/app/domain/usecase/ExecuteApprovedAgentActionUseCase.kt`
- `app/src/main/java/io/legado/app/domain/usecase/AiChatGenerationUseCase.kt`
- `app/src/main/java/io/legado/app/data/entities/AiAgentAudit.kt`
- `app/src/main/java/io/legado/app/data/dao/AiAgentDao.kt`
- `app/src/main/java/io/legado/app/data/repository/AiAgentRepository.kt`
- `app/src/main/java/io/legado/app/data/AppDatabase.kt`
- `app/src/main/java/io/legado/app/data/DatabaseMigrations.kt`
- `app/src/main/java/io/legado/app/ui/ai/chat/AiChatContract.kt`
- `app/src/main/java/io/legado/app/ui/ai/chat/AiChatViewModel.kt`
- `app/src/main/java/io/legado/app/ui/ai/chat/AiChatScreen.kt`
- `app/src/main/java/io/legado/app/ui/ai/agent/AgentDashboardContract.kt`
- `app/src/main/java/io/legado/app/ui/ai/agent/AgentDashboardViewModel.kt`
- `app/src/main/java/io/legado/app/ui/ai/agent/AgentDashboardScreen.kt`
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values-vi/strings.xml`
- `app/schemas/io.legado.app.data.AppDatabase/109.json`
- Agent permission/audit tests va migration test.

## Lenh kiem tra

```text
.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.domain.agent.AgentPermissionBrokerTest" --tests "io.legado.app.domain.agent.AgentAuditSanitizerTest" --tests "io.legado.app.domain.usecase.ExecuteApprovedAgentActionUseCaseTest" --tests "io.legado.app.domain.usecase.AiChatGenerationUseCaseTest" --tests "io.legado.app.data.repository.AiAgentRepositoryAuditTest" --tests "io.legado.app.ui.ai.agent.AgentDashboardStateMapperTest" --no-daemon --console=plain

.\gradlew.bat :app:connectedAppDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=io.legado.app.AgentAuditMigrationTest" --no-daemon --console=plain

.\gradlew.bat :app:compileAppDebugKotlin --no-daemon --console=plain
```

## Ket qua

- Unit focused: 28 tests PASS, failures/errors/skipped = 0.
  - `AgentPermissionBrokerTest`: 13 tests.
  - `AgentAuditSanitizerTest`: 1 test.
  - `ExecuteApprovedAgentActionUseCaseTest`: 4 tests.
  - `AiChatGenerationUseCaseTest`: 6 tests.
  - `AiAgentRepositoryAuditTest`: 1 test.
  - `AgentDashboardStateMapperTest`: 3 tests.
- Instrumented migration: `AgentAuditMigrationTest`: 1 test PASS tren `emulator-5554 - 14`.
- Kotlin compile: `:app:compileAppDebugKotlin` BUILD SUCCESSFUL.

## Bang chung

- `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.domain.agent.AgentPermissionBrokerTest.xml`
- `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.domain.agent.AgentAuditSanitizerTest.xml`
- `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.domain.usecase.ExecuteApprovedAgentActionUseCaseTest.xml`
- `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.domain.usecase.AiChatGenerationUseCaseTest.xml`
- `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.data.repository.AiAgentRepositoryAuditTest.xml`
- `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.ui.ai.agent.AgentDashboardStateMapperTest.xml`
- `app/build/outputs/androidTest-results/connected/debug/flavors/app/TEST-emulator-5554 - 14-_app-app.xml`
- `app/schemas/io.legado.app.data.AppDatabase/109.json`

## Rui ro va viec con lai

- `SESSION` grant hien duoc luu trong broker memory va co API revoke; flow runtime chinh van dung `ONE_TIME` mac dinh. UI co the chon scope, nhung grant khong persist qua process restart theo default-deny.
- `ALWAYS` grant cung chi song trong process hien tai o P08.T02; policy luu ben vung neu can se phai them UI/persistence rieng va audit them o P08.T04/P08.T06.
- Custom JS tool manifest/runtime chua co, thuoc P08.T03.
- Lifecycle draft/validate/test/approve/enable UI cho custom tool thuoc P08.T04.
