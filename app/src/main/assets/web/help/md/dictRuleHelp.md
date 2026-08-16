# Quy tắc từ điển QT

Từ điển QT dùng trong Quick Translator và cũng có thể hỗ trợ pipeline AI/NMT ở cấp Project.

## Loại từ điển

- Name: tên riêng, nhân vật, địa danh, tổ chức.
- Vietphrase: cụm từ, thuật ngữ, thành ngữ.
- Phiên âm: phiên âm ký tự lẻ khi không ghép được cụm nào.
- Pronoun: đại từ, xưng hô và quan hệ nhân vật.
- Luật Nhân: luật xử lý ngữ cảnh đặc biệt.
- Ignore: cụm cần xóa trước khi dịch.

## Phạm vi áp dụng

Thứ tự ưu tiên:

1. Project: chỉ áp dụng cho truyện đang chọn.
2. Universe: áp dụng cho nhóm/ngữ cảnh dùng chung.
3. Toàn cục: áp dụng cho mọi truyện.
4. Từ điển tích hợp.

Khi nhập từ điển cấp Project, chọn phạm vi Project rồi chọn truyện trong dropdown. App sẽ lưu theo khóa truyện để chỉ áp dụng cho truyện đó.

## Định dạng nhập nhanh

Mỗi dòng là một mục. Có thể dùng một trong các dấu phân cách:

```text
raw = bản dịch
raw => bản dịch
raw | bản dịch
raw : bản dịch
```

Với Ignore, chỉ cần nhập cụm cần xóa:

```text
cum can xoa
```

Với Phiên âm, nên dùng cho một ký tự hoặc một đơn vị rất ngắn để fallback không làm sai cụm dài.

## Lưu ý

- Project có ưu tiên cao nhất, phù hợp sửa tên riêng riêng cho một truyện.
- Ignore sẽ xóa cụm trước khi dịch, không phải giữ nguyên cụm.
- Phiên âm chỉ dùng khi ký tự lẻ không ghép được vào cụm Name/Vietphrase/Pronoun/Luật Nhân.
- AI Provider và NMT có thể đọc từ điển Project để đưa vào prompt hoặc tiền xử lý.

