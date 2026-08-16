# P11.T02 - Side-by-side end-to-end regression

## Muc tieu

Kiem tra app DrDucBook moi co the cai song song voi ban Legado fork cu va VBook, co dataDir rieng, launch doc lap, khong crash trong smoke dau tien.

## Trang thai

IN_PROGRESS. Da co smoke cai dat/launch song song tren emulator hien co. Chua dong DONE vi chua chay du luong end-to-end bookshelf/source/import/export/sync giua ca hai ban.

## Moi truong

- Device: `emulator-5554`
- Android: 14, SDK 34
- Model: `SM-S9280`
- APK moi: `app/build/outputs/apk/app/debug/app-app-x86_64-debug.apk`
- New app package: `com.drducbook.app.debug`
- Legacy app package: `io.legato.kazusa.debug`
- VBook package: `com.vbook.android`

## Kiem tra da chay

```powershell
adb devices -l
adb -s emulator-5554 shell pm list packages
adb -s emulator-5554 install -r app\build\outputs\apk\app\debug\app-app-x86_64-debug.apk
adb -s emulator-5554 shell am start -S -n com.drducbook.app.debug/io.legado.app.ui.main.MainActivity
adb -s emulator-5554 shell am start -S -n io.legato.kazusa.debug/io.legado.app.ui.main.MainActivity
adb -s emulator-5554 shell dumpsys package com.drducbook.app.debug
adb -s emulator-5554 shell dumpsys package io.legato.kazusa.debug
adb -s emulator-5554 shell dumpsys package com.vbook.android
adb -s emulator-5554 logcat -d -t 300
adb -s emulator-5554 shell screencap -p /sdcard/drducbook-p11-side-by-side.png
adb -s emulator-5554 pull /sdcard/drducbook-p11-side-by-side.png docs\plans\drducbook-rebuild-2026\reports\artifacts\p11-side-by-side-new-app.png
```

## Ket qua

- Device online: `emulator-5554 device product:SM-S9280 model:SM_S9280`.
- Cac package cung ton tai:
  - `com.drducbook.app.debug`
  - `io.legato.kazusa.debug`
  - `com.vbook.android`
- Cai lai APK moi: `Success`.
- App moi launch duoc:
  - PID: `4733`
  - Displayed `MainActivity` sau khoang 8s trong logcat.
  - Khong thay `FATAL EXCEPTION`, `AndroidRuntime`, `NoBeanDefFoundException`, `InstanceCreationException` trong doan smoke.
- App cu launch duoc:
  - PID: `5002`
  - App moi van con PID `4733` sau khi mo app cu.
- DataDir rieng:
  - New app: `dataDir=/data/user/0/com.drducbook.app.debug`
  - Legacy app: `dataDir=/data/user/0/io.legato.kazusa.debug`
  - VBook: `dataDir=/data/user/0/com.vbook.android`
- Screenshot smoke app moi:
  - `reports/artifacts/p11-side-by-side-new-app.png`

## Dieu kien da dat

- App moi cai song song voi ban cu va VBook tren cung device.
- App moi va app cu co dataDir rieng.
- App moi va app cu launch doc lap khong crash trong smoke logcat ngan.
- App moi hien UI `Gia sach` thay vi trang trang/crash.

## Dieu kien chua dat de dong DONE

- Chua chay regression luong doc/ghi bookshelf rieng giua app cu va app moi.
- Chua chay source import/explore/search/open-book tren ca hai app trong cung session.
- Chua chay backup/export/import side-by-side.
- Chua chay VBook ext/plugin compatibility trong P11.T03 tren device nay.

## Buoc tiep theo

- Chay P11.T03 compatibility regression voi corpus Legado/VBook sau khi xac dinh bo fixture/device data se dung.
- Bo sung smoke bookshelf/source import cho ca `com.drducbook.app.debug` va `io.legato.kazusa.debug`.
