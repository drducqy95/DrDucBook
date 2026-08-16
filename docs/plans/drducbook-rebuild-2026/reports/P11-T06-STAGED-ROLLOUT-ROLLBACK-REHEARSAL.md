# P11.T06 - Staged rollout va rollback rehearsal

Status: IN_PROGRESS - signed release ready, rollout still blocked by external metadata/runtime evidence

## Muc tieu

Khoa quy trinh phat hanh theo dot va rollback cho DrDucBook truoc khi dua len kenh production: xac nhan artifact hien tai, co APK rollback/triage, co cac cong tac tat tinh nang rui ro, co runbook dung rollout, va co bang chung cac gate cloud/domain/release metadata da san sang.

## Pham vi

- Android build identity va version release/debug/noR8.
- APK artifacts, checksum va ABI split.
- Feature flags de tat nhanh tinh nang rui ro.
- Web Service policy gate: Export, Dich tu dong, background Web Service.
- Supabase + HuggingFace asset delivery controls.
- Dieu kien rollout staged va rollback Play/internal testing.
- Khong thuc hien rollout that trong checkpoint nay.

## Bang chung tu verifier

Script moi:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\release\verify-rollout-rollback.ps1
```

Ket qua da ghi vao:

```text
docs\plans\drducbook-rebuild-2026\reports\artifacts\P11-T06-ROLLBACK-REHEARSAL.json
```

Tom tat checkpoint moi 2026-08-02 11:20:

- Version: `3.26.13`.
- Default `versionCode`: `32640`.
- Release package: `com.drducbook.app`.
- Debug package tach song song: `.debug`.
- ABI split: `armeabi-v7a`, `arm64-v8a`, `x86_64`, co universal APK.
- Build type `release`: minify/shrink bat.
- Build type `noR8`: da co artifact dung cho rollback/triage noi bo.
- APK hien co: 12 file trong output.
- Debug APK hop le: 4/4.
- noR8 APK hop le: 4/4, dung duoc lam artifact triage/rollback noi bo.
- Release APK hop le ve zip: 4/4; invalid release = 0.
- Release APK signed: 0/4; tat ca artifact release hien tai la `unsigned`.
- HF manifest: 35 artifacts, repo `Drduc/Legadofork`, revision `main`.
- HF delivery: 35 `hf_proxy`, 0 `storage_mirror_required`, 4 `local_verified_license_pending` retained for audit but not public-downloadable.
- Piper pending guard: `licensePendingTicketBlocked = true`, `publicPiperCatalogFiltered = true`.
- HF remote checkpoint: private dataset revision `adc61e3a041893fc38233e02fee3a183bde5083c`, artifactCount 35, metadata-only 0.
- Supabase ticket/download functions ton tai; HF token chi nam o download function; ticket issuer co auth; download co consume-ticket va Range support.

Debug APK checksum hien tai:

| Artifact | SHA-256 |
|---|---|
| `app-app-arm64-v8a-debug.apk` | `d492218dfe24673552891f3481360d79b820e5b67a91a688f994ddcfaf7911eb` |
| `app-app-armeabi-v7a-debug.apk` | `b7887c94494022262a42a0764afe05f677e3dabfa32280abdfda3343d4414493` |
| `app-app-x86_64-debug.apk` | `7be9a60da06a33e26c2f9ab73abb1ffc4a6b871215b546f301dabad6f0cd95d1` |
| `app-app-universal-debug.apk` | `6f6fc9cf11b87fc698391dd00fa4b73303bfd7aabf5474cf721afce204b65643` |

noR8 APK checksum hien tai:

| Artifact | ZIP | SHA-256 |
|---|---:|---|
| `app-app-arm64-v8a-noR8-unsigned.apk` | OK | `a3f3a25ec8385c7f7b00d117e228939cb818ac8fc876fcdd8645113ea831084d` |
| `app-app-armeabi-v7a-noR8-unsigned.apk` | OK | `51d54a4e7e8c9513d7e44621cbc41d686b8ba1d35d74c7a9307a3297180ac439` |
| `app-app-x86_64-noR8-unsigned.apk` | OK | `efa1bf08611628a692928c66ab716a739294fb79a3277f9cbde6514abdcfc38c` |
| `app-app-universal-noR8-unsigned.apk` | OK | `fd481f1cd1b32cdf7c6b659d35b50c842c932adc8c9704cabab94aa9a190924b` |

Release APK checkpoint hien tai:

| Artifact | ZIP | Entries | Signed | SHA-256 |
|---|---:|---:|---:|---|
| `app-app-arm64-v8a-release-unsigned.apk` | OK | 1876 | No | `ab79edf860bd507c001162a9a4401b0e73a1b9755a374c61aa8418596cfcc7be` |
| `app-app-armeabi-v7a-release-unsigned.apk` | OK | 1863 | No | `2d59bf64de99bd67fb713392d4abd018bdee1452cd6342b0f74a33a52126b9d7` |
| `app-app-x86_64-release-unsigned.apk` | OK | 1883 | No | `2518dfa71225886bf8f6d635b7043c0830f90df2c6fdef98ff8644d1573f36b4` |
| `app-app-universal-release-unsigned.apk` | OK | 1908 | No | `734fa131f8d5ed5e9b0d0a054a4d0c880433ec168ead3ebb36b1caa6333f7a50` |

## Cong tac tat nhanh hien co

- Agent mutation: `FeatureFlags.agentMutation`, mac dinh bat de chatbot co WRITE tool; co the tat khi rollback loi Agent/tool.
- Agent skill: `FeatureFlags.agentSkill`, mac dinh bat de chatbot doc/tao skill; co the tat khi rollback loi skill.
- Agent plugin: `FeatureFlags.agentPlugin`, mac dinh bat de chatbot tao/cai plugin co approval; co the tat khi rollback loi plugin.
- Manga translation: `FeatureFlags.mangaTranslation`, mac dinh tat.
- Browser page translation: `FeatureFlags.browserPageTranslation`, mac dinh bat.
- Source daily health: `FeatureFlags.sourceDailyHealth`, mac dinh bat.
- Media download: `FeatureFlags.mediaDownload`, mac dinh bat.
- Chat bubble: `FeatureFlags.chatBubble`, mac dinh bat.
- Fixed layout ebook: `FeatureFlags.ebookFixedLayout`, mac dinh bat.
- Web Service Export: policy default tat va server chan route khi tat.
- Web Service auto translation: policy default tat va server chan job khi tat.
- Web Service background: nguoi dung chi doi background/fit/position, khong mo rong thanh quyen he thong.

## Ke hoach rollout

Stage 0 - Internal debug/device smoke:

- Cai debug APK dung ABI thiet bi/emulator.
- Mo app, bookshelf, explore, reader truyen chu, reader truyen tranh, video player, media download.
- Kiem tra VBook/Legado import, chapter list, chapter content, manga image reader.
- Kiem tra AI chat/tool list/model fallback, AI translation pipeline moi, source health.
- Kiem tra Supabase Auth neu co config runtime, Google Drive backup neu co consent/client runtime.
- Neu co crash/debug blocker: dung rollout va quay lai P11.T04/P11.T03/P11.T08 tuy module loi.

Stage 1 - Internal/closed testing release:

- Build release APK/AAB bang signing key dung kenh test.
- Luu checksum, mapping/R8 output va artifact noR8 cung version.
- Cai len thiet bi Android 12/14, ABI arm64 va x86_64 neu co emulator.
- Theo doi crash, ANR, OOM, source error rate, HF ticket error rate, media playback/download error.

Stage 2 - 1% rollout:

- Chi mo khi Stage 1 pass va P11.T01-P11.T05 khong con blocker.
- Giu 24 gio hoac toi thieu 100 phien su dung that.
- Dung neu crash-free session thap hon 99.5%, ANR tang bat thuong, hoac loi mo sach/video/import vuot nguong.

Stage 3 - 5% / 25% / 50%:

- Moi muc giu toi thieu 24 gio.
- Chi tang khi khong co crash moi lap lai, Supabase/HF ticket on dinh, Drive backup khong xin sai scope, VBook/Legado corpus khong co regression nghiem trong.
- Neu mot module rui ro loi nhung app van doc duoc sach, tat feature flag tuong ung va giu stage thay vi tang.

Stage 4 - 100%:

- Chi mo khi co Play Console evidence, release metadata/pricing/privacy/support URL pass, va rollback artifact san sang.
- Sau khi 100%, giu noR8/mapping va previous stable artifact toi thieu mot chu ky release.

## Runbook rollback

Rollback cap 1 - Tat tinh nang tu xa/noi bo neu chua can thay APK:

- Tat `agentMutation`, `agentSkill`, `agentPlugin` neu loi Agent/tool/custom tool.
- Tat `mangaTranslation` neu loi reader truyen tranh/dich trang anh.
- Tat `browserPageTranslation` neu loi pipeline dich trang web/AI parser.
- Tat `sourceDailyHealth` neu worker/probe gay ton pin/crash.
- Tat `mediaDownload` neu loi download video/HLS/DASH.
- Tat Web Service `exportEnabled` hoac `autoTranslationEnabled` neu route web gay loi hoac ro ri du lieu.

Rollback cap 2 - Dung rollout:

- Tam dung rollout tren Play/Internal track.
- Giu version loi o stage hien tai, khong tang ty le.
- Luu crash file, logcat, Play vitals va checksum artifact.
- Dung noR8 APK cung version de triage neu crash can stack ro hon.

Rollback cap 3 - Quay ve ban on dinh:

- Phat hanh previous stable version voi `versionCode` cao hon neu Play yeu cau.
- Khong thuc hien migration pha huy DB; migration moi phai forward-compatible.
- Khong xoa du lieu local/Drive/Supabase khi rollback.
- Neu loi lien quan HF token/Supabase function, rotate `HF_READ_TOKEN`, `ASSET_TICKET_SECRET`, tam tat/undeploy function download neu can.

## Blocker hien tai

- Blocker het dung luong/partial release da duoc giai quyet: release APK ZIP hien 4/4 hop le, invalid release = 0 theo verifier 2026-08-02 11:20.
- Release output đã có 4 APK ABI và 1 AAB được ký production; APK đã xác minh v2/v3, AAB đã xác minh bằng `jarsigner`.
- Certificate fingerprint đã được ghi trong release signing checkpoint; không ghi private key hoặc mật khẩu vào repository.
- Signed release artifact đã sẵn sàng; production rollout vẫn chờ domain/metadata, rollback artifact và runtime gates.
- Chua co previous stable rollback artifact duoc xac dinh.
- P11.T05 van `IN_PROGRESS`: thieu domain HTTPS, `assetlinks.json`, privacy/support/release URLs, Google/Supabase dashboard evidence.
- P10 runtime gates van `IN_PROGRESS`: Supabase/Auth/Drive runtime chua co bang chung day du.
- P11.T01-P11.T05 van co predecessor `IN_PROGRESS`, nen P11.T06 chua the DONE.
- HF manifest khong con metadata-only/storage mirror blocker. 4 Piper voice license pending van duoc giu trong manifest de audit nhung da bi an khoi public catalog va bi chan ticket 451; con runtime Supabase secret/Edge Function gate.

## Dieu kien thong qua

- Build release/noR8 thanh cong cho version hien tai, luu checksum va artifact paths.
- Co previous stable APK/AAB hoac release candidate rollback duoc xac dinh.
- Co Play/Internal track rollout rehearsal evidence hoac local release rehearsal duoc chap nhan.
- Co bang chung tat duoc feature flags/policy gates lien quan.
- Supabase asset-ticket/asset-download da deploy, co secret runtime, co test ticket/download/range that hoac emulator contract duoc chap nhan.
- P11.T05 khong con blocker domain/privacy/support/release metadata.
- Co rollback dry-run log: dung rollout, chon previous stable/noR8, va danh sach buoc khoi phuc data.

## Nhat ky

2026-08-01 17:10 - STARTED. Tao verifier `scripts/release/verify-rollout-rollback.ps1`, chay tren artifact hien tai va sinh JSON evidence. Ket qua: cau hinh release/feature/web/Supabase controls co nen tang dung, nhung production rollout dang bi chan do thieu release/noR8 artifacts va cac gate external P10/P11 con pending.

2026-08-01 18:06 - CHECKPOINT. Build `:app:assembleAppNoR8` PASS va sinh 4 noR8 APK hop le. Build `:app:assembleAppRelease` that bai do het dung luong dia o buoc package; verifier da duoc nang cap de bat APK invalid/cat cut va release unsigned. Production rollout van BLOCKED, nhung noR8 triage artifact da san sang.

2026-08-01 19:13 - RETRY. Don cache build trung gian (`app/build/intermediates`, `app/build/kotlin`, `app/build/kspCaches`, `app/.cxx`, root `build`) va giai phong `D:` len khoang 6.7 GB. Retry `:app:assembleAppRelease` cham timeout 30 phut, khong sinh release output moi. Da dung Gradle daemon, khong con Java process nen khong co build treo.

2026-08-01 20:51 - HF CHECKPOINT. Sau P10.T01 upload/update, verifier P11.T06 chay lai va xac nhan HF manifest local co `hf_proxy` 35, `storage_mirror_required` 0, `metadata_only_pending_source` 0. Rollout van BLOCKED boi release APK partial/unsigned, P10 runtime gates va P11.T05 domain/public metadata.

2026-08-02 01:24 - PIPER LICENSE CHECKPOINT. Sau P10.T01 license review, verifier P11.T06 chay lai va xac nhan HF manifest local co `local_verified` 31 va `local_verified_license_pending` 4. Tai checkpoint nay rollout van BLOCKED boi release APK partial/unsigned, P10 runtime gates, 4 Piper voice license pending va P11.T05 domain/public metadata.

2026-08-02 07:57 - PIPER QUARANTINE CHECKPOINT. 4 Piper voice pending (`indo_goreng`, `john`, `mattheo`, `mattheo1`) van o manifest voi `license-review-required` de audit, nhung Android public catalog/resolver chi expose `releaseEligibleTtsVoiceCatalog` va Supabase `asset-ticket` chan bang `451 license_review_required`. Verifier P11.T06 xac nhan `licensePendingTicketBlocked = true`, `publicPiperCatalogFiltered = true`. Rollout van BLOCKED boi release APK partial/unsigned, P10 runtime gates va P11.T05 domain/public metadata.

2026-08-02 11:20 - RELEASE ZIP RECOVERY CHECKPOINT. Sau khi don cac heap dump lon va build release tiep tuc hoan tat, verifier P11.T06 chay lai xac nhan APKs total=12, valid debug=4, valid noR8=4, valid release=4, invalid release=0. Rollout van BLOCKED boi release unsigned=4, P10 runtime gates va P11.T05 domain/public metadata; blocker partial/disk-full khong con la blocker hien tai.

## Release signing checkpoint — 2026-08-04

- Production keystore đã được nạp qua các biến `ORG_GRADLE_PROJECT_RELEASE_*` của môi trường; không ghi mật khẩu hoặc private key vào repository.
- `:app:assembleAppRelease` thành công sau 22 phút 40 giây; bốn APK release đã ký.
- `:app:bundleAppRelease -PdisableAbiSplits=true` thành công sau 17 phút 10 giây; AAB release đã ký. Cờ này chỉ tắt ABI APK splits cho task bundle, không thay đổi APK split mặc định.
- APK signature verification: cả bốn APK đều `v2=true`, `v3=true`; certificate SHA-256:
  `5deac3fa21ff41c7319041ae2e4e3c1f2ce71e3c31c34113f218411d88872803`.
- APK SHA-256:
  - arm64-v8a: `257372DA43986A92BA9048D0334EFC6A6F1E5B52269EEDE1C2199DB7824C20C8`
  - armeabi-v7a: `82877D87CBC99F9B238EAAC0FFCAD1BE3EDDD4DDF73CDE0A7216B4FAE66F0F2B`
  - x86_64: `EE7F86C10040948025A200870066D80C09F34DC7E84BF4B48B21DAB5608C218D`
  - universal: `F8A2A523554BDD1734FAD26F9353C5086DE42A208E953C233342470608CDF743`
- AAB SHA-256: `954D17C8FA0FFC5BEFAD02509092812EDF89F2CE70AD3EC7ED574112FC314939`.
- Remaining rollout blockers are external: domain/assetlinks/privacy/support metadata, previous stable rollback artifact and P10 authenticated runtime gates.
