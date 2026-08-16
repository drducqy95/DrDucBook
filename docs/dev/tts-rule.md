# Quy tắc Đọc trực tuyến TTS (Online TTS Rule)

Quy tắc đọc trực tuyến TTS được sử dụng để kết nối với dịch vụ TTS (Text-to-Speech / Văn bản thành giọng nói) của bên thứ ba. Định dạng quy tắc là quy tắc URL, cú pháp tương tự như quy tắc URL của nguồn sách.

[[toc]]

## Tham số JS

Các biến JS sau đây có thể được sử dụng trong quy tắc:

| Tham số | Mô tả |
|--------------|--------------|
| `speakText`  | Nội dung văn bản cần đọc |
| `speakSpeed` | Tốc độ đọc, phạm vi từ 5 đến 50 |

## Ví dụ

Ví dụ giao diện TTS trực tuyến:

```
http://tts.example.com/text2audio,{
    "method": "POST",
    "body": "text={{java.encodeURI(speakText)}}&speed={{speakSpeed}}&lang=zh"
}
```

::: tip Giải thích
- Âm thanh phản hồi từ yêu cầu sẽ tự động được phát.
- Giá trị `speakSpeed` càng lớn thì tốc độ đọc càng nhanh, bạn cần ánh xạ tương ứng dựa theo phạm vi tham số của dịch vụ TTS cụ thể.
:::
