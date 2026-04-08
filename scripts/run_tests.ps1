# LAB 9: Performance Testing Script
# Runs all three stress tests (100, 500, 1000 ops/sec) and captures output

Write-Host "================================================================================" -ForegroundColor Cyan
Write-Host " LAB 9: PERFORMANCE ENGINEERING - STRESS TEST RUNNER" -ForegroundColor Cyan
Write-Host "================================================================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "This script will run 3 stress tests and capture output files:" -ForegroundColor Yellow
Write-Host "  • Test 1: 100 orders/sec  → stress_test_100ops_output.txt"
Write-Host "  • Test 2: 500 orders/sec  → stress_test_500ops_output.txt"
Write-Host "  • Test 3: 1000 orders/sec → stress_test_1000ops_output.txt"
Write-Host ""
Write-Host "INSTRUCTIONS:" -ForegroundColor Green
Write-Host "1. Ensure AppLauncher is RUNNING (port 9876)"
Write-Host "2. Open JVisualVM and connect to AppLauncher process"
Write-Host "3. Watch CPU & Memory tabs during each test"
Write-Host "4. Take SCREENSHOTS during peak load (especially 1000 ops/sec)"
Write-Host ""
Write-Host "Starting in 5 seconds... Press Ctrl+C to cancel" -ForegroundColor Yellow
Start-Sleep -Seconds 5

$tests = @(
    @{ rate = 100; orders = 10000; name = "100 ops/sec" },
    @{ rate = 500; orders = 10000; name = "500 ops/sec" },
    @{ rate = 1000; orders = 10000; name = "1000 ops/sec" }
)

$results = @()

foreach ($test in $tests) {
    $outputFile = "stress_test_$($test.rate)ops_output.txt"
    $testName = $test.name
    
    Write-Host ""
    Write-Host "================================================================================" -ForegroundColor Cyan
    Write-Host " TEST: $testName ($($test.orders) orders)" -ForegroundColor Cyan
    Write-Host "================================================================================" -ForegroundColor Cyan
    Write-Host "Output will be saved to: $outputFile" -ForegroundColor Yellow
    Write-Host ""
    
    $startTime = Get-Date
    
    # Run the stress test and capture output
    mvn exec:java '-Dexec.mainClass=com.stocker.StressTestClient' "-Dexec.args=$($test.rate) $($test.orders)" 2>&1 | Tee-Object -FilePath $outputFile
    
    $endTime = Get-Date
    $duration = ($endTime - $startTime).TotalSeconds
    
    # Extract latency from output
    $latencyMatch = Select-String -Path $outputFile -Pattern "Avg Latency: ([\d.]+)" | Select-Object -Last 1
    
    if ($latencyMatch) {
        $avgLatency = $latencyMatch.Line -replace ".*Avg Latency: ([\d.]+).*", '$1'
        $results += [PSCustomObject]@{
            Throughput = "$($test.rate) ops/sec"
            AvgLatency = "$avgLatency us"
            Duration = "$([math]::Round($duration, 2))s"
        }
        Write-Host "✓ Test completed - Avg Latency: $avgLatency us" -ForegroundColor Green
    } else {
        Write-Host "✗ Could not extract latency from output - check output file" -ForegroundColor Red
    }
    
    Write-Host "Waiting 10 seconds before next test..." -ForegroundColor Yellow
    Start-Sleep -Seconds 10
}

Write-Host ""
Write-Host "================================================================================" -ForegroundColor Cyan
Write-Host " TEST RESULTS SUMMARY" -ForegroundColor Cyan
Write-Host "================================================================================" -ForegroundColor Cyan
$results | Format-Table -AutoSize
Write-Host ""
Write-Host "✓ All tests completed!" -ForegroundColor Green
Write-Host ""
Write-Host "NEXT STEPS:" -ForegroundColor Yellow
Write-Host "1. Review the output files for detailed metrics"
Write-Host "2. Run: python plot_latency.py  (to generate graph)"
Write-Host "3. Analyze JVisualVM screenshots for bottleneck identification"
Write-Host ""
