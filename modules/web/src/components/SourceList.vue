<template>
  <el-input
    v-model="searchKey"
    class="search"
    :prefix-icon="Search"
    :placeholder="t('sourceFilter')"
  />
  <div class="tool">
    <el-button @click="importSourceFile" :icon="Folder">{{ t('open') }}</el-button>
    <el-button @click="importSourceUrl" :icon="Link">URL</el-button>
    <el-button v-if="isBookSource" @click="importVbookRegistryFile" :icon="Folder">VBook JSON</el-button>
    <el-button v-if="isBookSource" @click="importVbookRegistryUrl" :icon="Link">VBook URL</el-button>
    <el-button
      :disabled="sourcesFiltered.length === 0 || !exportEnabled"
      @click="outExport"
      :icon="Download"
    >
      {{ t('export') }}</el-button
    >
    <el-button
      type="danger"
      :icon="Delete"
      @click="deleteSelectSources"
      :disabled="sourceSelect.length === 0"
      >{{ t('delete') }}</el-button
    >
    <el-button
      type="danger"
      :icon="Delete"
      @click="clearAllSources"
      :disabled="sources.length === 0"
      >{{ t('clearAll') }}</el-button
    >
  </div>
  <section v-if="isBookSource" class="discovery-source-panel">
    <div class="discovery-source-heading">
      <strong>{{ t('webDiscoveryTitle') }}</strong>
      <span>{{ t('webDiscoveryDescription') }}</span>
    </div>
    <el-checkbox-group v-model="webDiscoverySelection" @change="saveDiscoverySelection">
      <el-checkbox v-for="source in discoverySources" :key="source.sourceUrl" :label="source.sourceUrl">
        {{ dynamicText('sources', source.name) }}
      </el-checkbox>
    </el-checkbox-group>
  </section>
  <el-checkbox-group id="source-list" v-model="sourceUrlSelect">
    <virtual-list
      style="height: 100%; overflow-y: auto; overflow-x: hidden"
      :data-key="(source: Source) => getSourceName(source)"
      :data-sources="sourcesFiltered"
      :data-component="SourceItem"
      :estimate-size="45"
    />
  </el-checkbox-group>
</template>

<script setup lang="ts">
import API from '@api'
import {
  downloadWebServiceExportSources,
  getWebServiceDiscoverySources,
  importWebServiceSources,
  importWebServiceVbookRegistry,
  patchWebServiceDiscoverySources,
} from '@/api/webService'
import { Folder, Link, Delete, Download, Search } from '@element-plus/icons-vue'
import {
  isSourceMatches,
  getSourceUniqueKey,
  getSourceName,
  convertSourcesToMap,
} from '@utils/souce'
import VirtualList from 'vue3-virtual-scroll-list'
import SourceItem from './SourceItem.vue'
import type { Source } from '@/source'
import { dynamicText, t, translateDynamicTexts, webLocale } from '@/i18n'

const store = useSourceStore()
const sourceUrlSelect = ref<string[]>([])
const searchKey = ref('')
const sources = computed(() => store.sources)
const webServiceStore = useWebServiceStore()
const exportEnabled = computed(
  () => webServiceStore.policy?.exportEnabled ?? false,
)
const discoverySources = ref<Array<{ sourceUrl: string; name: string; selectedForWeb: boolean }>>([])
const webDiscoverySelection = ref<string[]>([])
const sourceNames = computed(() => sources.value.map(source => getSourceName(source)))

/* Lọc nguồn */
const sourcesFiltered = computed<Source[]>(() => {
  const key = searchKey.value
  if (key === '') return sources.value
  return sources.value.filter(source => isSourceMatches(source, key))
})
// 计算当前筛选关键词下的选中源
const sourceSelect = computed<Source[]>(() => {
  const urls = sourceUrlSelect.value
  if (urls.length == 0) return []
  const sourcesFilteredMap =
    searchKey.value == ''
      ? store.sourcesMap
      : convertSourcesToMap(sourcesFiltered.value)
  return urls.reduce((sources, sourceUrl) => {
    const source = sourcesFilteredMap.get(sourceUrl)
    if (source) sources.push(source)
    return sources
  }, [] as Source[])
})

const deleteSelectSources = () => {
  const sourceSelectValue = sourceSelect.value
  API.deleteSource(sourceSelectValue).then(({ data }) => {
    if (!data.isSuccess) return ElMessage.error(data.errorMsg)
    store.deleteSources(sourceSelectValue)
    const sourceUrlSelectRawValue = toRaw(sourceUrlSelect.value)
    sourceSelectValue.forEach(source => {
      const index = sourceUrlSelectRawValue.indexOf(getSourceUniqueKey(source))
      if (index > -1) sourceUrlSelectRawValue.splice(index, 1)
    })
    sourceUrlSelect.value = sourceUrlSelectRawValue
  })
}
const clearAllSources = () => {
  store.clearAllSource()
  sourceUrlSelect.value = []
}

//导入本地Tệp
const importSourceFile = () => {
  const input = document.createElement('input')
  input.type = 'file'
  input.accept = '.json,.txt'
  input.addEventListener('change', () => {
    const files = input.files
    if (files === null) {
      return ElMessage.info(t('noFile'))
    }
    const reader = new FileReader()
    reader.readAsText(files[0])
    reader.onload = () => {
      importSourcePayload(reader.result as string)
    }
  })
  input.click()
}

const importSourceUrl = async () => {
  try {
    const { value } = await ElMessageBox.prompt(
      t('addSourceUrlPrompt'),
      t('addSourceUrlTitle'),
      { inputPlaceholder: 'https://example.com/sources.json', inputType: 'url' },
    )
    if (value?.trim()) await importSourcePayload(value.trim())
  } catch {
    // User cancelled the prompt.
  }
}

const importVbookRegistryFile = () => {
  const input = document.createElement('input')
  input.type = 'file'
  input.accept = '.json,.txt,application/json,text/json,text/plain'
  input.addEventListener('change', () => {
    const file = input.files?.[0]
    if (!file) return ElMessage.info(t('noFile'))
    const reader = new FileReader()
    reader.readAsText(file)
    reader.onload = () => { void importVbookRegistryPayload(String(reader.result || '')) }
  })
  input.click()
}

const importVbookRegistryUrl = async () => {
  try {
    const { value } = await ElMessageBox.prompt(
      t('vbookRegistryUrlPrompt'),
      t('vbookRegistryTitle'),
      { inputPlaceholder: 'https://example.com/vbook-registry.json', inputType: 'url' },
    )
    if (value?.trim()) await importVbookRegistryPayload(value.trim())
  } catch {
    // User cancelled the prompt.
  }
}

const importVbookRegistryPayload = async (payload: string) => {
  try {
    const result = await importWebServiceVbookRegistry(payload, true)
    const response = await API.getSources()
    if (response.data?.isSuccess) store.saveSources(response.data.data || [])
    await loadDiscoverySources()
    ElMessage.success(`${t('vbookRegistryImported')}: ${result.installed}/${result.selected}`)
  } catch (error) {
    ElMessage.error(
      `${t('vbookRegistryImportFailed')}: ` + (error instanceof Error ? error.message : String(error)),
    )
  }
}

const importSourcePayload = async (payload: string) => {
  try {
    const sourceType = isBookSource ? 'book' : 'rss'
    const result = await importWebServiceSources(payload, sourceType, true)
    const response = await API.getSources()
    if (response.data?.isSuccess) store.saveSources(response.data.data || [])
    await loadDiscoverySources()
    ElMessage.success(`${t('addSourceImported')}: ${result.items.length}`)
  } catch (error) {
    ElMessage.error(
      `${t('sourceImportFailed')}: ` + (error instanceof Error ? error.message : String(error)),
    )
  }
}

const isBookSource = /bookSource/i.test(window.location.href)
const loadDiscoverySources = async () => {
  if (!isBookSource) return
  try {
    const values = await getWebServiceDiscoverySources()
    discoverySources.value = values
    webDiscoverySelection.value = values.filter(item => item.selectedForWeb).map(item => item.sourceUrl)
  } catch {
    discoverySources.value = []
    webDiscoverySelection.value = []
  }
}

const saveDiscoverySelection = async () => {
  try {
    await patchWebServiceDiscoverySources(webDiscoverySelection.value)
  } catch {
    ElMessage.error(t('discoverySaveFailed'))
    await loadDiscoverySources()
  }
}

const outExport = async () => {
  if (!exportEnabled.value) {
    ElMessage.warning(t('exportDisabled'))
    return
  }
  const sources =
      sourceUrlSelect.value.length === 0
        ? sourcesFiltered.value
        : sourceSelect.value,
    sourceType = isBookSource ? 'BookSource' : 'RssSource'

  const fallbackName = `${sourceType}_${Date()
    .replace(/.*?\s(\d+)\s(\d+)\s(\d+:\d+:\d+).*/, '$2$1$3')
    .replace(/:/g, '')}.json`
  try {
    const download = await downloadWebServiceExportSources({
      sourceType: isBookSource ? 'book' : 'rss',
      sourceKeys: sources.map(getSourceUniqueKey),
      payloadJson: JSON.stringify(sources, null, 4),
    })
    saveBlob(download.blob, download.fileName || fallbackName)
  } catch {
    ElMessage.error(t('sourceExportFailed'))
  }
}

const saveBlob = (blob: Blob, fileName: string) => {
  const exportFile = document.createElement('a')
  exportFile.download = fileName
  exportFile.href = window.URL.createObjectURL(blob)
  exportFile.click()
  window.URL.revokeObjectURL(exportFile.href) //avoid memory leak
}

onMounted(() => {
  if (!webServiceStore.policy) {
    webServiceStore.loadPolicy().catch(() => undefined)
  }
  void loadDiscoverySources()
})
watch([sourceNames, webLocale], () => {
  void translateDynamicTexts('sources', [
    ...sourceNames.value,
    ...discoverySources.value.map(source => source.name),
  ])
}, { immediate: true })
</script>

<style lang="scss" scoped>
.tool {
  display: flex;
  margin: 4px 0;
  justify-content: center;
}

.tool :deep(.el-button) {
  min-height: 44px;
}

.discovery-source-panel {
  margin: 8px 0;
  padding: 10px 12px;
  border: 1px solid var(--web-border, #cbd9d5);
  border-radius: 10px;
  color: var(--web-text, #172a2a);
  background: var(--web-panel, #f9fcfa);
}
.discovery-source-heading { display: grid; gap: 3px; margin-bottom: 6px; }
.discovery-source-heading span { color: var(--web-text-muted, #405654); font-size: 12px; }
.discovery-source-panel :deep(.el-checkbox) { min-height: 36px; margin-right: 12px; }

#source-list {
  margin-top: 6px;
  height: calc(100vh - 112px - 7px);
  :deep(.el-checkbox) {
    margin-bottom: 4px;
    width: 100%;
  }
  :deep(.el-checkbox__label) { color: var(--web-text, #172a2a); }
}

@media (max-width: 700px) {
  #source-list { height: calc(100vh - 260px); min-height: 220px; }
  .tool { flex-wrap: wrap; gap: 6px; }
  .tool :deep(.el-button) { margin-left: 0; }
}
</style>
