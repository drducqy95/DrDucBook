# Biểu thức Chính quy (Regex)

Biểu thức chính quy (Regular Expression) được sử dụng để khớp, tìm kiếm và thay thế các mẫu cụ thể trong văn bản. Trong quy tắc nguồn sách Legado, các quy tắc bắt đầu bằng dấu `:` chính là biểu thức chính quy, có thể được dùng để trích xuất danh sách sách và danh sách mục lục.

[[toc]]

## Khớp cơ bản

Biểu thức chính quy là mẫu mà chúng ta sử dụng để tìm kiếm các chữ cái và chữ số trong văn bản. Ví dụ, biểu thức chính quy `cat` biểu thị: chữ cái `c` theo sau bởi chữ cái `a`, và tiếp tục theo sau bởi chữ cái `t`.

Biểu thức chính quy thường phân biệt chữ hoa - chữ thường, do đó `Cat` sẽ không khớp với chuỗi "cat".

## Ký tự đại diện (Metacharacters)

Ký tự đại diện (metacharacters) là các phần tử xây dựng cơ bản của biểu thức chính quy.

| Ký tự | Mô tả |
|:-------:|-----------------------|
| `.` | Khớp với bất kỳ ký tự nào ngoại trừ ký tự xuống dòng |
| `[ ]` | Lớp ký tự, khớp với bất kỳ ký tự nào nằm trong dấu ngoặc vuông |
| `[^ ]` | Lớp ký tự phủ định, khớp với bất kỳ ký tự nào KHÔNG nằm trong dấu ngoặc vuông |
| `*` | Khớp với biểu thức con phía trước 0 hoặc nhiều lần |
| `+` | Khớp với biểu thức con phía trước 1 hoặc nhiều lần |
| `?` | Khớp với biểu thức con phía trước 0 hoặc 1 lần |
| `{n,m}` | Khớp với ký tự phía trước ít nhất n lần nhưng không quá m lần |
| `(xyz)` | Nhóm ký tự, khớp với chuỗi ký tự xyz theo đúng thứ tự chính xác |
| `\|` | Cấu trúc rẽ nhánh (hoặc), khớp với ký tự phía trước hoặc phía sau ký hiệu |
| `\` | Ký tự thoát (Escape character) |
| `^` | Khớp với vị trí đầu dòng |
| `$` | Khớp với vị trí cuối dòng |

### Dấu chấm

`.` có thể khớp với bất kỳ ký tự đơn nào, nhưng không khớp với ký tự xuống dòng. Ví dụ, `.ar` khớp với "car", "par", "gar".

### Tập hợp ký tự

Tập hợp ký tự (lớp ký tự) được chỉ định bằng cách sử dụng dấu ngoặc vuông. Ví dụ, `[Tt]he` khớp với "The" hoặc "the".

**Tập hợp ký tự phủ định**: Khi `^` xuất hiện bên trong dấu ngoặc vuông, nó sẽ phủ định tập hợp ký tự đó. Ví dụ, `[^c]ar` khớp với "par", "gar" nhưng loại trừ "car".

### Lặp lại

- **Dấu sao `*`**: Khớp với quy tắc phía trước từ 0 lần trở lên. Ví dụ, `[a-z]*` khớp với bất kỳ số lượng chữ cái viết thường nào trên một dòng.
- **Dấu cộng `+`**: Khớp với ký tự phía trước từ 1 lần trở lên. Ví dụ, `c.+t` khớp với "cat sat on the mat".
- **Dấu hỏi `?`**: Biểu thị ký tự đứng trước nó là tùy chọn (0 hoặc 1 lần). Ví dụ, `[T]?he` khớp với cả "The" và "he".

### Dấu ngoặc nhọn (Quantifiers)

Dùng để chỉ định số lần một ký tự hoặc nhóm ký tự có thể lặp lại. Ví dụ, `[0-9]{2,3}` khớp với số có ít nhất 2 chữ số nhưng không quá 3 chữ số.

### Nhóm ký tự

Các mẫu con viết bên trong dấu ngoặc đơn `(...)`. Ví dụ, `(ab)*` khớp với 0 hoặc nhiều cụm "ab". `(c|g|p)ar` khớp với "car", "gar" hoặc "par".

### Cấu trúc rẽ nhánh

`|` dùng để định nghĩa cấu trúc rẽ nhánh. Tập hợp ký tự chỉ có tác dụng ở cấp độ ký tự đơn, trong khi cấu trúc rẽ nhánh có thể hoạt động ở cấp độ biểu thức.

### Thoát ký tự đặc biệt

Sử dụng `\` để thoát ký tự tiếp theo. Ví dụ, `(f|c|m)at\.?` khớp với "fat", "cat", "mat" và ký tự "." tùy chọn ở cuối.

### Ký tự neo (Anchors)

- `^` Kiểm tra xem ký tự khớp có phải là ký tự bắt đầu hay không
- `$` Kiểm tra xem ký tự khớp có phải là ký tự cuối cùng hay không

## Tập hợp ký tự viết tắt

| Viết tắt | Mô tả |
|:----:|-----------------------------|
| `.` | Khớp với bất kỳ ký tự nào ngoại trừ ký tự xuống dòng |
| `\w` | Khớp với tất cả các ký tự chữ và số cùng dấu gạch dưới: `[a-zA-Z0-9_]` |
| `\W` | Khớp với ký tự không phải chữ và số: `[^\w]` |
| `\d` | Khớp với chữ số: `[0-9]` |
| `\D` | Khớp với ký tự không phải chữ số: `[^\d]` |
| `\s` | Khớp với các ký tự khoảng trắng: `[\t\n\f\r\p{Z}]` |
| `\S` | Khớp với ký tự không phải khoảng trắng: `[^\s]` |

## Khẳng định / Khớp trước sau (Lookaround assertions)

| Ký hiệu | Mô tả |
|:-----:|--------|
| `?=` | Positive Lookahead (Khẳng định nhìn về phía trước dương tính) |
| `?!` | Negative Lookahead (Khẳng định nhìn về phía trước âm tính) |
| `?<=` | Positive Lookbehind (Khẳng định nhìn về phía sau dương tính) |
| `?<!` | Negative Lookbehind (Khẳng định nhìn về phía sau âm tính) |

**Positive Lookahead (Khẳng định nhìn về phía trước)**: Khớp nội dung đứng trước một mẫu nhất định. Ví dụ, `(T|t)he(?=\sfat)` khớp với "The" hoặc "the" chỉ khi theo sau nó là một khoảng trắng và chữ "fat".

**Negative Lookahead (Khẳng định nhìn về phía trước phủ định)**: Khớp nội dung không đứng trước một mẫu nhất định. Ví dụ, `(T|t)he(?!\sfat)` khớp với "The" hoặc "the" chỉ khi theo sau nó KHÔNG phải là khoảng trắng và chữ "fat".

**Positive Lookbehind (Khẳng định nhìn về phía sau)**: Khớp nội dung đứng sau một mẫu nhất định. Ví dụ, `(?<=(T|t)he\s)(fat|mat)` khớp với "fat" và "mat" chỉ khi đứng trước nó là "The" hoặc "the" và một khoảng trắng.

**Negative Lookbehind (Khẳng định nhìn về phía sau phủ định)**: Khớp nội dung không đứng sau một mẫu nhất định.

## Cờ tùy chọn (Flags)

| Cờ | Mô tả |
|:---:|--------|
| `i` | Không phân biệt chữ hoa/thường (Case-insensitive) |
| `g` | Tìm kiếm toàn cục (Global) |
| `m` | Khớp nhiều dòng (Multiline) |

## Các biểu thức chính quy thông dụng

| Mục đích | Biểu thức chính quy |
|---------|------------------------------|
| Chữ số | `\d+$` |
| Tên người dùng | `^[\w\d_.]{4,16}$` |
| Ký tự chữ và số | `^[a-zA-Z0-9]*$` |
| Chữ cái viết thường | `[a-z]+$` |
| Chữ cái viết hoa | `[A-Z]+$` |
| Thẻ HTML | `<[^>]+?>` |
| Lời kêu gọi cập nhật/chuyển tiếp/cảm ơn | `[\(（【].*?[求更谢乐发推].*?[】）\)]` |
| Tìm chương mới nhất | `您可以.*?查找最新章节` |
| PS/ps (tái bút) | `(?i)ps\b.*` |
