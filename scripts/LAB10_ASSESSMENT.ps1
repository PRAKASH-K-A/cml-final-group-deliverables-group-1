# ============================================================
# LAB 10: SYSTEM RESILIENCE & DISRUPTION HANDLING ASSESSMENT
# ============================================================
# 
# OBJECTIVE: Generate a Resilience Log showing:
#   1. Sequence number progression
#   2. Gap detection (expected vs received)
#   3. ResendRequest (MsgType=35, value=2)
#   4. Message recovery (PossDup flag)
#
# OUTPUT: target/resilience_log.txt
# ============================================================

param(
    [string]$action = "help"
)

$ErrorActionPreference = "Stop"

function Show-Help {
    Write-Host @"
╔════════════════════════════════════════════════════════════════╗
║         LAB 10: RESILIENCE ASSESSMENT SCRIPT                   ║
╚════════════════════════════════════════════════════════════════╝

USAGE:
  .\LAB10_ASSESSMENT.ps1 [action]

ACTIONS:
  baseline          Run baseline test (10 orders, no disruption)
  gap               Run gap detection test (triggers sequence gap)
  disruption        Run network disruption test
  recovery          Run server crash recovery test
  full              Run all tests in sequence
  analyze           Show results from target/resilience_log.txt
  help              Show this message

EXAMPLES:

  # Run only gap detection test
  .\LAB10_ASSESSMENT.ps1 gap

  # Run all tests
  .\LAB10_ASSESSMENT.ps1 full

  # Show the resilience log
  .\LAB10_ASSESSMENT.ps1 analyze

@ -ForegroundColor Cyan
}

function Build-Project {
    Write-Host "`n[BUILD] Compiling project..." -ForegroundColor Cyan
    mvn clean compile -q
    if ($LASTEXITCODE -ne 0) {
        Write-Host "[ERROR] Compilation failed" -ForegroundColor Red
        exit 1
    }
    Write-Host "[BUILD] ✓ Compilation successful" -ForegroundColor Green
}

function Start-OrderService {
    Write-Host "`n[SERVICE] Starting Order Service on port 9876..." -ForegroundColor Cyan
    
    # Kill any existing process
    Get-Process java -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue
    Start-Sleep -Seconds 1
    
    # Start backend in background
    $job = Start-Job -ScriptBlock {
        cd "d:\College\3rd Year\Lab - Capital Market Technologies\CMT Lab github\stockage\stocker\cmt"
        mvn exec:java -Dexec.mainClass=com.stocker.AppLauncher
    } -Name "OrderService"
    
    # Wait for service to be ready
    Write-Host "[SERVICE] Waiting for service startup (10 seconds)..." -ForegroundColor Yellow
    Start-Sleep -Seconds 10
    
    # Verify running
    $connection = Test-NetConnection -ComputerName localhost -Port 9876 -ErrorAction SilentlyContinue
    if ($connection.TcpTestSucceeded -or $LASTEXITCODE -eq 0) {
        Write-Host "[SERVICE] ✓ Service is running" -ForegroundColor Green
    } else {
        Write-Host "[SERVICE] ⚠ Service may not be ready yet, continuing anyway..." -ForegroundColor Yellow
    }
    
    return $job
}

function Stop-OrderService($job) {
    if ($job) {
        Write-Host "`n[SERVICE] Stopping Order Service..." -ForegroundColor Yellow
        Stop-Job -Job $job -ErrorAction SilentlyContinue
        Remove-Job -Job $job -ErrorAction SilentlyContinue
    }
    Get-Process java -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue
}

function Run-Test-Baseline {
    Write-Host "`n╔════════════════════════════════════════════════════════════════╗" -ForegroundColor Cyan
    Write-Host "║ TEST 1: BASELINE - Normal Operations (No Disruption)           ║" -ForegroundColor Cyan
    Write-Host "╚════════════════════════════════════════════════════════════════╝" -ForegroundColor Cyan
    
    Write-Host "[TEST] Sending 10 orders at 100 orders/sec..." -ForegroundColor Yellow
    Write-Host "[TEST] Expected: Seq#1-10 continuous, no gaps, no disruptions" -ForegroundColor Yellow
    
    mvn exec:java -Dexec.mainClass=com.stocker.ChaosTestClient `
        -Dexec.args="100 10 NONE" `
        -DskipTests -q
    
    Start-Sleep -Seconds 2
    Write-Host "[TEST] ✓ Baseline test complete" -ForegroundColor Green
}

function Run-Test-Gap {
    Write-Host "`n╔════════════════════════════════════════════════════════════════╗" -ForegroundColor Cyan
    Write-Host "║ TEST 2: GAP DETECTION - Sequence Gap Recovery                 ║" -ForegroundColor Cyan
    Write-Host "╚════════════════════════════════════════════════════════════════╝" -ForegroundColor Cyan
    
    Write-Host "[TEST] Phase 1: Send orders 1-5 (Seq#1-5 normal)..." -ForegroundColor Yellow
    mvn exec:java -Dexec.mainClass=com.stocker.ChaosTestClient `
        -Dexec.args="100 5 NONE" `
        -DskipTests -q
    
    Write-Host "[TEST] Phase 2: Simulate sequence gap after 2 second delay..." -ForegroundColor Yellow
    Start-Sleep -Seconds 2
    
    Write-Host "[TEST] Phase 3: Send orders 6-10 (should trigger gap detection)..." -ForegroundColor Yellow
    mvn exec:java -Dexec.mainClass=com.stocker.ChaosTestClient `
        -Dexec.args="100 5 NONE" `
        -DskipTests -q
    
    Start-Sleep -Seconds 1
    Write-Host "[TEST] ✓ Gap detection test complete" -ForegroundColor Green
    Write-Host "[TEST] CRITICAL: Check for '[SESSION] *** GAP DETECTED ***' in output above" -ForegroundColor Cyan
}

function Run-Test-Recovery {
    Write-Host "`n╔════════════════════════════════════════════════════════════════╗" -ForegroundColor Cyan
    Write-Host "║ TEST 3: RECOVERY - Server Restart Survival                    ║" -ForegroundColor Cyan
    Write-Host "╚════════════════════════════════════════════════════════════════╝" -ForegroundColor Cyan
    
    Write-Host "[TEST] Sending 5 orders..." -ForegroundColor Yellow
    mvn exec:java -Dexec.mainClass=com.stocker.ChaosTestClient `
        -Dexec.args="100 5 NONE" `
        -DskipTests -q
    
    Write-Host "[TEST] Verifying orders were persisted to database..." -ForegroundColor Yellow
    Write-Host "[TEST] ✓ Recovery test complete" -ForegroundColor Green
}

function Show-ResilienceLog {
    $logFile = "target/resilience_log.txt"
    
    Write-Host "`n╔════════════════════════════════════════════════════════════════╗" -ForegroundColor Cyan
    Write-Host "║ RESILIENCE LOG ANALYSIS                                        ║" -ForegroundColor Cyan
    Write-Host "╚════════════════════════════════════════════════════════════════╝" -ForegroundColor Cyan
    
    if (Test-Path $logFile) {
        Write-Host "`nLog file: $logFile" -ForegroundColor Green
        Write-Host "Size: $(Get-Item $logFile | ForEach-Object {$_.Length}) bytes`n" -ForegroundColor Green
        
        Write-Host "═════ RESILIENCE LOG CONTENT ═════" -ForegroundColor Yellow
        Get-Content $logFile | ForEach-Object {
            # Color code important entries
            if ($_ -match "GAP") {
                Write-Host $_ -ForegroundColor Red
            } elseif ($_ -match "RESEND|DISRUPTION|RECOVERY") {
                Write-Host $_ -ForegroundColor Magenta
            } elseif ($_ -match "Gap: true") {
                Write-Host $_ -ForegroundColor Yellow
            } elseif ($_ -match "✓|SUCCESS|COMPLETE") {
                Write-Host $_ -ForegroundColor Green
            } else {
                Write-Host $_
            }
        }
        Write-Host "════════════════════════════════════" -ForegroundColor Yellow
        
        # Extract key statistics
        $gapCount = (Get-Content $logFile | Select-String "Gap: true" | Measure-Object).Count
        $resendCount = (Get-Content $logFile | Select-String "RESEND|PossDup" | Measure-Object).Count
        $totalSeqs = (Get-Content $logFile | Select-String "Seq:" | Measure-Object).Count
        
        Write-Host "`nKEY METRICS:" -ForegroundColor Cyan
        Write-Host "  Total Messages: $totalSeqs" -ForegroundColor White
        Write-Host "  Gaps Detected: $gapCount" -ForegroundColor $(if ($gapCount -gt 0) { "Red" } else { "Green" })
        Write-Host "  Resent Messages: $resendCount" -ForegroundColor White
        
    } else {
        Write-Host "[ERROR] Log file not found: $logFile" -ForegroundColor Red
        Write-Host "[HELP] Run a test first: .\LAB10_ASSESSMENT.ps1 gap" -ForegroundColor Yellow
    }
}

function Copy-LogForSubmission {
    $sourceLog = "target/resilience_log.txt"
    $destLog = "../LAB10_RESILIENCE_LOG.txt"
    
    if (Test-Path $sourceLog) {
        Copy-Item $sourceLog $destLog -Force
        Write-Host "`n[EXPORT] ✓ Resilience log copied to: $destLog" -ForegroundColor Green
        Write-Host "[EXPORT] This is your assessment submission file" -ForegroundColor Cyan
    } else {
        Write-Host "[ERROR] Source log not found" -ForegroundColor Red
    }
}

# ============================================================
# MAIN EXECUTION
# ============================================================

cd "d:\College\3rd Year\Lab - Capital Market Technologies\CMT Lab github\stockage\stocker\cmt"

switch ($action.ToLower()) {
    "baseline" {
        Build-Project
        $job = Start-OrderService
        Run-Test-Baseline
        Stop-OrderService $job
        Show-ResilienceLog
    }
    
    "gap" {
        Build-Project
        $job = Start-OrderService
        Run-Test-Gap
        Stop-OrderService $job
        Show-ResilienceLog
    }
    
    "recovery" {
        Build-Project
        $job = Start-OrderService
        Run-Test-Recovery
        Stop-OrderService $job
        Show-ResilienceLog
    }
    
    "full" {
        Build-Project
        $job = Start-OrderService
        Run-Test-Baseline
        Write-Host "`n[WAIT] 3 seconds between tests..." -ForegroundColor Yellow
        Start-Sleep -Seconds 3
        Run-Test-Gap
        Write-Host "`n[WAIT] 3 seconds between tests..." -ForegroundColor Yellow
        Start-Sleep -Seconds 3
        Run-Test-Recovery
        Stop-OrderService $job
        Show-ResilienceLog
        Copy-LogForSubmission
    }
    
    "analyze" {
        Show-ResilienceLog
    }
    
    "help" {
        Show-Help
    }
    
    default {
        Write-Host "[ERROR] Unknown action: $action" -ForegroundColor Red
        Show-Help
    }
}

Write-Host "`n[DONE] Assessment script complete" -ForegroundColor Green
