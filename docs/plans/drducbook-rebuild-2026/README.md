# Ke hoach tai cau truc DrDucBook 2026

Ngay tao: 29/07/2026  
Codebase: `D:/Downloads/Archives/legado-with-MD3-main/legado-with-MD3-main`  
Ngon ngu tai lieu: Tieng Viet, UTF-8  
Trang thai hien tai: `ACTIVE - PHASE 00-01 DONE, PHASE 02 READY`

## 1. Muc dich

Bo tai lieu nay chuyen ke hoach tong the DrDucBook thanh cac phase va task co the giao truc tiep cho ky su hoac agent. Moi task phai neu ro muc tieu, file du kien tac dong, trinh tu thuc hien, dieu kien thong qua va bang chung. Task chi duoc danh dau `DONE` sau khi da cap nhat ca [TASK-MATRIX.md](./TASK-MATRIX.md) va [PLAN-LOG.md](./PLAN-LOG.md).

## 2. Quyet dinh da khoa

- Thuong hieu: `DrDucBook`.
- Android namespace/application ID: `com.drducbook.app`.
- DrDucBook cai va chay song song voi app cu; khong dung authority, permission, cloud client, backup hay cong WebService cua app cu.
- Nguon JSON/JS Legado, RSS/TTS va plugin ZIP/script VBook phai tuong thich; khong co plugin APK/JAR.
- Thanh dieu huong gom Trang chu, Ke sach, Kham pha, Workspace, Ca nhan; Browser khong nam tren thanh dieu huong.
- Browser lien thong voi nguon, tu sinh dau trang nguon, chia se cookie noi bo voi runtime nguon va kich hoat kiem tra dung nguon sau dang nhap.
- WebService doc lap voi theme Android. App chi bat/tat dich vu; web chi cho bat/tat Export, Dich tu dong va thay background.
- Goi private nam tai Hugging Face dataset `Drduc/Legadofork`; Supabase Edge Function xac thuc user va cap download ticket/URL ngan han ma khong lo HF token.
- Dang nhap dung Supabase Auth email/mat khau va Google; metadata sync dung Supabase Postgres + RLS, snapshot/assets dung private Supabase Storage voi canh bao conflict.
- Google Drive `appDataFolder` van la dich sao luu/dong bo tuy chon, dung OAuth consent rieng va cung snapshot/conflict contract; no khong duoc dung de phan phoi package.
- Firebase va Cloud Run khong nam trong kien truc dich; cac dependency/config Firebase hien co se duoc go o P01/P10.
- Icon nguon do nguoi dung cung cap phai duoc xoa marker Gemini va metadata C2PA truoc khi import.

## 3. Danh sach phase

| Phase | Tai lieu | Ket qua chinh |
|---|---|---|
| 00 | [Baseline va bao mat](./phases/PHASE-00-BASELINE-SECURITY.md) | Baseline co bang chung, secret an toan, compatibility corpus duoc khoa |
| 01 | [Nhan dien va cai song song](./phases/PHASE-01-IDENTITY-COEXISTENCE.md) | Package moi, thuong hieu moi, app cu va DrDucBook cung ton tai |
| 02 | [Dieu huong va Workspace](./phases/PHASE-02-NAVIGATION-WORKSPACE.md) | 5 destination, Workspace hub, Browser co nut thoat dung nghia |
| 03 | [Ca nhan hoa Android](./phases/PHASE-03-ANDROID-PERSONALIZATION.md) | Theme profile, icon noi bo, hinh nen, import/export theme |
| 04 | [Browser, nguon, bookmark va cookie](./phases/PHASE-04-BROWSER-SOURCE-COOKIE.md) | Browser nhan dien nguon, bookmark tu dong, cookie hai chieu |
| 05 | [Tinh trang nguon](./phases/PHASE-05-SOURCE-HEALTH.md) | Engine Quick/Standard/Full cho Book/RSS/VBook, dashboard va lich su |
| 06 | [WebService](./phases/PHASE-06-WEB-SERVICE.md) | Cong rieng, pairing, Export, Dich tu dong, background web |
| 07 | [Sang tac va Ebook](./phases/PHASE-07-AUTHORING-EBOOK.md) | Luu an toan, preview, EPUB3/PDF/TXT, backup |
| 08 | [Agent va cong cu tu tao](./phases/PHASE-08-AGENT-TOOLS.md) | Tool registry day du, JS sandbox, approval va audit |
| 09 | [Media player va download](./phases/PHASE-09-MEDIA.md) | Resolve media dung rule, player Media3, HLS/DASH download |
| 10 | [Hugging Face, Supabase Auth va Sync](./phases/PHASE-10-CLOUD-AUTH-SYNC.md) | Edge Function, Supabase Auth/Postgres/Storage, Google Drive backup va conflict |
| 11 | [Tich hop va phat hanh](./phases/PHASE-11-INTEGRATION-RELEASE.md) | Regression, security, performance, rollout va release gates |

## 4. Thu tu phu thuoc

1. P00 phai xong truoc moi thay doi kien truc hoac secret.
2. P01 phai xong truoc khi tao Supabase client config, Provider, WebService va release artifact.
3. P02 va P03 co the chay song song sau khi route/package cua P01 on dinh.
4. P04 phu thuoc P01 va route Browser cua P02; P05 phu thuoc `SourceKey`, cookie gateway va source context cua P04.
5. P06 phu thuoc package/cong cua P01; auto translation phu thuoc translation use case hien co, khong phu thuoc theme Android.
6. P07, P08 va P09 co the chay song song sau P01; tich hop Workspace duoc dong o P02/P11.
7. P10 phu thuoc P01 va schema backup cua P03/P04/P05/P07/P08/P06.
8. P11 chi dong sau khi tat ca phase duoc chon cho release da `DONE` va co log.

## 5. Trang thai task

- `TODO`: chua bat dau.
- `IN_PROGRESS`: dang thuc hien, phai co nguoi/agent phu trach trong task matrix.
- `BLOCKED`: bi chan; log phai neu ro blocker, bang chung va dieu kien mo chan.
- `DONE`: code, test, tai lieu va log deu da hoan thanh.
- `DEFERRED`: duoc dua ra khoi release voi quyet dinh co chu ky trong plan log.

## 6. Quy trinh bat buoc cho moi task

1. Chuyen task sang `IN_PROGRESS` trong `TASK-MATRIX.md` va ghi thoi diem bat dau vao `PLAN-LOG.md`.
2. Doc lai file/contract hien tai; khong sua ngoai pham vi neu khong co log thay doi pham vi.
3. Trien khai va bo sung test theo muc `Dieu kien thong qua` cua task.
4. Chay gate, luu lenh va ket qua. Neu khong the chay, task khong duoc `DONE`.
5. Cap nhat tai lieu/API/schema lien quan.
6. Them entry hoan thanh vao `PLAN-LOG.md`, gom file thay doi, test, bang chung, rui ro con lai va task tiep theo.
7. Chuyen task sang `DONE`; khong danh dau hang loat neu chua co log rieng cho tung task.

## 7. Gate chung

```powershell
.\gradlew.bat :app:compileAppDebugKotlin --no-daemon --console=plain
.\gradlew.bat :app:testAppDebugUnitTest --no-daemon --console=plain
.\gradlew.bat :app:assembleAppDebug --no-daemon --console=plain
```

Web phase phai chay them:

```powershell
pnpm --dir modules/web type-check
pnpm --dir modules/web build
```

Thay doi manifest, Room, R8, Supabase, resource hoac packaging phai co gate bo sung trong phase tuong ung. Release UI/Browser/Web/3D-media phai co screenshot hoac smoke-test thiet bi, khong chi compile.

## 8. Definition of Done chung

- Contract, schema, migration/compatibility adapter va DI da noi day du.
- Happy path, empty, loading, error, cancel, retry va process recreation co test khi ap dung.
- Khong crash/ANR/Koin error; khong lo token, cookie, credential hoac noi dung nguon trong log.
- UI moi tuan Compose MVI/UDF, immutable state, Navigation 3 va accessibility labels.
- App cu va DrDucBook cung cai duoc; khong authority/cong/data collision.
- Task matrix va plan log khop voi code/test thuc te.
