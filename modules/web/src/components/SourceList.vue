<template>
  <el-input
    v-model="searchKey"
    class="search"
    :prefix-icon="Search"
    placeholder="Lọc nguồn"
  />
  <div class="tool">
    <el-button @click="importSourceFile" :icon="Folder">Mở</el-button>
    <el-button
      :disabled="sourcesFiltered.length === 0 || !exportEnabled"
      @click="outExport"
      :icon="Download"
    >
      Xuất</el-button
    >
    <el-button
      type="danger"
      :icon="Delete"
      @click="deleteSelectSources"
      :disabled="sourceSelect.length === 0"
      >Xóa</el-button
    >
    <el-button
      type="danger"
      :icon="Delete"
      @click="clearAllSources"
      :disabled="sources.length === 0"
      >Xóa trắng</el-button
    >
  </div>
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
import { downloadWebServiceExportSources } from '@/api/webService'
import { Folder, Delete, Download, Search } from '@element-plus/icons-vue'
import {
  isSourceMatches,
  getSourceUniqueKey,
  getSourceName,
  convertSourcesToMap,
} from '@utils/souce'
import VirtualList from 'vue3-virtual-scroll-list'
import SourceItem from './SourceItem.vue'
import type { Source } from '@/source'

const store = useSourceStore()
const sourceUrlSelect = ref<string[]>([])
const searchKey = ref('')
const sources = computed(() => store.sources)
const webServiceStore = useWebServiceStore()
const exportEnabled = computed(
  () => webServiceStore.policy?.exportEnabled ?? false,
)

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
      return ElMessage.info('Chưa chọn tệp')
    }
    const reader = new FileReader()
    reader.readAsText(files[0])
    reader.onload = () => {
      try {
        const jsonData = JSON.parse(reader.result as string)
        store.saveSources(jsonData)
      } catch (e: unknown) {
        ElMessage.error('Định dạng nguồn tải lên không đúng: ' + (e as Error).message)
      }
    }
  })
  input.click()
}

const isBookSource = /bookSource/i.test(window.location.href)
const outExport = async () => {
  if (!exportEnabled.value) {
    ElMessage.warning('Export đang tắt trong WebService')
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
    ElMessage.error('Không thể xuất dữ liệu; hãy kiểm tra quyền Export')
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
})
</script>

<style lang="scss" scoped>
.tool {
  display: flex;
  margin: 4px 0;
  justify-content: center;
}

#source-list {
  margin-top: 6px;
  height: calc(100vh - 112px - 7px);
  :deep(.el-checkbox) {
    margin-bottom: 4px;
    width: 100%;
  }
}
</style>
