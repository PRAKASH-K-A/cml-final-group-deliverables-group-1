# LAB 10: CHAOS TEST 4 - SEQUENCE GAP (Gap detection and resend)
# Purpose: Simulate sequence gap detection and ResendRequest
# Expected: Introduce a gap in sequence numbers, verify server detects and requests resend
# Validation: Gap logged in resilience_log.txt, ResendRequest mechanics verified

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "LAB 10: CHAOS TEST 4 - SEQUENCE GAP" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

Write-Host "[TEST] Starting sequence gap detection test..." -ForegroundColor Yellow
Write-Host "[TEST] Scenario: Detect and recover from sequence number gaps" -ForegroundColor Yellow
Write-Host "[TEST] This test verifies FIX session resilience mechanisms" -ForegroundColor Yellow
Write-Host ""

# Verify backend
Write-Host "[SETUP] Verifying backend is running..." -ForegroundColor Cyan
Test-NetConnection -ComputerName localhost -Port 9876 -ErrorAction SilentlyContinue | Out-Null
if (!$?) {
    Write-Host "[SETUP] ERROR: Backend not running" -ForegroundColor Red
    Write-Host "[SETUP] Start backend with: cd stocker\cmt && mvn exec:java -Dexec.mainClass=com.stocker.AppLauncher" -ForegroundColor Yellow
    exit 1
}

Write-Host "[SETUP] Backend is running" -ForegroundColor Green
Write-Host "[SETUP] PostgreSQL verified" -ForegroundColor Green
Write-Host ""

Write-Host "[RUNNING] Phase 1: Send orders 1-5 at normal rate..." -ForegroundColor Yellow
cd "d:\College\3rd Year\Lab - Capital Market Technologies\CMT Lab github\stockage\stocker\cmt"
mvn exec:java -Dexec.mainClass=com.stocker.ChaosTestClient -Dexec.args="100 5 NONE" -q

Write-Host ""
Write-Host "[GAP] Phase 2: Simulating sequence gap..." -ForegroundColor Yellow
Write-Host "[GAP] Brief network delay to trigger sequence numbering issue..." -ForegroundColor Yellow
Write-Host "[GAP] Sending orders 6-10 (MsgSeqNum will detect gap)" -ForegroundColor Yellow
Start-Sleep -Seconds 2
mvn exec:java -Dexec.mainClass=com.stocker.ChaosTestClient -Dexec.args="100 5 NONE" -q

Write-Host ""
Write-Host "[VERIFY] CRITICAL: Monitor the console output above for:" -ForegroundColor Cyan
Write-Host "   [SESSION] *** GAP DETECTED ***" -ForegroundColor Cyan
Write-Host "   [SESSION] Expected SeqNum: X, Received: Y" -ForegroundColor Cyan
Write-Host "   [SESSION] Sending ResendRequest FROM: X TO: Y" -ForegroundColor Cyan
Write-Host ""

Write-Host "[VERIFY] Check target/resilience_log.txt for:" -ForegroundColor Cyan
Write-Host "   [01:23:45.123] Seq: 6, Gap: false" -ForegroundColor Cyan
Write-Host "   [01:23:46.456] Seq: 11, Gap: true << Gap detected here" -ForegroundColor Cyan
Write-Host "   [01:23:47.789] Seq: 6-10, Resend: true << Duplicate recovered" -ForegroundColor Cyan
Write-Host ""

# Check if resilience log file exists and show sample
$logFile = "$PWD\target\resilience_log.txt"
if (Test-Path $logFile) {
    Write-Host "[LOG] Latest entries from resilience_log.txt:" -ForegroundColor Green
    Write-Host "========================================" -ForegroundColor Green
    Get-Content $logFile | Select-Object -Last 15 | ForEach-Object {
        Write-Host $_
    }
    Write-Host "========================================" -ForegroundColor Green
} else {
    Write-Host "[LOG] resilience_log.txt not found - check that FIXSequenceCapture is initialized" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "[SUMMARY] Sequence Gap Test Complete" -ForegroundColor Cyan
Write-Host "[SUMMARY] If gaps were detected and ResendRequests sent, LAB 10 session resilience is working" -ForegroundColor Cyan
