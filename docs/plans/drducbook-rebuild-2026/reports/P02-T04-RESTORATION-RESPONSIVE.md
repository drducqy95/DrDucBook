# P02.T04 - State restoration va responsive navigation

## Ket qua

Trang thai: `DONE`

## Trien khai va contract

- Navigation 3 tiep tuc dung saveable state holder va ViewModel store entry decorators.
- Browser tab/session duoc luu ngoai Composable va phuc hoi khi process khoi dong lai.
- Pending explicit route duoc giu den khi back stack consume, tranh mat route khi Activity/Compose dang tai.
- Chinh sach responsive duoc tach thanh `resolveUseNavigationRail`: `auto` bat rail tu `sw600dp`, van ton trong `always`, `landscape`, `off`.
- Destination Workspace duoc giu khi configuration doi tu compact sang expanded.

## Verification

- `MainResponsiveNavigationTest`: 599dp bottom bar, 600dp rail va landscape preference PASS.
- `MainNavigatorTest`: cold Browser Home fallback va back stack PASS.
- `BrowserTabStoreTest`: session recreation PASS.
- Process smoke: force-stop, mo Browser khong truyen URL van phuc hoi `example.edu`.
- Rotation smoke: Browser giu Example Domain va toolbar trong landscape.
- Responsive smoke: phone `900x1600 @ 320dpi`; expanded `900x1600 @ 240dpi` (`sw600dp`).
- Configuration smoke: chon Workspace o bottom bar, doi sang `sw600dp`, Workspace van active tren rail.
- Emulator da restore ve `900x1600 @ 320dpi` sau QA.

## Bang chung

- `artifacts/phase02/browser-process-restored.png`
- `artifacts/phase02/browser-rotated-restored.png`
- `artifacts/phase02/tablet-main.png`
- `artifacts/phase02/tablet-workspace-final.png`
- `artifacts/phase02/tablet-workspace-final.xml`

## Rui ro con lai

- Predictive back animation duoc bao phu boi Navigation 3 transition hien co; gesture instrumentation day du can physical device lab neu sua animation sau nay.
