$ErrorActionPreference = 'Stop'
$taskRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$taskFrontend = Join-Path $taskRoot 'frontend'
$taskDbName = 'spendly-e2e-' + [Guid]::NewGuid().ToString('N').Substring(0, 10)
$taskPassword = [Guid]::NewGuid().ToString('N')
$taskJava = $null
$taskVite = $null
$taskEnvironment = @{}
$taskKeys = @('SPRING_DATASOURCE_URL', 'SPRING_DATASOURCE_USERNAME', 'SPRING_DATASOURCE_PASSWORD', 'SPENDLY_DB_PASSWORD', 'SPENDLY_API_TARGET', 'SPENDLY_E2E_BASE_URL')
foreach ($taskKey in $taskKeys) { $taskEnvironment[$taskKey] = [Environment]::GetEnvironmentVariable($taskKey, 'Process') }

function Get-FreePort {
    $listener = New-Object Net.Sockets.TcpListener([Net.IPAddress]::Loopback, 0)
    $listener.Start()
    $selectedPort = $listener.LocalEndpoint.Port
    $listener.Stop()
    return $selectedPort
}

function Wait-Http($url) {
    for ($attempt = 0; $attempt -lt 40; $attempt++) {
        try { Invoke-WebRequest $url -UseBasicParsing -TimeoutSec 2 | Out-Null; return }
        catch { Start-Sleep -Seconds 1 }
    }
    throw 'Test server did not become ready. See backend/build/frontend-e2e*.log.'
}

Push-Location $taskRoot
try {
    & './backend/gradlew.bat' -p backend bootJar
    if ($LASTEXITCODE -ne 0) { throw 'Backend build failed.' }
    docker run --pull=never --rm -d --name $taskDbName --env POSTGRES_DB=spendly_test --env "POSTGRES_PASSWORD=$taskPassword" --publish '127.0.0.1::5432' --health-cmd 'pg_isready -U postgres -d spendly_test' --health-interval 1s --health-timeout 3s --health-retries 30 postgres:16-alpine | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Temporary PostgreSQL failed. Pull postgres:16-alpine first.' }
    for ($attempt = 0; $attempt -lt 30; $attempt++) {
        $health = docker inspect --format '{{.State.Health.Status}}' $taskDbName
        if ($health -eq 'healthy') { break }
        Start-Sleep -Seconds 1
    }
    if ($health -ne 'healthy') { throw 'Temporary PostgreSQL is not healthy.' }
    $mapping = docker port $taskDbName 5432/tcp
    $taskDbPort = ($mapping.Trim() -split ':')[-1]
    $env:SPRING_DATASOURCE_URL = "jdbc:postgresql://127.0.0.1:$taskDbPort/spendly_test"
    $env:SPRING_DATASOURCE_USERNAME = 'postgres'
    $env:SPRING_DATASOURCE_PASSWORD = $taskPassword
    $env:SPENDLY_DB_PASSWORD = $taskPassword
    $taskApiPort = Get-FreePort
    $taskWebPort = Get-FreePort
    $env:SPENDLY_API_TARGET = "http://127.0.0.1:$taskApiPort"
    $env:SPENDLY_E2E_BASE_URL = "http://127.0.0.1:$taskWebPort"
    $jar = Get-ChildItem backend/build/libs/backend-*.jar | Where-Object { $_.Name -notlike '*-plain.jar' } | Sort-Object LastWriteTime -Descending | Select-Object -First 1
    $taskJava = Start-Process -FilePath (Get-Command java.exe).Source -ArgumentList @('-jar', $jar.FullName, '--spring.profiles.active=local', "--server.port=$taskApiPort") -WindowStyle Hidden -PassThru -RedirectStandardOutput "$taskRoot/backend/build/frontend-e2e-api.log" -RedirectStandardError "$taskRoot/backend/build/frontend-e2e-api-error.log"
    Wait-Http "$env:SPENDLY_API_TARGET/api/auth/csrf"
    $taskVite = Start-Process -FilePath (Get-Command node.exe).Source -ArgumentList @('node_modules/vite/bin/vite.js', '--port', $taskWebPort) -WorkingDirectory $taskFrontend -WindowStyle Hidden -PassThru -RedirectStandardOutput "$taskRoot/backend/build/frontend-e2e-vite.log" -RedirectStandardError "$taskRoot/backend/build/frontend-e2e-vite-error.log"
    Wait-Http "$env:SPENDLY_E2E_BASE_URL/api/auth/csrf"
    Push-Location $taskFrontend
    try {
        npm run test:e2e
        if ($LASTEXITCODE -ne 0) { throw 'Browser E2E failed.' }
    } finally { Pop-Location }
} finally {
    foreach ($taskProcess in @($taskVite, $taskJava)) {
        if ($null -ne $taskProcess) {
            $taskProcess.Refresh()
            if (-not $taskProcess.HasExited) { Stop-Process -Id $taskProcess.Id }
        }
    }
    docker stop $taskDbName 2>$null | Out-Null
    foreach ($taskKey in $taskKeys) { [Environment]::SetEnvironmentVariable($taskKey, $taskEnvironment[$taskKey], 'Process') }
    Pop-Location
    Write-Output 'Temporary E2E servers and DB cleaned.'
}
