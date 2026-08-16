<template>
  <div class="app-shell" :class="{ 'reader-shell': isReader }">
    <header class="app-header" :class="{ 'is-reader-header': isReader }">
      <div class="brand" role="button" tabindex="0" @click="goHome" @keyup.enter="goHome">
        <span class="brand-mark">D</span>
        <span>
          <strong>DrDucBook</strong>
          <small>Kho sách của bạn</small>
        </span>
      </div>

      <nav class="desktop-nav" aria-label="Điều hướng chính">
        <el-dropdown trigger="click">
          <button class="nav-button">Danh sách <span>⌄</span></button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item @click="goShelf({ status: 'updating' })">Truyện đang cập nhật</el-dropdown-item>
              <el-dropdown-item @click="goShelf({ status: 'completed' })">Truyện đã hoàn thành</el-dropdown-item>
              <el-dropdown-item @click="goShelf({})">Toàn bộ giá sách</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
        <el-dropdown trigger="click">
          <button class="nav-button">Thể loại <span>⌄</span></button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item v-for="item in typeItems" :key="item.value" @click="goShelf({ type: item.value })">
                {{ item.label }}
              </el-dropdown-item>
              <el-dropdown-item @click="goShelf({})">Tất cả thể loại</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
        <el-dropdown trigger="click">
          <button class="nav-button">Cài đặt hiển thị <span>⌄</span></button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item @click="showDisplaySettings = true">Mở cài đặt</el-dropdown-item>
              <el-dropdown-item @click="showDisplaySettings = true">Hình nền & độ rõ chữ</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
        <button class="nav-button" @click="router.push({ name: 'book-home' })">Nguồn sách</button>
        <button class="nav-button" @click="router.push({ name: 'upload' })">Tải sách lên</button>
        <button class="nav-button" @click="router.push({ name: 'rss-home' })">Nguồn RSS</button>
      </nav>

      <div class="header-actions">
        <el-input
          v-model="searchText"
          class="header-search"
          clearable
          placeholder="Tìm truyện..."
          @keyup.enter="submitSearch"
        >
          <template #prefix>⌕</template>
        </el-input>
        <button class="mobile-menu" aria-label="Mở menu" @click="showMobileMenu = !showMobileMenu">☰</button>
      </div>
    </header>

    <div v-if="showMobileMenu" class="mobile-nav">
      <button @click="goShelf({ status: 'updating' })">Đang cập nhật</button>
      <button @click="goShelf({ status: 'completed' })">Đã hoàn thành</button>
      <button v-for="item in typeItems" :key="item.value" @click="goShelf({ type: item.value })">{{ item.label }}</button>
      <button @click="showDisplaySettings = true">Cài đặt hiển thị</button>
      <button @click="router.push({ name: 'book-home' })">Nguồn sách</button>
      <button @click="router.push({ name: 'upload' })">Tải sách lên</button>
      <button @click="router.push({ name: 'rss-home' })">Nguồn RSS</button>
    </div>

    <main class="app-main"><slot /></main>

    <el-dialog v-model="showDisplaySettings" title="Cài đặt hiển thị" width="min(94vw, 640px)">
      <div class="display-settings">
        <section>
          <h3>Hình nền</h3>
          <div class="background-grid">
            <button
              v-for="preset in backgroundPresets"
              :key="preset.id"
              class="background-choice"
              :class="{ active: clientBackground.preference.kind === 'preset' && clientBackground.preference.presetId === preset.id }"
              @click="selectPreset(preset.id)"
            >
              <img :src="preset.image" :alt="preset.name" />
              <span>{{ preset.name }}</span>
            </button>
          </div>
          <div class="settings-row">
            <el-button @click="backgroundFileInput?.click()">Tải ảnh riêng</el-button>
            <el-button @click="resetBackground">Đặt lại</el-button>
            <input ref="backgroundFileInput" type="file" accept="image/png,image/jpeg,image/webp,image/avif" hidden @change="onBackgroundFile" />
          </div>
          <div class="settings-row slider-row">
            <label>Độ mờ</label><el-slider v-model="blur" :min="0" :max="20" :step="1" @change="saveVisualPreferences" />
          </div>
          <div class="settings-row slider-row">
            <label>Độ tối</label><el-slider v-model="dimPercent" :min="0" :max="75" :step="1" @change="saveVisualPreferences" />
          </div>
        </section>
        <section>
          <h3>Đọc và dịch trên trình duyệt này</h3>
          <div class="settings-row">
            <label>Cỡ chữ</label>
            <el-select v-model="fontSize" @change="saveReaderVisualPreferences"><el-option v-for="size in [14,16,18,20,22,24,28]" :key="size" :label="`${size}px`" :value="size" /></el-select>
          </div>
          <div class="settings-row">
            <label>Font chữ</label>
            <el-select v-model="fontFamily" @change="saveReaderVisualPreferences"><el-option label="Sans hiện đại" value="system-ui" /><el-option label="Serif dễ đọc" value="Georgia, serif" /><el-option label="Monospace" value="ui-monospace, monospace" /></el-select>
          </div>
          <div class="settings-row">
            <label>Chế độ dịch</label>
            <el-switch v-model="translationEnabled" active-text="Bật dịch khi đọc" @change="saveReaderVisualPreferences" />
          </div>
        </section>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  backgroundPresets,
  clientBackground,
  getReaderPreferences,
  initializeClientBackground,
  resetBackgroundPreference,
  saveBackgroundPreference,
  saveCustomBackground,
  saveReaderPreferences,
} from '@/utils/clientPreferences'

const router = useRouter()
const route = useRoute()
const showDisplaySettings = ref(false)
const showMobileMenu = ref(false)
const searchText = ref(typeof route.query.q === 'string' ? route.query.q : '')
const backgroundFileInput = ref<HTMLInputElement>()
const readerPreferences = getReaderPreferences()
const blur = ref(clientBackground.preference.blur)
const dimPercent = ref(Math.round(clientBackground.preference.dim * 100))
const fontSize = ref(readerPreferences.fontSize || 18)
const fontFamily = ref(readerPreferences.fontFamily || 'system-ui')
const translationEnabled = ref(readerPreferences.translationEnabled ?? false)
const isReader = computed(() => route.name === 'chapter' || route.name === 'media')
const typeItems = [
  { label: 'Truyện chữ', value: 'text' },
  { label: 'Truyện tranh', value: 'image' },
  { label: 'Sách nói', value: 'audio' },
  { label: 'Video', value: 'video' },
]

const goHome = () => router.push({ name: 'shelf' })
const goShelf = (query: Record<string, string>) => {
  showMobileMenu.value = false
  const nextQuery: Record<string, string> = {}
  if (typeof route.query.q === 'string' && route.query.q.trim()) nextQuery.q = route.query.q
  if (Object.keys(query).length > 0) {
    if (typeof route.query.status === 'string' && query.type) nextQuery.status = route.query.status
    if (typeof route.query.type === 'string' && query.status) nextQuery.type = route.query.type
    Object.assign(nextQuery, query)
  }
  router.push({ name: 'shelf', query: nextQuery })
}
const submitSearch = () => {
  const query = searchText.value.trim()
  router.push({ name: 'shelf', query: query ? { q: query } : {} })
}
const selectPreset = async (id: string) => {
  await saveBackgroundPreference({ kind: 'preset', presetId: id })
  blur.value = clientBackground.preference.blur
  dimPercent.value = Math.round(clientBackground.preference.dim * 100)
}
const saveVisualPreferences = async () => {
  await saveBackgroundPreference({ blur: blur.value, dim: dimPercent.value / 100 })
}
const resetBackground = async () => {
  await resetBackgroundPreference()
  blur.value = clientBackground.preference.blur
  dimPercent.value = Math.round(clientBackground.preference.dim * 100)
}
const onBackgroundFile = async (event: Event) => {
  const file = (event.target as HTMLInputElement).files?.[0]
  if (!file) return
  try {
    await saveCustomBackground(file)
    ElMessage.success('Đã lưu hình nền riêng trên trình duyệt này')
  } catch (error) {
    const code = error instanceof Error ? error.message : ''
    ElMessage.error(code === 'BACKGROUND_TOO_LARGE' ? 'Ảnh phải nhỏ hơn 20 MB' : 'Không thể lưu ảnh này')
  } finally {
    if (backgroundFileInput.value) backgroundFileInput.value.value = ''
  }
}
const saveReaderVisualPreferences = () => {
  saveReaderPreferences({ fontSize: fontSize.value, fontFamily: fontFamily.value, translationEnabled: translationEnabled.value })
  ElMessage.success('Đã lưu cho trình duyệt này')
}

watch(() => route.query.q, value => {
  searchText.value = typeof value === 'string' ? value : ''
})
const openDisplaySettingsEvent = () => { showDisplaySettings.value = true }
onMounted(() => {
  initializeClientBackground()
  document.addEventListener('open-display-settings', openDisplaySettingsEvent)
})
onBeforeUnmount(() => document.removeEventListener('open-display-settings', openDisplaySettingsEvent))
</script>

<style scoped>
.app-shell { min-height: 100vh; color: var(--web-text, #202b35); }
.app-header { position: sticky; top: 0; z-index: 20; display: flex; align-items: center; gap: 24px; min-height: 68px; padding: 0 28px; color: #f8fbfa; background: linear-gradient(110deg, rgba(13,53,57,.98), rgba(22,83,81,.94)); box-shadow: 0 7px 24px rgba(9,31,35,.2); }
.brand { display: flex; align-items: center; gap: 10px; min-width: 170px; cursor: pointer; }
.brand-mark { display: grid; place-items: center; width: 34px; height: 34px; border-radius: 12px; background: #e6c783; color: #173d3c; font: 800 20px Georgia, serif; }
.brand strong,.brand small { display: block; }.brand small { margin-top: 2px; color: rgba(255,255,255,.68); font-size: 11px; }
.desktop-nav { display: flex; align-items: center; gap: 3px; flex: 1; }.nav-button { border: 0; background: transparent; color: rgba(255,255,255,.9); cursor: pointer; padding: 9px 10px; border-radius: 8px; font-size: 13px; }.nav-button:hover { background: rgba(255,255,255,.12); }
.header-actions { display: flex; align-items: center; gap: 10px; }.header-search { width: 185px; }.header-search :deep(.el-input__wrapper) { border-radius: 99px; background: rgba(255,255,255,.14); box-shadow: none; }.header-search :deep(input) { color: white; }.header-search :deep(input::placeholder) { color: rgba(255,255,255,.7); }
.mobile-menu { display: none; border: 0; color: white; background: transparent; font-size: 22px; }.mobile-nav { display: none; }
.app-main { min-height: calc(100vh - 68px); }.reader-shell .app-header { position: fixed; width: 100%; transform: translateY(-100%); transition: transform .22s ease; }.reader-shell .app-header:hover,.reader-shell .app-header:focus-within { transform: translateY(0); }.reader-shell .app-main { min-height: 100vh; }
.display-settings { display: grid; grid-template-columns: 1.15fr .85fr; gap: 24px; }.display-settings h3 { margin: 0 0 12px; color: #244b4a; }.background-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 9px; }.background-choice { padding: 0; overflow: hidden; border: 2px solid transparent; border-radius: 10px; background: #f3f5f4; cursor: pointer; text-align: left; }.background-choice.active { border-color: #d69d49; }.background-choice img { display: block; width: 100%; height: 74px; object-fit: cover; }.background-choice span { display: block; padding: 6px; font-size: 12px; }.settings-row { display: flex; align-items: center; gap: 10px; margin-top: 14px; }.settings-row label { min-width: 76px; font-size: 13px; }.slider-row :deep(.el-slider) { flex: 1; }.settings-row .el-select { flex: 1; }
@media (max-width: 980px) { .desktop-nav { gap: 0; }.nav-button { padding: 8px 5px; font-size: 12px; }.brand { min-width: 145px; }.header-search { width: 145px; } }
@media (max-width: 760px) { .app-header { padding: 0 14px; gap: 12px; }.desktop-nav { display: none; }.brand { flex: 1; min-width: 0; }.header-search { width: 150px; }.mobile-menu { display: block; }.mobile-nav { position: fixed; top: 68px; left: 0; right: 0; z-index: 19; display: grid; padding: 10px 14px; background: rgba(13,53,57,.98); box-shadow: 0 10px 24px rgba(0,0,0,.2); }.mobile-nav button { padding: 12px; border: 0; border-bottom: 1px solid rgba(255,255,255,.12); background: transparent; color: white; text-align: left; }.display-settings { grid-template-columns: 1fr; }.background-grid { grid-template-columns: repeat(2, 1fr); } }
</style>
