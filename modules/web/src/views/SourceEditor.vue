<template>
  <div class="editor">
    <source-tab-form class="left" :config="config" :key="sourceKind" />
    <tool-bar />
    <source-tab-tools class="right" />
  </div>
</template>
<script setup lang="ts">
import bookSourceConfig from '@/config/bookSourceEditConfig'
import rssSourceConfig from '@/config/rssSourceEditConfig'
import '@/assets/sourceeditor.css'
import { computed, watch } from 'vue'
import { useRoute } from 'vue-router'
import type { SourceConfig } from '@/config/sourceConfig'

const route = useRoute()
const sourceKind = computed(() => /bookSource/i.test(`${String(route.name || '')}${route.path}`) ? 'book' : 'rss')
const config = computed<SourceConfig>(() => sourceKind.value === 'book'
  ? bookSourceConfig as SourceConfig
  : rssSourceConfig as SourceConfig)
watch(sourceKind, kind => {
  document.title = kind === 'book' ? 'Quản lý nguồn sách' : 'Quản lý nguồn RSS'
}, { immediate: true })
</script>
<style lang="scss" scoped>
.editor {
  display: flex;
  min-height: calc(100vh - 76px);
  height: auto;
  overflow: auto;
  gap: 14px;
  padding: 18px clamp(10px, 2vw, 24px) 28px;
  box-sizing: border-box;
  .left {
    flex: 1;
    min-width: 0;
  }
  .right {
    flex: 0 1 390px;
    width: min(390px, 38%);
    min-width: 280px;
  }
}

@media (max-width: 900px) {
  .editor {
    display: block;
    .left,
    .right {
      width: 100%;
      min-width: 0;
      margin: 0 0 14px;
    }
  }
}
</style>
