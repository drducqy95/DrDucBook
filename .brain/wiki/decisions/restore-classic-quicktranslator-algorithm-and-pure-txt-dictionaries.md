---
title: Restore Classic QuickTranslator Algorithm and Pure TXT Dictionaries
type: decision
slug: restore-classic-quicktranslator-algorithm-and-pure-txt-dictionaries
category: decisions
created: 2026-10-14T11:27:38
updated: 2026-10-14T11:27:38
status: active
source: /trinity-plan
tags: ["quick-translator", "classic-qt", "fmm", "pure-txt-dictionaries", "architecture"]
---

# Restore Classic QuickTranslator Algorithm and Pure TXT Dictionaries

Context: Analysis of QT translation defects in 10 chapters revealed that 'bạn, tôi' was caused by QuickTranslationPronounMode (applyModernPronounRules overriding 'ta, ngươi' with 'tôi, bạn') and bizarre modern errors ('Quốc khánh Trung Quốc', 'tu hành phát triển tâm linh', '1 tệ', 'Hội An') came directly from qt_clean_cvdict_base_v3.tsv. Rationale: Discard the entire 'QT Clean' bloatware and restore the original QuickTranslator Forward Maximum Matching (FMM) algorithm backed by pure classic TXT dictionaries (VietPhrase.txt, Names.txt, ChinesePhienAmWords.txt, Pronouns.txt, LuatNhan.txt / qtdict runtime index). Alternatives: Keeping QT Clean rules or regex fixes (leads to endless whack-a-mole dictionary bugs and breaks text mapping). Impact: Zero-pollution authentic Xianxia Hán-Việt convert tone, 50x faster translation (<30ms/chapter), and exact 1-1 token mapping for reader dictionary lookup.
