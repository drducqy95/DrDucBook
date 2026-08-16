# P08.T04 - Custom Agent Tool Lifecycle UI

## Muc tieu

Nguoi dung kiem soat vong doi custom Agent tool tu `Draft -> Validate -> Run Fixture -> Approve -> Enable`, khong co duong nao auto-enable tool do Agent tao.

## Ket qua

- Them lifecycle storage cho custom tool:
  - `ai_custom_tools`
  - `ai_custom_tool_versions`
  - DB version `110`
  - migration `109 -> 110`
- Them gateway/repository rieng cho custom tool, tach khoi `ai_skills` va VBook plugin.
- Noi custom tool vao Agent registry theo hai tang:
  - `registeredTools()`: built-in tools + custom tool da co active approved version.
  - `availableTools()`: built-in tools qua safety gate + custom tool da enabled.
- Them UI Compose quan ly custom tool:
  - Tao/sua draft manifest JSON.
  - Luu fixture input JSON.
  - Validate latest draft.
  - Run fixture.
  - Approve latest version khi validation valid va fixture pass.
  - Enable/disable approved active version.
  - Rollback ve version approved truoc do.
  - Delete tool va toan bo version.
- Them route `MainRouteAiCustomTools` va link tu Agent dashboard.
- Them strings tieng Anh va tieng Viet cho toan bo lifecycle UI.
- Cap nhat migration golden fixture sang DB `110`.

## File tac dong

- `app/src/main/java/io/legado/app/domain/agenttools/CustomAgentToolModels.kt`
- `app/src/main/java/io/legado/app/domain/gateway/CustomAgentToolGateway.kt`
- `app/src/main/java/io/legado/app/data/entities/AiCustomTool.kt`
- `app/src/main/java/io/legado/app/data/entities/AiCustomToolVersion.kt`
- `app/src/main/java/io/legado/app/data/dao/AiCustomToolDao.kt`
- `app/src/main/java/io/legado/app/data/repository/CustomAgentToolRepository.kt`
- `app/src/main/java/io/legado/app/data/repository/AiToolRepository.kt`
- `app/src/main/java/io/legado/app/data/AppDatabase.kt`
- `app/src/main/java/io/legado/app/data/DatabaseMigrations.kt`
- `app/schemas/io.legado.app.data.AppDatabase/110.json`
- `app/src/main/java/io/legado/app/ui/ai/agent/AgentDashboardScreen.kt`
- `app/src/main/java/io/legado/app/ui/ai/agent/tools/CustomAgentToolManagerContract.kt`
- `app/src/main/java/io/legado/app/ui/ai/agent/tools/CustomAgentToolManagerViewModel.kt`
- `app/src/main/java/io/legado/app/ui/ai/agent/tools/CustomAgentToolManagerScreen.kt`
- `app/src/main/java/io/legado/app/ui/main/MainNavKey.kt`
- `app/src/main/java/io/legado/app/ui/main/MainNavGraph.kt`
- `app/src/main/java/io/legado/app/ui/main/MainNavigator.kt`
- `app/src/main/java/io/legado/app/di/appModule.kt`
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values-vi/strings.xml`
- `app/src/test/java/io/legado/app/data/repository/CustomAgentToolRepositoryTest.kt`
- `app/src/androidTest/java/io/legado/app/CustomAgentToolMigrationTest.kt`
- `app/src/androidTest/java/io/legado/app/MigrationTest.kt`
- `app/src/androidTest/assets/test_db_migration_fixture.json`

## Dieu kien thong qua

- Khong auto-enable:
  - `createDraft()` luu tool disabled va `activeVersionId = null`.
  - `approveLatestVersion()` chi set active version sau khi latest version `VALIDATED`, validation `VALID`, fixture `PASS`.
  - `setEnabled()` yeu cau active version da approved.
- Enabled registry atomic:
  - `AiToolRepository.availableTools()` chi them custom definitions tu `availableToolDefinitions()`.
  - `CustomAgentToolRepository.execute()` tra loi disabled/no-active-version thay vi chay tool khi chua enable.
- Rollback:
  - `rollback()` chi chon version cu hon co `valid && approved`.
- Process recreation/unsaved draft:
  - `CustomAgentToolManagerViewModel` luu editor visible, selected tool id, manifest JSON va fixture JSON bang `SavedStateHandle`.
- Migration:
  - `compileAppDebugAndroidTestKotlin` pass voi `CustomAgentToolMigrationTest`.

## Kiem tra

```powershell
.\gradlew.bat :app:clean --no-daemon --console=plain
.\gradlew.bat :app:compileAppDebugKotlin --no-daemon --console=plain
.\gradlew.bat :app:compileAppDebugAndroidTestKotlin --no-daemon --console=plain
.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.data.repository.CustomAgentToolRepositoryTest" --tests "io.legado.app.domain.agenttools.CustomAgentToolManifestRuntimeTest" --tests "io.legado.app.data.repository.AiToolRepositoryToolCatalogTest" --tests "io.legado.app.domain.agent.AgentPermissionBrokerTest" --tests "io.legado.app.security.AgentPermissionSecurityTest" --tests "io.legado.app.ui.ai.agent.AgentDashboardStateMapperTest" --no-daemon --console=plain
```

Ket qua:

- `:app:clean`: PASS.
- `:app:compileAppDebugKotlin`: PASS.
- `:app:compileAppDebugAndroidTestKotlin`: PASS, co warning cu ve deprecated `MigrationTestHelper`.
- Focused JVM tests: PASS, 38 tests, 0 failures/errors/skipped.

Focused JVM XML:

- `TEST-io.legado.app.data.repository.CustomAgentToolRepositoryTest.xml`: 4 tests.
- `TEST-io.legado.app.domain.agenttools.CustomAgentToolManifestRuntimeTest.xml`: 7 tests.
- `TEST-io.legado.app.data.repository.AiToolRepositoryToolCatalogTest.xml`: 7 tests.
- `TEST-io.legado.app.domain.agent.AgentPermissionBrokerTest.xml`: 13 tests.
- `TEST-io.legado.app.security.AgentPermissionSecurityTest.xml`: 4 tests.
- `TEST-io.legado.app.ui.ai.agent.AgentDashboardStateMapperTest.xml`: 3 tests.

## Chua hoan tat / rui ro

- Connected instrumentation command cho `CustomAgentToolMigrationTest` da duoc thu tren `emulator-5554`, nhung bi timeout va khong tao report. Bang chung hien tai la Android test compile pass, chua co device execution report cho test migration moi.
- Custom JS v1 van chi mo capability `READ` va `NETWORK`; mutation/file/source/authoring tiep tuc di qua built-in tools + permission broker.
- P08.T05 van can kiem tra sau hon tuong thich Skill/VBook lifecycle va public fixtures.
- P08.T06 van can regression/security gate rong hon, gom connected/release/minified neu can.
