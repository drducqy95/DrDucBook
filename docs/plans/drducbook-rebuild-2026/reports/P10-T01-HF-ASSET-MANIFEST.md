# P10.T01 - HF asset manifest va catalog inventory

## Muc tieu

Chuyen catalog package/model khoi Google Drive URL sang delivery contract Hugging Face + Supabase, tao manifest allow-list co path/version/size/SHA-256/provenance va khong dua HF token vao app/log.

## Trang thai

IN_PROGRESS. Phan in-repo da hoan thanh va da test; 35 artifact local da upload len Hugging Face dataset va verify bang authenticated remote checks. Checkpoint 2026-08-02 da giam Piper license blocker tu 29 voice xuong 4 voice, sau do quarantine 4 voice nay khoi public Android catalog va Supabase ticket path. Chua dong DONE vi:

- 4 ZIP Piper local (`indo_goreng`, `john`, `mattheo`, `mattheo1`) chua co license card du ro; manifest van danh dau `license-review-required` de audit, nhung khong con public-downloadable.
- Supabase Edge Function/secret va private dataset runtime gate van nam o P10.T02/P10.T08.

## Pham vi file da tac dong

- `app/src/main/java/io/legado/app/domain/model/ExternalAssetCatalog.kt`
- `app/src/main/java/io/legado/app/domain/model/LocalAiModelCatalog.kt`
- `app/src/test/java/io/legado/app/domain/model/HfArtifactManifestTest.kt`
- `scripts/build-hf-asset-manifest.ps1`
- `scripts/upload-hf-artifacts.ps1`
- `scripts/cleanup-hf-upload-work.ps1`
- `scripts/test-cloud-security-gates.mjs`
- `supabase/artifacts/hf-artifacts-manifest.json`
- `docs/release/piper-voice-license-review.md`
- `docs/plans/drducbook-rebuild-2026/reports/P10-T01-HF-ASSET-MANIFEST.md`
- `docs/plans/drducbook-rebuild-2026/TASK-MATRIX.md`
- `docs/plans/drducbook-rebuild-2026/PLAN-LOG.md`

## Noi dung da hoan thanh

- Them `AssetDeliveryCatalog` voi contract:
  - `hfRepository = Drduc/Legadofork`
  - `hfRevision = main`
  - `manifestVersion = 2026.07.31-p10t01`
  - URI noi bo `drducbook-asset://download/{artifactId}` va `drducbook-asset://catalog/{catalogId}`.
- Thay Drive URL trong Android catalogs bang URI noi bo:
  - Hachimi ONNX package.
  - Quick Translation clean package.
  - Valtec TTS package.
  - Piper voice catalog va tung voice package.
  - Local GGUF/Hy-MT2 catalog.
- Them `artifactId` cho TTS/local AI de manifest/server dung khoa on dinh ma khong can doi ten hien thi cu.
- Tao script manifest khong can token:
  - Scan `artifacts/drive-assets`.
  - Verify size/SHA-256 cho local ZIP.
  - Ghi JSON UTF-8 no BOM vao `supabase/artifacts/hf-artifacts-manifest.json`.
- Tao script upload Hugging Face:
  - Doc token tu Process/User/Machine environment theo thu tu: `HF_TOKEN`, `HUGGINGFACE_HUB_TOKEN`, `HF_WRITE_TOKEN`, `HUGGINGFACE_TOKEN`.
  - Clone `https://huggingface.co/datasets/Drduc/Legadofork` bang Git LFS.
  - Copy 31 artifact local verified vao dung `hfPath`.
  - Upload `manifest/hf-artifacts-manifest.json`.
  - Khong in token; token nam trong `_netrc` tam thoi trong workdir va duoc xoa sau khi chay.
  - Checkpoint 2026-08-01: default `WorkRoot` cua uploader chinh da chuyen sang `%TEMP%\drducbook-hf-upload-work` de tranh lam day o D trong workspace.
  - Checkpoint 2026-08-01 19:30: uploader set `GIT_LFS_SKIP_SMUDGE=1` khi clone HF dataset va restore env sau khi xong, tranh tai nguoc LFS cu ve may truoc khi copy artifact moi.
- Tao wrapper local ignored `.secrets/upload-hf-artifacts.with-token.local.ps1` theo yeu cau van hanh thu cong:
  - Co placeholder token o dau file de nguoi dung tu sua tren may.
  - Khong in token va chi set `HF_TOKEN` trong process con khi goi uploader chinh.
  - Guard dung ngay neu van la placeholder hoac token khong bat dau bang `hf_`.
  - Mac dinh dung `%TEMP%\drducbook-hf-upload-work` de tranh day them vao o D khi workspace gan het dung luong.
- Tao `scripts/cleanup-hf-upload-work.ps1` de don cac repo upload tam `Legadofork-*` trong upload root da biet, co guard duong dan truoc khi xoa.
- Tao manifest 35 artifact:
  - 2 translation packages local verified.
  - 29 Piper voice ZIP local verified.
    - 25 NGHI-TTS/Piper voices co license review card va manifest `Apache-2.0`.
    - 4 extra voices van `license-review-required` va `local_verified_license_pending`.
  - 1 Valtec TTS local verified.
  - 3 Hy-MT2 GGUF local verified.
- Danh dau delivery class:
  - `hf_proxy` cho 35/35 artifact, bao gom Valtec va Hy-MT2 sau khi upload thanh cong.
- Upload len Hugging Face:
  - Dataset: `https://huggingface.co/datasets/Drduc/Legadofork`
  - Branch: `main`
  - Commit: `0171747e034c3d881dc6e182a5130a5d12b20872`
  - Uploaded LFS objects: 31/31
  - Uploaded bytes theo manifest: 1,753,597,758
  - Uploaded files: `manifest/hf-artifacts-manifest.json`, 2 translation ZIP, 29 Piper ZIP.
  - Commit `1b844feb72cca5bb8b5e119f29b334ebdaddf827`: uploaded 4 LFS objects Valtec + 3 Hy-MT2, 1.5 GB.
  - Commit `adc61e3a041893fc38233e02fee3a183bde5083c`: manifest update, 35/35 delivery class `hf_proxy`.
  - Dataset hien la private; authenticated manifest va HEAD verification PASS.

## Kiem tra da chay

- `powershell -ExecutionPolicy Bypass -File scripts/build-hf-asset-manifest.ps1`
  - Ket qua: ghi `supabase/artifacts/hf-artifacts-manifest.json` voi 35 artifacts; local size/hash mismatch se fail script.
- `powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\upload-hf-artifacts.ps1 -DryRun`
  - Ket qua: 31 artifact local verified san sang upload, tong 1,753,597,758 bytes; 4 artifact metadata-only chua co source file.
- `powershell -NoProfile -ExecutionPolicy Bypass -File .\.secrets\upload-hf-artifacts.with-token.local.ps1 -DryRun`
  - Ket qua: dung dung guard placeholder khi chua sua token, khong chay upload that.
- `powershell -NoProfile -ExecutionPolicy Bypass -File .\.secrets\upload-hf-artifacts.with-token.local.ps1`
  - Ket qua: commit `0171747`; `Uploading LFS objects: 100% (31/31), 1.8 GB`; push `main -> main`.
- `powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\upload-hf-artifacts.ps1`
  - Ket qua: commit `1b844fe`; `Uploading LFS objects: 100% (4/4), 1.5 GB`; push `main -> main`.
- `powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\upload-hf-artifacts.ps1`
  - Ket qua: commit `adc61e3`; manifest-only update; push `main -> main`.
- `try { .\scripts\upload-hf-artifacts.ps1 } catch { $_.Exception.Message }`
  - Ket qua: dung dung gate token khi khong co HF token trong environment.
- `Invoke-WebRequest https://huggingface.co/datasets/Drduc/Legadofork/raw/main/manifest/hf-artifacts-manifest.json`
  - Ket qua: HTTP 200; repository `Drduc/Legadofork`; manifest version `2026.07.31-p10t01`; 35 artifacts; 31 local-ready; 4 metadata-only.
- `git ls-remote https://huggingface.co/datasets/Drduc/Legadofork refs/heads/main`
  - Ket qua: `0171747e034c3d881dc6e182a5130a5d12b20872`.
- Authenticated HF API `https://huggingface.co/api/datasets/Drduc/Legadofork/revision/main`
  - Ket qua: `sha` = `adc61e3a041893fc38233e02fee3a183bde5083c`; `private` = true; `siblings` = 38.
- Authenticated HF manifest raw
  - Ket qua: artifactCount 35, `hf_proxy` 35, `storage_mirror_required` 0, `metadata_only_pending_source` 0.
- Authenticated HEAD cho 4 artifact lon
  - Ket qua: HTTP 200; Content-Length khop manifest cho 3 Hy-MT2 GGUF va Valtec ZIP.
- `curl.exe -I -L https://huggingface.co/datasets/Drduc/Legadofork/resolve/main/packages/translation/legado-qt-clean-20260721.zip`
  - Ket qua: final HTTP 200; `Content-Length: 575677`; `X-Repo-Commit: 0171747e034c3d881dc6e182a5130a5d12b20872`.
- `curl.exe -I -L https://huggingface.co/datasets/Drduc/Legadofork/resolve/main/packages/tts/piper/legado-tts-piper-yannew-20260721.zip`
  - Ket qua: final HTTP 200; `Content-Length: 58447476`; `X-Repo-Commit: 0171747e034c3d881dc6e182a5130a5d12b20872`.
- `powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\cleanup-hf-upload-work.ps1`
  - Ket qua: don xong cac upload repo tam; lan xac nhan sau do `removedCount: 0`; o D con khoang 4.2GB, sau cac build tiep theo con khoang 6.2GB.
- `.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.domain.model.HfArtifactManifestTest" --no-daemon --console=plain`
  - Ket qua: BUILD SUCCESSFUL; 2 tests PASS.
- `node --test scripts/test-asset-ticket.mjs scripts/test-cloud-sync-migration.mjs scripts/test-cloud-security-gates.mjs`
  - Ket qua checkpoint 2026-08-02: 14 tests PASS, bao gom gate Piper license review chi clear 25 voice audited va giu 4 voice pending.
- `.\gradlew.bat :app:compileAppDebugKotlin --no-daemon --console=plain`
  - Ket qua: BUILD SUCCESSFUL.
- `rg -n "drive\.google\.com|Google Drive/Legado System Assets" app/src/main/java app/src/test/java supabase scripts`
  - Ket qua: khong con Drive URL trong main catalog; chi con chuoi phu dinh trong test.

## Dieu kien da dat

- 100% catalog asset hien co trong Android co manifest entry: 35/35.
- Android catalog khong con package Google Drive URL.
- Hash/size cua 31 local ZIP duoc verify bang `Get-FileHash`.
- HF token khong xuat hien trong file da tao.
- Hash/size cua Valtec ZIP va 3 Hy-MT2 GGUF duoc verify bang `Get-FileHash`.
- 35 artifact local da upload len HF dataset; manifest remote dang `hf_proxy` 35/35.
- HF token khong duoc ghi vao uploader chinh, app source, manifest hay report; token local trong wrapper ignored da duoc scrub ve placeholder sau upload.
- Piper license review checkpoint 2026-08-02:
  - 25/29 Piper voices duoc gan `Apache-2.0` theo `docs/release/piper-voice-license-review.md`.
  - 4/29 Piper voices con `license-review-required`: `indo_goreng`, `john`, `mattheo`, `mattheo1`.
  - `asset-ticket` guard tiep tuc chan artifact co `inventoryState` chua `license_pending`.
  - Checkpoint 2026-08-02 07:57: 4 voice pending duoc giu trong manifest de audit nhung bi an khoi `releaseEligibleTtsVoiceCatalog`, khong resolve duoc qua public Android asset resolver, va Supabase ticket function tra `451 license_review_required`.
- Checkpoint 2026-08-01 19:26: `scripts/build-hf-asset-manifest.ps1` tu nhan local source cho Valtec/Hy-MT2 neu ton tai; manifest hien khong con `metadata_only_pending_source`.
- Dry-run uploader hien bao 35 artifact local-ready, `readyBytes` 3,298,972,546 va `metadataOnlyCount` 0.
- Remote authenticated verify hien bao `storage_mirror_required` 0 va `metadata_only_pending_source` 0.

## Dieu kien chua dat de dong DONE

- Chua co license card hop le cho 4 voice Piper: `indo_goreng`, `john`, `mattheo`, `mattheo1`; manifest dang giu audit entry `license-review-required`, public catalog/ticket da chan chung.
- Chua cau hinh Supabase secret/Edge Function runtime de dung HF dataset private qua backend proxy.

## Buoc tiep theo

- Dinh kem license/provenance card cho 4 Piper voice con lai neu muon mo lai chung trong public catalog.
- Cai dat/verify Supabase Edge Function secret cho HF token moi da rotate, sau do chuyen dataset sang private va chay runtime ticket/download gates.

## Checkpoint 2026-08-01 19:26

- Tim thay 3 Hy-MT2 GGUF trong `.codex-tmp/models` va 1 Valtec ZIP trong `.codex-tmp/release-assets`.
- Hash doi chieu khop manifest:
  - `Hy-MT2-1.8B-1.25bit-original.gguf`: `cc497fe8f033b52b3b8b00a7669e9661435432f9d4cd43f7ed24400c01507a93`.
  - `Hy-MT2-1.8B-1.25bit-v2.gguf`: `13a33fc4f72d5c92c439a65fd343696de4ccd0485bca84de2712bc0d8cc4e773`.
  - `Hy-MT2-1.8B-1.25bit-v2-stq42.gguf`: `dca0302d5bd54f70e90287332e4169305ca3602d1052c6480d49b732fcccefbc`.
  - `legado-tts-valtec-vietnamese-20260721.zip`: `c7ae93f15ec2aa39b7e9f6e4ca520c9c86298b7183c5b8c99ea6eb768eeeb77e`.
- Cap nhat `scripts/build-hf-asset-manifest.ps1` de neu cac file nay ton tai thi ghi `local_verified_upload_pending` va `localSource` tuong ung.
- Cap nhat `scripts/upload-hf-artifacts.ps1` de default upload workdir nam o `%TEMP%\drducbook-hf-upload-work`, thay vi `artifacts/hf-upload-work` trong workspace.
- Chay `powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\build-hf-asset-manifest.ps1`: PASS, 35 artifacts.
- Chay `powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\upload-hf-artifacts.ps1 -DryRun`: PASS, readyCount 35, metadataOnlyCount 0.
- Chay `node --test scripts/test-asset-ticket.mjs scripts/test-cloud-sync-migration.mjs scripts/test-cloud-security-gates.mjs`: PASS 13/13.

## Checkpoint 2026-08-01 19:30

- Cap nhat `scripts/upload-hf-artifacts.ps1` de clone HF dataset voi `GIT_LFS_SKIP_SMUDGE=1`; bien moi truong duoc restore trong `finally`.
- Muc tieu: upload that sau nay khong tu tai nguoc 31 LFS object cu ve workdir tam truoc khi copy 35 artifact local.
- Chay `powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\upload-hf-artifacts.ps1 -DryRun`: PASS, readyCount 35, metadataOnlyCount 0.
- Chay `node --test scripts/test-asset-ticket.mjs scripts/test-cloud-security-gates.mjs`: PASS 9/9.

## Checkpoint 2026-08-01 20:51

- Chay upload that bang `scripts/upload-hf-artifacts.ps1` voi token tu environment, khong in token.
- HF commit `1b844feb72cca5bb8b5e119f29b334ebdaddf827`: uploaded 4 LFS objects, 1.5 GB:
  - `models/local-ai/hy-mt2/Hy-MT2-1.8B-1.25bit-original.gguf`
  - `models/local-ai/hy-mt2/Hy-MT2-1.8B-1.25bit-v2.gguf`
  - `models/local-ai/hy-mt2/Hy-MT2-1.8B-1.25bit-v2-stq42.gguf`
  - `packages/tts/valtec/legado-tts-valtec-vietnamese-20260721.zip`
- Cap nhat manifest de 4 artifact lon dung `hf_proxy` va `local_verified`, khong con `storage_mirror_required`.
- HF commit `adc61e3a041893fc38233e02fee3a183bde5083c`: manifest update only.
- Authenticated remote verify PASS:
  - dataset `private: true`;
  - artifactCount 35;
  - `hf_proxy` 35;
  - `storage_mirror_required` 0;
  - `metadata_only_pending_source` 0;
  - 4 artifact lon HEAD HTTP 200 va Content-Length khop manifest.
- Cleanup temp upload workdir PASS, removed ~6.6 GB moi lan clone tam.

## Checkpoint 2026-08-02 01:20 - Piper license review gate

- Them `docs/release/piper-voice-license-review.md` lam license review card.
- Nguon doi chieu:
  - `https://huggingface.co/doof-ferb/nghitts-copy`
  - `https://huggingface.co/doof-ferb/nghitts-copy/tree/main/piper-tts`
  - `https://huggingface.co/doof-ferb/nghitts-copy/commit/0370fc001c63c28166ecd1df7fb394b6baab048c`
  - `https://huggingface.co/jimmyvu/viPiper/commit/a164a9f57defb94c6f547782dcfe3dcb85d1cfe5`
- Cap nhat `scripts/build-hf-asset-manifest.ps1` de clear 25 voice co trong NGHI-TTS Apache-2.0 mirror va giu 4 voice chua du license.
- Regenerate `supabase/artifacts/hf-artifacts-manifest.json`.
- Ket qua manifest local:
  - Piper `Apache-2.0`: 25.
  - Piper `license-review-required`: 4.
  - `local_verified`: 31.
  - `local_verified_license_pending`: 4.
- Them test `Piper voice license review only clears audited Apache voices` vao cloud security gate.
- Xac minh:
  - `node --test scripts/test-asset-ticket.mjs scripts/test-cloud-sync-migration.mjs scripts/test-cloud-security-gates.mjs`: PASS 14/14.
  - `.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.domain.model.HfArtifactManifestTest" --console=plain --no-daemon`: PASS.

## Checkpoint 2026-08-02 07:57 - Piper pending voice quarantine

- Them `releaseEligible` vao `ExternalTtsVoiceAsset`; 4 voice pending (`indo_goreng`, `john`, `mattheo`, `mattheo1`) dat `releaseEligible = false`.
- Public app lists (`ReadConfigScreen`, `ReadAloudConfigSheet`) va `AssetDeliveryCatalogResolver` chi dung `releaseEligibleTtsVoiceCatalog`.
- Direct URI `drducbook-asset://download/tts-piper-{pending}` bi reject o app resolver; `asset-ticket` tiep tuc tra `451 license_review_required` server-side.
- Manifest van giu 35 artifact va 4 entry pending de audit hash/provenance.
- Xac minh:
  - `node --test scripts/test-asset-ticket.mjs scripts/test-cloud-security-gates.mjs`: PASS 11/11.
  - `AssetDeliveryCatalogResolverTest`, `HfArtifactManifestTest`, `AssetDeliveryClientContractTest`: PASS, tong 13 tests.
  - `:app:compileAppDebugKotlin`: PASS.
