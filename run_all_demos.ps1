# Fixed demo settings: 100 clients per scenario and 600 seconds (10 minutes) timeout guard
$ClientCount = 100
$TimeoutSeconds = 600

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $ScriptDir

$LogsDir = Join-Path $ScriptDir "logs"
if (-not (Test-Path $LogsDir)) {
    New-Item -ItemType Directory -Path $LogsDir | Out-Null
}

Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host " Starting Bureaucratic System Demos Execution" -ForegroundColor Cyan
Write-Host " Citizens per scenario: $ClientCount"
Write-Host " Logs destination:      $LogsDir"
Write-Host " Timeout Guard:         Active for RACE_CONDITION_QUEUE and DEADLOCK (Timeout: ${TimeoutSeconds}s)"
Write-Host "=================================================================" -ForegroundColor Cyan

# Compile project before running
Write-Host "[Build] Compiling project with Maven..." -ForegroundColor Yellow
mvn compile -q

function Run-DemoScenario {
    param (
        [string]$Mode,
        [string]$Guard,
        [string]$Timeout,
        [string]$LogFileName
    )

    $LogPath = Join-Path $LogsDir $LogFileName
    Write-Host ""
    Write-Host "-----------------------------------------------------------------" -ForegroundColor Green
    Write-Host ">>> Running Demo: $Mode (Guard: $Guard, Timeout: $(if ($Timeout) { $Timeout + 's' } else { 'N/A' }))" -ForegroundColor Green
    Write-Host ">>> Output log:   $LogPath"
    Write-Host "-----------------------------------------------------------------" -ForegroundColor Green

    if ($Guard -eq "true") {
        java -cp target/classes bureaucraticSystem.demo.Demo $Mode $ClientCount $Guard $Timeout 2>&1 | Tee-Object -FilePath $LogPath -Encoding utf8
    } else {
        java -cp target/classes bureaucraticSystem.demo.Demo $Mode $ClientCount $Guard 2>&1 | Tee-Object -FilePath $LogPath -Encoding utf8
    }

    Write-Host ">>> Completed $Mode. Log saved to $LogPath" -ForegroundColor Green
    Start-Sleep -Seconds 1
}

# 1. ALL_GOOD (Safe components, completes naturally in ~430s)
Run-DemoScenario -Mode "ALL_GOOD" -Guard "false" -Timeout "" -LogFileName "all_good.log"

# 2. RACE_CONDITION_SCHEDULER (Singleton fault, clients terminate immediately on missing queues)
Run-DemoScenario -Mode "RACE_CONDITION_SCHEDULER" -Guard "false" -Timeout "" -LogFileName "race_condition_scheduler.log"

# 3. RACE_CONDITION_QUEUE (Unsafe LinkedList queue: drops tickets causing clients to hang; requires timeout guard)
Run-DemoScenario -Mode "RACE_CONDITION_QUEUE" -Guard "true" -Timeout $TimeoutSeconds -LogFileName "race_condition_queue.log"

# 4. RACE_CONDITION_COUNTER (Non-atomic counter lost updates, safe stamp/queue, completes naturally)
Run-DemoScenario -Mode "RACE_CONDITION_COUNTER" -Guard "false" -Timeout "" -LogFileName "race_condition_counter.log"

# 5. DEADLOCK (UnsafeStamp + UnsafeCounter circular wait: deadlocks desks; requires timeout guard)
Run-DemoScenario -Mode "DEADLOCK" -Guard "true" -Timeout $TimeoutSeconds -LogFileName "deadlock.log"

Write-Host ""
Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host " All 5 demos have finished executing!" -ForegroundColor Cyan
Write-Host " Log files created in '$LogsDir':"
Get-ChildItem -Path $LogsDir | Select-Object Name, Length, LastWriteTime | Format-Table -AutoSize
Write-Host "=================================================================" -ForegroundColor Cyan
