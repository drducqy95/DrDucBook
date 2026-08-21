# Phase 08: Story Memory Series Toggle

Status: ✅ Complete
Dependencies: Phase 06, Phase 07

## Objective

Mỗi sách giữ bộ nhớ dịch (story memory) riêng biệt. Bổ sung toggle "Kế thừa bộ nhớ bộ truyện" cho phép sách trong cùng BookGroup có thể kế thừa entity, relationship, world building từ các sách khác trong series.

## Root Cause Analysis

### Hiện trạng

| Layer | Cách hoạt động | Hạn chế |
|---|---|---|
| `AiMemory` entity | `scope=book, scopeId=bookUrl` | 1:1 per book, không có concept group |
| `AiMemoryDao` | `getByScope(scope, scopeId)` | Chỉ query 1 scopeId |
| `TranslationStoryMemoryUseCase` | `loadSnapshot(bookUrl)` | Single book snapshot |
| `prepareForTranslation` | Context từ 1 book | Volume 2 không thấy entity Volume 1 |
| Dashboard UI | Flat book selector | Không group theo series |

### Legado BookGroup system

- Table `book_groups` (`groupId: Long`, `groupName: String`)
- `Book.group: Long` — bitmask, query: `(books.'group' & :groupId) > 0`
- Custom groups có `groupId` là bit flags (1, 2, 4, 8...)

## Requirements

### Functional
- [ ] REQ-01: Mỗi sách vẫn có bộ nhớ dịch riêng (mặc định, không thay đổi behavior hiện tại)
- [ ] REQ-02: Bổ sung toggle "Kế thừa bộ nhớ bộ truyện" trong cài đặt sách
- [ ] REQ-03: Khi toggle bật, khi dịch sách A, hệ thống load thêm entity/relationship từ các sách cùng group
- [ ] REQ-04: Entity kế thừa KHÔNG ghi đè entity riêng của sách (priority: sách hiện tại > sách kế thừa)
- [ ] REQ-05: Story memory vẫn chỉ PERSIST vào scopeId = bookUrl của sách hiện tại
- [ ] REQ-06: Dashboard UI group sách theo BookGroup
- [ ] REQ-07: Dashboard hỗ trợ xem bộ nhớ dịch tổng hợp của toàn bộ series

### Non-Functional
- [ ] Performance: Batch query thay vì N+1 queries khi load series memory
- [ ] Không thay đổi schema AiMemory entity (backward-compatible)

## Implementation Steps

### Step 1: Data Layer — Batch scope queries

1. [ ] **`AiMemoryDao.kt`** — Thêm batch scope query:

```kotlin
@Query("SELECT * FROM ai_memory WHERE scope = :scope AND scopeId IN (:scopeIds) ORDER BY pinned DESC, updatedAt DESC")
suspend fun getByScopeIds(scope: String, scopeIds: List<String>): List<AiMemory>

@Query("SELECT * FROM ai_memory WHERE scope = :scope AND scopeId IN (:scopeIds) ORDER BY pinned DESC, updatedAt DESC")
fun observeByScopeIds(scope: String, scopeIds: List<String>): Flow<List<AiMemory>>
```

2. [ ] **`AiMemoryGateway.kt`** — Thêm interface method:

```kotlin
suspend fun getByScopeIds(scope: String, scopeIds: List<String>): List<AiMemory>
fun observeByScopeIds(scope: String, scopeIds: List<String>): Flow<List<AiMemory>>
```

3. [ ] **`AiMemoryRepository.kt`** — Implement:

```kotlin
override suspend fun getByScopeIds(scope: String, scopeIds: List<String>) =
    aiMemoryDao.getByScopeIds(scope, scopeIds)

override fun observeByScopeIds(scope: String, scopeIds: List<String>) =
    aiMemoryDao.observeByScopeIds(scope, scopeIds)
```

### Step 2: Book entity — Series memory toggle

1. [ ] **`Book.kt`** — Thêm field:

```kotlin
@ColumnInfo(defaultValue = "0")
var inheritSeriesMemory: Boolean = false
```

2. [ ] **Room migration** — Tăng schema version, thêm column:

```kotlin
val MIGRATION_XXX_XXX = object : Migration(currentVersion, currentVersion + 1) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE books ADD COLUMN inheritSeriesMemory INTEGER NOT NULL DEFAULT 0")
    }
}
```

### Step 3: Domain Layer — Series-aware snapshot

1. [ ] **`TranslationStoryMemoryUseCase.kt`** — Thêm series snapshot loading:

```kotlin
/**
 * Load snapshot cho sách hiện tại, có thể kế thừa từ sách cùng series.
 * Entity riêng của book luôn có priority cao hơn entity kế thừa.
 */
suspend fun loadSnapshotWithSeriesInheritance(
    book: Book,
): AiTranslationStoryMemorySnapshot {
    val ownSnapshot = loadSnapshot(book.bookUrl)
    
    if (!book.inheritSeriesMemory || book.group == 0L) {
        return ownSnapshot
    }
    
    // Find sibling books in same group
    val siblingBooks = appDb.bookDao.getBooksByGroup(book.group)
        .filter { it.bookUrl != book.bookUrl }
    if (siblingBooks.isEmpty()) return ownSnapshot
    
    val siblingUrls = siblingBooks.map { it.bookUrl }
    val siblingMemories = aiMemoryGateway.getByScopeIds(AiMemory.SCOPE_BOOK, siblingUrls)
    val siblingSnapshot = siblingMemories.toStorySnapshot()
    
    // Merge: own entities override sibling entities (by raw.lowercase())
    return mergeSnapshots(primary = ownSnapshot, inherited = siblingSnapshot)
}

private fun mergeSnapshots(
    primary: AiTranslationStoryMemorySnapshot,
    inherited: AiTranslationStoryMemorySnapshot,
): AiTranslationStoryMemorySnapshot {
    val ownEntityKeys = primary.entities.map { it.raw.lowercase() }.toSet()
    val inheritedEntities = inherited.entities.filter { 
        it.raw.lowercase() !in ownEntityKeys 
    }
    
    val ownRelKeys = primary.relationships.map { 
        "${it.source.lowercase()}|${it.target.lowercase()}|${it.relationship.lowercase()}" 
    }.toSet()
    val inheritedRels = inherited.relationships.filter {
        "${it.source.lowercase()}|${it.target.lowercase()}|${it.relationship.lowercase()}" !in ownRelKeys
    }
    
    return primary.copy(
        entities = primary.entities + inheritedEntities,
        relationships = primary.relationships + inheritedRels,
        worldBuilding = (primary.worldBuilding + inherited.worldBuilding).distinctBy { 
            "${it.category.lowercase()}|${it.raw.lowercase()}" 
        },
        // Timelines: only keep own book's timelines
    )
}
```

2. [ ] **`prepareForTranslation()`** — Sử dụng `loadSnapshotWithSeriesInheritance()`:

```kotlin
val snapshot = if (book.inheritSeriesMemory) {
    loadSnapshotWithSeriesInheritance(book)
} else {
    loadSnapshot(book.bookUrl)
}
```

### Step 4: WebService API — Group support

1. [ ] **`WebServiceTranslationJobController.kt`** — Extend `getStoryMemory()`:

```kotlin
suspend fun getStoryMemory(
    bookUrlValue: String?,
    groupIdValue: String?,
): WebServiceStoryMemorySummaryResponse {
    if (groupIdValue != null) {
        val groupId = groupIdValue.toLongOrNull() 
            ?: throw IllegalArgumentException("INVALID_GROUP_ID")
        val books = appDb.bookDao.getBooksByGroup(groupId)
        val bookUrls = books.map { it.bookUrl }
        val memories = aiMemoryGateway.getByScopeIds(AiMemory.SCOPE_BOOK, bookUrls)
        val snapshot = memories.toStorySnapshot()
        // Map to response DTOs...
        return WebServiceStoryMemorySummaryResponse(
            bookUrl = "group:$groupId",
            entities = ...,
            relationships = ...,
        )
    }
    // Existing single-book logic
    ...
}
```

2. [ ] **`KtorServer.kt`** — Thêm `groupId` query param:

```kotlin
get("/api/v2/translation/memory/story") {
    val bookUrl = call.request.queryParameters["bookUrl"]
    val groupId = call.request.queryParameters["groupId"]
    ...
}
```

3. [ ] **`KtorServer.kt`** — Thêm endpoint lấy BookGroup list:

```kotlin
get("/api/v2/bookshelf/groups") {
    val groups = appDb.bookGroupDao.all
    call.respond(groups.map { WebServiceBookGroupResponse(it.groupId, it.groupName) })
}
```

### Step 5: Frontend — Grouped book selector

1. [ ] **`webService.ts`** — Thêm API functions:

```typescript
export const getBookGroups = async () => { ... }
export const getStoryMemoryForGroup = async (groupId: number) => { ... }
```

2. [ ] **`TranslationDashboard.vue`** — Story Memory tab cải tiến:

```html
<!-- Group selector -->
<el-select v-model="selectedMemoryGroup" @change="onGroupChange" clearable>
  <el-option v-for="g in bookGroups" :key="g.groupId" :value="g.groupId"
             :label="'📚 ' + g.groupName" />
</el-select>

<!-- Book selector grouped -->
<el-select v-model="selectedMemoryBookUrl" filterable>
  <el-option-group v-for="group in groupedBooks" :key="group.id" :label="group.name">
    <el-option v-for="book in group.books" :key="book.bookUrl"
               :label="book.name" :value="book.bookUrl" />
  </el-option-group>
</el-select>

<!-- Toggle: view aggregated series memory -->
<el-switch v-model="viewSeriesMemory" :active-text="t('viewSeriesMemory')" />
```

3. [ ] **`i18n.ts`** — Thêm keys:

```typescript
viewSeriesMemory: 'Xem bộ nhớ toàn series',
bookGroup: 'Nhóm sách',
inheritSeriesMemory: 'Kế thừa bộ nhớ bộ truyện',
```

### Step 6: Native UI — Toggle in Book settings

1. [ ] Thêm toggle "Kế thừa bộ nhớ bộ truyện" trong Book Info / Settings screen
2. [ ] Toggle lưu vào `Book.inheritSeriesMemory` qua Room

## Files to Create/Modify
- `app/src/main/java/io/legado/app/data/dao/AiMemoryDao.kt` — Batch queries
- `app/src/main/java/io/legado/app/domain/gateway/AiMemoryGateway.kt` — Interface
- `app/src/main/java/io/legado/app/data/repository/AiMemoryRepository.kt` — Implementation
- `app/src/main/java/io/legado/app/data/entities/Book.kt` — `inheritSeriesMemory` field
- `app/src/main/java/io/legado/app/data/AppDatabase.kt` — Migration
- `app/src/main/java/io/legado/app/domain/usecase/TranslationStoryMemoryUseCase.kt` — Series merge
- `app/src/main/java/io/legado/app/web/WebServiceTranslationJobController.kt` — Group API
- `app/src/main/java/io/legado/app/web/KtorServer.kt` — Routes
- `modules/web/src/views/TranslationDashboard.vue` — Grouped selector
- `modules/web/src/api/webService.ts` — API functions
- `modules/web/src/i18n.ts` — Translations

## Test Criteria
- [ ] Mặc định: toggle OFF → dịch sách A chỉ dùng memory sách A
- [ ] Toggle ON + sách thuộc group → dịch sách B thấy entity từ sách A cùng group
- [ ] Entity sách B override entity kế thừa từ sách A (cùng raw name)
- [ ] Persist story memory vẫn chỉ ghi vào scopeId = bookUrl sách hiện tại
- [ ] Dashboard: group selector hiện danh sách BookGroup
- [ ] Dashboard: chọn xem series memory → hiện tổng hợp entity từ tất cả sách trong group
- [ ] Sách không thuộc group nào → toggle không ảnh hưởng (vẫn chỉ memory riêng)

---
Next Phase: Phase 09 - AI Rewrite Prompt Enhancement
