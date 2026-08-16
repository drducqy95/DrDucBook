# Phase 02 - Dieu huong, Workspace va Browser shell

Trang thai: `DONE` (2026-07-29)

## Muc tieu phase

Rut gon navigation thanh nam destination, gom bon cong cu vao Workspace va bien Browser thanh route co diem thoat ro rang.

## Pham vi file chinh

- `app/src/main/java/com/drducbook/app/ui/main/MainDestination.kt`
- `MainNavKey.kt`, `MainNavGraph.kt`, `MainScreen.kt`, theme/navigation config
- `ui/workspace/**` `[NEW]`
- `ui/browser/BrowserContract.kt`, `BrowserViewModel.kt`, `BrowserScreen.kt`, `BrowserRouteScreen.kt`
- strings, icons va navigation tests

## Task chi tiet

### P02.T01 - Rut gon top-level navigation

**Muc tieu:** Chi con Home, Bookshelf, Explore, Workspace, My.

**Pham vi file:** `MainDestination.kt`, `MainNavKey.kt`, `MainNavGraph.kt`, `MainScreen.kt`, navigation preferences va tests.

**Thuc hien:** Cap nhat destination/order/default-page/config migration; loai Browser, Agent, Writing, Ebook, RSS khoi top-level nhung giu route noi bo.

**Dieu kien thong qua:** Khong orphan route; deep link va saved destination cu co fallback; bottom bar/rail hien dung thu tu.

**Log:** Ghi route mapping cu->moi va test back stack.

### P02.T02 - Workspace Compose/MVI hub

**Muc tieu:** Tao mot page mo Sang tac, Ebook, Agent va Nguon RSS.

**Pham vi:** `ui/workspace/WorkspaceContract.kt`, `WorkspaceViewModel.kt`, `WorkspaceScreen.kt`, DI/nav entries `[NEW]`.

**Thuc hien:** State gom module availability, recent projects/tasks va badges; composable stateless; navigation qua callback; khong truy cap DB truc tiep.

**Dieu kien thong qua:** Bon module mo dung route, recent/empty/error states dung, process recreation khong mat selection.

**Log:** Screenshot phone/tablet va test ViewModel/navigation.

### P02.T03 - Browser route va nut Exit

**Muc tieu:** Back va Exit co hanh vi khong nhap nhang.

**Pham vi file:** Browser Contract/ViewModel/Screen/RouteScreen, main nav callbacks, strings/icons va Browser navigation tests.

**Thuc hien:** Back uu tien WebView history; nut `X` dong toan bo Browser route va ve route app truoc/Home; giu tab session; loai Browser shortcut nav.

**Dieu kien thong qua:** Test no-history/history/multiple-tabs/deep-link/process recreation pass; Exit khong chi `goBack()` trang web.

**Log:** Ghi scenario va video/screenshot smoke test.

### P02.T04 - State restoration va responsive navigation

**Muc tieu:** Navigation khong mat state khi rotate, process death hoac doi compact/expanded.

**Pham vi:** nav back stack serialization, destination state holders, bottom bar/rail UI.

**Dieu kien thong qua:** Restore duoc destination, Workspace, Browser tabs; text/icon khong tran o phone/tablet; predictive back pass.

**Log:** Ghi viewport va state restoration evidence.

### P02.T05 - Localization, accessibility va tests

**Muc tieu:** Hoan tat chat luong dieu huong.

**Pham vi:** `values*/strings.xml`, content descriptions, unit/UI tests.

**Dieu kien thong qua:** Vietnamese/English co day du; TalkBack labels va touch targets dat yeu cau; navigation tests pass.

**Log:** Lien ket test reports va accessibility checklist.

## Gate dong phase

- Nam destination duy nhat tren top-level.
- Workspace va Browser Exit pass unit/UI/device tests.
- Khong regression deep link hoac route legacy.

## Ket qua hoan tat

- 5/5 task `DONE`; top-level con Home, Bookshelf, Explore, Workspace, My.
- Workspace Compose/MVI gom Sang tac, Ebook, Agent va Nguon RSS.
- Browser Back/Exit, cold/warm route va session restoration PASS.
- Phone, landscape va `sw600dp` rail visual QA PASS.
- Full unit gate: 677 tests, 0 failure/error, 1 skipped.
- Loi crash phan Gioi thieu da sua va xac nhan tren APK cuoi.
