/** https://github.com/gedoor/legado/tree/master/app/src/main/java/io/legado/app/data/entities */
type BaseSource = {
  /**
   * Tỷ lệ đồng thời
   */
  concurrentRate?: string
  /**
   * Địa chỉ đăng nhập
   */
  loginUrl?: string

  /**
   * 登录UI
   */
  loginUi?: string

  /**
   * Header yêu cầu
   */
  header?: string

  /**
   * BậtcookieJar
   */
  enabledCookieJar?: boolean

  /**
   * Thư viện JS
   */
  jsLib?: string
}
type BookSoure = BaseSource & {
  // 地址，包括 http/https
  bookSourceUrl: string
  // 名称
  bookSourceName: string
  // 分组
  bookSourceGroup?: string
  // 类型，0 Văn bản，1 Âm thanh, 2 Hình ảnh, 3 Tệp（指的是类似知轩藏书只提供下载的网站）
  bookSourceType: number
  // Chi tiết页url正则
  bookUrlPattern?: string
  // 手动Số thứ tự sắp xếp
  customOrder: number
  // 是KhôngBật
  enabled: boolean
  // BậtKhám phá
  enabledExplore: boolean
  // Kiểm tra đăng nhậpjs
  loginCheckJs?: string
  // Giải mã bìajs
  coverDecodeJs?: string
  // 注释
  bookSourceComment?: string
  // 自定义Mô tả biến
  variableComment?: string
  // 最后更新时间，用于排序
  lastUpdateTime: number
  // 响应时间，用于排序
  respondTime: number
  // 智能排序的权重
  weight: number
  // Khám pháurl
  exploreUrl?: string
  // Khám phá筛选规则
  exploreScreen?: string
  // Khám phá规则
  ruleExplore?: ExploreRule
  // Tìm kiếmurl
  searchUrl?: string
  // Tìm kiếm规则
  ruleSearch?: SearchRule
  // 书籍信息页规则
  ruleBookInfo?: BookInfoRule
  // Mục lục页规则
  ruleToc?: TocRule
  // Nội dung页规则
  ruleContent?: ContentRule
  // Bình luận đoạn规则
  ruleReview?: ReviewRule
}
type RuleSearch = {
  checkKeyWord?: string
  [prop: string]: string
}
/* type ExploreRule = {
    [prop:string]: string
}
type BookInfoRule = {
    [prop:string]: string
}
type TocRule = {
    [prop:string]: string
}
type ContentRule = {
    [prop:string]: string
}
type ReviewRule = {
    [prop:string]: string
} */
type RssSource = BaseSource & {
  sourceUrl: string
  // 名称
  sourceName: string
  // Biểu tượng
  sourceIcon: string
  // 分组
  sourceGroup?: string
  // 注释
  sourceComment?: string
  // 是KhôngBật
  enabled: boolean
  // 自定义Mô tả biến
  variableComment?: string
  /**Kiểm tra đăng nhậpjs**/
  loginCheckJs?: string
  /**Giải mã bìajs**/
  coverDecodeJs?: string
  /**分类Url**/
  sortUrl?: string
  /**是Không单url源**/
  singleUrl: boolean
  /*Quy tắc danh sách*/
  /**Kiểu danh sách,0,1,2**/
  articleStyle: number
  /**Quy tắc danh sách**/
  ruleArticles?: string
  /**下一页规则**/
  ruleNextPage?: string
  /**Quy tắc tiêu đề**/
  ruleTitle?: string
  /**发布日期规则**/
  rulePubDate?: string
  /*webView规则*/
  /**Quy tắc mô tả**/
  ruleDescription?: string
  /**Hình ảnh规则**/
  ruleImage?: string
  /**Quy tắc liên kết**/
  ruleLink?: string
  /**Quy tắc nội dung**/
  ruleContent?: string
  /**Nội dungurlDanh sách trắng**/
  contentWhitelist?: string
  /**Nội dungurlDanh sách đen**/
  contentBlacklist?: string
  /**
   * 跳转url拦截,
   * js, 返回true拦截,js变量url,可以通过jsMởurl,比如调用阅读Tìm kiếm,添加Kệ sách等,简化规则写法,不用webView js注入
   * **/
  shouldOverrideUrlLoading?: string
  /**webView样式**/
  style?: string
  enableJs: boolean
  loadWithBaseUrl: boolean
  /**注入js**/
  injectJs?: string
  /*其它规则*/
  /**最后更新时间，用于排序**/
  lastUpdateTime: number
  customOrder: number
}
type Source = BookSoure | RssSource

export { Source, BookSoure, RssSource }
