param(
    [string]$EnvFile = ".env.dev.local",
    [switch]$WithTunnel,
    [int]$BackendPort = 8080,
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$GradleArgs
)

$ErrorActionPreference = "Stop"

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
$envPath = Join-Path $repoRoot $EnvFile

function Load-EnvFile {
    param([string]$Path)

    if (-not (Test-Path $Path)) {
        Write-Warning "Env file not found: $Path. Continuing with application defaults and current process environment."
        return
    }

    Write-Host "Loading environment from $Path"
    Get-Content $Path | ForEach-Object {
        $line = $_.Trim()
        if (-not $line -or $line.StartsWith("#")) {
            return
        }

        $idx = $line.IndexOf("=")
        if ($idx -le 0) {
            return
        }

        $name = $line.Substring(0, $idx).Trim()
        $value = $line.Substring($idx + 1).Trim()
        if (($value.StartsWith('"') -and $value.EndsWith('"')) -or ($value.StartsWith("'") -and $value.EndsWith("'"))) {
            $value = $value.Substring(1, $value.Length - 2)
        }

        [Environment]::SetEnvironmentVariable($name, $value, "Process")
    }
}

function Get-PortListenerSummary {
    param([int]$Port)

    $listeners = @(Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue)
    if (-not $listeners) {
        return ""
    }

    return ($listeners | ForEach-Object {
        $process = Get-Process -Id $_.OwningProcess -ErrorAction SilentlyContinue
        "$($_.LocalAddress):$($_.LocalPort) pid=$($_.OwningProcess) process=$($process.ProcessName)"
    }) -join "; "
}

function Test-BackendAlreadyRunning {
    param([int]$Port)

    $listeners = @(Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue)
    if (-not $listeners) {
        return $false
    }

    try {
        $response = Invoke-WebRequest `
            -Uri "http://127.0.0.1:$Port/api/auth/me" `
            -UseBasicParsing `
            -TimeoutSec 3

        return $response.StatusCode -ge 200 -and $response.StatusCode -lt 500
    } catch {
        return $false
    }
}

Load-EnvFile -Path $envPath

if (-not $PSBoundParameters.ContainsKey("BackendPort") -and $env:SERVER_PORT) {
    $BackendPort = [int]$env:SERVER_PORT
}

if ($WithTunnel) {
    & (Join-Path $PSScriptRoot "start-vps-postgres-tunnel.ps1")
}

if (-not $GradleArgs -or $GradleArgs.Count -eq 0) {
    $GradleArgs = @("bootRun")
}

$startsBackend = $GradleArgs -contains "bootRun"
if ($startsBackend) {
    if (Test-BackendAlreadyRunning -Port $BackendPort) {
        Write-Host "Backend already running at http://127.0.0.1:$BackendPort (/api/auth/me OK). Skipping bootRun."
        exit 0
    }

    $portListenerSummary = Get-PortListenerSummary -Port $BackendPort
    if ($portListenerSummary) {
        throw "Backend port $BackendPort is already in use, but /api/auth/me is not reachable. Listener(s): $portListenerSummary"
    }
}

Push-Location $repoRoot
try {
    & .\gradlew.bat @GradleArgs
    if ($LASTEXITCODE -ne 0) {
        exit $LASTEXITCODE
    }
} finally {
    Pop-Location
}
