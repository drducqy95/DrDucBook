# P06.T05 - AI translation pipeline hotfix

## Muc tieu

Bo pipeline dich AI cu dua tren `[result]`/`[dictionary]`; thay bang pipeline refiner moi tham khao `Translator Engine` voi context pack, raw segments, QT draft, JSON output theo segment ID va QC khong con CJK trong ban dich tieng Viet.

## Thay doi

- `app/src/main/java/io/legado/app/domain/model/AiTranslationRefinePipeline.kt`
  - Them runtime pipeline Stage 2 -> Stage 4 cho Android: tao `context_pack`, tach `raw_segments`, gan ban nhap QT, loc `locked_dictionary`, va parse ket qua `refined_segments`.
  - Bat buoc output du moi ID, khong trung/khong thieu/khong them ID la; output tieng Viet bi chan neu con Han/Kana/Hangul.
  - Giu fallback doc output legacy `[result]`/`[dictionary]` de khong pha cache/provider cu, nhung runtime moi khong con yeu cau format nay.
- `app/src/main/java/io/legado/app/domain/usecase/TranslateChapterUseCase.kt`
  - Duong `PROVIDER_APP_AI` nay tao request tu `context_pack` + `SEGMENTS_RAW_QT`, khong con prompt paragraph-marker cu.
  - Local GGUF va provider online dung chung prompt JSON refiner; retry duoc gan parse error ro rang khi thieu ID, sai JSON, merge layout hoac con CJK.
  - `new_entities` duoc map ve dictionary pair de van hoc thuat ngu lien chuong; legacy dictionary section giu type `VIETPHRASE`.
  - Giu bao ve placeholder/URL/markup va restore token truoc khi cache.
- `app/src/main/java/io/legado/app/domain/model/TranslationConstants.kt`
  - Prompt mac dinh da doi sang hop dong JSON refiner, khong con huong dan `[result]`/`[dictionary]`.
- `app/src/test/java/io/legado/app/domain/model/AiTranslationRefinePipelineTest.kt`
  - Khoa regression context pack, loc locked dictionary, parse JSON, bat thieu ID, bat CJK va doc legacy output.
- `app/src/test/java/io/legado/app/domain/usecase/TranslateChapterAiRetryTest.kt`
  - Cap nhat retry theo pipeline JSON; khoa request AI phai co `CONTEXT_PACK_JSON`, `SEGMENTS_RAW_QT`, `refined_segments` va khong con cau prompt marker cu.
- `app/src/test/java/io/legado/app/domain/model/AiPromptCatalogTest.kt`
  - Cap nhat mandatory prompt gate theo hop dong JSON moi.

## Kiem tra

- `.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.domain.model.AiTranslationRefinePipelineTest" --tests "io.legado.app.domain.model.AiPromptCatalogTest" --console=plain` PASS.
- `.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.domain.usecase.TranslateChapterAiRetryTest" --console=plain` PASS.
- `.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.help.vbook.VbookPluginAdapterTest" --tests "io.legado.app.help.vbook.VbookExecutorTest" --tests "io.legado.app.help.vbook.VbookPluginImporterTest" --tests "io.legado.app.ui.widget.components.text.MarkdownBlockNormalizerTest" --tests "io.legado.app.data.repository.AiToolRepositoryToolCatalogTest" --tests "io.legado.app.domain.agent.AgentPermissionBrokerTest" --tests "io.legado.app.domain.usecase.AiChatGenerationUseCaseTest" --tests "io.legado.app.domain.model.AiTranslationRefinePipelineTest" --tests "io.legado.app.domain.model.AiPromptCatalogTest" --tests "io.legado.app.domain.usecase.TranslateChapterAiRetryTest" --console=plain` PASS.
- `.\gradlew.bat :app:compileAppDebugKotlin --console=plain` PASS.
- `.\gradlew.bat :app:assembleAppDebug --console=plain` PASS.
- `adb -s emulator-5554 install -r -t app/build/outputs/apk/app/debug/app-app-x86_64-debug.apk` PASS.
- `adb -s emulator-5554 shell am start -n com.drducbook.app.debug/io.legado.app.ui.main.MainActivity` PASS.

## Ket qua

- Pipeline AI moi da dung contract JSON theo segment ID, giong huong Stage 2/3/4 cua Translator Engine nhung chay truc tiep trong Android runtime.
- Loi AI merge/mat doan nay bi chan som va retry; loi con CJK trong ban dich tieng Viet bi chan truoc khi ghi cache.
- Prompt mac dinh va prompt runtime khong con ra lenh output `[result]`/`[dictionary]`.
- Ban debug moi da duoc build, cai va mo lai tren LDPlayer `emulator-5554`.

## Luu y van hanh

- Cac ban dich chuong da cache bang pipeline cu co the can dich lai/force retranslate de sinh cache moi theo contract JSON.
- Neu preset nguoi dung da tu sua va con noi dung yeu cau `[result]`, runtime van co cau "Pipeline override" de ep JSON, nhung nen tao lai preset tu template moi de chat luong on dinh hon.
