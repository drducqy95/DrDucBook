# Phase 04: Database Schema, Room Cache & Viewport Async Prefetcher

Status: ⬜ Pending
Dependencies: Phase 03

## Objective
Xây dựng tầng lưu trữ bền vững (Room Database) và cơ chế phân giải bất đồng bộ thông minh theo khung nhìn (Viewport Lazy Loading), đảm bảo danh sách hiển thị mượt mà 60 FPS, không gửi request dồn dập và mở lại thư mục cũ là hiện ngay lập tức 0ms.

## Requirements

### Functional
- [ ] Room Entity `RemoteBookMetadata`:
  - `path: String` (Primary Key)
  - `title: String`
  - `author: String?`
  - `intro: String?`
  - `format: String`
  - `coverUrl: String?`
  - `fileSize: Long`
  - `lastModify: Long`
  - `lastUpdated: Long`
- [ ] DAO `RemoteBookMetadataDao`:
  - `getByPath(path: String): RemoteBookMetadata?`
  - `getAllInFolder(folderPrefix: String): List<RemoteBookMetadata>`
  - `insert(item: RemoteBookMetadata)`
  - `insertAll(items: List<RemoteBookMetadata>)`
- [ ] `RemoteBookMetadataRepository`:
  - LruCache bộ nhớ RAM (500 items).
  - Tầng 1: Đọc từ RAM cache $\rightarrow$ Tầng 2: Đọc từ Room DB $\rightarrow$ Tầng 3: Kích hoạt Range Extractor.
- [ ] Viewport-driven Prefetching:
  - Chỉ phân tích các item đang nằm trong viewport của LazyColumn/LazyVerticalGrid (hoặc trong khoảng buffer +/- 5 items).
  - Giới hạn tối đa 3 coroutines trích xuất đồng thời để tránh nghẽn mạng.

### Non-Functional
- [ ] Room Migration an toàn không làm mất dữ liệu người dùng.
- [ ] Bộ nhớ cache RAM không vượt quá 20MB.

## Implementation Steps
1. [ ] Tạo `RemoteBookMetadata.kt` entity.
2. [ ] Tạo `RemoteBookMetadataDao.kt`.
3. [ ] Cập nhật `AppDatabase.kt` thêm DAO mới.
4. [ ] Viết `RemoteBookMetadataRepository.kt` kết hợp LruCache và Semaphore giới hạn concurrency.
5. [ ] Đăng ký repository trong Koin DI (`di/appModule.kt`).

## Files to Create/Modify
- `app/src/main/java/io/legado/app/data/entities/RemoteBookMetadata.kt` [NEW]
- `app/src/main/java/io/legado/app/data/dao/RemoteBookMetadataDao.kt` [NEW]
- `app/src/main/java/io/legado/app/data/AppDatabase.kt` [MODIFY]
- `app/src/main/java/io/legado/app/data/repository/RemoteBookMetadataRepository.kt` [NEW]
- `app/src/main/java/io/legado/app/di/appModule.kt` [MODIFY]

## Test Criteria
- [ ] Dữ liệu trích xuất được lưu vào Room DB và đọc lại thành công.
- [ ] Khi duyệt lại thư mục đã xem, 100% metadata và ảnh bìa hiển thị tức thì mà không có request mạng Range nào được phát ra.
