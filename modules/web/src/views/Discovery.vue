<template>
  <div class="discovery-page">
    <section class="discovery-hero glass-panel">
      <div>
        <span class="eyebrow">{{ t('discovery') }}</span>
        <h1>{{ t('discoveryTitle') }}</h1>
        <p>{{ t('discoveryDescription') }}</p>
      </div>
      <el-button type="primary" :loading="loading && page === 1" @click="refreshDiscovery">
        {{ t('refreshDiscovery') }}
      </el-button>
    </section>

    <!-- Source Selection, Category Dropdown & Type Filters -->
    <section class="discovery-controls glass-panel">
      <div class="control-row">
        <!-- Source Selector -->
        <div class="selector-item">
          <label class="control-label">{{ t('selectSource') }}</label>
          <el-select
            v-model="selectedSource"
            filterable
            :loading="sourcesLoading"
            :placeholder="t('selectSource')"
            class="control-select"
            @change="onSourceChange"
          >
            <el-option
              v-for="source in sources"
              :key="source.sourceUrl"
              :label="displaySource(source.name)"
              :value="source.sourceUrl"
            >
              <div class="source-option">
                <span>{{ displaySource(source.name) }}</span>
                <span v-if="source.group" class="source-group-tag">{{ source.group }}</span>
              </div>
            </el-option>
          </el-select>
        </div>

        <!-- Category Dropdown Selector -->
        <div class="selector-item">
          <label class="control-label">{{ t('selectCategory') }}</label>
          <el-select
            v-model="selectedKindUrl"
            filterable
            :loading="kindsLoading"
            :placeholder="t('selectCategory')"
            class="control-select"
            :disabled="!selectedSource"
            @change="onKindSelectChange"
          >
            <el-option
              :label="t('allCategories')"
              value=""
            />
            <el-option
              v-for="kind in urlKinds"
              :key="kind.url || kind.title"
              :label="displayKind(kind.title)"
              :value="kind.url || ''"
            />
          </el-select>
          <el-button
            v-if="selectedSource"
            circle
            size="small"
            class="refresh-kinds-btn"
            :loading="kindsLoading"
            title="Làm mới danh mục"
            @click="reloadKinds"
          >
            <el-icon><Refresh /></el-icon>
          </el-button>
        </div>

        <!-- Type Filter Tabs -->
        <div class="type-filter-wrap">
          <el-tabs v-model="typeFilter" @tab-change="onTypeFilterChange">
            <el-tab-pane :label="t('allTypes')" name="" />
            <el-tab-pane :label="t('textBooks')" name="text" />
            <el-tab-pane :label="t('comics')" name="image" />
            <el-tab-pane :label="t('audio')" name="audio" />
            <el-tab-pane :label="t('video')" name="video" />
          </el-tabs>
        </div>
      </div>

      <!-- Additional Dynamic Filter Kinds (Select, Toggle, Text) -->
      <div v-if="filterKinds.length > 0" class="filter-kinds-bar">
        <div v-for="kind in filterKinds" :key="kind.title" class="filter-item">
          <label class="filter-label">{{ displayKind(kind.title) }}</label>

          <!-- Select Type -->
          <el-select
            v-if="kind.type === 'select'"
            v-model="kindValues[kind.title]"
            size="small"
            class="filter-control"
            @change="onKindValueChange"
          >
            <el-option
              v-for="opt in kind.chars"
              :key="opt"
              :label="displayKind(opt)"
              :value="opt"
            />
          </el-select>

          <!-- Toggle Type -->
          <el-switch
            v-else-if="kind.type === 'toggle'"
            v-model="kindValues[kind.title]"
            active-value="1"
            inactive-value="0"
            size="small"
            @change="onKindValueChange"
          />

          <!-- Text Type -->
          <el-input
            v-else-if="kind.type === 'text'"
            v-model="kindValues[kind.title]"
            size="small"
            class="filter-control"
            :placeholder="t('kindInput')"
            clearable
            @change="onKindValueChange"
          />
        </div>
      </div>
    </section>

    <!-- Books Results Section -->
    <section class="results-section">
      <div v-if="loading && page === 1" class="empty-panel glass-panel">{{ t('waiting') }}</div>
      <div v-else-if="error" class="empty-panel glass-panel">{{ t('discoveryLoadFailed') }}</div>
      <div v-else-if="!items.length" class="empty-panel glass-panel">{{ t('noDiscoveryData') }}</div>
      <div v-else>
        <div class="discovery-grid">
          <article
            v-for="book in items"
            :key="`${book.bookUrl}-${book.originName}`"
            class="discovery-card glass-panel"
            @click="openBook(book)"
          >
            <img :src="coverUrl(book)" alt="" loading="lazy" @error="onCoverError" />
            <div class="book-info">
              <h3>{{ displayDynamic(book.name) }}</h3>
              <p class="author">{{ displayDynamic(book.author) }}</p>
              <p v-if="book.kind" class="kind-tag">{{ displayDynamic(book.kind) }}</p>
              <span class="source-tag">{{ book.originName ? displayDynamic(book.originName) : t('sourceName') }}</span>
            </div>
          </article>
        </div>

        <!-- Pagination / Load More -->
        <div class="load-more-section">
          <el-button
            v-if="hasMore"
            type="primary"
            plain
            class="load-more-btn"
            :loading="loadingMore"
            @click="loadMore"
          >
            {{ loadingMore ? t('loadingMore') : t('loadMore') }}
          </el-button>
          <span v-else class="no-more-text muted">{{ t('noMoreBooks') }}</span>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { Refresh } from '@element-plus/icons-vue'
import API, { getDiscoveryHome } from '@api'
import {
  getWebServiceDiscoveryKinds,
  getWebServiceDiscoverySources,
  patchWebServiceDiscoveryKinds,
  type WebServiceDiscoveryKind,
  type WebServiceDiscoverySource,
} from '@/api/webService'
import type { SeachBook } from '@/book'
import { isLegadoUrl } from '@/utils/utils'
import { dynamicText, t, translateDynamicTexts, webLocale } from '@/i18n'

const router = useRouter()
const typeFilter = ref('')
const loading = ref(false)
const loadingMore = ref(false)
const error = ref(false)
const sourcesLoading = ref(false)
const kindsLoading = ref(false)
const page = ref(1)
const hasMore = ref(true)

const items = ref<SeachBook[]>([])
const sources = ref<WebServiceDiscoverySource[]>([])
const selectedSource = ref('')
const kinds = ref<WebServiceDiscoveryKind[]>([])
const selectedKindUrl = ref('')
const kindValues = ref<Record<string, string>>({})

const bookType = { audio: 32, video: 4 }

const currentSourceName = computed(() => {
  const source = sources.value.find(s => s.sourceUrl === selectedSource.value)
  return source?.name ? displaySource(source.name) : t('discoverySourcesTitle')
})

const urlKinds = computed(() =>
  kinds.value.filter(k => !k.type || k.type === 'url' || k.url),
)

const filterKinds = computed(() =>
  kinds.value.filter(k => k.type === 'select' || k.type === 'toggle' || k.type === 'text'),
)

const displayDynamic = (value: string | null | undefined) => dynamicText('discovery', value)
const displaySource = (value: string | null | undefined) => dynamicText('sources', value)
const displayKind = (value: string | null | undefined) => dynamicText('kinds', value)

const coverUrl = (book: SeachBook) =>
  book.coverUrl && !isLegadoUrl(book.coverUrl)
    ? book.coverUrl
    : API.getProxyCoverUrl(book.coverUrl || book.bookUrl)

const onCoverError = (event: Event) => {
  ;(event.target as HTMLImageElement).src = '/vue/favicon.ico'
}

const loadSources = async () => {
  sourcesLoading.value = true
  try {
    const list = await getWebServiceDiscoverySources()
    sources.value = list.filter(s => s.enabled)
    if (!selectedSource.value && sources.value.length > 0) {
      const preferred = sources.value.find(s => s.selectedForWeb) || sources.value[0]
      selectedSource.value = preferred.sourceUrl
      await loadKindsForSource(preferred.sourceUrl)
    }
  } catch {
    sources.value = []
  } finally {
    sourcesLoading.value = false
  }
}

const loadKindsForSource = async (sourceUrl: string) => {
  if (!sourceUrl) {
    kinds.value = []
    selectedKindUrl.value = ''
    kindValues.value = {}
    return
  }
  kindsLoading.value = true
  try {
    const res = await getWebServiceDiscoveryKinds(sourceUrl)
    kinds.value = res.kinds || []
    selectedKindUrl.value = ''
    const initialValues: Record<string, string> = {}
    for (const kind of kinds.value) {
      initialValues[kind.title] = kind.currentValue || kind.defaultValue || ''
    }
    kindValues.value = initialValues
  } catch {
    kinds.value = []
    selectedKindUrl.value = ''
    kindValues.value = {}
  } finally {
    kindsLoading.value = false
  }
}

const onSourceChange = async (sourceUrl: string) => {
  page.value = 1
  items.value = []
  hasMore.value = true
  await loadKindsForSource(sourceUrl)
  await loadDiscovery(false)
}

const reloadKinds = async () => {
  if (!selectedSource.value) return
  await loadKindsForSource(selectedSource.value)
  await loadDiscovery(true)
}

const onKindSelectChange = async (url: string) => {
  selectedKindUrl.value = url
  page.value = 1
  items.value = []
  hasMore.value = true
  await loadDiscovery(false)
}

const onKindValueChange = async () => {
  if (!selectedSource.value) return
  try {
    await patchWebServiceDiscoveryKinds(selectedSource.value, kindValues.value)
    page.value = 1
    items.value = []
    hasMore.value = true
    await loadDiscovery(false)
  } catch {
    ElMessage.error('Không thể cập nhật giá trị lọc')
  }
}

const onTypeFilterChange = () => {
  page.value = 1
  items.value = []
  hasMore.value = true
  void loadDiscovery(false)
}

const loadDiscovery = async (refresh = false) => {
  if (!selectedSource.value) return
  loading.value = true
  error.value = false
  page.value = 1
  try {
    const response = await getDiscoveryHome({
      sourceUrl: selectedSource.value,
      exploreUrl: selectedKindUrl.value || undefined,
      type: typeFilter.value || undefined,
      page: 1,
      limit: 24,
      refresh,
    })
    items.value = response.items || []
    hasMore.value = (response.items || []).length >= 24
    error.value = response.sourceErrors > 0 && items.value.length === 0
  } catch {
    error.value = true
    items.value = []
  } finally {
    loading.value = false
  }
}

const loadMore = async () => {
  if (!selectedSource.value || loadingMore.value || !hasMore.value) return
  loadingMore.value = true
  const nextPage = page.value + 1
  try {
    const response = await getDiscoveryHome({
      sourceUrl: selectedSource.value,
      exploreUrl: selectedKindUrl.value || undefined,
      type: typeFilter.value || undefined,
      page: nextPage,
      limit: 24,
      refresh: false,
    })
    const newItems = response.items || []
    if (newItems.length > 0) {
      // Deduplicate
      const existingUrls = new Set(items.value.map(b => b.bookUrl))
      const uniqueNew = newItems.filter(b => !existingUrls.has(b.bookUrl))
      items.value = [...items.value, ...uniqueNew]
      page.value = nextPage
      hasMore.value = newItems.length >= 24
    } else {
      hasMore.value = false
    }
  } catch {
    ElMessage.warning('Không thể tải thêm sách')
  } finally {
    loadingMore.value = false
  }
}

const refreshDiscovery = () => loadDiscovery(true)

const openBook = async (book: SeachBook) => {
  try {
    const response = await API.saveBook(book)
    if (!response.data.isSuccess) throw new Error(response.data.errorMsg || 'BOOK_SAVE_FAILED')
    const isMedia = Boolean(book.type & (bookType.audio | bookType.video))
    sessionStorage.setItem('bookUrl', book.bookUrl)
    sessionStorage.setItem('bookName', book.name)
    sessionStorage.setItem('bookAuthor', book.author)
    sessionStorage.setItem('chapterIndex', '0')
    sessionStorage.setItem('chapterPos', '0')
    sessionStorage.setItem('isSeachBook', 'true')
    await router.push({ name: isMedia ? 'media' : 'chapter' })
  } catch (cause) {
    ElMessage.error(cause instanceof Error ? cause.message : t('bookSaveFailed'))
  }
}

// Watchers for dynamic translation
watch(
  [() => sources.value.map(s => s.name), webLocale],
  () => {
    if (sources.value.length > 0) {
      void translateDynamicTexts('sources', sources.value.map(s => s.name))
    }
  },
  { immediate: true },
)

watch(
  [() => kinds.value.flatMap(k => [k.title, ...(k.chars || [])]), webLocale],
  () => {
    if (kinds.value.length > 0) {
      void translateDynamicTexts('kinds', kinds.value.flatMap(k => [k.title, ...(k.chars || [])]))
    }
  },
  { immediate: true },
)

watch(
  [() => items.value.map(book => [book.name, book.author, book.kind, book.originName]), webLocale],
  () => {
    if (items.value.length > 0) {
      void translateDynamicTexts('discovery', items.value.flatMap(book => [book.name, book.author, book.kind, book.originName]))
    }
  },
  { immediate: true },
)

onMounted(async () => {
  await loadSources()
  if (selectedSource.value) {
    await loadDiscovery(false)
  }
})
</script>

<style scoped>
.discovery-page {
  width: min(1240px, calc(100% - 36px));
  margin: 0 auto;
  padding: 30px 0 54px;
}

.glass-panel {
  border: 1px solid rgba(255, 255, 255, 0.55);
  background: rgba(249, 252, 250, 0.86);
  box-shadow: 0 14px 34px rgba(18, 51, 50, 0.1);
  backdrop-filter: blur(12px);
}

.discovery-hero {
  display: flex;
  align-items: end;
  justify-content: space-between;
  gap: 20px;
  padding: 30px 34px;
  border-radius: 20px;
  background: linear-gradient(115deg, rgba(20, 74, 75, 0.92), rgba(42, 108, 100, 0.76));
  color: white;
}

.eyebrow {
  color: #c28245;
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.13em;
}

.discovery-hero .eyebrow {
  color: #f1d49b;
}

.discovery-hero h1 {
  margin: 8px 0;
  font: 600 clamp(28px, 5vw, 46px)/1.08 Georgia, serif;
}

.discovery-hero p {
  max-width: 620px;
  margin: 0;
  color: rgba(255, 255, 255, 0.78);
}

.discovery-controls {
  margin-top: 18px;
  padding: 16px 20px;
  border-radius: 14px;
}

.control-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 16px;
}

.selector-item {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
  min-width: 260px;
}

.control-label {
  font-size: 13px;
  font-weight: 600;
  color: #173e3d;
  white-space: nowrap;
}

.control-select {
  flex: 1;
  max-width: 320px;
}

.refresh-kinds-btn {
  margin-left: 2px;
}

.source-option {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  width: 100%;
}

.source-group-tag {
  font-size: 11px;
  color: #889694;
  background: rgba(0, 0, 0, 0.05);
  padding: 1px 6px;
  border-radius: 4px;
}

.type-filter-wrap :deep(.el-tabs__header) {
  margin: 0;
}

.filter-kinds-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 16px;
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px dashed rgba(20, 74, 75, 0.12);
}

.filter-item {
  display: flex;
  align-items: center;
  gap: 8px;
}

.filter-label {
  font-size: 12px;
  color: #556966;
  white-space: nowrap;
}

.filter-control {
  max-width: 160px;
}

.results-section {
  margin-top: 24px;
}

.empty-panel {
  padding: 44px 18px;
  border-radius: 14px;
  color: #6d7b7a;
  text-align: center;
}

.discovery-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

.discovery-card {
  display: grid;
  grid-template-columns: 78px minmax(0, 1fr);
  gap: 12px;
  padding: 12px;
  border-radius: 13px;
  cursor: pointer;
  transition: transform 0.18s ease, box-shadow 0.18s ease;
}

.discovery-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 17px 35px rgba(18, 51, 50, 0.18);
}

.discovery-card img {
  width: 78px;
  height: 108px;
  object-fit: cover;
  border-radius: 6px;
  background: #dde5e1;
}

.book-info {
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.book-info h3 {
  overflow: hidden;
  margin: 2px 0 4px;
  color: #1b4441;
  font-size: 14px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.book-info .author {
  overflow: hidden;
  margin: 0 0 4px;
  color: #788683;
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.book-info .kind-tag {
  overflow: hidden;
  margin: 0 0 6px;
  color: #92632b;
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.book-info .source-tag {
  margin-top: auto;
  color: #aa7435;
  font-size: 11px;
}

.load-more-section {
  display: flex;
  justify-content: center;
  align-items: center;
  padding: 28px 0 12px;
}

.load-more-btn {
  min-width: 180px;
  height: 40px;
  border-radius: 20px;
  font-size: 14px;
}

.muted {
  margin: 7px 0;
  color: #71827e;
  font-size: 13px;
}

@media (max-width: 980px) {
  .discovery-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 720px) {
  .discovery-page {
    width: min(100% - 22px, 600px);
    padding-top: 18px;
  }

  .discovery-hero {
    display: block;
    padding: 24px 20px;
  }

  .discovery-hero .el-button {
    margin-top: 18px;
  }

  .control-row {
    flex-direction: column;
    align-items: stretch;
  }

  .selector-item {
    min-width: 0;
  }

  .control-select {
    max-width: none;
  }

  .discovery-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .discovery-card {
    grid-template-columns: 58px minmax(0, 1fr);
    padding: 9px;
    gap: 9px;
  }

  .discovery-card img {
    width: 58px;
    height: 82px;
  }
}
</style>
