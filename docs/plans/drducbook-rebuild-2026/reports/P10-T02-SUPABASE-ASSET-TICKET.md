# P10.T02 - Supabase asset ticket va HF proxy

## Muc tieu

Cap ticket download ngan han cho artifact trong HF manifest, khong lo HF token cho Android/WebService/Agent, co allow-list, Range, replay guard va migration DB cho ticket mot lan.

## Trang thai

IN_PROGRESS. Function code, migration va test logic da co; HF dataset private upload da verify o P10.T01 voi 35/35 artifact qua `hf_proxy`. Chua dong DONE vi may hien khong co Deno/Supabase CLI de chay/deploy local stack, va Supabase project secret/runtime chua duoc cau hinh de verify private fetch thuc te qua Edge Function.

## Pham vi file da tac dong

- `supabase/config.toml`
- `supabase/functions/_shared/asset_ticket.mjs`
- `supabase/functions/_shared/http.mjs`
- `supabase/functions/asset-ticket/index.ts`
- `supabase/functions/asset-download/index.ts`
- `supabase/migrations/20260731044500_artifact_tickets.sql`
- `scripts/test-asset-ticket.mjs`
- `docs/plans/drducbook-rebuild-2026/reports/P10-T02-SUPABASE-ASSET-TICKET.md`
- `docs/plans/drducbook-rebuild-2026/TASK-MATRIX.md`
- `docs/plans/drducbook-rebuild-2026/PLAN-LOG.md`

## Noi dung da hoan thanh

- Scaffold Supabase functions:
  - `asset-ticket`: POST, JWT subject lay tu Authorization header da duoc Supabase gateway verify, allow-list artifact tu manifest, TTL 10 phut, rate limit 30 ticket/phut/user, persist hash ticket vao DB.
  - `asset-download`: GET/HEAD, nhan ticket qua query/header, verify HMAC, match artifact, consume one-time ticket qua RPC, proxy HF `HEAD/GET/Range`, tra hash/size headers cho app verify.
- Them shared logic:
  - Artifact ID normalization.
  - Manifest allow-list validation.
  - HF resolve URL builder chi dung `Drduc/Legadofork` + path trong manifest.
  - Single Range parser, reject multi-range/unsatisfiable range.
  - HMAC signed ticket, expiry, artifact scope, SHA-256 ticket hash.
  - CORS headers khong expose secret.
- Them migration:
  - `artifact_tickets` table.
  - RLS enabled va revoke client direct access.
  - `consume_artifact_ticket()` security definer RPC, grant execute cho `service_role`.
- Function khong hard-code secret:
  - `ASSET_TICKET_SECRET`
  - `SUPABASE_URL`
  - `SUPABASE_SERVICE_ROLE_KEY`
  - `HF_READ_TOKEN`
- Artifact co `inventoryState` chua license pending bi chan khi cap ticket.
- Manifest hien khong con `storage_mirror_required`; `asset-download` van giu nhanh 409 nhu guard neu manifest tuong lai can mirror/signed URL.

## Kiem tra da chay

- `node --test scripts/test-asset-ticket.mjs`
  - Ket qua: 3 tests PASS.
- `node --check supabase/functions/_shared/asset_ticket.mjs`
  - Ket qua: PASS.
- `node --check supabase/functions/_shared/http.mjs`
  - Ket qua: PASS.
- Secret scan:
  - `rg -n "hf_[A-Za-z0-9]{30,}|SUPABASE_SERVICE_ROLE_KEY\\s*=|HF_READ_TOKEN\\s*=|ASSET_TICKET_SECRET\\s*=" supabase scripts app/src/main/java app/src/test/java docs/plans/drducbook-rebuild-2026 -S`
  - Ket qua: khong co match.
- HF dataset live verify tu P10.T01:
  - Manifest raw HTTP 200 tai `https://huggingface.co/datasets/Drduc/Legadofork/raw/main/manifest/hf-artifacts-manifest.json`.
  - Commit `0171747e034c3d881dc6e182a5130a5d12b20872`.
  - Sample resolve URLs cho translation ZIP va Piper ZIP deu final HTTP 200 qua CDN.
- HF private dataset checkpoint 2026-08-01:
  - Authenticated HF API revision `main`: `adc61e3a041893fc38233e02fee3a183bde5083c`, `private: true`.
  - Authenticated remote manifest: artifactCount 35, `hf_proxy` 35, `storage_mirror_required` 0, `metadata_only_pending_source` 0.
  - Authenticated HEAD cho 3 Hy-MT2 GGUF va Valtec ZIP: HTTP 200, Content-Length khop manifest.
- Runtime tooling check:
  - `supabase`: khong co trong PATH.
  - `deno`: khong co trong PATH.
  - `node`, `npm`, `npx`: co trong PATH.

## Dieu kien da dat

- Anonymous/no user bi chan trong `asset-ticket` logic.
- Artifact unknown/path traversal bi reject bang manifest allow-list.
- Ticket signed, scoped theo artifact, co expiry va hash de persist ma khong luu raw ticket.
- Ticket replay duoc chan bang RPC consume mot lan trong DB contract.
- Range single/suffix/default end duoc normalize; multi-range bi reject.
- HF token chi doc tu Edge secret `HF_READ_TOKEN`, khong co trong app/source/report.

## Dieu kien chua dat de dong DONE

- Chua chay `supabase start`, migration apply, function serve/deploy vi CLI khong co trong PATH.
- Chua test Supabase JWT expiry/revoke thuc te.
- Chua verify HF `HEAD/GET/Range` qua Edge Function voi dataset private vi chua cau hinh Supabase secret/runtime.
- Chua co benchmark Edge runtime size/time cho piper/Hachimi proxy.

## Buoc tiep theo

- Cai/chay Supabase CLI local stack hoac deploy vao project `faegbafmkpsocoecrhvz`, apply migration va serve/deploy functions.
- Dua token HF moi da rotate vao Supabase secret `HF_READ_TOKEN`, khong dua vao source/log.
- Them integration tests cho JWT, replay, range, HF fetch va mirror.
- Noi app downloader P10.T03 vao `asset-ticket`/`asset-download` contract.
