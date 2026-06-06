param(
    [int]$PostgresLocalPort = $(if ($env:DEV_TUNNEL_POSTGRES_LOCAL_PORT) { [int]$env:DEV_TUNNEL_POSTGRES_LOCAL_PORT } else { 5432 }),
    [int]$RedisLocalPort = $(if ($env:DEV_TUNNEL_REDIS_LOCAL_PORT) { [int]$env:DEV_TUNNEL_REDIS_LOCAL_PORT } else { 6379 }),
    [string]$TunnelHost = $env:DEV_TUNNEL_HOST,
    [string]$TunnelUser = $env:DEV_TUNNEL_USER,
    [string]$IdentityFile = $env:DEV_TUNNEL_IDENTITY_FILE,
    [int]$PostgresRemotePort = $(if ($env:DEV_TUNNEL_POSTGRES_REMOTE_PORT) { [int]$env:DEV_TUNNEL_POSTGRES_REMOTE_PORT } else { 5432 }),
    [int]$RedisRemotePort = $(if ($env:DEV_TUNNEL_REDIS_REMOTE_PORT) { [int]$env:DEV_TUNNEL_REDIS_REMOTE_PORT } else { 6379 }),
    [string]$RemoteHost = $(if ($env:DEV_TUNNEL_REMOTE_HOST) { $env:DEV_TUNNEL_REMOTE_HOST } else { "127.0.0.1" })
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($TunnelHost) -or [string]::IsNullOrWhiteSpace($TunnelUser)) {
    throw @"
Missing SSH tunnel target.

This script is optional and intentionally has no repository default host/user.
Pass -TunnelHost and -TunnelUser, or set DEV_TUNNEL_HOST and DEV_TUNNEL_USER in .env.dev.local / your shell.
Example:
  .\scripts\start-vps-postgres-tunnel.ps1 -TunnelHost example.com -TunnelUser ubuntu -IdentityFile C:\path\to\id_rsa
"@
}

$forwardArgs = @(
    "$PostgresLocalPort`:$RemoteHost`:$PostgresRemotePort",
    "$RedisLocalPort`:$RemoteHost`:$RedisRemotePort"
)

$existing = Get-CimInstance Win32_Process -Filter "name = 'ssh.exe'" |
    Where-Object {
        $cmd = $_.CommandLine
        ($cmd -match [regex]::Escape($forwardArgs[0])) -and
        ($cmd -match [regex]::Escape($forwardArgs[1])) -and
        ($cmd -match [regex]::Escape("$TunnelUser@$TunnelHost"))
    }

if ($existing) {
    Write-Host "SSH tunnels already running:"
    $existing | Select-Object ProcessId, CommandLine | Format-List
} else {
    Write-Host "Starting SSH tunnels:"
    Write-Host "  PostgreSQL: 127.0.0.1:$PostgresLocalPort -> $TunnelHost`:$RemoteHost`:$PostgresRemotePort"
    Write-Host "  Redis:      127.0.0.1:$RedisLocalPort -> $TunnelHost`:$RemoteHost`:$RedisRemotePort"

    $sshArgs = @(
        "-o", "ExitOnForwardFailure=yes",
        "-o", "ServerAliveInterval=30",
        "-o", "ServerAliveCountMax=3",
        "-N",
        "-L", $forwardArgs[0],
        "-L", $forwardArgs[1],
        "$TunnelUser@$TunnelHost"
    )

    if (-not [string]::IsNullOrWhiteSpace($IdentityFile)) {
        $sshArgs = @("-i", $IdentityFile) + $sshArgs
    }

    $sshProcess = Start-Process -FilePath "ssh.exe" `
        -ArgumentList $sshArgs `
        -WindowStyle Hidden `
        -PassThru

    Start-Sleep -Seconds 2

    if ($sshProcess.HasExited) {
        throw "SSH tunnel process exited immediately with code $($sshProcess.ExitCode)."
    }
}

if (-not (Test-NetConnection 127.0.0.1 -Port $PostgresLocalPort -InformationLevel Quiet)) {
    throw "PostgreSQL tunnel check failed: 127.0.0.1:$PostgresLocalPort is not reachable."
}

if (-not (Test-NetConnection 127.0.0.1 -Port $RedisLocalPort -InformationLevel Quiet)) {
    throw "Redis tunnel check failed: 127.0.0.1:$RedisLocalPort is not reachable."
}

Write-Host "PostgreSQL tunnel ready: jdbc:postgresql://127.0.0.1:$PostgresLocalPort/<database>"
Write-Host "Redis tunnel ready:      redis://127.0.0.1:$RedisLocalPort/0"
