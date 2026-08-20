<template>
  <div class="menu flex-column-center">
    <el-button
      v-for="button in buttons"
      size="large"
      :key="button.name"
      @click="button.action"
    >
      {{ button.name }}
    </el-button>
    <el-button size="large" @click="() => (hotkeysDialogVisible = true)"
      >Phím tắt</el-button
    >
  </div>
  <el-dialog
    v-model="hotkeysDialogVisible"
    width="min(92vw, 560px)"
    :show-close="true"
    :before-close="stopRecordKeyDown"
  >
    <template #header="{ titleClass, titleId }">
      <div class="hotkeys-header flex-space-between">
        <div :id="titleId" :class="titleClass">
          Cài đặt phím tắt
          <span v-if="recordKeyDowning">
            <el-text> / đang ghi </el-text>
          </span>
        </div>
        <el-button
          :disabled="recordKeyDowning"
          @click="saveHotKeys"
          :icon="CircleCheckFilled"
          >Lưu</el-button
        >
      </div>
    </template>

    <div class="hotkeys-settings flex-column-center">
      <div
        v-for="(button, buttonIndex) in buttons"
        :key="button.name"
        class="hotkeys-item flex-space-between"
      >
        <span class="title"
          ><el-text>{{ button.name }}</el-text></span
        >
        <div class="hotkeys-item__content">
          <div v-for="(key, hotKeysIndex) in button.hotKeys" :key="key">
            <kbd>{{ key }}</kbd>
            <span v-if="hotKeysIndex + 1 < button.hotKeys.length">
              <el-text>+</el-text>
            </span>
          </div>
          <span v-if="button.hotKeys.length == 0">Chưa đặt</span>
        </div>
        <el-button
          :disabled="recordKeyDowning"
          text
          :icon="Edit"
          @click="recordKeyDown(buttonIndex)"
          >Sửa</el-button
        >
      </div>
    </div>
  </el-dialog>
</template>

<script setup lang="ts">
import API from '@api'
import { CircleCheckFilled, Edit } from '@element-plus/icons-vue'
import hotkeys from 'hotkeys-js'
import { getSourceName, isInvaildSource, normalizeSource } from '../utils/souce'

const router = useRouter()
const store = useSourceStore()
const pull = () => {
  const loadingMsg = ElMessage({
    message: 'Đang tải…',
    showClose: true,
    duration: 0,
  })
  API.getSources()
    .then(({ data }) => {
      if (data.isSuccess) {
        store.changeTabName('editList')
        store.saveSources(data.data)
        ElMessage({
          message: `Đã kéo ${data.data.length} nguồn`,
          type: 'success',
        })
      } else {
        ElMessage({
          message: data.errorMsg ?? 'Lỗi backend',
          type: 'error',
        })
      }
    })
    .finally(() => loadingMsg.close())
}

const push = () => {
  const sources = store.sources
  store.changeTabName('editList')
  if (sources.length === 0) {
    return ElMessage({
      message: 'Không có dữ liệu',
      type: 'info',
    })
  }
  ElMessage({
    message: 'Đang đẩy dữ liệu',
    type: 'info',
  })
  API.saveSources(sources).then(({ data }) => {
    if (data.isSuccess) {
      const okData = data.data
      if (Array.isArray(okData)) {
        let failMsg = ``
        if (sources.length > okData.length) {
          failMsg = '\nNguồn đẩy thất bại sẽ được đánh dấu màu đỏ!'
          store.setPushReturnSources(okData)
        }
        ElMessage({
          message: `Đẩy hàng loạt nguồn vào “DrDucBook”\nTổng: ${
            sources.length
          } mục\nThành công: ${okData.length} mục\nThất bại: ${
            sources.length - okData.length
          } mục${failMsg}`,
          type: 'success',
        })
      }
    } else {
      ElMessage({
        message: `Đẩy hàng loạt nguồn thất bại!\nErrorMsg: ${data.errorMsg}`,
        type: 'error',
      })
    }
  })
}

const conver2Tab = () => {
  store.changeTabName('editTab')
  store.changeEditTabSource(store.currentSource)
}
const conver2Source = () => {
  store.changeCurrentSource(store.editTabSource)
}

const undo = () => {
  store.editHistoryUndo()
}

const clearEdit = () => {
  store.clearEdit()
  ElMessage({
    message: 'Đã xóa',
    type: 'success',
  })
}

const redo = () => {
  store.clearEdit()
  store.clearAllHistory()
  ElMessage({
    message: 'Đã xóa toàn bộ lịch sử',
    type: 'success',
  })
}

const saveSource = () => {
  const source = store.currentSource
  if (isInvaildSource(source)) {
    normalizeSource(source)
    API.saveSource(source).then(({ data }) => {
      const sourceName = getSourceName(source)
      if (data.isSuccess) {
        ElMessage({
          message: `Nguồn “${sourceName}” đã được lưu vào “DrDucBook”`,
          type: 'success',
        })
        //save to store
        store.saveCurrentSource()
      } else {
        ElMessage({
          message: `Nguồn “${sourceName}” lưu thất bại!\nErrorMsg: ${data.errorMsg}`,
          type: 'error',
        })
      }
    })
  } else {
    ElMessage({
      message: `Hãy kiểm tra đã điền đủ các mục <bắt buộc>`,
      type: 'error',
    })
  }
}

const debug = () => {
  store.startDebug()
}

const openWebServiceSettings = () => {
  router.push({ name: 'web-service' })
}

const buttons = ref<{ name: string; hotKeys: string[]; action: () => void }[]>(
  Array.of(
    { name: 'WebService', hotKeys: [], action: openWebServiceSettings },
    { name: '⇈ Đẩy nguồn', hotKeys: [], action: push },
    { name: '⇊ Kéo nguồn', hotKeys: [], action: pull },
    { name: '⋙ Tạo nguồn', hotKeys: [], action: conver2Tab },
    { name: '⋘ Sửa nguồn', hotKeys: [], action: conver2Source },
    { name: '✗ Xóa form', hotKeys: [], action: clearEdit },
    { name: '↶ Hoàn tác', hotKeys: [], action: undo },
    { name: '↷ Làm lại', hotKeys: [], action: redo },
    { name: '⇏ Gỡ lỗi nguồn', hotKeys: [], action: debug },
    { name: '✓ Lưu nguồn', hotKeys: [], action: saveSource },
  ),
)
const hotkeysDialogVisible = ref(false)

const recordKeyDowning = ref(false)

const recordKeyDownIndex = ref(-1)

const stopRecordKeyDown = () => {
  if (!recordKeyDowning.value) {
    hotkeysDialogVisible.value = false
  }
  recordKeyDowning.value = false
}

watch(
  hotkeysDialogVisible,
  visibale => {
    if (!visibale) {
      hotkeys.unbind('*')
      readHotkeysConfig()
      bindHotKeys()
      return
    }
    readHotkeysConfig()
    hotkeys.unbind()
    /**监听按键 */
    hotkeys('*', event => {
      event.preventDefault()
      const pressedKeys = hotkeys.getPressedKeyString()
      if (pressedKeys.length == 1 && pressedKeys[0] == 'esc') {
        //单独按下esc 不录入
        return
      }
      if (recordKeyDowning.value && recordKeyDownIndex.value > -1)
        buttons.value[recordKeyDownIndex.value].hotKeys = pressedKeys
    })
  },
  { immediate: true },
)

const recordKeyDown = (index: number) => {
  recordKeyDowning.value = true
  ElMessage({
    message: 'Nhấn ESC hoặc bấm vùng trống để kết thúc ghi phím',
    type: 'info',
  })
  buttons.value[index].hotKeys = []
  recordKeyDownIndex.value = index
}

const saveHotKeys = () => {
  const hotKeysConfig: string[][] = []
  buttons.value.forEach(({ hotKeys }) => {
    hotKeysConfig.push(hotKeys)
  })
  saveHotkeysConfig(hotKeysConfig)
  hotkeysDialogVisible.value = false
}

const bindHotKeys = () => {
  // hotkeysMặc định过滤INPUT SELECT TEXTAREA
  hotkeys.filter = () => true
  buttons.value.forEach(({ hotKeys, action }) => {
    if (hotKeys.length == 0) return
    hotkeys(hotKeys.join('+'), event => {
      event.preventDefault()
      action.call(null)
    })
  })
}
const saveHotkeysConfig = (config: string[][]) => {
  localStorage.setItem('legado_web_hotkeys', JSON.stringify(config))
}

/**
 * 读取Phím tắt配置
 * @return 是Không成功读取配置
 */
function readHotkeysConfig() {
  try {
    const localStorageConfig = localStorage.getItem('legado_web_hotkeys')
    if (localStorageConfig === null) return false
    const config = JSON.parse(localStorageConfig)
    if (!Array.isArray(config) || config.length == 0) return false
    buttons.value.forEach((button, index) => (button.hotKeys = config[index]))
    return true
  } catch {
    ElMessage({ message: 'Cấu hình phím tắt không đúng', type: 'error' })
    localStorage.removeItem('legado_web_hotkeys')
  }
  return false
}

onMounted(() => {
  /**读取热键配置 */
  if (readHotkeysConfig()) {
    hotkeysDialogVisible.value = false
  }
})
</script>

<style lang="scss" scoped>
.flex-space-between {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
}
.flex-column-center {
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.menu > .el-button {
  margin: 4px;
  padding: 1em;
  width: 6em;
}

.hotkeys-item {
  .title {
    width: 5em;
    display: flex;
    justify-content: flex-end;
    margin-right: 1em;
  }
  .hotkeys-item__content {
    display: flex;
    flex-wrap: wrap;
    flex: 1;
    div {
      margin-bottom: 1em;
    }
    span {
      margin: 0.5em;
    }
  }
}
</style>
