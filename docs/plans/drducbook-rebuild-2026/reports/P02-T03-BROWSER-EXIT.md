# P02.T03 - Browser route va nut Exit

## Ket qua

Trang thai: `DONE`

## Hanh vi da khoa

- Back uu tien WebView history, sau do dong tab hien tai neu con nhieu tab, cuoi cung moi pop route app.
- Nut `X` phat `ExitBrowser` va dong toan bo Browser route, khong goi `WebView.goBack()`.
- Browser khong con la top-level destination; van mo duoc tu Explore, source flow va explicit app intent.
- Cold Browser route co Home fallback trong back stack.
- Explicit route dang cho duoc giu bang `MutableStateFlow` cho den khi Navigation 3 xu ly xong.
- Browser tab/session store va cookie behavior hien co duoc giu nguyen.

## Verification

- `BrowserBackPolicyTest`: history/tab/app-route priority PASS.
- `BrowserTabStoreTest`: nhieu tab va session recreation PASS.
- `MainNavigatorTest`: Browser push/pop va cold fallback PASS.
- Focused Phase 02 test suite va debug assemble PASS.
- Device smoke Android 14: Explore -> Browser -> X -> Explore PASS.
- Warm explicit route: Settings -> Browser(example.edu) -> X -> Settings PASS.
- Cold explicit route: Home fallback + Example Domain PASS.

## Bang chung

- `artifacts/phase02/phone-browser-final.png`
- `artifacts/phase02/browser-exit-return-final.png`
- `artifacts/phase02/warm-browser-route-fixed.png`
- `artifacts/phase02/warm-browser-exit-return.png`
- `artifacts/phase02/cold-browser-route.png`

## Rui ro con lai

- Responsive rail va process/configuration recreation duoc dong tai P02.T04.
