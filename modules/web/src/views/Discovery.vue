<template>
  <div class="discovery-page">
    <section class="discovery-hero glass-panel">
      <div>
        <span class="eyebrow">{{ t('discovery') }}</span>
        <h1>{{ t('discoveryTitle') }}</h1>
        <p>{{ t('discoveryDescription') }}</p>
      </div>
      <el-button type="primary" :loading="loading" @click="refreshDiscovery">
        {{ t('refreshDiscovery') }}
      </el-button>
    </section>

    <section class="discovery-toolbar glass-panel">
      <el-tabs v-model="typeFilter" @tab-change="() => loadDiscovery(false)">
        <el-tab-pane :label="t('allTypes')" name="" />
        <el-tab-pane :label="t('textBooks')" name="text" />
        <el-tab-pane :label="t('comics')" name="image" />
        <el-tab-pane :label="t('audio')" name="audio" />
        <el-tab-pane :label="t('video')" name="video" />
      </el-tabs>
    </section>

    <section class="source-panel glass-panel">
      <div class="section-heading">
        <div>
          <span class="eyebrow">{{ t('discoverySources') }}</span>
          <h2>{{ t('discoverySourcesTitle') }}</h2>
        </div>
        <el-button text :loading="sourcesLoading" @click="loadSources">{{ t('refresh') }}</el-button>
      </div>
      <p class="muted">{{ t('discoverySourcesDescription') }}</p>
      <el-checkbox-group v-model="selectedSources" @change="saveSources">
        <el-checkbox v-for="source in sources" :key="source.sourceUrl" :label="source.sourceUrl">
          {{ dynamicText('sources', source.name) }}
        </el-checkbox>
      </el-checkbox-group>
    </section>

    <section class="results-section">
      <div v-if="loading && !items.length" class="empty-panel glass-panel">{{ t('waiting') }}</div>
      <div v-else-if="error" class="empty-panel glass-panel">{{ t('discoveryLoadFailed') }}</div>
      <div v-else-if="!items.length" class="empty-panel glass-panel">{{ t('noDiscoveryData') }}</div>
      <div v-else class="discovery-grid">
        <article v-for="book in items" :key="`${book.bookUrl}-${book.originName}`" class="discovery-card glass-panel" @click="openBook(book)">
          <img :src="coverUrl(book)" alt="" loading="lazy" @error="onCoverError" />
          <div class="book-info">
            <h3>{{ displayDynamic(book.name) }}</h3>
            <p>{{ displayDynamic(book.author) }}</p>
            <span>{{ book.originName ? displayDynamic(book.originName) : t('sourceName') }}</span>
          </div>
        </article>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import API, { getDiscoveryHome } from '@api'
import {
  getWebServiceDiscoverySources,
  patchWebServiceDiscoverySources,
  type WebServiceDiscoverySource,
} from '@/api/webService'
import type { SeachBook } from '@/book'
import { isLegadoUrl } from '@/utils/utils'
import { dynamicText, t, translateDynamicTexts, webLocale } from '@/i18n'

const router = useRouter()
const typeFilter = ref('')
const loading = ref(false)
const error = ref(false)
const sourcesLoading = ref(false)
const items = ref<SeachBook[]>([])
const sources = ref<WebServiceDiscoverySource[]>([])
const selectedSources = ref<string[]>([])
const bookType = { audio: 32, video: 4 }

const displayDynamic = (value: string | null | undefined) => dynamicText('discovery', value)
const coverUrl = (book: SeachBook) => book.coverUrl && !isLegadoUrl(book.coverUrl)
  ? book.coverUrl
  : API.getProxyCoverUrl(book.coverUrl || book.bookUrl)
const onCoverError = (event: Event) => {
  ;(event.target as HTMLImageElement).src = '/vue/favicon.ico'
}

const loadSources = async () => {
  sourcesLoading.value = true
  try {
    sources.value = await getWebServiceDiscoverySources()
    selectedSources.value = sources.value.filter(item => item.selectedForWeb).map(item => item.sourceUrl)
  } catch {
    sources.value = []
    selectedSources.value = []
  } finally {
    sourcesLoading.value = false
  }
}

const saveSources = async () => {
  try {
    await patchWebServiceDiscoverySources(selectedSources.value)
    await loadDiscovery(false)
  } catch {
    ElMessage.error(t('discoverySaveFailed'))
    await loadSources()
  }
}

const loadDiscovery = async (refresh = false) => {
  loading.value = true
  error.value = false
  try {
    const response = await getDiscoveryHome({ type: typeFilter.value || undefined, limit: 48, refresh })
    items.value = response.items || []
    error.value = response.sourceErrors > 0 && items.value.length === 0
  } catch {
    error.value = true
    items.value = []
  } finally {
    loading.value = false
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

watch([() => items.value.map(book => [book.name, book.author, book.originName]), webLocale], () => {
  void translateDynamicTexts('discovery', items.value.flatMap(book => [book.name, book.author, book.originName]))
}, { immediate: true })

onMounted(() => {
  void loadSources()
  void loadDiscovery(false)
})
</script>

<style scoped>
.discovery-page { width: min(1240px, calc(100% - 36px)); margin: 0 auto; padding: 30px 0 54px; }
.glass-panel { border: 1px solid rgba(255,255,255,.55); background: rgba(249,252,250,.86); box-shadow: 0 14px 34px rgba(18,51,50,.1); backdrop-filter: blur(12px); }
.discovery-hero { display: flex; align-items: end; justify-content: space-between; gap: 20px; padding: 30px 34px; border-radius: 20px; background: linear-gradient(115deg, rgba(20,74,75,.92), rgba(42,108,100,.76)); color: white; }
.eyebrow { color: #c28245; font-size: 11px; font-weight: 800; letter-spacing: .13em; }.discovery-hero .eyebrow { color: #f1d49b; }
.discovery-hero h1 { margin: 8px 0; font: 600 clamp(28px, 5vw, 46px)/1.08 Georgia, serif; }.discovery-hero p { max-width: 620px; margin: 0; color: rgba(255,255,255,.78); }
.discovery-toolbar,.source-panel { margin-top: 18px; padding: 12px 18px; border-radius: 14px; }.discovery-toolbar :deep(.el-tabs__header) { margin: 0; }.source-panel { padding: 18px; }
.section-heading { display: flex; align-items: center; justify-content: space-between; gap: 12px; }.section-heading h2 { margin: 4px 0 0; color: #173e3d; font: 600 23px Georgia, serif; }.muted { margin: 7px 0 13px; color: #71827e; font-size: 13px; }.source-panel :deep(.el-checkbox) { margin: 5px 18px 5px 0; }
.results-section { margin-top: 24px; }.empty-panel { padding: 44px 18px; border-radius: 14px; color: #6d7b7a; text-align: center; }.discovery-grid { display: grid; grid-template-columns: repeat(4, minmax(0,1fr)); gap: 13px; }.discovery-card { display: grid; grid-template-columns: 78px minmax(0,1fr); gap: 12px; padding: 12px; border-radius: 13px; cursor: pointer; transition: transform .18s ease, box-shadow .18s ease; }.discovery-card:hover { transform: translateY(-2px); box-shadow: 0 17px 35px rgba(18,51,50,.18); }.discovery-card img { width: 78px; height: 108px; object-fit: cover; border-radius: 6px; background: #dde5e1; }.book-info { min-width: 0; }.book-info h3 { overflow: hidden; margin: 2px 0 6px; color: #1b4441; font-size: 14px; text-overflow: ellipsis; white-space: nowrap; }.book-info p { overflow: hidden; margin: 0 0 9px; color: #788683; font-size: 12px; text-overflow: ellipsis; white-space: nowrap; }.book-info span { color: #aa7435; font-size: 11px; }
@media (max-width: 980px) { .discovery-grid { grid-template-columns: repeat(3, minmax(0,1fr)); } }
@media (max-width: 720px) { .discovery-page { width: min(100% - 22px, 600px); padding-top: 18px; }.discovery-hero { display: block; padding: 24px 20px; }.discovery-hero .el-button { margin-top: 18px; }.discovery-grid { grid-template-columns: repeat(2, minmax(0,1fr)); }.discovery-card { grid-template-columns: 58px minmax(0,1fr); padding: 9px; gap: 9px; }.discovery-card img { width: 58px; height: 82px; }.source-panel :deep(.el-checkbox) { display: flex; margin-right: 0; } }
</style>
