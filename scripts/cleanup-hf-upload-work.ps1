param(
    [string]$Root = (Resolve-Path "$PSScriptRoot/..").Path,
    [switch]$KeepTemp
)

$ErrorActionPreference = "Stop"

function Assert-ChildPath {
    param(
        [string]$Path,
        [string]$Parent
    )
    $resolvedPath = [System.IO.Path]::GetFullPath($Path)
    $resolvedParent = [System.IO.Path]::GetFullPath($Parent)
    if (-not $resolvedPath.StartsWith($resolvedParent, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Path escapes parent: $resolvedPath"
    }
    $resolvedPath
}

function Remove-UploadChildren {
    param(
        [string]$UploadRoot,
        [string]$AllowedParent
    )
    if (-not (Test-Path -LiteralPath $UploadRoot)) {
        return @()
    }
    $rootFull = Assert-ChildPath -Path $UploadRoot -Parent $AllowedParent
    $removed = @()
    Get-ChildItem -LiteralPath $rootFull -Directory -Force | Where-Object {
        $_.Name -like "Legadofork-*"
    } | ForEach-Object {
        $child = Assert-ChildPath -Path $_.FullName -Parent $rootFull
        $size = (Get-ChildItem -LiteralPath $child -Recurse -File -Force -ErrorAction SilentlyContinue | Measure-Object Length -Sum).Sum
        Remove-Item -LiteralPath $child -Recurse -Force
        $removed += [pscustomobject]@{
            path = $child
            bytes = [long]$size
        }
    }
    $removed
}

$workspace = [System.IO.Path]::GetFullPath($Root)
$workspaceUploadRoot = Join-Path $workspace "artifacts/hf-upload-work"
$allRemoved = @()

foreach ($item in (Remove-UploadChildren -UploadRoot $workspaceUploadRoot -AllowedParent $workspace)) {
    $allRemoved += $item
}

if (-not $KeepTemp) {
    $tempParent = [System.IO.Path]::GetFullPath([System.IO.Path]::GetTempPath())
    $tempUploadRoot = Join-Path $tempParent "drducbook-hf-upload-work"
    foreach ($item in (Remove-UploadChildren -UploadRoot $tempUploadRoot -AllowedParent $tempParent)) {
        $allRemoved += $item
    }
}

$removedArray = @($allRemoved)
$removedMeasure = $removedArray | Measure-Object -Property bytes -Sum
$removedBytes = if ($null -eq $removedMeasure.Sum) { 0L } else { [long]$removedMeasure.Sum }
$drives = @([System.IO.DriveInfo]::GetDrives() |
    Where-Object { $_.Name -eq "C:\" -or $_.Name -eq "D:\" } |
    Select-Object Name, AvailableFreeSpace)

[pscustomobject]@{
    removedCount = $removedArray.Count
    removedBytes = $removedBytes
    removed = $removedArray
    drives = $drives
} | ConvertTo-Json -Depth 5
