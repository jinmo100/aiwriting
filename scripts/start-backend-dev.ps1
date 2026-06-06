param(
    [string]$EnvFile = ".env.dev.local",
    [switch]$WithTunnel,
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$GradleArgs
)

$ErrorActionPreference = "Stop"

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
$envPath = Join-Path $repoRoot $EnvFile

if (Test-Path $envPath) {
    Write-Host "Loading environment from $envPath"
    Get-Content $envPath | ForEach-Object {
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
} else {
    Write-Warning "Env file not found: $envPath. Continuing with application defaults and current process environment."
}

if ($WithTunnel) {
    & (Join-Path $PSScriptRoot "start-vps-postgres-tunnel.ps1")
}

if (-not $GradleArgs -or $GradleArgs.Count -eq 0) {
    $GradleArgs = @("bootRun")
}

Push-Location $repoRoot
try {
    & .\gradlew.bat @GradleArgs
} finally {
    Pop-Location
}
