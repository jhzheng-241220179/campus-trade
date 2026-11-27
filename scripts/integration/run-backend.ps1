
$ErrorActionPreference = "Stop"

$projectRoot = (
    Resolve-Path (Join-Path $PSScriptRoot "..\..")
).Path

$backendDir = Join-Path $projectRoot "backend"

function Read-Password([string]$prompt) {
    $secure = Read-Host $prompt -AsSecureString

    return [System.Net.NetworkCredential]::new(
        "",
        $secure
    ).Password
}

$settings = @{
    "SPRING_PROFILES_ACTIVE" = "integration"

    "SERVER_PORT" = "8081"
    "SERVER_ADDRESS" = "127.0.0.1"

    "SPRING_DATASOURCE_URL" = "jdbc:mysql://localhost:3306/campustrade_test?connectionTimeZone=Asia/Shanghai"
    "SPRING_DATASOURCE_USERNAME" = "campustrade_test_app"

    "SPRING_FLYWAY_ENABLED" = "true"
    "SPRING_FLYWAY_URL" = "jdbc:mysql://localhost:3306/campustrade_test?connectionTimeZone=Asia/Shanghai"
    "SPRING_FLYWAY_USER" = "campustrade_test_migrator"
    "SPRING_FLYWAY_CLEAN_DISABLED" = "true"

    "SMTP_USERNAME" = "test@example.invalid"
    "SMTP_PASSWORD" = "integration-test-only"

    "SPRING_MAIL_HOST" = "127.0.0.1"
    "SPRING_MAIL_PORT" = "2525"

    "VERIFICATION_HMAC_SECRET" = (
        [Guid]::NewGuid().ToString("N") +
        [Guid]::NewGuid().ToString("N")
    )

    "CAMPUSTRADE_RESERVATION_SCHEDULER_ENABLED" = "true"

    "CAMPUSTRADE_RESERVATION_PRIORITYWINDOW" = "10s"
    "CAMPUSTRADE_RESERVATION_COOLINGPERIOD" = "60s"
    "CAMPUSTRADE_RESERVATION_SELLERRESPONSEPERIOD" = "60s"
    "CAMPUSTRADE_RESERVATION_BUYERCANCELCOOLDOWN" = "5s"
}

$settings["DB_PASSWORD"] = Read-Password `
    "Enter test database app password"

$settings["SPRING_FLYWAY_PASSWORD"] = Read-Password `
    "Enter test database migrator password"

$previousValues = @{}

try {
    foreach ($key in $settings.Keys) {
        $previousValues[$key] = `
            [Environment]::GetEnvironmentVariable(
                $key,
                "Process"
            )

        [Environment]::SetEnvironmentVariable(
            $key,
            $settings[$key],
            "Process"
        )
    }

    Write-Host ""
    Write-Host "Starting integration backend..."
    Write-Host "Database: campustrade_test"
    Write-Host "Address: http://127.0.0.1:8081"
    Write-Host "Priority window: 10s"
    Write-Host "Cooling period: 60s"
    Write-Host "Scheduler: enabled"
    Write-Host ""

    Push-Location $backendDir

    try {
        & ".\mvnw.cmd" spring-boot:run

        if ($LASTEXITCODE -ne 0) {
            throw "Backend exited with code $LASTEXITCODE"
        }
    }
    finally {
        Pop-Location
    }
}
finally {
    foreach ($key in $previousValues.Keys) {
        [Environment]::SetEnvironmentVariable(
            $key,
            $previousValues[$key],
            "Process"
        )
    }

    $settings.Clear()
    $previousValues.Clear()

    Write-Host "Integration environment restored."
}
