param(
    [string]$GoDir = "D:\Android\go",
    [string]$NdkVersion = "26.3.11579264",
    [string]$OutputAar = "app\libs\go-webdav.aar"
)

$ErrorActionPreference = 'Stop'

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "../..")
$goBin = Join-Path $GoDir "bin"
$gomobileBin = Join-Path $env:USERPROFILE "go\bin"

# Ensure Go and Gomobile are in PATH
if (-not (Test-Path (Join-Path $goBin "go.exe"))) {
    $goCmd = Get-Command go -ErrorAction SilentlyContinue
    if (-not $goCmd) {
        throw "go.exe not found at $goBin and not in PATH."
    }
} else {
    $env:PATH = "$goBin;$gomobileBin;$env:PATH"
}

# Ensure Android SDK / NDK paths (must NOT contain spaces for CGO clang on Windows)
if (Test-Path "D:\Android\Sdk") {
    $env:ANDROID_HOME = "D:\Android\Sdk"
} elseif (-not $env:ANDROID_HOME) {
    $env:ANDROID_HOME = "D:\Android\Android Studio\SDK\Sdk"
}

if (-not $env:ANDROID_NDK_HOME -or ($env:ANDROID_NDK_HOME -match '\s')) {
    $ndkCandidate = Join-Path $env:ANDROID_HOME "ndk\$NdkVersion"
    if (Test-Path $ndkCandidate) {
        $env:ANDROID_NDK_HOME = $ndkCandidate
    } else {
        # Auto-pick the newest available NDK
        $ndkBase = Join-Path $env:ANDROID_HOME "ndk"
        if (Test-Path $ndkBase) {
            $latest = Get-ChildItem $ndkBase | Sort-Object Name -Descending | Select-Object -First 1
            if ($latest) {
                $env:ANDROID_NDK_HOME = $latest.FullName
            }
        }
    }
}

if (-not (Test-Path $env:ANDROID_NDK_HOME)) {
    throw "ANDROID_NDK_HOME not found: $($env:ANDROID_NDK_HOME)"
}

Write-Host "=== GOMOBILE BUILD ENVIRONMENT ==="
Write-Host "Go: $(& go version)"
Write-Host "ANDROID_HOME: $env:ANDROID_HOME"
Write-Host "ANDROID_NDK_HOME: $env:ANDROID_NDK_HOME"

$targetPackage = "io.legado.gowebdav/bind"
$outputPath = Join-Path $repoRoot $OutputAar
$outputDir = Split-Path $outputPath -Parent
if (-not (Test-Path $outputDir)) {
    New-Item -ItemType Directory -Path $outputDir -Force | Out-Null
}

Push-Location (Join-Path $repoRoot "native/go-webdav")
try {
    Write-Host "Running go vet..."
    & go vet ./...
    if ($LASTEXITCODE -ne 0) { throw "go vet failed" }

    Write-Host "Running go test..."
    & go test ./...
    if ($LASTEXITCODE -ne 0) { throw "go test failed" }

    Write-Host "Building AAR via gomobile bind: $outputPath"
    $targetAbis = "android/arm,android/arm64,android/amd64"
    & gomobile bind -target $targetAbis -androidapi 26 -javapkg io.legado.app.gowebdav -ldflags "-s -w" -o $outputPath $targetPackage
    if ($LASTEXITCODE -ne 0) { throw "gomobile bind failed with exit code $LASTEXITCODE" }

    if (Test-Path $outputPath) {
        $file = Get-Item $outputPath
        $sizeMb = [math]::Round($file.Length / 1MB, 2)
        $hash = (Get-FileHash -Path $outputPath -Algorithm SHA256).Hash
        $hashFile = "$outputPath.sha256"
        Set-Content -Path $hashFile -Value $hash -Force

        Write-Host "`n✅ AAR build successful!"
        Write-Host "File: $outputPath ($sizeMb MB)"
        Write-Host "SHA256: $hash"
    } else {
        throw "Output file $outputPath does not exist after build."
    }
} finally {
    Pop-Location
}
