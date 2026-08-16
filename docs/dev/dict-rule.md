# Quy tắc Từ điển (Dictionary Rule)

Quy tắc từ điển được sử dụng cho tính năng "Chọn văn bản → Từ điển/Dịch" khi đọc nội dung chương. Sau khi người dùng chọn văn bản, Legado sẽ gọi quy tắc từ điển để truy vấn kết quả và hiển thị trong một cửa sổ bật lên (popup).

[[toc]]

## Mô tả các trường

| Trường | Mô tả |
|------------|-----------------------|
| `urlRule`  | Quy tắc URL yêu cầu, cú pháp tương tự như quy tắc URL của nguồn sách |
| `showRule` | Quy tắc trích xuất nội dung hiển thị từ kết quả phản hồi |

## Ví dụ cấu hình

In `urlRule`, bạn có thể sử dụng `{{key}}` để tham chiếu đến văn bản do người dùng chọn:

```
https://dict.example.com/s?wd={{key}}&ptype=zici
```

`showRule` sử dụng cú pháp quy tắc nguồn tiêu chuẩn để trích xuất nội dung giải nghĩa từ trang web.
