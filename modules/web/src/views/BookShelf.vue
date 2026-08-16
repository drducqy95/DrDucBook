<template>
  <div class="home-page">
    <section class="hero-panel glass-panel">
      <div>
        <p class="eyebrow">DRDUCBOOK · THƯ VIỆN CÁ NHÂN</p>
        <h1>Đọc tiếp câu chuyện của bạn</h1>
        <p class="hero-copy">Giá sách trên thiết bị, nguồn online và lịch sử đọc được gom trong một không gian.</p>
      </div>
      <div class="hero-actions">
        <el-button type="primary" @click="openSettings">Cài đặt hiển thị</el-button>
        <el-button plain @click="router.push({ name: 'upload' })">Tải sách lên</el-button>
      </div>
    </section>

    <div class="home-toolbar glass-panel">
      <div class="toolbar-title">
        <span class="eyebrow">GIÁ SÁCH</span>
        <strong>{{ activeFilterLabel }}</strong>
      </div>
      <div class="toolbar-actions">
        <el-button text @click="clearFilter">Xóa bộ lọc</el-button>
        <el-button :loading="onlineLoading" @click="loadDiscovery(true)">Làm mới khám phá</el-button>
      </div>
    </div>

    <section v-if="recentBook" class="section-block">
      <div class="section-heading"><div><span class="eyebrow">TIẾP TỤC</span><h2>Đang đọc gần đây</h2></div><button class="text-link" @click="openBook(recentBook)">Mở sách</button></div>
      <article class="continue-card glass-panel" @click="openBook(recentBook)">
        <img :src="coverUrl(recentBook)" alt="" @error="onCoverError" />
        <div><h3>{{ recentBook.name }}</h3><p>{{ recentBook.author }}</p><p class="muted">{{ recentBook.durChapterTitle || 'Bắt đầu đọc' }}</p></div>
        <div class="continue-progress"><span>{{ progressText(recentBook) }}</span><el-progress :percentage="progressPercent(recentBook)" :show-text="false" /></div>
      </article>
    </section>

    <section class="section-block">
      <div class="section-heading"><div><span class="eyebrow">GIÁ SÁCH CỦA BẠN</span><h2>{{ searchWord ? `Kết quả cho “${searchWord}”` : 'Truyện mới cập nhật' }}</h2></div><span class="count-label">{{ filteredBooks.length }} truyện</span></div>
      <div v-if="loading" class="empty-panel glass-panel"><el-icon class="is-loading"><Loading /></el-icon> Đang tải giá sách...</div>
      <div v-else-if="filteredBooks.length === 0" class="empty-panel glass-panel">Chưa có sách phù hợp. Hãy thử nguồn online hoặc tải sách lên.</div>
      <div v-else class="book-grid">
        <article v-for="book in filteredBooks" :key="book.bookUrl" class="book-card glass-panel" @click="openBook(book)">
          <img class="book-cover" :src="coverUrl(book)" alt="" loading="lazy" @error="onCoverError" />
          <div class="book-info"><div class="book-title">{{ book.name }}</div><div class="book-author">{{ book.author || 'Chưa rõ tác giả' }}</div><div class="book-meta"><span>{{ typeLabel(book.type) }}</span><span>{{ 'totalChapterNum' in book ? book.totalChapterNum || 0 : 0 }} chương</span></div><p class="latest">{{ book.latestChapterTitle || 'Chưa có chương mới' }}</p></div>
        </article>
      </div>
    </section>

    <section class="section-block">
      <div class="section-heading"><div><span class="eyebrow">KHÁM PHÁ ONLINE</span><h2>Gợi ý từ nguồn sách</h2></div><span v-if="onlineError" class="error-label">Một số nguồn đang bận</span></div>
      <div v-if="onlineLoading && discovery.length === 0" class="empty-panel glass-panel">Đang lấy gợi ý từ nguồn sách...</div>
      <div v-else-if="discovery.length === 0" class="empty-panel glass-panel">Chưa có dữ liệu khám phá. Hãy bật nguồn Explore trong app.</div>
      <div v-else class="discovery-grid"><article v-for="book in discovery" :key="`${book.bookUrl}-${book.originName}`" class="discovery-card glass-panel" @click="openOnlineBook(book)"><img :src="coverUrl(book)" alt="" loading="lazy" @error="onCoverError" /><div><h3>{{ book.name }}</h3><p>{{ book.author }}</p><span>{{ book.originName || 'Nguồn sách' }}</span></div></article></div>
    </section>

    <aside class="home-footer glass-panel"><div><strong>Không tìm thấy truyện?</strong><span>Thử tìm online bằng ô tìm kiếm phía trên hoặc mở Nguồn sách để cập nhật.</span></div><el-button @click="router.push({ name: 'book-home' })">Quản lý nguồn</el-button></aside>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import API, { getDiscoveryHome } from '@api'
import type { Book, SeachBook } from '@/book'
import { isLegadoUrl } from '@/utils/utils'
import { useBookStore } from '@/store'
import { getReaderPreferences } from '@/utils/clientPreferences'

const router = useRouter()
const route = useRoute()
const store = useBookStore()
const loading = ref(true)
const onlineLoading = ref(false)
const onlineError = ref(false)
const searchWord = ref(typeof route.query.q === 'string' ? route.query.q : '')
const searchResults = ref<SeachBook[]>([])
const discovery = ref<SeachBook[]>([])
const recentBook = ref<Book | undefined>()
const readerPreferences = getReaderPreferences()
const bookType = { video: 4, text: 8, audio: 32, image: 64 }

const shelf = computed(() => store.shelf)
const filterType = computed(() => typeof route.query.type === 'string' ? route.query.type : '')
const filterStatus = computed(() => typeof route.query.status === 'string' ? route.query.status : '')
const filteredBooks = computed<Array<Book | SeachBook>>(() => {
  const source: Array<Book | SeachBook> = searchWord.value ? searchResults.value : shelf.value
  return source.filter(book => {
    const isBook = 'canUpdate' in book
    if (filterStatus.value && isBook && filterStatus.value === 'updating' && !book.canUpdate) return false
    if (filterStatus.value && isBook && filterStatus.value === 'completed' && book.canUpdate) return false
    if (filterType.value && !matchesType(book.type, filterType.value)) return false
    return !searchWord.value || `${book.name} ${book.author}`.toLowerCase().includes(searchWord.value.toLowerCase())
  }).sort((a, b) => ('lastCheckTime' in b ? b.lastCheckTime : b.respondTime) - ('lastCheckTime' in a ? a.lastCheckTime : a.respondTime))
})
const activeFilterLabel = computed(() => {
  if (filterStatus.value === 'updating') return 'Truyện đang cập nhật'
  if (filterStatus.value === 'completed') return 'Truyện đã hoàn thành'
  if (filterType.value) return typeLabel(Number(typeValue(filterType.value)))
  return 'Tất cả sách'
})

const typeValue = (value: string) => ({ text: bookType.text, image: bookType.image, audio: bookType.audio, video: bookType.video }[value as 'text' | 'image' | 'audio' | 'video'] || 0)
const matchesType = (value: number, type: string) => value === typeValue(type) || (value & typeValue(type)) !== 0
const typeLabel = (value?: number) => {
  if (value && (value & bookType.video)) return 'Video'
  if (value && (value & bookType.audio)) return 'Sách nói'
  if (value && (value & bookType.image)) return 'Truyện tranh'
  return 'Truyện chữ'
}
const coverUrl = (book: Book | SeachBook) => {
  const cover = ('customCoverUrl' in book ? book.customCoverUrl : undefined) || book.coverUrl
  return cover && !isLegadoUrl(cover) ? cover : API.getProxyCoverUrl(cover || book.bookUrl)
}
const onCoverError = (event: Event) => {
  const image = event.target as HTMLImageElement
  image.src = '/vue/favicon.ico'
}
const progressPercent = (book: Book) => book.totalChapterNum > 0 ? Math.min(100, Math.round((book.durChapterIndex / book.totalChapterNum) * 100)) : 0
const progressText = (book: Book) => `${Math.max(0, book.durChapterIndex || 0)}/${book.totalChapterNum || 0} chương`
const clearFilter = () => router.push({ name: 'shelf' })
const openSettings = () => document.dispatchEvent(new CustomEvent('open-display-settings'))

const openBook = async (book: Book | SeachBook) => {
  const isSearchBook = 'respondTime' in book
  const index = 'durChapterIndex' in book ? book.durChapterIndex : 0
  const position = 'durChapterPos' in book ? book.durChapterPos : 0
  const base = { bookUrl: book.bookUrl, bookName: book.name, bookAuthor: book.author, chapterIndex: index, chapterPos: position, isSeachBook: isSearchBook }
  Object.entries(base).forEach(([key, value]) => sessionStorage.setItem(key, String(value)))
  if (isSearchBook) {
    // Saving a search result may involve a slow source/plugin. Keep navigation
    // responsive while the app persists the book in the background.
    void API.saveBook(book).catch(() => undefined)
  }
  if (book.type & (bookType.audio | bookType.video)) router.push({ name: 'media' })
  else router.push({ name: 'chapter' })
}
const openOnlineBook = (book: SeachBook) => openBook(book)

const loadDiscovery = async (refresh = false) => {
  onlineLoading.value = true
  onlineError.value = false
  try {
    const response = await getDiscoveryHome({ limit: 24, refresh })
    discovery.value = response.items || []
    onlineError.value = (response.sourceErrors || 0) > 0
  } catch {
    onlineError.value = true
  } finally {
    onlineLoading.value = false
  }
}
const loadShelf = async () => {
  loading.value = true
  try {
    await store.loadWebConfig()
    await store.loadBookShelf()
    recentBook.value = store.shelf.find(book => book.durChapterTime > 0)
  } finally {
    loading.value = false
  }
  // Online discovery is intentionally opt-in. Explore providers can perform
  // expensive network/database work on the phone, so never start them while
  // the user is opening the local bookshelf or a chapter.
}
const runSearch = () => {
  const query = searchWord.value.trim()
  if (!query) { searchResults.value = []; return }
  API.search(query, books => { searchResults.value = books }, () => { if (!searchResults.value.length) ElMessage.info('Không có kết quả online') })
}
watch(() => route.query.q, value => { searchWord.value = typeof value === 'string' ? value : ''; runSearch() })
onMounted(() => { void loadShelf(); if (searchWord.value) runSearch() })
</script>

<style scoped>
.home-page { width: min(1320px, calc(100% - 36px)); margin: 0 auto; padding: 30px 0 54px; }.glass-panel { border: 1px solid rgba(255,255,255,.55); background: rgba(249,252,250,.84); box-shadow: 0 14px 34px rgba(18,51,50,.1); backdrop-filter: blur(12px); }.hero-panel { display: flex; align-items: end; justify-content: space-between; gap: 28px; padding: 34px 38px; border-radius: 22px; background: linear-gradient(115deg, rgba(20,74,75,.92), rgba(42,108,100,.76)); color: white; }.hero-panel h1 { max-width: 650px; margin: 8px 0; font: 600 clamp(28px, 5vw, 48px)/1.08 Georgia, serif; }.hero-copy { max-width: 600px; margin: 0; color: rgba(255,255,255,.78); }.eyebrow { color: #c28245; font-size: 11px; font-weight: 800; letter-spacing: .13em; }.hero-panel .eyebrow { color: #f1d49b; }.hero-actions { display: flex; flex-wrap: wrap; gap: 9px; }.hero-actions .el-button { margin: 0; }.home-toolbar { display: flex; justify-content: space-between; align-items: center; gap: 15px; margin-top: 20px; padding: 14px 20px; border-radius: 14px; }.toolbar-title strong { display: block; margin-top: 4px; color: #204b49; }.toolbar-actions { display: flex; gap: 6px; }.section-block { margin-top: 34px; }.section-heading { display: flex; align-items: end; justify-content: space-between; gap: 14px; margin-bottom: 14px; }.section-heading h2 { margin: 4px 0 0; color: #173e3d; font: 600 25px Georgia, serif; }.text-link { border: 0; background: transparent; color: #a96f2f; cursor: pointer; }.count-label,.error-label { color: #6d7b7a; font-size: 13px; }.error-label { color: #bd6d43; }.continue-card { display: grid; grid-template-columns: 70px minmax(0,1fr) 220px; align-items: center; gap: 16px; padding: 15px; border-radius: 14px; cursor: pointer; }.continue-card img { width: 70px; height: 94px; object-fit: cover; border-radius: 7px; }.continue-card h3 { margin: 0 0 4px; color: #183e3c; }.continue-card p { margin: 3px 0; }.muted { color: #788b88; font-size: 13px; }.continue-progress { color: #70817e; font-size: 12px; }.book-grid { display: grid; grid-template-columns: repeat(3, minmax(0,1fr)); gap: 14px; }.book-card { display: flex; gap: 14px; min-height: 148px; padding: 13px; border-radius: 12px; cursor: pointer; transition: transform .18s ease, box-shadow .18s ease; }.book-card:hover,.discovery-card:hover,.continue-card:hover { transform: translateY(-2px); box-shadow: 0 17px 35px rgba(18,51,50,.18); }.book-cover { flex: 0 0 82px; width: 82px; height: 116px; object-fit: cover; border-radius: 6px; background: #dde5e1; }.book-info { min-width: 0; }.book-title { overflow: hidden; color: #1b4441; font-size: 16px; font-weight: 700; text-overflow: ellipsis; white-space: nowrap; }.book-author { margin-top: 5px; color: #788683; font-size: 13px; }.book-meta { display: flex; gap: 7px; margin-top: 10px; color: #aa7435; font-size: 11px; }.latest { display: -webkit-box; overflow: hidden; margin: 10px 0 0; color: #667875; font-size: 12px; -webkit-box-orient: vertical; -webkit-line-clamp: 2; }.empty-panel { padding: 38px 18px; border-radius: 13px; color: #6d7b7a; text-align: center; }.discovery-grid { display: grid; grid-template-columns: repeat(4, minmax(0,1fr)); gap: 12px; }.discovery-card { display: grid; grid-template-columns: 62px minmax(0,1fr); gap: 11px; padding: 11px; border-radius: 12px; cursor: pointer; }.discovery-card img { width: 62px; height: 86px; object-fit: cover; border-radius: 5px; }.discovery-card h3 { overflow: hidden; margin: 2px 0 4px; color: #1b4441; font-size: 14px; text-overflow: ellipsis; white-space: nowrap; }.discovery-card p { overflow: hidden; margin: 0 0 8px; color: #788683; font-size: 12px; text-overflow: ellipsis; white-space: nowrap; }.discovery-card span { color: #aa7435; font-size: 11px; }.home-footer { display: flex; justify-content: space-between; align-items: center; gap: 15px; margin-top: 34px; padding: 17px 20px; border-radius: 12px; }.home-footer strong,.home-footer span { display: block; }.home-footer span { margin-top: 4px; color: #71827e; font-size: 13px; }
@media (max-width: 1050px) { .book-grid { grid-template-columns: repeat(2, minmax(0,1fr)); }.discovery-grid { grid-template-columns: repeat(3, minmax(0,1fr)); } }
@media (max-width: 720px) { .home-page { width: min(100% - 22px, 600px); padding-top: 18px; }.hero-panel { display: block; padding: 24px 20px; }.hero-actions { margin-top: 18px; }.home-toolbar,.section-heading,.home-footer { align-items: flex-start; flex-direction: column; }.book-grid { grid-template-columns: 1fr; }.discovery-grid { grid-template-columns: repeat(2, minmax(0,1fr)); }.continue-card { grid-template-columns: 60px minmax(0,1fr); }.continue-card img { width: 60px; height: 80px; }.continue-progress { grid-column: 1 / -1; }.home-footer { padding: 16px; } }
</style>
