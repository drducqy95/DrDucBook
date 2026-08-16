# P08.T06 - Chatbot layout and agent tool hotfix

## Muc tieu

Sua loi regex/Markdown lam mat bo cuc cau tra loi chatbot; bo sung tool va huong dan skill de chatbot co the kiem tra model dang dung, thong bao han muc khi biet duoc va ho tro chan doan/sua loi nguon sach.

## Thay doi

- `app/src/main/java/io/legado/app/ui/widget/components/text/MarkdownBlock.kt`
  - Them normalizer truoc khi parse Markdown de tach cac bullet/list bi model tra ve lien nhau trong cung dong.
  - Doi render list item sang hang co cot noi dung co `weight(1f)`, tranh noi dung sau marker chen ngang/mat bo cuc.
  - Bo qua marker node trong content cua list item de khong hien trung dau bullet.
- `app/src/main/java/io/legado/app/data/repository/AiToolRepository.kt`
  - Them tool `get_ai_runtime_status` de xem preset/model/provider/protocol/params hien hanh ma khong lo API key/token.
  - Them tool `get_ai_quota_status`; chi bao han muc chinh xac khi provider co du lieu, con lai tra `quotaKnown=false` va khong doan.
  - Them tool `diagnose_book_source` dung source-check engine de kiem tra trang thai nguon sach.
  - Them tool `repair_book_source` de sua metadata VBook/source compatibility co approval gate.
  - Tool catalog hien co 45 tool; khi tat safety gates con 23 tool kha dung, giai thich hien tuong nguoi dung thay it tool hon tong so.
- `app/src/main/java/io/legado/app/domain/agent/AgentPermissionBroker.kt`
  - Gan capability va risk cho diagnose/repair source; repair la WRITE va can approval.
- `app/src/main/java/io/legado/app/domain/usecase/AiChatGenerationUseCase.kt`
  - System guidance nay neu hoi model/quota thi goi dung runtime/quota tool; neu sua nguon thi search/diagnose truoc, repair chi sau khi duoc xac nhan.
- `app/src/main/java/io/legado/app/di/appModule.kt`
  - Cap nhat DI cho cac dependency source-check va AI profile moi cua `AiToolRepository`.
- Tests
  - `MarkdownBlockNormalizerTest.kt`
  - `AiToolRepositoryToolCatalogTest.kt`
  - `AgentPermissionBrokerTest.kt`
  - `AiChatGenerationUseCaseTest.kt`

## Kiem tra

- `.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.ui.widget.components.text.MarkdownBlockNormalizerTest" --tests "io.legado.app.data.repository.AiToolRepositoryToolCatalogTest" --tests "io.legado.app.domain.agent.AgentPermissionBrokerTest" --tests "io.legado.app.domain.usecase.AiChatGenerationUseCaseTest" --console=plain` PASS.
- Focused VBook + Markdown + Agent + AI translation regression suite PASS sau khi bo sung pipeline dich moi.
- `.\gradlew.bat :app:compileAppDebugKotlin --console=plain` PASS.
- `.\gradlew.bat :app:assembleAppDebug --console=plain` PASS.
- APK debug moi da install va launch tren LDPlayer `emulator-5554`.

## Ket qua

- Cau tra loi chatbot dang list/bullet khong con bi regex gom dong lam vo bo cuc nhu screenshot.
- Agent co du tool doc runtime model, thong bao quota that/co gioi han thong tin, chan doan source va repair source co approval.
- Huong dan skill/system prompt cua chatbot da biet khi nao can goi cac tool moi, tranh doan thong tin quota/model.

## Luu y

- Quota exact phu thuoc provider co endpoint/response cong khai hay khong; tool khong tu che so du khi provider khong tra ve.
- Repair source chi sua metadata/rule compatibility o muc app; neu script VBook thuc su sai logic/anti-bot, Agent can draft/test/install tool plugin rieng sau khi nguoi dung xac nhan.

## Checkpoint 2026-08-02 09:25 - Tool policy default

- Default feature flags `agentMutation`, `agentSkill`, `agentPlugin` duoc bat cho cai dat moi.
- Built-in WRITE/plugin/skill tools van qua approval gate, nhung khong con bi an/chan mac dinh truoc khi app tao hop xac nhan.
- Muc tieu truc tiep: tranh loi `Tool 'create_vbook_plugin_draft' is disabled by the current feature policy` khi chatbot/agent can tao draft plugin VBook.
- Kiem tra PASS: `AgentPermissionBrokerTest`, `AiToolRepositoryToolCatalogTest`, `AiChatGenerationUseCaseTest`, `:app:compileAppDebugKotlin`, `:app:assembleAppDebug`.

## Checkpoint 2026-08-02 11:16 - Provider alias va installed-app policy migration

- Them `AgentToolNameNormalizer` de canonical hoa alias tool tu provider, dac biet `create_vbook_plugin.draft` -> `create_vbook_plugin_draft`.
- `ToolTraceBuilder`, `RunAiAgentUseCase`, `AgentPermissionBroker` va `AiToolRepository` deu dung ten canonical khi tao proposal, kiem tra policy, thuc thi tool, luu trace/result.
- `LabConfig` mac dinh bat `agentMutation`, `agentSkill`, `agentPlugin`; startup migration ghi ca DataStore va SharedPreferences de ban debug da cai san khong giu policy false cu.
- Kiem tra PASS:
  - `.\gradlew.bat :app:compileAppDebugKotlin --console=plain --no-daemon`
  - `.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.domain.agent.AgentPermissionBrokerTest" --tests "io.legado.app.data.repository.AiToolRepositoryToolCatalogTest" --tests "io.legado.app.domain.usecase.AiChatGenerationUseCaseTest" --console=plain --no-daemon`
  - `.\gradlew.bat :app:assembleAppDebug --console=plain --no-daemon`
- Cai APK x86_64 debug len LDPlayer `emulator-5554` PASS; force-stop va mo `MainActivity` PASS.
- Device verification:
  - DataStore `featureAgentMutation=True`, `featureAgentSkill=True`, `featureAgentPlugin=True`, `featureAgentToolPolicyUpgrade=True`.
  - SharedPreferences `com.drducbook.app.debug_preferences.xml` co bon khoa tren voi `value="true"`.
- APK checkpoint:
  - x86_64 debug SHA-256 `7BE9A60DA06A33E26C2F9AB73ABB1FFC4A6B871215B546F301DABAD6F0CD95D1`
  - universal debug SHA-256 `6F6FC9CF11B87FC698391DD00FA4B73303BFD7AABF5474CF721AFCE204B65643`
