#!/bin/bash
# Simple stress test runner - bypasses Maven complexity

cd "d:\College\3rd Year\Lab - Capital Market Technologies\CMT Lab github\stockage\stocker\cmt"

echo "Building classpath..."
# Create classpath
$cp = "target/classes"
$libs = @(Get-ChildItem target/dependency/*.jar 2>/dev/null)
foreach($lib in $libs) {
    $cp = $cp + ";" + $lib.FullName
}

echo "Generated classpath length: $($cp.Length) chars"
echo ""
echo "================================================"
echo "TEST 1: 100 orders/sec"
echo "================================================"
echo "Starting at $(Get-Date -Format 'HH:mm:ss')"
echo ""

java -cp "$cp" com.stocker.StressTestClient 100 10000 > stress_test_100ops_output.txt 2>&1

echo "Completed at $(Get-Date -Format 'HH:mm:ss')"
echo ""
echo "Extracting latency from output..."

$latency = Select-String -Path "stress_test_100ops_output.txt" -Pattern "Avg Latency: ([\d.]+)" | Select-Object -Last 1
if ($latency) {
    $latency.Line
} else {
    echo "Could not find latency data - check output file"
    echo "First 20 lines:"
    Get-Content "stress_test_100ops_output.txt" | Select-Object -First 20
}
