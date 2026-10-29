# Usage: .\dev.ps1 start|stop|restart|status
[CmdletBinding()]
param(
    [ValidateSet('start', 'stop', 'restart', 'status', 'run-service')]
    [string]$Action = 'start',

    [ValidateSet('frontend', 'backend')]
    [string]$Service
)

$ErrorActionPreference = 'Stop'

# Some Windows launch environments expose both PATH and Path. Start-Process
# rejects the duplicate keys when it builds the child process environment.
$pathValue = [System.Environment]::GetEnvironmentVariable('PATH', 'Process')
[System.Environment]::SetEnvironmentVariable('Path', $null, 'Process')
[System.Environment]::SetEnvironmentVariable('PATH', $pathValue, 'Process')

$FrontendDir = (Resolve-Path -LiteralPath $PSScriptRoot).Path
$RepoRoot = Split-Path -Parent $FrontendDir
$BackendDir = Join-Path $RepoRoot 'slowfoot-backend'

if (-not (Test-Path -LiteralPath $BackendDir -PathType Container)) {
    throw "Backend directory not found: $BackendDir"
}

$sha = [System.Security.Cryptography.SHA256]::Create()
try {
    $rootBytes = [System.Text.Encoding]::UTF8.GetBytes($FrontendDir.ToLowerInvariant())
    $rootHash = [BitConverter]::ToString($sha.ComputeHash($rootBytes)).Replace('-', '').Substring(0, 12).ToLowerInvariant()
}
finally {
    $sha.Dispose()
}

$RunDir = Join-Path ([System.IO.Path]::GetTempPath()) "slowfoot-dev-$rootHash"

function Get-ServicePaths {
    param([string]$Name)

    return @{
        PidFile = Join-Path $RunDir "$Name.json"
        Stdout = Join-Path $RunDir "$Name.out.log"
        Stderr = Join-Path $RunDir "$Name.err.log"
    }
}

function Get-TrackedProcess {
    param([string]$Name)

    $paths = Get-ServicePaths -Name $Name
    if (-not (Test-Path -LiteralPath $paths.PidFile -PathType Leaf)) {
        return $null
    }

    try {
        $record = Get-Content -LiteralPath $paths.PidFile -Raw | ConvertFrom-Json
        $process = Get-Process -Id ([int]$record.Pid) -ErrorAction Stop
        $actualStart = $process.StartTime.ToUniversalTime().Ticks.ToString()
        if ($actualStart -ne [string]$record.StartTimeUtcTicks) {
            Remove-Item -LiteralPath $paths.PidFile -Force
            return $null
        }

        return $process
    }
    catch {
        Remove-Item -LiteralPath $paths.PidFile -Force -ErrorAction SilentlyContinue
        return $null
    }
}

function Start-ServiceProcess {
    param([string]$Name)

    $existing = Get-TrackedProcess -Name $Name
    if ($null -ne $existing) {
        Write-Host "$Name is already running (PID $($existing.Id))."
        return
    }

    if (-not (Test-Path -LiteralPath $RunDir -PathType Container)) {
        New-Item -Path $RunDir -ItemType Directory -Force | Out-Null
    }

    $paths = Get-ServicePaths -Name $Name
    $powershellExe = (Get-Process -Id $PID).Path
    $arguments = '-NoProfile -ExecutionPolicy Bypass -File "{0}" run-service {1}' -f $PSCommandPath, $Name
    $process = Start-Process -FilePath $powershellExe `
        -ArgumentList $arguments `
        -WorkingDirectory $FrontendDir `
        -WindowStyle Hidden `
        -RedirectStandardOutput $paths.Stdout `
        -RedirectStandardError $paths.Stderr `
        -PassThru

    $record = @{
        Pid = $process.Id
        StartTimeUtcTicks = $process.StartTime.ToUniversalTime().Ticks.ToString()
    }
    $record | ConvertTo-Json | Set-Content -LiteralPath $paths.PidFile -Encoding UTF8

    if ($Name -eq 'frontend') {
        Write-Host "Frontend is starting at http://localhost:5378 (PID $($process.Id))."
    }
    else {
        Write-Host "Backend is starting at http://localhost:8882 (PID $($process.Id))."
    }
    Write-Host "Logs: $($paths.Stdout) and $($paths.Stderr)"
}

function Stop-ServiceProcess {
    param([string]$Name)

    $paths = Get-ServicePaths -Name $Name
    $process = Get-TrackedProcess -Name $Name
    if ($null -eq $process) {
        Write-Host "$Name is not running."
        return
    }

    & taskkill.exe /PID $process.Id /T /F | Out-Null
    $exitCode = $LASTEXITCODE
    if ($exitCode -ne 0 -and (Get-Process -Id $process.Id -ErrorAction SilentlyContinue)) {
        throw "Could not stop $Name (PID $($process.Id)); taskkill exited with code $exitCode."
    }

    Remove-Item -LiteralPath $paths.PidFile -Force -ErrorAction SilentlyContinue
    Write-Host "$Name stopped."
}

function Show-ServiceStatus {
    param([string]$Name)

    $process = Get-TrackedProcess -Name $Name
    if ($null -eq $process) {
        Write-Host "$Name`: stopped"
    }
    else {
        Write-Host "$Name`: running (PID $($process.Id))"
    }
}

if ($Action -eq 'run-service') {
    if (-not $Service) {
        throw 'Specify frontend or backend for run-service.'
    }

    if ($Service -eq 'frontend') {
        if (-not (Get-Command npm.cmd -ErrorAction SilentlyContinue)) {
            throw 'npm.cmd was not found on PATH.'
        }
        Set-Location -LiteralPath $FrontendDir
        $ErrorActionPreference = 'Continue'
        & npm.cmd run dev
    }
    else {
        if (-not (Get-Command java.exe -ErrorAction SilentlyContinue)) {
            throw 'java.exe was not found on PATH.'
        }
        Set-Location -LiteralPath $BackendDir
        $ErrorActionPreference = 'Continue'
        & .\mvnw.cmd spring-boot:run '-Dspring-boot.run.jvmArguments=-Xmx256m'
    }

    exit $LASTEXITCODE
}

switch ($Action) {
    'start' {
        Start-ServiceProcess -Name 'backend'
        Start-ServiceProcess -Name 'frontend'
    }
    'stop' {
        Stop-ServiceProcess -Name 'frontend'
        Stop-ServiceProcess -Name 'backend'
    }
    'restart' {
        Stop-ServiceProcess -Name 'frontend'
        Stop-ServiceProcess -Name 'backend'
        Start-ServiceProcess -Name 'backend'
        Start-ServiceProcess -Name 'frontend'
    }
    'status' {
        Show-ServiceStatus -Name 'backend'
        Show-ServiceStatus -Name 'frontend'
    }
}
