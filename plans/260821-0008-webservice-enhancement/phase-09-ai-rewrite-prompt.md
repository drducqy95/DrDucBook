# Phase 09: AI Rewrite Prompt Enhancement for Convert-style Vietnamese

Status: ✅ Completed
Dependencies: Phase 07

## Objective

Hoàn thiện prompt cho tác vụ viết lại AI đối với truyện tiếng Việt đang dùng văn phong convert (dịch máy QT/NMT). Bổ sung các prompt preset chuyên biệt giúp AI rewrite chuyển văn phong convert sang văn phong tiếng Việt tự nhiên, văn học, phù hợp thể loại.

## Problem Analysis

### Văn phong convert là gì?

Văn phong convert (QT/NMT auto-translated) có các đặc điểm nhận biết:
1. **Cấu trúc câu Hán-Việt**: "Hắn đối với nàng nói" thay vì "Hắn nói với nàng"
2. **Đại từ cứng nhắc**: Lặp lại "hắn/nàng/y/thị" không đổi theo ngữ cảnh
3. **Thành ngữ dịch sát**: "Nhất kiếm xuyên tâm" thay vì "Một nhát kiếm đâm thấu tim"
4. **Câu thừa chủ ngữ**: "Hắn nhìn hắn, hắn cười" thay vì "Nhìn đối phương, hắn mỉm cười"
5. **Văn tả cảnh khô khan**: Liệt kê sự kiện thay vì tạo cảm xúc
6. **Dialogue thiếu sắc thái**: Tất cả nhân vật nói cùng giọng điệu

### Hiện trạng prompt system

Hiện có 6 context presets cho `TRANSLATE_CHAPTER`:
- `context_auto_v3`: Tự nhận diện bối cảnh
- `context_ancient_v3`: Cổ đại Đông phương / Tiên hiệp
- `context_modern_v3`: Hiện đại / Đô thị
- `context_western_v3`: Kỳ huyễn phương Tây
- `context_scifi_v3`: Khoa huyễn / Game / Hệ thống
- `context_crossover_v3`: Xuyên không / Đồng nhân

Và `REWRITE_TEXT` task type có 1 generic prompt.

**Thiếu**: Prompt chuyên biệt cho việc chuyển đổi văn phong convert → văn phong tự nhiên.

## Requirements

### Functional
- [ ] REQ-01: Thêm `RETRANSLATE` stage prompt presets chuyên biệt cho từng thể loại
- [ ] REQ-02: Thêm `REWRITE_TEXT` presets cho tác vụ "Chuyển văn phong convert"
- [ ] REQ-03: Prompt phải hướng dẫn AI nhận diện và sửa các pattern convert đặc trưng
- [ ] REQ-04: Prompt giữ nguyên dữ kiện, tên nhân vật (locked_dictionary), số liệu
- [ ] REQ-05: Prompt phân biệt theo thể loại (cổ đại, hiện đại, kỳ huyễn...)
- [ ] REQ-06: Bổ sung chỉ thị xử lý xưng hô theo ngữ cảnh trong `RETRANSLATE` stage
- [ ] REQ-07: Tích hợp vào Reader AI Rewrite sheet như preset có sẵn

### Non-Functional
- [ ] Prompt ngắn gọn, tối ưu token usage
- [ ] Không ảnh hưởng pipeline dịch ban đầu (chỉ kích hoạt khi user chọn retranslate/rewrite)

## Implementation Steps

### Step 1: Bổ sung `RETRANSLATE` stage presets trong `AiPromptCatalog.kt`

1. [ ] Thêm retranslate presets chuyên biệt chuyển convert → tự nhiên:

```kotlin
// RETRANSLATE STAGE — Chuyển văn phong convert
AiPromptPreset(
    id = "retranslate_convert_reform_v1",
    taskType = TranslationPromptStage.RETRANSLATE.taskType,
    name = "Cải cách văn phong convert",
    instruction = """<retranslate_convert>
<muc_tieu>Viết lại bản dịch đang ở văn phong convert (dịch máy) sang tiếng Việt tự nhiên, mượt mà, giữ nguyên 100% dữ kiện.</muc_tieu>

<nhan_dien_convert>
Các pattern convert cần sửa:
1. Cấu trúc câu Hán-Việt ngược: "A đối với B nói" → "A nói với B"
2. Thừa chủ ngữ lặp: "Hắn X, hắn Y, hắn Z" → Rút gọn, dùng đại từ ẩn hoặc thay thế
3. Thành ngữ dịch sát chữ: Chuyển sang thành ngữ/diễn đạt Việt tương đương
4. Đại từ cứng nhắc: Điều chỉnh xưng hô theo quan hệ, tuổi tác, hoàn cảnh
5. Tả cảnh liệt kê: Thêm liên từ, biến tấu cấu trúc, tạo nhịp điệu
6. Dialogue đơn điệu: Phân biệt giọng nói từng nhân vật theo tính cách
</nhan_dien_convert>

<quy_tac>
- KHÔNG thay đổi sự kiện, nhân quả, tên riêng, địa danh, thuật ngữ locked
- KHÔNG thêm tình tiết hoặc cảm xúc không có trong nguyên tác
- KHÔNG đổi ngôi kể, điểm nhìn, thì thời gian
- Giữ nguyên tất cả segment ID, số lượng segment, thứ tự
- Ưu tiên câu ngắn, mạch lạc, tránh câu dài lê thê kiểu Hán
</quy_tac>
</retranslate_convert>""",
    enabled = false,
    builtIn = true,
    sortNumber = 100,
),
```

2. [ ] Thêm variant theo thể loại:

```kotlin
// Cổ đại / Tiên hiệp — giữ văn phong cổ nhưng tự nhiên
AiPromptPreset(
    id = "retranslate_convert_ancient_v1",
    taskType = TranslationPromptStage.RETRANSLATE.taskType,
    name = "Convert → Cổ đại tự nhiên",
    instruction = """<retranslate_ancient>
<muc_tieu>Chuyển văn phong convert sang cổ văn Việt tự nhiên.</muc_tieu>
<van_phong>
- Giữ sắc thái cổ trang: dùng từ cổ khi phù hợp (bẩm, chàng, nàng, cô nương, thiếu hiệp)
- Xưng hô theo đẳng cấp: đệ tử→sư phụ dùng "con/thầy", đồng bối dùng "sư huynh/sư đệ"
- Chiến đấu: miêu tả chiêu thức mượt mà, không liệt kê cơ học
- Nội tâm: suy nghĩ nhân vật phải có chiều sâu, không chỉ "hắn nghĩ"
- Đối thoại cung đình: trang trọng, lễ nghi đúng mực
- Đối thoại giang hồ: hào sảng, phóng khoáng
</van_phong>
<cam_ky>
- KHÔNG hiện đại hóa quá mức (tránh "OK", "chill", "vibe")
- KHÔNG dùng xưng hô hiện đại trong bối cảnh cổ đại
</cam_ky>
</retranslate_ancient>""",
    enabled = false,
    builtIn = true,
    sortNumber = 101,
),

// Hiện đại / Đô thị — văn phong đời thường
AiPromptPreset(
    id = "retranslate_convert_modern_v1",
    taskType = TranslationPromptStage.RETRANSLATE.taskType,
    name = "Convert → Hiện đại tự nhiên",
    instruction = """<retranslate_modern>
<muc_tieu>Chuyển văn phong convert sang văn xuôi hiện đại Việt Nam.</muc_tieu>
<van_phong>
- Xưng hô linh hoạt: anh/em, tôi/cậu, mày/tao tùy mức thân mật
- Hội thoại tự nhiên: thêm ngữ khí từ (à, ừ, nhỉ, thôi, đi) khi phù hợp
- Miêu tả: sống động, cụ thể, tránh abstract kiểu Hán
- Nội tâm: dùng dòng ý thức tự nhiên, không "hắn trong lòng nghĩ"
- Cảm xúc: show don't tell — miêu tả qua hành động, biểu cảm
</van_phong>
<cam_ky>
- KHÔNG dùng từ cổ khi nhân vật là người hiện đại
- KHÔNG dùng cấu trúc câu Hán-Việt (đối với X mà nói...)
</cam_ky>
</retranslate_modern>""",
    enabled = false,
    builtIn = true,
    sortNumber = 102,
),

// Kỳ huyễn phương Tây
AiPromptPreset(
    id = "retranslate_convert_western_v1",
    taskType = TranslationPromptStage.RETRANSLATE.taskType,
    name = "Convert → Kỳ huyễn phương Tây",
    instruction = """<retranslate_western>
<muc_tieu>Chuyển văn phong convert sang văn kỳ huyễn phương Tây phong cách.</muc_tieu>
<van_phong>
- Phép thuật: miêu tả huyền ảo, dùng từ vựng fantasy phương Tây (pháp sư, kỵ sĩ, rune, mana)
- Tên riêng: giữ romanized/phiên âm gốc, không Hán-Việt hóa
- Giọng kể: trang trọng nhưng hiện đại, kiểu tiểu thuyết fantasy dịch
- Đối thoại: phân biệt quý tộc (trang trọng) vs thường dân (bình dị)
- Chiến đấu: miêu tả tactical, chiến thuật rõ ràng
</van_phong>
</retranslate_western>""",
    enabled = false,
    builtIn = true,
    sortNumber = 103,
),

// Khoa huyễn / Game / Hệ thống
AiPromptPreset(
    id = "retranslate_convert_scifi_v1",
    taskType = TranslationPromptStage.RETRANSLATE.taskType,
    name = "Convert → Khoa huyễn / Game",
    instruction = """<retranslate_scifi>
<muc_tieu>Chuyển văn phong convert sang văn khoa huyễn/game system hiện đại.</muc_tieu>
<van_phong>
- System text: giữ nguyên format [Thông báo hệ thống], {Skill Name}, (Stats)
- Thuật ngữ game: dùng tiếng Việt game phổ biến (cấp độ, kinh nghiệm, boss, buff, nerf, combo)
- Nội tâm MC: suy luận logic, phân tích chiến thuật rõ ràng
- Miêu tả kỹ thuật: ngắn gọn, chính xác, không hoa mỹ thừa
- Dialogue: casual, đời thường khi ngoài combat; tactical khi chiến đấu
</van_phong>
</retranslate_scifi>""",
    enabled = false,
    builtIn = true,
    sortNumber = 104,
),
```

### Step 2: Bổ sung `REWRITE_TEXT` presets cho Reader AI Rewrite

1. [ ] **`AiPromptCatalog.kt`** — Thêm REWRITE_TEXT presets:

```kotlin
AiPromptPreset(
    id = "rewrite_convert_to_natural_v1",
    taskType = AiTaskType.REWRITE_TEXT.taskType,
    name = "Chuyển convert → Tự nhiên",
    instruction = """Viết lại đoạn văn từ văn phong convert (dịch máy) sang tiếng Việt tự nhiên.

Quy tắc:
1. Đảo cấu trúc câu Hán-Việt về thuận tiếng Việt
2. Rút gọn chủ ngữ lặp, dùng đại từ ẩn khi ngữ cảnh rõ
3. Thay thành ngữ dịch sát bằng diễn đạt Việt tương đương
4. Điều chỉnh xưng hô theo quan hệ nhân vật
5. Thêm liên từ, biến tấu nhịp câu, tránh câu dài kiểu Hán
6. Giữ nguyên 100% sự kiện, tên riêng, số liệu, thuật ngữ
7. Không thêm tình tiết, không lược bỏ chi tiết""",
    enabled = false,
    builtIn = true,
    sortNumber = 10,
),

AiPromptPreset(
    id = "rewrite_polish_dialogue_v1",
    taskType = AiTaskType.REWRITE_TEXT.taskType,
    name = "Trau chuốt hội thoại",
    instruction = """Viết lại phần hội thoại cho tự nhiên hơn.

Quy tắc:
1. Mỗi nhân vật phải có giọng nói riêng phù hợp tính cách, tuổi tác, địa vị
2. Thêm ngữ khí từ tự nhiên (à, ừ, nhỉ, chứ, thôi, đi) khi phù hợp
3. Xưng hô đúng quan hệ: sư phụ/đồ đệ, anh/em, ngươi/ta tùy bối cảnh
4. Không đổi nội dung lời nói, chỉ đổi cách diễn đạt
5. Câu thoại ngắn gọn, tự nhiên — tránh câu dài lê thê
6. Giữ nguyên tên nhân vật và thuật ngữ locked""",
    enabled = false,
    builtIn = true,
    sortNumber = 11,
),

AiPromptPreset(
    id = "rewrite_action_scenes_v1",
    taskType = AiTaskType.REWRITE_TEXT.taskType,
    name = "Nâng cấp cảnh chiến đấu",
    instruction = """Viết lại cảnh chiến đấu/hành động cho sống động, mãnh liệt hơn.

Quy tắc:
1. Thay liệt kê chiêu thức khô khan bằng miêu tả hành động liên hoàn
2. Thêm tốc độ, nhịp điệu: câu ngắn cho hành động nhanh, câu dài cho nội tâm
3. Miêu tả cảm giác vật lý: gió, lực va chạm, đau đớn, mệt mỏi
4. Giữ đúng chiêu thức, kỹ năng, thuật ngữ locked
5. Không đổi kết quả trận đấu hay sức mạnh nhân vật
6. Tránh dùng cấu trúc Hán-Việt khi miêu tả hành động""",
    enabled = false,
    builtIn = true,
    sortNumber = 12,
),
```

### Step 3: Cập nhật `ReadBookViewModel` AI Rewrite presets

1. [ ] **`ReadBookViewModel.kt`** — Thêm preset "Chuyển convert → Tự nhiên" vào danh sách preset mặc định:

```kotlin
private fun getDefaultRewritePresets(): List<AiRewritePreset> = listOf(
    AiRewritePreset(R.string.ai_rewrite_preset_polish_name, R.string.ai_rewrite_preset_polish_instruction),
    AiRewritePreset(R.string.ai_rewrite_preset_concise_name, R.string.ai_rewrite_preset_concise_instruction),
    AiRewritePreset(R.string.ai_rewrite_preset_dialogue_name, R.string.ai_rewrite_preset_dialogue_instruction),
    // NEW:
    AiRewritePreset(R.string.ai_rewrite_preset_convert_name, R.string.ai_rewrite_preset_convert_instruction),
)
```

2. [ ] **`strings.xml`** — Thêm string resources:

```xml
<string name="ai_rewrite_preset_convert_name">Chuyển văn phong convert</string>
<string name="ai_rewrite_preset_convert_instruction">Viết lại đoạn văn từ văn phong convert (dịch máy) sang tiếng Việt tự nhiên. Đảo cấu trúc câu Hán-Việt, rút gọn chủ ngữ lặp, thay thành ngữ dịch sát, điều chỉnh xưng hô theo ngữ cảnh. Giữ nguyên 100% dữ kiện.</string>
```

### Step 4: Cải thiện `DEFAULT_PROMPT` cho xử lý xưng hô context-aware

1. [ ] **`TranslationConstants.kt`** — Bổ sung rule xưng hô vào `DEFAULT_PROMPT`:

```diff
 5. Choose pronouns by genre, era, age, gender, rank, relationship, and tone. If uncertain, use names or neutral titles.
+   When pronouns_addressing is provided in the context pack, follow those pronoun rules exactly.
+   For Vietnamese: vary pronouns naturally — do not repeat the same pronoun more than 3 times consecutively.
+   Use implicit subject (zero pronoun) when context is clear, as natural Vietnamese often does.
 6. Detect genre context before choosing pronouns and terminology; do not mix ancient, modern, western fantasy, sci-fi, game, or crossover registers.
+   For convert-style input (machine-translated QT/NMT): actively restructure Sino-Vietnamese sentence patterns into natural Vietnamese word order.
```

### Step 5: Cập nhật `AiTranslationRefinePipeline.kt` retranslate stage support

1. [ ] Verify `buildSystemPrompt()` đã include `RETRANSLATE` stage instructions khi `isRetranslate = true`
2. [ ] Ensure retranslate presets từ Step 1 được inject vào system prompt khi user chọn "Dịch lại chương"

## Files to Create/Modify
- `app/src/main/java/io/legado/app/domain/model/AiPromptCatalog.kt` — Thêm RETRANSLATE + REWRITE presets
- `app/src/main/java/io/legado/app/domain/model/TranslationConstants.kt` — Cải thiện DEFAULT_PROMPT
- `app/src/main/java/io/legado/app/ui/book/read/ReadBookViewModel.kt` — Thêm rewrite preset
- `app/src/main/res/values/strings.xml` — String resources
- `app/src/main/java/io/legado/app/domain/model/AiTranslationRefinePipeline.kt` — Verify retranslate stage

## Test Criteria
- [ ] Mở cài đặt Translation Prompt → stage RETRANSLATE → hiện các preset mới (convert reform, cổ đại, hiện đại, kỳ huyễn, khoa huyễn)
- [ ] Bật preset "Cải cách văn phong convert" → dịch lại chương → output không còn cấu trúc Hán-Việt
- [ ] Mở Reader → AI Rewrite → hiện preset "Chuyển văn phong convert"
- [ ] Chọn preset + đoạn văn convert → output tự nhiên hơn, giữ đúng dữ kiện
- [ ] DEFAULT_PROMPT mới: dịch mới vẫn hoạt động bình thường, xưng hô tự nhiên hơn
- [ ] Verify CJK residue check vẫn pass cho output tiếng Việt

---
Previous Phase: Phase 08 - Story Memory Series Toggle
