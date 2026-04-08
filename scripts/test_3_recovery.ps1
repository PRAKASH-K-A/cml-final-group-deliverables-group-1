# LAB 10: CHAOS TEST 3 - RECOVERY (Server crash and restart)
# Purpose: Simulate backend server crash mid-operation
# Expected: Send 5 orders, crash server, send 5 more orders, restart server, verify recovery
# Validation: Orders recovered from persistence, sequence numbers preserved

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "LAB 10: CHAOS TEST 3 - RECOVERY" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

Write-Host "[TEST] Starting server recovery test..." -ForegroundColor Yellow
Write-Host "[TEST] Scenario: Backend server crashes, orders persist, server restarts" -ForegroundColor Yellow
Write-Host "[TEST] You will be prompted to:" -ForegroundColor Yellow
Write-Host "   1. Kill the AppLauncher process" -ForegroundColor Yellow
Write-Host "   2. Restart the backend" -ForegroundColor Yellow
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

Write-Host "[RUNNING] First phase: Send 5 baseline orders" -ForegroundColor Yellow
cd "d:\College\3rd Year\Lab - Capital Market Technologies\CMT Lab github\stockage\stocker\cmt"
mvn exec:java -Dexec.mainClass=com.stocker.ChaosTestClient -Dexec.args="100 5 NONE" -q

Write-Host ""
Write-Host "[CRASH] Ready to simulate server crash..." -ForegroundColor Red
Write-Host "[CRASH] This test will kill the AppLauncher Java process" -ForegroundColor Red
Write-Host "[CRASH] You will then restart it manually" -ForegroundColor Red
Write-Host "[CRASH] Press ENTER to continue" -ForegroundColor Red
Read-Host

Write-Host "[CRASH] Killing AppLauncher process..." -ForegroundColor Red
Get-Process | Where-Object {$_.ProcessName -like "*java*"} | Stop-Process -Force -ErrorAction SilentlyContinue
Start-Sleep -Seconds 2

Write-Host "[CRASH] Process killed - backend is now down" -ForegroundColor Red
Write-Host ""

Write-Host "[QUEUING] Now backend is offline, sending 5 more orders..." -ForegroundColor Yellow
Write-Host "[QUEUING] These will fail to reach backend but are tracked locally" -ForegroundColor Yellow
mvn exec:java -Dexec.mainClass=com.stocker.ChaosTestClient -Dexec.args="100 5 NONE" -q

Write-Host ""
Write-Host "[RESTART] Restarting backend server..." -ForegroundColor Green
Write-Host "[RESTART] Starting AppLauncher..." -ForegroundColor Green
Start-Process cmd.exe -ArgumentList "/c cd stocker\cmt && mvn clean compile exec:java -Dexec.mainClass=com.stocker.AppLauncher" -WindowStyle Minimized -ErrorAction SilentlyContinue
Start-Sleep -Seconds 5

Write-Host "[RESTART] Waiting for backend to initialize..." -ForegroundColor Green
for ($i = 0; $i -lt 10; $i++) {
    Test-NetConnection -ComputerName localhost -Port 9876 -ErrorAction SilentlyContinue | Out-Null
    if ($?) {
        Write-Host "[RESTART] Backend is online again" -ForegroundColor Green
        break
    }
    Write-Host "[RESTART] Attempt $($i+1)/10..." -ForegroundColor Cyan
    Start-Sleep -Seconds 1
}

Write-Host ""
Write-Host "[VERIFY] Check database for recovered orders:" -ForegroundColor Cyan
Write-Host "   SELECT order_id, customer_id, order_status, created_at" -ForegroundColor Cyan
Write-Host "   FROM orders ORDER BY created_at DESC LIMIT 10;" -ForegroundColor Cyan
Write-Host ""
Write-Host "[VERIFY] Expected: 10 orders total (5 before crash + 5 after crash + recovery)" -ForegroundColor Cyan
Write-Host "[VERIFY] Check resilience_log.txt for sequence recovery messages" -ForegroundColor Cyan
