# P08.T01 - Agent tool contracts

## Muc tieu

Khoa registry built-in Agent tools truoc khi them permission broker/audit/custom JS tools: moi tool co ID on dinh, schema object, permission classification va regression test.

## Ket luan inventory

- Built-in registry: 41 tool IDs trong `AiToolRepository.toolDefinitions`.
- Safety-gated available tools mac dinh trong app: 20 tool.
- Nguyen nhan UI tung bao 20 tool: `AiToolRepository.availableTools()` loc qua `AgentPermissionBroker.isToolEnabled`; cac flag mac dinh `featureAgentMutation=false`, `featureAgentSkill=false`, `featureAgentPlugin=false`, nen mutation tools va skill/plugin tools bi an khoi runtime list.
- Dashboard da tach ro:
  - `registeredTools()` = toan bo 41 registry contracts.
  - `availableTools()` = tool thuc su gui cho model trong cau hinh hien tai.
  - UI hien `registered`, `enabled`, `read-only`, `require approval`.

## Tool IDs da khoa

```text
activate_agent_skill_version
add_book_to_bookshelf
clear_book_dictionary
create_agent_skill_draft
create_vbook_plugin_draft
delete_authoring_project
delete_book_dictionary_term
delete_dictionary_entry
delete_memory
download_book_chapters
fetch_internet_page
get_ai_artifacts
get_authoring_project
get_book_detail
get_bookshelf_automation
get_chapter_content
get_chapter_window
get_download_status
get_reading_stats
install_vbook_plugin
list_agent_skills
list_authoring_projects
list_book_chapters
list_book_dictionary_terms
list_dictionary_entries
recall_memory
rollback_agent_skill
save_ai_artifact
save_authoring_project
save_book_dictionary_term
save_dictionary_entry
save_memory
search_book_sources
search_bookmarks
search_books
search_chapter_content
search_internet
search_online_books
set_agent_skill_enabled
set_bookshelf_automation
update_book
```

## Permission classification

- Read/default tools: tools khong nam trong mutation risk map, gom search/read/list/get/recall.
- WRITE: `save_ai_artifact`, `save_memory`, `update_book`, `download_book_chapters`, `add_book_to_bookshelf`, `create_vbook_plugin_draft`, `create_agent_skill_draft`, `save_book_dictionary_term`, `save_dictionary_entry`, `save_authoring_project`, `set_bookshelf_automation`.
- DELETE: `delete_memory`, `delete_book_dictionary_term`, `clear_book_dictionary`, `delete_dictionary_entry`, `delete_authoring_project`.
- PLUGIN_INSTALL: `install_vbook_plugin`, `set_agent_skill_enabled`, `activate_agent_skill_version`, `rollback_agent_skill`.

## Thay doi trien khai

- Them `AiToolGateway.registeredTools()` de UI/diagnostic doc day du registry, khong lam thay doi runtime tool exposure.
- `AiToolRepository.execute()` tra structured unknown-tool error truoc khi dispatch.
- Them `AiToolRepository.validateToolArguments()`:
  - parse args thanh JSON object.
  - yeu cau schema root la object.
  - reject unknown args khi schema `additionalProperties=false`.
- Dashboard Agent dung `registeredTools()` cho tong registry va `availableTools()` cho so dang bat.
- Cap nhat chuoi UI: `enabled / read-only / require approval`.

## Lenh kiem tra

```text
.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.data.repository.AiToolRepositoryToolCatalogTest" --tests "io.legado.app.ui.ai.agent.AgentDashboardStateMapperTest" --tests "io.legado.app.domain.agent.AgentPermissionBrokerTest" --tests "io.legado.app.security.AgentPermissionSecurityTest" --no-daemon --console=plain
```

## Ket qua

- BUILD SUCCESSFUL.
- `AiToolRepositoryToolCatalogTest`: 7 tests PASS.
- `AgentDashboardStateMapperTest`: 3 tests PASS.
- `AgentPermissionBrokerTest`: 9 tests PASS.
- `AgentPermissionSecurityTest`: 4 tests PASS.
- Tong: 23 tests PASS; failures/errors/skipped = 0.

## Bang chung

- `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.data.repository.AiToolRepositoryToolCatalogTest.xml`
- `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.ui.ai.agent.AgentDashboardStateMapperTest.xml`
- `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.domain.agent.AgentPermissionBrokerTest.xml`
- `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.security.AgentPermissionSecurityTest.xml`

## Rui ro va viec con lai

- P08.T02 se thay feature flag thô bang permission broker/audit log day du.
- P08.T03-P08.T04 se them custom JS tool manifest/runtime va lifecycle UI; custom tool van khong duoc auto-enable.
