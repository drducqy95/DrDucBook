# P00.T04 - Architecture decisions

## Ket qua

Trang thai: `DONE`

Tam ADR da khoa cac ranh gioi kho dao nguoc truoc implementation. Moi ADR co Context, Decision, Alternatives, Consequences va Rollback; index nam tai `adr/README.md`.

## ADR da chap nhan

| ADR | Quyet dinh chinh | Phase su dung |
|---|---|---|
| ADR-001 | `com.drducbook.app`, authority/callback rieng, giu `io.legado.app` compatibility island va cai song song | P01, P11 |
| ADR-002 | SourceKey v1, source bookmarks, encrypted CookieVault, mot SourceCheckEngine khong sua source config | P04, P05 |
| ADR-003 | AppearanceProfile/IconSlot/background; launcher chi bundled aliases; web appearance tach Android | P01, P03, P06 |
| ADR-004 | Workspace gom Sang tac/Ebook/Agent/RSS; Browser khong top-level va co Exit browser | P02 |
| ADR-005 | Web ports 1122/1123, pairing, 27 HTTP + 3 WS compatibility, web chi Export/Dich tu dong/background | P06 |
| ADR-006 | Custom tool JS safe Rhino, permission broker, Agent chi tao DRAFT, user approve/enable | P08 |
| ADR-007 | ResolvedMedia v1, Media3 player, direct/HLS/DASH downloader va ephemeral auth context | P09 |
| ADR-008 | HF canonical assets, Supabase Auth/Edge/Postgres/RLS/Storage, optional Drive appDataFolder snapshot | P01, P10, P11 |

## Quyet dinh cloud cuoi cung

- Firebase/Cloud Run bi loai khoi target.
- Supabase la backend cho Auth, Postgres metadata/RLS, Edge Functions va private Storage.
- Hugging Face `Drduc/Legadofork` la canonical package source; token chi o Edge secret.
- Google Drive duoc giu nhu optional snapshot target, scope duy nhat `drive.appdata`, consent tach Supabase login.
- Snapshot modes: `SUPABASE`, `GOOGLE_DRIVE`, `BOTH`; head revision doc lap va khong auto-merge khi divergence.
- Google Drive package URLs van phai migrate sang HF; Drive khong la artifact delivery fallback.

## Validation

Ngay 2026-07-29 da chay structural validation:

- 8/8 ADR co day du 5 section bat buoc va Rollback.
- 77 phase task IDs, 77 unique, 77 matrix IDs.
- 0 task thieu `Muc tieu`, `Pham vi`, `Dieu kien thong qua` hoac `Log`.
- 14 README local links, 0 broken.
- Phase 10 va task matrix cung co P10.T01-P10.T08 sau khi them Drive transport.

Google Drive official documentation xac nhan `appDataFolder` chi accessible boi app, an khoi Drive UI va dung non-sensitive scope `https://www.googleapis.com/auth/drive.appdata`. Tai lieu target duoc cap nhat sang URL Workspace Drive API hien hanh.

## Rui ro con lai

- Edge proxy/mirror threshold phai benchmark bang catalog artifact that tai P10, khong dat con so tuy y trong ADR.
- Android namespace doi nhung compatibility packages duoc giu co chu dich; P01 phai khoa dependency direction va minified corpus.
- Supabase va Drive co hai auth lifecycle; account mismatch, partial `BOTH` commit va revoke phai co integration test.
- Custom launcher bitmap khong kha thi tren Android; UI phai noi dung dung pham vi icon noi bo va bundled aliases.
