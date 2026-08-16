export default {
  base: {
    name: 'Cơ bản',
    children: [
      {
        title: 'Tên miền nguồn',
        id: 'sourceUrl',
        type: 'String',
        hint: 'Thường nhập trang chủ website, ví dụ: https://www.qidian.com',
        required: true,
      },
      {
        title: 'Biểu tượng',
        id: 'sourceIcon',
        type: 'String',
        hint: 'Nhập liên kết ảnh online',
      },
      {
        title: 'Tên nguồn',
        id: 'sourceName',
        type: 'String',
        hint: 'Sẽ hiển thị trong danh sách nguồn',
        required: true,
      },
      {
        title: 'Nhóm nguồn',
        id: 'sourceGroup',
        type: 'String',
        hint: 'Mô tả đặc điểm của nguồn',
      },
      {
        title: 'Ghi chú nguồn',
        id: 'sourceComment',
        type: 'String',
        hint: 'Mô tả tác giả và trạng thái nguồn',
      },
      {
        title: 'Địa chỉ phân loại',
        id: 'sortUrl',
        type: 'String',
        hint: 'Tên 1::Liên kết 1\nTên 2::Liên kết 2',
      },
      {
        title: 'Địa chỉ đăng nhập',
        id: 'loginUrl',
        type: 'String',
        hint: 'Nhập URL đăng nhập website, chỉ dùng cho nguồn cần đăng nhập',
      },
      {
        title: 'Giao diện đăng nhập',
        id: 'loginUi',
        type: 'String',
        hint: 'Tùy chỉnh giao diện đăng nhập',
      },
      {
        title: 'Kiểm tra đăng nhập',
        id: 'loginCheckJs',
        type: 'String',
        hint: 'Kiểm tra đăng nhậpjs',
      },
      {
        title: 'Giải mã bìa',
        id: 'coverDecodeJs',
        type: 'String',
        hint: 'Giải mã bìajs',
      },
      {
        title: 'Header yêu cầu',
        id: 'header',
        type: 'String',
        hint: 'Định danh client',
      },
      {
        title: 'Mô tả biến',
        id: 'variableComment',
        type: 'String',
        hint: 'Mô tả biến nguồn',
      },
      {
        title: 'Tỷ lệ đồng thời',
        id: 'concurrentRate',
        type: 'String',
        hint: 'Tỷ lệ đồng thời',
      },
      {
        title: 'Thư viện JS',
        id: 'jsLib',
        type: 'String',
        hint: 'Thư viện JS, có thể nhập JS hoặc key-value object để lấy tệp JS online',
      },
    ],
  },
  list: {
    name: 'Danh sách',
    children: [
      {
        title: 'Quy tắc danh sách',
        id: 'ruleArticles',
        type: 'String',
        hint: 'Kết quả quy tắc là List<Element>',
      },
      {
        title: 'Quy tắc phân trang',
        id: 'ruleNextPage',
        type: 'String',
        hint: 'Liên kết trang sau, kết quả quy tắc là List<String> hoặc String',
      },
      {
        title: 'Quy tắc tiêu đề',
        id: 'ruleTitle',
        type: 'String',
        hint: 'Tiêu đề bài viết, kết quả quy tắc là String',
      },
      {
        title: 'Quy tắc thời gian',
        id: 'rulePubDate',
        type: 'String',
        hint: 'Thời gian đăng bài, kết quả quy tắc là String',
      },
      {
        title: 'Quy tắc mô tả',
        id: 'ruleDescription',
        type: 'String',
        hint: 'Mô tả ngắn bài viết, kết quả quy tắc là String',
      },
      {
        title: 'Quy tắc ảnh',
        id: 'ruleImage',
        type: 'String',
        hint: 'Liên kết ảnh bài viết, kết quả quy tắc là String',
      },
      {
        title: 'Quy tắc liên kết',
        id: 'ruleLink',
        type: 'String',
        hint: 'Liên kết bài viết, kết quả quy tắc là String',
      },
    ],
  },
  webView: {
    name: 'WebView',
    children: [
      {
        title: 'Quy tắc nội dung',
        id: 'ruleContent',
        type: 'String',
        hint: 'Nội dung bài viết',
      },
      {
        title: 'Quy tắc CSS',
        id: 'style',
        type: 'String',
        hint: 'CSS nội dung bài viết',
      },
      {
        title: 'Quy tắc chèn',
        id: 'injectJs',
        type: 'String',
        hint: 'JavaScript chèn vào trang web',
      },
      {
        title: 'Danh sách đen',
        id: 'contentBlacklist',
        type: 'String',
        hint: 'Danh sách đen liên kết WebView, phân tách bằng dấu phẩy tiếng Anh',
      },
      {
        title: 'Danh sách trắng',
        id: 'contentWhitelist',
        type: 'String',
        hint: 'Danh sách trắng liên kết WebView, phân tách bằng dấu phẩy tiếng Anh',
      },
      {
        title: 'Chặn liên kết',
        id: 'shouldOverrideUrlLoading',
        type: 'String',
        hint: 'Nhập JS; biến url là liên kết tài nguyên hiện tại; trả true để chặn',
      },
    ],
  },
  other: {
    name: 'Khác',
    children: [
      {
        title: 'Kiểu danh sách',
        id: 'articleStyle',
        type: 'Array',
        array: ['Mặc định', 'Ảnh lớn', 'Hai cột'],
      },
      {
        title: 'Địa chỉ tải',
        id: 'loadWithBaseUrl',
        type: 'Boolean',
      },
      {
        title: 'Bật JS',
        id: 'enableJs',
        type: 'Boolean',
      },
      {
        title: 'Bật',
        id: 'enabled',
        type: 'Boolean',
      },
      {
        title: 'Cookie',
        id: 'enabledCookieJar',
        type: 'Boolean',
      },
      {
        title: 'URL đơn',
        id: 'singleUrl',
        type: 'Boolean',
      },
      {
        title: 'Số thứ tự sắp xếp',
        id: 'customOrder',
        type: 'Number',
      },
    ],
  },
}
