# P08.T03 - Custom JS tool manifest/runtime

## Muc tieu

Cho Agent tao duoc custom tool JavaScript co manifest/schema ro rang, nhung runtime khong co raw Android/Java access, khong doc secret/cookie/file va khong tu cap quyen.

## Ket qua trien khai

- Them custom tool domain contract:
  - `CustomAgentToolManifest`
  - `CustomAgentToolValidationResult`
  - `CustomAgentToolExecutionRequest/Response/Result`
  - `CustomAgentToolNetworkBridge`
- Them manifest parser/validator:
  - `schemaVersion=1`
  - `id` bat buoc prefix `custom_`, semver `version`, name/description/script bat buoc.
  - `inputSchema` root phai la object va `additionalProperties=false`.
  - `outputSchema` duoc validate va runtime phai validate output truoc khi tra ve Agent.
  - `capabilities` v1 chi ho tro `READ` va `NETWORK`; `WRITE`, `FILE`, `SOURCE`, `AUTHORING` chua duoc custom JS bridge mo.
  - `allowedDomains` bat buoc neu co `NETWORK`, reject localhost/private/local IP literal.
  - `checksum` neu khai bao phai khop SHA-256 cua script; neu khong khai bao runtime manifest tu tinh `sha256:*`.
- Them Rhino runtime:
  - Script contract: `function execute(input, context)`.
  - Reuse `SafeContextFactory`/`SafeContext` cua VBook sandbox.
  - Timeout theo manifest, toi da 20 giay.
  - Global nguy hiem bi chan: `eval`, `Function`, `load`, `require`, `Packages`, `java`, `javax`, `android`, `kotlin`, `XMLHttpRequest`, global `fetch`, storage browser.
  - Khong truyen Android Context, filesystem, CookieStore, Supabase/Drive session, HF token hay credential vao scope.
  - `console.log`/`context.log` bi drop trong v1 de khong luu input/output nhay cam.
- Them network bridge allow-list:
  - Chi co `context.fetch(url, options)`, khong co global `fetch`.
  - Chi HTTP(S), allow-list domain, block localhost/private DNS/IP, block sensitive headers `Authorization`, `Cookie`, `Set-Cookie`, `Proxy-Authorization`.
  - Method v1: `GET`, `HEAD`, `POST`.
  - Response body gioi han 5 MiB dang char; timeout network khong vuot manifest timeout va toi da 20 giay.

## Sandbox limits

| Hang muc | Gioi han |
|---|---|
| Manifest size | 250,000 chars |
| Script size | 120,000 chars |
| Timeout | 50-20,000 ms |
| Output | 2-200,000 chars |
| Network response | 5 MiB chars |
| Allowed domains | Toi da 32 |
| Schema depth | Toi da 6 |
| Object properties | Toi da 64 moi object |

## Threat/regression coverage

- Valid deterministic JS tool tra output JSON dung schema.
- Invalid schema va forbidden script tra loi co `field` va `line`.
- Infinite loop bi timeout.
- Rhino/Java/Android/process/reflection APIs bi static validator/runtime chan.
- Path traversal, Windows absolute path va secret literal bi chan.
- Oversized output bi chan truoc khi tra ve Agent.
- Network call khong co `NETWORK` capability bi chan.
- Network co capability phai qua allow-list domain va fake bridge.
- Local/private network target va sensitive headers bi chan.

## File thay doi chinh

- `app/src/main/java/io/legado/app/domain/agenttools/CustomAgentToolModels.kt`
- `app/src/main/java/io/legado/app/domain/agenttools/CustomAgentToolManifestParser.kt`
- `app/src/main/java/io/legado/app/data/agenttools/CustomAgentToolRuntime.kt`
- `app/src/test/java/io/legado/app/domain/agenttools/CustomAgentToolManifestRuntimeTest.kt`

## Lenh kiem tra

```text
.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.domain.agenttools.CustomAgentToolManifestRuntimeTest" --no-daemon --console=plain

.\gradlew.bat :app:testAppDebugUnitTest --tests "io.legado.app.domain.agenttools.CustomAgentToolManifestRuntimeTest" --tests "io.legado.app.data.repository.AiToolRepositoryToolCatalogTest" --tests "io.legado.app.domain.agent.AgentPermissionBrokerTest" --tests "io.legado.app.security.AgentPermissionSecurityTest" --no-daemon --console=plain

.\gradlew.bat :app:compileAppDebugKotlin --no-daemon --console=plain
```

## Ket qua

- Custom runtime focused: 7 tests PASS, failures/errors/skipped = 0.
- P08 focused regression: 31 tests PASS, failures/errors/skipped = 0.
  - `CustomAgentToolManifestRuntimeTest`: 7 tests.
  - `AiToolRepositoryToolCatalogTest`: 7 tests.
  - `AgentPermissionBrokerTest`: 13 tests.
  - `AgentPermissionSecurityTest`: 4 tests.
- Kotlin compile: `:app:compileAppDebugKotlin` BUILD SUCCESSFUL.

## Bang chung

- `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.domain.agenttools.CustomAgentToolManifestRuntimeTest.xml`
- `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.data.repository.AiToolRepositoryToolCatalogTest.xml`
- `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.domain.agent.AgentPermissionBrokerTest.xml`
- `app/build/test-results/testAppDebugUnitTest/TEST-io.legado.app.security.AgentPermissionSecurityTest.xml`

## Rui ro va viec con lai

- Custom tool lifecycle storage/UI chua co, thuoc P08.T04.
- Custom JS v1 chi mo `READ`/`NETWORK`; app mutation/file/source/authoring tiep tuc phai di qua built-in tool + permission broker.
- Memory hard cap rieng cho Rhino heap khong duoc Android/JVM expose theo tung invocation; P08.T03 da co script size, static allocation scan, timeout va output/network cap. P08.T06 tiep tuc dong malicious corpus/release security gate.
