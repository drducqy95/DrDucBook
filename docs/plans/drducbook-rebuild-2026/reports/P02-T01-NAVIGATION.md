# P02.T01 - Rut gon top-level navigation

## Ket qua

Trang thai: `DONE`

## Route mapping

- Top-level moi: `home`, `bookshelf`, `explore`, `workspace`, `my`.
- Saved destination cu `browser`, `ai_agent`, `writing`, `ebook_editor`, `rss` duoc anh xa ve `workspace`.
- Thu tu cu co nhieu tool duoc collapse tai vi tri tool dau tien va khong tao Workspace trung lap.
- Route sau `MainRouteBrowser`, `MainRouteAiAgentDashboard`, `MainRouteWriting`, `MainRouteEbookEditor` va RSS routes van duoc giu.
- Bo hai shortcut Writing/Ebook trung lap khoi trang My.

## Verification

- `MainDestinationTest`: 3/3 PASS.
- `MainNavigatorTest`: back stack va Browser fallback PASS.
- Kotlin debug compile PASS.
- Focused Phase 02 suite PASS.

## Rui ro con lai

- Visual bottom bar/rail duoc kiem tra tren thiet bi tai P02.T04.
