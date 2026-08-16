# P01.T01 - Application identity va namespace

## Ket qua

Trang thai: `DONE`

- Gradle namespace: `com.drducbook.app`.
- Release application ID: `com.drducbook.app`.
- Debug application ID: `com.drducbook.app.debug`.
- Root project: `DrDucBook`.
- Baseline Profile fallback target: `com.drducbook.app`.
- APK app cu da duoc bao toan truoc build moi tai `artifacts/phase01/legacy-baseline/legado-baseline-x86_64-debug.apk`.

## Compatibility package allow-list

`io.legado.app` duoc giu co chu dich cho:

1. Entity/rule JSON va Room schema da ton tai.
2. Rhino/Legado JS helpers va native wrappers.
3. VBook importer/executor/adapters.
4. ReaderProvider/Web API contract.
5. UI/runtime legacy chua duoc phase owner migrate.

Implementation identity/cloud moi nam trong `com.drducbook.app`. `DrDucBookApplication` ke thua compatibility `App`; implementation moi phu thuoc facade cu, khong dua Supabase/session/secret vao compatibility entities/JS/API.

Generated `R`, `BR`, `BuildConfig` va `databinding` imports duoc chuyen sang namespace moi trong 478 files. Manifest runtime components dung fully-qualified compatibility class names; Application class dung product package moi.

## Verification

```powershell
.\gradlew.bat :app:compileAppDebugKotlin --no-daemon --console=plain
```

Ket qua: `BUILD SUCCESSFUL` trong 9 phut 24 giay sau full namespace recompilation. Vong dau xac nhan Firebase client cu chan package moi; P01.T04 removal/config duoc thuc hien som de mo compile.

## Rui ro con lai

- Minified R8/ABI corpus gate thuoc P01.T03.
- Authority/deep-link merged manifest gate thuoc P01.T02.
- Firebase APK scan va Supabase focused tests thuoc P01.T04.
