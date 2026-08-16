# P02.T05 - Localization, accessibility va tests

## Ket qua

Trang thai: `DONE`

## Localization

- Default English va `values-vi` deu co day du 10 string contract moi cua Workspace/Browser.
- Sua `default_home_page_value` tieng Viet ve route key `bookshelf`; nhan hien thi van la `Gia sach`.
- Resource merge/compile va debug assemble PASS.

## Accessibility

- Workspace module row co semantics button, label/so luong va chieu cao toi thieu 64dp.
- Toolbar Browser co content description rieng cho Back va `Thoat trinh duyet`.
- Bottom bar/rail dung label cua 5 destination va vung bam cua widget navigation chuan.
- Phone/tablet visual QA khong co text/icon tran hay chong lap.

## About fix

- Thay drawable XML bitmap wrapper khong duoc Compose ho tro bang PNG foreground truc tiep.
- Dung `ContentScale.Fit`, kich thuoc on dinh 120dp va can giua mo ta voi padding ngang.
- Kiem tra tren APK cuoi: About mo thanh cong; AndroidRuntime khong co crash moi.

## Test gate

- Full `:app:testAppDebugUnitTest`: 677 tests, 0 failure, 0 error, 1 skipped.
- Focused main/browser/workspace suite: PASS.
- `:app:assembleAppDebug`: PASS.
- Android 14 device smoke: phone, landscape, `sw600dp`, process restore va About PASS.

## Bang chung

- `app/build/test-results/testAppDebugUnitTest/`
- `artifacts/phase02/about-fixed-final.png`
- `artifacts/phase02/phone-workspace-final.png`
- `artifacts/phase02/tablet-workspace-final.png`
- `artifacts/phase02/phone-browser-final.png`

## Rui ro con lai

- Android instrumentation/TalkBack automation day du nen duoc chay tren device farm khi co production signing; khong co blocker cho Phase 02.
