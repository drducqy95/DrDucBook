# Phase 02 — AI Agent Chatbot, tool/skill/plugin, memory và bong bóng toàn ứng dụng

## 1. Kết quả phải đạt

Chatbot trở thành AI Agent có khả năng đọc dữ liệu ứng dụng, tìm sách/internet, lập kế hoạch, gọi tool và đề xuất mutation có kiểm soát. Agent có memory dài hạn, dashboard quản trị, có thể tạo draft skill/tool/plugin và xuất hiện qua bong bóng chat trong mọi màn hình Legado mà không cần quyền overlay hệ thống.

## 2. Phạm vi

### Trong phạm vi

- Agent orchestration loop và trace từng bước.
- Tool registry đọc/ghi dữ liệu ứng dụng.
- Permission broker, preview, confirmation, audit và undo.
- Tìm kiếm nguồn sách, thêm/xóa/sắp xếp/tag/dọn kệ.
- Browser/Google search adapter và tìm trong dữ liệu sách.
- Memory global/conversation/book/project.
- Dashboard quản lý agent/session/memory/tool/skill/plugin.
- Tạo, validate, version, enable/disable và rollback skill/tool/plugin.
- Bong bóng chat nội bộ trên MainActivity, Reader, Browser, Media và Activity legacy.
- Context bridge theo màn hình hiện tại.

### Ngoài phạm vi

- Quyền nổi trên ứng dụng khác và `SYSTEM_ALERT_WINDOW`.
- Chạy shell/Kotlin/native code do model tự sinh.
- Tự động cài hoặc kích hoạt plugin/skill không có xác nhận.
- Phá captcha hoặc điều khiển website ẩn; Browser chỉ thực hiện theo policy Phase 04.
- Multi-agent tự trị chạy nền vô hạn.

## 3. Kiến trúc Agent

### 3.1 Vòng lặp

Mỗi lượt Agent phải có state machine bền vững:

1. Nhận message và scope hiện tại.
2. Nạp profile, policy, context màn hình và memory liên quan.
3. Chọn route qua AI Router.
4. Gọi model và parse tool call.
5. Kiểm tra tool/capability/permission.
6. Nếu mutation: tạo `ProposedAction`, dừng chờ user.
7. Thực thi tool qua gateway.
8. Ghi observation, trace và audit.
9. Tiếp tục cho tới final answer, cancel hoặc max-step.
10. Cập nhật summary/memory theo policy.

Giới hạn mặc định: 12 tool steps/lượt; user có thể tiếp tục thủ công. Agent phải phát hiện lặp cùng tool+args và dừng sau ba lần không tiến triển.

### 3.2 Permission broker

- `READ`: cho phép theo capability đã bật.
- `WRITE`: luôn có preview before/after.
- `DELETE/BULK/PLUGIN_INSTALL`: xác nhận mạnh, nêu số mục và khả năng phục hồi.
- Confirmation tạo token một lần, chứa proposal hash, args hash, expiry và conversation ID.
- Repository từ chối mutation nếu thiếu/sai token, kể cả tool bị gọi trực tiếp trong test.
- Batch proposal không được thay đổi danh sách target sau khi user xác nhận.

### 3.3 Tool lõi

Read tools:

- Liệt kê/tìm kệ sách, sách, chương cached, bookmark, reading stats.
- Tìm trong raw/cache/final translation.
- Liệt kê nguồn, search/explore qua nguồn enabled.
- Đọc dự án Ebook/Sáng tác.
- Tìm memory, artifact và dictionary.
- Search internet; fetch nội dung chỉ sau policy check.

Mutation tools:

- Thêm sách từ search result vào kệ.
- Xóa sách/cache/file tải; đổi group/tag/sort/metadata.
- Dọn sách trùng, cache hỏng và file mồ côi.
- CRUD dictionary/memory/bookmark/note.
- Tạo/sửa project, áp dụng suggestion hoặc revision.
- Tạo/pause/resume/delete download task.

### 3.4 Skill/tool/plugin tự tạo

- Skill: folder có manifest + `SKILL.md`, requirements, allowed tools và version.
- Tool tự tạo: declarative DSL hoặc JavaScript Rhino sandbox dùng API whitelist.
- Plugin ứng dụng/VBook: manifest, script/resource và capability declaration.
- Agent chỉ tạo draft; validator kiểm tra schema, permission, network domain, path traversal và API không được phép.
- Plugin/skill mới mặc định disabled, chỉ enable sau preview và xác nhận.
- Sau ba workflow thành công tương tự, Agent được đề xuất tạo skill; không tự tạo/kích hoạt âm thầm.

### 3.5 Memory

- Scope: global, conversation, book, writing project, ebook project.
- Loại: fact, preference, decision, glossary, relationship, workflow result và summary.
- Truy hồi bằng Room FTS; embedding là tùy chọn khi có embedding route.
- Memory có source conversation/message, confidence, created/updated time và pin state.
- User có thể xem, sửa, ghim, xóa, quên theo scope và export không chứa secret.

## 4. Bong bóng Chatbot toàn ứng dụng

### 4.1 Hosting

- Tạo `ChatBubbleCoordinator` process-scoped.
- Dùng `Application.ActivityLifecycleCallbacks` để attach một `ComposeView` vào decor của Activity đang resumed.
- Không dùng WindowManager overlay ngoài app.
- `BaseComposeActivity`, `BaseActivity` và `VMBaseActivity` cung cấp hook rõ ràng để tránh attach trùng.
- Dialog/system picker/secret screen có exclusion policy.

### 4.2 UX

- Bubble 48–56 dp, kéo thả và snap cạnh.
- Lưu vị trí chuẩn hóa riêng portrait/landscape.
- Tôn trọng cutout, system bars, navigation bar và IME.
- Tap mở chat panel; long press mở menu `Mở toàn màn hình`, `Ẩn phiên này`, `Tắt bong bóng`.
- Badge hiển thị unread/running/approval-required/error.
- Phone dùng sheet 60–80% chiều cao; tablet dùng side panel.
- Session và stream tiếp tục khi chuyển Activity; panel có stop/cancel.
- Mặc định tắt, bật từ AI Settings hoặc Chatbot.

### 4.3 Context bridge

- Reader: book URL, chapter, vị trí, text selection do user cấp.
- Browser: URL/title và visible text được user cho phép.
- Media: book/episode/variant/position.
- Writing/Ebook: project/chapter/block selection.
- Home/Bookshelf: selected group/filter.
- Context provider chỉ cung cấp DTO read-only; không đưa Activity/View/WebView object vào Agent.
- API-key/OAuth popup, password field và màn hình nhạy cảm tự vô hiệu context capture và ẩn bubble nếu cần.

## 5. Dashboard Agent

- Profile/persona, system policy và route.
- Conversation/session, trạng thái run và trace tool.
- Memory browser theo scope/type.
- Tool registry, quyền và usage.
- Skill/plugin version, diff, validate, enable, rollback.
- Proposal đang chờ duyệt và audit mutation.
- Usage: provider/model, latency, token/cost estimate, lỗi và số tool step.

## 6. File tác động

### File hiện có cần sửa

- `app/src/main/java/io/legado/app/ui/ai/chat/AiChatContract.kt`
- `app/src/main/java/io/legado/app/ui/ai/chat/AiChatViewModel.kt`
- `app/src/main/java/io/legado/app/ui/ai/chat/AiChatScreen.kt`
- `app/src/main/java/io/legado/app/data/repository/AiChatRepository.kt`
- `app/src/main/java/io/legado/app/data/repository/AiToolRepository.kt`
- `app/src/main/java/io/legado/app/data/repository/AiMemoryRepository.kt`
- `app/src/main/java/io/legado/app/domain/gateway/AiChatGateway.kt`
- `app/src/main/java/io/legado/app/domain/gateway/AiToolGateway.kt`
- `app/src/main/java/io/legado/app/domain/gateway/AiMemoryGateway.kt`
- `app/src/main/java/io/legado/app/domain/usecase/AiToolAwareGenerationUseCase.kt`
- `app/src/main/java/io/legado/app/data/entities/AiChatConversation.kt`
- `app/src/main/java/io/legado/app/data/entities/AiChatMessage.kt`
- `app/src/main/java/io/legado/app/data/entities/AiMemory.kt`
- `app/src/main/java/io/legado/app/data/dao/AiChatDao.kt`
- `app/src/main/java/io/legado/app/data/dao/AiMemoryDao.kt`
- `app/src/main/java/io/legado/app/base/BaseComposeActivity.kt`
- `app/src/main/java/io/legado/app/base/BaseActivity.kt`
- `app/src/main/java/io/legado/app/base/VMBaseActivity.kt`
- `app/src/main/java/io/legado/app/App.kt`
- `app/src/main/java/io/legado/app/ui/main/MainNavKey.kt`
- `app/src/main/java/io/legado/app/ui/main/MainNavGraph.kt`
- `app/src/main/java/io/legado/app/ui/config/ai/AiConfig*`
- `app/src/main/java/io/legado/app/di/appModule.kt`
- `app/src/main/java/io/legado/app/data/AppDatabase.kt`

### File tạo mới dự kiến

- `app/src/main/java/io/legado/app/domain/agent/AgentModels.kt`
- `app/src/main/java/io/legado/app/domain/agent/AgentPermissionPolicy.kt`
- `app/src/main/java/io/legado/app/domain/usecase/RunAiAgentUseCase.kt`
- `app/src/main/java/io/legado/app/domain/usecase/ExecuteApprovedAgentActionUseCase.kt`
- `app/src/main/java/io/legado/app/data/entities/AiAgentRun.kt`
- `app/src/main/java/io/legado/app/data/entities/AiAgentTrace.kt`
- `app/src/main/java/io/legado/app/data/entities/AiAgentProposal.kt`
- `app/src/main/java/io/legado/app/data/entities/AiSkill.kt`
- `app/src/main/java/io/legado/app/data/entities/AiSkillVersion.kt`
- `app/src/main/java/io/legado/app/data/entities/AiPlugin.kt`
- `app/src/main/java/io/legado/app/data/dao/AiAgentDao.kt`
- `app/src/main/java/io/legado/app/data/repository/AiAgentRepository.kt`
- `app/src/main/java/io/legado/app/data/repository/AiSkillRepository.kt`
- `app/src/main/java/io/legado/app/ui/ai/agent/AgentDashboardContract.kt`
- `app/src/main/java/io/legado/app/ui/ai/agent/AgentDashboardViewModel.kt`
- `app/src/main/java/io/legado/app/ui/ai/agent/AgentDashboardScreen.kt`
- `app/src/main/java/io/legado/app/ui/ai/bubble/ChatBubbleCoordinator.kt`
- `app/src/main/java/io/legado/app/ui/ai/bubble/ChatBubbleHost.kt`
- `app/src/main/java/io/legado/app/ui/ai/bubble/ChatBubblePanel.kt`
- `app/src/main/java/io/legado/app/ui/ai/context/AiScreenContextRegistry.kt`

## 7. Test bắt buộc phải pass

### Unit/integration mới

- Agent state machine: final answer, tool call, cancel, max-step và loop detection.
- Permission broker từ chối mọi mutation thiếu/expired/mismatched token.
- Proposal hash không thay đổi target sau confirm.
- Add/remove/tag/cleanup bookshelf có before/after chính xác và audit.
- Skill detector chỉ đề xuất sau ba execution thành công tương tự.
- Skill/tool/plugin validator chặn API cấm, path traversal và private network trái policy.
- Memory scope không rò dữ liệu giữa hai book/project.
- Compaction vẫn truy hồi được transcript nguồn.
- Bubble position normalization, safe inset và orientation restore.
- Context registry trả DTO đúng và tắt ở secret screen.

### Instrumentation/Nox

1. Bật bubble, mở lần lượt Home, Reader, Browser, Media, Writing và Ebook Editor.
2. Chuyển màn hình khi đang stream; session/partial response không mất.
3. Kéo/snap bubble, xoay màn hình và mở bàn phím.
4. Yêu cầu tìm sách: Agent dùng source tool và trả card.
5. Yêu cầu thêm sách: phải dừng ở proposal, chỉ thêm sau confirm.
6. Yêu cầu xóa/dọn kệ: diff và số mục phải đúng; cancel không thay đổi DB.
7. Tạo skill/plugin draft: mặc định disabled, validate và rollback được.
8. API key popup không lộ context cho Agent.

### Gate build

```powershell
.\gradlew.bat :app:compileAppDebugKotlin --no-daemon --console=plain
.\gradlew.bat :app:testAppDebugUnitTest --no-daemon --console=plain
.\gradlew.bat :app:assembleAppDebug --no-daemon --console=plain
```

## 8. Điều kiện đóng phase

- Agent có trace, memory và permission broker thực thi ở repository.
- Tool đọc/ghi kệ sách hoạt động end-to-end.
- Skill/tool/plugin chỉ là draft trước khi user duyệt.
- Bong bóng hoạt động trên toàn app, không xin quyền overlay ngoài app.
- Không có mutation hoặc data leak khi user cancel/deny.
