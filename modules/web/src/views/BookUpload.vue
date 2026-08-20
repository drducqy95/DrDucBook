<template>
  <div class="upload-page">
    <section class="upload-head glass-panel"><div><span class="eyebrow">{{ t('uploadEyebrow') }}</span><h1>{{ t('uploadTitle') }}</h1><p>{{ t('uploadDescription') }}</p></div><el-button @click="router.push({ name: 'shelf' })">{{ t('backHome') }}</el-button></section>
    <section class="upload-box glass-panel" :class="{ dragging }" @dragover.prevent="dragging = true" @dragleave.prevent="dragging = false" @drop.prevent="onDrop">
      <input ref="fileInput" type="file" multiple accept=".txt,.epub,.umd,.pdf,.mobi,.azw,.azw3" hidden @change="onFileChange" />
      <div class="upload-icon">↥</div><h2>{{ t('dropFiles') }}</h2><p>{{ t('supportedFormats') }}</p><el-button type="primary" @click="chooseFiles">{{ t('chooseFiles') }}</el-button>
    </section>
    <section v-if="items.length" class="upload-list glass-panel"><div class="list-heading"><h2>{{ t('uploadProgress') }}</h2><el-button text @click="items = []">{{ t('clearList') }}</el-button></div><article v-for="item in items" :key="item.id" class="upload-item"><div class="file-name"><span class="file-type">{{ extension(item.file.name) }}</span><div><strong>{{ item.file.name }}</strong><small>{{ formatSize(item.file.size) }}</small></div></div><div class="file-progress"><el-progress :percentage="item.progress" :status="item.status === 'error' ? 'exception' : item.status === 'done' ? 'success' : undefined" /><span>{{ item.message }}</span></div></article></section>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { uploadLocalBook } from '@/api/webService'
import { t } from '@/i18n'

const router = useRouter()
const fileInput = ref<HTMLInputElement>()
const dragging = ref(false)
type UploadItem = { id: string; file: File; progress: number; status: 'queued' | 'uploading' | 'done' | 'error'; message: string }
const items = ref<UploadItem[]>([])
const allowed = new Set(['txt', 'epub', 'umd', 'pdf', 'mobi', 'azw', 'azw3'])

const extension = (name: string) => name.split('.').pop()?.toUpperCase() || 'FILE'
const formatSize = (size: number) => size > 1024 * 1024 ? `${(size / 1024 / 1024).toFixed(1)} MB` : `${Math.max(1, Math.round(size / 1024))} KB`
const normalizeFiles = (files: File[]) => files.filter(file => allowed.has(file.name.split('.').pop()?.toLowerCase() || '') && file.size <= 512 * 1024 * 1024)
const chooseFiles = () => fileInput.value?.click()
const enqueue = (files: File[]) => {
  const valid = normalizeFiles(files)
  const rejected = files.length - valid.length
  if (rejected) ElMessage.warning(`${rejected} ${t('skippedFiles')}`)
  const added = valid.map(file => ({ id: `${file.name}-${file.lastModified}-${Math.random()}`, file, progress: 0, status: 'queued' as const, message: t('waiting') }))
  items.value.push(...added)
  void processQueue()
}
const processQueue = async () => {
  const active = items.value.filter(item => item.status === 'uploading').length
  const next = items.value.find(item => item.status === 'queued')
  if (!next || active >= 2) return
  next.status = 'uploading'; next.message = t('uploading')
  try {
    await uploadLocalBook(next.file, value => { next.progress = value })
    next.progress = 100; next.status = 'done'; next.message = t('importedToShelf')
  } catch (error) {
    next.status = 'error'; next.message = error instanceof Error ? error.message : t('importFailed')
  } finally {
    void processQueue()
  }
  if (items.value.some(item => item.status === 'queued')) void processQueue()
}
const onDrop = (event: DragEvent) => { dragging.value = false; enqueue(Array.from(event.dataTransfer?.files || [])) }
const onFileChange = (event: Event) => { enqueue(Array.from((event.target as HTMLInputElement).files || [])); if (fileInput.value) fileInput.value.value = '' }
</script>

<style scoped>
.upload-page { width: min(980px, calc(100% - 36px)); margin: 0 auto; padding: 30px 0 54px; }.glass-panel { border: 1px solid rgba(255,255,255,.55); background: rgba(249,252,250,.86); box-shadow: 0 14px 34px rgba(18,51,50,.1); backdrop-filter: blur(12px); }.upload-head { display: flex; justify-content: space-between; align-items: end; gap: 20px; padding: 30px; border-radius: 20px; background: linear-gradient(115deg, rgba(20,74,75,.92), rgba(42,108,100,.76)); color: white; }.eyebrow { color: #f1d49b; font-size: 11px; font-weight: 800; letter-spacing: .13em; }.upload-head h1 { margin: 8px 0; font: 600 36px Georgia,serif; }.upload-head p { max-width: 650px; margin: 0; color: rgba(255,255,255,.78); }.upload-box { margin-top: 22px; padding: 52px 20px; border: 2px dashed rgba(27,87,83,.28); border-radius: 18px; text-align: center; }.upload-box.dragging { border-color: #c28245; background: rgba(255,249,232,.9); }.upload-icon { color: #b97b3a; font-size: 38px; }.upload-box h2 { margin: 8px 0; color: #1a4845; }.upload-box p { margin: 0 0 18px; color: #72837f; }.upload-list { margin-top: 22px; padding: 20px; border-radius: 16px; }.list-heading { display: flex; justify-content: space-between; align-items: center; }.list-heading h2 { margin: 0; color: #1a4845; font: 600 22px Georgia,serif; }.upload-item { display: grid; grid-template-columns: minmax(0,1fr) minmax(250px,1fr); gap: 20px; align-items: center; padding: 15px 0; border-bottom: 1px solid rgba(28,78,73,.1); }.upload-item:last-child { border-bottom: 0; }.file-name { display: flex; align-items: center; gap: 10px; min-width: 0; }.file-type { display: grid; place-items: center; width: 42px; height: 42px; border-radius: 10px; background: #e4eee9; color: #2c736d; font-size: 10px; font-weight: 800; }.file-name strong,.file-name small { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }.file-name small,.file-progress span { color: #74847f; font-size: 12px; }.file-progress span { display: block; margin-top: 5px; }
@media(max-width:700px){.upload-page{width:min(100% - 22px,600px);padding-top:18px}.upload-head{display:block;padding:23px 20px}.upload-head h1{font-size:29px}.upload-head .el-button{margin-top:16px}.upload-item{grid-template-columns:1fr;gap:10px}}
</style>
