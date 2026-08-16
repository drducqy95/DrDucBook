param(
    [Parameter(Mandatory = $true)]
    [string]$CloudflaredSource,
    [Parameter(Mandatory = $true)]
    [string]$GoExecutable
)

$ErrorActionPreference = 'Stop'
$repositoryRoot = Resolve-Path (Join-Path $PSScriptRoot '../..')
$sourceRoot = Resolve-Path $CloudflaredSource
$go = Resolve-Path $GoExecutable
$bridge = Join-Path $PSScriptRoot 'android_dns.go'
$bridgeTarget = Join-Path $sourceRoot 'cmd/cloudflared/android_dns.go'
Copy-Item -LiteralPath $bridge -Destination $bridgeTarget -Force

$targets = @(
    @{ Abi = 'arm64-v8a'; Arch = 'arm64'; Arm = '' },
    @{ Abi = 'armeabi-v7a'; Arch = 'arm'; Arm = '7' },
    @{ Abi = 'x86_64'; Arch = 'amd64'; Arm = '' }
)

Push-Location $sourceRoot
try {
    foreach ($target in $targets) {
        $outputDirectory = Join-Path $repositoryRoot "app/src/main/jniLibs/$($target.Abi)"
        New-Item -ItemType Directory -Path $outputDirectory -Force | Out-Null
        $output = Join-Path $outputDirectory 'libcloudflared.so'
        $env:CGO_ENABLED = '0'
        $env:GOOS = 'linux'
        $env:GOARCH = $target.Arch
        $env:GOARM = $target.Arm
        & $go build -mod=mod -buildvcs=false -trimpath -ldflags '-s -w -X main.Version=2026.5.2-android' -o $output ./cmd/cloudflared
        if ($LASTEXITCODE -ne 0) {
            throw "cloudflared build failed for $($target.Abi)"
        }
    }
} finally {
    Pop-Location
    Remove-Item -LiteralPath $bridgeTarget -ErrorAction SilentlyContinue
}
