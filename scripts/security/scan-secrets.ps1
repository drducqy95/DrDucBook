param(
    [string]$Root = (Resolve-Path (Join-Path $PSScriptRoot "../..")),
    [switch]$IncludeBuildOutputs
)

$ErrorActionPreference = "Stop"
$rootPath = (Resolve-Path -LiteralPath $Root).Path
$allowedExtensions = @(
    ".gradle", ".java", ".js", ".json", ".kt", ".kts", ".md", ".properties",
    ".ps1", ".sh", ".toml", ".ts", ".txt", ".vue", ".xml", ".yaml", ".yml"
)
$excludedDirectories = @(".git", ".gradle", ".idea", ".kotlin", "build", "node_modules")
$allowListedFiles = @(
    "app/google-services.json",
    "app/src/test/java/io/legado/app/security/DiagnosticRedactionTest.kt",
    "app/src/test/java/io/legado/app/domain/agent/AgentAuditSanitizerTest.kt",
    "app/src/test/java/io/legado/app/domain/agent/AgentSkillValidatorTest.kt"
)
$detectors = @(
    @{ Id = "HF_TOKEN"; Pattern = "(?i)\bhf_[a-z0-9]{20,}\b" },
    @{ Id = "PRIVATE_KEY"; Pattern = "-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----" },
    @{ Id = "BEARER_TOKEN"; Pattern = "(?i)\bBearer\s+[a-z0-9._~+/=-]{20,}" },
    @{ Id = "GITHUB_TOKEN"; Pattern = "\bgh[opusr]_[A-Za-z0-9]{20,}\b" },
    @{ Id = "GOOGLE_API_KEY"; Pattern = "\bAIza[0-9A-Za-z_-]{30,}\b" }
)

function Get-RelativePath([string]$Path) {
    $fullPath = [System.IO.Path]::GetFullPath($Path)
    if (-not $fullPath.StartsWith($rootPath, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Path is outside scan root."
    }
    return $fullPath.Substring($rootPath.Length).TrimStart("\", "/").Replace("\", "/")
}

$files = Get-ChildItem -LiteralPath $rootPath -Recurse -File | Where-Object {
    $relative = Get-RelativePath $_.FullName
    $segments = $relative.Split("/")
    $excluded = $segments | Where-Object { $excludedDirectories -contains $_ }
    $extensionAllowed = $allowedExtensions -contains $_.Extension.ToLowerInvariant()
    $buildAllowed = $IncludeBuildOutputs -or ($segments -notcontains "build")
    -not $excluded -and $extensionAllowed -and $buildAllowed
}

$findings = @()
foreach ($file in $files) {
    $relative = Get-RelativePath $file.FullName
    foreach ($detector in $detectors) {
        $matches = Select-String -LiteralPath $file.FullName -Pattern $detector.Pattern -AllMatches
        foreach ($match in $matches) {
            $findings += [PSCustomObject]@{
                Detector = $detector.Id
                File = $relative
                Line = $match.LineNumber
                Allowed = $allowListedFiles -contains $relative
            }
        }
    }
}

$findings | Sort-Object File, Line, Detector | ForEach-Object {
    $classification = if ($_.Allowed) { "ALLOWLISTED" } else { "UNAPPROVED" }
    Write-Output "$classification $($_.Detector) $($_.File):$($_.Line)"
}

$unapproved = @($findings | Where-Object { -not $_.Allowed })
if ($unapproved.Count -gt 0) {
    Write-Error "Secret scan failed: $($unapproved.Count) unapproved finding(s). Values were intentionally omitted."
    exit 1
}

Write-Output "Secret scan passed: $($findings.Count) allow-listed finding(s), 0 unapproved finding(s)."
