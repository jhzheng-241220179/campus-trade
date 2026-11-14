
$ErrorActionPreference = "Stop"

Set-Location -LiteralPath $PSScriptRoot

$securePassword = $null
$secureHmac = $null
$secureSmtpPassword = $null

try {
    # 1. Restore HMAC secret
    $hmacPath = Join-Path $env:USERPROFILE ".campustrade\verification-hmac.txt"

    if (-not (Test-Path -LiteralPath $hmacPath)) {
        throw "HMAC secret file not found."
    }

    $encryptedHmac = (Get-Content -LiteralPath $hmacPath -Raw).Trim()
    $secureHmac = ConvertTo-SecureString $encryptedHmac

    $env:VERIFICATION_HMAC_SECRET = [System.Net.NetworkCredential]::new(
        "",
        $secureHmac
    ).Password

    if ([Text.Encoding]::UTF8.GetByteCount($env:VERIFICATION_HMAC_SECRET) -lt 32) {
        throw "HMAC secret length is invalid."
    }

    # 2. Read MySQL password
    $securePassword = Read-Host "Enter MySQL password" -AsSecureString

    $env:DB_PASSWORD = [System.Net.NetworkCredential]::new(
        "",
        $securePassword
    ).Password

    # 3. Read SMTP credentials
    $env:SMTP_USERNAME = Read-Host "Enter QQ email address"

    if ([string]::IsNullOrWhiteSpace($env:SMTP_USERNAME)) {
        throw "SMTP username is required."
    }

    $secureSmtpPassword = Read-Host "Enter SMTP authorization code" -AsSecureString

    $env:SMTP_PASSWORD = [System.Net.NetworkCredential]::new(
        "",
        $secureSmtpPassword
    ).Password

    if ([string]::IsNullOrWhiteSpace($env:SMTP_PASSWORD)) {
        throw "SMTP password is required."
    }

    # 4. Start Spring Boot
    Write-Host "Starting CampusTrade backend..."

    & "$PSScriptRoot\mvnw.cmd" spring-boot:run
}
finally {
    # 5. Clean up environment variables
    Remove-Item Env:DB_PASSWORD -ErrorAction SilentlyContinue
    Remove-Item Env:VERIFICATION_HMAC_SECRET -ErrorAction SilentlyContinue
    Remove-Item Env:SMTP_USERNAME -ErrorAction SilentlyContinue
    Remove-Item Env:SMTP_PASSWORD -ErrorAction SilentlyContinue

    Remove-Variable securePassword -ErrorAction SilentlyContinue
    Remove-Variable secureHmac -ErrorAction SilentlyContinue
    Remove-Variable secureSmtpPassword -ErrorAction SilentlyContinue
}
