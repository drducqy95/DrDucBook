# Phase 05: Xây Dựng Hệ Thống Dữ Liệu Wiki Nhân Vật Bách Khoa Toàn Thư (Chuẩn Thư Viện Anime - Hàn Lập)

Status: ✅ Completed
Dependencies: Phase 04

## 1. Mục tiêu
Thiết kế và bổ sung hệ thống dữ liệu bách khoa toàn thư cho nhân vật và thế giới quan theo cấu trúc bách khoa truyện tiên hiệp / huyền huyễn chuẩn mực như trang tham khảo [Hàn Lập | Wiki Tiểu sử nam chính Phàm Nhân Tu Tiên](https://thuvienanime.net/han-lap/):
1. **Mở rộng Schema Thực thể:** Bổ sung các trường chi tiết cho `AiTranslationStoryEntity`: Thân phận (Identity), Ngoại hình (Appearance), Tính cách (Personality), Tư chất / Linh căn (Aptitude), Cảnh giới tu vi (Cultivation Realm), Danh hiệu / Biệt danh (Titles), Môn phái gia nhập (Sects/Factions), Pháp bảo / Khí vật sở hữu (Artifacts), Công pháp / Kỹ năng tu luyện (Techniques), Thần thông (Supernatural powers), Linh thú (Pets/Companions).
2. **Liên kết 2 chiều động giữa Thực thể và Thiết lập Thế giới (Cross-entity Linking):** Khi xem hồ sơ nhân vật, các mục Pháp bảo, Công pháp, Thế lực hiển thị dạng thẻ/chip liên kết, bấm vào sẽ nhảy trực tiếp tới mục chi tiết tương ứng trong World Building.
3. **Giao diện Hồ Sơ Nhân Vật Chuyên Sâu (Character Dossier Screen / Sheet):** Giao diện Material 3 đẹp mắt với Header ảnh chân dung, thanh cảnh giới, các tab thông tin (Tổng quan, Thiết lập nhân vật, Năng lực & Sức mạnh, Mạng lưới quan hệ, Niên biểu cuộc đời).

## 2. Phân tích kiến trúc dữ liệu & Storage Strategy

### Cấu trúc dữ liệu mở rộng:
```kotlin
@Serializable
data class CharacterProfileDetails(
    val identity: String = "",        // Thân phận / Vai trò trong truyện
    val appearance: String = "",      // Ngoại hình, đặc điểm nhận dạng
    val personality: String = "",     // Tính cách (quả quyết, cẩn trọng, sát phạt...)
    val aptitude: String = "",        // Tư chất (Linh căn, thể chất đặc biệt)
    val realm: String = "",           // Cảnh giới tu vi cao nhất (Trúc Cơ, Nguyên Anh, Hóa Thần...)
    val titles: List<String> = emptyList(), // Danh hiệu (Hàn Chạy Chạy, Hàn Lão Ma...)
    val sect: String = "",            // Tông môn / Môn phái (Hoàng Phong Cốc, Lạc Vân Tông...)
    val artifacts: List<String> = emptyList(), // Tên các Pháp bảo (liên kết World Building)
    val techniques: List<String> = emptyList(), // Tên các Công pháp (liên kết World Building)
    val divineAbilities: List<String> = emptyList(), // Thần thông
    val spiritBeasts: List<String> = emptyList(), // Linh thú đồng hành
)
```

### ⚠️ Storage Strategy — Dùng field `metadata` (KHÔNG dùng `description`)

**Vấn đề đã phát hiện:** Nếu lưu JSON vào `description`, sẽ conflict với mô tả text thuần đã có (description đang chứa mô tả ngắn nhân vật do AI refiner sinh ra).

**Giải pháp:** Thêm field `metadata: String = ""` vào `AiTranslationStoryEntity`:
- `metadata` chứa JSON stringified của `CharacterProfileDetails`.
- `description` giữ nguyên vai trò mô tả ngắn nhân vật.
- **Backward compatibility:** `metadata` mặc định rỗng → entity cũ không bị ảnh hưởng.
- **Persistence:** `AiTranslationStoryEntity` được lưu dưới dạng JSON value trong Room DB `ai_memory` table → thêm field JSON trong value không cần DB schema migration.

```kotlin
// Ví dụ parse
val profile: CharacterProfileDetails? = entity.metadata
    .takeIf { it.startsWith("{") }
    ?.let { Json.decodeFromString<CharacterProfileDetails>(it) }

// Ví dụ serialize
val updatedEntity = entity.copy(
    metadata = Json.encodeToString(profileDetails)
)
```

## 3. Các bước thực hiện
1. **Nâng cấp Model Domain (`AiTranslationStoryMemory.kt`):**
   - Thêm field `metadata: String = ""` vào `AiTranslationStoryEntity`.
   - Định nghĩa `CharacterProfileDetails` data class với `@Serializable`.
   - Thêm extension functions:
     - `AiTranslationStoryEntity.characterProfile(): CharacterProfileDetails?` — parse metadata
     - `AiTranslationStoryEntity.withCharacterProfile(profile: CharacterProfileDetails): AiTranslationStoryEntity` — serialize metadata
   - Bổ sung `entityRefs` và `characterRefs` để hỗ trợ liên kết 2 chiều giữa Entity và World Building.
2. **Xây dựng UI `CharacterDossierSheet` / `CharacterDetailScreen`:**
   - **Header Section:**
     - Ảnh chân dung minh họa (`AsyncImage` do AI sinh hoặc tải lên).
     - Tên tiếng Việt (Hán Việt), Tên tiếng Trung gốc, Pinyin / Biệt hiệu.
     - Chip Cảnh giới tu vi (Badge nổi bật), Tông môn hiện tại.
     - Danh sách Biệt danh dạng Tag chips.
   - **Tab 1: Tổng quan & Thiết lập (Overview & Profile):**
     - Thân phận, Xuất thân.
     - Ngoại hình & Tính cách.
     - Tư chất linh căn.
   - **Tab 2: Năng lực & Khí vật (Power & Inventory):**
     - Danh sách Pháp bảo / Khí vật (Clickable -> mở chi tiết World Building).
     - Danh sách Công pháp & Thần thông (Clickable -> mở chi tiết World Building).
     - Linh thú / Đồng hành.
   - **Tab 3: Quan hệ nhân mạch (Relationships):**
     - Hiển thị danh sách các nhân vật có quan hệ: Bạn đời, Sư phụ, Huynh đệ, Kẻ thù... Bấm vào nhân vật nào nhảy sang hồ sơ nhân vật đó!
   - **Tab 4: Niên biểu cuộc đời (Timeline):**
     - Các sự kiện và chương xuất hiện nổi bật của nhân vật này trong mạch truyện.
3. **Cập nhật Trình biên tập thủ công (`StoryMemoryEditorDialog`):**
   - Hỗ trợ nhập liệu đầy đủ các trường trên khi người dùng muốn tự tay xây dựng hoặc chỉnh sửa hồ sơ nhân vật.
   - Tách thành collapsible sections để form không quá dài.

## 4. Files ảnh hưởng
- `app/src/main/java/io/legado/app/domain/model/AiTranslationStoryMemory.kt`
- `app/src/main/java/io/legado/app/ui/translation/memory/BookStoryMemoryContract.kt`
- `app/src/main/java/io/legado/app/ui/translation/memory/BookStoryMemoryScreen.kt`
- `app/src/main/java/io/legado/app/ui/translation/memory/StoryWikiScreen.kt`
- `app/src/main/java/io/legado/app/ui/translation/memory/CharacterDossierSheet.kt` (MỚI)

## 5. Tiêu chuẩn nghiệm thu
- [ ] Bấm vào một nhân vật (ví dụ "Triệu Kỳ" trong *Chủ Thần Đại Đạo*): Mở hồ sơ nhân vật chuyên sâu đầy đủ các trường thiết lập, năng lực, quan hệ và niên biểu.
- [ ] Bấm vào một pháp bảo trong hồ sơ nhân vật: Tự động điều hướng đến mục chi tiết của pháp bảo đó trong World Building.
- [ ] Dữ liệu lưu trữ đồng bộ và an toàn, không làm hỏng cấu trúc memory hiện có.
- [ ] Entity cũ (không có `metadata`) vẫn hiển thị bình thường, không crash.
- [ ] Sửa profile qua editor → reload → dữ liệu giữ nguyên (round-trip serialize/deserialize).
