param(
    [Parameter(Mandatory = $true)]
    [string]$SourcePath,

    [Parameter(Mandatory = $true)]
    [string]$OutputPath
)

$ErrorActionPreference = 'Stop'

if (-not (Test-Path -LiteralPath $SourcePath -PathType Leaf)) {
    throw "Phonetic source not found: $SourcePath"
}

$rows = [System.Collections.Generic.List[string]]::new()
$seen = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)

Get-Content -LiteralPath $SourcePath -Encoding utf8 | ForEach-Object {
    if ($_ -notmatch '^\|\s*(.+?)\s*\|\s*(.+?)\s*\|\s*$') {
        return
    }

    $source = $Matches[1].Trim()
    $target = ($Matches[2].Trim() -split '/')[0].Trim()
    if ($source -in @('source', '---') -or $target -in @('target', '---')) {
        return
    }
    if ($source.Length -notin 1, 2 -or [string]::IsNullOrWhiteSpace($target)) {
        return
    }
    if ($seen.Add($source)) {
        $rows.Add("$source`t$target")
    }
}

if ($rows.Count -lt 10000) {
    throw "Unexpectedly small phonetic source: $($rows.Count) entries"
}

$header = @(
    '# Generated from Converter by DrDuc _bulk_phienam.md',
    '# Format: character<TAB>first Vietnamese Hán-Việt reading',
    '# Runtime role: lowest-priority single-character fallback'
)
$parent = Split-Path -Parent $OutputPath
if ($parent) {
    [System.IO.Directory]::CreateDirectory($parent) | Out-Null
}
[System.IO.File]::WriteAllLines(
    [System.IO.Path]::GetFullPath($OutputPath),
    @($header) + @($rows),
    [System.Text.UTF8Encoding]::new($false)
)

Write-Output "Generated $($rows.Count) phonetic entries at $OutputPath"
