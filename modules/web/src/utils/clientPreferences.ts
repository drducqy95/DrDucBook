import { reactive } from 'vue'
import mistyJourney from '@/assets/backgrounds/misty-journey.png'
import koiWater from '@/assets/backgrounds/koi-water.png'
import mountainDawn from '@/assets/backgrounds/mountain-dawn.png'
import floatingIsles from '@/assets/backgrounds/floating-isles.png'
import silverDuo from '@/assets/backgrounds/silver-duo.png'

export type BackgroundFit = 'cover' | 'contain'
export type BackgroundPosition =
  | 'center'
  | 'top'
  | 'bottom'
  | 'left'
  | 'right'
  | 'left top'
  | 'left bottom'
  | 'right top'
  | 'right bottom'

export type BackgroundPreset = {
  id: string
  name: string
  image: string
  position?: BackgroundPosition
}

export const backgroundPresets: BackgroundPreset[] = [
  { id: 'misty-journey', name: 'Mưa hoa', image: mistyJourney, position: 'center' },
  { id: 'koi-water', name: 'Cá chép bên hồ', image: koiWater, position: 'center' },
  { id: 'mountain-dawn', name: 'Sơn hà bình minh', image: mountainDawn, position: 'center' },
  { id: 'floating-isles', name: 'Tiên cảnh', image: floatingIsles, position: 'center' },
  { id: 'silver-duo', name: 'Song hành', image: silverDuo, position: 'center' },
]

export type ClientBackgroundPreference = {
  kind: 'preset' | 'custom'
  presetId: string
  fit: BackgroundFit
  position: BackgroundPosition
  blur: number
  dim: number
}

export type ReaderPreferences = {
  fontSize?: number
  fontFamily?: string
  translationEnabled?: boolean
  translationProvider?: string
  targetLanguage?: string
}

const backgroundCookie = 'drducbook_web_background_v2'
const readerStorageKey = 'drducbook_reader_preferences_v2'
const chineseSearchStorageKey = 'drducbook_search_chinese_v1'
const backgroundDbName = 'drducbook-web-client'
const backgroundStoreName = 'backgrounds'
const customBackgroundKey = 'custom'

export const defaultBackgroundPreference: ClientBackgroundPreference = {
  kind: 'preset',
  presetId: 'mountain-dawn',
  fit: 'cover',
  position: 'center',
  blur: 8,
  dim: 0.32,
}

export const clientBackground = reactive({
  preference: { ...defaultBackgroundPreference } as ClientBackgroundPreference,
  imageUrl: backgroundPresets.find(item => item.id === defaultBackgroundPreference.presetId)?.image || '',
  initialized: false,
})

let activeObjectUrl = ''

const setCookie = (value: string) => {
  document.cookie = `${backgroundCookie}=${encodeURIComponent(value)}; Max-Age=31536000; Path=/; SameSite=Strict`
}

const readCookie = () => {
  const value = document.cookie
    .split('; ')
    .find(item => item.startsWith(`${backgroundCookie}=`))
    ?.slice(backgroundCookie.length + 1)
  if (!value) return undefined
  try {
    return JSON.parse(decodeURIComponent(value)) as Partial<ClientBackgroundPreference>
  } catch {
    return undefined
  }
}

const normalizePreference = (
  value?: Partial<ClientBackgroundPreference>,
): ClientBackgroundPreference => {
  const presetId = backgroundPresets.some(item => item.id === value?.presetId)
    ? value?.presetId || defaultBackgroundPreference.presetId
    : defaultBackgroundPreference.presetId
  return {
    ...defaultBackgroundPreference,
    ...value,
    presetId,
    kind: value?.kind === 'custom' ? 'custom' : 'preset',
    blur: Math.max(0, Math.min(20, Number(value?.blur ?? defaultBackgroundPreference.blur))),
    dim: Math.max(0, Math.min(0.75, Number(value?.dim ?? defaultBackgroundPreference.dim))),
  }
}

const openBackgroundDb = (): Promise<IDBDatabase> =>
  new Promise((resolve, reject) => {
    const request = indexedDB.open(backgroundDbName, 1)
    request.onupgradeneeded = () => {
      request.result.createObjectStore(backgroundStoreName)
    }
    request.onsuccess = () => resolve(request.result)
    request.onerror = () => reject(request.error)
  })

const readCustomBackground = async (): Promise<Blob | undefined> => {
  if (!('indexedDB' in window)) return undefined
  const db = await openBackgroundDb()
  return new Promise((resolve, reject) => {
    const request = db.transaction(backgroundStoreName, 'readonly')
      .objectStore(backgroundStoreName)
      .get(customBackgroundKey)
    request.onsuccess = () => resolve(request.result as Blob | undefined)
    request.onerror = () => reject(request.error)
  })
}

const writeCustomBackground = async (blob: Blob) => {
  if (!('indexedDB' in window)) throw new Error('BROWSER_STORAGE_UNAVAILABLE')
  const db = await openBackgroundDb()
  await new Promise<void>((resolve, reject) => {
    const request = db.transaction(backgroundStoreName, 'readwrite')
      .objectStore(backgroundStoreName)
      .put(blob, customBackgroundKey)
    request.onsuccess = () => resolve()
    request.onerror = () => reject(request.error)
  })
}

const deleteCustomBackground = async () => {
  if (!('indexedDB' in window)) return
  const db = await openBackgroundDb()
  await new Promise<void>((resolve, reject) => {
    const request = db.transaction(backgroundStoreName, 'readwrite')
      .objectStore(backgroundStoreName)
      .delete(customBackgroundKey)
    request.onsuccess = () => resolve()
    request.onerror = () => reject(request.error)
  })
}

const revokeObjectUrl = () => {
  if (activeObjectUrl) URL.revokeObjectURL(activeObjectUrl)
  activeObjectUrl = ''
}

const applyPreference = async (preference: ClientBackgroundPreference) => {
  revokeObjectUrl()
  let imageUrl = backgroundPresets.find(item => item.id === preference.presetId)?.image || ''
  if (preference.kind === 'custom') {
    const blob = await readCustomBackground().catch(() => undefined)
    if (blob) {
      activeObjectUrl = URL.createObjectURL(blob)
      imageUrl = activeObjectUrl
    } else {
      preference = { ...preference, kind: 'preset' }
    }
  }
  clientBackground.preference = preference
  clientBackground.imageUrl = imageUrl
}

export const initializeClientBackground = async () => {
  if (clientBackground.initialized) return
  await applyPreference(normalizePreference(readCookie()))
  clientBackground.initialized = true
}

export const saveBackgroundPreference = async (
  value: Partial<ClientBackgroundPreference>,
) => {
  const preference = normalizePreference({ ...clientBackground.preference, ...value })
  setCookie(JSON.stringify(preference))
  await applyPreference(preference)
}

export const saveCustomBackground = async (file: File) => {
  if (!/^image\/(png|jpe?g|webp|avif)$/i.test(file.type)) {
    throw new Error('BACKGROUND_FORMAT_INVALID')
  }
  if (file.size > 20 * 1024 * 1024) throw new Error('BACKGROUND_TOO_LARGE')
  await writeCustomBackground(file)
  await saveBackgroundPreference({ kind: 'custom' })
}

export const resetBackgroundPreference = async () => {
  await deleteCustomBackground()
  setCookie(JSON.stringify(defaultBackgroundPreference))
  await applyPreference({ ...defaultBackgroundPreference })
}

export const getReaderPreferences = (): ReaderPreferences => {
  try {
    return JSON.parse(localStorage.getItem(readerStorageKey) || '{}') as ReaderPreferences
  } catch {
    return {}
  }
}

export const saveReaderPreferences = (value: ReaderPreferences) => {
  localStorage.setItem(readerStorageKey, JSON.stringify({ ...getReaderPreferences(), ...value }))
}

export const getChineseSearchEnabled = () => localStorage.getItem(chineseSearchStorageKey) === 'true'

export const setChineseSearchEnabled = (enabled: boolean) => {
  localStorage.setItem(chineseSearchStorageKey, String(enabled))
}
