<template>
  <div
    class="chapter-wrapper"
    :style="bodyTheme"
    :class="{
      night: isNight,
      day: !isNight,
      'paged-mode': readMode === 'paged',
    }"
    @click="showToolBar = !showToolBar"
  >
    <div class="tool-bar" :style="leftBarTheme">
      <div class="tools">
        <el-popover
          placement="right"
          :width="popupWidth"
          trigger="click"
          :show-arrow="false"
          v-model:visible="popCataVisible"
          popper-class="pop-cata"
        >
          <PopCatalog @getContent="getContent" class="popup" />
          <template #reference>
            <div class="tool-icon" :class="{ 'no-point': false }">
              <div class="iconfont">&#58905;</div>
              <div class="icon-text">Mục lục</div>
            </div>
          </template>
        </el-popover>
        <el-popover
          placement="right"
          :width="popupWidth"
          trigger="click"
          :show-arrow="false"
          v-model:visible="readSettingsVisible"
          popper-class="pop-setting"
        >
          <read-settings class="popup" />
          <template #reference>
            <div class="tool-icon" :class="{ 'no-point': noPoint }">
              <div class="iconfont">&#58971;</div>
              <div class="icon-text">Cài đặt</div>
            </div>
          </template>
        </el-popover>
        <el-popover
          placement="right"
          :width="300"
          trigger="click"
          :show-arrow="false"
          v-model:visible="translationPanelVisible"
          popper-class="translation-provider-popover"
        >
          <div class="translation-provider-panel" @click.stop>
            <strong>{{ t('translationReader') }}</strong>
            <label>{{ t('provider') }}</label>
            <el-select
              v-model="selectedTranslationProvider"
              :disabled="translationLoading"
              @change="onTranslationProviderChange"
            >
              <el-option
                v-for="provider in translationProviders"
                :key="provider.id"
                :label="provider.name"
                :value="provider.id"
              />
            </el-select>
            <label>{{ t('targetLanguage') }}</label>
            <el-select
              v-model="selectedTargetLanguage"
              :disabled="translationLoading"
              @change="onTranslationTargetLanguageChange"
            >
              <el-option
                v-for="language in selectedProviderLanguages"
                :key="language"
                :label="translationLanguageName(language)"
                :value="language"
              />
            </el-select>
            <el-button
              type="primary"
              :loading="translationLoading"
              :disabled="noPoint"
              @click="toggleTranslationJob"
            >
              {{ translationToolText }}
            </el-button>
            <div class="pretranslate-controls">
              <el-input-number
                v-model="pretranslateCount"
                :min="1"
                :max="50"
                :step="1"
                size="small"
                controls-position="right"
                :disabled="pretranslateLoading || noPoint"
                :aria-label="t('pretranslateCount')"
              />
              <el-button
                plain
                size="small"
                :loading="pretranslateLoading"
                :disabled="noPoint || !canUseWebTranslation"
                @click="pretranslateChapters"
              >
                {{ t('pretranslate') }}
              </el-button>
            </div>
            <el-button plain :loading="ttsLoading" :disabled="noPoint || chapterData.length === 0" @click="ttsPlaying ? stopWebTts() : speakCurrentChapter()">
              {{ ttsPlaying ? t('stopReading') : t('listenCurrent') }}
            </el-button>
            <el-select v-model="exportFormat" size="small" :disabled="exportLoading || noPoint" aria-label="Định dạng ebook">
              <el-option label="EPUB 3" value="epub3" />
              <el-option label="EPUB 2" value="epub2" />
              <el-option label="PDF" value="pdf" />
              <el-option label="HTML" value="html" />
              <el-option label="TXT" value="txt" />
              <el-option label="CBZ" value="cbz" />
            </el-select>
            <el-button plain :loading="exportLoading" :disabled="noPoint" @click="exportCurrentBook">
              {{ t('exportEbook') }}
            </el-button>
          </div>
          <template #reference>
            <div
              class="tool-icon"
              :class="{ 'no-point': noPoint || translationLoading }"
              @click.stop
            >
              <div class="iconfont">T</div>
              <div class="icon-text">Dịch</div>
            </div>
          </template>
        </el-popover>
        <div class="tool-icon" @click="toShelf">
          <div class="iconfont">&#58892;</div>
          <div class="icon-text">Kệ sách</div>
        </div>
        <div class="tool-icon" :class="{ 'no-point': noPoint }" @click="toTop">
          <div class="iconfont">&#58914;</div>
          <div class="icon-text">Đầu trang</div>
        </div>
        <div
          class="tool-icon"
          :class="{ 'no-point': noPoint }"
          @click="toBottom"
        >
          <div class="iconfont">&#58915;</div>
          <div class="icon-text">Cuối trang</div>
        </div>
      </div>
    </div>
    <div class="read-bar" :style="rightBarTheme">
      <div class="tools">
        <div
          class="tool-icon"
          :class="{ 'no-point': noPoint }"
          @click="readMode === 'paged' ? turnHorizontalPage(-1) : toPreChapter()"
        >
          <div class="iconfont">&#58920;</div>
          <span v-if="miniInterface">{{ readMode === 'paged' ? 'Trang trước' : 'Chương trước' }}</span>
        </div>
        <div
          class="tool-icon"
          :class="{ 'no-point': noPoint }"
          @click="readMode === 'paged' ? turnHorizontalPage(1) : toNextChapter()"
        >
          <span v-if="miniInterface">{{ readMode === 'paged' ? 'Trang sau' : 'Chương sau' }}</span>
          <div class="iconfont">&#58913;</div>
        </div>
      </div>
    </div>
    <div class="chapter-bar"></div>
    <div v-if="translationStatusText" class="translation-status">
      {{ translationStatusText }}
    </div>
    <div v-if="ttsControlVisible" class="tts-control-panel" @click.stop>
      <div class="tts-control-heading">
        <strong>{{ t('listenCurrent') }}</strong>
        <span>{{ ttsProgressLabel }}</span>
      </div>
      <div class="tts-progress-track" role="progressbar" :aria-valuenow="ttsProgress" aria-valuemin="0" aria-valuemax="100">
        <span class="tts-progress-value" :style="{ width: `${ttsProgress}%` }" />
      </div>
      <div class="tts-control-actions">
        <button type="button" :aria-label="t('previousChapter')" :title="t('previousChapter')" @click="skipWebTtsChapter(-1)">‹</button>
        <button
          type="button"
          class="tts-main-action"
          :disabled="!ttsAudioReady || ttsLoading"
          :aria-label="ttsPaused ? t('resumeReading') : t('pauseReading')"
          :title="ttsPaused ? t('resumeReading') : t('pauseReading')"
          @click="toggleWebTtsPause"
        >
          {{ ttsPaused ? '▶' : 'Ⅱ' }}
        </button>
        <button type="button" :aria-label="t('stopReading')" :title="t('stopReading')" @click="stopWebTts">■</button>
        <button type="button" :aria-label="t('nextChapter')" :title="t('nextChapter')" @click="skipWebTtsChapter(1)">›</button>
      </div>
    </div>
    <div
      class="chapter"
      ref="content"
      :style="chapterTheme"
      @scroll.passive="onReaderScroll"
      @wheel="onReaderWheel"
    >
      <div class="content">
        <div class="top-bar" ref="top"></div>
        <div
          v-if="chapterData.length === 0 && (isLoading || readerError)"
          class="reader-state"
          @click.stop
        >
          <p>{{ isLoading ? 'Đang tải nội dung chương…' : readerError }}</p>
          <el-button v-if="readerError && !isLoading" type="primary" @click="reloadReader">
            Thử lại
          </el-button>
        </div>
        <div
          v-for="data in chapterData"
          :key="data.index"
          :chapterIndex="data.index"
          ref="chapter"
        >
          <chapter-content
            ref="chapterRef"
            :chapterIndex="data.index"
            :contents="translatedChapters[data.index] ?? data.content"
            :title="data.title"
            :spacing="store.config.spacing"
            :fontSize="fontSize"
            :fontFamily="fontFamily"
            :readMode="readMode"
            :activeParagraphIndex="ttsActiveChapter === data.index ? ttsActiveParagraph : -1"
            @readedLengthChange="onReadedLengthChange"
            v-if="showContent"
          />
        </div>
        <div class="loading" ref="loading"></div>
        <div class="bottom-bar" ref="bottom"></div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import jump from '@/plugins/jump'
import settings from '@/config/themeConfig'
import API from '@api'
import {
  cancelWebServiceTranslationJob,
  createWebServiceTranslationJob,
  downloadWebServiceExportEbook,
  getWebServiceTtsCapabilities,
  getWebServiceTranslationContent,
  getWebServiceTranslationJob,
  getWebServiceTranslationProviders,
  pretranslateWebServiceChapters,
  resolveWebServiceUrl,
  synthesizeWebServiceTts,
  type WebServiceTtsSynthesisResponse,
  type WebServiceTranslationProvider,
  type WebServiceTranslationJobResponse,
} from '@/api/webService'
import { useLoading } from '@/hooks/loading'
import { useThrottleFn } from '@vueuse/shared'
import { isNullOrBlank } from '@/utils/utils'
import { initXboxGamepad } from '@/utils/xboxGamepad'
import { getReaderPreferences } from '@/utils/clientPreferences'
import { withWebSession } from '@/api/webSession'
import { t } from '@/i18n'

const content = ref()
// loading spinner
const { isLoading, loadingWrapper } = useLoading(content, 'Đang tải thông tin')
const store = useBookStore()
const webServiceStore = useWebServiceStore()
const ttsLoading = ref(false)
const ttsPlaying = ref(false)
const ttsPaused = ref(false)
const ttsAudioReady = ref(false)
const ttsActiveChapter = ref<number | null>(null)
const ttsActiveParagraph = ref(-1)
const ttsActiveParagraphCount = ref(0)
const exportLoading = ref(false)
const pretranslateLoading = ref(false)
const pretranslateCount = ref(10)
const exportFormat = ref<'epub2' | 'epub3' | 'pdf' | 'txt' | 'html' | 'cbz'>('epub3')
let ttsAudio: HTMLAudioElement | null = null
let ttsPlaybackToken = 0

const ttsControlVisible = computed(() =>
  ttsPlaying.value || ttsPaused.value || ttsLoading.value || ttsActiveChapter.value !== null,
)
const ttsProgress = computed(() => {
  if (ttsActiveParagraphCount.value <= 0 || ttsActiveParagraph.value < 0) return 0
  return Math.min(100, Math.round(((ttsActiveParagraph.value + 1) / ttsActiveParagraphCount.value) * 100))
})
const ttsProgressLabel = computed(() => {
  const title = catalog.value[ttsActiveChapter.value ?? chapterIndex.value]?.title || t('listenCurrent')
  if (ttsActiveParagraphCount.value <= 0 || ttsActiveParagraph.value < 0) return title
  return `${title} · ${ttsActiveParagraph.value + 1}/${ttsActiveParagraphCount.value}`
})

const {
  catalog,
  popCataVisible,
  readSettingsVisible,
  miniInterface,
  showContent,
  bookProgress,
  theme,
  isNight,
} = storeToRefs(store)
const readMode = computed(() => store.config.readMode || 'vertical')

const saveBlob = (blob: Blob, fileName: string) => {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = fileName
  link.click()
  URL.revokeObjectURL(url)
}

const exportCurrentBook = async () => {
  if (!webServiceStore.policy?.exportEnabled) {
    ElMessage.warning('Export đang tắt trong WebService')
    return
  }
  const bookUrl = store.readingBook.bookUrl
  if (!bookUrl) return
  exportLoading.value = true
  try {
    const download = await downloadWebServiceExportEbook({
      bookUrl,
      format: exportFormat.value,
      scope: 'all',
      contentSource: 'original',
      imageOptimization: 'balanced',
    })
    saveBlob(download.blob, download.fileName || `book.${exportFormat.value}`)
    ElMessage.success('Đã tạo ebook và tải xuống')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : 'Không thể xuất ebook')
  } finally {
    exportLoading.value = false
  }
}

const chapterPos = computed({
  get: () => store.readingBook.chapterPos,
  set: value => (store.readingBook.chapterPos = value),
})
const chapterIndex = computed({
  get: () => store.readingBook.chapterIndex,
  set: value => (store.readingBook.chapterIndex = value),
})
const isSeachBook = computed({
  get: () => store.readingBook.isSeachBook,
  set: value => (store.readingBook.isSeachBook = value),
})

// 当前阅读书籍readingBook持久化
watch(
  () => store.readingBook,
  book => {
    // LưulocalStorage
    // localStorage.setItem(book.bookUrl, JSON.stringify(book));
    // Đọc gần đây
    localStorage.setItem('readingRecent', JSON.stringify(book))
    //Lưu sessionStorage
    sessionStorage.setItem('chapterIndex', book.chapterIndex.toString())
    sessionStorage.setItem('chapterPos', book.chapterPos.toString())
  },
  { deep: 1 },
)

// 无限滚动
const infiniteLoading = computed(() => store.config.infiniteLoading)
let scrollObserver: IntersectionObserver | null
const loading = ref()
watchEffect(() => {
  if (!infiniteLoading.value || readMode.value === 'paged') {
    scrollObserver?.disconnect()
  } else if (loading.value) {
    scrollObserver?.observe(loading.value)
  }
})
const loadMore = () => {
  if (isLoading.value || chapterData.value.length === 0) return
  const index = chapterData.value.slice(-1)[0].index
  if (catalog.value.length - 1 > index) {
    getContent(index + 1, false)
    store.saveBookProgress() // Lưu的是Chương trước的进度，不是预载的本章进度
  }
}
// IntersectionObserver回调 Cuối trang加载
const onReachBottom = (entries: IntersectionObserverEntry[]) => {
  if (isLoading.value) return
  for (const { isIntersecting } of entries) {
    if (!isIntersecting) return
    loadMore()
  }
}

// 字体
const fontFamily = computed(() => {
  const clientFont = getReaderPreferences().fontFamily
  if (clientFont) return clientFont
  if (store.config.font >= 0) {
    return settings.fonts[store.config.font]
  }
  return store.config.customFontName
})
const fontSize = computed(() => {
  return (getReaderPreferences().fontSize || store.config.fontSize) + 'px'
})

// 主题部分
const bodyColor = computed(() => settings.themes[theme.value].body)
const chapterColor = computed(() => settings.themes[theme.value].content)
const popupColor = computed(() => settings.themes[theme.value].popup)

const readWidth = computed(() => {
  if (!miniInterface.value) {
    return store.config.readWidth - 130 + 'px'
  } else {
    return window.innerWidth + 'px'
  }
})
const popupWidth = computed(() => {
  if (!miniInterface.value) {
    return store.config.readWidth - 33
  } else {
    return window.innerWidth - 33
  }
})
const bodyTheme = computed(() => {
  return {
    background: bodyColor.value,
  }
})
const chapterTheme = computed(() => {
  return {
    background: chapterColor.value,
    width: readWidth.value,
    '--reader-content-width': readWidth.value,
  }
})
const showToolBar = ref(false)
const leftBarTheme = computed(() => {
  return {
    background: popupColor.value,
    marginLeft: miniInterface.value
      ? 0
      : -(store.config.readWidth / 2 + 68) + 'px',
    display: miniInterface.value && !showToolBar.value ? 'none' : 'block',
  }
})
const rightBarTheme = computed(() => {
  return {
    background: popupColor.value,
    marginRight: miniInterface.value
      ? 0
      : -(store.config.readWidth / 2 + 52) + 'px',
    display: miniInterface.value && !showToolBar.value ? 'none' : 'block',
  }
})

/**
 * pc移动端判断 最大阅读宽度修正
 * 阅读宽度最小为640px 加上工具栏 68px 52px 取较大值 为 776px
 */
const onResize = () => {
  store.setMiniInterface(window.innerWidth < 776)
  const width = store.config.readWidth /**包含padding */
  checkPageWidth(width)
}
/** 判断阅读宽度是Không超出页面或者低于Mặc định值640 */
const checkPageWidth = (readWidth: number) => {
  if (store.miniInterface) return
  if (readWidth < 640) store.config.readWidth = 640
  if (readWidth + 2 * 68 > window.innerWidth) store.config.readWidth -= 160
}
watch(
  () => store.config.readWidth,
  width => checkPageWidth(width),
)
// Đầu trangCuối trang跳转
const top = ref()
const bottom = ref()
const toTop = () => {
  if (readMode.value === 'paged') {
    content.value?.scrollTo({ left: 0, behavior: 'smooth' })
  } else {
    jump(top.value)
  }
}
const toBottom = () => {
  if (readMode.value === 'paged') {
    content.value?.scrollTo({
      left: content.value.scrollWidth,
      behavior: 'smooth',
    })
  } else {
    jump(bottom.value)
  }
}

watch(readMode, () => {
  nextTick(() => {
    content.value?.scrollTo({ left: 0, behavior: 'auto' })
    if (chapterPos.value > 0) toChapterPos(chapterPos.value)
  })
})

// Kệ sách路由切换
const router = useRouter()
const toShelf = () => {
  router.push('/')
}

// 获取章节内容
const chapterData = ref<{ index: number; content: string[]; title: string }[]>(
  [],
)
const readerError = ref('')
const reloadReader = () => location.reload()
const noPoint = ref(true)
const translatedChapters = ref<Record<number, string[]>>({})
const translationJob = ref<WebServiceTranslationJobResponse | null>(null)
const translationLoading = ref(false)
const translationPanelVisible = ref(false)
const TRANSLATION_READING_KEY = 'webTranslationReadingEnabled'
const TRANSLATION_PROVIDER_KEY = 'webTranslationProvider'
const TRANSLATION_LANGUAGE_KEY = 'webTranslationTargetLanguage'
const translationReadingEnabled = ref(
  getReaderPreferences().translationEnabled ?? localStorage.getItem(TRANSLATION_READING_KEY) === 'true',
)
const translationProviders = ref<WebServiceTranslationProvider[]>([])
const selectedTranslationProvider = ref(
  getReaderPreferences().translationProvider || localStorage.getItem(TRANSLATION_PROVIDER_KEY) || '',
)
const selectedTargetLanguage = ref(
  localStorage.getItem(TRANSLATION_LANGUAGE_KEY) || '',
)
const selectedProviderLanguages = computed(
  () =>
    translationProviders.value.find(
      provider => provider.id === selectedTranslationProvider.value,
    )?.targetLanguages ?? [],
)
const translationLanguageNames: Record<string, string> = {
    zh: '简体中文',
    en: 'English',
    vi: 'Tiếng Việt',
    ja: '日本語',
    ko: '한국어',
    fr: 'Français',
    de: 'Deutsch',
    es: 'Español',
    ru: 'Русский',
    ar: 'العربية',
  }
const translationLanguageName = (language: string) =>
  translationLanguageNames[language] || language
let translationTimer = 0

const isTranslationRunning = computed(() => {
  const status = translationJob.value?.status
  return status === 'idle' || status === 'translating'
})
const canUseWebTranslation = computed(
  () => webServiceStore.policy?.autoTranslationEnabled ?? false,
)
const translationToolText = computed(() => {
  if (isTranslationRunning.value) return 'Hủy dịch'
  return translationReadingEnabled.value ? 'Bản gốc' : 'Bản dịch'
})

type WebTtsChapter = {
  displayIndex: number
  title: string
  paragraphs: string[]
}

type WebTtsChunk = {
  text: string
  startParagraph: number
  parts: Array<{ paragraphIndex: number; text: string }>
}

// Valtec ONNX currently accepts at most 420 characters per synthesis call.
// Keep a margin for punctuation and engine-specific tokenization.
const WEB_TTS_CHUNK_LIMIT = 360
const WEB_TTS_PREFETCH_AHEAD = 2
const WEB_TTS_PREFETCH_CACHE_LIMIT = 6
const webTtsPrefetchCache = new Map<string, Promise<WebServiceTtsSynthesisResponse | null>>()
const webTtsChapterCache = new Map<string, Promise<WebTtsChapter>>()

const stopWebTts = () => {
  ttsPlaybackToken += 1
  ttsAudio?.pause()
  if (ttsAudio) {
    ttsAudio.removeAttribute('src')
    ttsAudio.load()
  }
  ttsAudio = null
  ttsAudioReady.value = false
  ttsLoading.value = false
  ttsPlaying.value = false
  ttsPaused.value = false
  ttsActiveChapter.value = null
  ttsActiveParagraph.value = -1
  ttsActiveParagraphCount.value = 0
  webTtsPrefetchCache.clear()
  webTtsChapterCache.clear()
}

const toggleWebTtsPause = () => {
  if (!ttsAudio || !ttsAudioReady.value || !ttsPlaying.value) return
  if (ttsPaused.value) {
    ttsAudio.play().then(() => {
      ttsPaused.value = false
    }).catch(() => {
      ElMessage.error('Không thể tiếp tục phát TTS')
    })
  } else {
    ttsAudio.pause()
    ttsPaused.value = true
  }
}

const stripUnsupportedLocalTtsText = (value: string) =>
  value
    .replace(/[\u2E80-\u9FFF\uAC00-\uD7AF\u3040-\u30FF\u0400-\u04FF\u0600-\u06FF]+/g, ' ')
    .replace(/\s+/g, ' ')
    .trim()

const speechText = (value: string, localOnly = false) => {
  if (!value || /^\s*<img\b/i.test(value)) return ''
  const element = document.createElement('div')
  element.innerHTML = value
  const text = (element.textContent || '')
    .replace(/\u00a0/g, ' ')
    .replace(/[\u200B-\u200D\uFEFF]/g, '')
    .replace(/\s+/g, ' ')
    .trim()
  return localOnly ? stripUnsupportedLocalTtsText(text) : text
}

const splitLongWebTtsParagraph = (paragraph: string, paragraphIndex: number): WebTtsChunk[] => {
  const chunks: WebTtsChunk[] = []
  let remaining = paragraph.trim()
  while (remaining.length > WEB_TTS_CHUNK_LIMIT) {
    const candidate = remaining.slice(0, WEB_TTS_CHUNK_LIMIT + 1)
    const breakPositions = [...candidate.matchAll(/[\s၊。！？；：,.!?;:]/g)].map(match => match.index ?? 0)
    const preferredBreak = breakPositions.filter(position => position >= WEB_TTS_CHUNK_LIMIT * 0.55).pop()
    const cutAt = preferredBreak && preferredBreak > 0
      ? preferredBreak + 1
      : WEB_TTS_CHUNK_LIMIT
    const text = remaining.slice(0, cutAt).trim()
    if (!text) break
    chunks.push({
      text,
      startParagraph: paragraphIndex,
      parts: [{ paragraphIndex, text }],
    })
    remaining = remaining.slice(cutAt).trim()
  }
  if (remaining) {
    chunks.push({
      text: remaining,
      startParagraph: paragraphIndex,
      parts: [{ paragraphIndex, text: remaining }],
    })
  }
  return chunks
}

const splitWebTtsChunks = (paragraphs: string[]): WebTtsChunk[] => {
  return paragraphs.flatMap((paragraph, index) =>
    splitLongWebTtsParagraph(paragraph, index),
  )
}

const webTtsChunkCacheKey = (chunk: WebTtsChunk) =>
  `${store.readingBook.bookUrl}\u0000${chunk.text}`

const synthesizeWebTtsChunk = async (chunk: WebTtsChunk) => {
  try {
    return await synthesizeWebServiceTts(chunk.text, undefined, store.readingBook.bookUrl)
  } catch (error: any) {
    const serverError = error?.response?.data?.error || error?.response?.data?.message
    if (serverError === 'TTS_TEXT_UNSUPPORTED_LOCAL' || serverError === 'TTS_TEXT_REQUIRED') return null
    throw error
  }
}

const trimWebTtsPrefetchCache = () => {
  while (webTtsPrefetchCache.size > WEB_TTS_PREFETCH_CACHE_LIMIT) {
    const oldest = webTtsPrefetchCache.keys().next().value
    if (!oldest) break
    webTtsPrefetchCache.delete(oldest)
  }
}

const prefetchWebTtsChunks = (chunks: WebTtsChunk[], token: number) => {
  let previousTask: Promise<WebServiceTtsSynthesisResponse | null> = Promise.resolve(null)
  chunks.forEach(chunk => {
    if (token !== ttsPlaybackToken) return
    const key = webTtsChunkCacheKey(chunk)
    const existingTask = webTtsPrefetchCache.get(key)
    if (existingTask) {
      previousTask = existingTask.catch(() => null)
      return
    }
    const task = previousTask
      .catch(() => null)
      .then(() => {
        if (token !== ttsPlaybackToken) return null
        return synthesizeWebTtsChunk(chunk).catch(() => null)
      })
    webTtsPrefetchCache.set(key, task)
    previousTask = task
  })
  trimWebTtsPrefetchCache()
}

const getWebTtsChunkSynthesis = async (chunk: WebTtsChunk) => {
  const key = webTtsChunkCacheKey(chunk)
  const prefetched = webTtsPrefetchCache.get(key)
  if (prefetched) {
    webTtsPrefetchCache.delete(key)
    const result = await prefetched
    if (result) return result
  }
  return synthesizeWebTtsChunk(chunk)
}

const loadWebTtsChapter = async (displayIndex: number, localOnly = false): Promise<WebTtsChapter> => {
  const cacheKey = `${store.readingBook.bookUrl}\u0000${displayIndex}\u0000${localOnly ? 'local' : 'full'}`
  const cached = webTtsChapterCache.get(cacheKey)
  if (cached) return cached
  const task = (async () => {
    const chapter = catalog.value[displayIndex]
    if (!chapter) throw new Error('Không tìm thấy chương cần đọc')
    let paragraphs = translatedChapters.value[displayIndex]
    const loaded = chapterData.value.find(item => item.index === displayIndex)
    if (!paragraphs && loaded) paragraphs = loaded.content
    if (!paragraphs) {
      const response = await API.getBookContent(store.readingBook.bookUrl, chapter.index)
      if (!response.data.isSuccess) throw new Error(response.data.errorMsg || 'Không tải được chương để đọc')
      paragraphs = splitChapterContent(response.data.data || '')
    }
    const spokenParagraphs = paragraphs.map(paragraph => speechText(paragraph, localOnly))
    return { displayIndex, title: speechText(chapter.title, localOnly), paragraphs: spokenParagraphs }
  })().catch(error => {
    webTtsChapterCache.delete(cacheKey)
    throw error
  })
  webTtsChapterCache.set(cacheKey, task)
  return task
}

const prefetchWebTtsChapterHead = async (
  displayIndex: number,
  localOnly: boolean,
  token: number,
  limit: number,
) => {
  if (displayIndex < 0 || displayIndex >= catalog.value.length || limit <= 0 || token !== ttsPlaybackToken) return
  try {
    const chapter = await loadWebTtsChapter(displayIndex, localOnly)
    if (token !== ttsPlaybackToken) return
    const chapterParagraphs = [chapter.title, ...chapter.paragraphs]
    prefetchWebTtsChunks(splitWebTtsChunks(chapterParagraphs).slice(0, limit), token)
  } catch {
    // The normal chapter loop will surface a load/synthesis error if the user
    // actually reaches this chapter. Prefetch failures should not interrupt
    // the current audio.
  }
}

const waitForWebTtsChapter = async (displayIndex: number, token: number) => {
  for (let attempt = 0; attempt < 160 && token === ttsPlaybackToken; attempt += 1) {
    if (chapterData.value.some(item => item.index === displayIndex) && showContent.value) return true
    await new Promise(resolve => window.setTimeout(resolve, 50))
  }
  return token === ttsPlaybackToken
}

const updateWebTtsPosition = async (
  displayIndex: number,
  startParagraph: number,
  paragraphCount: number,
  token: number,
) => {
  if (token !== ttsPlaybackToken) return
  ttsActiveChapter.value = displayIndex
  ttsActiveParagraph.value = startParagraph === 0 ? -2 : startParagraph - 1
  ttsActiveParagraphCount.value = Math.max(0, paragraphCount - 1)
  if (chapterIndex.value !== displayIndex || !chapterData.value.some(item => item.index === displayIndex)) {
    getContent(displayIndex)
    await waitForWebTtsChapter(displayIndex, token)
  }
  if (token !== ttsPlaybackToken) return
  const position = Math.max(0, startParagraph === 0
    ? 0
    : Math.round(startParagraph / Math.max(1, paragraphCount) * 20_000))
  saveReadingBookProgressToBrowser(displayIndex, position)
  await nextTick()
  if (readMode.value === 'paged' && content.value) {
    const maxScroll = Math.max(0, content.value.scrollWidth - content.value.clientWidth)
    const ratio = startParagraph / Math.max(1, paragraphCount)
    content.value.scrollTo({ left: maxScroll * ratio, behavior: 'smooth' })
  } else {
    const renderedIndex = chapterData.value.findIndex(item => item.index === displayIndex)
    const renderedChapter = chapterRef.value?.[renderedIndex]
    renderedChapter?.scrollToReadedLength(position)
  }
}

const playWebTtsChunk = async (
  chunk: WebTtsChunk,
  token: number,
  displayIndex: number,
  paragraphCount: number,
  prefetchChunks: WebTtsChunk[] = [],
  afterCurrentReady?: () => void,
) => {
  if (token !== ttsPlaybackToken) return
  const result = await getWebTtsChunkSynthesis(chunk)
  if (!result) return
  if (token !== ttsPlaybackToken) return
  prefetchWebTtsChunks(prefetchChunks, token)
  afterCurrentReady?.()
  ttsAudio?.pause()
  const audioUrl = withWebSession(new URL(resolveWebServiceUrl(result.audioUrl)))
  audioUrl.searchParams.set('t', String(Date.now()))
  const audio = new Audio(audioUrl.toString())
  audio.preload = 'auto'
  audio.setAttribute('playsinline', 'true')
  ttsAudio = audio
  ttsAudioReady.value = true
  ttsPaused.value = false
  let lastParagraphIndex = chunk.startParagraph
  const syncActiveParagraph = () => {
    if (token !== ttsPlaybackToken || !Number.isFinite(audio.duration) || audio.duration <= 0) return
    const targetLength = Math.min(chunk.text.length, Math.max(0, audio.currentTime / audio.duration * chunk.text.length))
    let offset = 0
    let paragraphIndex = chunk.startParagraph
    for (const part of chunk.parts) {
      offset += part.text.length + 1
      if (targetLength <= offset) {
        paragraphIndex = part.paragraphIndex
        break
      }
    }
    if (paragraphIndex === lastParagraphIndex) return
    lastParagraphIndex = paragraphIndex
    ttsActiveChapter.value = displayIndex
    ttsActiveParagraph.value = paragraphIndex === 0 ? -2 : paragraphIndex - 1
    void nextTick(() => {
      const renderedIndex = chapterData.value.findIndex(item => item.index === displayIndex)
      const renderedChapter = chapterRef.value?.[renderedIndex]
      if (paragraphIndex > 0) renderedChapter?.scrollToParagraph(paragraphIndex - 1)
    })
  }
  audio.addEventListener('loadedmetadata', syncActiveParagraph)
  audio.addEventListener('timeupdate', syncActiveParagraph)
  await updateWebTtsPosition(displayIndex, chunk.startParagraph, paragraphCount, token)
  if (token !== ttsPlaybackToken) return
  await new Promise<void>((resolve, reject) => {
    audio.onended = () => {
      audio.removeEventListener('loadedmetadata', syncActiveParagraph)
      audio.removeEventListener('timeupdate', syncActiveParagraph)
      resolve()
    }
    audio.onerror = () => {
      audio.removeEventListener('loadedmetadata', syncActiveParagraph)
      audio.removeEventListener('timeupdate', syncActiveParagraph)
      reject(new Error('TTS_AUDIO_PLAYBACK_FAILED'))
    }
    audio.play()
      .then(() => {
        if (token === ttsPlaybackToken) ttsLoading.value = false
      })
      .catch(reject)
  })
}

const skipWebTtsChapter = async (direction: number) => {
  const current = ttsActiveChapter.value ?? chapterIndex.value
  const target = current + direction
  if (target < 0 || target >= catalog.value.length) {
    ElMessage.info(direction < 0 ? 'Đây là chương đầu' : 'Đây là chương cuối')
    return
  }
  const shouldResume = ttsPlaying.value || ttsPaused.value || ttsLoading.value
  stopWebTts()
  getContent(target)
  if (!shouldResume) return
  await waitForWebTtsChapter(target, ttsPlaybackToken)
  if (target === chapterIndex.value) void speakCurrentChapter()
}

const speakCurrentChapter = async () => {
  if (ttsPlaying.value) return
  const token = ++ttsPlaybackToken
  ttsPlaying.value = true
  ttsLoading.value = true
  try {
    const capabilities = await getWebServiceTtsCapabilities(store.readingBook.bookUrl)
    const localOnly = capabilities.engine === 'local' &&
      !/^(zh|ja|ko|ru|ar)\b/i.test(capabilities.language || '')
    for (let displayIndex = chapterIndex.value; displayIndex < catalog.value.length; displayIndex += 1) {
      if (token !== ttsPlaybackToken) return
      const chapter = await loadWebTtsChapter(displayIndex, localOnly)
      // Keep empty/image entries so the TTS paragraph index stays aligned with
      // the exact list rendered by ChapterContent.
      const chapterParagraphs = [chapter.title, ...chapter.paragraphs]
      const chunks = splitWebTtsChunks(chapterParagraphs)
      if (chunks.length === 0) continue
      for (let chunkIndex = 0; chunkIndex < chunks.length; chunkIndex += 1) {
        if (token !== ttsPlaybackToken) return
        const prefetchChunks = chunks.slice(
          chunkIndex + 1,
          chunkIndex + 1 + WEB_TTS_PREFETCH_AHEAD,
        )
        const missingLookahead = WEB_TTS_PREFETCH_AHEAD - prefetchChunks.length
        const prefetchNextChapter = missingLookahead > 0 && displayIndex + 1 < catalog.value.length
          ? () => {
              void prefetchWebTtsChapterHead(
                displayIndex + 1,
                localOnly,
                token,
                missingLookahead,
              )
            }
          : undefined
        await playWebTtsChunk(
          chunks[chunkIndex],
          token,
          displayIndex,
          chapterParagraphs.length,
          prefetchChunks,
          prefetchNextChapter,
        )
        ttsLoading.value = false
      }
    }
    if (token === ttsPlaybackToken) ElMessage.success('Đã đọc hết sách')
  } catch (error: any) {
    if (token !== ttsPlaybackToken) return
    const serverError = error?.response?.data?.error || error?.response?.data?.message
    const code = typeof serverError === 'string' ? serverError : error instanceof Error ? error.message : ''
    ElMessage.error(code ? `Không thể phát TTS từ ứng dụng: ${code}` : 'Không thể phát TTS từ ứng dụng')
  } finally {
    if (token === ttsPlaybackToken) {
      ttsLoading.value = false
      ttsPlaying.value = false
      ttsPaused.value = false
      ttsAudioReady.value = false
      ttsActiveChapter.value = null
      ttsActiveParagraph.value = -1
      ttsActiveParagraphCount.value = 0
      ttsAudio = null
    }
  }
}
const translationStatusText = computed(() => {
  const job = translationJob.value
  if (!job) return ''
  if (job.status === 'translating' || job.status === 'idle') {
    const total = job.totalChunks > 0 ? job.totalChunks : '?'
    return `Đang dịch ${job.currentChunk}/${total}`
  }
  if (job.status === 'translated') return 'Đang đọc bản dịch'
  if (job.status === 'cancelled') return 'Đã hủy dịch'
  if (job.status === 'failed') return job.error || 'Dịch lỗi'
  return ''
})

const getContent = (index: number, reloadChapter = true, chapterPos = 0) => {
  readerError.value = ''
  if (reloadChapter) {
    //展示进度条
    store.setShowContent(false)
    //强制滚回顶层
    if (readMode.value === 'paged') {
      content.value?.scrollTo({ left: 0, behavior: 'auto' })
    } else {
      jump(top.value, { duration: 0 })
    }
    //从Mục lục，按钮切换章节时Lưu进度 预加载时不Lưu
    saveReadingBookProgressToBrowser(index, chapterPos)
    chapterData.value = []
  }
  const bookUrl = store.readingBook.bookUrl
  const chapter = catalog.value[index]
  if (!chapter) {
    readerError.value = 'Không tìm thấy chương cần đọc. Hãy tải lại mục lục.'
    store.setShowContent(true)
    return
  }
  const { title, index: chapterIndex } = chapter

  void loadingWrapper(
    API.getBookContent(bookUrl, chapterIndex).then(
      res => {
        if (res.data.isSuccess) {
          const data = res.data.data?.trim()
          if (!data) {
            readerError.value = 'Nội dung chương đang trống. Hãy thử tải lại.'
            store.setShowContent(true)
            return
          }
          const content = data.split(/\n+/)
          const urlEncodedBookUrl = encodeURIComponent(bookUrl)
          for (let i = 0; i < content.length; i++) {
            if (!/^\s*<img[^>]*src[^>]+>$/.test(content[i])) {
              content[i] = content[i].replace(new RegExp('img src="', 'g'), `img src="/image?url=${urlEncodedBookUrl}&path=`);
            }
          }
          chapterData.value.push({ index, content, title })
          if (reloadChapter) toChapterPos(chapterPos)
          if (reloadChapter && translationReadingEnabled.value) {
            void translateChapterForReading(index)
          }
        } else {
          readerError.value = res.data.errorMsg || 'Không tải được nội dung chương.'
        }
        store.setContentLoading(true)
        noPoint.value = false
        store.setShowContent(true)
      },
      () => {
        readerError.value = 'Không tải được nội dung chương. Hãy kiểm tra kết nối và thử lại.'
        store.setShowContent(true)
      },
    ),
  )
}

// 章节进度跳转和计算
const splitChapterContent = (content: string) =>
  content.split(/\n+/).filter(line => line.length > 0)

const clearTranslationTimer = () => {
  if (translationTimer) {
    window.clearTimeout(translationTimer)
    translationTimer = 0
  }
}

const applyTranslationJob = (
  job: WebServiceTranslationJobResponse,
  displayIndex: number,
) => {
  translationJob.value = job
  if (job.status === 'translated' && job.content) {
    translatedChapters.value = {
      ...translatedChapters.value,
      [displayIndex]: splitChapterContent(job.content),
    }
    ElMessage.success('Đã dịch chương')
  }
  if (job.status === 'failed') {
    ElMessage.error(job.error || 'Không thể dịch chương')
  }
}

const pollTranslationJob = (jobId: string, displayIndex: number) => {
  clearTranslationTimer()
  translationTimer = window.setTimeout(async () => {
    try {
      const job = await getWebServiceTranslationJob(jobId)
      applyTranslationJob(job, displayIndex)
      if (job.status === 'idle' || job.status === 'translating') {
        pollTranslationJob(jobId, displayIndex)
      }
    } catch {
      ElMessage.error('Không thể cập nhật trạng thái dịch')
    }
  }, 1000)
}

const setTranslationReadingEnabled = (enabled: boolean) => {
  translationReadingEnabled.value = enabled
  localStorage.setItem(TRANSLATION_READING_KEY, String(enabled))
}

const ensureSelectedTranslationLanguage = () => {
  const languages = selectedProviderLanguages.value
  if (!languages.includes(selectedTargetLanguage.value)) {
    selectedTargetLanguage.value = languages.includes('vi')
      ? 'vi'
      : languages[0] || ''
  }
  localStorage.setItem(
    TRANSLATION_LANGUAGE_KEY,
    selectedTargetLanguage.value,
  )
}

const reloadTranslationForSelection = async () => {
  translatedChapters.value = {}
  clearTranslationTimer()
  if (isTranslationRunning.value && translationJob.value?.jobId) {
    await cancelWebServiceTranslationJob(translationJob.value.jobId).catch(
      () => undefined,
    )
  }
  translationJob.value = null
  if (translationReadingEnabled.value) {
    await translateChapterForReading(chapterIndex.value)
  }
}

const onTranslationProviderChange = async () => {
  localStorage.setItem(
    TRANSLATION_PROVIDER_KEY,
    selectedTranslationProvider.value,
  )
  ensureSelectedTranslationLanguage()
  await reloadTranslationForSelection()
}

const onTranslationTargetLanguageChange = async () => {
  localStorage.setItem(
    TRANSLATION_LANGUAGE_KEY,
    selectedTargetLanguage.value,
  )
  await reloadTranslationForSelection()
}

const translateChapterForReading = async (displayIndex: number) => {
  if (!translationReadingEnabled.value) return
  if (!selectedTranslationProvider.value || !selectedTargetLanguage.value) return
  const chapter = catalog.value[displayIndex]
  const bookUrl = store.readingBook.bookUrl
  if (!bookUrl || !chapter) return
  const activeJob = translationJob.value
  if (
    activeJob?.jobId &&
    isTranslationRunning.value &&
    (activeJob.bookUrl !== bookUrl || activeJob.chapterIndex !== chapter.index)
  ) {
    await cancelWebServiceTranslationJob(activeJob.jobId).catch(() => undefined)
    clearTranslationTimer()
  }
  translationLoading.value = true
  try {
    const cached = await getWebServiceTranslationContent(
      bookUrl,
      chapter.index,
      selectedTranslationProvider.value,
      selectedTargetLanguage.value,
    )
    if (cached.content) {
      translatedChapters.value = {
        ...translatedChapters.value,
        [displayIndex]: splitChapterContent(cached.content),
      }
      translationJob.value = null
      return
    }
    if (!webServiceStore.policy) {
      await webServiceStore.loadPolicy().catch(() => undefined)
    }
    if (!canUseWebTranslation.value) {
      ElMessage.warning('Chương chưa có bản dịch; Dịch tự động đang tắt trong WebService')
      return
    }
    const job = await createWebServiceTranslationJob({
      bookUrl,
      chapterIndex: chapter.index,
      provider: selectedTranslationProvider.value,
      targetLanguage: selectedTargetLanguage.value,
    })
    applyTranslationJob(job, displayIndex)
    if (job.status === 'idle' || job.status === 'translating') {
      pollTranslationJob(job.jobId, displayIndex)
    }
  } catch {
    ElMessage.error('Không thể đọc hoặc tạo bản dịch cho chương')
  } finally {
    translationLoading.value = false
  }
}

const pretranslateChapters = async () => {
  const bookUrl = store.readingBook.bookUrl
  if (!bookUrl) return
  if (!selectedTranslationProvider.value || !selectedTargetLanguage.value) {
    ElMessage.warning('Hãy chọn provider và ngôn ngữ đích trước')
    translationPanelVisible.value = true
    return
  }
  if (!webServiceStore.policy) {
    await webServiceStore.loadPolicy().catch(() => undefined)
  }
  if (!canUseWebTranslation.value) {
    ElMessage.warning('Dịch tự động đang tắt trong WebService')
    return
  }
  pretranslateLoading.value = true
  try {
    const result = await pretranslateWebServiceChapters({
      bookUrl,
      fromChapter: Math.max(0, chapterIndex.value),
      count: pretranslateCount.value,
      provider: selectedTranslationProvider.value,
      targetLanguage: selectedTargetLanguage.value,
    }) as { jobs?: Array<unknown> }
    const count = result.jobs?.length || pretranslateCount.value
    ElMessage.success(`Đã bắt đầu dịch trước ${count} chương`)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : 'Không thể dịch trước chương')
  } finally {
    pretranslateLoading.value = false
  }
}

const toggleTranslationJob = async () => {
  if (translationReadingEnabled.value) {
    setTranslationReadingEnabled(false)
    translatedChapters.value = {}
    clearTranslationTimer()
    if (isTranslationRunning.value && translationJob.value?.jobId) {
      await cancelWebServiceTranslationJob(translationJob.value.jobId).catch(
        () => undefined,
      )
    }
    translationJob.value = null
    return
  }
  if (isTranslationRunning.value && translationJob.value?.jobId) {
    translationLoading.value = true
    try {
      const job = await cancelWebServiceTranslationJob(translationJob.value.jobId)
      clearTranslationTimer()
      translationJob.value = job
      ElMessage.info('Đã hủy dịch chương')
    } catch {
      ElMessage.error('Không thể hủy dịch chương')
    } finally {
      translationLoading.value = false
    }
    return
  }
  const displayIndex = chapterIndex.value
  setTranslationReadingEnabled(true)
  await translateChapterForReading(displayIndex)
}

const chapter = ref()
const chapterRef = ref()
const toChapterPos = (pos: number) => {
  nextTick(() => {
    if (chapterRef.value.length === 1)
      chapterRef.value[0].scrollToReadedLength(pos)
  })
}

// 60秒Lưu一次进度
const saveBookProgressThrottle = useThrottleFn(
  () => store.saveBookProgress(),
  60000,
)

const onReadedLengthChange = (index: number, pos: number) => {
  saveReadingBookProgressToBrowser(index, pos)
  saveBookProgressThrottle()
}

// 文档标题
watchEffect(() => {
  document.title = catalog.value[chapterIndex.value]?.title || document.title
})

// 阅读记录Lưu浏览器
const saveReadingBookProgressToBrowser = (index: number, pos: number) => {
  // Lưupinia
  chapterIndex.value = index
  chapterPos.value = pos
}

// 进度同步
// 返回导航变化 同步请求会在获取Kệ sách前完成

/**
 * VisibilityChange https://developer.mozilla.org/zh-CN/docs/Web/API/Document/visibilitychange_event
 * 监听Tắt页面 切换tab 返回桌面 等操作
 * 注意不用监听点击链接导航变化 不对Safari<14.5兼容处理
 **/
const onVisibilityChange = () => {
  const _bookProgress = bookProgress.value
  if (document.visibilityState == 'hidden' && _bookProgress) {
    store.saveBookProgress()
  }
}
// 定时同步

// 章节切换
const toNextChapter = () => {
  store.setContentLoading(true)
  const index = chapterIndex.value + 1
  if (typeof catalog.value[index] !== 'undefined') {
    ElMessage({
      message: 'Chương sau',
      type: 'info',
    })
    getContent(index)
    store.saveBookProgress()
  } else {
    ElMessage({
      message: 'Đây là chương cuối',
      type: 'error',
    })
  }
}
const toPreChapter = () => {
  store.setContentLoading(true)
  const index = chapterIndex.value - 1
  if (typeof catalog.value[index] !== 'undefined') {
    ElMessage({
      message: 'Chương trước',
      type: 'info',
    })
    getContent(index)
    store.saveBookProgress()
  } else {
    ElMessage({
      message: 'Đây là chương đầu',
      type: 'error',
    })
  }
}

let canJump = true
let pagedScrollLocked = false
let pagedLoadRequestedAt = 0
const onReaderScroll = () => {
  if (
    readMode.value !== 'paged' ||
    !infiniteLoading.value ||
    isLoading.value ||
    !content.value
  ) {
    return
  }
  const remaining =
    content.value.scrollWidth - content.value.clientWidth - content.value.scrollLeft
  const now = Date.now()
  if (remaining <= content.value.clientWidth / 2 && now - pagedLoadRequestedAt > 500) {
    pagedLoadRequestedAt = now
    loadMore()
  }
}
const turnHorizontalPage = (direction: number) => {
  if (!content.value || pagedScrollLocked) return
  const atStart = content.value.scrollLeft <= 1
  const atEnd =
    content.value.scrollLeft + content.value.clientWidth >=
    content.value.scrollWidth - 1
  if (direction < 0 && atStart) {
    toPreChapter()
    return
  }
  if (direction > 0 && atEnd) {
    toNextChapter()
    return
  }
  pagedScrollLocked = true
  content.value.scrollBy({
    left: direction * content.value.clientWidth,
    behavior: 'smooth',
  })
  window.setTimeout(
    () => (pagedScrollLocked = false),
    Math.max(180, store.config.jumpDuration),
  )
}
const onReaderWheel = (event: WheelEvent) => {
  if (readMode.value !== 'paged') return
  event.preventDefault()
  const delta = Math.abs(event.deltaY) >= Math.abs(event.deltaX)
    ? event.deltaY
    : event.deltaX
  if (delta !== 0) turnHorizontalPage(delta > 0 ? 1 : -1)
}
// 监听方向键
const handleKeyPress = (event: KeyboardEvent) => {
  if (!canJump) return
  switch (event.key) {
    case 'ArrowLeft':
      event.stopPropagation()
      event.preventDefault()
      if (readMode.value === 'paged') turnHorizontalPage(-1)
      else toPreChapter()
      break
    case 'ArrowRight':
      event.stopPropagation()
      event.preventDefault()
      if (readMode.value === 'paged') turnHorizontalPage(1)
      else toNextChapter()
      break
    case 'ArrowUp':
      event.stopPropagation()
      event.preventDefault()
      if (readMode.value === 'paged') {
        turnHorizontalPage(-1)
        break
      }
      if (document.documentElement.scrollTop === 0) {
        ElMessage.warning('Đã ở đầu trang')
      } else {
        canJump = false
        jump(0 - document.documentElement.clientHeight + 100, {
          duration: store.config.jumpDuration,
          callback: () => (canJump = true),
        })
      }
      break
    case 'ArrowDown':
      event.stopPropagation()
      event.preventDefault()
      if (readMode.value === 'paged') {
        turnHorizontalPage(1)
        break
      }
      if (
        document.documentElement.clientHeight +
          document.documentElement.scrollTop ===
        document.documentElement.scrollHeight
      ) {
        ElMessage.warning('Đã ở cuối trang')
      } else {
        canJump = false
        jump(document.documentElement.clientHeight - 100, {
          duration: store.config.jumpDuration,
          callback: () => (canJump = true),
        })
      }
      break
  }
}

// 阻止Mặc định滚动事件
const ignoreKeyPress = (event: KeyboardEvent) => {
  if (event.key === 'ArrowUp' || event.key === 'ArrowDown') {
    event.preventDefault()
    event.stopPropagation()
  }
}

const loadTranslationOptions = async () => {
  if (!webServiceStore.policy) {
    webServiceStore.loadPolicy().catch(() => undefined)
  }
  try {
    const providerList = await getWebServiceTranslationProviders()
    translationProviders.value = providerList.providers
    if (
      !translationProviders.value.some(
        provider => provider.id === selectedTranslationProvider.value,
      )
    ) {
      selectedTranslationProvider.value = providerList.defaultProvider
    }
    if (!selectedTargetLanguage.value) {
      selectedTargetLanguage.value = providerList.defaultTargetLanguage
    }
    localStorage.setItem(
      TRANSLATION_PROVIDER_KEY,
      selectedTranslationProvider.value,
    )
    ensureSelectedTranslationLanguage()
  } catch {
    translationReadingEnabled.value = false
  }
}

onMounted(async () => {
  void loadTranslationOptions()
  await store.loadWebConfig().catch(() => undefined)
  //获取书籍数据
  const bookUrl = sessionStorage.getItem('bookUrl')
  const name = sessionStorage.getItem('bookName')
  const author = sessionStorage.getItem('bookAuthor')
  const chapterIndex = Number(sessionStorage.getItem('chapterIndex') || 0)
  const chapterPos = Number(sessionStorage.getItem('chapterPos') || 0)
  const isSeachBook = sessionStorage.getItem('isSeachBook') === 'true'
  if (isNullOrBlank(bookUrl) || isNullOrBlank(name) || author === null) {
    ElMessage.warning('Thông tin sách trống, sắp tự quay về kệ sách...')
    return setTimeout(toShelf, 500)
  }
  const book: typeof store.readingBook = {
    // @ts-expect-error: bookUrl name author is NON_Blank string here
    bookUrl,
    // @ts-expect-error: bookUrl name author is NON_Blank string here
    name,
    author,
    chapterIndex,
    chapterPos,
    isSeachBook,
  }
  onResize()
  window.addEventListener('resize', onResize)
  readerError.value = ''
  void loadingWrapper(
    store.loadWebCatalog(book).then(chapters => {
      if (chapters.length === 0) {
        throw new Error('Mục lục đang trống.')
      }
      const safeChapterIndex = Math.min(
        Math.max(chapterIndex, 0),
        chapters.length - 1,
      )
      book.chapterIndex = safeChapterIndex
      store.setReadingBook(book)
      getContent(safeChapterIndex, true, chapterPos)
      window.addEventListener('keyup', handleKeyPress)
      window.addEventListener('keydown', ignoreKeyPress)
      // 兼容Safari < 14
      document.addEventListener('visibilitychange', onVisibilityChange)
      //监听Cuối trang加载
      scrollObserver = new IntersectionObserver(onReachBottom, {
        rootMargin: '-100% 0% 20% 0%',
      })
      if (infiniteLoading.value === true) scrollObserver.observe(loading.value)
      //第二次点击同一本书 页面标题不会变化
      document.title = '...'
      document.title = (name as string) + ' | ' + chapters[safeChapterIndex].title
    }).catch(error => {
      readerError.value = error instanceof Error && error.message
        ? error.message
        : 'Không tải được mục lục. Hãy kiểm tra kết nối và thử lại.'
      store.setShowContent(true)
    }),
  )
  initXboxGamepad()
})

onUnmounted(() => {
  clearTranslationTimer()
  window.removeEventListener('keyup', handleKeyPress)
  window.removeEventListener('keydown', ignoreKeyPress)
  window.removeEventListener('resize', onResize)
  // 兼容Safari < 14
  document.removeEventListener('visibilitychange', onVisibilityChange)
  readSettingsVisible.value = false
  popCataVisible.value = false
  scrollObserver?.disconnect()
  scrollObserver = null
  stopWebTts()
})

const addToBookShelfConfirm = async () => {
  const book = store.readingBook
  // 阅读的是Tìm kiếm的书籍 并未在Kệ sách
  if (book.isSeachBook === true) {
    await ElMessageBox.confirm(`Có thêm “${book.name}” vào kệ sách không?`, 'Thêm vào kệ sách', {
      confirmButtonText: 'Xác nhận',
      cancelButtonText: 'Không',
      type: 'info',
      /*
        ElMessageBox.confirmMặc định在触发hashChange事件时自动Tắt
        按下物理返回键时触发hashChange事件
        使用router.push("/")则不会触发hashChange事件
        */
      closeOnHashChange: false,
    })
      .then(() => {
        //选择是，无动作
        isSeachBook.value = false
      })
      .catch(async () => {
        //选择Không，Xóa书籍
        await API.deleteBook(book)
      })
      .finally(() => sessionStorage.removeItem('isSeachBook'))
  }
}
onBeforeRouteLeave(async (to, from, next) => {
  console.log('onBeforeRouteLeave')
  // 弹窗时停止响应按键翻页
  window.removeEventListener('keyup', handleKeyPress)
  await addToBookShelfConfirm()
  next()
})
</script>

<style lang="scss" scoped>
:deep(.pop-setting) {
  margin-left: 68px;
  top: 0;
}

:deep(.pop-cata) {
  margin-left: 10px;
}

.chapter-wrapper {
  padding: 0 4%;

  overflow-x: hidden;

  :deep(.no-point) {
    pointer-events: none;
  }

  .tool-bar {
    position: fixed;
    top: 0;
    left: 50%;
    z-index: 100;

    .tools {
      display: flex;
      flex-direction: column;

      .tool-icon {
        font-size: 18px;
        width: 58px;
        height: 48px;
        text-align: center;
        padding-top: 12px;
        cursor: pointer;
        outline: none;

        .iconfont {
          font-family: iconfont;
          width: 16px;
          height: 16px;
          font-size: 16px;
          margin: 0 auto 6px;
        }

        .icon-text {
          font-size: 12px;
        }
      }
    }
  }

  .read-bar {
    position: fixed;
    bottom: 0;
    right: 50%;
    z-index: 100;

    .tools {
      display: flex;
      flex-direction: column;

      .tool-icon {
        font-size: 18px;
        width: 42px;
        height: 31px;
        padding-top: 12px;
        text-align: center;
        align-items: center;
        cursor: pointer;
        outline: none;
        margin-top: -1px;

        .iconfont {
          font-family: iconfont;
          width: 16px;
          height: 16px;
          font-size: 16px;
          margin: 0 auto 6px;
        }
      }
    }
  }

  .chapter {
    font-family: 'Microsoft YaHei', PingFangSC-Regular, HelveticaNeue-Light,
      'Helvetica Neue Light', sans-serif;
    text-align: left;
    padding: 0 65px;
    min-height: 100vh;
    width: 670px;
    margin: 0 auto;

    .content {
      font-size: 18px;
      line-height: 1.8;
      font-family: 'Microsoft YaHei', PingFangSC-Regular, HelveticaNeue-Light,
        'Helvetica Neue Light', sans-serif;

      .bottom-bar,
      .top-bar {
        height: 64px;
      }

      .reader-state {
        min-height: calc(100vh - 128px);
        display: flex;
        flex-direction: column;
        align-items: center;
        justify-content: center;
        gap: 12px;
        text-align: center;
      }
    }
  }

  &.paged-mode {
    height: 100vh;
    overflow: hidden;

    .chapter {
      height: 100vh;
      min-height: 0;
      overflow-x: auto;
      overflow-y: hidden;
      overscroll-behavior: contain;

      .content {
        height: calc(100vh - 128px);
        margin: 64px 0;
        column-width: var(--reader-content-width);
        column-gap: 130px;
        column-fill: auto;

        > div {
          break-inside: auto;
        }

        .top-bar,
        .bottom-bar {
          height: 1px;
        }
      }
    }
  }
}

.translation-provider-panel {
  display: flex;
  flex-direction: column;
  gap: 10px;

  label {
    color: #666;
    font-size: 12px;
  }

  .el-button {
    margin-top: 4px;
  }

  :deep(.el-select) {
    width: 100%;
  }

  .pretranslate-controls {
    display: flex;
    align-items: center;
    gap: 8px;

    :deep(.el-input-number) {
      width: 96px;
    }

    .el-button {
      flex: 1;
      min-width: 0;
    }
  }
}

.translation-status {
  position: fixed;
  top: 16px;
  right: 16px;
  z-index: 101;
  max-width: min(320px, calc(100vw - 32px));
  padding: 6px 10px;
  border-radius: 6px;
  background: rgba(34, 48, 42, 0.88);
  color: #fff;
  font-size: 12px;
  line-height: 1.4;
  pointer-events: none;
}

.tts-control-panel {
  position: fixed;
  right: 16px;
  bottom: 18px;
  z-index: 103;
  width: min(360px, calc(100vw - 32px));
  padding: 12px 14px;
  border: 1px solid rgba(185, 121, 53, 0.32);
  border-radius: 14px;
  background: rgba(255, 251, 242, 0.96);
  box-shadow: 0 10px 28px rgba(37, 40, 30, 0.2);
  color: #29413d;
  backdrop-filter: blur(12px);
}

.tts-control-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  min-width: 0;
  font-size: 13px;

  strong,
  span {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  span {
    color: #7c6a54;
    font-size: 11px;
  }
}

.tts-progress-track {
  height: 5px;
  margin: 10px 0;
  overflow: hidden;
  border-radius: 999px;
  background: rgba(111, 126, 112, 0.2);
}

.tts-progress-value {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: #b97935;
  transition: width 0.2s ease;
}

.tts-control-actions {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;

  button {
    display: grid;
    place-items: center;
    width: 36px;
    height: 32px;
    padding: 0;
    border: 1px solid rgba(41, 65, 61, 0.22);
    border-radius: 9px;
    background: rgba(255, 255, 255, 0.68);
    color: #29413d;
    cursor: pointer;
    font-size: 18px;
    line-height: 1;

    &:hover:not(:disabled) {
      border-color: #b97935;
      background: #fff1d8;
    }

    &:disabled {
      cursor: not-allowed;
      opacity: 0.45;
    }
  }

  .tts-main-action {
    width: 42px;
    height: 36px;
    border-color: #b97935;
    background: #fff1d8;
    font-size: 16px;
  }
}

.day {
  :deep(.popup) {
    box-shadow:
      0 2px 4px rgba(0, 0, 0, 0.12),
      0 0 6px rgba(0, 0, 0, 0.04);
  }

  :deep(.tool-icon) {
    border: 1px solid rgba(0, 0, 0, 0.1);
    margin-top: -1px;
    color: #000;

    .icon-text {
      color: rgba(0, 0, 0, 0.4);
    }
  }

  :deep(.chapter) {
    border: 1px solid #d8d8d8;
    color: #262626;
  }
}

.night {
  :deep(.popup) {
    box-shadow:
      0 2px 4px rgba(0, 0, 0, 0.48),
      0 0 6px rgba(0, 0, 0, 0.16);
  }

  :deep(.tool-icon) {
    border: 1px solid #444;
    margin-top: -1px;
    color: #666;

    .icon-text {
      color: #666;
    }
  }

  :deep(.chapter) {
    border: 1px solid #444;
    color: #666;
  }

  .tts-control-panel {
    border-color: rgba(230, 184, 102, 0.35);
    background: rgba(35, 40, 38, 0.96);
    color: #f2eee4;

    .tts-control-heading span,
    .tts-control-actions button {
      color: #ddd4c2;
    }

    .tts-control-actions button {
      border-color: rgba(230, 184, 102, 0.36);
      background: rgba(255, 255, 255, 0.08);
    }

    .tts-control-actions .tts-main-action,
    .tts-control-actions button:hover:not(:disabled) {
      background: rgba(185, 121, 53, 0.28);
    }
  }

  :deep(.popper__arrow) {
    background: #666;
  }
}

@media screen and (max-width: 776px) {
  .translation-status {
    top: 58px;
  }

  .tts-control-panel {
    right: 8px;
    bottom: 52px;
    left: 8px;
    width: auto;
  }

  .chapter-wrapper {
    padding: 0;

    .tool-bar {
      left: 0;
      width: 100vw;
      margin-left: 0 !important;

      .tools {
        flex-direction: row;
        justify-content: space-between;

        .tool-icon {
          border: none;
        }
      }
    }

    .read-bar {
      right: 0;
      width: 100vw;
      margin-right: 0 !important;

      .tools {
        flex-direction: row;
        justify-content: space-between;
        padding: 0 15px;

        .tool-icon {
          border: none;
          width: auto;

          .iconfont {
            display: inline-block;
          }
        }
      }
    }

    .chapter {
      width: 100vw !important;
      padding: 0 20px;
      box-sizing: border-box;
    }

    &.paged-mode .chapter .content {
      column-width: calc(100vw - 40px);
      column-gap: 40px;
    }
  }
}
</style>
