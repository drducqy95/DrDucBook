param(
    [string]$Root = (Resolve-Path "$PSScriptRoot/..").Path,
    [string]$RepoId = "Drduc/Legadofork",
    [string]$ManifestPath = "",
    [string]$WorkRoot = "",
    [switch]$DryRun,
    [switch]$IncludeMetadataOnly
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($ManifestPath)) {
    $ManifestPath = Join-Path $Root "supabase/artifacts/hf-artifacts-manifest.json"
}
if ([string]::IsNullOrWhiteSpace($WorkRoot)) {
    $tempRoot = if ([string]::IsNullOrWhiteSpace($env:TEMP)) { [System.IO.Path]::GetTempPath() } else { $env:TEMP }
    $WorkRoot = Join-Path $tempRoot "drducbook-hf-upload-work"
}

function Assert-Tool {
    param([string]$Name)
    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        throw "$Name is required for Hugging Face upload"
    }
}

function Get-HfToken {
    $names = @("HF_TOKEN", "HUGGINGFACE_HUB_TOKEN", "HF_WRITE_TOKEN", "HUGGINGFACE_TOKEN")
    $scopes = @("Process", "User", "Machine")
    foreach ($scope in $scopes) {
        foreach ($name in $names) {
            $token = [Environment]::GetEnvironmentVariable($name, $scope)
            if (-not [string]::IsNullOrWhiteSpace($token)) {
                return $token
            }
        }
    }
    if ([string]::IsNullOrWhiteSpace($token)) {
        throw "Set HF_TOKEN, HUGGINGFACE_HUB_TOKEN, HF_WRITE_TOKEN or HUGGINGFACE_TOKEN in Process/User/Machine environment before uploading"
    }
}

function Get-Sha256 {
    param([string]$Path)
    (Get-FileHash -Algorithm SHA256 -LiteralPath $Path).Hash.ToLowerInvariant()
}

function Assert-UnderRoot {
    param(
        [string]$Path,
        [string]$AllowedRoot
    )
    $resolvedPath = [System.IO.Path]::GetFullPath($Path)
    $resolvedRoot = [System.IO.Path]::GetFullPath($AllowedRoot)
    if (-not $resolvedPath.StartsWith($resolvedRoot, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Resolved path escapes allowed root: $resolvedPath"
    }
}

function Invoke-Git {
    param(
        [string[]]$Arguments,
        [string]$WorkingDirectory = $PWD.Path
    )
    & git @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "git $($Arguments -join ' ') failed with exit code $LASTEXITCODE"
    }
}

Assert-Tool "git"
Assert-Tool "git-lfs"

$manifest = Get-Content -LiteralPath $ManifestPath -Raw | ConvertFrom-Json
if ($manifest.repository -ne $RepoId) {
    throw "Manifest repository '$($manifest.repository)' does not match target '$RepoId'"
}

$ready = New-Object System.Collections.Generic.List[object]
$missing = New-Object System.Collections.Generic.List[object]

foreach ($artifact in $manifest.artifacts) {
    if ([string]::IsNullOrWhiteSpace($artifact.localSource)) {
        $missing.Add($artifact)
        continue
    }
    $source = Join-Path $Root $artifact.localSource
    if (-not (Test-Path -LiteralPath $source -PathType Leaf)) {
        throw "Local artifact is missing: $($artifact.id) -> $source"
    }
    $file = Get-Item -LiteralPath $source
    $actualSha = Get-Sha256 $file.FullName
    if ($file.Length -ne [long]$artifact.sizeBytes) {
        throw "Size mismatch for $($artifact.id): expected $($artifact.sizeBytes), got $($file.Length)"
    }
    if ($actualSha -ne $artifact.sha256) {
        throw "SHA-256 mismatch for $($artifact.id): expected $($artifact.sha256), got $actualSha"
    }
    $ready.Add([pscustomobject]@{
        id = $artifact.id
        localPath = $file.FullName
        hfPath = $artifact.hfPath
        sizeBytes = [long]$artifact.sizeBytes
        sha256 = $artifact.sha256
    })
}

$summary = [pscustomobject]@{
    repository = $RepoId
    manifestVersion = $manifest.manifestVersion
    readyCount = $ready.Count
    readyBytes = ($ready | Measure-Object sizeBytes -Sum).Sum
    metadataOnlyCount = $missing.Count
    metadataOnlyIds = @($missing | ForEach-Object { $_.id })
}

if ($DryRun) {
    $summary | ConvertTo-Json -Depth 5
    return
}

if ($missing.Count -gt 0 -and $IncludeMetadataOnly) {
    throw "Metadata-only artifacts cannot be uploaded without local source files: $($summary.metadataOnlyIds -join ', ')"
}

$token = Get-HfToken
$runId = Get-Date -Format "yyyyMMdd-HHmmss"
$workRootFull = [System.IO.Path]::GetFullPath($WorkRoot)
$repoDir = Join-Path $workRootFull "Legadofork-$runId"
$authHome = Join-Path $workRootFull ".hf-auth-$runId"
Assert-UnderRoot -Path $repoDir -AllowedRoot $workRootFull
Assert-UnderRoot -Path $authHome -AllowedRoot $workRootFull
New-Item -ItemType Directory -Force -Path $workRootFull | Out-Null
New-Item -ItemType Directory -Force -Path $authHome | Out-Null

$oldHome = $env:HOME
$oldUserProfile = $env:USERPROFILE
$oldGitLfsSkipSmudge = $env:GIT_LFS_SKIP_SMUDGE
try {
    $netrcPath = Join-Path $authHome "_netrc"
    [System.IO.File]::WriteAllText(
        $netrcPath,
        "machine huggingface.co login hf_user password $token`n",
        [System.Text.UTF8Encoding]::new($false)
    )
    $env:HOME = $authHome
    $env:USERPROFILE = $authHome
    $env:GIT_TERMINAL_PROMPT = "0"
    $env:GIT_LFS_SKIP_SMUDGE = "1"

    Invoke-Git -Arguments @("clone", "https://huggingface.co/datasets/$RepoId", $repoDir)
    Push-Location $repoDir
    try {
        Invoke-Git -Arguments @("config", "user.name", "DrDucBook Asset Uploader")
        Invoke-Git -Arguments @("config", "user.email", "drducbook-assets@example.invalid")
        Invoke-Git -Arguments @("lfs", "install", "--local")
        Invoke-Git -Arguments @("lfs", "track", "*.zip")
        Invoke-Git -Arguments @("lfs", "track", "*.gguf")

        foreach ($artifact in $ready) {
            $target = Join-Path $repoDir ($artifact.hfPath -replace "/", [System.IO.Path]::DirectorySeparatorChar)
            Assert-UnderRoot -Path $target -AllowedRoot $repoDir
            New-Item -ItemType Directory -Force -Path (Split-Path -Parent $target) | Out-Null
            Copy-Item -LiteralPath $artifact.localPath -Destination $target -Force
        }

        $manifestTarget = Join-Path $repoDir "manifest/hf-artifacts-manifest.json"
        New-Item -ItemType Directory -Force -Path (Split-Path -Parent $manifestTarget) | Out-Null
        Copy-Item -LiteralPath $ManifestPath -Destination $manifestTarget -Force

        $readmePath = Join-Path $repoDir "README.md"
        if (-not (Test-Path -LiteralPath $readmePath)) {
            [System.IO.File]::WriteAllText(
                $readmePath,
                "---`npretty_name: DrDucBook Legadofork Assets`n---`n`n# DrDucBook Legadofork Assets`n`nVersioned model and package artifacts for DrDucBook asset delivery.`n",
                [System.Text.UTF8Encoding]::new($false)
            )
        }

        $addPaths = New-Object System.Collections.Generic.List[string]
        foreach ($path in @(".gitattributes", "README.md", "manifest", "packages", "models")) {
            if (Test-Path -LiteralPath (Join-Path $repoDir $path)) {
                $addPaths.Add($path)
            }
        }
        if ($addPaths.Count -eq 0) {
            throw "No Hugging Face dataset paths were prepared for upload"
        }
        Invoke-Git -Arguments (@("add") + $addPaths.ToArray())
        $status = & git status --porcelain
        if ([string]::IsNullOrWhiteSpace(($status -join "`n"))) {
            Write-Host "No Hugging Face dataset changes to upload"
        } else {
            Invoke-Git -Arguments @("commit", "-m", "Upload DrDucBook asset manifest $($manifest.manifestVersion)")
            Invoke-Git -Arguments @("push", "origin", "main")
        }
    } finally {
        Pop-Location
    }
} finally {
    $env:HOME = $oldHome
    $env:USERPROFILE = $oldUserProfile
    $env:GIT_LFS_SKIP_SMUDGE = $oldGitLfsSkipSmudge
    Remove-Item -LiteralPath $authHome -Recurse -Force -ErrorAction SilentlyContinue
}

$summary | ConvertTo-Json -Depth 5
