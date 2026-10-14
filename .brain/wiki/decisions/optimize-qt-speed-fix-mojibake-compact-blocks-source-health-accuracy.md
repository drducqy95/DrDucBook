---
title: Optimize QT Quality & Speed (6 Chapters), Fix Mojibake, Compact Blocks & Source Health Accuracy
type: decision
slug: optimize-qt-speed-fix-mojibake-compact-blocks-source-health-accuracy
category: decisions
created: 2026-10-14T14:23:55
updated: 2026-10-14T15:00:00
status: active
source: /trinity-plan
tags: ["qt", "i18n", "ui", "sourcehealth", "architecture", "xianxia"]
---

# Optimize QT Quality & Speed (6 Chapters), Fix Mojibake, Compact Blocks & Source Health Accuracy

Context: User reported 4 key issues after installing new QT engine: QT translation quality, speed, and mapping latency when adding terms (evaluated across 6 live chapters on LDPlayer for `数据修仙，我在五行观证长生`), Vietnamese Mojibake in NMT model selector, cluttered package download cards, and inconsistent source health report vs actual reading.

Rationale:
1) Core QT Quality & Speed:
   - Eliminate 424 'đích' and 54 'liễu' occurrences across 6 chapters by enabling `LuatNhan.txt` templates and slot rules in `qt2025Runtime`, and stripping sentence-ending aspect particles.
   - Fix '四周' mistranslated as '4 tuần' by prioritizing lexical Trie compounds over `NUMBER_UNIT_TEMPLATES`.
   - Correct hilarious/terrible lexical errors: `放空` (thả lỏng, not chạy xe không), `一块` (cùng nhau/khối, not 1 tệ).
   - Fix mangled Xianxia proper nouns: `庶务殿` (Sở Vụ Điện), `木灵内观经` (Mộc Linh Nội Quan Kinh), `万叶飞花` (Vạn Diệp Phi Hoa), `小聚气丹` (Tiểu Tụ Khí Đan), `许有德` (Hứa Hữu Đức), `陈师兄` (Trần sư huynh), `月儿` (vầng trăng).
   - Invert postposition locatives (`ở phía sau`, `ở bên trong`, `ở bên dưới`) and superlative clauses (`最...`).
   - Switch Trie match to lazy on-demand FMM (3-5x faster, 80% less memory allocation).
   - Localize selection mapping window in Reader to ~500 chars and fast-path direct Trie candidate probe (<50ms).
2) Vietnamese Mojibake: Fix UTF-8 encoding in manifests and add regex Mojibake sanitizer in `HachimiOnnxModelRegistry`.
3) Compact Blocks: Compact `AssetDeliveryItemRow` and `TranslationConfigScreen` to show clean package names without technical hashes/zip file names.
4) Source Health Consistency: Dynamic probe search keyword from home sample or locale, prevent non-HTTP errors from marking sources dead, and fix stage error message leakage.

Alternatives: Eager matching retained with caching (insufficient speed, huge GC churn), manual source health keyword config (unfriendly UX).
Impact: Authentic Xianxia convert tone, ultra-fast QT output and dictionary mapping, clean UI, accurate source health reporting.
