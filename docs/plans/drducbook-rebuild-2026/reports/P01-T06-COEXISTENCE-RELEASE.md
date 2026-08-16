# P01.T06 - Coexistence, isolation va release gate

## Ket qua

Trang thai: `DONE`

Thiet bi: `emulator-5554`, model `SM_S9280`.

| App | Package | UID sau final install | Data dir |
|---|---|---:|---|
| Legado cu | `io.legato.kazusa.debug` | 10082 | `/data/user/0/io.legato.kazusa.debug` |
| DrDucBook | `com.drducbook.app.debug` | 10083 | `/data/user/0/com.drducbook.app.debug` |

Hai APK dung cung debug certificate SHA-256 `6565ec...af38f1` nhung package, provider authority, UID va data directory rieng.

## Device evidence

- Cai app cu va DrDucBook: ca hai `Success`, khong provider conflict.
- Cold start app cu va DrDucBook: `Status: ok`, khong `FATAL EXCEPTION`/AndroidRuntime crash.
- Provider cu tra 3 bookshelf records; provider DrDucBook moi tra empty state. Khong copy DB/preferences/cookie sandbox tu dong.
- Giai cai rieng DrDucBook thanh cong; package cu, `legado.db` va kha nang mo app cu van con.
- Cai lai DrDucBook thanh cong; final device co ca hai package.
- Screenshots: `artifacts/phase01/device-legacy.png`, `device-drducbook-fixed.png`, `device-welcome-fixed.png`, `device-app-details.png`.

## Build va test gate

- Full debug unit: 663 tests, 0 failure, 0 error, 1 skipped.
- Debug assemble: PASS.
- noR8 assemble: PASS.
- Release assemble voi R8/resource shrinking: PASS.
- Compatibility ABI: 17/17 classes trong 4 dex files.
- Web type-check/build/sync: PASS.

Final artifact hashes:

- Debug x86_64: `289412C1727332F2277E9F7941267CD5537C884FDD385EE34206B9352C1EB50E`.
- noR8 universal unsigned: `ECB63EE18CED083EC747CB34FE4DCEEA561FF83ED3FA0440DF28F7D98013FFFE`.
- Release universal unsigned: `D0A5C58B91BCB7C85735288DA11301679503B61C76E4F1F64BCB0ADBDE7FEE65`.

## Rui ro con lai

- AGP 9.2.1 incremental APK splitter co the fail lan package dau sau resource change; isolated full package retry PASS cho ca noR8/release. Can theo doi/nang plugin o P11.
- Release artifact dang unsigned vi workspace khong co production keystore; P11 se chay signing/rollout gate.
- Khong go app cu de tranh xoa du lieu legacy tren emulator; chieu DrDucBook uninstall -> Legado survive da duoc xac minh.
