# LAB 10: CHAOS TEST 2 - DISRUPTION (Network failure)
# Purpose: Simulate network failure during order stream
# Expected: Send 5 orders, network fails, send 5 more orders
# Validation: Gap detected, ResendRequest sent, recovery logged

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "LAB 10: CHAOS TEST 2 - DISRUPTION" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

Write-Host "[TEST] Starting disruption test..." -ForegroundColor Yellow
Write-Host "[TEST] Scenario: Network fails between order #5 and #6" -ForegroundColor Yellow
Write-Host "[TEST] You will be prompted to:" -ForegroundColor Yellow
Write-Host "   1. Disable network (Wi-Fi/Ethernet)" -ForegroundColor Yellow
Write-Host "   2. Wait for orders to queue locally" -ForegroundColor Yellow
Write-Host "   3. Restore network" -ForegroundColor Yellow
Write-Host ""

# Verify backend
Write-Host "[SETUP] Verifying backend is running..." -ForegroundColor Cyan
Test-NetConnection -ComputerName localhost -Port 9876 -ErrorAction SilentlyContinue | Out-Null
if (!$?) {
    Write-Host "[SETUP] ERROR: Backend not running" -ForegroundColor Red
    exit 1
}

Write-Host "[SETUP] Backend is running" -ForegroundColor Green
Write-Host ""

Write-Host "[RUNNING] This test requires manual network disruption" -ForegroundColor Yellow
Write-Host "[RUNNING] Orders 1-5 should send successfully" -ForegroundColor Yellow
Write-Host "[RUNNING] At order 6, the test will pause for network disruption" -ForegroundColor Yellow
Write-Host ""
Write-Host "[ACTION] When prompted:" -ForegroundColor Cyan
Write-Host "   1. Immediately DISABLE your network (unplug Ethernet or turn off Wi-Fi)" -ForegroundColor Cyan
Write-Host "   2. Keep network disabled for ~10 seconds" -ForegroundColor Cyan
Write-Host "   3. Then RESTORE network connection" -ForegroundColor Cyan
Write-Host ""

# Simulate 5 orders before disruption
Write-Host "[PREP] Sending 5 orders (baseline)..." -ForegroundColor Yellow
cd "d:\College\3rd Year\Lab - Capital Market Technologies\CMT Lab github\stockage\stocker\cmt"
mvn exec:java -Dexec.mainClass=com.stocker.ChaosTestClient -Dexec.args="100 5 NONE" -q

Write-Host ""
Write-Host "[DISRUPTION] About to trigger network failure..." -ForegroundColor Red
Write-Host "[DISRUPTION] Press ENTER to continue" -ForegroundColor Red
Read-Host

Write-Host "[DISRUPTION] DISABLING NETWORK NOW - Disrupt your connection!" -ForegroundColor Red
Write-Host "[DISRUPTION] Waiting 15 seconds while network is down..." -ForegroundColor Red
Start-Sleep -Seconds 15

Write-Host "[RECOVERY] RESTORE NETWORK NOW" -ForegroundColor Green
Write-Host "[RECOVERY] Waiting for connections to restore..." -ForegroundColor Green
Start-Sleep -Seconds 3

Write-Host "[RUNNING] Sending orders 6-10 (should trigger gap detection)..." -ForegroundColor Yellow
mvn exec:java -Dexec.mainClass=com.stocker.ChaosTestClient -Dexec.args="100 5 NONE" -q

Write-Host ""
Write-Host "[VERIFY] Check console output for:" -ForegroundColor Cyan
Write-Host "   [SESSION] *** GAP DETECTED ***" -ForegroundColor Cyan
Write-Host "   [SESSION] Sending ResendRequest FROM: X TO: 0" -ForegroundColor Cyan
Write-Host ""
Write-Host "[VERIFY] Check resilience_log.txt for:" -ForegroundColor Cyan
Write-Host "   [GAP] markers where orders are missing" -ForegroundColor Cyan
Write-Host "   [RESEND] markers for duplicate/recovered orders" -ForegroundColor Cyan
