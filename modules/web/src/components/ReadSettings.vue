<template>
  <div
    class="settings-wrapper"
    :style="popupTheme"
    :class="{ night: isNight, day: !isNight }"
  >
    <div class="settings-title">Cài đặt</div>
    <div class="setting-list">
      <ul>
        <li class="theme-list">
          <i>Chủ đề đọc</i>
          <span
            class="theme-item"
            v-for="(themeColor, index) in themeColors"
            :key="index"
            :style="themeColor"
            ref="themes"
            @click="setTheme(index)"
            :class="{ selected: theme == index }"
            ><em v-if="index < 6" class="iconfont">&#58980;</em
            ><em v-else class="moon-icon">{{ moonIcon }}</em></span
          >
        </li>
        <li class="font-list">
          <i>Phông chữ nội dung</i>
          <span
            class="font-item"
            v-for="(font, index) in fonts"
            :key="index"
            :class="{ selected: selectedFont == index }"
            @click="setFont(index)"
            >{{ font }}</span
          >
        </li>
        <li class="font-list">
          <i>Phông tùy chỉnh</i>
          <el-tooltip effect="dark" content="Tên phông tùy chỉnh" placement="top">
            <input
              type="text"
              class="font-item font-item-input"
              v-model="customFontName"
              placeholder="Nhập tên phông tùy chỉnh"
            />
          </el-tooltip>

          <el-popover
            placement="top"
            width="270"
            trigger="click"
            v-model:visible="customFontSavePopVisible"
          >
            <p>
              Nếu phông đã được cài trên thiết bị, hãy nhập đúng tên phông; hoặc tải phông từ mạng.
            </p>
            <div style="text-align: right; margin: 0">
              <el-button
                size="small"
                plain
                @click="customFontSavePopVisible = false"
                >Hủy</el-button
              >
              <el-button type="primary" size="small" @click="setCustomFont()"
                >Xác nhận</el-button
              >
              <el-button type="primary" size="small" @click="loadFontFromURL()"
                >Tải từ mạng</el-button
              >
            </div>
            <template #reference>
              <span type="text" class="font-item">Lưu</span>
            </template>
          </el-popover>
        </li>
        <li class="font-size">
          <i>Cỡ chữ</i>
          <div class="resize">
            <span class="less" @click="lessFontSize"
              ><em class="iconfont">&#58966;</em></span
            ><b></b> <span class="lang">{{ fontSize }}</span
            ><b></b>
            <span class="more" @click="moreFontSize"
              ><em class="iconfont">&#58976;</em></span
            >
          </div>
        </li>
        <li class="letter-spacing">
          <i>Giãn chữ</i>
          <div class="resize">
            <span class="less" @click="lessLetterSpacing"
              ><em class="iconfont">&#58966;</em></span
            ><b></b> <span class="lang">{{ spacing.letter.toFixed(2) }}</span
            ><b></b>
            <span class="more" @click="moreLetterSpacing"
              ><em class="iconfont">&#58976;</em></span
            >
          </div>
        </li>
        <li class="line-spacing">
          <i>Giãn dòng</i>
          <div class="resize">
            <span class="less" @click="lessLineSpacing"
              ><em class="iconfont">&#58966;</em></span
            ><b></b> <span class="lang">{{ spacing.line.toFixed(1) }}</span
            ><b></b>
            <span class="more" @click="moreLineSpacing"
              ><em class="iconfont">&#58976;</em></span
            >
          </div>
        </li>
        <li class="paragraph-spacing">
          <i>Giãn đoạn</i>
          <div class="resize">
            <div class="resize">
              <span class="less" @click="lessParagraphSpacing"
                ><em class="iconfont">&#58966;</em></span
              ><b></b>
              <span class="lang">{{ spacing.paragraph.toFixed(1) }}</span
              ><b></b>
              <span class="more" @click="moreParagraphSpacing"
                ><em class="iconfont">&#58976;</em></span
              >
            </div>
          </div>
        </li>
        <li class="read-width" v-if="!store.miniInterface">
          <i>Độ rộng trang</i>
          <div class="resize">
            <span class="less" @click="lessReadWidth"
              ><em class="iconfont">&#58965;</em></span
            ><b></b> <span class="lang">{{ readWidth }}</span
            ><b></b>
            <span class="more" @click="moreReadWidth"
              ><em class="iconfont">&#58975;</em></span
            >
          </div>
        </li>
        <li class="paragraph-spacing">
          <i>Tốc độ lật trang</i>
          <div class="resize">
            <div class="resize">
              <span class="less" @click="lessJumpDuration">
                <em class="iconfont">&#xe625;</em>
              </span>
              <b></b> <span class="lang">{{ jumpDuration }}</span
              ><b></b>
              <span class="more" @click="moreJumpDuration"
                ><em class="iconfont">&#xe626;</em></span
              >
            </div>
          </div>
        </li>
        <li class="infinite-loading">
          <i>Chế độ đọc</i>
          <span
            class="infinite-loading-item"
            :class="{ selected: readMode === 'vertical' }"
            @click="setReadMode('vertical')"
            >Cuộn dọc</span
          >
          <span
            class="infinite-loading-item"
            :class="{ selected: readMode === 'paged' }"
            @click="setReadMode('paged')"
            >Trang ngang</span
          >
        </li>
        <li class="infinite-loading">
          <i>Tải liên tục</i>
          <span
            class="infinite-loading-item"
            :key="0"
            :class="{ selected: infiniteLoading == false }"
            @click="setInfiniteLoading(false)"
            >Tắt</span
          >
          <span
            class="infinite-loading-item"
            :key="1"
            :class="{ selected: infiniteLoading == true }"
            @click="setInfiniteLoading(true)"
            >Bật</span
          >
        </li>
      </ul>
    </div>
  </div>
</template>

<script setup lang="ts">
import '../assets/fonts/popfont.css'
import '../assets/fonts/iconfont.css'
import settings from '../config/themeConfig'
import API from '@api'
import { useDebounceFn } from '@vueuse/shared'

const store = useBookStore()
const saveConfigDebounce = useDebounceFn(
  () => API.saveReadConfig(store.config),
  500,
)
//阅读界面Cài đặt改变时Lưu同步配置
watch(
  () => store.config,
  () => {
    saveConfigDebounce()
  },
  {
    deep: 2, //深度为2
  },
)

//主题颜色
const theme = computed(() => store.theme)
const isNight = computed(() => store.isNight)
const moonIcon = computed(() => (theme.value == 6 ? '' : ''))
const themeColors = [
  {
    background: 'rgba(250, 245, 235, 0.8)',
  },
  {
    background: 'rgba(245, 234, 204, 0.8)',
  },
  {
    background: 'rgba(230, 242, 230, 0.8)',
  },
  {
    background: 'rgba(228, 241, 245, 0.8)',
  },
  {
    background: 'rgba(245, 228, 228, 0.8)',
  },
  {
    background: 'rgba(224, 224, 224, 0.8)',
  },
  {
    background: 'rgba(0, 0, 0, 0.5)',
  },
]
const popupTheme = computed(() => {
  return {
    background: settings.themes[theme.value].popup,
  }
})
const setTheme = (theme: number) => {
  store.config.theme = theme
}

//预置字体
const fonts = ref(['Microsoft YaHei', 'SimSun', 'KaiTi'])
const setFont = (font: number) => {
  store.config.font = font
}
const selectedFont = computed(() => {
  return store.config.font
})
//自定义字体
const customFontName = ref(store.config.customFontName)
const customFontSavePopVisible = ref(false)
const setCustomFont = () => {
  customFontSavePopVisible.value = false
  store.config.font = -1
  store.config.customFontName = customFontName.value
}
// 加载网络字体
const loadFontFromURL = () => {
  customFontSavePopVisible.value = false
  ElMessageBox.prompt('Nhập liên kết phông chữ', 'Thông báo', {
    confirmButtonText: 'Xác nhận',
    cancelButtonText: 'Hủy',
    inputPattern: /^https?:.+$/,
    inputErrorMessage: 'URL không hợp lệ',
    beforeClose: (action, instance, done) => {
      if (action === 'confirm') {
        instance.confirmButtonLoading = true
        instance.confirmButtonText = 'Đang tải…'
        // instance.inputValue
        const url = instance.inputValue
        if (typeof FontFace !== 'function') {
          ElMessage.error('Trình duyệt không hỗ trợ FontFace')
          return done()
        }
        const fontface = new FontFace(customFontName.value, `url("${url}")`)
        document.fonts.add(fontface)
        fontface
          .load()
          //API.getBookShelf()
          .then(function () {
            instance.confirmButtonLoading = false
            ElMessage.info('Tải phông thành công!')
            setCustomFont()
            done()
          })
          .catch(function (error) {
            instance.confirmButtonLoading = false
            instance.confirmButtonText = 'Xác nhận'
            ElMessage.error('Tải thất bại, hãy kiểm tra URL đã nhập')
            throw error
          })
      } else {
        done()
      }
    },
  })
}

//Cỡ chữ
const fontSize = computed(() => {
  return store.config.fontSize
})
const moreFontSize = () => {
  if (store.config.fontSize < 48) store.config.fontSize += 2
}
const lessFontSize = () => {
  if (store.config.fontSize > 12) store.config.fontSize -= 2
}

//字 行 段落间距
const spacing = computed(() => {
  return store.config.spacing
})
const lessLetterSpacing = () => {
  store.config.spacing.letter -= 0.01
}
const moreLetterSpacing = () => {
  store.config.spacing.letter += 0.01
}
const lessLineSpacing = () => {
  store.config.spacing.line -= 0.1
}
const moreLineSpacing = () => {
  store.config.spacing.line += 0.1
}
const lessParagraphSpacing = () => {
  store.config.spacing.paragraph -= 0.1
}
const moreParagraphSpacing = () => {
  store.config.spacing.paragraph += 0.1
}

//Độ rộng trang
const readWidth = computed(() => {
  return store.config.readWidth
})
const moreReadWidth = () => {
  // 此时会截断页面
  if (store.config.readWidth + 160 + 2 * 68 > window.innerWidth) return
  store.config.readWidth += 160
}
const lessReadWidth = () => {
  if (store.config.readWidth > 640) store.config.readWidth -= 160
}

//Tốc độ lật trang
const jumpDuration = computed(() => {
  return store.config.jumpDuration
})
const moreJumpDuration = () => {
  store.config.jumpDuration += 100
}
const lessJumpDuration = () => {
  if (store.config.jumpDuration === 0) return
  store.config.jumpDuration -= 100
}

//Tải liên tục
const infiniteLoading = computed(() => {
  return store.config.infiniteLoading
})
const setInfiniteLoading = (loading: boolean) => {
  store.config.infiniteLoading = loading
}

const readMode = computed(() => store.config.readMode)
const setReadMode = (mode: 'vertical' | 'paged') => {
  store.config.readMode = mode
}
</script>

<style lang="scss" scoped>
:deep(.iconfont) {
  font-family: iconfont;
  font-style: normal;
}

:deep(.moon-icon) {
  font-family: iconfont;
  font-style: normal;
}

.settings-wrapper {
  user-select: none;
  margin: -13px;
  /*   width: 478px;
  height: 350px; */
  text-align: left;
  padding: 40px 0 40px 24px;
  background: #ede7da url('../assets/imgs/themes/popup_1.png') repeat;

  .settings-title {
    font-size: 18px;
    line-height: 22px;
    margin-bottom: 28px;
    font-family: FZZCYSK;
    font-weight: 400;
  }

  .setting-list {
    max-height: calc(70vh - 50px);
    overflow: auto;

    ul {
      list-style: none outside none;
      margin: 0;
      padding: 0;

      li {
        list-style: none outside none;

        i {
          font:
            12px / 16px PingFangSC-Regular,
            '-apple-system',
            Simsun;
          display: inline-block;
          min-width: 48px;
          margin-right: 16px;
          vertical-align: middle;
          color: #666;
        }

        .theme-item {
          line-height: 32px;
          width: 34px;
          height: 34px;
          margin-right: 16px;
          margin-top: 5px;
          border-radius: 100%;
          display: inline-block;
          cursor: pointer;
          text-align: center;
          vertical-align: middle;

          .iconfont {
            display: none;
          }
        }

        .selected {
          color: #ed4259;

          .iconfont {
            display: inline;
          }
        }
      }

      .font-list,
      .infinite-loading {
        margin-top: 28px;

        .font-item,
        .infinite-loading-item {
          width: 78px;
          height: 34px;
          cursor: pointer;
          margin-right: 16px;
          border-radius: 2px;
          text-align: center;
          vertical-align: middle;
          display: inline-block;
          font:
            14px / 34px PingFangSC-Regular,
            HelveticaNeue-Light,
            'Helvetica Neue Light',
            'Microsoft YaHei',
            sans-serif;
        }
        .font-item-input {
          width: 168px;
          color: #000000;
        }
        .selected {
          color: #ed4259;
          border: 1px solid #ed4259;
        }

        .font-item:hover,
        .infinite-loading-item:hover {
          border: 1px solid #ed4259;
          color: #ed4259;
        }
      }

      .font-size,
      .read-width,
      .letter-spacing,
      .line-spacing,
      .paragraph-spacing {
        margin-top: 28px;

        .resize {
          display: inline-block;
          width: 274px;
          height: 34px;
          vertical-align: middle;
          border-radius: 2px;

          span {
            width: 89px;
            height: 34px;
            line-height: 34px;
            display: inline-block;
            cursor: pointer;
            text-align: center;
            vertical-align: middle;

            em {
              font-style: normal;
            }
          }

          .less:hover,
          .more:hover {
            color: #ed4259;
          }

          .lang {
            color: #a6a6a6;
            font-weight: 400;
            font-family: FZZCYSK;
          }

          b {
            display: inline-block;
            height: 20px;
            vertical-align: middle;
          }
        }
      }
    }
  }
}

.night {
  :deep(.theme-item) {
    border: 1px solid #666;
  }

  :deep(.selected) {
    border: 1px solid #666;
  }

  :deep(.moon-icon) {
    color: #ed4259;
  }

  :deep(.font-list),
  .infinite-loading {
    .font-item,
    .infinite-loading-item {
      border: 1px solid #666;
      background: rgba(45, 45, 45, 0.5);
    }
  }

  :deep(.resize) {
    border: 1px solid #666;
    background: rgba(45, 45, 45, 0.5);

    b {
      border-right: 1px solid #666;
    }
  }
}

.day {
  :deep(.theme-item) {
    border: 1px solid #e5e5e5;
  }

  :deep(.selected) {
    border: 1px solid #ed4259;
  }

  :deep(.moon-icon) {
    display: inline;
    color: rgba(255, 255, 255, 0.2);
  }

  :deep(.font-list),
  .infinite-loading {
    .font-item,
    .infinite-loading-item {
      background: rgba(255, 255, 255, 0.5);
      border: 1px solid rgba(0, 0, 0, 0.1);
    }
  }

  :deep(.resize) {
    border: 1px solid #e5e5e5;
    background: rgba(255, 255, 255, 0.5);

    b {
      border-right: 1px solid #e5e5e5;
    }
  }
}

@media screen and (max-width: 500px) {
  .settings-wrapper i {
    display: flex !important;
    flex-wrap: wrap;
    padding-bottom: 5px !important;
  }
}
</style>
