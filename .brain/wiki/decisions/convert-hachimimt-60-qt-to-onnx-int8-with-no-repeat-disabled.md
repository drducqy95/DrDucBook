---
title: Convert HachimiMT-60-QT to ONNX INT8 with No-Repeat Disabled
type: decision
slug: convert-hachimimt-60-qt-to-onnx-int8-with-no-repeat-disabled
category: decisions
created: 2026-09-12T12:12:51
updated: 2026-09-12T12:12:51
status: active
source: /plan
tags: ["nmt", "onnx", "hachimi", "translation", "p34", "auto"]
---

# Convert HachimiMT-60-QT to ONNX INT8 with No-Repeat Disabled

Context: Sino-Vietnamese QuickTranslator register NMT model alongside standard HachimiMT-60. Rationale: Model card explicitly warns against no-repeat ngram because Sino-Vietnamese vocabulary uses natural word repetition. Runtime auto-disables noRepeatNgramSize and isolates translation cache. Artifact hosted on HuggingFace. Alternatives: Single model runtime. Impact: Multi-model runtime in nmt_onnx process, model selector UI.
