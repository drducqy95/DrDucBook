<script setup lang="ts">
import { Link } from '@element-plus/icons-vue'
</script>
<template>
  <el-link :icon="Link" href="/help/#appHelp" target="_blank"
    >Tài liệu trợ giúp app</el-link
  ><br />
  <el-link :icon="Link" href="/help/#ruleHelp" target="_blank"
    >Hướng dẫn tạo nguồn sách</el-link
  ><br />
  <el-link :icon="Link" href="/help/#jsHelp" target="_blank"
    >Biến và hàm JS</el-link
  ><br />
  <el-link :icon="Link" href="/help/#xpathHelp" target="_blank"
    >Hướng dẫn cú pháp XPath</el-link
  ><br />
  <el-link :icon="Link" href="/help/#regexHelp" target="_blank"
    >Hướng dẫn biểu thức chính quy</el-link
  ><br />
  <el-link :icon="Link" href="/help/#txtTocRuleHelp" target="_blank"
    >Giải thích regex mục lục TXT</el-link
  ><br />
  <el-link :icon="Link" href="/help/#debugHelp" target="_blank"
    >Hướng dẫn gỡ lỗi nguồn sách</el-link
  ><br />
  <el-link :icon="Link" href="/help/#httpTTSHelp" target="_blank"
    >Quy tắc đọc thành tiếng online</el-link
  ><br />
  <el-link :icon="Link" href="/help/#webDavBookHelp" target="_blank">
    Hướng dẫn nhanh sách WebDAV</el-link
  ><br />
  <el-link :icon="Link" href="/help/#webDavHelp" target="_blank">
    Hướng dẫn sao lưu WebDAV</el-link
  ><br />
  <el-link :icon="Link" href="https://regexr-cn.com/" target="_blank"
    >Công cụ kiểm tra regex online</el-link
  ><br />
  <div style="margin-top: 20px">
    <span
      ><el-text
        ><code>^$()[]{}.?+*|</code> đây là ký tự đặc biệt của regex Java, cần escape khi khớp</el-text
      ></span
    ><br />
    <span
      ><el-text><code>(?s)</code> tiền tố dùng để phân tích qua nhiều dòng</el-text></span
    ><br />
    <span
      ><el-text><code>(?m)</code> tiền tố dùng để khớp từng dòng</el-text></span
    ><br />
    <span
      ><el-text><code>(?i)</code> tiền tố dùng để bỏ qua hoa/thường</el-text></span
    ><br />
  </div>
</template>

<style lang="scss" scoped>
.el-link {
  padding: 4px;
}
.el-text {
  padding-top: 20px;
}
</style>
