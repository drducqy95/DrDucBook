import { defineConfig } from 'vitepress'

export default defineConfig({
  title: 'Đọc sách',
  description: 'Tài liệu hướng dẫn sử dụng Legado-with-MD3',
  base: '/legado-with-MD3/',
  head: [
    ['link', { rel: 'icon', href: '/legado-with-MD3/favicon.ico' }]
  ],
  themeConfig: {
    logo: '/logo.png',
    siteTitle: 'Legado Docs',
    nav: [
      { text: 'Trang chủ', link: '/' },
      { text: 'Bắt đầu nhanh', link: '/guide/' },
      { text: 'Tài liệu phát triển', link: '/dev/' },
    ],
    sidebar: {
      '/guide/': [
        {
          text: 'Hướng dẫn sử dụng',
          items: [
            { text: 'Tài liệu trợ giúp', link: '/guide/' },
            { text: 'Giao diện đọc', link: '/guide/reading' },
            { text: 'Quản lý nguồn nhập', link: '/guide/book-source' },
            { text: 'Quản lý nguồn đăng ký', link: '/guide/rss-source' },
            { text: 'Quy tắc thay thế', link: '/guide/replace-rule' },
          ],
        },
        {
          text: 'Hướng dẫn',
          items: [
            { text: 'Hướng dẫn sao lưu WebDAV', link: '/tutorial/webdav-backup' },
            { text: 'Đồng bộ sách qua WebDAV', link: '/tutorial/webdav-book' },
          ],
        },
      ],
      '/tutorial/': [
        {
          text: 'Hướng dẫn sử dụng',
          items: [
            { text: 'Tài liệu trợ giúp', link: '/guide/' },
            { text: 'Giao diện đọc', link: '/guide/reading' },
            { text: 'Quản lý nguồn nhập', link: '/guide/book-source' },
            { text: 'Quản lý nguồn đăng ký', link: '/guide/rss-source' },
            { text: 'Quy tắc thay thế', link: '/guide/replace-rule' },
          ],
        },
        {
          text: 'Hướng dẫn',
          items: [
            { text: 'Hướng dẫn sao lưu WebDAV', link: '/tutorial/webdav-backup' },
            { text: 'Đồng bộ sách qua WebDAV', link: '/tutorial/webdav-book' },
          ],
        },
      ],
      '/dev/': [
        {
          text: 'Bắt đầu',
          items: [
            { text: 'Chi tiết cú pháp quy tắc', link: '/dev/syntax' },
            { text: 'Chi tiết tham số URL', link: '/dev/url-options' },
            { text: 'Tra nhanh các trường trong nguồn', link: '/dev/source-fields' },
            { text: 'Các ví dụ về nguồn', link: '/dev/examples' },
          ],
        },
        {
          text: 'Tham khảo',
          items: [
            { text: 'Hướng dẫn quy tắc nguồn', link: '/dev/rule' },
            { text: 'Biến và hàm JS', link: '/dev/js' },
            { text: 'Biểu thức đường dẫn XPath', link: '/dev/xpath' },
            { text: 'Biểu thức chính quy', link: '/dev/regex' },
          ],
        },
        {
          text: 'Quy chuẩn cấu hình',
          items: [
            { text: 'Cấu hình Request Headers', link: '/dev/request-headers' },
            { text: 'Xác thực và đăng nhập', link: '/dev/authentication' },
            { text: 'Cấu hình URL khám phá', link: '/dev/discovery-url' },
            { text: 'Cấu hình mô-đun trang chủ', link: '/spec/homepage-modules' },
            { text: 'Cấu hình sách liên quan', link: '/spec/related-books' },
          ],
        },
        {
          text: 'Tính năng mở rộng',
          items: [
            { text: 'Gỡ lỗi nguồn sách', link: '/dev/debug' },
            { text: 'Quy tắc từ điển', link: '/dev/dict-rule' },
            { text: 'Quy tắc đọc trực tuyến', link: '/dev/tts-rule' },
            { text: 'Regex mục lục TXT', link: '/dev/txt-toc' },
            { text: 'Tham khảo kiểu MIME', link: '/spec/mime-types' },
          ],
        },
      ],
      '/spec/': [
        {
          text: 'Bắt đầu',
          items: [
            { text: 'Chi tiết cú pháp quy tắc', link: '/dev/syntax' },
            { text: 'Chi tiết tham số URL', link: '/dev/url-options' },
            { text: 'Tra nhanh các trường trong nguồn', link: '/dev/source-fields' },
            { text: 'Các ví dụ về nguồn', link: '/dev/examples' },
          ],
        },
        {
          text: 'Tham khảo',
          items: [
            { text: 'Hướng dẫn quy tắc nguồn', link: '/dev/rule' },
            { text: 'Biến và hàm JS', link: '/dev/js' },
            { text: 'Biểu thức đường dẫn XPath', link: '/dev/xpath' },
            { text: 'Biểu thức chính quy', link: '/dev/regex' },
          ],
        },
        {
          text: 'Quy chuẩn cấu hình',
          items: [
            { text: 'Cấu hình Request Headers', link: '/dev/request-headers' },
            { text: 'Xác thực và đăng nhập', link: '/dev/authentication' },
            { text: 'Cấu hình URL khám phá', link: '/dev/discovery-url' },
            { text: 'Cấu hình mô-đun trang chủ', link: '/spec/homepage-modules' },
            { text: 'Cấu hình sách liên quan', link: '/spec/related-books' },
          ],
        },
        {
          text: 'Tính năng mở rộng',
          items: [
            { text: 'Gỡ lỗi nguồn sách', link: '/dev/debug' },
            { text: 'Quy tắc từ điển', link: '/dev/dict-rule' },
            { text: 'Quy tắc đọc trực tuyến', link: '/dev/tts-rule' },
            { text: 'Regex mục lục TXT', link: '/dev/txt-toc' },
            { text: 'Tham khảo kiểu MIME', link: '/spec/mime-types' },
          ],
        },
      ],
    },
    socialLinks: [
      { icon: 'github', link: 'https://github.com/HapeLee/legado-with-MD3' },
    ],
    footer: {
      message: 'Phát hành theo giấy phép Apache-2.0',
      copyright: 'Copyright © 2026 Legado',
    },
    search: {
      provider: 'local',
    },
    outline: {
      level: [2, 3],
      label: 'Mục lục trang này',
    },
    docFooter: {
      prev: 'Trang trước',
      next: 'Trang sau',
    },
    lastUpdated: {
      text: 'Cập nhật lần cuối vào',
    },
    editLink: {
      pattern: 'https://github.com/HapeLee/legado-with-MD3/edit/main/docs/:path',
      text: 'Chỉnh sửa trang này trên GitHub',
    },
  },
  lastUpdated: true,
  markdown: {
    lineNumbers: true,
  },
})
