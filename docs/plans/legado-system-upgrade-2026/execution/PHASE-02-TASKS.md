# Phase 02 — AI Agent & Chat Bubble — Kế hoạch triển khai

> Cập nhật 26/07/2026: P02.T01–P02.T09 đã hoàn tất ở mức automated. P02.T10 được thay bằng hook tập trung `Application.ActivityLifecycleCallbacks` để tránh attach trùng. Gate thiết bị được ghi riêng trong evidence log.

## Kết quả triển khai

| Task | Kết quả | Artifact |
|---|---|---|
| P02.T01 | DONE | `domain/agent/AgentModels.kt` |
| P02.T02 | DONE | `AgentPermissionBroker.kt`, permission suite |
| P02.T03 | DONE | `RunAiAgentUseCase.kt`, loop/cancel/max-step tests |
| P02.T04 | DONE | `ExecuteApprovedAgentActionUseCase.kt`, success/mismatch tests |
| P02.T05 | DONE | `AiToolRepository.kt`, 41 registered tools, authoring project tools |
| P02.T06 | AUTOMATED_DONE / DEVICE_PARTIAL | `ChatBubblePanel.kt`, `ChatBubbleSessionStore.kt`, Nox bubble/panel/secret exclusion pass; đa Activity còn chờ |
| P02.T07 | DONE | DTO registry, redaction, secret editor suppression |
| P02.T08 | DONE | Skill repository/validator/version/rollback/default-disabled |
| P02.T09 | VERIFIED_DONE | Dashboard proposal audit, run trace, skill confirmation; Nox hiển thị đúng |
| P02.T10 | SUPERSEDED | App-level lifecycle hook covers View và Compose Activity without duplicate attach |

Spec gốc: [../PHASE-02-AI-AGENT-CHAT-BUBBLE.md](../PHASE-02-AI-AGENT-CHAT-BUBBLE.md)
Wave: **2** | Phụ thuộc: Phase 01
Ước lượng: 5–7 ngày

---

## 1. Mục tiêu

Xây dựng Chatbot thành AI Agent đầy đủ: vòng lặp orchestration với trace, permission broker
an toàn, tool registry đọc/ghi dữ liệu ứng dụng, memory dài hạn, skill/plugin validation,
dashboard quản trị, và bong bóng chat hoạt động trên toàn app mà không cần overlay hệ thống.

---

## 2. Trạng thái hiện tại

### Đã có

| Artifact | File | Size | Trạng thái |
|---|---|:---:|:---:|
| Agent Dashboard UI | `ui/ai/agent/AgentDashboard{Contract,ViewModel,Screen}.kt` | 3+12+20 KB | PARTIAL |
| Chat UI | `ui/ai/chat/AiChat{Contract,ViewModel,Screen}.kt` + parts | 8 files | PARTIAL |
| Bubble | `ui/ai/bubble/ChatBubble{Coordinator,Host,Position}.kt` | 3+8+3 KB | PARTIAL |
| Context bridge | `ui/ai/context/AiScreenContextRegistry.kt` + Activity | 2+2 KB | PARTIAL |
| Agent domain | `RunAiAgentUseCase.kt` (9.4 KB), `AiToolAwareGenerationUseCase.kt` (4.4 KB) | | PARTIAL |
| Agent entities | `AiAgentRun.kt`, `AiAgentTrace.kt`, `AiAgentProposal.kt` | ~2.3 KB total | DONE |
| Skill entities | `AiSkill.kt`, `AiSkillVersion.kt` | 1.8 KB | DONE |
| Memory | `AiMemory.kt` (2.2 KB) + FTS | | DONE |
| Gateways | `AiAgent/Tool/Memory/Skill/Chat Gateway.kt` | | DONE |
| Repositories | `AiChat/Tool/Memory Repository.kt` | | PARTIAL |

### Chưa có

| Artifact | File dự kiến | Ưu tiên |
|---|---|:---:|
| Permission broker | `domain/agent/AgentPermissionPolicy.kt` | **CRITICAL** |
| Agent models (domain) | `domain/agent/AgentModels.kt` | **CRITICAL** |
| Execute approved action | `domain/usecase/ExecuteApprovedAgentActionUseCase.kt` | HIGH |
| Chat bubble panel | `ui/ai/bubble/ChatBubblePanel.kt` | HIGH |
| Skill repository | `data/repository/AiSkillRepository.kt` | MEDIUM |
| Agent DAO | `data/dao/AiAgentDao.kt` | MEDIUM |
| Agent repository | `data/repository/AiAgentRepository.kt` | MEDIUM |

---

## 3. Phạm vi điều chỉnh

- **Infrastructure (entities, gateways, DAOs) đã scaffold** → Focus vào logic/behavior
- **UI skeleton có** → Focus vào wiring agent loop, permission, tools vào UI
- **Permission broker chưa có** → Đây là component CRITICAL nhất, implement trước hết
- **Bubble skeleton có nhưng thiếu Panel** → Cần tạo ChatBubblePanel

---

## 4. Task chi tiết

### P02.T01 — Agent domain models `[TODO → CRITICAL]`

**File:** `app/src/main/java/io/legado/app/domain/agent/AgentModels.kt` `[NEW]`

**Nội dung:**
```
ProposedAction — mutation request chờ user approve
PermissionToken — one-time token: proposal hash, args hash, expiry, conversation ID
AgentStep / AgentStepResult — trace mỗi bước
AgentLoopState enum — WAITING_INPUT, CALLING_MODEL, EXECUTING_TOOL, WAITING_APPROVAL, 
                       FINAL_ANSWER, CANCELLED, MAX_STEP, LOOP_DETECTED
ToolCall / ToolResult — structured tool invocation
```

**Tiêu chí pass:**
- Tất cả model `@Stable` hoặc `data class` với sealed interface
- Không phụ thuộc Android framework
- Serialize/deserialize round-trip (Room/JSON)

---

### P02.T02 — Permission broker `[TODO → CRITICAL]`

**File:** `app/src/main/java/io/legado/app/domain/agent/AgentPermissionPolicy.kt` `[NEW]`

**Yêu cầu:**
1. Permission levels: `READ` (theo capability), `WRITE` (preview before/after), `DELETE/BULK/PLUGIN_INSTALL` (xác nhận mạnh)
2. Confirmation tạo `PermissionToken`: one-time, chứa proposal hash + args hash + expiry + conversation ID
3. Repository PHẢI từ chối mutation nếu thiếu/sai/expired token — kể cả tool gọi trực tiếp
4. Batch proposal không được thay đổi danh sách target sau khi user confirm
5. Token replay protection (used flag)

**Tiêu chí pass:**
- Unit test: mutation thiếu token → reject
- Unit test: token expired → reject
- Unit test: token hash mismatch → reject
- Unit test: batch target thay đổi sau confirm → reject
- Unit test: token dùng lại lần 2 → reject

---

### P02.T03 — Agent orchestration loop hoàn thiện `[PARTIAL → DONE]`

**File:** `app/src/main/java/io/legado/app/domain/usecase/RunAiAgentUseCase.kt` `[MODIFY]`

**Yêu cầu:**
1. Implement full 10-step state machine (nhận message → load context → chọn route → call model → parse tool → check permission → proposal nếu mutation → execute → trace → loop/final)
2. Max 12 tool steps/lượt; user tiếp tục thủ công
3. Loop detection: 3x cùng tool+args → AgentLoopState.LOOP_DETECTED → stop
4. Mỗi step ghi `AiAgentTrace`
5. Cancel tại bất kỳ step → cleanup, không để orphan coroutine

**Tiêu chí pass:**
- Test: agent đạt final answer trong ≤12 steps
- Test: tool call 3x cùng args → stop với LOOP_DETECTED
- Test: cancel giữa tool execution → state = CANCELLED
- Test: max-step → state = MAX_STEP

---

### P02.T04 — Execute approved action `[TODO]`

**File:** `app/src/main/java/io/legado/app/domain/usecase/ExecuteApprovedAgentActionUseCase.kt` `[NEW]`

**Yêu cầu:**
1. Nhận `ProposedAction` + `PermissionToken` từ user confirm
2. Verify token validity (hash, expiry, used flag)
3. Execute mutation qua gateway
4. Ghi audit trail vào `AiAgentTrace`
5. Update UI state sau mutation

**Tiêu chí pass:**
- Approved action thực thi thành công + audit ghi đúng
- Invalid token → reject + không thay đổi data

---

### P02.T05 — Tool registry implementation `[PARTIAL → DONE]`

**Files:**
- `domain/gateway/AiToolGateway.kt` `[MODIFY]`
- `data/repository/AiToolRepository.kt` `[MODIFY]`

**Read tools:**
1. List/search kệ sách, sách, chương cached, bookmark, reading stats
2. Search trong raw/cache/final translation
3. List nguồn, search/explore qua nguồn enabled
4. Đọc dự án Ebook/Sáng tác
5. Search memory, artifact, dictionary
6. Internet search (Google adapter)

**Mutation tools (qua permission broker):**
1. Thêm sách từ search result
2. Xóa sách/cache/file tải
3. Đổi group/tag/sort/metadata
4. Dọn sách trùng/cache hỏng/file mồ côi
5. CRUD dictionary/memory/bookmark/note
6. Tạo/sửa project
7. Download task CRUD

**Tiêu chí pass:**
- Read tool trả data chính xác
- Mutation tool tạo ProposedAction, KHÔNG execute trực tiếp
- Test end-to-end: agent tìm sách → trả card → user confirm thêm → sách trong kệ

---

### P02.T06 — Chat bubble panel & completion `[TODO/PARTIAL]`

**Files:**
- `ui/ai/bubble/ChatBubblePanel.kt` `[NEW]`
- `ui/ai/bubble/ChatBubbleCoordinator.kt` `[MODIFY]`
- `ui/ai/bubble/ChatBubbleHost.kt` `[MODIFY]`

**Panel:**
1. Phone: `AppModalBottomSheet` 60–80% height
2. Tablet: side panel
3. Session stream continues khi chuyển Activity
4. Stop/cancel controls
5. Message list + input

**Coordinator:**
1. Process-scoped (App-level singleton)
2. `ActivityLifecycleCallbacks` attach `ComposeView` vào decor
3. Exclusion policy: dialog, system picker, secret screen

**Host:**
1. Bubble 48–56 dp, drag-snap edge
2. Lưu position riêng portrait/landscape
3. Tôn trọng cutout, system bars, nav bar, IME
4. Tap → mở panel; long press → menu (fullscreen, ẩn, tắt)
5. Badge: unread/running/approval-required/error

**Tiêu chí pass:**
- Bubble hiển thị trên Home, Reader, Browser, Media, Writing
- Drag + snap hoạt động
- Chuyển Activity → session không mất
- Secret screen → bubble ẩn

---

### P02.T07 — Context bridge hoàn thiện `[PARTIAL → DONE]`

**File:** `ui/ai/context/AiScreenContextRegistry.kt` `[MODIFY]`

**Context DTO cho mỗi màn hình:**
- Reader: book URL, chapter, position, text selection
- Browser: URL/title, visible text (user-approved)
- Media: book/episode/variant/position
- Writing/Ebook: project/chapter/block selection
- Home/Bookshelf: selected group/filter

**Security:**
- API-key/OAuth popup → disable context capture
- Password field → disable
- Context provider CHỈ cung cấp DTO read-only, KHÔNG đưa Activity/View/WebView object

---

### P02.T08 — Skill/plugin validation `[TODO]`

**Files:**
- `domain/gateway/AiSkillGateway.kt` `[MODIFY]`
- `data/repository/AiSkillRepository.kt` `[NEW]`

**Yêu cầu:**
1. Skill: folder manifest + SKILL.md, requirements, allowed tools, version
2. Tool tự tạo: DSL/JavaScript Rhino sandbox + API whitelist
3. Plugin: manifest, script/resource, capability declaration
4. Validator chặn: API cấm, path traversal, network domain cấm, private IP
5. Mặc định disabled; enable chỉ sau preview + confirm
6. Skill suggestion: đề xuất tạo skill chỉ sau 3 workflow thành công tương tự

---

### P02.T09 — Agent Dashboard hoàn thiện `[PARTIAL → DONE]`

**Files:**
- `ui/ai/agent/AgentDashboardContract.kt` `[MODIFY]`
- `ui/ai/agent/AgentDashboardViewModel.kt` `[MODIFY]`
- `ui/ai/agent/AgentDashboardScreen.kt` `[MODIFY]`

**Sections cần bổ sung:**
1. Profile/persona, system policy, route
2. Conversation/session browser, run status, trace viewer
3. Memory browser theo scope/type (global, conversation, book, project)
4. Tool registry, permissions, usage stats
5. Skill/plugin: version, diff, validate, enable, rollback
6. Proposal queue (pending approval) + audit log
7. Usage: provider/model, latency, token/cost estimate, errors, tool step count

---

### P02.T10 — Base Activity bubble hooks `[TODO]`

**Files:**
- `base/BaseComposeActivity.kt` `[MODIFY]`
- `base/BaseActivity.kt` `[MODIFY]`
- `base/VMBaseActivity.kt` `[MODIFY]`

**Yêu cầu:**
- Hook rõ ràng để ChatBubbleCoordinator attach/detach
- Không duplicate attach
- Opt-out cho secret screens

---

## 5. Test bắt buộc

### Unit tests mới

| Test case | File | Priority |
|---|---|:---:|
| Agent final answer in ≤12 steps | `RunAiAgentUseCaseTest.kt` | CRITICAL |
| Agent tool call, parse, execute | same | CRITICAL |
| Agent cancel → CANCELLED state | same | CRITICAL |
| Agent max-step → MAX_STEP | same | HIGH |
| Agent loop 3x → LOOP_DETECTED | same | HIGH |
| Permission broker reject missing token | `AgentPermissionPolicyTest.kt` | CRITICAL |
| Permission broker reject expired | same | CRITICAL |
| Permission broker reject hash mismatch | same | CRITICAL |
| Permission broker reject replay | same | CRITICAL |
| Batch proposal immutable after confirm | same | HIGH |
| Bookshelf add/remove/tag audit | `AiToolRepositoryTest.kt` | HIGH |
| Skill detector 3-execution threshold | `AiSkillRepositoryTest.kt` | MEDIUM |
| Validator chặn path traversal | same | HIGH |
| Memory scope isolation | `AiMemoryRepositoryTest.kt` | HIGH |
| Bubble position normalize | `ChatBubblePositionTest.kt` | MEDIUM |
| Context registry DTO + secret disable | `AiScreenContextRegistryTest.kt` | HIGH |

### Nox smoke test

| # | Kịch bản |
|:---:|---|
| 1 | Bật bubble → hiện trên Home, Reader, Browser, Media, Writing, Ebook Editor |
| 2 | Chuyển màn hình khi stream → session/response không mất |
| 3 | Kéo/snap bubble, xoay màn hình, mở bàn phím |
| 4 | "Tìm sách Naruto" → Agent dùng source tool → trả card |
| 5 | "Thêm sách này" → proposal → confirm → sách trong kệ |
| 6 | "Xóa cache sách X" → diff + số mục → cancel → DB không đổi |
| 7 | Tạo skill draft → mặc định disabled → validate + rollback |
| 8 | API key popup → Agent context tắt |
| 9 | Logcat: không Koin error, không leak Activity/WebView |

---

## 6. Điều kiện đóng phase

- [ ] Agent có trace, memory và permission broker thực thi ở repository
- [ ] Tool đọc/ghi kệ sách hoạt động end-to-end
- [ ] Skill/tool/plugin chỉ là draft trước khi user duyệt
- [ ] Bong bóng hoạt động trên toàn app, không xin quyền overlay ngoài app
- [ ] Không có mutation hoặc data leak khi user cancel/deny
