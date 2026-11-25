param(
    [switch]$Plan,
    [switch]$Lendora,
    [ValidateRange(10, 1800)]
    [int]$StartupTimeoutSeconds = 240,
    [Alias('Observabilty')]
    [switch]$Observability
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
        Command = 'call mvnw.cmd spring-boot:run -Dspring-boot.run.arguments=--lendora.auth-enabled=false'
    },
    @{
        Name = 'Lender service'
        Folder = 'lender-service'
        Port = 8084
        ReadyPath = '/actuator/health'
        Command = 'call mvnw.cmd spring-boot:run -Dspring-boot.run.arguments=--lendora.auth-enabled=false'
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
    ExpectedTitle = 'Lending lab'
    Folder = 'security-lending-test-demo'
    Port = 5176
    ReadyPath = '/'
    Command = 'if exist node_modules\vite\package.json (call npm run dev) else (call npm ci && call npm run dev)'
}

$frontends = @($frontend)
if ($Lendora) {
    foreach ($service in $services) {
        if ($service.Port -in @(8081, 8084)) {
            $service.Command = 'call mvnw.cmd spring-boot:run -Dspring-boot.run.arguments=--lendora.auth-enabled=true'
        }
    }
    $services += @{
        Name = 'Lendora account service'
        Folder = 'lendora-service'
        Port = 8086
        ReadyPath = '/api/v1/auth/me'
        Command = 'call mvnw.cmd spring-boot:run'
    }
    $services += @{
        Name = 'API Gateway'
        Folder = 'api-gateway'
        Port = 8090
        ReadyPath = '/actuator/health'
        Command = 'call mvnw.cmd spring-boot:run'
    }
    $frontends = @(
        @{ Name = 'BorrowDesk'; Folder = 'borrowdesk'; Port = 5173; ReadyPath = '/'; ExpectedTitle = 'BorrowDesk' },
        @{ Name = 'LendDesk'; Folder = 'lenddesk'; Port = 5175; ReadyPath = '/'; ExpectedTitle = 'lenddesk' },
        @{ Name = 'Lendora'; Folder = 'lendora'; Port = 5176; ReadyPath = '/'; ExpectedTitle = 'Lendora' }
    )
    foreach ($app in $frontends) {
        $app.Command = "if exist node_modules\vite\package.json (call npm run dev -- --port $($app.Port) --strictPort) else (call npm ci && call npm run dev -- --port $($app.Port) --strictPort)"
    }
}

function Initialize-LendoraKey {
    if (-not [string]::IsNullOrWhiteSpace($env:LENDORA_PROVISIONING_KEY)) { return }
    $directory = Join-Path $PSScriptRoot 'runtime'
    $path = Join-Path $directory 'lendora-provisioning.key'
    if (-not (Test-Path -LiteralPath $path)) {
        $null = New-Item -ItemType Directory -Path $directory -Force
        $bytes = New-Object byte[] 32
        $random = [Security.Cryptography.RandomNumberGenerator]::Create()
        try { $random.GetBytes($bytes) } finally { $random.Dispose() }
        $value = [BitConverter]::ToString($bytes).Replace('-', '').ToLowerInvariant()
        [IO.File]::WriteAllText($path, $value)
    }
    $env:LENDORA_PROVISIONING_KEY = [IO.File]::ReadAllText($path).Trim()
    if ($env:LENDORA_PROVISIONING_KEY.Length -lt 32) {
        throw 'The saved provisioning key is invalid. Set LENDORA_PROVISIONING_KEY or replace runtime/lendora-provisioning.key.'
    }
    Write-Host 'Using the saved local provisioning key for all three backend services.'
}

function Get-EndpointStatus([string]$Url) {
    $response = $null
    try {
        $request = [System.Net.HttpWebRequest]::Create($Url)
        $request.Proxy = $null
        $request.AllowAutoRedirect = $false
        $request.Timeout = 4000
        $response = $request.GetResponse()
        return [int]$response.StatusCode
    } catch {
        # PowerShell may wrap a WebException thrown by the .NET method.
        for ($errorCause = $_.Exception; $null -ne $errorCause; $errorCause = $errorCause.InnerException) {
            if ($errorCause -is [System.Net.WebException] -and $null -ne $errorCause.Response) {
                $response = $errorCause.Response
                return [int]$response.StatusCode
            }
        }
        return 0
    } finally {
        if ($null -ne $response) { $response.Close() }
    }
}

function Test-LoginRequired([int]$Port, [string]$Path) {
    return (Get-EndpointStatus "http://127.0.0.1:$Port$Path") -eq 401
}

function Assert-LendoraGatewayRoute {
    $url = 'http://localhost:8090/api/v1/auth/me'
    # An anonymous startup probe should receive 401 from the account service.
    # The browser checks CORS separately. This probe has no Origin or session.
    $status = 0
    for ($attempt = 0; $attempt -lt 3; $attempt++) {
        $status = Get-EndpointStatus $url
        if ($status -eq 401) { return }
        if ($attempt -lt 2) { Start-Sleep -Seconds 1 }
    }
    $result = if ($status -eq 0) { 'no HTTP response' } else { "HTTP $status" }
    $ipv4Status = Get-EndpointStatus 'http://127.0.0.1:8090/api/v1/auth/me'
    if ($ipv4Status -eq 401) {
        throw "The account route on localhost:8090 returned $result, but the same route on 127.0.0.1:8090 returned HTTP 401. Check whether localhost resolves to a different listener on port 8090 with netstat -ano."
    }

    $directUrl = 'http://localhost:8086/api/v1/auth/me'
    $directStatus = Get-EndpointStatus $directUrl
    $directResult = if ($directStatus -eq 0) { 'no HTTP response' } else { "HTTP $directStatus" }
    throw "The Lendora account route at $url returned $result. The direct account endpoint at $directUrl returned $directResult. Expected HTTP 401 before login. Check the API Gateway console and its LENDORA_SERVICE_URL (default http://localhost:8086)."
}

function Test-Port([int]$Port, [string]$Address = '127.0.0.1') {
    $ip = [System.Net.IPAddress]::Parse($Address)
    $client = $null
    try {
        $client = [System.Net.Sockets.TcpClient]::new($ip.AddressFamily)
        return $client.ConnectAsync($ip, $Port).Wait(500) -and $client.Connected
    } catch {
        return $false
    } finally {
        if ($null -ne $client) { $client.Dispose() }
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
        if (-not $Lendora -and $Component.Port -in @(8081, 8084) -and (Test-LoginRequired $Component.Port '/api/v1/organizations')) {
            throw "The existing $($Component.Name) requires Lendora login. Stop the borrower and lender backend windows, then rerun start-demo.cmd without -Lendora for the test console. The launcher will not silently reuse authenticated backends in anonymous test mode."
        }
        if ($Lendora -and $Component.Port -in @(8081, 8084)) {
            if (-not (Test-LoginRequired $Component.Port '/api/v1/organizations')) {
                throw "Stop the existing $($Component.Name) and rerun in Lendora mode so authentication and the shared key are loaded."
            }
        }
        if ($Component.ExpectedTitle) {
            $existing = Invoke-WebRequest -UseBasicParsing -Uri "http://127.0.0.1:$($Component.Port)/" -TimeoutSec 4
            if ($existing.Content -notmatch "<title>[^<]*$([regex]::Escape($Component.ExpectedTitle))") {
                throw "Port $($Component.Port) is occupied by another frontend. Stop it before starting $($Component.Name)."
            }
        }
    } else {
        Write-Host "Starting $($Component.Name) on port $($Component.Port)..."
        Start-Process -FilePath $env:ComSpec -WorkingDirectory $directory -WindowStyle Normal `
            -ArgumentList "/d /k title $($Component.Name) & $($Component.Command)"
    }
    $null = Wait-Ready $Component.Name {
        if ($Component.ReadyPath -eq '/api/v1/auth/me') { return Test-LoginRequired $Component.Port $Component.ReadyPath }
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

function Get-ObservabilityBinaries {
    $config = Join-Path $PSScriptRoot '.env'
    if (-not (Test-Path -LiteralPath $config -PathType Leaf)) {
        throw 'Create a repository-root .env with GRAFANA_PATH and PROMETHEUS_PATH.'
    }
    $paths = @{}
    foreach ($line in Get-Content -LiteralPath $config) {
        if ($line -match '^\s*(GRAFANA_PATH|PROMETHEUS_PATH)\s*=\s*(.*?)\s*$') {
            $key, $path = $Matches[1], $Matches[2]
            if ($path -match '^"(.*)"$' -or $path -match "^'(.*)'$") { $path = $Matches[1] }
            if ($path -and -not [IO.Path]::IsPathRooted($path)) { $path = Join-Path $PSScriptRoot $path }
            $paths[$key] = $path
        }
    }
    foreach ($key in @('GRAFANA_PATH', 'PROMETHEUS_PATH')) {
        if (-not $paths[$key] -or -not (Test-Path -LiteralPath $paths[$key] -PathType Leaf)) {
            throw "Set $key in .env to the path of the installed executable."
        }
        $paths[$key] = (Resolve-Path -LiteralPath $paths[$key]).Path
    }
    return $paths
}

function Start-Observability($Paths) {
    $config = Join-Path $PSScriptRoot 'observability/prometheus.yaml'
    $storage = Join-Path $PSScriptRoot 'runtime/prometheus'
    if (-not (Test-Port 9090)) {
        $null = New-Item -ItemType Directory -Force -Path $storage
        Write-Host 'Starting Prometheus on port 9090...'
        $arguments = '--config.file="{0}" --storage.tsdb.path="{1}" --web.listen-address=127.0.0.1:9090' -f $config, $storage
        Start-Process -FilePath $Paths.PROMETHEUS_PATH -WorkingDirectory (Split-Path $Paths.PROMETHEUS_PATH) -ArgumentList $arguments
    }
    $null = Wait-Ready 'Prometheus on port 9090' {
        (Get-EndpointStatus 'http://127.0.0.1:9090/-/ready') -eq 200
    }
    # The target list can still be empty immediately after Prometheus becomes ready.
    $loaded = Get-LocalJson 9090 '/api/v1/status/config'
    if ($loaded.status -ne 'success' -or -not $loaded.data.yaml) {
        throw 'Could not read the active Prometheus configuration at http://127.0.0.1:9090/api/v1/status/config.'
    }
    if ($loaded.data.yaml -notmatch '(?m)^[ 	]*(?:-[ 	]+)?job_name:[ 	]+["'']?lending-services["'']?[ 	]*\r?$') {
        throw 'The running Prometheus on port 9090 loaded a config without the lending-services scrape job. Stop that Prometheus process and rerun start-lendora.cmd -Observability, or use observability/prometheus.yaml when starting Prometheus yourself.'
    }

    if (-not (Test-Port 3000)) {
        $bin = Split-Path $Paths.GRAFANA_PATH
        $homePath = if ((Split-Path -Leaf $bin) -eq 'bin') { Split-Path $bin } else { $bin }
        $arguments = '--homepath="{0}"' -f $homePath
        if ((Split-Path -Leaf $Paths.GRAFANA_PATH) -eq 'grafana.exe') { $arguments = "server $arguments" }
        Write-Host 'Starting Grafana on port 3000...'
        Start-Process -FilePath $Paths.GRAFANA_PATH -WorkingDirectory $homePath -ArgumentList $arguments
    }
    $null = Wait-Ready 'Grafana on port 3000' {
        (Get-LocalJson 3000 '/api/health').database -eq 'ok'
    }
}

function Invoke-PortalPost([string]$Path) {
    Invoke-RestMethod -Method Post -Uri "http://127.0.0.1:3001$Path" `
        -ContentType 'application/json' -Body '{}' -TimeoutSec 60
}

try {
    if ($Plan) {
        @($portal) + $services + $frontends | ForEach-Object {
            $directory = Join-Path $PSScriptRoot $_.Folder
            if (-not (Test-Path -LiteralPath $directory -PathType Container)) {
                throw "Missing service folder: $directory"
            }
            Write-Host "$($_.Port)  $($_.Name)  [$directory]"
        }
        if ($Observability) {
            Write-Host '9090  Prometheus [PROMETHEUS_PATH in .env]'
            Write-Host '3000  Grafana [GRAFANA_PATH in .env]'
        }
        exit 0
    }

    $observabilityBinaries = if ($Observability) { Get-ObservabilityBinaries }
    if ($Observability) {
        foreach ($service in $services) {
            if (Test-Port $service.Port) {
                throw "Stop $($service.Name) on port $($service.Port) before starting it with observability."
            }
            if ($service.Folder -eq 'fix-adapter-service') {
                $profile = if ($service.Port -eq 8082) { 'borrower' } else { 'lender' }
                $service.Command = 'call mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles={0},observability"' -f $profile
            } else {
                $service.Command += ' -Dspring-boot.run.profiles=observability'
            }
        }
    }

    if (-not (Get-Command npm.cmd -ErrorAction SilentlyContinue)) {
        throw 'npm was not found on PATH. Install Node.js before starting the demo.'
    }
    if (-not (Test-Port 5432)) {
        throw 'Start PostgreSQL on port 5432 and create the databases listed in START_DEMO.md.'
    }

    if ($Lendora) {
        foreach ($port in @(8081, 8082, 8083, 8084, 8086, 8090)) {
            if ((Test-Port $port) -or ($port -eq 8090 -and (Test-Port 8090 '::1'))) {
                throw "Port $port already has a backend running. Stop the existing borrower, lender, Lendora, FIX adapters, and API Gateway windows first, then rerun to load the shared provisioning key and routing mode consistently."
            }
        }
        Initialize-LendoraKey
        $env:LENDORA_ROUTING_ENABLED = 'true'
        Write-Host 'Lendora mode requires the lendora_db PostgreSQL database in addition to the existing demo databases.'
    }

    if ($Observability) { Start-Observability $observabilityBinaries }

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
    if ($Lendora) { Assert-LendoraGatewayRoute }
    $null = Wait-Ready 'Borrower-to-lender FIX session' {
        $adapter = Get-LocalJson 8082 '/api/v1/adapter/status'
        @($adapter.data.sessions | Where-Object { $_.loggedOn }).Count -gt 0
    }
    foreach ($app in $frontends) { Start-Component $app }

    Write-Host ''
    if ($Lendora) {
        Write-Host 'API Gateway: http://localhost:8090'
        Write-Host 'Lendora:    http://localhost:5176'
        Write-Host 'BorrowDesk: http://localhost:5173'
        Write-Host 'LendDesk:   http://localhost:5175'
    } else {
        Write-Host 'Test frontend: http://127.0.0.1:5176'
    }
    if ($Observability) {
        Write-Host 'Grafana:    http://localhost:3000'
        Write-Host 'Prometheus: http://localhost:9090'
    }
    Write-Host 'Kafka UI:      http://127.0.0.1:3001'
    Write-Host 'Each application runs in its own Command Prompt.'
} catch {
    Write-Host "Startup failed: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}
