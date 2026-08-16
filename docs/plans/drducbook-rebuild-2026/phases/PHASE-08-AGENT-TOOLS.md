# Phase 08 - Agent va he thong cong cu tu tao

## Muc tieu phase

Kiem ke/hoan thien built-in tools va cho Agent tao cong cu JavaScript an toan theo lifecycle co phe duyet, audit va test.

## Pham vi file chinh

- `data/repository/AiToolRepository.kt`, Agent contracts/ViewModels/screens
- tool/skill/plugin domain models, permission broker, audit storage
- `domain/agenttools/**`, `data/agenttools/**`, `ui/agent/tools/**` `[NEW/MODIFY]`
- Rhino sandbox/class shutter, VBook plugin importer/validator
- Agent security/unit/integration tests

## Task chi tiet

### P08.T01 - Kiem ke va khoa Agent tool contracts

**Muc tieu:** Moi built-in tool co ID, input/output schema, permission, owner va test.

**Pham vi file:** `AiToolRepository.kt`, tool registry/contracts, Agent tool tests va inventory report.

**Thuc hien:** Inventory 41 tools; phat hien duplicate/dead/placeholder; chuan hoa registry va structured errors; khong doi ID cong khai neu khong co adapter.

**Dieu kien thong qua:** 100% tool registry co schema/permission/test status; unknown args bi reject; contract snapshot pass.

**Log:** Lien ket inventory va diff tool IDs/capabilities.

### P08.T02 - Permission broker va audit log

**Muc tieu:** Mutation va du lieu nhay cam luon qua domain permission.

**Pham vi file:** Permission broker/domain models/repository, approval UI bridge, audit entities/DAO va security tests.

**Thuc hien:** Capability levels read/write/network/file/source/authoring; one-time/session/always approval; audit request/result/duration/redacted error; revoke va deny defaults.

**Dieu kien thong qua:** UI bypass khong the chay mutation; deny/revoke/process restart pass; audit khong chua cookie/token/content nhay cam.

**Log:** Ghi permission matrix, attack tests va audit redaction scan.

### P08.T03 - Custom JS tool manifest va sandbox runtime

**Muc tieu:** Agent tao duoc tool co schema ma khong co raw Android/Java access.

**Pham vi file:** Custom tool manifest/parser/validator, Rhino sandbox/class shutter, capability bridge/runtime va threat tests.

**Thuc hien:** `CustomAgentToolManifest` ID/version/description/input JSON Schema/capabilities/script; parser/validator; timeout, memory/output/network limits; capability bridge allow-list.

**Dieu kien thong qua:** Infinite loop/OOM/path traversal/process/Java reflection/secret access bi chan; valid tool deterministic; invalid schema/script co line error.

**Log:** Ghi sandbox limits, threat tests va sample tool fixtures.

### P08.T04 - Lifecycle UI Draft -> Enable

**Muc tieu:** Nguoi dung kiem soat moi tool tu tao.

**Pham vi file:** Agent tool lifecycle Contract/ViewModel/Screen, draft storage/versioning, DI/navigation va UI tests.

**Thuc hien:** Draft editor, schema/script validation, fixture test runner, diff/version, permission review, approve/enable/disable/rollback/delete; Agent chi tao/sua draft.

**Dieu kien thong qua:** Khong duong code nao auto-enable; unsaved/process recreation pass; enabled registry update atomic; rollback phuc hoi ban truoc.

**Log:** Screenshot lifecycle va ViewModel/integration tests.

### P08.T05 - Skill/VBook compatibility va lifecycle

**Muc tieu:** Giu skill/VBook draft/install hien co va tach ro khoi custom Agent tool.

**Pham vi file:** Skill lifecycle, VBook importer/inspector/validators, migration adapters va compatibility tests.

**Thuc hien:** Reuse validators/importer; lifecycle state/version/source provenance; compatibility fixtures; khong cho custom tool gia mao VBook/legacy API.

**Dieu kien thong qua:** VBook public fixtures import/run; path/URL/ZIP attacks bi chan; old drafts co migration/fallback.

**Log:** Ghi compatibility matrix va validator tests.

### P08.T06 - Agent security va regression tests

**Muc tieu:** Dong gate toan bo Agent/tool system.

**Pham vi file:** Agent/tool unit, integration, security, backup va minified-release tests/reports.

**Dieu kien thong qua:** Built-in tool tests, custom lifecycle, permission denial, cancellation, timeout, audit, backup/restore, malicious scripts va minified release pass.

**Log:** Security matrix, reports va remaining risks.

## Gate dong phase

- Tool inventory 100% co status/test.
- Custom tool khong auto-enable, khong doc cookie/secret.
- Audit va permission tests pass release build.
