# LAB 10: CHAOS TEST 1 - BASELINE (Normal order flow)
# Purpose: Establish baseline - Send 10 orders normally
# Expected: All orders received in sequence, Seq#1-10
# Output: resilience_log.txt shows normal progression

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "LAB 10: CHAOS TEST 1 - BASELINE" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

Write-Host "[TEST] Starting baseline test..." -ForegroundColor Yellow
Write-Host "[TEST] This test establishes normal behavior" -ForegroundColor Yellow
Write-Host "[TEST] Expected: 10 orders sent in sequence, Seq#1-10" -ForegroundColor Yellow
Write-Host ""

# Ensure backend is running
Write-Host "[SETUP] Verifying backend is running on port 9876..." -ForegroundColor Cyan
Test-NetConnection -ComputerName localhost -Port 9876 -ErrorAction SilentlyContinue | Out-Null
if ($?) {
    Write-Host "[SETUP] Backend is running" -ForegroundColor Green
} else {
    Write-Host "[SETUP] ERROR: Backend not running. Start it with: mvn exec:java" -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "[RUNNING] Sending 10 orders at 100 orders/sec..." -ForegroundColor Yellow
Write-Host "[RUNNING] Check MiniFix client for incoming orders" -ForegroundColor Yellow
Write-Host ""

# Run test
cd "d:\College\3rd Year\Lab - Capital Market Technologies\CMT Lab github\stockage\stocker\cmt"
mvn exec:java -Dexec.mainClass=com.stocker.ChaosTestClient -Dexec.args="100 10 NONE" -q

Write-Host ""
Write-Host "[VERIFY] Test complete. Check resilience_log.txt:" -ForegroundColor Cyan
Write-Host "  1. Look for Seq#1 through Seq#10 sequential" -ForegroundColor Cyan
Write-Host "  2. No [GAP] markers should appear" -ForegroundColor Cyan
Write-Host "  3. All orders should have status NEW or FILLED" -ForegroundColor Cyan
Write-Host ""
Write-Host "[NEXT] Run test_2_disruption.ps1 for network failure scenario" -ForegroundColor Yellow
