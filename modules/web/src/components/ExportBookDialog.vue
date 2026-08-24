<template>
  <el-dialog
    :model-value="modelValue"
    :title="t('exportBook')"
    width="min(94vw, 560px)"
    class="export-book-dialog"
    destroy-on-close
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <div v-if="book" class="export-dialog-content">
      <!-- Book Summary Header -->
      <div class="export-book-header">
        <div class="book-cover-wrap">
          <img
            v-if="book.coverUrl"
            :src="proxyCover(book.coverUrl)"
            :alt="book.name"
            class="book-cover"
            @error="coverError = true"
          />
          <div v-else class="book-cover-placeholder">📖</div>
        </div>
        <div class="book-info">
          <h3 class="book-title">{{ book.name }}</h3>
          <p class="book-author">{{ book.author || t('unknownAuthor') }}</p>
          <span v-if="book.totalChapterNum" class="chapter-badge">
            {{ book.totalChapterNum }} {{ t('chapters') }}
          </span>
        </div>
      </div>

      <!-- Warning if Export is disabled in policy -->
      <el-alert
        v-if="!exportEnabled"
        type="warning"
        show-icon
        :closable="false"
        class="export-alert"
      >
        <template #title>
          <div class="alert-content">
            <span>{{ t('exportDisabledHint') }}</span>
            <el-button
              type="primary"
              size="small"
              :loading="enablingExport"
              @click="enableExport"
            >
              {{ t('enableExportNow') }}
            </el-button>
          </div>
        </template>
      </el-alert>

      <!-- Settings Form -->
      <el-form label-position="top" class="export-form">
        <!-- Format Selection -->
        <el-form-item :label="t('exportFormat')">
          <el-radio-group v-model="selectedFormat" class="format-radio-group">
            <el-radio-button value="epub3">EPUB 3</el-radio-button>
            <el-radio-button value="epub2">EPUB 2</el-radio-button>
            <el-radio-button value="pdf">PDF</el-radio-button>
            <el-radio-button value="txt">TXT</el-radio-button>
            <el-radio-button value="html">HTML</el-radio-button>
            <el-radio-button value="cbz">CBZ</el-radio-button>
          </el-radio-group>
        </el-form-item>

        <!-- Content Source Selection -->
        <el-form-item :label="t('exportContentSource')">
          <el-select v-model="contentSource" class="full-width">
            <el-option value="translation" :label="t('contentSourceTranslation')" />
            <el-option value="original" :label="t('contentSourceOriginal')" />
            <el-option value="both" :label="t('contentSourceBoth')" />
          </el-select>
        </el-form-item>

        <!-- Chapter Scope -->
        <el-form-item :label="t('exportScope')">
          <el-radio-group v-model="scopeType" class="scope-radio-group">
            <el-radio value="all">{{ t('scopeAll') }}</el-radio>
            <el-radio value="custom">{{ t('scopeCustom') }}</el-radio>
          </el-radio-group>
          <el-input
            v-if="scopeType === 'custom'"
            v-model="customScope"
            :placeholder="t('scopeCustomPlaceholder')"
            class="scope-input"
            clearable
          />
        </el-form-item>

        <!-- Image Optimization (for EPUB/PDF/HTML/CBZ) -->
        <el-form-item v-if="selectedFormat !== 'txt'" :label="t('imageOptimization')">
          <el-select v-model="imageOptimization" class="full-width">
            <el-option value="balanced" :label="t('imageOptBalanced')" />
            <el-option value="original" :label="t('imageOptOriginal')" />
            <el-option value="small" :label="t('imageOptCompact')" />
          </el-select>
        </el-form-item>
      </el-form>
    </div>

    <template #footer>
      <div class="dialog-footer">
        <el-button :disabled="loading" @click="$emit('update:modelValue', false)">
          {{ t('settingsBack') || 'Đóng' }}
        </el-button>
        <el-button
          type="primary"
          :loading="loading"
          :disabled="!exportEnabled"
          @click="startExport"
        >
          {{ loading ? t('exporting') : t('exportStart') }}
        </el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { t } from '@/i18n'
import {
  downloadWebServiceExportEbook,
  patchWebServicePolicy,
} from '@/api/webService'
import API from '@/api/api'
import { useWebServiceStore } from '@/store'

export type ExportBookTarget = {
  name: string
  author?: string
  bookUrl: string
  totalChapterNum?: number
  coverUrl?: string
}

const props = defineProps<{
  modelValue: boolean
  book: ExportBookTarget | null
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
}>()

const webServiceStore = useWebServiceStore()

const selectedFormat = ref<'epub3' | 'epub2' | 'pdf' | 'txt' | 'html' | 'cbz'>('epub3')
const contentSource = ref<'translation' | 'original' | 'both'>('translation')
const scopeType = ref<'all' | 'custom'>('all')
const customScope = ref('')
const imageOptimization = ref<'original' | 'balanced' | 'small'>('balanced')
const loading = ref(false)
const enablingExport = ref(false)
const coverError = ref(false)

const exportEnabled = computed(() => webServiceStore.policy?.exportEnabled ?? false)

watch(
  () => props.modelValue,
  visible => {
    if (visible && !webServiceStore.policy) {
      webServiceStore.loadPolicy().catch(() => undefined)
    }
  },
)

const proxyCover = (url: string) => {
  if (coverError.value || !url) return ''
  return API.getProxyCoverUrl(url)
}

const enableExport = async () => {
  enablingExport.value = true
  try {
    const etag = webServiceStore.policy?.etag || ''
    await patchWebServicePolicy({ exportEnabled: true }, etag)
    await webServiceStore.loadPolicy()
    ElMessage.success('Đã kích hoạt chức năng Export')
  } catch {
    ElMessage.error('Không thể bật Export. Vui lòng bật trong Cài đặt WebService.')
  } finally {
    enablingExport.value = false
  }
}

const saveBlob = (blob: Blob, fileName: string) => {
  const url = window.URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = fileName
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  window.URL.revokeObjectURL(url)
}

const startExport = async () => {
  if (!props.book?.bookUrl) return
  if (!exportEnabled.value) {
    ElMessage.warning(t('exportDisabled'))
    return
  }

  const scope = scopeType.value === 'custom' ? customScope.value.trim() : 'all'
  if (scopeType.value === 'custom' && !scope) {
    ElMessage.warning(t('scopeCustomPlaceholder'))
    return
  }

  loading.value = true
  try {
    const download = await downloadWebServiceExportEbook({
      bookUrl: props.book.bookUrl,
      format: selectedFormat.value,
      scope: scope || 'all',
      contentSource: contentSource.value,
      imageOptimization: imageOptimization.value,
    })

    const ext = selectedFormat.value === 'epub2' || selectedFormat.value === 'epub3' ? 'epub' : selectedFormat.value
    const safeName = (props.book.name || 'book').replace(/[\\/:*?"<>|]/g, '_')
    saveBlob(download.blob, download.fileName || `${safeName}.${ext}`)
    ElMessage.success(t('exportSuccess'))
    emit('update:modelValue', false)
  } catch (error) {
    const msg = error instanceof Error ? error.message : ''
    ElMessage.error(msg === 'EXPORT_DISABLED' ? t('exportDisabled') : t('exportFailed'))
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.export-dialog-content {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.export-book-header {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 12px 14px;
  background: rgba(0, 0, 0, 0.03);
  border-radius: 12px;
}

.book-cover-wrap {
  width: 48px;
  height: 66px;
  flex-shrink: 0;
  border-radius: 6px;
  overflow: hidden;
  background: #e2e8e5;
  display: grid;
  place-items: center;
}

.book-cover {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.book-cover-placeholder {
  font-size: 24px;
}

.book-info {
  flex: 1;
  min-width: 0;
}

.book-title {
  margin: 0 0 4px;
  font-size: 15px;
  font-weight: 600;
  color: #173d3c;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.book-author {
  margin: 0 0 6px;
  font-size: 13px;
  color: #64748b;
}

.chapter-badge {
  display: inline-block;
  padding: 2px 8px;
  background: #e6f0ed;
  color: #165351;
  border-radius: 99px;
  font-size: 11px;
  font-weight: 600;
}

.export-alert {
  margin-bottom: 6px;
}

.alert-content {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  flex-wrap: wrap;
  width: 100%;
}

.export-form {
  margin-top: 4px;
}

.full-width {
  width: 100%;
}

.format-radio-group {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.scope-radio-group {
  margin-bottom: 8px;
}

.scope-input {
  margin-top: 6px;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}
</style>
