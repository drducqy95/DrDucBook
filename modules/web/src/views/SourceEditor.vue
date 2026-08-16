<template>
  <div class="editor">
    <source-tab-form class="left" :config="config" />
    <tool-bar />
    <source-tab-tools class="right" />
  </div>
</template>
<script setup lang="ts">
import bookSourceConfig from '@/config/bookSourceEditConfig'
import rssSourceConfig from '@/config/rssSourceEditConfig'
import '@/assets/sourceeditor.css'
import { useDark } from '@vueuse/core'
import type { SourceConfig } from '@/config/sourceConfig'

useDark()

let config: SourceConfig

if (/bookSource/i.test(location.href)) {
  config = bookSourceConfig as SourceConfig
  document.title = 'Quản lý nguồn sách'
} else {
  config = rssSourceConfig as SourceConfig
  document.title = 'Quản lý nguồn RSS'
}
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
