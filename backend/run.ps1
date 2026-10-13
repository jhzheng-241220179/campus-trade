
$ErrorActionPreference = "Stop"

Set-Location $PSScriptRoot

try {
    $securePassword = Read-Host "Enter MySQL password" -AsSecureString

    $env:DB_PASSWORD = [System.Net.NetworkCredential]::new(
        "",
        $securePassword
    ).Password

    Write-Host "Starting CampusTrade backend..."

    & "$PSScriptRoot\mvnw.cmd" spring-boot:run
}
finally {
    Remove-Item Env:DB_PASSWORD -ErrorAction SilentlyContinue
    Remove-Variable securePassword -ErrorAction SilentlyContinue
}
