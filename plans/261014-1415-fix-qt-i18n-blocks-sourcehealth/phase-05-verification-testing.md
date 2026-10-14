# Phase 05: Tối Ưu Hóa Gemini Web (4 Model Thực Tế + Cấp Độ Tư Duy + Lồng Kỹ Năng Gemini Web Vào Prompt), Khóa Chặt AI Model/Combo, Sửa Lỗi Hệ Thống TTS & Kiểm Thử Toàn Diện



> **Thuộc kế hoạch:** [`plan.md`](./plan.md)  

> **Phase ID:** `phase-05`  

> **Trạng thái:** ⬜ Pending  

> **Phụ thuộc:** Phases 01, 02, 03, 04



---



## 1. Mục Tiêu (Objectives)

1. **Đồng bộ chính xác 4 Model thực tế & Cấp độ tư duy của Gemini Web (`GEMINI_WEB`):**

   - Cập nhật đúng 4 dòng model thực tế hiển thị trên giao diện Gemini Web hiện hành:

     - **`3.5 Flash-Lite`** — *"Câu trả lời nhanh nhất"*

     - **`3.8 Flash`** — *"Trợ giúp toàn diện"*

     - **`3.1 Pro`** — *"Suy luận nâng cao"*

     - **`4 Argon`** — *"Nhiệm vụ phức tạp"*

   - Hỗ trợ chọn **Cấp độ tư duy (`Cấp độ tư duy ▸`: `Tự động`, `Thấp`, `Trung bình`, `Cao`)** và bóc tách sạch khối suy luận nội bộ (`thought`) khỏi kết quả đầu ra.

2. **Tận dụng tính năng “Kỹ năng” (Tuỳ chỉnh $\rightarrow$ Kỹ năng) của Gemini Web lồng vào Prompt:**

   - Cho phép người dùng khai báo và kích hoạt **Kỹ năng (Skill) đã tạo trước trên Gemini Web** (ví dụ: Kỹ năng dịch truyện Tiên Hiệp/Cổ Phong, Kỹ năng biên tập văn học).

   - Tự động lồng chỉ thị kích hoạt Kỹ năng vào đầu prompt trong `GeminiWebHandler`, đồng thời rút gọn phần System Prompt trùng lặp của ứng dụng để giảm 70%–80% độ dài prompt hệ thống, tăng tốc độ phản hồi và tối đa hóa hiệu quả dịch thuật.

3. **Khóa chặt lựa chọn Model/Combo AI của người dùng:** Vá dứt điểm 5 lỗ hổng trong codebase khiến sau khi chọn Model lẻ hoặc Combo ở tác vụ AI, hệ thống lại tự ý nhảy về `"Free fallback"`.

4. **Khắc phục triệt để các lỗi chạy TTS (`ReadAloud` / `LocalTts` / `ZeroTTS`).**

5. **Thực thi toàn bộ Unit Tests & Nghiệm thu thực tế trên thiết bị.**



---



## 2. Thiết Kế Chi Tiết: 4 Model Thực Tế, Cấp Độ Tư Duy & Lồng Kỹ Năng Gemini Web Vào Prompt



### 2.1. Bảng Danh Mục 4 Model Thực Tế & Cấp Độ Tư Duy (`AiProviderCatalog.kt` & `GeminiWebHandler.kt`)



| Model ID (DrDucBook) | Tên Hiển Thị Trên UI | Mô Tả Gốc Trên Gemini Web | Cấp Độ Tư Duy (`GeminiWebThinkingLevel`) | Context / Output | Vai Trò & Khuyên Dùng |

|---|---|---|---|---|---|

| `gemini-web-3.5-flash-lite` | `3.5 Flash-Lite (Câu trả lời nhanh nhất · Free)` | Câu trả lời nhanh nhất | `NONE` (Trả lời trực tiếp) | `1,000,000` / `16,384` | Tốc độ nhanh nhất, mở cho cả gói Free & Pro |

| `gemini-web-3.8-flash` | `3.8 Flash (Trợ giúp toàn diện)` | Trợ giúp toàn diện | `FAST` (Tiêu chuẩn) | `1,000,000` / `32,768` | Dịch chương toàn diện, cân bằng tốc độ và chất lượng |

| `gemini-web-3.1-pro` | `3.1 Pro (Suy luận nâng cao)` | Suy luận nâng cao | `ADVANCED` (Suy luận nâng cao) | `1,000,000` / `64,000` | Dịch chương độ khó cao, phân tích ngữ cảnh & thực thể |

| `gemini-web-4-argon` | `4 Argon (Nhiệm vụ phức tạp · Tư duy: Tự động)` | Nhiệm vụ phức tạp | `AUTO` (Tự động điều tiết) | `1,000,000` / `64,000` | Model thế hệ 4 mạnh nhất cho các nhiệm vụ phức tạp |

| `gemini-web-4-argon-low` | `4 Argon (Cấp độ tư duy: Thấp)` | Nhiệm vụ phức tạp · Tư duy Thấp | `LOW` (Suy luận ngắn) | `1,000,000` / `64,000` | Dùng 4 Argon dịch chương nhanh, giảm thời gian chờ |

| `gemini-web-4-argon-medium` | `4 Argon (Cấp độ tư duy: Trung bình)` | Nhiệm vụ phức tạp · Tư duy Trung bình | `MEDIUM` (Cân bằng) | `1,000,000` / `64,000` | Dùng 4 Argon phân tích xưng hô, thuật ngữ & mạch truyện |

| `gemini-web-4-argon-high` | `4 Argon (Cấp độ tư duy: Cao)` | Nhiệm vụ phức tạp · Tư duy Cao | `HIGH` (Suy luận sâu tối đa) | `1,000,000` / `64,000` | Dùng 4 Argon cho AI Rewrite (Cổ phong), tóm tắt & đại cục |



#### Ánh xạ tương thích ngược tự động (Legacy Alias Mapping trong `GeminiWebHandler.kt`):

- `gemini-web-flash-lite` $\rightarrow$ ánh xạ tự động sang `gemini-web-3.5-flash-lite`.

- `gemini-web-default` / `gemini-web-flash` $\rightarrow$ ánh xạ tự động sang `gemini-web-3.8-flash`.

- `gemini-web-pro` $\rightarrow$ ánh xạ tự động sang `gemini-web-3.1-pro`.

- `gemini-web-thinking` $\rightarrow$ ánh xạ tự động sang `gemini-web-4-argon`.



### 2.2. Cơ Chế Tận Dụng “Kỹ Năng” Của Gemini Web (Tuỳ Chỉnh $\rightarrow$ Kỹ Năng) Lồng Vào Prompt

Khi người dùng đã tạo trước một **Kỹ năng (Skill)** trong mục **Tuỳ chỉnh $\rightarrow$ Kỹ năng** trên giao diện Gemini Web (ví dụ: đặt tên Kỹ năng là `"DrDucBook Dịch Truyện"` hoặc `"Dịch Giả Tiên Hiệp Cổ Phong"`):



1. **Cách khai báo Kỹ năng trong `DrDucBook` (Hỗ trợ đồng thời 2 cách):**

   - **Cách 1 — Qua trường cấu hình `geminiWebSkillName` trong `AiRuntimeOptions` / Màn hình Cấu hình Prompt:**

     - Thêm trường `geminiWebSkillName: String? = null` và `geminiWebSkillCompactMode: Boolean = true` vào `AiRuntimeOptions`.

     - Trên `TranslationConfigScreen` / `TranslationPromptConfigScreen` (và `PerBookTranslationPromptSection`), thêm ô nhập: **"Tên Kỹ năng Gemini Web (Tuỳ chỉnh -> Kỹ năng)"** cùng nút **"Sao chép mẫu Kỹ năng chuẩn"** (để người dùng dán vào Gemini Web khi tạo Kỹ năng).

   - **Cách 2 — Qua Tag trực tiếp trong ô Prompt tùy chỉnh (Per-Book hoặc Global Prompt):**

     - Nếu người dùng viết ở dòng đầu của Prompt tùy chỉnh cú pháp: `[SKILL: Tên Kỹ Năng]` hoặc `@Skill("Tên Kỹ Năng")`, hệ thống tự động trích xuất tên Kỹ năng đó.

2. **Cách `GeminiWebHandler.buildFullPrompt()` lồng Kỹ năng & Tối ưu hóa Prompt:**

   - Khi có `skillName` được cấu hình:

     - **Bước 1 (Skill Activation Header):** Đặt khối kích hoạt Kỹ năng ngay đầu prompt gửi tới Gemini Web:

       ```xml

       <gemini_skill_activation>

       [Kích hoạt Kỹ năng: "{skillName}"]

       Hãy sử dụng Kỹ năng "{skillName}" đã được thiết lập của tôi và tuân thủ nghiêm ngặt toàn bộ quy tắc dịch thuật, văn phong, đại từ xưng hô cùng định dạng đầu ra trong Kỹ năng đó.

       </gemini_skill_activation>

       ```

     - **Bước 2 (Compact Prompt Optimization):** Nếu `geminiWebSkillCompactMode == true`, hệ thống tự động **bỏ qua phần văn bản hướng dẫn dịch thuật chung dài dòng của `DEFAULT_PROMPT` / `Hard Rules 1..14`** (do đã nằm sẵn trong Kỹ năng trên Gemini Web của người dùng), và chỉ đính kèm:

       1. Từ điển khóa của chương (`<locked_dictionary>`).

       2. Ngữ cảnh nhân vật & cặp đại từ xưng hô `SELF/OTHER` của chương (`<relationship_context>`, `<character_pronouns>`).

       3. Ràng buộc định dạng JSON trả về (`{"translations":[{"i":0,"t":"..."}]}`) và danh sách đoạn văn gốc (`<segments>`).

     - **Lợi ích:** Giảm 70%–80% token hệ thống lặp lại ở mỗi request, giúp `4 Argon`, `3.1 Pro`, `3.8 Flash` và `3.5 Flash-Lite` phản hồi nhanh hơn rõ rệt và bám sát 100% phong cách trong Kỹ năng của người dùng!



### 2.3. Các Task Thực Thi Chi Tiết Cho Gemini Web

- [ ] **Task 05.1 (`AiProviderCatalog.kt`, `AiModels.kt`, `GeminiWebHandler.kt`):**

  - Cập nhật danh sách model của `gemini_web` trong `AiProviderCatalog.kt` và `GeminiWebHandler.fetchModels()` theo đúng bảng 7 mục ở Mục 2.1 (`3.5 Flash-Lite`, `3.8 Flash`, `3.1 Pro`, `4 Argon` + 3 mức tư duy `Thấp`, `Trung bình`, `Cao`).

  - Trong `GeminiWebHandler.kt`: xây dựng `resolveGeminiWebModelSpec(modelId, runtimeOptions)` để gửi đúng header `x-goog-ext-525001261-jspb` và tham số cấp độ tư duy trong RPC `StreamGenerate`.

  - Trong `GeminiStreamAccumulator`: tách riêng phần suy luận nội bộ (`thought`) sang `AiStreamEvent.ReasoningDelta`, chỉ phát phần văn bản trả lời chính thức (`content`) vào `AiStreamEvent.TextDelta`.

  - Giữ cơ chế tự động hạ cấp mượt trong phiên nếu tài khoản Free gọi vào model gói Pro (`4 Argon` $\rightarrow$ `3.1 Pro` $\rightarrow$ `3.8 Flash` $\rightarrow$ `3.5 Flash-Lite`).

- [ ] **Task 05.2 (`GeminiWebHandler.kt`, `AiTranslationRefinePipeline.kt`, `TranslationPromptConfigScreen.kt`):**

  - Bổ sung `geminiWebSkillName: String? = null` và `geminiWebSkillCompactMode: Boolean = true` vào `AiRuntimeOptions`.

  - Hỗ trợ trích xuất tag `[SKILL: ...]` / `@Skill(...)` và lồng khối `<gemini_skill_activation>` vào đầu `GeminiWebHandler.buildFullPrompt()`.

  - Thêm ô nhập tên Kỹ năng Gemini Web và nút **"Sao chép mẫu Kỹ năng chuẩn"** trên giao diện cấu hình dịch thuật.



---



## 3. Khóa Chặt Lựa Chọn Model/Combo AI — Chống Tự Ý Đổi Về Free Fallback



### 3.1. Tóm tắt 5 lỗ hổng trong Codebase cần vá

1. **Lỗ hổng 1 (`ensureGeneratedPresetRouteBinding` & `RepairAiRouteBindingsUseCase`):** Chỉ kiểm tra `existing.runtimeOptions.routeProfileId.isNullOrBlank()`, nên khi người dùng chọn **Model đơn lẻ** (`modelProfileId` có giá trị nhưng `routeProfileId == null`), hàm tưởng là preset chưa cấu hình và **ghi đè cả `modelProfileId` lẫn `routeProfileId` về Free Fallback (`route_free_*`)**.

2. **Lỗ hổng 2 (`AiRouterRepository.resolveRoute`):** Khi `request.routeProfileId == null` (chế độ chọn Model đơn lẻ), hàm lại gọi `dao.getActiveRoute(taskType)` (lấy `route_free_*` đang có `isDefault = 1` trong bảng `ai_route_profiles`) thay vì chạy trực tiếp `preset.modelProfileId` mà người dùng đã chọn.

3. **Lỗ hổng 3 (`AiComboModelPickerSheet`):** Khi chọn một Combo từ màn hình cấu hình Dịch/Prompt, code chỉ lưu `routeProfileId` vào `AiTaskPreset` mà không cập nhật `isDefault = true` cho route đó trong bảng `ai_route_profiles`.

4. **Lỗ hổng 4 (`ensureGeneratedFreeRoutes`):** Khi rebuild danh sách model của Free Route, cần đảm bảo không bao giờ ghi đè `makeDefault = true` nếu người dùng đã có route default khác.

5. **Lỗ hổng 5 (Không xóa `routeProfileId` cũ khi chuyển từ Combo sang Model đơn lẻ):** Khi preset trước đó đang gắn `routeProfileId = "route_free_translate_chapter"`, nếu người dùng chuyển sang chọn 1 Model đơn lẻ mà không set `routeProfileId = null`, hệ thống vẫn ưu tiên đọc `routeProfileId` cũ.



### 3.2. Các Task Thực Thi Khóa Chặt Lựa Chọn Model/Combo

- [ ] **Task 05.3 (`AiRouterViewModel.kt` & `RepairAiRouteBindingsUseCase.kt`):**

  - Trong `ensureGeneratedPresetRouteBinding()`:

    ```kotlin

    val existing = profileGateway.getTaskPreset(taskType)

    if (existing != null && (existing.modelProfileId.isNotBlank() || !existing.runtimeOptions.routeProfileId.isNullOrBlank())) {

        return

    }

    ```

  - Trong `RepairAiRouteBindingsUseCase.kt`: Không bao giờ tự động sửa `routeProfileId` hoặc `modelProfileId` của một preset đã tồn tại về `route_free_*`.

- [ ] **Task 05.4 (`AiRouterRepository.kt`):**

  - Trong `resolveRoute()` / `execute()`: Nếu `request.routeProfileId.isNullOrBlank()` nhưng `request.modelProfileId.isNotBlank()` (hoặc `preset.modelProfileId.isNotBlank()` và `preset.runtimeOptions.routeProfileId == null`), thực thi trực tiếp model `modelProfileId` đó (với toàn bộ pool credentials của provider tương ứng), **không gọi `dao.getActiveRoute(taskType)`**.

- [ ] **Task 05.5 (`TranslationConfigScreen.kt`, `TranslationPromptConfigViewModel.kt`, `AiConfigViewModel.kt`):**

  - Khi người dùng chọn **Model đơn lẻ** trong `AiComboModelPickerSheet`: cập nhật `modelProfileId = selectedModel.id` và đặt `runtimeOptions = runtimeOptions.copy(routeProfileId = null)`.

  - Khi người dùng chọn **Combo (Route)** trong `AiComboModelPickerSheet`: cập nhật `runtimeOptions = runtimeOptions.copy(routeProfileId = selectedRoute.id)` **đồng thời** gọi `aiRouterRepository.setDefaultRoute(taskType, selectedRoute.id)`.



---



## 4. Khắc Phục Toàn Diện Lỗi Chạy Hệ Thống TTS (`ReadAloud` / `LocalTts` / `ZeroTTS`)



- [ ] **Task 05.6 — Sửa định tuyến Service & Chặn Race Condition `onDestroy` (`ReadAloud.kt` & `BaseReadAloudService.kt`):**

  - Trong `ReadAloud.upReadAloudClass()`:

    - Nếu `AppConfig.ttsFollowSys == true` $\rightarrow$ trả về `TTSReadAloudService::class.java`.

    - Nếu `ttsEngine` là `LocalTtsEngineRef` (hoặc khớp với ID mô hình trong `LocalTtsModelRegistry`) $\rightarrow$ trả về `LocalTtsReadAloudService::class.java`.

    - Nếu `ttsEngine.toLongOrNull() != null` và tồn tại trong `httpTTSDao` $\rightarrow$ trả về `HttpReadAloudService::class.java`.

    - Mặc định fallback an toàn về `TTSReadAloudService::class.java`.

  - Trong `BaseReadAloudService.kt`:

    - Lưu tham chiếu `@Volatile var activeServiceInstance: BaseReadAloudService? = null` khi `onCreate()`.

    - Trong `onDestroy()`, chỉ gán `isRun = false` và phát `EventBus(EventBus.ALOUD_STATE, Status.STOP)` khi `activeServiceInstance === this`. Điều này chấm dứt hoàn toàn lỗi khi chuyển đổi qua lại giữa System TTS và ZeroTTS làm service cũ hủy muộn tắt luôn service mới.

- [ ] **Task 05.7 — Bắt buộc `Dispatchers.Main` cho `ExoPlayer` & Bỏ qua đoạn văn chỉ có dấu câu (`LocalTtsReadAloudService.kt` & `ZeroTtsOnnxEngine.kt`):**

  - Trong `LocalTtsReadAloudService.kt` và `HttpReadAloudService.kt`: bọc mọi thao tác lên `exoPlayer` (`stop()`, `clearMediaItems()`, `setMediaItem()`, `addMediaItem()`, `prepare()`, `play()`) trong `withContext(Dispatchers.Main.immediate)`.

  - Trước khi đưa đoạn văn (`paragraph`) vào `LocalTtsSynthesis.synthesize()`: làm sạch các marker định dạng ẩn và kiểm tra `cleanParagraph.any { it.isLetterOrDigit() }`. Nếu đoạn văn chỉ toàn dấu câu (`***`, `---`, `...`, `===`) hoặc khoảng trắng, tự động gọi `nextParagraph()` trên Main thread thay vì dừng dịch vụ.

  - Trong `ZeroTtsOnnxEngine.kt`: tự động dò tìm tệp `.onnx` (ưu tiên bản INT8, fallback bản FP32) và kiểm tra tệp giọng đọc `.npy` trong thư mục `voices/`. Nếu `voiceId` được chọn chưa có tệp `.npy` trên đĩa, tự động fallback về tệp `.npy` đầu tiên sẵn có trong thư mục model.



---



## 5. Quy Trình Kiểm Thử Tự Động & Nghiệm Thu (Verification & Build Gate)



### 5.1. Các Lệnh Kiểm Thử Bắt Buộc

```powershell

# 1. Kiểm tra biên dịch Kotlin toàn dự án

.\gradlew.bat :app:compileAppDebugKotlin --no-daemon --console=plain



# 2. Chạy Unit Tests cho Phase 01 (QT Gốc + Mapping) & Phase 02 (Mojibake)

.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.data.repository.QuickTranslationRepositoryTest" --tests "io.legado.app.ui.quickdict.QuickDictionarySelectionResolverTest" --tests "io.legado.app.domain.model.VietnameseTranslationPostProcessorTest" --no-daemon --console=plain



# 3. Chạy Unit Tests cho Phase 03 (Blocks) & Phase 04 (Source Health)

.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.domain.model.BookContentProcessEngineTest" --tests "io.legado.app.ui.book.source.health.SourceHealthStateTest" --tests "io.legado.app.domain.sourcehealth.SourceCheckClassifierTest" --tests "io.legado.app.data.repository.sourcehealth.SourceCheckRepositoryTest" --no-daemon --console=plain



# 4. Chạy Unit Tests cho Phase 05 (Gemini Web, AI Route Lock & Session Manager)

.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.data.repository.ai.WebSessionManagerTest" --tests "io.legado.app.ui.ai.router.AiRouterAutoInstallPolicyTest" --tests "io.legado.app.domain.usecase.RepairAiRouteBindingsUseCaseTest" --no-daemon --console=plain



```



### 5.2. Checklist Nghiệm Thu Cuối Cùng (Final Acceptance Checklist)



* [ ] **Phase 01:** Dịch chương QT < 50ms, chuẩn `VietPhrase` + `LuatNhan`, bôi đen từ bất kỳ lấy đúng 100% chữ Hán gốc.

* [ ] **Phase 02:** Không còn dấu `?` lỗi giữa tên riêng hay ký tự Mojibake; 100% nhãn trên `SourceHealthScreen` hiển thị tiếng Việt.

* [ ] **Phase 03:** Toàn bộ khối tải xuống thu gọn `54.dp`–`72.dp`, hiển thị 6–8 mục trên 1 màn hình.

* [ ] **Phase 04:** Bộ đếm `SourceHealthSummaryUi` khớp 100% với bộ lọc; xóa nguồn lỗi xóa sạch cả `Book`/`RSS`/`VBook` và lịch sử check.

* [ ] **Phase 05.A (Gemini Web):** Chọn được đủ 4 model thực tế (`3.5 Flash-Lite`, `3.8 Flash`, `3.1 Pro`, `4 Argon`) + `Cấp độ tư duy` (`Tự động`, `Thấp`, `Trung bình`, `Cao`), lồng thành công **Kỹ năng Gemini Web** vào prompt và tách sạch khối `thought`.

* [ ] **Phase 05.B (AI Model/Combo Lock):** Chọn Model lẻ hoặc Combo xong giữ nguyên bền vững 100%, không bao giờ tự nhảy về `"Free fallback"`.

* [ ] **Phase 05.C (TTS):** Đọc thành tiếng mượt mà trên cả System TTS và ZeroTTS, tự động vượt qua dòng `***` và đổi giọng không bị dừng/crash.







---



# 📌 PHỤ LỤC: MẪU “KỸ NĂNG” (SKILL TEMPLATE) ĐỂ TẠO TRƯỚC TRÊN GEMINI WEB (MỤC `TUỲ CHỈNH` $\rightarrow$ `KỸ NĂNG` $\rightarrow$ `TẠO`)[cite: 3]



Bạn có thể copy trực tiếp nội dung dưới đây để tạo sẵn **2 Kỹ năng cốt lõi** trên tài khoản Gemini Web của mình[cite: 3]:



### 1. Kỹ năng 1: Dịch Thuật Chương Truyện Chuẩn Văn Học

* **Tên Kỹ năng (đặt trên Gemini Web):** `DrDucBook Dịch Truyện`

* **Nội dung hướng dẫn Kỹ năng:**

```text

Bạn là Dịch Giả Tiểu Thuyết Trung-Việt cao cấp của hệ thống DrDucBook. Nhiệm vụ của bạn là dịch các phân đoạn truyện tiếng Trung sang tiếng Việt giàu nhạc điệu, đúng văn phong thể loại (Cổ phong / Tiên hiệp / Huyền huyễn / Đô thị / Kỳ huyễn phương Tây) và tuân thủ tuyệt đối tính nhất quán của bộ truyện.



QUY TẮC BẮT BUỘC:

1. TỪ ĐIỂN & THỰC THỂ KHÓA (<locked_dictionary>):

   - Bắt buộc sử dụng chính xác 100% cách dịch tên nhân vật, địa danh, cảnh giới, chiêu thức đã khai báo trong <locked_dictionary>.

   - Đối với tên nước ngoài phiên âm Hán tự (Nhật/Hàn/Phương Tây), chuyển về tên Romaji/Latin chuẩn xác, tuyệt đối không dịch Hán-Việt từng chữ rời rạc.

2. ĐẠI TỪ XƯNG HÔ (<relationship_context> & <character_pronouns>):

   - Chuyển đổi toàn bộ đại từ "我 / 你 / 他 / 她 / 咱们" sang cặp đại từ tiếng Việt tương ứng với quan hệ nhân vật (SELF / OTHER: ta - ngươi, huynh - muội, đệ - tỷ, bản tọa, tại hạ, lão phu, hắn, nàng, y, anh - em, tôi - cậu...).

   - Không bao giờ dịch máy "của tôi / của bạn" trong bối cảnh cổ trang/tiên hiệp hoặc đối thoại thân mật.

3. NGỮ PHÁP & VĂN PHONG THUẦN VIỆT:

   - Xóa bỏ cấu trúc calque tiếng Trung: không lạm dụng từ "của" (的) trước động từ/tính từ; đảo định ngữ về đúng trật tự tiếng Việt; xử lý mượt cấu trúc "把" (đem/lấy) và "被" (bị/được).

   - Giữ nguyên nhịp điệu câu văn, sắc thái biểu cảm và thuật ngữ tu luyện/hệ thống RPG.

4. BẢO TOÀN ĐOẠN VĂN & ĐỊNH DẠNG ĐẦU RA:

   - Dịch đủ 100% số đoạn theo đúng chỉ số "i", không gộp đoạn, không cắt bớt nội dung, không để sót bất kỳ ký tự chữ Hán (CJK) nào trong bản dịch.

   - Khi prompt yêu cầu định dạng JSON {"translations":[{"i":0,"t":"..."}]}, chỉ trả về duy nhất chuỗi JSON hợp lệ, không thêm lời dẫn hay giải thích bên ngoài.



```



### 2. Kỹ năng 2: Biên Tập & Viết Lại Chương Truyện (AI Rewrite)



* **Tên Kỹ năng (đặt trên Gemini Web):** `DrDucBook Biên Tập Cổ Phong`

* **Nội dung hướng dẫn Kỹ năng:**



```text

Bạn là Biên Tập Viên Văn Học Việt Nam chuyên hiệu đính và viết lại (Rewrite) các chương tiểu thuyết dịch thô/convert sang văn phong tiểu thuyết xuất bản mượt mà, trau chuốt và giàu cảm xúc.



QUY TẮC BẮT BUỘC:

1. Giữ nguyên 100% cốt truyện, tình tiết, tên riêng nhân vật, chiêu thức, cảnh giới và cặp đại từ xưng hô hiện có.

2. Tái cấu trúc các câu văn dịch thô/convert bị ngược trật tự từ thành câu tiếng Việt tự nhiên, uyển chuyển, giàu hình ảnh.

3. Bảo toàn số lượng đoạn văn và đối thoại; chỉ xuất trực tiếp nội dung chương đã biên tập hoàn chỉnh, không thêm lời bình luận ở đầu hoặc cuối.
```
