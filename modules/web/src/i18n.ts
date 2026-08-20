import { computed, reactive, ref, watch } from 'vue'
import { translateWebServiceUi } from '@/api/webService'

export type WebLocale = 'vi' | 'en' | 'zh-CN'
export const supportedLocales: Array<{ value: WebLocale; label: string }> = [
  { value: 'vi', label: 'Tiếng Việt' },
  { value: 'en', label: 'English' },
  { value: 'zh-CN', label: '简体中文' },
]
const messages: Record<WebLocale, Record<string, string>> = {
  vi: {
    library: 'Kho sách của bạn', list: 'Danh sách', types: 'Thể loại', display: 'Cài đặt hiển thị', sources: 'Nguồn sách', upload: 'Tải sách lên', rss: 'Nguồn RSS', webServiceSettings: 'Cài đặt WebService', search: 'Tìm truyện...', updating: 'Truyện đang cập nhật', completed: 'Truyện đã hoàn thành', allBooks: 'Toàn bộ giá sách', allTypes: 'Tất cả thể loại', openSettings: 'Mở cài đặt', backgroundText: 'Hình nền & độ rõ chữ', textBooks: 'Truyện chữ', comics: 'Truyện tranh', audio: 'Sách nói', video: 'Video', mobileMenu: 'Mở menu', background: 'Hình nền', uploadImage: 'Tải ảnh riêng', reset: 'Đặt lại', blur: 'Độ mờ', dim: 'Độ tối', reader: 'Đọc và dịch trên trình duyệt này', fontSize: 'Cỡ chữ', font: 'Font chữ', translation: 'Chế độ dịch', enableTranslation: 'Bật dịch khi đọc', modernSans: 'Sans hiện đại', readableSerif: 'Serif dễ đọc', monospace: 'Monospace',
    sourceFilter: 'Lọc nguồn', open: 'Mở', export: 'Xuất', delete: 'Xóa', clearAll: 'Xóa trắng', vbookRegistryTitle: 'Nhập registry VBook', vbookRegistryUrlPrompt: 'Dán URL registry VBook JSON', vbookRegistryImported: 'Đã nạp plugin VBook', vbookRegistryImportFailed: 'Không thể nạp registry VBook', webDiscoveryTitle: 'Khám phá trực tiếp trên web', webDiscoveryDescription: 'Chọn nguồn web độc lập với Explore trong app.', addSourceUrlPrompt: 'Dán URL JSON nguồn (có thể dùng #requestWithoutUA)', addSourceUrlTitle: 'Thêm nguồn từ URL', addSourceImported: 'Đã nạp nguồn vào app', sourceImportFailed: 'Không thể nạp nguồn', discoverySaveFailed: 'Không thể lưu nguồn khám phá web', exportDisabled: 'Export đang tắt trong WebService', sourceExportFailed: 'Không thể xuất dữ liệu; hãy kiểm tra quyền Export', noFile: 'Chưa chọn tệp',
    uploadEyebrow: 'THƯ VIỆN CÁ NHÂN', uploadTitle: 'Tải sách lên thiết bị', uploadDescription: 'Nhập sách vào thư mục sách đang dùng trong DrDucBook. Tệp chỉ được gửi tới thiết bị đang chạy dịch vụ web.', backHome: 'Về trang chủ', dropFiles: 'Thả tệp vào đây', supportedFormats: 'TXT · EPUB · UMD · PDF · MOBI · AZW · AZW3 · tối đa 512 MB mỗi tệp', chooseFiles: 'Chọn tệp', uploadProgress: 'Tiến trình nhập', clearList: 'Xóa danh sách', skippedFiles: 'tệp bị bỏ qua do sai định dạng hoặc quá lớn', waiting: 'Đang chờ', uploading: 'Đang tải lên...', importedToShelf: 'Đã nhập vào giá sách', importFailed: 'Không thể nhập tệp',
    settingsBack: 'Quay lại', refresh: 'Làm mới', features: 'Chức năng', webControl: 'Điều khiển từ web', exportDescription: 'Cho phép các lệnh xuất dữ liệu qua web.', autoTranslation: 'Dịch tự động', autoTranslationDescription: 'Cho phép web tạo tác vụ dịch chương bằng cấu hình của app.', serviceName: 'Tên dịch vụ', displayedOnWeb: 'Hiển thị trên web', saveName: 'Lưu tên', customBackground: 'Đang dùng ảnh riêng', default: 'Mặc định', noBackground: 'Chưa chọn ảnh nền', chooseImage: 'Chọn ảnh', deleteBackground: 'Xóa nền', displayMode: 'Kiểu hiển thị', position: 'Vị trí', center: 'Giữa', top: 'Trên', bottom: 'Dưới', left: 'Trái', right: 'Phải', dimLabel: 'Độ tối', blurLabel: 'Độ mờ', cover: 'Cover', contain: 'Contain',
    translationReader: 'Đọc bản dịch', provider: 'Provider', targetLanguage: 'Ngôn ngữ đích', listenCurrent: 'Nghe chương hiện tại', stopReading: 'Dừng đọc', pretranslate: 'Dịch trước chương', pretranslateCount: 'Số chương dịch trước', exportEbook: 'Xuất ebook', waitingService: 'Đang chờ WebService từ app', configReloading: 'Cấu hình đã thay đổi, đang tải lại', nameSaved: 'Đã lưu tên dịch vụ', imageTypeSupport: 'Chỉ hỗ trợ PNG, JPG hoặc WebP', imageTooLarge: 'Ảnh nền tối đa 5 MB', backgroundUpdated: 'Đã cập nhật ảnh nền', backgroundUpdateFailed: 'Không thể cập nhật ảnh nền', backgroundDeleted: 'Đã xóa ảnh nền', backgroundDeleteFailed: 'Không thể xóa ảnh nền', settingsReset: 'Đã đặt lại cấu hình WebService', settingsResetFailed: 'Không thể đặt lại cấu hình',
    personalLibrary: 'THƯ VIỆN CÁ NHÂN', continueStory: 'Đọc tiếp câu chuyện của bạn', libraryDescription: 'Giá sách trên thiết bị, nguồn online và lịch sử đọc được gom trong một không gian.', displaySettings: 'Cài đặt hiển thị', continueLabel: 'TIẾP TỤC', recentReading: 'Đang đọc gần đây', openBook: 'Mở sách', startReading: 'Bắt đầu đọc', bookshelfLabel: 'GIÁ SÁCH', clearFilter: 'Xóa bộ lọc', refreshDiscovery: 'Làm mới khám phá', yourBookshelf: 'GIÁ SÁCH CỦA BẠN', updatedBooks: 'Truyện mới cập nhật', searchResultFor: 'Kết quả tìm kiếm', books: 'truyện', noMatchingBooks: 'Chưa có sách phù hợp. Hãy thử nguồn online hoặc tải sách lên.', unknownAuthor: 'Chưa rõ tác giả', noLatestChapter: 'Chưa có chương mới', chapters: 'chương', onlineDiscovery: 'KHÁM PHÁ ONLINE', sourceSuggestions: 'Gợi ý từ nguồn sách', sourcesBusy: 'Một số nguồn đang bận', noDiscoveryData: 'Chưa có dữ liệu khám phá. Hãy thêm hoặc chọn nguồn ở trang Nguồn sách.', sourceName: 'Nguồn sách', notFoundBook: 'Không tìm thấy truyện?', onlineSearchHint: 'Thử tìm online bằng ô tìm kiếm phía trên hoặc mở Nguồn sách để cập nhật.', manageSources: 'Quản lý nguồn', noOnlineResults: 'Không có kết quả online', textNovel: 'Truyện chữ', comicNovel: 'Truyện tranh', language: 'Ngôn ngữ',
  },
  en: {
    library: 'Your library', list: 'Library', types: 'Genres', display: 'Display settings', sources: 'Book sources', upload: 'Upload books', rss: 'RSS sources', webServiceSettings: 'WebService settings', search: 'Search books...', updating: 'Updating', completed: 'Completed', allBooks: 'All books', allTypes: 'All genres', openSettings: 'Open settings', backgroundText: 'Background & readability', textBooks: 'Text', comics: 'Comics', audio: 'Audiobooks', video: 'Video', mobileMenu: 'Open menu', background: 'Background', uploadImage: 'Upload image', reset: 'Reset', blur: 'Blur', dim: 'Dim', reader: 'Reading and translation', fontSize: 'Font size', font: 'Font', translation: 'Translation', enableTranslation: 'Translate while reading', modernSans: 'Modern sans', readableSerif: 'Readable serif', monospace: 'Monospace',
    sourceFilter: 'Filter sources', open: 'Open', export: 'Export', delete: 'Delete', clearAll: 'Clear all', vbookRegistryTitle: 'Import VBook registry', vbookRegistryUrlPrompt: 'Paste a VBook registry JSON URL', vbookRegistryImported: 'VBook plugins loaded', vbookRegistryImportFailed: 'Could not load VBook registry', webDiscoveryTitle: 'Discover directly on the web', webDiscoveryDescription: 'Choose web sources independently from Explore in the app.', addSourceUrlPrompt: 'Paste a source JSON URL (you may use #requestWithoutUA)', addSourceUrlTitle: 'Add source from URL', addSourceImported: 'Sources loaded into the app', sourceImportFailed: 'Could not load sources', discoverySaveFailed: 'Could not save web discovery sources', exportDisabled: 'Export is disabled in WebService', sourceExportFailed: 'Could not export data; check Export permission', noFile: 'No file selected',
    uploadEyebrow: 'PERSONAL LIBRARY', uploadTitle: 'Upload books to device', uploadDescription: 'Import books into the DrDucBook library. Files are sent only to the device running the web service.', backHome: 'Back home', dropFiles: 'Drop files here', supportedFormats: 'TXT · EPUB · UMD · PDF · MOBI · AZW · AZW3 · up to 512 MB per file', chooseFiles: 'Choose files', uploadProgress: 'Import progress', clearList: 'Clear list', skippedFiles: 'files skipped because of type or size', waiting: 'Waiting', uploading: 'Uploading...', importedToShelf: 'Imported to library', importFailed: 'Could not import file',
    settingsBack: 'Back', refresh: 'Refresh', features: 'Features', webControl: 'Web controls', exportDescription: 'Allow data export commands through the web.', autoTranslation: 'Automatic translation', autoTranslationDescription: 'Allow the web to create chapter translation jobs using app settings.', serviceName: 'Service name', displayedOnWeb: 'Shown on the web', saveName: 'Save name', customBackground: 'Custom image active', default: 'Default', noBackground: 'No background selected', chooseImage: 'Choose image', deleteBackground: 'Delete background', displayMode: 'Display mode', position: 'Position', center: 'Center', top: 'Top', bottom: 'Bottom', left: 'Left', right: 'Right', dimLabel: 'Dim', blurLabel: 'Blur', cover: 'Cover', contain: 'Contain',
    translationReader: 'Read translation', provider: 'Provider', targetLanguage: 'Target language', listenCurrent: 'Listen to current chapter', stopReading: 'Stop reading', pretranslate: 'Pretranslate chapters', pretranslateCount: 'Chapters to pretranslate', exportEbook: 'Export ebook', waitingService: 'Waiting for WebService from the app', configReloading: 'Configuration changed; reloading', nameSaved: 'Service name saved', imageTypeSupport: 'PNG, JPG and WebP are supported', imageTooLarge: 'Background images must be 5 MB or smaller', backgroundUpdated: 'Background updated', backgroundUpdateFailed: 'Could not update background', backgroundDeleted: 'Background deleted', backgroundDeleteFailed: 'Could not delete background', settingsReset: 'WebService settings reset', settingsResetFailed: 'Could not reset settings',
    personalLibrary: 'PERSONAL LIBRARY', continueStory: 'Continue your story', libraryDescription: 'Your device library, online sources and reading history in one place.', displaySettings: 'Display settings', continueLabel: 'CONTINUE', recentReading: 'Continue reading', openBook: 'Open book', startReading: 'Start reading', bookshelfLabel: 'LIBRARY', clearFilter: 'Clear filter', refreshDiscovery: 'Refresh discovery', yourBookshelf: 'YOUR LIBRARY', updatedBooks: 'Recently updated', searchResultFor: 'Search results', books: 'books', noMatchingBooks: 'No matching books. Try online sources or upload a book.', unknownAuthor: 'Unknown author', noLatestChapter: 'No new chapter', chapters: 'chapters', onlineDiscovery: 'ONLINE DISCOVERY', sourceSuggestions: 'Suggestions from sources', sourcesBusy: 'Some sources are busy', noDiscoveryData: 'No discovery data yet. Add or select sources on the Sources page.', sourceName: 'Book source', notFoundBook: 'Can’t find a book?', onlineSearchHint: 'Search online above or open Book sources to refresh.', manageSources: 'Manage sources', noOnlineResults: 'No online results', textNovel: 'Text', comicNovel: 'Comics', language: 'Language',
  },
  'zh-CN': {
    library: '你的书架', list: '书架', types: '分类', display: '显示设置', sources: '书源', upload: '上传书籍', rss: 'RSS源', webServiceSettings: 'WebService设置', search: '搜索书籍...', updating: '更新中', completed: '已完成', allBooks: '全部书架', allTypes: '全部分类', openSettings: '打开设置', backgroundText: '背景与清晰度', textBooks: '文字书', comics: '漫画', audio: '有声书', video: '视频', mobileMenu: '打开菜单', background: '背景', uploadImage: '上传图片', reset: '重置', blur: '模糊', dim: '暗度', reader: '阅读与翻译', fontSize: '字号', font: '字体', translation: '翻译模式', enableTranslation: '阅读时翻译', modernSans: '现代无衬线', readableSerif: '易读衬线', monospace: '等宽字体',
    sourceFilter: '筛选书源', open: '打开', export: '导出', delete: '删除', clearAll: '清空', vbookRegistryTitle: '导入 VBook registry', vbookRegistryUrlPrompt: '粘贴 VBook registry JSON URL', vbookRegistryImported: 'VBook 插件已加载', vbookRegistryImportFailed: '无法加载 VBook registry', webDiscoveryTitle: '直接在网页上发现', webDiscoveryDescription: '独立于应用 Explore 选择网页书源。', addSourceUrlPrompt: '粘贴书源 JSON URL（可使用 #requestWithoutUA）', addSourceUrlTitle: '从 URL 添加书源', addSourceImported: '书源已加载到应用', sourceImportFailed: '无法加载书源', discoverySaveFailed: '无法保存网页发现书源', exportDisabled: 'WebService 中已关闭导出', sourceExportFailed: '无法导出数据，请检查导出权限', noFile: '未选择文件',
    uploadEyebrow: '个人书库', uploadTitle: '上传书籍到设备', uploadDescription: '将书籍导入 DrDucBook 书库。文件只会发送到正在运行网页服务的设备。', backHome: '返回首页', dropFiles: '将文件拖到这里', supportedFormats: 'TXT · EPUB · UMD · PDF · MOBI · AZW · AZW3 · 每个文件最大 512 MB', chooseFiles: '选择文件', uploadProgress: '导入进度', clearList: '清空列表', skippedFiles: '个文件因格式或大小被跳过', waiting: '等待中', uploading: '上传中...', importedToShelf: '已导入书架', importFailed: '无法导入文件',
    settingsBack: '返回', refresh: '刷新', features: '功能', webControl: '网页控制', exportDescription: '允许通过网页执行导出数据命令。', autoTranslation: '自动翻译', autoTranslationDescription: '允许网页使用应用配置创建章节翻译任务。', serviceName: '服务名称', displayedOnWeb: '显示在网页上', saveName: '保存名称', customBackground: '正在使用自定义图片', default: '默认', noBackground: '未选择背景图片', chooseImage: '选择图片', deleteBackground: '删除背景', displayMode: '显示方式', position: '位置', center: '居中', top: '顶部', bottom: '底部', left: '左侧', right: '右侧', dimLabel: '暗度', blurLabel: '模糊', cover: '覆盖', contain: '包含',
    translationReader: '阅读翻译', provider: '翻译服务', targetLanguage: '目标语言', listenCurrent: '朗读当前章节', stopReading: '停止朗读', pretranslate: '预翻译章节', pretranslateCount: '预翻译章节数', exportEbook: '导出电子书', waitingService: '等待应用中的 WebService', configReloading: '配置已更改，正在重新加载', nameSaved: '服务名称已保存', imageTypeSupport: '支持 PNG、JPG 和 WebP', imageTooLarge: '背景图片不能超过 5 MB', backgroundUpdated: '背景已更新', backgroundUpdateFailed: '无法更新背景', backgroundDeleted: '背景已删除', backgroundDeleteFailed: '无法删除背景', settingsReset: 'WebService 设置已重置', settingsResetFailed: '无法重置设置',
    personalLibrary: '个人书库', continueStory: '继续你的故事', libraryDescription: '设备书架、在线书源和阅读历史都集中在这里。', displaySettings: '显示设置', continueLabel: '继续阅读', recentReading: '最近阅读', openBook: '打开书籍', startReading: '开始阅读', bookshelfLabel: '书架', clearFilter: '清除筛选', refreshDiscovery: '刷新发现', yourBookshelf: '你的书架', updatedBooks: '最近更新', searchResultFor: '搜索结果', books: '本书', noMatchingBooks: '没有符合条件的书籍。请尝试在线书源或上传书籍。', unknownAuthor: '未知作者', noLatestChapter: '暂无最新章节', chapters: '章', onlineDiscovery: '在线发现', sourceSuggestions: '书源推荐', sourcesBusy: '部分书源忙碌', noDiscoveryData: '暂无发现数据，请在书源页面添加或选择书源。', sourceName: '书源', notFoundBook: '找不到书？', onlineSearchHint: '使用上方搜索框在线搜索，或打开书源刷新。', manageSources: '管理书源', noOnlineResults: '没有在线结果', textNovel: '文字书', comicNovel: '漫画', language: '语言',
  },
}
const additionalMessages: Record<WebLocale, Record<string, string>> = {
  vi: {
    pauseReading: 'Tạm dừng',
    resumeReading: 'Tiếp tục',
    previousChapter: 'Chương trước',
    nextChapter: 'Chương sau',
    discovery: 'Khám phá',
    discoveryTitle: 'Khám phá nguồn sách trên web',
    discoveryDescription: 'Tìm và mở sách trực tiếp từ các nguồn đã chọn, độc lập với Explore native.',
    discoverySources: 'NGUỒN WEB',
    discoverySourcesTitle: 'Nguồn dùng cho Khám phá',
    discoverySourcesDescription: 'Danh sách này chỉ áp dụng cho dịch vụ web và được lưu trong WebService policy.',
    discoveryLoadFailed: 'Không thể tải dữ liệu khám phá. Hãy kiểm tra nguồn và thử lại.',
    serviceNameNativeOnly: 'Tên dịch vụ chỉ được thay đổi trong phần cài đặt WebService của app native.',
    searchChinese: 'Tìm bằng tên tiếng Trung',
    searchChineseHint: 'Dịch từ khóa tiếng Việt sang tiếng Trung trước khi tìm',
    searchKeywordUsed: 'Từ khóa đã dịch',
  },
  en: {
    pauseReading: 'Pause',
    resumeReading: 'Resume',
    previousChapter: 'Previous chapter',
    nextChapter: 'Next chapter',
    discovery: 'Discover',
    discoveryTitle: 'Discover books on the web',
    discoveryDescription: 'Find and open books from selected sources independently of native Explore.',
    discoverySources: 'WEB SOURCES',
    discoverySourcesTitle: 'Sources used by Discovery',
    discoverySourcesDescription: 'This selection applies only to WebService and is stored in its policy.',
    discoveryLoadFailed: 'Could not load discovery data. Check the sources and try again.',
    serviceNameNativeOnly: 'The service name can only be changed in the native app WebService settings.',
    searchChinese: 'Search with Chinese title',
    searchChineseHint: 'Translate a Vietnamese keyword to Chinese before searching',
    searchKeywordUsed: 'Translated keyword',
  },
  'zh-CN': {
    pauseReading: '暂停',
    resumeReading: '继续',
    previousChapter: '上一章',
    nextChapter: '下一章',
    discovery: '发现',
    discoveryTitle: '在网页上发现书籍',
    discoveryDescription: '使用选定书源查找并打开书籍，独立于原生 Explore。',
    discoverySources: '网页书源',
    discoverySourcesTitle: '发现使用的书源',
    discoverySourcesDescription: '此选择仅应用于 WebService，并保存在服务策略中。',
    discoveryLoadFailed: '无法加载发现数据，请检查书源后重试。',
    serviceNameNativeOnly: '服务名称只能在原生应用的 WebService 设置中修改。',
    searchChinese: '使用中文书名搜索',
    searchChineseHint: '搜索前将越南语关键词翻译成中文',
    searchKeywordUsed: '已翻译关键词',
  },
}
const stored = localStorage.getItem('web-locale') as WebLocale | null
export const webLocale = ref<WebLocale>(stored && messages[stored] ? stored : 'vi')
export const localeOptions = computed(() => supportedLocales)
const remoteMessages = reactive<Record<string, string>>({})
const dynamicMessages = reactive<Record<string, string>>({})
const loadRemoteMessages = async (locale: WebLocale) => {
  if (locale === 'vi') return
  const keys = Object.keys(messages.vi)
  try {
    const result = await translateWebServiceUi('web-shell', keys.map(key => messages.vi[key]), locale)
    result.texts.forEach((value, index) => {
      if (value && value !== messages.vi[keys[index]]) remoteMessages[`${locale}:${keys[index]}`] = value
    })
  } catch {
    // Static locale strings remain available when the app has no translation provider/network.
  }
}
export const setWebLocale = (value: WebLocale) => {
  webLocale.value = value
  localStorage.setItem('web-locale', value)
  document.documentElement.lang = value
  void loadRemoteMessages(value)
}
export const t = (key: string) => remoteMessages[`${webLocale.value}:${key}`] || messages[webLocale.value][key] || additionalMessages[webLocale.value][key] || messages.vi[key] || additionalMessages.vi[key] || key
export const dynamicText = (scopeKey: string, value: string | null | undefined) => {
  const original = value || ''
  if (!original) return original
  return dynamicMessages[`${scopeKey}:${webLocale.value}:${original}`] || original
}
export const translateDynamicTexts = async (scopeKey: string, values: Array<string | null | undefined>) => {
  const unique = [...new Set(values.map(value => value?.trim()).filter((value): value is string => Boolean(value)))]
  if (unique.length === 0) return
  try {
    const result = await translateWebServiceUi(`dynamic:${scopeKey}`, unique, webLocale.value)
    result.texts.forEach((translated, index) => {
      const original = unique[index]
      if (original && translated && translated !== original) {
        dynamicMessages[`${scopeKey}:${webLocale.value}:${original}`] = translated
      }
    })
  } catch {
    // Dynamic content remains readable when the provider is unavailable.
  }
}
watch(webLocale, value => { void loadRemoteMessages(value) }, { immediate: true })
