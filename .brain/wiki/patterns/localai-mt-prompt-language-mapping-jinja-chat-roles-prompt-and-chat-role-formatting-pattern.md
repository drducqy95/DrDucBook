---
title: LocalAI MT Prompt Language Mapping & Jinja Chat Roles - PROMPT_AND_CHAT_ROLE_FORMATTING Pattern
type: pattern
slug: localai-mt-prompt-language-mapping-jinja-chat-roles-prompt-and-chat-role-formatting-pattern
category: patterns
created: 2026-08-26T19:55:35
updated: 2026-08-26T19:55:35
status: active
source: /debug
tags: ["LocalAI MT Prompt Language Mapping & Jinja Chat Roles", "PROMPT_AND_CHAT_ROLE_FORMATTING", "auto-generated"]
---

# LocalAI MT Prompt Language Mapping & Jinja Chat Roles - PROMPT_AND_CHAT_ROLE_FORMATTING Pattern

> [!WARNING]
> **Origin:** `ERR_0007`
> **Module:** `LocalAI MT Prompt Language Mapping & Jinja Chat Roles` | **Type:** `PROMPT_AND_CHAT_ROLE_FORMATTING`

## 🚨 The Issue

**Message:** 
```text
Test translation returned raw Chinese source text instead of translated Vietnamese
```

**Root Cause:** 
LocalAiTranslationPrompt sent raw ISO code 'vi' in an English prompt instruction without mapping to natural language names or Chinese MT directives (e.g. 'Vietnamese (Tiếng Việt) / 请将以下文本翻译为越南语'). Additionally, system and user messages were lumped into a single USER message rather than distinct SYSTEM and USER messages required for ChatML/Jinja templates

## 🛠️ The Fix

1) Added resolveTargetLanguageName mapping in LocalAiTranslationPrompt; 2) Separated buildSystemPrompt and buildUserPrompt with clear Vietnamese/Sino-Vietnamese directives; 3) Structured AiGenerateRequest with distinct SYSTEM and USER AiMessages in LocalAiTranslationRepository

**Files Affected:**
- `LocalAiTranslationPrompt.kt`
- `LocalAiTranslationRepository.kt`

## 🛡️ Prevention

> [!TIP]
> - Check for similar issues in `LocalAI MT Prompt Language Mapping & Jinja Chat Roles` module
> - Add test case to prevent regression
