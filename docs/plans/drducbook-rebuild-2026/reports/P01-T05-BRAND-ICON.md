# P01.T05 - Thuong hieu va icon DrDucBook

## Ket qua

Trang thai: `DONE`

- Ten app, debug label, share label, Web Service tile, welcome/about text va web UI duoc doi sang `DrDucBook`.
- Cac chu `Legado` con lai chi mo ta du lieu/nguon tuong thich, import legacy, attribution hoac package contract.
- Tat ca launcher activities/aliases dung `@mipmap/ic_launcher`; round/adaptive/monochrome icon dung cung bo DrDucBook.
- Icon vector cu khong con tham chieu da duoc go.
- Favicon va title web duoc build/sync vao Android assets.

## Image pipeline

Anh nguon nguoi dung duoc chinh bang image editing de bo Gemini marker/watermark. Vung den/checkerboard ngoai khung duoc chuyen thanh alpha that; foreground 432 px dat tai `drawable-xxxhdpi` de tuong ung 108 dp va khong bi cat trong welcome/about.

Output chinh:

- `branding/drducbook-icon-master.png`: RGBA 1024x1024.
- `drawable-xxxhdpi/drducbook_icon_foreground.png` va monochrome variant.
- Mipmap 48/72/96/144/192 px, round variants, favicon ICO.
- `artifacts/phase01/icon-qa.png` va device screenshots.

Master SHA-256: `23C3EB94C2B6D9BD2C625BF6085E5327C88D30A3ADFB810F08FD56D34527C64C`.

## Verification

- `scripts/branding/verify-icons.py`: 14/14 assets PASS; khong EXIF/XMP/C2PA/comment; bon goc master/launcher alpha 0.
- `BrandIdentityTest`: label va 9 launcher components PASS.
- Vue type-check/build/sync PASS, 1633 modules transformed.
- AAPT release: label `DrDucBook`, moi launcher alias tro cung adaptive icon.
- Visual QA: `device-app-details.png` hien dung icon trong suot; `device-welcome-fixed.png` hien icon tron ven, khong bi cat.
