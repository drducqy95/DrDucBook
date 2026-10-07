---
title: ML Kit Dynamic Dictionary & Pure Grammar Architecture
type: decision
slug: ml-kit-dynamic-dictionary-pure-grammar-architecture
category: decisions
created: 2026-10-07T07:22:05
updated: 2026-10-07T07:22:05
status: active
source: /plan
tags: ["mlkit", "quickdict", "grammar", "architecture", "auto"]
---

# ML Kit Dynamic Dictionary & Pure Grammar Architecture

Context: ML Kit translation previously relied on hardcoded string lists (KNOWN_PIVOT_REPLACEMENTS) which do not scale across different chapters and novels. Rationale: Shift ML Kit to a dynamic pipeline that automatically queries QuickTranslator Trie packs (Names, VietPhrase), Room QuickDictionary, and Story Memory (BookStoryMemory) for entities and terminology, using pre-translation masking and post-translation alignment. Isolate post-processing purely to generic Vietnamese grammar and pronoun rules without content hardcoding. Alternatives: Hardcoding hundreds of phrases per novel was rejected as unmaintainable. Impact: Every novel automatically benefits from QT dictionaries and translation memory with zero custom hardcoding.
