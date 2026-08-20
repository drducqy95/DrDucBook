<template>
  <div class="web-service-settings">
    <header class="settings-header">
      <el-button text @click="goBack">{{ t('settingsBack') }}</el-button>
      <div>
        <h1>{{ serviceName || 'WebService' }}</h1>
        <p>{{ instanceText }}</p>
      </div>
      <el-button :loading="loading" @click="refresh">{{ t('refresh') }}</el-button>
    </header>

    <main class="settings-layout">
      <section class="settings-panel">
        <div class="panel-heading">
          <h2>{{ t('features') }}</h2>
          <el-tag type="info">{{ t('webControl') }}</el-tag>
        </div>
        <div class="setting-line">
          <div>
            <strong>Export</strong>
            <span>{{ t('exportDescription') }}</span>
          </div>
          <el-switch
            :model-value="policy?.exportEnabled ?? false"
            :disabled="!canEdit"
            :loading="saving === 'export'"
            @change="updateExportEnabled"
          />
        </div>
        <div class="setting-line">
          <div>
            <strong>{{ t('autoTranslation') }}</strong>
            <span>{{ t('autoTranslationDescription') }}</span>
          </div>
          <el-switch
            :model-value="policy?.autoTranslationEnabled ?? false"
            :disabled="!canEdit"
            :loading="saving === 'translation'"
            @change="updateAutoTranslationEnabled"
          />
        </div>
      </section>

      <section class="settings-panel">
        <div class="panel-heading">
          <h2>{{ t('serviceName') }}</h2>
          <el-tag type="info">{{ t('displayedOnWeb') }}</el-tag>
        </div>
        <p class="readonly-service-name">{{ serviceName || 'WebService' }}</p>
        <p class="settings-note">{{ t('serviceNameNativeOnly') }}</p>
      </section>

      <section class="settings-panel background-panel">
        <div class="panel-heading">
          <h2>{{ t('background') }}</h2>
          <el-tag :type="policy?.backgroundAssetId ? 'success' : 'info'">
            {{ policy?.backgroundAssetId ? t('customBackground') : t('default') }}
          </el-tag>
        </div>

        <div class="background-preview" :class="{ empty: !previewObjectUrl }">
          <div class="background-preview-image" :style="previewImageStyle"></div>
          <span v-if="!previewObjectUrl">{{ t('noBackground') }}</span>
        </div>

        <div class="background-actions">
          <input
            ref="fileInput"
            class="hidden-file"
            type="file"
            accept="image/png,image/jpeg,image/webp"
            @change="onFileChange"
          />
          <el-button :disabled="!canEdit" :loading="saving === 'upload'" @click="pickFile">
            {{ t('chooseImage') }}
          </el-button>
          <el-button
            :disabled="!canEdit || !policy?.backgroundAssetId"
            :loading="saving === 'delete'"
            @click="deleteBackground"
          >
            {{ t('deleteBackground') }}
          </el-button>
          <el-button :disabled="!canEdit" :loading="saving === 'reset'" @click="resetPolicy">
            {{ t('reset') }}
          </el-button>
        </div>

        <div class="background-controls">
          <label>
            {{ t('displayMode') }}
            <el-radio-group
              :model-value="policy?.backgroundFit ?? 'cover'"
              :disabled="!canEdit"
              @change="updateFit"
            >
              <el-radio-button label="cover">{{ t('cover') }}</el-radio-button>
              <el-radio-button label="contain">{{ t('contain') }}</el-radio-button>
            </el-radio-group>
          </label>

          <label>
            {{ t('position') }}
            <el-select
              :model-value="policy?.backgroundPosition ?? 'center'"
              :disabled="!canEdit"
              @change="updatePosition"
            >
              <el-option
                v-for="option in positionOptions"
                :key="option.value"
                :label="option.label"
                :value="option.value"
              />
            </el-select>
          </label>

          <label>
            {{ t('dimLabel') }}
            <el-slider
              :model-value="policy?.backgroundDim ?? 0.22"
              :disabled="!canEdit"
              :min="0"
              :max="0.75"
              :step="0.01"
              @change="updateDim"
            />
          </label>

          <label>
            {{ t('blurLabel') }}
            <el-slider
              :model-value="policy?.backgroundBlur ?? 0"
              :disabled="!canEdit"
              :min="0"
              :max="24"
              :step="1"
              @change="updateBlur"
            />
          </label>
        </div>
      </section>
    </main>
  </div>
</template>

<script setup lang="ts">
import type { CSSProperties } from 'vue'
import {
  computed,
  onBeforeUnmount,
  onMounted,
  ref,
  watch,
} from 'vue'
import { useRouter } from 'vue-router'
import {
  getWebServiceBackgroundBlob,
  type WebServicePolicy,
  type WebServicePolicyPatch,
} from '@/api/webService'
import { useWebServiceStore } from '@/store'
import { t } from '@/i18n'

const MAX_CLIENT_BACKGROUND_BYTES = 5 * 1024 * 1024
type BackgroundFit = WebServicePolicy['backgroundFit']
type BackgroundPosition = WebServicePolicy['backgroundPosition']

const router = useRouter()
const webServiceStore = useWebServiceStore()
const fileInput = ref<HTMLInputElement>()
const loading = ref(false)
const saving = ref('')
const previewObjectUrl = ref('')
let activePreviewUrl = ''

const policy = computed(() => webServiceStore.policy)
const serviceName = computed(() => {
  const instance = webServiceStore.instance
  return typeof instance?.serviceName === 'string' && instance.serviceName.trim()
    ? instance.serviceName.trim()
    : typeof instance?.appName === 'string' ? instance.appName.trim() : ''
})
const canEdit = computed(() => Boolean(policy.value))
const instanceText = computed(() => {
  const instance = webServiceStore.instance
  const appName = typeof instance?.serviceName === 'string' && instance.serviceName.trim()
    ? instance.serviceName.trim()
    : typeof instance?.appName === 'string' ? instance.appName.trim() : ''
  const versionName = typeof instance?.versionName === 'string' ? instance.versionName.trim() : ''
  const httpPort = Number.isFinite(instance?.httpPort) ? instance?.httpPort : null
  const webSocketPort = Number.isFinite(instance?.webSocketPort) ? instance?.webSocketPort : null
  if (!appName || !versionName || httpPort === null || webSocketPort === null) {
    return t('waitingService')
  }
  return `${appName} ${versionName} · HTTP ${httpPort} · WS ${webSocketPort}`
})

const positionOptions = computed<Array<{
  label: string
  value: WebServicePolicy['backgroundPosition']
}>>(() => [
  { label: t('center'), value: 'center' },
  { label: t('top'), value: 'top' },
  { label: t('bottom'), value: 'bottom' },
  { label: t('left'), value: 'left' },
  { label: t('right'), value: 'right' },
  { label: `${t('left')} ${t('top')}`, value: 'left top' },
  { label: `${t('left')} ${t('bottom')}`, value: 'left bottom' },
  { label: `${t('right')} ${t('top')}`, value: 'right top' },
  { label: `${t('right')} ${t('bottom')}`, value: 'right bottom' },
])

const previewImageStyle = computed<CSSProperties>(() => ({
  backgroundImage: previewObjectUrl.value
    ? `linear-gradient(rgba(0, 0, 0, ${policy.value?.backgroundDim ?? 0.22}), rgba(0, 0, 0, ${policy.value?.backgroundDim ?? 0.22})), url("${previewObjectUrl.value}")`
    : undefined,
  backgroundSize: policy.value?.backgroundFit ?? 'cover',
  backgroundPosition: policy.value?.backgroundPosition ?? 'center',
  filter: `blur(${policy.value?.backgroundBlur ?? 0}px)`,
}))

const clearPreviewObjectUrl = () => {
  if (activePreviewUrl) URL.revokeObjectURL(activePreviewUrl)
  activePreviewUrl = ''
  previewObjectUrl.value = ''
}

const loadPreview = async (assetId?: string | null) => {
  clearPreviewObjectUrl()
  if (!assetId) return
  try {
    const blob = await getWebServiceBackgroundBlob(assetId)
    activePreviewUrl = URL.createObjectURL(blob)
    previewObjectUrl.value = activePreviewUrl
  } catch {
    previewObjectUrl.value = ''
  }
}

const refresh = async () => {
  loading.value = true
  try {
    await webServiceStore.loadInstance()
    await webServiceStore.loadPolicy()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : String(error))
  } finally {
    loading.value = false
  }
}

const updatePolicy = async (
  key: string,
  patch: WebServicePolicyPatch,
) => {
  if (!canEdit.value) return
  saving.value = key
  try {
    await webServiceStore.patchPolicy(patch)
  } catch {
    ElMessage.error(t('configReloading'))
    await webServiceStore.loadPolicy().catch(() => undefined)
  } finally {
    saving.value = ''
  }
}

const updateExportEnabled = (value: string | number | boolean) => {
  updatePolicy('export', { exportEnabled: Boolean(value) })
}

const updateAutoTranslationEnabled = (value: string | number | boolean) => {
  updatePolicy('translation', { autoTranslationEnabled: Boolean(value) })
}

const updateFit = (value: string | number | boolean | undefined) => {
  if (value === undefined) return
  updatePolicy('fit', { backgroundFit: String(value) as BackgroundFit })
}

const updatePosition = (value: string) => {
  updatePolicy('position', { backgroundPosition: value as BackgroundPosition })
}

const updateDim = (value: number | number[]) => {
  updatePolicy('dim', { backgroundDim: Number(Array.isArray(value) ? value[0] : value) })
}

const updateBlur = (value: number | number[]) => {
  updatePolicy('blur', { backgroundBlur: Number(Array.isArray(value) ? value[0] : value) })
}

const pickFile = () => {
  fileInput.value?.click()
}

const onFileChange = async (event: Event) => {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  input.value = ''
  if (!['image/png', 'image/jpeg', 'image/webp'].includes(file.type)) {
    ElMessage.warning(t('imageTypeSupport'))
    return
  }
  if (file.size > MAX_CLIENT_BACKGROUND_BYTES) {
    ElMessage.warning(t('imageTooLarge'))
    return
  }
  saving.value = 'upload'
  try {
    await webServiceStore.uploadBackground(file)
    ElMessage.success(t('backgroundUpdated'))
  } catch {
    ElMessage.error(t('backgroundUpdateFailed'))
  } finally {
    saving.value = ''
  }
}

const deleteBackground = async () => {
  saving.value = 'delete'
  try {
    await webServiceStore.deleteBackground()
    clearPreviewObjectUrl()
    ElMessage.success(t('backgroundDeleted'))
  } catch {
    ElMessage.error(t('backgroundDeleteFailed'))
  } finally {
    saving.value = ''
  }
}

const resetPolicy = async () => {
  saving.value = 'reset'
  try {
    await webServiceStore.resetPolicy()
    clearPreviewObjectUrl()
    ElMessage.success(t('settingsReset'))
  } catch {
    ElMessage.error(t('settingsResetFailed'))
  } finally {
    saving.value = ''
  }
}

const goBack = () => {
  if (history.length > 1) {
    router.back()
  } else {
    router.push({ name: 'shelf' })
  }
}

watch(
  () => policy.value?.backgroundAssetId,
  assetId => loadPreview(assetId),
  { immediate: true },
)

onMounted(refresh)
onBeforeUnmount(clearPreviewObjectUrl)
</script>

<style lang="scss" scoped>
.web-service-settings {
  min-height: 100vh;
  box-sizing: border-box;
  padding: 28px;
  color: #22302a;
}

.settings-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  max-width: 1120px;
  margin: 0 auto 24px;

  h1 {
    margin: 0;
    font-size: 26px;
    line-height: 1.2;
  }

  p {
    margin: 6px 0 0;
    color: #66746d;
  }
}

.settings-layout {
  display: grid;
  grid-template-columns: minmax(260px, 380px) minmax(320px, 1fr);
  gap: 18px;
  max-width: 1120px;
  margin: 0 auto;
}

.settings-panel {
  border: 1px solid rgba(42, 64, 54, 0.14);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.92);
  padding: 20px;
}

.panel-heading,
.setting-line,
.background-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
}

.panel-heading h2 {
  margin: 0;
  font-size: 18px;
}

.readonly-service-name {
  margin: 18px 0 6px;
  color: #22302a;
  font-size: 20px;
  font-weight: 700;
  overflow-wrap: anywhere;
}

.settings-note {
  margin: 0;
  color: #66746d;
  line-height: 1.5;
}

.setting-line span {
  display: block;
  color: #66746d;
  line-height: 1.5;
}

.setting-line {
  border-top: 1px solid rgba(42, 64, 54, 0.12);
  padding-top: 16px;
  margin-top: 16px;
}

.background-preview {
  position: relative;
  height: 260px;
  overflow: hidden;
  border-radius: 8px;
  border: 1px dashed rgba(42, 64, 54, 0.24);
  background: #dfe8e3;
  margin: 18px 0;
  display: grid;
  place-items: center;
  color: #66746d;
}

.background-preview-image {
  position: absolute;
  inset: 0;
  background-repeat: no-repeat;
}

.background-preview.empty .background-preview-image {
  display: none;
}

.background-actions {
  justify-content: flex-start;
  flex-wrap: wrap;
}

.hidden-file {
  display: none;
}

.background-controls {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 18px;
  margin-top: 22px;

  label {
    min-width: 0;
    display: grid;
    gap: 8px;
    color: #47564f;
  }

  :deep(.el-select),
  :deep(.el-slider) {
    width: 100%;
    min-width: 0;
  }
}

@media screen and (max-width: 760px) {
  .web-service-settings {
    padding: 18px;
  }

  .settings-header {
    align-items: flex-start;
    flex-direction: column;
  }

  .settings-layout,
  .background-controls {
    grid-template-columns: 1fr;
  }
}
</style>
