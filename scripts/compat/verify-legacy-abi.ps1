param(
    [Parameter(Mandatory = $true)]
    [string]$ApkPath,

    [string]$AllowListPath = "app/legacy-compat-abi.txt"
)

$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.IO.Compression.FileSystem
$root = (Resolve-Path (Join-Path $PSScriptRoot "../..")).Path
$apk = (Resolve-Path (Join-Path $root $ApkPath)).Path
$allowList = (Resolve-Path (Join-Path $root $AllowListPath)).Path
$sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { $env:ANDROID_SDK_ROOT }

if (-not $sdk) {
    throw "ANDROID_HOME or ANDROID_SDK_ROOT is required."
}

$dexDump = Get-ChildItem (Join-Path $sdk "build-tools") -Filter "dexdump.exe" -Recurse |
    Sort-Object FullName -Descending |
    Select-Object -First 1 -ExpandProperty FullName
if (-not $dexDump) {
    throw "dexdump.exe was not found under $sdk/build-tools."
}

$temp = Join-Path ([System.IO.Path]::GetTempPath()) ("drducbook-abi-" + [Guid]::NewGuid())
New-Item -ItemType Directory -Path $temp | Out-Null

try {
    $archive = [System.IO.Compression.ZipFile]::OpenRead($apk)
    try {
        foreach ($entry in $archive.Entries) {
            if ($entry.FullName -notmatch '^classes\d*\.dex$') {
                continue
            }
            $destination = Join-Path $temp $entry.Name
            $sourceStream = $entry.Open()
            $targetStream = [System.IO.File]::Create($destination)
            try {
                $sourceStream.CopyTo($targetStream)
            }
            finally {
                $targetStream.Dispose()
                $sourceStream.Dispose()
            }
        }
    }
    finally {
        $archive.Dispose()
    }
    $dexFiles = Get-ChildItem $temp -Filter "classes*.dex" | Sort-Object Name
    if (-not $dexFiles) {
        throw "No classes*.dex files were found in $apk."
    }

    $descriptors = foreach ($dex in $dexFiles) {
        & $dexDump -f $dex.FullName 2>$null |
            Select-String "Class descriptor" |
            ForEach-Object { $_.Line }
    }
    $descriptorText = $descriptors -join "`n"
    $requiredClasses = Get-Content $allowList |
        ForEach-Object { $_.Trim() } |
        Where-Object { $_ -and -not $_.StartsWith("#") }
    $missing = foreach ($className in $requiredClasses) {
        $descriptor = "L" + $className.Replace(".", "/") + ";"
        if (-not $descriptorText.Contains("'$descriptor'")) {
            $className
        }
    }

    if ($missing) {
        throw "Minified APK is missing compatibility classes: $($missing -join ', ')"
    }

    Write-Output "PASS: $($requiredClasses.Count) legacy ABI classes found in $($dexFiles.Count) dex file(s)."
    Write-Output "APK: $apk"
}
finally {
    Remove-Item -LiteralPath $temp -Recurse -Force
}
