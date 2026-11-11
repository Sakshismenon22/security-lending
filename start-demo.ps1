param(
    [switch]$Plan,
    [ValidateRange(10, 1800)]
    [int]$StartupTimeoutSeconds = 240
)

$ErrorActionPreference = 'Stop'

$portal = @{
    Name = 'Kafka UI'
    Folder = 'kafka-ui-portal\kafka-ui-portal'
    Port = 3001
    ReadyPath = '/api/health'
    Command = 'if exist node_modules\vite\package.json (call npm run build && call npm start) else (call npm ci && call npm run build && call npm start)'
}
$services = @(
    @{
        Name = 'Market data'
        Folder = 'market-data-service'
        Port = 8080
        ReadyPath = '/actuator/health'
        Command = 'call mvnw.cmd spring-boot:run -Dspring-boot.run.arguments=--spring.jpa.hibernate.ddl-auto=update'
    },
    @{
        Name = 'Lender FIX adapter'
        Folder = 'fix-adapter-service'
        Port = 8083
        ReadyPath = '/api/v1/adapter/status'
        Command = 'call run-lender.cmd'
    },
    @{
        Name = 'Borrower FIX adapter'
        Folder = 'fix-adapter-service'
        Port = 8082
        ReadyPath = '/api/v1/adapter/status'
        Command = 'call run-borrower.cmd'
    },
    @{
        Name = 'Borrower service'
        Folder = 'borrower-service'
        Port = 8081
        ReadyPath = '/actuator/health'
        Command = 'call mvnw.cmd spring-boot:run'
    },
    @{
        Name = 'Lender service'
        Folder = 'lender-service'
        Port = 8084
        ReadyPath = '/actuator/health'
        Command = 'call mvnw.cmd spring-boot:run'
    },
    @{
        Name = 'Lifecycle service'
        Folder = 'lifecycle-service'
        Port = 8085
        ReadyPath = '/actuator/health'
        Command = 'call mvnw.cmd spring-boot:run'
    }
)
$frontend = @{
    Name = 'Lending test frontend'
    Folder = 'security-lending-test-demo'
    Port = 5176
    ReadyPath = '/'
    Command = 'if exist node_modules\vite\package.json (call npm run dev) else (call npm ci && call npm run dev)'
}

function Test-Port([int]$Port) {
    $client = New-Object System.Net.Sockets.TcpClient
    try {
        return $client.ConnectAsync('127.0.0.1', $Port).Wait(500) -and $client.Connected
    } catch {
        return $false
    } finally {
        $client.Dispose()
    }
}

function Get-LocalJson([int]$Port, [string]$Path) {
    try {
        Invoke-RestMethod -Uri "http://127.0.0.1:$Port$Path" -TimeoutSec 4
    } catch {
        $null
    }
}

function Wait-Ready([string]$Name, [scriptblock]$Check) {
    $timer = [Diagnostics.Stopwatch]::StartNew()
    do {
        $result = & $Check
        if ($result) { return $result }
        Start-Sleep -Seconds 2
    } while ($timer.Elapsed.TotalSeconds -lt $StartupTimeoutSeconds)
    throw "$Name did not become ready within $StartupTimeoutSeconds seconds. Check its Command Prompt for the startup error."
}

function Start-Component($Component) {
    $directory = Join-Path $PSScriptRoot $Component.Folder
    if (-not (Test-Path -LiteralPath $directory -PathType Container)) {
        throw "Missing service folder: $directory"
    }
    if (Test-Port $Component.Port) {
        Write-Host "Checking existing $($Component.Name) on port $($Component.Port)..."
    } else {
        Write-Host "Starting $($Component.Name) on port $($Component.Port)..."
        Start-Process -FilePath $env:ComSpec -WorkingDirectory $directory -WindowStyle Normal `
            -ArgumentList "/d /k title $($Component.Name) & $($Component.Command)"
    }
    $null = Wait-Ready $Component.Name {
        if ($Component.ReadyPath -eq '/') {
            try {
                $page = Invoke-WebRequest -UseBasicParsing -Uri "http://127.0.0.1:$($Component.Port)/" -TimeoutSec 4
                return $page.StatusCode -eq 200
            } catch { return $false }
        }
        $status = Get-LocalJson $Component.Port $Component.ReadyPath
        if ($Component.ReadyPath -eq '/actuator/health') { return $status.status -eq 'UP' }
        if ($Component.ReadyPath -eq '/api/v1/adapter/status') { return $status.success -eq $true }
        return $null -ne $status.online
    }
}

function Invoke-PortalPost([string]$Path) {
    Invoke-RestMethod -Method Post -Uri "http://127.0.0.1:3001$Path" `
        -ContentType 'application/json' -Body '{}' -TimeoutSec 60
}

try {
    if ($Plan) {
        @($portal) + $services + @($frontend) | ForEach-Object {
            $directory = Join-Path $PSScriptRoot $_.Folder
            if (-not (Test-Path -LiteralPath $directory -PathType Container)) {
                throw "Missing service folder: $directory"
            }
            Write-Host "$($_.Port)  $($_.Name)  [$directory]"
        }
        exit 0
    }

    if (-not (Get-Command npm.cmd -ErrorAction SilentlyContinue)) {
        throw 'npm was not found on PATH. Install Node.js before starting the demo.'
    }
    if (-not (Test-Port 5432)) {
        throw 'Start PostgreSQL on port 5432 and create the databases listed in START_DEMO.md.'
    }

    Start-Component $portal
    $health = Get-LocalJson 3001 '/api/health'
    if ($health.bootstrap -notin @('localhost:9092', '127.0.0.1:9092')) {
        throw "The portal uses Kafka at '$($health.bootstrap)'. This demo expects localhost:9092."
    }
    if (-not $health.online -and $health.status -notin @('initializing', 'starting')) {
        if (-not $health.initialized) { $null = Invoke-PortalPost '/api/kafka/initialize' }
        $null = Invoke-PortalPost '/api/kafka/start'
    }
    $null = Wait-Ready 'Kafka broker' { (Get-LocalJson 3001 '/api/health').online }
    $topics = Invoke-PortalPost '/api/topics/lending-preset'
    Write-Host $topics.message

    foreach ($service in $services) { Start-Component $service }
    $null = Wait-Ready 'Borrower-to-lender FIX session' {
        $adapter = Get-LocalJson 8082 '/api/v1/adapter/status'
        @($adapter.data.sessions | Where-Object { $_.loggedOn }).Count -gt 0
    }
    Start-Component $frontend

    Write-Host ''
    Write-Host 'Test frontend: http://127.0.0.1:5176'
    Write-Host 'Kafka UI:      http://127.0.0.1:3001'
    Write-Host 'Each application runs in its own Command Prompt.'
} catch {
    Write-Host "Startup failed: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}
