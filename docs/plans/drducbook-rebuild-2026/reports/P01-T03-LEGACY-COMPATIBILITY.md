# P01.T03 - Legacy compatibility island va R8

## Ket qua

Trang thai: `DONE`

DrDucBook giu compatibility island trong package `io.legado.app` thay vi tao module rong roi di chuyen hang loat. Lua chon nay bao toan ten serialized/reflection va runtime hien co; implementation nhan dien/cloud moi nam trong `com.drducbook.app`.

Contract va owner duoc khoa tai `contracts/LEGACY-COMPATIBILITY-SURFACE.md`. Bemat duoc bao ve gom:

- Entity/rule JSON cua BookSource, RSS, HTTP TTS.
- Rhino `JsExtensions`/`NativeBaseSource` va ten member JavaScript.
- VBook executor/adapter/importer/inspector cho text, comic, audio, video, TTS va translator.
- ReaderProvider route/payload va Web API contract.
- Legacy deep-link aliases `legado://` va `yuedu://`.

## File va quy tac

- `app/legacy-compat-rules.pro`: keep rule co pham vi, khong co `io.legado.app.**` package-wide keep.
- `app/legacy-compat-abi.txt`: 17 class bat buoc con ten lich su sau R8.
- `scripts/compat/verify-legacy-abi.ps1`: chi trich `classes*.dex` va doi chieu allow-list.
- `app/proguard-rules.pro`: entity keep duoc thu hep ve `io.legado.app.data.entities.**`.

## Verification

- `CompatibilityCorpusTest`: 5/5 tests PASS, gom fixture Legado va 6 loai VBook.
- Final release: `BUILD SUCCESSFUL`, R8 + precise resource shrinking bat.
- Dex verification: 17/17 class PASS trong 4 dex files.
- Final release universal SHA-256: `D0A5C58B91BCB7C85735288DA11301679503B61C76E4F1F64BCB0ADBDE7FEE65`.

## Rui ro con lai

Baseline Profile plugin 1.4.1 canh bao chua duoc test voi AGP 9.2.1 va co startup entries cu; build van pass. Viec regenerate profile tren device duoc de lai P11 performance/release gate.
