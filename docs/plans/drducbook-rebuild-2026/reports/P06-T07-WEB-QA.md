# P06.T07 - Web type-check, Playwright va responsive QA

## Muc tieu

Dong QA Phase 06 cho WebService web UI: type-check/build, dong bo asset vao Android, smoke desktop/mobile va xac nhan man hinh WebService khong con loi trang trang/circular import/`undefined`.

## Thay doi chinh

- Sua `modules/web/src/api/index.ts` de goi `useConnectionStore()` lazy, tranh circular import lam trang web runtime bi loi `Cannot access ... before initialization`.
- Sua `modules/web/src/views/WebServiceSettings.vue`:
  - Fallback instance text ve `Dang cho WebService tu app` khi backend chua tra du thong tin.
  - Khong hien `undefined undefined · HTTP undefined · WS undefined`.
  - Sua copy tieng Viet cua `Dich tu dong`.
  - Co dinh width cac control background de desktop khong bi tran panel.
- Build lai `modules/web/dist/**` va sync sang `app/src/main/assets/web/vue/**`.

## Kiem thu

Da chay:

```text
pnpm type-check
pnpm build
node .codex-tmp/p06-web-qa/smoke.mjs
.\gradlew.bat :app:compileAppDebugKotlin --no-daemon --console=plain
```

Ket qua:

- `pnpm type-check`: PASS.
- `pnpm build`: PASS; asset hash moi da sync vao Android assets.
- Smoke desktop `1365x900`: PASS.
- Smoke mobile `390x844`: PASS.
- `:app:compileAppDebugKotlin`: BUILD SUCCESSFUL.

## Smoke metrics

Smoke dung Chromium/Playwright local voi `/api/v2/instance` stub rong de mo phong truong hop app Android chua bat WebService backend.

| Viewport | Settings root | Panel | Header | Undefined text | Horizontal overflow | Console/page error |
|---|---:|---:|---|---|---|---|
| Desktop 1365x900 | true | 3 | `Dang cho WebService tu app` | false | false | 0 |
| Mobile 390x844 | true | 3 | `Dang cho WebService tu app` | false | false | 0 |

Bang chung:

- `reports/artifacts/P06-T07-webservice-desktop-1365x900.png`
- `reports/artifacts/P06-T07-webservice-mobile-390x844.png`
- `reports/artifacts/P06-T07-webservice-smoke.json`
- `modules/web/dist/assets/WebServiceSettings-D00wCIik.js`
- `app/src/main/assets/web/vue/assets/WebServiceSettings-D00wCIik.js`

## Rui ro/cong viec con lai

- Chua chay live pairing/export/translation tren may Android that trong task nay; cac API da duoc khoa o P06.T01-P06.T06 va P06.T07 tap trung vao packaged web UI/render.
- Ktor/WebSocket integration host van nen them o Phase 11 de test route thuc te dau-cuoi voi backend Android dang chay.
