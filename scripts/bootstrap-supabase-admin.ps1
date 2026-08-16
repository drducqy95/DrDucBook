param(
    [string]$SupabaseUrl = $env:SUPABASE_URL,
    [string]$ServiceRoleKey = $env:SUPABASE_SERVICE_ROLE_KEY,
    [string]$AdminEmail = $env:DRDUCBOOK_BOOTSTRAP_ADMIN_EMAIL,
    [string]$AdminPassword = $env:DRDUCBOOK_BOOTSTRAP_ADMIN_PASSWORD,
    [string]$Username = "Drduc"
)

$ErrorActionPreference = "Stop"

if (-not [Uri]::IsWellFormedUriString($SupabaseUrl, [UriKind]::Absolute) -or
    -not $SupabaseUrl.StartsWith("https://", [StringComparison]::OrdinalIgnoreCase)) {
    throw "SUPABASE_URL must be a valid HTTPS URL."
}
if ([string]::IsNullOrWhiteSpace($ServiceRoleKey)) {
    throw "SUPABASE_SERVICE_ROLE_KEY is required and must never be bundled in the Android app."
}
if ($AdminEmail -notmatch '^[^@\s]+@[^@\s]+\.[^@\s]+$') {
    throw "DRDUCBOOK_BOOTSTRAP_ADMIN_EMAIL must be a valid email address."
}
if ($AdminPassword.Length -lt 12 -or
    $AdminPassword -notmatch '[A-Z]' -or
    $AdminPassword -notmatch '[a-z]' -or
    $AdminPassword -notmatch '[0-9]') {
    throw "The bootstrap password must contain at least 12 characters, uppercase, lowercase, and a number."
}

$baseUrl = $SupabaseUrl.TrimEnd('/')
$headers = @{
    apikey = $ServiceRoleKey
    Authorization = "Bearer $ServiceRoleKey"
    "Content-Type" = "application/json"
}

$existingAdmins = Invoke-RestMethod `
    -Method Get `
    -Uri "$baseUrl/rest/v1/account_access?role=eq.admin&select=user_id&limit=1" `
    -Headers $headers
if (@($existingAdmins).Count -gt 0) {
    throw "An administrator already exists. Bootstrap is intentionally one-time only."
}

$createBody = @{
    email = $AdminEmail
    password = $AdminPassword
    email_confirm = $true
    user_metadata = @{
        username = $Username
        display_name = $Username
        must_change_password = $true
    }
} | ConvertTo-Json -Depth 4

$createdUser = Invoke-RestMethod `
    -Method Post `
    -Uri "$baseUrl/auth/v1/admin/users" `
    -Headers $headers `
    -Body $createBody
if ([string]::IsNullOrWhiteSpace($createdUser.id)) {
    throw "Supabase did not return the new administrator ID."
}

$permissions = @(
    "cloud_backup",
    "download_content",
    "export_ebook",
    "authoring_chapter",
    "edit_ebook_chapter",
    "manage_accounts"
)
$accessBody = @{
    role = "admin"
    permissions = $permissions
} | ConvertTo-Json -Depth 3
$encodedUserId = [Uri]::EscapeDataString($createdUser.id)
$adminAccess = Invoke-RestMethod `
    -Method Patch `
    -Uri "$baseUrl/rest/v1/account_access?user_id=eq.$encodedUserId" `
    -Headers ($headers + @{ Prefer = "return=representation" }) `
    -Body $accessBody
if (@($adminAccess).Count -ne 1) {
    throw "The Auth user was created, but the account_access row was not promoted. Apply the account migration and retry the promotion manually."
}

Write-Host "Administrator '$Username' was created for $AdminEmail. Change the temporary password immediately after the first sign-in."
