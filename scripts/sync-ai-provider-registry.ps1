param(
    [Parameter(Mandatory = $true)]
    [string] $SourceDir,
    [string] $OutputFile = "app/src/main/java/io/legado/app/domain/model/AiProviderRegistryGenerated.kt"
)

$ErrorActionPreference = "Stop"

function Match-String([string] $Text, [string] $Pattern, [string] $Default = "") {
    $match = [regex]::Match($Text, $Pattern, [Text.RegularExpressions.RegexOptions]::Multiline)
    if ($match.Success) { return $match.Groups[1].Value }
    return $Default
}

function Get-Enclosed([string] $Text, [int] $OpenIndex, [char] $Open, [char] $Close) {
    if ($OpenIndex -lt 0 -or $OpenIndex -ge $Text.Length -or $Text[$OpenIndex] -ne $Open) {
        return ""
    }
    $depth = 0
    $quote = [char]0
    $escaped = $false
    for ($index = $OpenIndex; $index -lt $Text.Length; $index++) {
        $char = $Text[$index]
        if ($quote -ne [char]0) {
            if ($escaped) {
                $escaped = $false
            } elseif ($char -eq '\') {
                $escaped = $true
            } elseif ($char -eq $quote) {
                $quote = [char]0
            }
            continue
        }
        if ($char -eq '"' -or $char -eq "'" -or $char -eq '`') {
            $quote = $char
            continue
        }
        if ($char -eq $Open) { $depth++ }
        if ($char -eq $Close) {
            $depth--
            if ($depth -eq 0) {
                return $Text.Substring($OpenIndex + 1, $index - $OpenIndex - 1)
            }
        }
    }
    return ""
}

function Get-PropertyBlock([string] $Text, [string] $Property, [char] $Open, [char] $Close) {
    $match = [regex]::Match($Text, "(?m)^\s*$([regex]::Escape($Property))\s*:\s*\$Open")
    if (-not $match.Success) { return "" }
    $openIndex = $Text.IndexOf($Open, $match.Index)
    return Get-Enclosed $Text $openIndex $Open $Close
}

function Get-TopLevelPropertyBlock([string] $Text, [string] $Property, [char] $Open, [char] $Close) {
    $match = [regex]::Match($Text, "(?m)^  $([regex]::Escape($Property))\s*:\s*\$Open")
    if (-not $match.Success) { return "" }
    $openIndex = $Text.IndexOf($Open, $match.Index)
    return Get-Enclosed $Text $openIndex $Open $Close
}

function Get-Endpoint([string] $Text, [string] $Property, [string] $Kind) {
    $block = Get-PropertyBlock $Text $Property '{' '}'
    if (-not $block) { return $null }
    $url = Match-String $block '(?m)^\s*baseUrl\s*:\s*["'']([^"'']+)["'']'
    if (-not $url) { return $null }
    [pscustomobject]@{
        Kind = $Kind
        Url = $url
        Format = Match-String $block '(?m)^\s*format\s*:\s*["'']([^"'']+)["'']'
        AuthType = Match-String $block '(?m)^\s*authType\s*:\s*["'']([^"'']+)["'']'
        AuthHeader = Match-String $block '(?m)^\s*authHeader\s*:\s*["'']([^"'']+)["'']'
    }
}

function Get-Models([string] $Text) {
    $block = Get-PropertyBlock $Text 'models' '[' ']'
    if (-not $block) { return @() }
    $results = [Collections.Generic.List[object]]::new()
    $index = 0
    while ($index -lt $block.Length) {
        $openIndex = $block.IndexOf('{', $index)
        if ($openIndex -lt 0) { break }
        $modelBlock = Get-Enclosed $block $openIndex '{' '}'
        if (-not $modelBlock) { break }
        $id = Match-String $modelBlock '\bid\s*:\s*["'']([^"'']+)["'']'
        if ($id) {
            $name = Match-String $modelBlock '\bname\s*:\s*["'']([^"'']+)["'']' $id
            $kind = Match-String $modelBlock '\bkind\s*:\s*["'']([^"'']+)["'']' 'llm'
            $results.Add([pscustomobject]@{ Id = $id; Name = $name; Kind = $kind })
        }
        $index = $openIndex + $modelBlock.Length + 2
    }
    return $results
}

$endpointProperties = [ordered]@{
    transport = 'llm'
    embeddingConfig = 'embedding'
    imageConfig = 'image'
    searchConfig = 'webSearch'
    fetchConfig = 'webFetch'
    ttsConfig = 'tts'
    sttConfig = 'stt'
    videoConfig = 'video'
}

$entries = Get-ChildItem -LiteralPath $SourceDir -Filter '*.js' |
    Where-Object { $_.Name -ne 'index.js' } |
    ForEach-Object {
        $source = Get-Content -Raw -Encoding utf8 $_.FullName
        $id = Match-String $source '(?m)^\s*id\s*:\s*["'']([^"'']+)["'']'
        if (-not $id) { return }
        $display = Get-PropertyBlock $source 'display' '{' '}'
        $name = Match-String $display '(?m)^\s*name\s*:\s*["'']([^"'']+)["'']' $id
        $category = Match-String $source '(?m)^\s*category\s*:\s*["'']([^"'']+)["'']' 'apikey'
        $oauthBlock = Get-TopLevelPropertyBlock $source 'oauth' '{' '}'
        if (-not $oauthBlock -and $source -match '(?m)^\s*hasOAuth\s*:\s*true') {
            $oauthBlock = Get-PropertyBlock $source 'transport' '{' '}'
        }
        $authModesBlock = Get-PropertyBlock $source 'authModes' '[' ']'
        $authModes = [regex]::Matches($authModesBlock, '["'']([^"'']+)["'']') |
            ForEach-Object { $_.Groups[1].Value }
        $noAuth = $source -match '(?m)^\s*noAuth\s*:\s*true'
        if (-not $authModes) {
            $authModes = if ($noAuth) {
                @('none')
            } elseif ($oauthBlock) {
                @('oauth')
            } else {
                switch ($category) {
                    'oauth' { @('oauth') }
                    'webCookie' { @('cookie') }
                    default { @('apikey') }
                }
            }
        }
        if ($oauthBlock -and 'oauth' -notin $authModes) { $authModes = @($authModes) + 'oauth' }
        $serviceBlock = Get-PropertyBlock $source 'serviceKinds' '[' ']'
        $serviceKinds = [regex]::Matches($serviceBlock, '["'']([^"'']+)["'']') |
            ForEach-Object { $_.Groups[1].Value }
        if (-not $serviceKinds) { $serviceKinds = @('llm') }
        $endpoints = foreach ($pair in $endpointProperties.GetEnumerator()) {
            Get-Endpoint $source $pair.Key $pair.Value
        }
        $fetcher = Get-PropertyBlock $source 'modelsFetcher' '{' '}'
        [pscustomobject]@{
            Id = $id
            Name = $name
            Alias = Match-String $source '(?m)^\s*alias\s*:\s*["'']([^"'']+)["'']'
            Category = $category
            AuthModes = @($authModes | Sort-Object -Unique)
            ServiceKinds = @($serviceKinds | Sort-Object -Unique)
            NoAuth = $noAuth
            Hidden = $source -match '(?m)^\s*hidden\s*:\s*true'
            HasFree = $source -match '(?m)^\s*hasFree\s*:\s*true'
            Deprecated = $display -match '(?m)^\s*deprecated\s*:\s*true'
            Endpoints = @($endpoints | Where-Object { $_ })
            Models = @(Get-Models $source)
            ModelsUrl = Match-String $fetcher '(?m)^\s*url\s*:\s*["'']([^"'']+)["'']'
            ModelsFetcherType = Match-String $fetcher '(?m)^\s*type\s*:\s*["'']([^"'']+)["'']'
            OAuth = if ($oauthBlock) {
                $authorizeUrl = Match-String $oauthBlock '(?m)^\s*authorizeUrl\s*:\s*["'']([^"'']+)["'']'
                $deviceCodeUrl = Match-String $oauthBlock '(?m)^\s*(?:deviceCodeUrl|deviceAuthUrl|initiateUrl)\s*:\s*["'']([^"'']+)["'']'
                $tokenUrl = Match-String $oauthBlock '(?m)^\s*(?:tokenUrl|tokenExchangeUrl)\s*:\s*["'']([^"'']+)["'']'
                $refreshUrl = Match-String $oauthBlock '(?m)^\s*refreshUrl\s*:\s*["'']([^"'']+)["'']'
                $scope = Match-String $oauthBlock '(?m)^\s*scope\s*:\s*["'']([^"'']+)["'']'
                if (-not $scope) {
                    $scopesBlock = Get-PropertyBlock $oauthBlock 'scopes' '[' ']'
                    $scope = ([regex]::Matches($scopesBlock, '["'']([^"'']+)["'']') |
                        ForEach-Object { $_.Groups[1].Value }) -join ' '
                }
                $flow = if ($deviceCodeUrl) { 'device_code' } elseif ($authorizeUrl) { 'authorization_code' } else { 'custom' }
                [pscustomobject]@{
                    Flow = $flow
                    ClientId = Match-String $oauthBlock '(?m)^\s*clientId\s*:\s*["'']([^"'']+)["'']'
                    AuthorizeUrl = $authorizeUrl
                    DeviceCodeUrl = $deviceCodeUrl
                    TokenUrl = $tokenUrl
                    RefreshUrl = $refreshUrl
                    Scope = $scope
                }
            } else { $null }
            SourcePath = "open-sse/providers/registry/$($_.Name)"
        }
    } | Sort-Object Id

$compactEntries = @($entries | ForEach-Object {
    [ordered]@{
        id = $_.Id
        name = $_.Name
        alias = $_.Alias
        category = $_.Category
        authModes = @($_.AuthModes)
        serviceKinds = @($_.ServiceKinds)
        noAuth = [bool]$_.NoAuth
        hidden = [bool]$_.Hidden
        hasFree = [bool]$_.HasFree
        deprecated = [bool]$_.Deprecated
        endpoints = @($_.Endpoints | ForEach-Object {
            [ordered]@{
                kind = $_.Kind
                url = $_.Url
                format = $_.Format
                authType = $_.AuthType
                authHeader = $_.AuthHeader
            }
        })
        models = @($_.Models | ForEach-Object {
            [ordered]@{ id = $_.Id; name = $_.Name; kind = $_.Kind }
        })
        modelsUrl = if ($_.ModelsUrl) { $_.ModelsUrl } else { $null }
        modelsFetcherType = if ($_.ModelsFetcherType) { $_.ModelsFetcherType } else { $null }
        oauth = if ($_.OAuth) {
            [ordered]@{
                flow = $_.OAuth.Flow
                clientId = $_.OAuth.ClientId
                authorizeUrl = $_.OAuth.AuthorizeUrl
                deviceCodeUrl = $_.OAuth.DeviceCodeUrl
                tokenUrl = $_.OAuth.TokenUrl
                refreshUrl = $_.OAuth.RefreshUrl
                scope = $_.OAuth.Scope
            }
        } else { $null }
        sourcePath = $_.SourcePath
    }
})

# Keep the generated Kotlin AST tiny. Thousands of nested constructors made K2/FIR consume several
# gigabytes while compiling; a compact Base64 JSON payload preserves the complete registry and is
# decoded once, lazily, at runtime.
$json = ConvertTo-Json -InputObject $compactEntries -Depth 12 -Compress
$jsonBytes = [Text.Encoding]::UTF8.GetBytes($json)
$compressedStream = [IO.MemoryStream]::new()
$gzip = [IO.Compression.GZipStream]::new(
    $compressedStream,
    [IO.Compression.CompressionLevel]::Optimal,
    $true
)
$gzip.Write($jsonBytes, 0, $jsonBytes.Length)
$gzip.Dispose()
$encoded = [Convert]::ToBase64String($compressedStream.ToArray())
$compressedStream.Dispose()
$chunks = for ($index = 0; $index -lt $encoded.Length; $index += 12000) {
    $length = [Math]::Min(12000, $encoded.Length - $index)
    $encoded.Substring($index, $length)
}

$lines = [Collections.Generic.List[string]]::new()
$lines.Add('// Generated by scripts/sync-ai-provider-registry.ps1; do not edit manually.')
$lines.Add('package io.legado.app.domain.model')
$lines.Add('')
$lines.Add('import com.google.gson.Gson')
$lines.Add('import java.io.ByteArrayInputStream')
$lines.Add('import java.util.Base64')
$lines.Add('import java.util.zip.GZIPInputStream')
$lines.Add('')
$lines.Add('internal object AiProviderRegistryGenerated {')
$lines.Add('    val entries: List<AiProviderRegistryEntry> by lazy(LazyThreadSafetyMode.PUBLICATION) {')
$lines.Add('        val encoded = DATA.joinToString(separator = "")')
$lines.Add('        val compressed = Base64.getDecoder().decode(encoded)')
$lines.Add('        val json = GZIPInputStream(ByteArrayInputStream(compressed)).use { stream ->')
$lines.Add('            String(stream.readBytes(), Charsets.UTF_8)')
$lines.Add('        }')
$lines.Add('        Gson().fromJson(json, Array<AiProviderRegistryEntry>::class.java).toList()')
$lines.Add('    }')
$lines.Add('')
$lines.Add('    private val DATA = arrayOf(')
foreach ($chunk in $chunks) { $lines.Add("        `"$chunk`",") }
$lines.Add('    )')
$lines.Add('}')

$parent = Split-Path -Parent $OutputFile
if ($parent) { New-Item -ItemType Directory -Force -Path $parent | Out-Null }
[IO.File]::WriteAllLines((Join-Path (Get-Location) $OutputFile), $lines, [Text.UTF8Encoding]::new($false))
Write-Host "Generated $($entries.Count) providers at $OutputFile"
