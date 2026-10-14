---
title: QT Quality and Speed Optimization Across 10 Chapters
type: decision
slug: qt-quality-and-speed-optimization-across-10-chapters
category: decisions
created: 2026-10-14T11:14:58
updated: 2026-10-14T11:14:58
status: active
source: /trinity-plan
tags: ["quick-translator", "quality", "benchmark", "xianxia"]
---

# QT Quality and Speed Optimization Across 10 Chapters

Context: 10-chapter translation benchmark on real story 'Dữ Liệu Tu Tiên' showed excellent speed (3673 chars/s average, ~250ms/chapter), but revealed 4 core quality issues: lexicon contamination ('Quốc khánh Trung Quốc', '1 tệ', 'phát triển tâm linh', 'Hội An', 'nửa 0 phút tâm'), compound term fracturing ('chip', 'tập hợp khí đan', 'khí đan tiểu tụ', 'công việc vặt điện'), grammar inversions ('Bây giờ của Lạc Ngôn', 'tại ... dưới'), and title formatting. Rationale: Maintain pure Xianxia Hán-Việt convert tone without modern or sociological over-vietnameseization while preserving zero-allocation lookup speed. Alternatives: Full neural MT (too heavy/slow for instant flip reading), LLM post-editing (requires network API/cookies). Impact: Instant high-quality reading experience for chapters 1-10 on LDPlayer.
