<template>
  <WebAppShell>
    <div class="translation-dashboard">
      <div class="dashboard-header">
        <div>
          <h1>{{ t('translationDashboard') }}</h1>
          <p class="dashboard-subtitle">{{ t('memoryStats') }}</p>
        </div>
        <div class="header-actions">
          <el-button :loading="loadingStats" @click="refreshAll">
            {{ t('retry') }}
          </el-button>
        </div>
      </div>

      <!-- Overview Stats Cards -->
      <div class="stats-grid">
        <div class="stat-card">
          <div class="stat-icon">📚</div>
          <div class="stat-content">
            <span class="stat-value">{{ stats.globalDictTerms.toLocaleString() }}</span>
            <span class="stat-label">{{ t('globalTerms') }}</span>
          </div>
        </div>
        <div class="stat-card">
          <div class="stat-icon">📖</div>
          <div class="stat-content">
            <span class="stat-value">{{ stats.projectGlossaryTerms.toLocaleString() }}</span>
            <span class="stat-label">{{ t('projectTerms') }}</span>
          </div>
        </div>
        <div class="stat-card">
          <div class="stat-icon">👤</div>
          <div class="stat-content">
            <span class="stat-value">{{ stats.characterProfiles.toLocaleString() }}</span>
            <span class="stat-label">{{ t('characterProfiles') }}</span>
          </div>
        </div>
        <div class="stat-card">
          <div class="stat-icon">⚔️</div>
          <div class="stat-content">
            <span class="stat-value">{{ stats.factions.toLocaleString() }}</span>
            <span class="stat-label">{{ t('factions') }}</span>
          </div>
        </div>
        <div class="stat-card">
          <div class="stat-icon">🗺️</div>
          <div class="stat-content">
            <span class="stat-value">{{ stats.worldEntries.toLocaleString() }}</span>
            <span class="stat-label">{{ t('worldEntries') }}</span>
          </div>
        </div>
        <div class="stat-card">
          <div class="stat-icon">⏳</div>
          <div class="stat-content">
            <span class="stat-value">{{ stats.storyEvents.toLocaleString() }}</span>
            <span class="stat-label">{{ t('storyEvents') }}</span>
          </div>
        </div>
      </div>

      <!-- Main Tabs -->
      <el-tabs v-model="activeTab" class="dashboard-tabs">
        <!-- Tab 1: Active Jobs & Pretranslate -->
        <el-tab-pane :label="t('activeJobs')" name="jobs">
          <div class="dashboard-section-grid">
            <!-- Active Jobs Section -->
            <el-card class="dashboard-card" shadow="hover">
              <template #header>
                <div class="card-header">
                  <span>{{ t('activeJobs') }}</span>
                  <el-tag v-if="activeJobs.length > 0" type="primary" effect="dark" round>
                    {{ activeJobs.length }}
                  </el-tag>
                </div>
              </template>

              <div v-if="jobs.length === 0" class="empty-state">
                <el-empty :description="t('noActiveJobs')" />
              </div>

              <div v-else class="jobs-list">
                <div v-for="job in jobs" :key="job.jobId" class="job-item">
                  <div class="job-header">
                    <div class="job-title-row">
                      <span class="job-book-name">{{ getBookName(job.bookUrl) }}</span>
                      <span class="job-chapter-idx">Chương {{ job.chapterIndex + 1 }}</span>
                    </div>
                    <div class="job-status-tags">
                      <el-tag :type="getJobStatusTagType(job.status)" size="small">
                        {{ getJobStatusLabel(job.status) }}
                      </el-tag>
                      <el-tag type="info" size="small">{{ getProviderName(job.provider) }}</el-tag>
                    </div>
                  </div>

                  <div class="job-progress-row">
                    <el-progress
                      :percentage="Math.min(100, Math.round((job.progress || 0) * 100))"
                      :status="job.status === 'failed' ? 'exception' : (job.status === 'translated' ? 'success' : '')"
                    />
                    <div class="job-chunks-text" v-if="job.totalChunks > 0">
                      {{ job.currentChunk }} / {{ job.totalChunks }} đoạn
                    </div>
                  </div>

                  <div v-if="job.error" class="job-error">
                    {{ job.error }}
                  </div>

                  <div class="job-actions" v-if="job.status === 'translating'">
                    <el-button size="small" type="danger" plain @click="cancelJob(job.jobId)">
                      {{ t('cancelTranslation') }}
                    </el-button>
                  </div>
                </div>
              </div>
            </el-card>

            <!-- Pretranslate Control Card -->
            <el-card class="dashboard-card" shadow="hover">
              <template #header>
                <div class="card-header">
                  <span>{{ t('pretranslateControl') }}</span>
                </div>
              </template>

              <el-form :model="pretranslateForm" label-position="top">
                <el-form-item :label="t('selectBook')">
                  <el-select
                    v-model="pretranslateForm.bookUrl"
                    filterable
                    class="full-width"
                    :placeholder="t('selectBook')"
                    @change="onPretranslateBookChange"
                  >
                    <el-option
                      v-for="b in bookshelf"
                      :key="b.bookUrl"
                      :label="b.name"
                      :value="b.bookUrl"
                    >
                      <span class="book-opt-title">{{ b.name }}</span>
                      <span class="book-opt-author">{{ b.author }}</span>
                    </el-option>
                  </el-select>
                </el-form-item>

                <el-form-item :label="t('ttsEngine')">
                  <el-select
                    v-model="pretranslateForm.provider"
                    class="full-width"
                    :placeholder="t('translate')"
                  >
                    <el-option
                      v-for="p in providers"
                      :key="p.id"
                      :label="p.name"
                      :value="p.id"
                    />
                  </el-select>
                </el-form-item>

                <div class="form-row-2">
                  <el-form-item :label="t('fromChapter')">
                    <el-input-number
                      v-model="pretranslateForm.fromChapter"
                      :min="1"
                      :max="selectedBookChapterCount || 9999"
                      class="full-width"
                    />
                  </el-form-item>
                  <el-form-item :label="t('chapterCount')">
                    <el-input-number
                      v-model="pretranslateForm.count"
                      :min="1"
                      :max="100"
                      class="full-width"
                    />
                  </el-form-item>
                </div>

                <el-form-item>
                  <el-checkbox v-model="pretranslateForm.forceRetranslate">
                    {{ t('retranslateChapter') }} (Ghi đè cache cũ)
                  </el-checkbox>
                </el-form-item>

                <el-form-item>
                  <el-button
                    type="primary"
                    :loading="submittingPretranslate"
                    :disabled="!pretranslateForm.bookUrl"
                    class="full-width"
                    @click="submitPretranslate"
                  >
                    {{ t('startPretranslate') }}
                  </el-button>
                </el-form-item>
              </el-form>
            </el-card>
          </div>
        </el-tab-pane>

        <!-- Tab 2: Provider Cache Browser -->
        <el-tab-pane :label="t('providerCaches')" name="caches">
          <el-card class="dashboard-card" shadow="hover">
            <div class="browser-controls">
              <div class="browser-select-row">
                <el-select
                  v-model="selectedCacheBookUrl"
                  filterable
                  class="book-select-flex"
                  :placeholder="t('selectBook')"
                  @change="onCacheBookChange"
                >
                  <el-option
                    v-for="b in bookshelf"
                    :key="b.bookUrl"
                    :label="b.name"
                    :value="b.bookUrl"
                  >
                    <span>{{ b.name }}</span>
                    <small class="book-author-meta"> — {{ b.author }}</small>
                  </el-option>
                </el-select>

                <el-input
                  v-model="chapterSearchQuery"
                  clearable
                  placeholder="Tìm kiếm chương..."
                  class="chapter-search-input"
                >
                  <template #prefix>⌕</template>
                </el-input>
              </div>
            </div>

            <div v-if="!selectedCacheBookUrl" class="empty-state">
              <el-empty :description="t('selectBook')" />
            </div>

            <div v-else-if="loadingChapters" class="loading-state">
              <el-skeleton :rows="6" animated />
            </div>

            <div v-else class="chapters-cache-table-wrapper">
              <el-table :data="filteredChapterList" stripe max-height="600">
                <el-table-column prop="index" label="#" width="70">
                  <template #default="{ row }">
                    {{ row.index + 1 }}
                  </template>
                </el-table-column>
                <el-table-column prop="title" label="Tiêu đề chương" min-width="220" />
                <el-table-column :label="t('providerCaches')" min-width="260">
                  <template #default="{ row }">
                    <div class="cache-badge-group">
                      <template v-if="rowCaches[row.index] && rowCaches[row.index].length > 0">
                        <el-tag
                          v-for="c in rowCaches[row.index]"
                          :key="c.provider"
                          size="small"
                          :type="c.isStale ? 'warning' : 'success'"
                          class="cache-item-badge"
                        >
                          {{ c.providerName }} ✓ {{ c.isStale ? '(cũ)' : '' }}
                        </el-tag>
                      </template>
                      <el-tag v-else size="small" type="info">Chưa có cache</el-tag>
                    </div>
                  </template>
                </el-table-column>
                <el-table-column label="Thao tác" width="160" align="right">
                  <template #default="{ row }">
                    <el-button
                      size="small"
                      type="primary"
                      plain
                      @click="triggerChapterTranslate(row.index)"
                    >
                      {{ t('translateNow') }}
                    </el-button>
                  </template>
                </el-table-column>
              </el-table>
            </div>
          </el-card>
        </el-tab-pane>

        <!-- Tab 3: Story Memory & Glossary -->
        <el-tab-pane :label="t('viewStoryMemory')" name="memory">
          <el-card class="dashboard-card" shadow="hover">
            <div class="browser-controls">
              <div class="browser-select-row">
                <el-select
                  v-model="selectedMemoryGroupId"
                  clearable
                  class="group-select"
                  :placeholder="t('allGroups')"
                  @change="onMemoryGroupChange"
                >
                  <el-option
                    v-for="g in customBookGroups"
                    :key="g.groupId"
                    :label="'📚 ' + g.groupName"
                    :value="g.groupId"
                  />
                </el-select>

                <el-select
                  v-model="selectedMemoryBookUrl"
                  filterable
                  class="book-select-flex"
                  :placeholder="t('selectBook')"
                  @change="onMemoryBookChange"
                >
                  <el-option
                    v-for="b in filteredBookshelfForMemory"
                    :key="b.bookUrl"
                    :label="b.name"
                    :value="b.bookUrl"
                  >
                    <span>{{ b.name }}</span>
                    <small class="book-author-meta"> — {{ b.author }}</small>
                  </el-option>
                </el-select>

                <el-switch
                  v-if="selectedMemoryGroupId"
                  v-model="viewSeriesAggregated"
                  :active-text="t('viewSeriesMemory')"
                  @change="onSeriesToggleChange"
                />
              </div>
            </div>

            <div v-if="!selectedMemoryBookUrl && !viewSeriesAggregated" class="empty-state">
              <el-empty :description="t('selectBook')" />
            </div>

            <div v-else-if="loadingMemory" class="loading-state">
              <el-skeleton :rows="6" animated />
            </div>

            <div v-else class="memory-details-container">
              <el-tabs v-model="memorySubTab">
                <!-- SubTab: Glossary Terms -->
                <el-tab-pane :label="`${t('projectTerms')} (${glossaryTerms.length})`" name="glossary">
                  <div v-if="glossaryTerms.length === 0" class="empty-state">
                    <el-empty :description="t('noGlossaryTerms')" />
                  </div>
                  <el-table v-else :data="glossaryTerms" stripe max-height="500">
                    <el-table-column prop="source" label="Từ gốc (Trung)" min-width="160" />
                    <el-table-column prop="target" label="Bản dịch (Việt)" min-width="180" />
                    <el-table-column prop="category" label="Phân loại" width="130">
                      <template #default="{ row }">
                        <el-tag size="small">{{ row.category }}</el-tag>
                      </template>
                    </el-table-column>
                  </el-table>
                </el-tab-pane>

                <!-- SubTab: Characters & Factions -->
                <el-tab-pane :label="`${t('characterProfiles')} & ${t('factions')} (${storyEntities.length})`" name="entities">
                  <div v-if="storyEntities.length === 0" class="empty-state">
                    <el-empty :description="t('noStoryMemory')" />
                  </div>
                  <div v-else class="entities-grid">
                    <div v-for="ent in storyEntities" :key="ent.raw" class="entity-card">
                      <div class="entity-header">
                        <div>
                          <strong class="entity-target">{{ ent.target || ent.raw }}</strong>
                          <small class="entity-raw" v-if="ent.target && ent.target !== ent.raw"> ({{ ent.raw }})</small>
                        </div>
                        <el-tag size="small" :type="getEntityTagType(ent.type)">{{ ent.type }}</el-tag>
                      </div>
                      <p v-if="ent.description" class="entity-desc">{{ ent.description }}</p>
                      <div v-if="ent.aliases && ent.aliases.length > 0" class="entity-aliases">
                        <small>Bí danh: </small>
                        <el-tag v-for="alias in ent.aliases" :key="alias" size="small" type="info" class="alias-tag">
                          {{ alias }}
                        </el-tag>
                      </div>
                    </div>
                  </div>
                </el-tab-pane>

                <!-- SubTab: Relationships -->
                <el-tab-pane :label="`${t('relationships')} (${storyRelationships.length})`" name="relationships">
                  <div v-if="storyRelationships.length === 0" class="empty-state">
                    <el-empty :description="t('noStoryMemory')" />
                  </div>
                  <el-table v-else :data="storyRelationships" stripe max-height="500">
                    <el-table-column prop="source" label="Đối tượng 1" min-width="140" />
                    <el-table-column prop="relationship" label="Quan hệ" min-width="160">
                      <template #default="{ row }">
                        <el-tag size="small" type="warning">{{ row.relationship }}</el-tag>
                      </template>
                    </el-table-column>
                    <el-table-column prop="target" label="Đối tượng 2" min-width="140" />
                    <el-table-column prop="description" label="Ghi chú" min-width="200" />
                  </el-table>
                </el-tab-pane>
              </el-tabs>
            </div>
          </el-card>
        </el-tab-pane>
      </el-tabs>
    </div>
  </WebAppShell>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import WebAppShell from '../components/WebAppShell.vue'
import { t } from '../i18n'
import API from '../api/api'
import type { Book } from '../book.d'
import {
  getWebServiceTranslationMemoryStats,
  getWebServiceGlossary,
  getWebServiceStoryMemory,
  listWebServiceTranslationJobs,
  cancelWebServiceTranslationJob,
  createWebServiceTranslationJob,
  pretranslateWebServiceChapters,
  getWebServiceTranslationProviders,
  getWebServiceProviderCaches,
  getWebServiceBookGroups,
  type WebServiceBookGroupItem,
  type WebServiceTranslationMemoryStatsResponse,
  type WebServiceTranslationJobResponse,
  type WebServiceTranslationProvider,
  type WebServiceProviderCacheInfo,
  type WebServiceGlossaryTermResponse,
  type WebServiceStoryEntityResponse,
  type WebServiceStoryRelationshipResponse,
} from '../api/webService'

const activeTab = ref('jobs')
const memorySubTab = ref('glossary')

// Stats
const loadingStats = ref(false)
const stats = reactive<WebServiceTranslationMemoryStatsResponse>({
  globalDictTerms: 0,
  projectGlossaryTerms: 0,
  characterProfiles: 0,
  factions: 0,
  storyEvents: 0,
  worldEntries: 0,
})

// Bookshelf & Providers
const bookshelf = ref<Book[]>([])
const providers = ref<WebServiceTranslationProvider[]>([])

// Jobs
const jobs = ref<WebServiceTranslationJobResponse[]>([])
const activeJobs = computed(() => jobs.value.filter(j => j.status === 'translating'))
let pollTimer: number | null = null

// Pretranslate Form
const pretranslateForm = reactive({
  bookUrl: '',
  provider: 'quick_translator',
  fromChapter: 1,
  count: 10,
  forceRetranslate: false,
})
const submittingPretranslate = ref(false)
const selectedBookChapterCount = computed(() => {
  const b = bookshelf.value.find(item => item.bookUrl === pretranslateForm.bookUrl)
  return b?.totalChapterNum || 0
})

// Cache Browser
const selectedCacheBookUrl = ref('')
const chapterList = ref<{ index: number; title: string; url: string }[]>([])
const rowCaches = reactive<Record<number, WebServiceProviderCacheInfo[]>>({})
const loadingChapters = ref(false)
const chapterSearchQuery = ref('')

const filteredChapterList = computed(() => {
  if (!chapterSearchQuery.value) return chapterList.value
  const q = chapterSearchQuery.value.toLowerCase()
  return chapterList.value.filter(ch => ch.title.toLowerCase().includes(q) || (ch.index + 1).toString().includes(q))
})

// Memory Details
const bookGroups = ref<WebServiceBookGroupItem[]>([])
const selectedMemoryGroupId = ref<number | undefined>(undefined)
const viewSeriesAggregated = ref(false)
const selectedMemoryBookUrl = ref('')
const loadingMemory = ref(false)
const glossaryTerms = ref<WebServiceGlossaryTermResponse[]>([])
const storyEntities = ref<WebServiceStoryEntityResponse[]>([])
const storyRelationships = ref<WebServiceStoryRelationshipResponse[]>([])

const customBookGroups = computed(() => {
  return bookGroups.value.filter(g => g.groupId > 0)
})

const filteredBookshelfForMemory = computed(() => {
  if (!selectedMemoryGroupId.value) return bookshelf.value
  const gid = selectedMemoryGroupId.value
  return bookshelf.value.filter(b => ((b.group || 0) & gid) > 0)
})

const getBookName = (bookUrl: string) => {
  const b = bookshelf.value.find(item => item.bookUrl === bookUrl)
  return b?.name || bookUrl
}

const getProviderName = (providerId: string) => {
  const p = providers.value.find(item => item.id === providerId)
  return p?.name || providerId
}

const getJobStatusTagType = (status: string) => {
  switch (status) {
    case 'translated': return 'success'
    case 'translating': return 'primary'
    case 'failed': return 'danger'
    case 'cancelled': return 'info'
    default: return 'info'
  }
}

const getJobStatusLabel = (status: string) => {
  switch (status) {
    case 'translated': return 'Hoàn thành'
    case 'translating': return 'Đang dịch'
    case 'failed': return 'Thất bại'
    case 'cancelled': return 'Đã hủy'
    default: return status
  }
}

const getEntityTagType = (type: string) => {
  switch (type.toLowerCase()) {
    case 'character':
    case 'person':
      return 'primary'
    case 'faction':
    case 'sect':
    case 'organization':
      return 'warning'
    case 'location':
    case 'world':
      return 'success'
    default:
      return 'info'
  }
}

const loadStats = async () => {
  loadingStats.value = true
  try {
    const data = await getWebServiceTranslationMemoryStats()
    Object.assign(stats, data)
  } catch (error) {
    console.error('Failed to load stats', error)
  } finally {
    loadingStats.value = false
  }
}

const loadBookshelf = async () => {
  try {
    const resp = await API.getBookShelf()
    if (resp.data.isSuccess && resp.data.data) {
      bookshelf.value = resp.data.data
      if (!pretranslateForm.bookUrl && bookshelf.value.length > 0) {
        pretranslateForm.bookUrl = bookshelf.value[0].bookUrl
      }
    }
  } catch (error) {
    console.error('Failed to load bookshelf', error)
  }
}

const loadProviders = async () => {
  try {
    const data = await getWebServiceTranslationProviders()
    providers.value = data.providers
    if (data.defaultProvider) {
      pretranslateForm.provider = data.defaultProvider
    }
  } catch (error) {
    console.error('Failed to load providers', error)
  }
}

const loadJobs = async () => {
  try {
    const data = await listWebServiceTranslationJobs()
    jobs.value = data.jobs
  } catch (error) {
    console.error('Failed to load jobs', error)
  }
}

const cancelJob = async (jobId: string) => {
  try {
    await cancelWebServiceTranslationJob(jobId)
    ElMessage.success('Đã hủy tiến trình dịch')
    await loadJobs()
  } catch (error) {
    ElMessage.error('Không thể hủy tiến trình')
  }
}

const onPretranslateBookChange = (url: string) => {
  const b = bookshelf.value.find(item => item.bookUrl === url)
  if (b) {
    pretranslateForm.fromChapter = (b.durChapterIndex || 0) + 1
  }
}

const submitPretranslate = async () => {
  if (!pretranslateForm.bookUrl) return
  submittingPretranslate.value = true
  try {
    await pretranslateWebServiceChapters({
      bookUrl: pretranslateForm.bookUrl,
      fromChapter: pretranslateForm.fromChapter - 1,
      count: pretranslateForm.count,
      provider: pretranslateForm.provider,
      forceRetranslate: pretranslateForm.forceRetranslate,
    })
    ElMessage.success(t('pretranslateStarted').replace('{count}', String(pretranslateForm.count)))
    await loadJobs()
  } catch (error) {
    ElMessage.error(t('cannotPretranslateChapters'))
  } finally {
    submittingPretranslate.value = false
  }
}

const onCacheBookChange = async (bookUrl: string) => {
  if (!bookUrl) return
  loadingChapters.value = true
  chapterList.value = []
  Object.keys(rowCaches).forEach(k => delete rowCaches[Number(k)])
  try {
    const resp = await API.getChapterList(bookUrl)
    if (resp.data.isSuccess && resp.data.data) {
      chapterList.value = resp.data.data
      // Batch fetch first 30 chapters caches
      const sample = chapterList.value.slice(0, 30)
      for (const ch of sample) {
        try {
          const cacheData = await getWebServiceProviderCaches(bookUrl, ch.index)
          rowCaches[ch.index] = cacheData.caches
        } catch {
          // ignore individual cache failures
        }
      }
    }
  } catch (error) {
    console.error('Failed to load chapters', error)
  } finally {
    loadingChapters.value = false
  }
}

const triggerChapterTranslate = async (chapterIndex: number) => {
  if (!selectedCacheBookUrl.value) return
  try {
    await createWebServiceTranslationJob({
      bookUrl: selectedCacheBookUrl.value,
      chapterIndex: chapterIndex,
      forceRetranslate: true,
      provider: pretranslateForm.provider,
    })
    ElMessage.success('Đã gửi yêu cầu dịch chương ' + (chapterIndex + 1))
    await loadJobs()
  } catch (error) {
    ElMessage.error(t('cannotTranslateChapter'))
  }
}

const loadBookGroups = async () => {
  try {
    const data = await getWebServiceBookGroups()
    bookGroups.value = data
  } catch (error) {
    console.error('Failed to load book groups', error)
  }
}

const onMemoryGroupChange = async () => {
  if (viewSeriesAggregated.value && selectedMemoryGroupId.value) {
    await loadAggregatedSeriesMemory(selectedMemoryGroupId.value)
  } else {
    const books = filteredBookshelfForMemory.value
    if (books.length > 0 && !books.some(b => b.bookUrl === selectedMemoryBookUrl.value)) {
      selectedMemoryBookUrl.value = books[0].bookUrl
      await onMemoryBookChange(selectedMemoryBookUrl.value)
    }
  }
}

const onSeriesToggleChange = async (val: string | number | boolean) => {
  const isAggregated = Boolean(val)
  if (isAggregated && selectedMemoryGroupId.value) {
    await loadAggregatedSeriesMemory(selectedMemoryGroupId.value)
  } else if (selectedMemoryBookUrl.value) {
    await onMemoryBookChange(selectedMemoryBookUrl.value)
  }
}

const loadAggregatedSeriesMemory = async (groupId: number) => {
  loadingMemory.value = true
  glossaryTerms.value = []
  storyEntities.value = []
  storyRelationships.value = []
  try {
    const story = await getWebServiceStoryMemory({ groupId })
    storyEntities.value = story.entities
    storyRelationships.value = story.relationships
  } catch (error) {
    console.error('Failed to load series memory', error)
  } finally {
    loadingMemory.value = false
  }
}

const onMemoryBookChange = async (bookUrl: string) => {
  if (!bookUrl) return
  if (viewSeriesAggregated.value) {
    viewSeriesAggregated.value = false
  }
  loadingMemory.value = true
  glossaryTerms.value = []
  storyEntities.value = []
  storyRelationships.value = []
  try {
    const [glossary, story] = await Promise.all([
      getWebServiceGlossary(bookUrl),
      getWebServiceStoryMemory({ bookUrl }),
    ])
    glossaryTerms.value = glossary.terms
    storyEntities.value = story.entities
    storyRelationships.value = story.relationships
  } catch (error) {
    console.error('Failed to load memory', error)
  } finally {
    loadingMemory.value = false
  }
}

const refreshAll = async () => {
  await Promise.all([
    loadStats(),
    loadBookshelf(),
    loadBookGroups(),
    loadProviders(),
    loadJobs(),
  ])
}

onMounted(() => {
  void refreshAll()
  pollTimer = window.setInterval(() => {
    if (activeJobs.value.length > 0) {
      void loadJobs()
    }
  }, 3000)
})

onUnmounted(() => {
  if (pollTimer) clearInterval(pollTimer)
})
</script>

<style scoped>
.translation-dashboard {
  max-width: 1200px;
  margin: 0 auto;
  padding: 24px 16px 64px;
}

.dashboard-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 24px;
}

.dashboard-header h1 {
  font-size: 26px;
  font-weight: 700;
  margin: 0 0 6px;
  color: var(--el-text-color-primary);
}

.dashboard-subtitle {
  font-size: 14px;
  color: var(--el-text-color-secondary);
  margin: 0;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(170px, 1fr));
  gap: 14px;
  margin-bottom: 28px;
}

.stat-card {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 16px;
  background: var(--el-bg-color-overlay);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 12px;
  transition: transform 0.2s, box-shadow 0.2s;
}

.stat-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 16px rgba(0, 0, 0, 0.06);
}

.stat-icon {
  font-size: 28px;
  width: 44px;
  height: 44px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--el-fill-color-light);
  border-radius: 10px;
}

.stat-content {
  display: flex;
  flex-direction: column;
}

.stat-value {
  font-size: 20px;
  font-weight: 700;
  color: var(--el-color-primary);
  line-height: 1.2;
}

.stat-label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-top: 2px;
}

.dashboard-tabs {
  margin-top: 12px;
}

.dashboard-section-grid {
  display: grid;
  grid-template-columns: 3fr 2fr;
  gap: 20px;
}

@media (max-width: 860px) {
  .dashboard-section-grid {
    grid-template-columns: 1fr;
  }
}

.dashboard-card {
  border-radius: 12px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-weight: 600;
  font-size: 16px;
}

.jobs-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.job-item {
  padding: 14px;
  background: var(--el-fill-color-blank);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;
}

.job-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.job-title-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.job-book-name {
  font-weight: 600;
  font-size: 14px;
  color: var(--el-text-color-primary);
}

.job-chapter-idx {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.job-status-tags {
  display: flex;
  gap: 6px;
}

.job-progress-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin: 8px 0;
}

.job-progress-row :deep(.el-progress) {
  flex: 1;
}

.job-chunks-text {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  white-space: nowrap;
}

.job-error {
  font-size: 12px;
  color: var(--el-color-danger);
  margin-top: 4px;
}

.job-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 8px;
}

.full-width {
  width: 100%;
}

.form-row-2 {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.book-opt-title {
  font-weight: 600;
}

.book-opt-author {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-left: 8px;
}

.browser-controls {
  margin-bottom: 16px;
}

.browser-select-row {
  display: flex;
  gap: 14px;
  flex-wrap: wrap;
}

.group-select {
  width: 200px;
}

.book-select-flex {
  flex: 1;
  min-width: 240px;
}

.book-author-meta {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.chapter-search-input {
  width: 240px;
}

.cache-badge-group {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.cache-item-badge {
  font-size: 11px;
}

.empty-state, .loading-state {
  padding: 32px 0;
  text-align: center;
}

.entities-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 14px;
  padding: 12px 0;
}

.entity-card {
  padding: 14px;
  background: var(--el-fill-color-blank);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;
}

.entity-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 6px;
}

.entity-target {
  font-size: 15px;
  color: var(--el-text-color-primary);
}

.entity-raw {
  font-size: 13px;
  color: var(--el-text-color-secondary);
}

.entity-desc {
  font-size: 13px;
  color: var(--el-text-color-regular);
  margin: 6px 0;
  line-height: 1.4;
}

.entity-aliases {
  display: flex;
  align-items: center;
  gap: 4px;
  flex-wrap: wrap;
  margin-top: 6px;
}

.alias-tag {
  font-size: 11px;
}
</style>
