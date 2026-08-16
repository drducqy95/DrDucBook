# P02.T02 - Workspace Compose/MVI hub

## Ket qua

Trang thai: `DONE`

## Trien khai

- Tao `WorkspaceContract`, `WorkspaceViewModel` va `WorkspaceScreen` theo MVI/UDF.
- Gom Sang tac, Bien tap Ebook, Agent va Nguon RSS vao mot page duy nhat.
- Tong hop project/tac vu gan day tu domain use case va Agent gateway; Composable khong truy cap DB.
- Ho tro loading, error/retry, empty state, badge co du lieu va recent item sap xep theo thoi gian.
- Dieu huong module qua effect va callback cua Navigation 3; ViewModel duoc dang ky bang Koin.

## Verification

- `WorkspaceStateTest`: badge, thu tu recent va empty state PASS.
- Focused Phase 02 unit suite PASS.
- Debug APK build va cai dat tren Android 14 PASS.
- Phone visual QA: 4 module dung nhan, khong con badge `0`, empty state khong tran.
- Device route smoke: Workspace -> Sang tac mo dung man hinh Sang tac.

## Bang chung

- `artifacts/phase02/phone-workspace-final.png`
- `artifacts/phase02/phone-workspace-final.xml`
- `artifacts/phase02/workspace-writing-route-final.png`
- `artifacts/phase02/workspace-writing-route-final.xml`

## Rui ro con lai

- Kiem thu rail/tablet va state recreation duoc dong tai P02.T04.
