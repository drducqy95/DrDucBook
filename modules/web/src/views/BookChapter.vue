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
            <strong>Đọc bản dịch</strong>
            <label>Provider</label>
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
            <label>Ngôn ngữ đích</label>
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
            <el-button plain :loading="ttsLoading" :disabled="noPoint || chapterData.length === 0" @click="speakCurrentChapter">
              Nghe chương hiện tại
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
  getWebServiceTranslationContent,
  getWebServiceTranslationJob,
  getWebServiceTranslationProviders,
  synthesizeWebServiceTts,
  type WebServiceTranslationProvider,
  type WebServiceTranslationJobResponse,
} from '@/api/webService'
import { useLoading } from '@/hooks/loading'
import { useThrottleFn } from '@vueuse/shared'
import { isNullOrBlank } from '@/utils/utils'
import { initXboxGamepad } from '@/utils/xboxGamepad'
import { getReaderPreferences } from '@/utils/clientPreferences'

const content = ref()
// loading spinner
const { isLoading, loadingWrapper } = useLoading(content, 'Đang tải thông tin')
const store = useBookStore()
const webServiceStore = useWebServiceStore()
const ttsLoading = ref(false)
let ttsAudio: HTMLAudioElement | null = null

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

const speakCurrentChapter = async () => {
  const text = chapterData.value
    .map(item => `${item.title}\n${(translatedChapters.value[item.index] ?? item.content).join('\n')}`)
    .join('\n\n')
    .trim()
    .slice(0, 20_000)
  if (!text) return
  ttsLoading.value = true
  try {
    const result = await synthesizeWebServiceTts(text)
    ttsAudio?.pause()
    const base = /^https?:\/\//i.test(result.audioUrl) ? '' : window.location.origin
    ttsAudio = new Audio(`${base}${result.audioUrl}${result.audioUrl.includes('?') ? '&' : '?'}t=${Date.now()}`)
    await ttsAudio.play()
  } catch {
    ElMessage.error('Không thể phát TTS từ ứng dụng')
  } finally {
    ttsLoading.value = false
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
  ttsAudio?.pause()
  ttsAudio = null
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

  :deep(.popper__arrow) {
    background: #666;
  }
}

@media screen and (max-width: 776px) {
  .translation-status {
    top: 58px;
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
