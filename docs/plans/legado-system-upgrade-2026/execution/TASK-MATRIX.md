# Task Matrix — Tổng hợp task toàn dự án

Ngày tạo: 24/07/2026

## Thống kê tổng quan

| Metric | Giá trị |
|---|:---:|
| Tổng số task | **68** |
| Files mới cần tạo | **~63** |
| Files cần sửa | **~42** |
| Unit tests mới | **~85 test cases** |
| Integration tests | **5 suites** |
| Security tests | **3 suites** |
| LDPlayer smoke scenarios | **~55 kịch bản** |

## Ma trận task theo Wave

### Wave 1 — Foundation (P01 + P05)

| ID | Task | Status | Priority | Files mới | Files sửa |
|---|---|:---:|:---:|:---:|:---:|
| P01.T01 | SearchableModelPicker | TODO | HIGH | 1 | 0 |
| P01.T02 | Tích hợp model picker | TODO | HIGH | 0 | 1 |
| P01.T03 | Home/Cá nhân shortcuts | TODO | HIGH | 0 | 2 |
| P01.T04 | Dashboard health & family | PARTIAL | HIGH | 0 | 1 |
| P01.T05 | Provider config workflow | PARTIAL | HIGH | 0 | 1 |
| P01.T06 | Credential security | VERIFY | CRITICAL | 0 | 2 |
| P01.T07 | OAuth repair | VERIFY | HIGH | 0 | 1 |
| P01.T08 | Local provider entry | TODO | MEDIUM | 0 | 1 |
| P01.T09 | AI Settings cleanup | TODO | LOW | 0 | 1 |
| P01.T10 | Route redirect | VERIFY | LOW | 0 | 1 |
| P05.T01 | Domain gateway/repo | DONE | HIGH | 2 | 0 |
| P05.T02 | Test use case | DONE | HIGH | 1 | 0 |
| P05.T03 | TTS Settings screen | PARTIAL | CRITICAL | 3 | 0 |
| P05.T04 | Model Manager screen | DONE | HIGH | 3 | 0 |
| P05.T05 | Model guide sheet | DONE | MEDIUM | 1 | 0 |
| P05.T06 | Navigation & DI | DONE | HIGH | 0 | 3 |
| P05.T07 | Import security | PARTIAL | HIGH | 0 | 1 |
| P05.T08 | Service integration | PARTIAL | HIGH | 0 | 1 |

### Wave 2 — AI + Translation (P02 + P03 partial)

| ID | Task | Status | Priority | Files mới | Files sửa |
|---|---|:---:|:---:|:---:|:---:|
| P02.T01 | Agent domain models | TODO | CRITICAL | 1 | 0 |
| P02.T02 | Permission broker | TODO | CRITICAL | 1 | 0 |
| P02.T03 | Agent orchestration loop | PARTIAL | CRITICAL | 0 | 1 |
| P02.T04 | Execute approved action | TODO | HIGH | 1 | 0 |
| P02.T05 | Tool registry | PARTIAL | HIGH | 1 | 1 |
| P02.T06 | Chat bubble panel | TODO | HIGH | 1 | 2 |
| P02.T07 | Context bridge | PARTIAL | HIGH | 0 | 1 |
| P02.T08 | Skill/plugin validation | TODO | MEDIUM | 1 | 1 |
| P02.T09 | Agent Dashboard | PARTIAL | MEDIUM | 0 | 3 |
| P02.T10 | Base Activity hooks | TODO | MEDIUM | 0 | 3 |
| P03.T01 | LocalAI runtime | PARTIAL | HIGH | 0 | 3 |
| P03.T02 | ML Kit prerequisite | TODO | HIGH | 0 | 2 |
| P03.T03 | Translation revision model | TODO | CRITICAL | 1 | 0 |
| P03.T04 | Cache revision integration | TODO | HIGH | 0 | 3 |
| P03.T05 | Revision UI | TODO | HIGH | 3 | 0 |
| P03.T06 | Reader revision precedence | TODO | HIGH | 0 | 1 |

### Wave 3 — Content Pipeline (P03 manga + P04 + P06)

| ID | Task | Status | Priority | Files mới | Files sửa |
|---|---|:---:|:---:|:---:|:---:|
| P03.T07 | Manga pipeline domain | TODO | HIGH | 1 | 0 |
| P03.T08 | Manga OCR + use case | TODO | HIGH | 3 | 0 |
| P03.T09 | Manga overlay | TODO | HIGH | 2 | 0 |
| P03.T10 | Manga export | TODO | MEDIUM | 0 | 1 |
| P04.T01 | VBook import models/use case | TODO | HIGH | 2 | 0 |
| P04.T02 | VBook import UI | TODO | HIGH | 3 | 0 |
| P04.T03 | Source health entity/DAO | TODO | CRITICAL | 2 | 0 |
| P04.T04 | Probe use case | TODO | HIGH | 1 | 0 |
| P04.T05 | Source health worker | TODO | HIGH | 1 | 0 |
| P04.T06 | Source health UI | TODO | HIGH | 3 | 0 |
| P04.T07 | Browser Compose arch | TODO | HIGH | 4 | 0 |
| P04.T08 | Browser features | TODO | HIGH | 0 | 0* |
| P04.T09 | Login/captcha/cookie | TODO | HIGH | 0 | 1 |
| P04.T10 | Page translation | TODO | MEDIUM | 1 | 0 |
| P06.T01 | Unified playback service | DONE | CRITICAL | 1 | 0 |
| P06.T02 | Player ViewModel refactor | DONE | HIGH | 0 | 1 |
| P06.T03 | Player screen completion | DONE | HIGH | 0 | 1 |
| P06.T04 | Download domain | DONE | HIGH | 6 | 0 |
| P06.T05 | Download service | DONE | HIGH | 1 | 0 |
| P06.T06 | Download UI | DONE | HIGH | 3 | 0 |
| P06.T07 | Audiobook import | DONE | MEDIUM | 5 | 0 |
| P06.T08 | Offline playback | DONE | MEDIUM | 0 | 1 |

### Wave 4 — Creation Tools (P07)

| ID | Task | Status | Priority | Files mới | Files sửa |
|---|---|:---:|:---:|:---:|:---:|
| P07.T01 | Pre-writing model/workspace | DONE | HIGH | 2 | 0 |
| P07.T02 | Clone sách | DONE | HIGH | 1 | 0 |
| P07.T03 | Block document model | DONE | CRITICAL | 3 | 0 |
| P07.T04 | Ebook Editor integration | DONE | HIGH | 0 | 3 |
| P07.T05 | Block canvas | DONE | HIGH | 1 | 0 |
| P07.T06 | Layer panel | DONE | HIGH | 1 | 0 |
| P07.T07 | Chapter manager | DONE | HIGH | 1 | 0 |
| P07.T08 | Preview screen | DONE | HIGH | 1 | 0 |
| P07.T09 | Shared layout renderer | DONE | HIGH | 1 | 0 |
| P07.T10 | Validate use case | DONE | HIGH | 1 | 0 |
| P07.T11 | Export enhancement | DONE | HIGH | 0 | 1 |
| P07.T12 | Drop cap unification | DONE | HIGH | 0 | 2 |

### Wave 5 — Integration & Release (P08)

| ID | Task | Status | Priority | Files mới | Files sửa |
|---|---|:---:|:---:|:---:|:---:|
| P08.T01 | Room migration chain | DONE | CRITICAL | 0 | 2 |
| P08.T02 | Golden fixture pack | DONE | HIGH | 2 | 0 |
| P08.T03 | Migration tests | DONE | HIGH | 0 | 1 |
| P08.T04 | Integration tests | DONE | HIGH | 5 | 0 |
| P08.T05 | Security tests | DONE | HIGH | 3 | 0 |
| P08.T06 | Feature flags | DONE | HIGH | 1 | 0 |
| P08.T07 | Documentation | DONE | MEDIUM | 7 | 0 |
| P08.T08 | Performance benchmark | DONE | MEDIUM | 0 | 0 |
| P08.T09 | Release stages | DONE | HIGH | 0 | 0 |
| P08.T10 | State files update | DONE | LOW | 0 | 2 |

## Dependency graph

```
P01 ─┬── P02 ── P04 (agent context)
     ├── P03 ── P04 (dịch trang Browser)
     │    └── P07 (clone variant)
     └── P07 (AI suggestion)

P04 ── P06 (VBook media)

P05 (độc lập)

P01..P07 ── P08 (integration)
```
