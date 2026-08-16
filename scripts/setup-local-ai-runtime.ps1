param(
    [string]$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot ".."))
)

$ErrorActionPreference = "Stop"
$Commit = "781aadf8749c9fa26bcf12f27d891dff86916b44"
$ArchiveSha256 = "4C0085B19D4FBDC8904088F461C4B00A305440A9AE55AE1A7E1A05059A23DAD8"
$Url = "https://codeload.github.com/sjl623/llama.cpp/zip/$Commit"
$Destination = Join-Path $ProjectRoot "app/src/main/cpp/local-ai/third_party/llama.cpp"
$WorkspaceTemp = Join-Path $ProjectRoot ".codex-tmp"
$Work = Join-Path $WorkspaceTemp "llama-stq-$([guid]::NewGuid().ToString('N').Substring(0, 8))"
$ExtractRoot = Join-Path $Work "extract"
$Archive = Join-Path $Work "source.zip"
$RequiredFiles = @(
    "CMakeLists.txt",
    "vendor/cpp-httplib/CMakeLists.txt",
    "vendor/cpp-httplib/httplib.cpp",
    "vendor/cpp-httplib/httplib.h"
)

if (($RequiredFiles | Where-Object { -not (Test-Path (Join-Path $Destination $_)) }).Count -eq 0) {
    Write-Host "Pinned llama.cpp source already exists: $Destination"
    exit 0
}

if (Test-Path -LiteralPath $Destination) {
    $ExpectedParent = [IO.Path]::GetFullPath(
        (Join-Path $ProjectRoot "app/src/main/cpp/local-ai/third_party")
    ).TrimEnd([IO.Path]::DirectorySeparatorChar)
    $ResolvedDestination = [IO.Path]::GetFullPath($Destination)
    if (-not $ResolvedDestination.StartsWith(
        "$ExpectedParent$([IO.Path]::DirectorySeparatorChar)",
        [StringComparison]::OrdinalIgnoreCase
    )) {
        throw "Refusing to replace llama.cpp outside the expected third_party directory: $ResolvedDestination"
    }
    Write-Host "Replacing incomplete pinned llama.cpp source: $ResolvedDestination"
    Remove-Item -LiteralPath $ResolvedDestination -Recurse -Force
}

New-Item -ItemType Directory -Path $ExtractRoot -Force | Out-Null
try {
    Invoke-WebRequest -Uri $Url -OutFile $Archive
    $ActualHash = (Get-FileHash -LiteralPath $Archive -Algorithm SHA256).Hash
    if ($ActualHash -ne $ArchiveSha256) {
        throw "Pinned llama.cpp archive checksum mismatch: $ActualHash"
    }

    tar.exe -xf $Archive -C $ExtractRoot
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to extract pinned llama.cpp archive"
    }
    $Candidates = @(Get-ChildItem -LiteralPath $ExtractRoot -Directory |
        Where-Object { $_.Name -like "llama.cpp-*" } |
        Select-Object -First 2)
    if ($Candidates.Count -ne 1 -or
        ($RequiredFiles | Where-Object {
            -not (Test-Path (Join-Path $Candidates[0].FullName $_))
        }).Count -ne 0) {
        throw "Pinned llama.cpp archive did not contain the expected source directory"
    }
    $Extracted = $Candidates[0]
    New-Item -ItemType Directory -Path (Split-Path $Destination) -Force | Out-Null
    Move-Item -LiteralPath $Extracted.FullName -Destination $Destination
    Write-Host "Installed llama.cpp STQ runtime source at commit $Commit"
} finally {
    $ResolvedTemp = [IO.Path]::GetFullPath($WorkspaceTemp).TrimEnd([IO.Path]::DirectorySeparatorChar)
    $ResolvedWork = [IO.Path]::GetFullPath($Work)
    if ($ResolvedWork.StartsWith("$ResolvedTemp$([IO.Path]::DirectorySeparatorChar)") -and
        (Test-Path -LiteralPath $ResolvedWork)) {
        Remove-Item -LiteralPath $ResolvedWork -Recurse -Force
    }
}
