import axios from 'axios'
import ajax from './axios'
import { legado_http_entry_point } from './api'
import { getWebSessionToken, setWebSessionToken } from './webSession'
import type { SeachBook } from '@/book'

export type WebServiceInstanceResponse = {
  appName: string
  serviceName?: string
  packageName: string
  versionName: string
  versionCode: number
  instanceId: string
  apiVersion: number
  legacyApiVersion: number
  httpPort: number
  webSocketPort: number
  legacyHttpPort: number
  legacyWebSocketPort: number
  requiresPairing: boolean
  pairingCodeTtlMillis: number
  sessionTtlMillis: number
}

export type WebServiceSourceImportResponse = {
  items: Array<{
    sourceUrl: string
    name: string
    existing: boolean
    lastUpdateTime: number
  }>
  committed: boolean
}

export type WebServiceVbookRegistryImportResponse = {
  sourceLabel: string
  classification: string
  total: number
  compatible: number
  rejected: number
  selected: number
  installed: number
  failed: number
  committed: boolean
}

export type WebServiceDiscoverySource = {
  sourceUrl: string
  name: string
  group?: string | null
  enabled: boolean
  selectedForWeb: boolean
}

export type WebServiceDiscoveryKind = {
  title: string
  displayName: string
  url?: string | null
  type: 'url' | 'text' | 'button' | 'toggle' | 'select' | string
  action?: string | null
  chars: string[]
  defaultValue?: string | null
  currentValue: string
}

export type WebServiceDiscoveryKindsResponse = {
  sourceUrl: string
  sourceName: string
  group?: string | null
  kinds: WebServiceDiscoveryKind[]
}

export type WebServicePolicy = {
  exportEnabled: boolean
  autoTranslationEnabled: boolean
  backgroundAssetId: string | null
  backgroundFit: 'cover' | 'contain'
  backgroundPosition:
    | 'center'
    | 'top'
    | 'bottom'
    | 'left'
    | 'right'
    | 'left top'
    | 'left bottom'
    | 'right top'
    | 'right bottom'
  backgroundDim: number
  backgroundBlur: number
  webDiscoverySourceUrls: string[]
  revision: number
  updatedAt: number
  etag: string
}

export type WebServicePolicyPatch = {
  exportEnabled?: boolean
  autoTranslationEnabled?: boolean
  backgroundFit?: WebServicePolicy['backgroundFit']
  backgroundPosition?: WebServicePolicy['backgroundPosition']
  backgroundDim?: number
  backgroundBlur?: number
  webDiscoverySourceUrls?: string[]
}

export type WebServicePolicyEnvelope = {
  policy: WebServicePolicy
  etag: string
}

export type WebServiceBackgroundAsset = {
  assetId: string
  contentType: string
  sizeBytes: number
  width: number
  height: number
  etag: string
}

export type WebServiceBackgroundUploadResponse = {
  asset: WebServiceBackgroundAsset
  policy: WebServicePolicy
}

export type WebServiceExportSourcesRequest = {
  sourceType: 'book' | 'rss'
  sourceKeys?: string[]
  payloadJson?: string
}

export type WebServiceExportBookshelfRequest = {
  bookUrls?: string[]
}

export type WebServiceExportChapterRequest = {
  bookUrl: string
  chapterIndex: number
}

export type WebServiceExportBookTextRequest = {
  bookUrl: string
  chapterIndices?: number[]
}

export type WebServiceExportEbookRequest = {
  bookUrl: string
  format: 'epub2' | 'epub3' | 'pdf' | 'txt' | 'html' | 'cbz'
  scope?: string
  contentSource?: 'original' | 'translation' | 'both'
  imageOptimization?: 'original' | 'balanced' | 'small'
}

export type WebServiceTranslationJobStatus =
  | 'idle'
  | 'translating'
  | 'translated'
  | 'failed'
  | 'cancelled'

export type WebServiceTranslationJobRequest = {
  bookUrl: string
  chapterIndex: number
  forceRetranslate?: boolean
  provider?: string
  targetLanguage?: string
}

export type WebServiceTranslationProvider = {
  id: string
  name: string
  targetLanguages: string[]
}

export type WebServiceTranslationProviderListResponse = {
  providers: WebServiceTranslationProvider[]
  defaultProvider: string
  defaultTargetLanguage: string
}

export type WebServiceTranslationContentResponse = {
  bookUrl: string
  chapterIndex: number
  content: string | null
  provider: string | null
  targetLanguage: string
  updatedAt: number
}

export type WebServiceTranslationJobResponse = {
  jobId: string
  bookUrl: string
  chapterIndex: number
  provider: string
  targetLanguage: string
  status: WebServiceTranslationJobStatus
  currentChunk: number
  totalChunks: number
  progress: number
  content: string | null
  preview: string | null
  error: string | null
  updatedAt: number
}

export type WebServiceTranslationJobListResponse = {
  jobs: WebServiceTranslationJobResponse[]
}

export type WebServiceDownload = {
  blob: Blob
  fileName: string
}

export type WebServiceDiscoveryResponse = {
  items: SeachBook[]
  sourceErrors: number
  refreshedAt: number
}

export type WebServiceBookImportResponse = {
  fileName: string
  imported: boolean
}

export type WebServiceMediaVariant = {
  id: string
  title: string
  contentKind: 'VIDEO' | 'AUDIO' | 'UNKNOWN' | string
  protocol: 'DIRECT' | 'HLS' | 'DASH' | 'IFRAME' | 'UNKNOWN' | string
  mimeType: string
  playbackUrl: string
  externalPlayerRequired: boolean
  drmUnsupported: boolean
  durationMs?: number | null
}

export type WebServiceMediaSession = {
  sessionId: string
  expiresAt: number
  bookUrl: string
  bookTitle: string
  coverUrl?: string | null
  chapterIndex: number
  chapterCount: number
  previousChapterIndex?: number | null
  nextChapterIndex?: number | null
  isVideo: boolean
  chapters: { index: number; title: string; isOffline: boolean }[]
  variants: WebServiceMediaVariant[]
  subtitles: { id: string; label: string; language: string; mimeType: string; playbackUrl: string; isDefault: boolean }[]
  audioTracks: { id: string; label: string; language: string; mimeType: string; playbackUrl: string; isDefault: boolean }[]
}

export type WebServiceTtsCapabilities = {
  enabled: boolean
  engine: string
  language: string
}

export type WebServiceTtsSynthesisResponse = {
  audioUrl: string
  engine: string
  language: string
  expiresAt: number
}

const v2 = axios.create({
  timeout: 30_000,
})

v2.interceptors.request.use(config => {
  const token = getWebSessionToken()
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

const baseURL = () =>
  legado_http_entry_point ||
  (typeof ajax.defaults.baseURL === 'string' ? ajax.defaults.baseURL : '') ||
  location.origin

export const resolveWebServiceUrl = (path: string) => {
  const root = baseURL().replace(/\/$/, '')
  return /^https?:\/\//i.test(path) ? path : `${root}/${path.replace(/^\//, '')}`
}

const policyEnvelope = (
  policy: WebServicePolicy,
  etag?: string,
): WebServicePolicyEnvelope => ({
  policy,
  etag: etag || policy.etag,
})

export const getWebServiceInstance = async () => {
  const response = await v2.get<WebServiceInstanceResponse>('api/v2/instance', {
    baseURL: baseURL(),
  })
  return response.data
}

export const importWebServiceSources = async (
  payload: string,
  sourceType: 'book' | 'rss',
  commit = true,
) => {
  const response = await v2.post<WebServiceSourceImportResponse>(
    'api/v2/sources/import',
    { payload, sourceType, commit },
    { baseURL: baseURL(), timeout: 120_000 },
  )
  return response.data
}

export const importWebServiceVbookRegistry = async (
  payload: string,
  commit = true,
) => {
  const response = await v2.post<WebServiceVbookRegistryImportResponse>(
    'api/v2/vbook/registry/import',
    { payload, commit },
    { baseURL: baseURL(), timeout: 180_000 },
  )
  return response.data
}

export const getWebServiceDiscoverySources = async () => {
  const response = await v2.get<WebServiceDiscoverySource[]>('api/v2/discovery/sources', { baseURL: baseURL() })
  return response.data
}

export const patchWebServiceDiscoverySources = async (sourceUrls: string[]) => {
  const response = await v2.patch('api/v2/discovery/sources', { sourceUrls }, { baseURL: baseURL() })
  return response.data
}

export const getWebServiceDiscoveryKinds = async (sourceUrl: string) => {
  const response = await v2.get<WebServiceDiscoveryKindsResponse>('api/v2/discovery/kinds', {
    baseURL: baseURL(),
    params: { sourceUrl },
  })
  return response.data
}

export const patchWebServiceDiscoveryKinds = async (
  sourceUrl: string,
  values: Record<string, string>,
) => {
  const response = await v2.patch<WebServiceDiscoveryKindsResponse>(
    'api/v2/discovery/kinds',
    { sourceUrl, values },
    { baseURL: baseURL() },
  )
  return response.data
}

export const translateWebServiceUi = async (
  scopeKey: string,
  texts: string[],
  targetLanguage: string,
) => {
  const response = await v2.post<{ targetLanguage: string; texts: string[] }>(
    'api/v2/translation/ui',
    { scopeKey, texts, targetLanguage },
    { baseURL: baseURL(), timeout: 120_000 },
  )
  return response.data
}

export const getDiscoveryHome = async (options: {
  type?: string
  limit?: number
  refresh?: boolean
  sourceUrl?: string
  exploreUrl?: string
  args?: string
  page?: number
} = {}) => {
  const response = await v2.get<WebServiceDiscoveryResponse>('api/v2/discovery/home', {
    baseURL: baseURL(),
    params: options,
  })
  return response.data
}

export const uploadLocalBook = async (file: File, onUploadProgress?: (percent: number) => void) => {
  const form = new FormData()
  form.append('fileName', file.name)
  form.append('file', file, file.name)
  const response = await v2.post<WebServiceBookImportResponse>('api/v2/books/import', form, {
    baseURL: baseURL(),
    timeout: 10 * 60_000,
    onUploadProgress: event => {
      if (event.total) onUploadProgress?.(Math.round((event.loaded / event.total) * 100))
    },
  })
  return response.data
}

export const createMediaSession = async (bookUrl: string, chapterIndex?: number) => {
  const response = await v2.post<WebServiceMediaSession>('api/v2/media/sessions', { bookUrl, chapterIndex }, { baseURL: baseURL(), timeout: 60_000 })
  return response.data
}

export const getMediaSession = async (sessionId: string) => {
  const response = await v2.get<WebServiceMediaSession>(`api/v2/media/sessions/${encodeURIComponent(sessionId)}`, { baseURL: baseURL() })
  return response.data
}

export const getWebServiceTtsCapabilities = async (bookUrl?: string) => {
  const response = await v2.get<WebServiceTtsCapabilities>('api/v2/tts/capabilities', {
    baseURL: baseURL(),
    params: bookUrl ? { bookUrl } : undefined,
  })
  return response.data
}

export const synthesizeWebServiceTts = async (text: string, language?: string, bookUrl?: string) => {
  const response = await v2.post<WebServiceTtsSynthesisResponse>(
    'api/v2/tts/synthesize',
    { text, language, bookUrl },
    { baseURL: baseURL(), timeout: 120_000 },
  )
  return response.data
}

export const exchangeWebServicePairingCode = async (code: string) => {
  const response = await v2.post<{ sessionToken: string; expiresAt: number }>(
    'api/v2/session',
    { code },
    { baseURL: baseURL() },
  )
  setWebSessionToken(response.data.sessionToken)
  return response.data
}

export const getWebServiceSession = async () => {
  const response = await v2.get<{ active: boolean; expiresAt: number }>(
    'api/v2/session',
    { baseURL: baseURL() },
  )
  return response.data
}

export const getWebServicePolicy = async () => {
  const response = await v2.get<WebServicePolicy>('api/v2/policy', {
    baseURL: baseURL(),
  })
  return policyEnvelope(response.data, response.headers.etag)
}

export const patchWebServicePolicy = async (
  patch: WebServicePolicyPatch,
  etag: string,
) => {
  const response = await v2.patch<WebServicePolicy>(
    'api/v2/policy',
    patch,
    {
      baseURL: baseURL(),
      headers: {
        'If-Match': etag,
      },
    },
  )
  return policyEnvelope(response.data, response.headers.etag)
}

export const resetWebServicePolicy = async () => {
  const response = await v2.post<WebServicePolicy>(
    'api/v2/policy/reset',
    undefined,
    {
      baseURL: baseURL(),
    },
  )
  return policyEnvelope(response.data, response.headers.etag)
}

export const getWebServiceBackgroundBlob = async (assetId: string) => {
  const response = await v2.get<Blob>(
    `api/v2/background/${encodeURIComponent(assetId)}`,
    {
      baseURL: baseURL(),
      responseType: 'blob',
    },
  )
  return response.data
}

export const uploadWebServiceBackground = async (
  file: File,
  etag: string,
) => {
  const form = new FormData()
  form.append('fileName', file.name)
  form.append('file', file, file.name)
  const response = await v2.post<WebServiceBackgroundUploadResponse>(
    'api/v2/background',
    form,
    {
      baseURL: baseURL(),
      headers: {
        'If-Match': etag,
      },
    },
  )
  return {
    asset: response.data.asset,
    policy: response.data.policy,
    etag: response.headers.etag || response.data.policy.etag,
  }
}

export const deleteWebServiceBackground = async (etag: string) => {
  const response = await v2.delete<WebServicePolicy>('api/v2/background', {
    baseURL: baseURL(),
    headers: {
      'If-Match': etag,
    },
  })
  return policyEnvelope(response.data, response.headers.etag)
}

export const downloadWebServiceExportSources = async (
  request: WebServiceExportSourcesRequest,
): Promise<WebServiceDownload> => {
  return downloadV2(
    'api/v2/export/sources',
    request,
    `${request.sourceType === 'rss' ? 'rss_sources' : 'book_sources'}.json`,
  )
}

export const downloadWebServiceExportBookshelf = async (
  request: WebServiceExportBookshelfRequest = {},
): Promise<WebServiceDownload> =>
  downloadV2('api/v2/export/bookshelf', request, 'bookshelf.json')

export const downloadWebServiceExportChapter = async (
  request: WebServiceExportChapterRequest,
): Promise<WebServiceDownload> =>
  downloadV2('api/v2/export/chapter', request, 'chapter.txt')

export const downloadWebServiceExportBookText = async (
  request: WebServiceExportBookTextRequest,
): Promise<WebServiceDownload> =>
  downloadV2('api/v2/export/book-txt', request, 'book.txt')

export const downloadWebServiceExportEbook = async (
  request: WebServiceExportEbookRequest,
): Promise<WebServiceDownload> =>
  downloadV2(
    'api/v2/export/ebook',
    request,
    `book.${request.format === 'epub2' || request.format === 'epub3' ? 'epub' : request.format}`,
  )

export const createWebServiceTranslationJob = async (
  request: WebServiceTranslationJobRequest,
) => {
  const response = await v2.post<WebServiceTranslationJobResponse>(
    'api/v2/translation/jobs',
    request,
    {
      baseURL: baseURL(),
    },
  )
  return response.data
}

export const pretranslateWebServiceChapters = async (request: {
  bookUrl: string
  fromChapter: number
  count: number
  provider?: string
  targetLanguage?: string
  forceRetranslate?: boolean
}) => {
  const response = await v2.post('api/v2/translation/pretranslate', request, { baseURL: baseURL() })
  return response.data
}

export const getWebServiceTranslationContent = async (
  bookUrl: string,
  chapterIndex: number,
  provider?: string,
  targetLanguage?: string,
) => {
  const response = await v2.get<WebServiceTranslationContentResponse>(
    'api/v2/translation/content',
    {
      baseURL: baseURL(),
      params: { bookUrl, chapterIndex, provider, targetLanguage },
    },
  )
  return response.data
}

export const getWebServiceTranslationProviders = async () => {
  const response = await v2.get<WebServiceTranslationProviderListResponse>(
    'api/v2/translation/providers',
    {
      baseURL: baseURL(),
    },
  )
  return response.data
}

export const listWebServiceTranslationJobs = async () => {
  const response = await v2.get<WebServiceTranslationJobListResponse>(
    'api/v2/translation/jobs',
    {
      baseURL: baseURL(),
    },
  )
  return response.data
}

export const getWebServiceTranslationJob = async (jobId: string) => {
  const response = await v2.get<WebServiceTranslationJobResponse>(
    `api/v2/translation/jobs/${encodeURIComponent(jobId)}`,
    {
      baseURL: baseURL(),
    },
  )
  return response.data
}

export const cancelWebServiceTranslationJob = async (jobId: string) => {
  const response = await v2.delete<WebServiceTranslationJobResponse>(
    `api/v2/translation/jobs/${encodeURIComponent(jobId)}`,
    {
      baseURL: baseURL(),
    },
  )
  return response.data
}

const downloadV2 = async (
  path: string,
  request: unknown,
  fallbackName: string,
): Promise<WebServiceDownload> => {
  const response = await v2.post<Blob>(path, request, {
    baseURL: baseURL(),
    responseType: 'blob',
  })
  return {
    blob: response.data,
    fileName: fileNameFromContentDisposition(
      response.headers['content-disposition'],
      fallbackName,
    ),
  }
}

const fileNameFromContentDisposition = (
  contentDisposition: string | undefined,
  fallback: string,
) => {
  const match = contentDisposition?.match(/filename="?([^"]+)"?/i)
  return match?.[1] || fallback
}
