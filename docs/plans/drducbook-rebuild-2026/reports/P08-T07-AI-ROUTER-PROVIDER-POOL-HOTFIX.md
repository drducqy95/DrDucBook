# P08.T07 - AI Router provider credential pool hotfix

Status: DONE - focused AI Router provider/account pool hotfix PASS

## Muc tieu

Hoan thien UI va runtime AI Router theo co che 9router-style:

- Moi provider quan ly duoc nhieu account OAuth hoac nhieu API key/token trong cung man hinh provider.
- Combo/route chi chon model; khong can liet ke tung account/API key.
- Runtime tu lay lan luot credential dang bat cua provider tuong ung voi model.
- Du lieu route OAuth cu gan credential cu the duoc chuyen ve target theo model/provider pool.

## Pham vi file tac dong

- `app/src/main/java/io/legado/app/data/dao/AiRouterDao.kt`
- `app/src/main/java/io/legado/app/data/repository/AiRouterRepository.kt`
- `app/src/main/java/io/legado/app/data/repository/AiOAuthRepository.kt`
- `app/src/main/java/io/legado/app/domain/usecase/RepairAiRouteBindingsUseCase.kt`
- `app/src/main/java/io/legado/app/ui/ai/router/AiRouterContract.kt`
- `app/src/main/java/io/legado/app/ui/ai/router/AiRouterViewModel.kt`
- `app/src/main/java/io/legado/app/ui/ai/router/AiRouterScreen.kt`
- `app/src/main/java/io/legado/app/ui/ai/router/AiProviderGrid.kt`
- `app/src/main/java/io/legado/app/ui/ai/router/AiRouterDashboardMapper.kt`
- `app/src/main/java/io/legado/app/ui/ai/router/AiProviderConfigSheet.kt`
- `app/src/test/java/io/legado/app/data/repository/AiRouterRepositoryTest.kt`
- `app/src/test/java/io/legado/app/domain/usecase/RepairAiRouteBindingsUseCaseTest.kt`

## Ket qua

- Them pool cursor theo `providerId:targetId`; cursor chi tang sau khi credential that su duoc dung.
- Target model-only se resolve toan bo credential dang bat cua provider, loc cooldown/status va xoay vong theo thu tu `sortNumber/createdAt/id`.
- Semantic retry bo qua ca target/model da fail, khong nhay sang account tiep theo trong cung model.
- Loi credential trong provider pool chi danh dau credential neu policy xac dinh loi thuoc credential; target model khong bi cooldown oan.
- OAuth login moi khong tao target rieng theo account nua; chi tao/cap nhat target theo model voi `credentialId = null`.
- Startup repair use case chuyen target OAuth cu gan account thanh mot target pool cho moi model va xoa target account trung lap.
- UI AI Router co tab provider/model/combo/log; provider OAuth mo sheet pool de dang nhap them account va quan ly tung credential.
- Provider API key tiep tuc luu nhieu key trong provider config sheet; combo chi them model, route target khong can credential cu the.
- 2026-08-02 follow-up: route card/combo copy khong con goi y chon credential cu the; target hien la model + provider credential pool.
- 2026-08-02 follow-up: them regression test cho model-only target xoay nhieu API key cua cung provider, tuong tu OAuth account pool.

## Kiem chung

- `.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.data.repository.AiRouterRepositoryTest" --tests "io.legado.app.domain.usecase.RepairAiRouteBindingsUseCaseTest" --console=plain --no-daemon`
- `.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.data.repository.AiRouterRepositoryTest" --console=plain --no-daemon` (2026-08-02 follow-up)
- `.\gradlew.bat :app:compileAppDebugKotlin --console=plain --no-daemon`
- `.\gradlew.bat :app:assembleAppDebug --console=plain --no-daemon`

## Dieu kien thong qua

- Focused AI Router + repair tests PASS.
- Kotlin compile PASS.
- Debug APK assemble PASS.
- Khong in/ghi token, OAuth code hay API key vao report/log.

## Rui ro con lai

- Chua co device screenshot rieng cho sheet provider pool sau hotfix nay.
- Han muc/quota chinh xac van phu thuoc provider co tra metadata hay khong.
- Neu nguoi dung co route tuy bien cu co target credential explicit, runtime van ton trong target do; startup repair chi chuyen cac route OAuth repairable mac dinh va target duplicate theo provider pool.

## Checkpoint 2026-08-02 09:25 - OpenCode Free va OAuth PKCE

- `opencode_free` chuyen sang OpenCode Console OpenAI-compatible free endpoint va khong gui `Authorization: Bearer public`.
- Catalog them `laguna-s-2.1-free`, `ling-3.0-flash-free`, `nemotron-3-super-free`; `hy3-free` duoc loai khoi catalog tinh va filter fetch de tranh model cu gay AUTH/UNKNOWN.
- UI label doi tu `Free Zen` sang `Free Console`.
- OAuth authorization-code exchange chi gui `code_verifier` khi provider bat PKCE, sua luong Antigravity dang tat PKCE nhung van gui verifier.
- Kiem tra PASS: `AiProviderCatalogTest`, `AiRouterDashboardMapperTest`, `AgentPermissionBrokerTest`, `AiToolRepositoryToolCatalogTest`, `AiChatGenerationUseCaseTest`, `OpenAiResponsesHandlerTest`, `:app:compileAppDebugKotlin`, `:app:assembleAppDebug`.
