# P11.T08 - AI translation pipeline rewrite validation

Status: IN_PROGRESS - code/test checkpoint PASS, live AI device gate pending

## Muc tieu

Viet lai/kiem dinh lan cuoi pipeline dich AI truoc khi dong release: bo cac duong runtime con dung pipeline cu, doi chieu voi engine tham khao ben ngoai va khoa pipeline moi bang test + device evidence.

Reference can doc khi bat dau task:

`D:\Dev\Projects\legado-qt-main\legado-qt-main\Translator Engine\Translator Engine`

## Pham vi

- Android translation domain/model/use-case/prompt catalog.
- WebService auto-translation job wiring neu con dung contract cu.
- Translation cache/migration neu noi dung cu co marker hoac schema cu.
- Unit/integration tests cho schema, provider fallback, cache va live chapter smoke.

## Checklist task

- [x] Kiem ke pipeline cu con sot: `[result]`, `[dictionary]`, paragraph marker, regex parser, legacy fallback ghi de output.
- [x] Doc reference Translator Engine va lap mapping cu -> moi.
- [x] Chot contract moi: context pack, raw segment id, QT draft, locked dictionary, JSON schema, QC/repair.
- [x] Migrate runtime app sang contract moi; WebService khong co job dich rieng trong `app/web`, toggle web chi lien quan chinh sach/setting va runtime doc sach dung `TranslateChapterUseCase`.
- [x] Xoa hoac khoa cac fallback cu trong production refiner path sau khi regression pass.
- [x] Them cache invalidation/force retranslate cho noi dung da dich bang pipeline cu neu can.
- [x] Chay focused tests va compile.
- [ ] Dich thu mot chuong that tren LDPlayer bang model that, log model/token an toan va screenshot/output evidence.

## Reference da doc

- `Pipeline_Schema_Report.md`
- `Script/stage2_context_pack.py`
- `Script/stage3_ai_refiner.py`
- `Script/stage4_post_process.py`
- `Script/qc_checker.py`

Mapping chinh:

- Stage 2 Android tao `context_pack` co `translation_config`, `current_chapter`, `story_timeline`, `locked_dictionary`, `suggested_dictionary`, `relationships_graph`, `pronouns_addressing`, `translation_memory_hits`, va `raw_segments`.
- Moi raw segment co id on dinh, source RAW va ban nhap QT.
- Stage 3 refiner phai tra JSON duy nhat voi `refined_segments`, `story_timeline`, `new_entities`, `relationships`, `grammar_notes`.
- Stage 4/QC chap nhan theo id segment, cap nhat tu dien tu `new_entities`, va reject output thieu id/sai id/con CJK khi dich sang tieng Viet.

## Thay doi checkpoint 2026-08-01 15:57

- `AiTranslationRefinePipeline.kt`: bo fallback doc output cu; `parseRefinerOutput` chi nhan JSON object hop le, `preview` cung di qua parser JSON moi.
- `AiTranslationChunkPipeline.kt`: xoa `AiTranslationStreamParser` kieu `[result]`/`[dictionary]` vi khong con duong production dung contract cu.
- `TranslateChapterUseCase.kt`: bo nhanh boc `[result]` trong finalizer, giu lai unwrap Markdown fence va JSON envelope cua provider.
- `TranslateChapterAiRetryTest.kt`: doi fake AI output sang JSON `refined_segments`; them test output cu `[result]` bi retry truoc khi ghi cache; entity character hoc vao tu dien kieu `NAME`.
- `AiTranslationRefinePipelineTest.kt`: them/giu regression reject `[result]` + `[dictionary]`.
- `AiTranslationChunkPipelineTest.kt`: bo test parser cu, chi giu chunk planner/token budget/stream accumulator.
- `TranslateChapterFinalizeOutputTest.kt`: doi test layout sang marker/prefix trung tinh, khong mo ta `[result]` la format hop le nua.

## Thay doi checkpoint 2026-08-01 16:06

- `TranslateChapterUseCase.kt`: them `containsLegacyAiTranslationContract()` de cache APP_AI co section `[result]`, `[/result]` hoac `[dictionary]` bi xem la khong hop le va duoc dich lai.
- `TranslateChapterAiRetryTest.kt`: doi regression cache chunk sang case cache cu `[result]`/`[dictionary]`, xac nhan app khong dung lai chunk cu va ghi chunk moi theo JSON refiner.

## Lenh kiem tra

- `rg -n "AiTranslationStreamParser|\[result\]|\[dictionary\]|extractResultSection|stripResultBoundaryLines|SECTION_LABEL_PATTERN" app/src/main/java/io/legado/app/domain app/src/test/java/io/legado/app/domain`
- `.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.domain.model.AiTranslationRefinePipelineTest" --tests "io.legado.app.domain.usecase.TranslateChapterAiRetryTest" --tests "io.legado.app.domain.model.AiTranslationChunkPipelineTest" --tests "io.legado.app.domain.usecase.TranslateChapterFinalizeOutputTest" --tests "io.legado.app.domain.model.AiPromptCatalogTest" --console=plain --no-daemon`
- `.\gradlew.bat :app:compileAppDebugKotlin --console=plain --no-daemon`
- `.\gradlew.bat :app:assembleAppDebug --console=plain --no-daemon`
- `adb -s emulator-5554 install -r -t app/build/outputs/apk/app/debug/app-app-x86_64-debug.apk`
- `adb -s emulator-5554 shell am start -S -n com.drducbook.app.debug/io.legado.app.ui.main.MainActivity`
- `adb -s emulator-5554 logcat -d -t 500 | rg -n "FATAL EXCEPTION|AndroidRuntime|NoDefinitionFoundException|Could not create instance|AiTranslationRefinePipeline|Translation parse error|ReadBookViewModel|SourceCheckEngine"`
- `.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.domain.usecase.TranslateChapterAiRetryTest" --tests "io.legado.app.domain.model.AiTranslationRefinePipelineTest" --tests "io.legado.app.domain.usecase.TranslateChapterFinalizeOutputTest" --console=plain --no-daemon`
- `.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.domain.model.LocalAiTranslationPromptTest" --tests "io.legado.app.domain.usecase.ExportAuthoringProjectUseCaseTest" --console=plain --no-daemon`
- `.\gradlew.bat :app:testAppDebugUnitTest --no-daemon --console=plain`

## Ket qua

- Focused AI pipeline/cache tests PASS.
- Kotlin compile PASS.
- Debug assemble PASS.
- APK x86_64 install len LDPlayer `emulator-5554` PASS.
- App launch smoke PASS; logcat khong co match crash/Koin/translation parse fatal trong 500 dong gan nhat.
- APK x86_64 SHA-256 latest: `AA3645CC50B3D96B77084E56D60F7C0A6DDC782227D956202EE38261CA0D5B82`.
- Local AI prompt regression PASS: custom style appended after the current default JSON-refiner prompt is kept as compact `STYLE` guidance for local models.
- Full Android debug unit suite latest PASS: 944 tests, 0 failures, 0 errors, 1 skipped.

## Dieu kien thong qua

- Khong con duong production tao prompt/output theo contract cu.
- Ket qua dich khong mat doan, khong chen marker/tag ky thuat va giu thu tu segment.
- Tests bao phu missing/duplicate/wrong segment id, CJK leak, JSON repair, provider fallback, cache cu va web auto-translation job.
- Report nay co danh sach file reference da doc, thay doi code, lenh test va rui ro con lai.

## Nhat ky task

2026-08-01 15:36 - STARTED. Task duoc them theo yeu cau nguoi dung sau checkpoint P11.T04; P06.T05 la hotfix da co, P11.T08 la gate rewrite/validation cuoi cung.

2026-08-01 15:57 - CODE/TEST CHECKPOINT PASS. Contract runtime da khoa ve JSON refiner moi; parser va fallback `[result]`/`[dictionary]` cu da duoc go khoi duong production refiner. Con pending gate dich thu mot chuong that bang model that va quyet dinh cache invalidation cho ban dich cu neu can.

2026-08-01 16:06 - CACHE INVALIDATION CHECKPOINT PASS. Cache APP_AI co marker contract cu `[result]`, `[/result]`, `[dictionary]` se bi bo qua va dich lai; focused tests/assemble/install/launch smoke PASS. Con pending gate dich thu mot chuong that bang model that.

2026-08-02 09:25 - READER/EXPORT TRANSLATION CONTENT CHECKPOINT PASS. Reader co mode ban dich tong hop; finalize co the chot payload cache cu thanh revision vinh vien; export dung resolver uu tien ban chot/cache dich va MIME type SAF. Focused reader/export/revision tests PASS, Kotlin compile PASS, assemble/install LDPlayer PASS. Con pending gate user smoke voi mot sach dich that va kiem tra file xuat trong thu muc nguoi dung chon.

2026-08-02 11:51 - LOCAL AI PROMPT REGRESSION PASS. `LocalAiTranslationPrompt` nhan dien `TranslationConstants.DEFAULT_PROMPT` hien tai va chi dua phan user suffix vao `STYLE`, tranh day ca prompt JSON-refiner dai vao model local. Targeted tests PASS; full Android debug unit suite PASS 944 tests.

## Thay doi checkpoint 2026-08-02 09:25

- Reader co content page tong hop `TRANSLATION`: uu tien ban dich da chot/user-edited, sau do cache provider theo thu tu AI provider -> NMT -> Quick Translator -> Google -> ML Kit.
- Chuc nang chot ban dich trong reader luu revision vinh vien; neu current revision la payload cache cu thi `finalize()` tao user-edit snapshot truoc khi chot.
- Export ebook co lua chon noi dung `original`, `translation`, `both`; khi chon translation-only thi thieu cache dich se bao loi thay vi am tham xuat raw.
- Export ban dich dung cung resolver voi reader, nen cac chuong da chot la co so noi dung dich xuat ebook; chuong chua chot lay cache provider theo thu tu uu tien.
- Sua tao file qua SAF bang MIME type theo duoi file de file EPUB/PDF/HTML/TXT/CBZ xuat ra hien dung trong document provider.
- Kiem tra PASS: `ReaderContentModeTest`, `ReaderTranslationModePolicyTest`, `EbookExportScopeTest`, `ManageTranslationRevisionUseCaseTest`, `:app:compileAppDebugKotlin`, `:app:assembleAppDebug`, install x86_64 debug len `emulator-5554`.
