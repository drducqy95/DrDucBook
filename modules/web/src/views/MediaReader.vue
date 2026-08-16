<template>
  <div class="media-page">
    <section class="media-header glass-panel"><div><button class="back-button" @click="router.back()">‹ Quay lại</button><span class="eyebrow">TRÌNH PHÁT WEB</span><h1>{{ displayText(session?.bookTitle) || 'Đang tải media...' }}</h1><p>{{ currentChapterTitle }}</p></div><div class="media-actions"><el-button v-if="session?.previousChapterIndex != null" @click="openChapter(session.previousChapterIndex)">‹ Tập trước</el-button><el-button v-if="session?.nextChapterIndex != null" type="primary" @click="openChapter(session.nextChapterIndex)">Tập sau ›</el-button></div></section>
    <section v-if="error" class="state-panel glass-panel"><h2>Không thể mở nội dung</h2><p>{{ error }}</p><el-button @click="loadSession">Thử lại</el-button></section>
    <section v-else-if="loading" class="state-panel glass-panel"><el-icon class="is-loading"><Loading /></el-icon> Đang phân giải nguồn media...</section>
    <template v-else-if="session">
      <section class="player-panel glass-panel"><div class="player-wrap"><video v-if="session.isVideo" ref="videoElement" class="player" controls playsinline @error="onPlaybackError"><track v-for="track in session.subtitles" :key="track.id" kind="subtitles" :src="track.playbackUrl" :srclang="track.language || 'vi'" :label="displayText(track.label)" :default="track.isDefault" /></video><audio v-else ref="audioElement" class="audio-player" controls @error="onPlaybackError" /></div><div class="player-caption"><span>{{ displayText(selectedVariant?.title) }}</span><span v-if="selectedVariant?.drmUnsupported" class="warning">Nguồn DRM không hỗ trợ trên web</span></div></section>
      <section class="variant-panel glass-panel"><div class="section-title"><h2>Chất lượng và nguồn phát</h2><span>{{ session.variants.length }} lựa chọn</span></div><div class="variant-list"><button v-for="variant in session.variants" :key="variant.id" class="variant-button" :class="{ active: selectedVariant?.id === variant.id }" @click="selectVariant(variant)"><strong>{{ displayText(variant.title) || variant.protocol }}</strong><small>{{ variant.protocol }} · {{ variant.mimeType || 'media' }}</small></button></div><a v-if="selectedVariant?.externalPlayerRequired || selectedVariant?.drmUnsupported" class="external-link" :href="playbackUrl(selectedVariant)" target="_blank" rel="noopener">Mở bằng trình phát ngoài</a></section>
      <section class="chapters-panel glass-panel"><div class="section-title"><h2>Mục lục</h2><span>{{ session.chapterCount }} tập/chương</span></div><div class="chapter-list"><button v-for="chapter in session.chapters" :key="chapter.index" :class="{ active: chapter.index === session.chapterIndex }" @click="openChapter(chapter.index)"><span>{{ displayText(chapter.title) }}</span><small v-if="chapter.isOffline">Ngoại tuyến</small></button></div></section>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import Hls from 'hls.js'
import dashjs from 'dashjs'
import { createMediaSession, type WebServiceMediaSession, type WebServiceMediaVariant } from '@/api/webService'

const router = useRouter()
const videoElement = ref<HTMLVideoElement>()
const audioElement = ref<HTMLAudioElement>()
const session = ref<WebServiceMediaSession>()
const selectedVariant = ref<WebServiceMediaVariant>()
const loading = ref(true)
const error = ref('')
let hls: Hls | undefined
let dash: dashjs.MediaPlayerClass | undefined
const bookUrl = sessionStorage.getItem('bookUrl') || ''
const chapterIndex = Number(sessionStorage.getItem('chapterIndex') || 0)

// A few older video rules returned UTF-8 text decoded as Windows-1252/Latin-1
// (for example `ChÆ°Æ¡ng`). Repair only strings that are fully byte-compatible
// and whose mojibake signature becomes smaller after UTF-8 decoding; normal
// Vietnamese/CJK text is left untouched.
const mojibakeScore = (value: string) =>
  (value.match(/Ã|Â(?:[· ])|(?:á|tá)[º»]|(?:Ä|Å|Æ|Ð|Ñ)/g) || []).length

const displayText = (value?: string | null) => {
  if (!value) return ''
  let current = value
  for (let attempt = 0; attempt < 2; attempt += 1) {
    if (mojibakeScore(current) === 0 || [...current].some(char => char.charCodeAt(0) > 0xff)) break
    try {
      const bytes = Uint8Array.from([...current], char => char.charCodeAt(0))
      const decoded = new TextDecoder('utf-8', { fatal: true }).decode(bytes)
      if (decoded === current || mojibakeScore(decoded) >= mojibakeScore(current)) break
      current = decoded
    } catch {
      break
    }
  }
  return current
}

const currentChapterTitle = computed(() => {
  const chapter = session.value?.chapters.find(item => item.index === session.value?.chapterIndex)
  return displayText(chapter?.title) || `Chương ${session.value?.chapterIndex ?? chapterIndex}`
})

const playbackUrl = (variant?: WebServiceMediaVariant) => variant ? new URL(variant.playbackUrl, location.origin).toString() : ''
const disposePlayer = () => {
  hls?.destroy(); hls = undefined
  dash?.reset(); dash = undefined
  if (videoElement.value) { videoElement.value.pause(); videoElement.value.removeAttribute('src'); videoElement.value.load() }
  if (audioElement.value) { audioElement.value.pause(); audioElement.value.removeAttribute('src'); audioElement.value.load() }
}
const attachVariant = async () => {
  await nextTick()
  disposePlayer()
  const variant = selectedVariant.value
  if (!variant || variant.externalPlayerRequired || variant.drmUnsupported) return
  const url = playbackUrl(variant)
  if (session.value?.isVideo && videoElement.value) {
    if (variant.protocol === 'HLS' && Hls.isSupported()) { hls = new Hls({ enableWorker: true }); hls.loadSource(url); hls.attachMedia(videoElement.value) }
    else if (variant.protocol === 'DASH') { dash = dashjs.MediaPlayer().create(); dash.initialize(videoElement.value, url, false) }
    else videoElement.value.src = url
  } else if (audioElement.value) {
    audioElement.value.src = url
  }
}
const selectVariant = (variant: WebServiceMediaVariant) => { selectedVariant.value = variant; void attachVariant() }
const loadSession = async () => {
  if (!bookUrl) { error.value = 'Thiếu thông tin sách để mở trình phát'; loading.value = false; return }
  loading.value = true; error.value = ''
  try {
    session.value = await createMediaSession(bookUrl, chapterIndex)
    selectedVariant.value = session.value.variants[0]
    // Render the player before attaching HLS/DASH. While loading is true the
    // template only renders the loading panel, so videoElement would be null.
    loading.value = false
    await attachVariant()
  }
  catch (e) { error.value = e instanceof Error ? e.message : 'Nguồn media không phản hồi' }
  finally { loading.value = false }
}
const openChapter = async (index: number) => {
  sessionStorage.setItem('chapterIndex', String(index));
  loading.value = true; error.value = ''
  try {
    session.value = await createMediaSession(bookUrl, index)
    selectedVariant.value = session.value.variants[0]
    // Keep the same render-before-attach ordering when changing episodes.
    loading.value = false
    await attachVariant()
  }
  catch (e) { error.value = e instanceof Error ? e.message : 'Không thể mở tập này' }
  finally { loading.value = false }
}
const onPlaybackError = () => ElMessage.warning('Trình duyệt không phát được biến thể này; hãy thử nguồn khác hoặc mở ngoài')
onMounted(loadSession)
onBeforeUnmount(disposePlayer)
</script>

<style scoped>
.media-page { width: min(1060px, calc(100% - 36px)); margin: 0 auto; padding: 30px 0 54px; font-family: "Noto Sans", "Noto Sans Vietnamese", "Noto Sans CJK SC", "Microsoft YaHei", "Segoe UI", system-ui, sans-serif; font-synthesis: none; text-rendering: optimizeLegibility; }.media-page button,.media-page input { font: inherit; }.glass-panel { border: 1px solid rgba(255,255,255,.55); background: rgba(249,252,250,.86); box-shadow: 0 14px 34px rgba(18,51,50,.1); backdrop-filter: blur(12px); }.media-header { display: flex; align-items: end; justify-content: space-between; gap: 18px; padding: 25px 30px; border-radius: 18px; background: linear-gradient(115deg, rgba(20,74,75,.92), rgba(42,108,100,.76)); color: white; }.back-button { display: block; margin-bottom: 14px; padding: 0; border: 0; background: transparent; color: #f1d49b; cursor: pointer; }.eyebrow { color: #f1d49b; font-size: 11px; font-weight: 800; letter-spacing: .13em; }.media-header h1 { margin: 8px 0 4px; font: 600 32px/1.2 "Noto Serif", "Noto Serif CJK SC", Georgia, serif; overflow-wrap: anywhere; }.media-header p { margin: 0; color: rgba(255,255,255,.75); }.media-actions { display: flex; gap: 8px; }.player-panel { margin-top: 20px; padding: 20px; border-radius: 16px; }.player-wrap { display: grid; place-items: center; min-height: 220px; overflow: hidden; border-radius: 12px; background: #0b1b1d; }.player { display: block; width: 100%; max-height: 68vh; }.player::cue { font-family: "Noto Sans", "Noto Sans Vietnamese", "Noto Sans CJK SC", "Microsoft YaHei", sans-serif; font-size: 1.05em; }.audio-player { width: min(720px, 100%); }.player-caption { display: flex; justify-content: space-between; gap: 10px; margin-top: 12px; color: #244b49; overflow-wrap: anywhere; }.warning { color: #bd6d43; font-size: 12px; }.variant-panel,.chapters-panel { margin-top: 18px; padding: 20px; border-radius: 16px; }.section-title { display: flex; justify-content: space-between; align-items: center; gap: 10px; }.section-title h2 { margin: 0; color: #1a4845; font: 600 21px/1.3 "Noto Serif", "Noto Serif CJK SC", Georgia, serif; }.section-title span { color: #71827e; font-size: 12px; }.variant-list { display: flex; flex-wrap: wrap; gap: 9px; margin-top: 14px; }.variant-button { display: grid; gap: 4px; min-width: 130px; padding: 11px 13px; border: 1px solid rgba(38,93,88,.2); border-radius: 10px; background: rgba(255,255,255,.5); color: #244b49; cursor: pointer; text-align: left; }.variant-button.active { border-color: #bd7d39; background: #fff4dd; }.variant-button small { color: #75857f; font-size: 10px; }.external-link { display: inline-block; margin-top: 14px; color: #a96f2f; }.chapter-list { display: grid; grid-template-columns: repeat(2, minmax(0,1fr)); gap: 7px; max-height: 360px; overflow: auto; margin-top: 14px; }.chapter-list button { display: flex; justify-content: space-between; gap: 8px; padding: 10px 12px; border: 0; border-radius: 8px; background: rgba(255,255,255,.45); color: #395653; cursor: pointer; text-align: left; }.chapter-list button.active { background: #e2efe9; color: #1a625a; font-weight: 700; }.chapter-list small { color: #aa7435; font-size: 10px; }.state-panel { margin-top: 20px; padding: 44px 20px; border-radius: 16px; text-align: center; }.state-panel h2 { color: #1a4845; }.state-panel p { color: #71827e; }
@media(max-width:700px){.media-page{width:min(100% - 22px,600px);padding-top:18px}.media-header{display:block;padding:22px 20px}.media-header h1{font-size:27px}.media-actions{margin-top:16px}.chapter-list{grid-template-columns:1fr}.player-wrap{min-height:160px}.player-caption{display:block}.warning{display:block;margin-top:6px}}
</style>
