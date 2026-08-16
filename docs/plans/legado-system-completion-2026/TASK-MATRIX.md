# Task matrix

| ID | Phase | Task | Trạng thái baseline | Dependency |
|---|---:|---|---|---|
| C00.01–C00.08 | 00 | Reproduce LDPlayer, picker, runtime, import, translation, mapping | AUTOMATED_DONE / DEVICE_PARTIAL | LDPlayer |
| C01.01–C01.06 | 01 | Router UI, model list, combo, OAuth, secret, local GGUF | AUTOMATED_DONE / DEVICE_PARTIAL | 00 |
| C02.01–C02.06 | 02 | Agent contract, permission, tools, bubble, skills, dashboard | AUTOMATED_DONE / DEVICE_PARTIAL | 01 |
| C03.01–C03.06 | 03 | QT/ML Kit/NMT, revision, mapping, manga | AUTOMATED_DONE / DEVICE_PARTIAL | 00, 01 |
| C04.01–C04.06 | 04 | VBook registry, health, Browser, login, page translation | PARTIAL | 03 cho page translation |
| C05.01–C05.05 | 05 | TTS gateway, import, Piper, catalog, service | PARTIAL | 00 |
| C06.01–C06.05 | 06 | Playback, persistent download, service, audiobook, offline | TODO/PARTIAL | 04 |
| C07.01–C07.06 | 07 | Writing, block editor, renderer, validation, export, lock | PARTIAL | 01, 03 |
| C08.01–C08.06 | 08 | Migration, integration, security, performance, release | PARTIAL | 00–07 |

## Quy tắc task

Mỗi task trong phase file phải có: owner boundary, file/package dự kiến, dữ liệu vào/ra, ví dụ happy/error/cancel, test bắt buộc và điều kiện pass. Không mở task phụ thuộc khi gate phase trước chưa pass.
