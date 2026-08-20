<template>
  <el-tabs id="source-edit">
    <el-tab-pane
      v-for="{ name, children } in Object.values(config)"
      :label="name"
      :key="name"
    >
      <el-form label-position="right" label-width="auto" class="source-form">
        <el-form-item
          v-for="{
            type,
            title,
            namespace,
            id,
            array,
            hint,
            required = false,
          } in children"
          :label="title"
          :key="title"
          :required="required"
        >
          <el-input
            v-if="type == 'String' && typeof namespace == 'undefined'"
            type="textarea"
            v-model="currentSource[id]"
            :placeholder="hint"
            autosize
          />
          <el-input
            v-if="type == 'String' && typeof namespace != 'undefined'"
            type="textarea"
            v-model="currentSource[namespace][id]"
            :placeholder="hint"
            autosize
          />

          <el-switch
            v-if="(type as string) === 'Boolean'"
            v-model="currentSource[id]"
          />

          <el-input-number
            v-if="(type as string) === 'Number'"
            v-model="currentSource[id]"
            :min="0"
          />

          <el-select
            v-if="(type as string) === 'Array'"
            v-model="currentSource[id]"
          >
            <el-option
              v-for="(optionName, index) in array"
              :value="index"
              :key="optionName"
              :label="optionName"
            />
          </el-select>
        </el-form-item>
      </el-form>
    </el-tab-pane>
  </el-tabs>
</template>

<script setup lang="ts">
import type { SourceConfig } from '@/config/sourceConfig'

const store = useSourceStore()
defineProps<{ config: SourceConfig }>()

const currentSource = computed(() => store.currentSource)
/* 
修改currentSource的属性 没有直接修改本身
const { currentSource } = storeToRefs(store);
 */
</script>

<style lang="scss" scoped>
:deep(.el-tab-pane) {
  min-height: 0;
  height: auto;
  max-height: calc(100vh - 130px);
  padding-top: 15px;
  padding-right: 5px;
  overflow-y: auto;
}
:deep(.el-form-item__label) {
  color: var(--web-text-muted, #334155);
  font-weight: 600;
}
:deep(.el-input__wrapper),
:deep(.el-textarea__inner),
:deep(.el-select),
:deep(.el-input-number) {
  width: 100%;
  font-size: 16px;
}
@media (max-width: 700px) {
  :deep(.el-tab-pane) { max-height: none; overflow: visible; padding: 12px 0 18px; }
  :deep(.el-tabs__nav-wrap) { overflow-x: auto; }
  :deep(.el-form-item) { display: block; margin-bottom: 16px; }
  :deep(.el-form-item__label) { display: block; line-height: 1.35; margin-bottom: 6px; text-align: left; }
  :deep(.el-form-item__content) { margin-left: 0 !important; }
}
:deep(.el-tabs__header) {
  margin: 0;
}
</style>
