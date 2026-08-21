# Plan: WebService Enhancement (P09)

Created: 2026-08-21T00:08:00+07:00
Updated: 2026-08-21T22:48:00+07:00
Status: 🟡 In Progress

## Overview

Nâng cấp WebService với các nhóm tính năng: Discovery single-source, Translation Search fix, TTS voice selection, UI translation completion, Translation Memory + AI Pipeline với per-provider permanent cache, Translation Dashboard, Provider Cache Isolation, Story Memory Series Toggle, và AI Rewrite Prompt Enhancement.

## Tech Stack
- Backend: Kotlin + Ktor embedded server + Room DB
- Frontend: Vue 3 + TypeScript + Element Plus + Vite
- Translation Engine: TranslateChapterUseCase → TranslationManager (Koin singleton)
- AI Pipeline: AiTranslationRefinePipeline (Stage 2→4 context pack + JSON refiner)

## Phases

| Phase | Name | Status | Progress |
|-------|------|--------|----------|
| 01 | UI Translation Completion | ✅ Complete | 100% |
| 02 | Discovery Single-Source Dropdown | ✅ Complete | 100% |
| 03 | Translation Search Fix | ✅ Complete | 100% |
| 04 | TTS Voice Selection | ✅ Complete | 100% |
| 05 | Per-Provider Cache & Pipeline | ✅ Complete | 100% |
| 06 | Translation Dashboard & Memory API | ✅ Complete | 100% |
| 07 | Provider Cache Isolation Fix | ✅ Complete | 100% |
| 08 | Story Memory Series Toggle | ✅ Complete | 100% |
| 09 | AI Rewrite Prompt Enhancement | ✅ Complete | 100% |

## Quick Commands
- Check progress: `/next`
- Save context: `/save-brain`
