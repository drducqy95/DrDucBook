# Phase 02b: Tối Ưu Hiển Thị UI Form QuickDictionary (UI Compaction)

Status: ✅ Complete
Dependencies: Phase 02

## 1. Mục tiêu
Tối ưu hiển thị form "Thêm từ điển QT" (`QuickDictionaryForm.kt`) bằng cách:
1. Chuyển các control chọn mục (Type, Scope, Universe, Provider, Memory Category) từ `FlowRow + FilterChip` sang **`ExposedDropdownMenuBox`** (Material 3 dropdown).
2. Thu gọn block hiển thị để hạn chế scroll dọc.
3. Compaction layout tổng thể — tiết kiệm ~250-300dp chiều cao so với layout hiện tại.

## 2. Phân tích hiện trạng code

### Vấn đề 1: Type selector chiếm quá nhiều không gian dọc
```kotlin
// QuickDictionaryForm.kt: lines 296-304
FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    quickDictionaryVisibleTypes.forEach { type ->
        FilterChip(
            selected = type == state.type,
            onClick = { onTypeChange(type) },
            label = { AppText(stringResource(type.labelResource())) },
        )
    }
}
```
6 FilterChips (NAME, VIETPHRASE, PHONETIC, PRONOUN, LUAT_NHAN, IGNORE) xếp trong FlowRow → wrap 2 dòng → **~80dp chiều cao**.

### Vấn đề 2: Scope selector + Universe sub-section
```kotlin
// QuickDictionaryForm.kt: lines 310-318 + 319-357
FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    QuickDictionaryScope.entries.forEach { scope -> FilterChip(...) }
}
// + Universe FilterChips (chọn universe), universe name field, context markers field
```
3 FilterChips + Universe sub-section khi mở rộng: **~250dp+**

### Vấn đề 3: Translation Provider chips
```kotlin
// QuickDictionaryForm.kt: lines 258-268
FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    state.providerOptions.forEach { provider -> FilterChip(...) }
}
```
5 Provider chips (QT, NMT, AI, ML_KIT, HAN_VIET) → FlowRow wrap → **~96dp** (label + 2 rows)

### Vấn đề 4: Selection Tools section header thừa
```kotlin
// QuickDictionaryForm.kt: lines 161-163
AppText(
    text = stringResource(R.string.quick_dictionary_selection_tools),
    style = LegadoTheme.typography.titleSmall,
)
```
Section header "Công cụ chọn vùng" + 4 buttons → **~56dp** (có thể bỏ header, giữ buttons)

## 3. Các bước thực hiện

### 3.1 Type Selector → ExposedDropdownMenuBox
```kotlin
// TRƯỚC: FlowRow + 6 FilterChips (~80dp, 2 rows)
// SAU: ExposedDropdownMenuBox (~56dp, 1 dropdown)
var typeExpanded by remember { mutableStateOf(false) }
ExposedDropdownMenuBox(
    expanded = typeExpanded,
    onExpandedChange = { typeExpanded = it },
) {
    OutlinedTextField(
        value = stringResource(state.type.labelResource()),
        onValueChange = {},
        readOnly = true,
        label = { AppText(stringResource(R.string.quick_dictionary_type)) },
        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(typeExpanded) },
        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        singleLine = true,
    )
    ExposedDropdownMenu(expanded = typeExpanded, onDismissRequest = { typeExpanded = false }) {
        quickDictionaryVisibleTypes.forEach { type ->
            DropdownMenuItem(
                text = { AppText(stringResource(type.labelResource())) },
                onClick = { onTypeChange(type); typeExpanded = false },
            )
        }
    }
}
```

### 3.2 Scope Selector → Dropdown + Collapsible Universe Section
```kotlin
// TRƯỚC: FlowRow + 3 FilterChips + inline universe form (~250dp khi mở rộng)
// SAU: Dropdown (~56dp) + AnimatedVisibility cho universe details
var scopeExpanded by remember { mutableStateOf(false) }
ExposedDropdownMenuBox(expanded = scopeExpanded, onExpandedChange = { scopeExpanded = it }) {
    OutlinedTextField(
        value = stringResource(state.scope.labelResource()),
        readOnly = true,
        label = { AppText(stringResource(R.string.quick_dictionary_scope)) },
        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(scopeExpanded) },
        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        singleLine = true,
    )
    ExposedDropdownMenu(expanded = scopeExpanded, onDismissRequest = { scopeExpanded = false }) {
        QuickDictionaryScope.entries.forEach { scope ->
            DropdownMenuItem(
                text = { AppText(stringResource(scope.labelResource())) },
                onClick = { onScopeChange(scope); scopeExpanded = false },
            )
        }
    }
}
// Universe details only expand when scope == UNIVERSE
AnimatedVisibility(visible = state.scope == QuickDictionaryScope.UNIVERSE) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Universe picker ALSO as dropdown (instead of FilterChips)
        ExposedDropdownMenuBox(...) { /* available universes */ }
        OutlinedTextField(value = state.universeName, ...)
        OutlinedTextField(value = state.contextMarkers, ...)
    }
}
```

### 3.3 Provider Suggestion → Inline Dropdown
```kotlin
// TRƯỚC: Section label + FlowRow + 5 FilterChips → ~96dp
// SAU: Row(label + dropdown) → ~56dp
Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
) {
    AppText(
        text = stringResource(R.string.quick_dictionary_translation_provider),
        style = LegadoTheme.typography.titleSmall,
        modifier = Modifier.weight(1f),
    )
    ExposedDropdownMenuBox(...) {
        OutlinedTextField(
            value = providerLabel(state.selectedProvider),
            readOnly = true,
            ...
        )
        ExposedDropdownMenu(...) {
            state.providerOptions.forEach { provider -> DropdownMenuItem(...) }
        }
    }
}
```

### 3.4 Memory Category → Dropdown (khi saveToTranslationMemory == true)
```kotlin
// Thay vì thêm 7 FilterChips mới (~80dp), dùng Dropdown (~56dp)
AnimatedVisibility(visible = state.saveToTranslationMemory) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ExposedDropdownMenuBox(...) {
            OutlinedTextField(
                value = state.memoryCategory.displayName(),
                label = { AppText(stringResource(R.string.quick_dictionary_memory_category)) },
                ...
            )
            ExposedDropdownMenu(...) {
                StoryMemoryCategory.entries.forEach { cat ->
                    DropdownMenuItem(
                        text = { AppText("${cat.icon} ${cat.displayName()}") },
                        onClick = { onMemoryCategoryChange(cat); expanded = false },
                    )
                }
            }
        }
        OutlinedTextField(
            value = state.memoryDescription,
            onValueChange = onMemoryDescriptionChange,
            label = { AppText(stringResource(R.string.quick_dictionary_memory_description)) },
            singleLine = false,
            minLines = 2,
        )
    }
}
```

### 3.5 Selection Tools — Compact (bỏ section header)
```kotlin
// Loại bỏ section title "Công cụ chọn vùng" — 4 icon buttons tự giải thích
if (state.hasSelectionControls) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 4 SelectionIconButtons — không section header, giảm ~16dp
    }
}
```

### 3.6 Restructure Advanced section
- Di chuyển **Type** và **Scope** dropdown ra **ngoài** advanced section (vì thường xuyên sử dụng).
- Chỉ giữ **Universe details**, **Priority summary** và **Translation Memory** checkbox+category trong advanced.

## 4. Tổng kết cải thiện

| Control | Trước (FilterChip) | Sau (Dropdown) | Tiết kiệm |
|---|---|---|---|
| Type (6 items) | ~80dp (2 rows) | ~56dp (1 dropdown) | **24dp** |
| Scope (3 items) | ~40dp + ~200dp universe | ~56dp + collapsible | **~150dp** khi universe |
| Provider (5 items) | ~96dp (label + 2 rows) | ~56dp (inline) | **40dp** |
| Memory Category (7 items) | N/A (mới) | ~56dp dropdown | Tránh thêm **~80dp** |
| Selection header | ~16dp | Removed | **16dp** |
| **Tổng** | | | **~250-300dp** giảm scroll |

## 5. Files ảnh hưởng
- `app/src/main/java/io/legado/app/ui/quickdict/QuickDictionaryForm.kt`
- `app/src/main/java/io/legado/app/ui/quickdict/QuickDictionaryContract.kt`
- `app/src/main/res/values/strings.xml` & `app/src/main/res/values-vi/strings.xml`

## 6. Tiêu chuẩn nghiệm thu
- [ ] Form QT hiển thị gọn gàng, không cần scroll để thấy nút Save khi bàn phím đóng.
- [ ] Type dropdown: Mở ra 6 mục, chọn được, hiển thị đúng tên type đã chọn.
- [ ] Scope dropdown: Mở ra 3 mục, chọn Universe → hiện sub-form mượt (AnimatedVisibility).
- [ ] Provider dropdown: Chọn provider → tự động gọi suggestion.
- [ ] Memory Category dropdown: Chỉ hiện khi tick "Lưu bộ nhớ dịch", 7 mục có icon + tên.
- [ ] Advanced section thu gọn tốt, mở rộng không gây giật.
