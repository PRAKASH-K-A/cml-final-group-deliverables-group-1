# LAB 9: JVisualVM PROFILING GUIDE

## Overview
This guide walks you through using JVisualVM to profile your Order Management System during stress testing. JVisualVM provides real-time insights into:
- **Heap Memory Usage** (GC pressure detection)
- **CPU Usage** (bottleneck identification)
- **Thread Activity**
- **Memory Statistics**

---

## Prerequisites
- ✅ Java JDK 11+ installed (includes JVisualVM)
- ✅ AppLauncher running
- ✅ MiniFix stress test process ready to run

---

## PART 1: LAUNCHING JVISUALVM

### Option 1: Launch from Terminal

#### PowerShell:
```powershell
jvisualvm
```

#### Command Prompt:
```cmd
jvisualvm
```

### Option 2: Locate JVisualVM Executable
- **Windows**: `%JAVA_HOME%\bin\jvisualvm.exe`
- **Example**: `C:\Program Files\Java\jdk-11.0.x\bin\jvisualvm.exe`

### Expected Output:
JVisualVM window opens with a list of running Java processes on the left panel.

---

## PART 2: CONNECTING TO APPAUNCHER

### Step 1: Identify AppLauncher Process
1. Look at the **Left Panel** in JVisualVM
2. Find the process tree under "Local"
3. Look for: `com.stocker.AppLauncher` or similar Java process
4. It should show the process ID (PID)

### Step 2: Connect to Process
1. **Double-click** on `AppLauncher` process
2. JVisualVM will open a new tab with the process details
3. Wait for the connection to establish (you'll see tabs appear)

### Expected Tabs:
- Overview
- Monitor
- Threads
- Sampler
- Profiler

---

## PART 3: MONITOR TAB - HEAP MEMORY PROFILING

### Step 1: Click "Monitor" Tab
Location: `[Process Tab] → Monitor`

### Step 2: Observe Heap Memory Graph
**Key Metrics:**
- **Heap Size**: Total allocated memory (blue line)
- **Heap Used**: Currently used memory (red area)
- **Sawtooth Pattern**: Normal behavior when GC runs
  - Memory rises as objects allocate
  - Sharp drop when GC cleans up
  - Repeats periodically

### Step 3: Interpretation

#### ✅ GOOD - Minimal Sawtooth:
- GC runs infrequently (every 30+ seconds)
- Memory stays under 50% of heap max
- Avg latency stable

#### ⚠️ WARNING - Frequent Sawtooth:
- GC runs every 5-10 seconds
- Memory spikes to 80%+ of heap
- Avg latency increases during GC
- **Indicates**: Database or logging bottleneck

#### ❌ CRITICAL - Continuous Sawtooth:
- GC runs constantly (1-3 sec intervals)
- Memory near 100% of heap
- System becomes unresponsive
- **Fix**: Implement batch inserts or reduce logging

### Step 4: Screenshot During Stress Test

**When to Capture:**
1. Start the stress test (100 ops/sec first)
2. Wait 20 seconds for memory pattern to stabilize
3. **Take Screenshot** (Print Screen or screenshot tool)
4. Let test run to completion

**What to Look For:**
- Time axis (bottom): Shows test duration
- Memory graph: Shows sawtooth pattern
- Note the frequency and magnitude of GC pauses

**Save As:**
- `jvisualvm_heap_100ops.png`
- `jvisualvm_heap_500ops.png`
- `jvisualvm_heap_1000ops.png`

---

## PART 4: SAMPLER TAB - CPU PROFILING

### Step 1: Click "Sampler" Tab
Location: `[Process Tab] → Sampler`

### Step 2: Start CPU Sampling
1. Click the **"CPU"** button (not "Memory")
2. Status shows: "Sampling in progress..."
3. JVisualVM starts collecting CPU metrics

### Step 3: Run Stress Test
1. Open a new terminal
2. Start stress test: `powershell -ExecutionPolicy Bypass -File run_stress_tests.ps1`
3. Or run one test: `java -cp target/classes:target/lib/* com.stocker.StressTestClient 100 10000`

### Step 4: Monitor Sampling Progress
- JVisualVM continuously updates the method list
- **Wait** until 1000+ orders are processed
- Methods appear ranked by CPU time

### Step 5: Identify Top 5 CPU-Consuming Methods
When sampling completes or after 30+ seconds:
1. Look at the "Hot Classes" or "Methods" section
2. Notice the table with columns:
   - **Method Name**: Full qualified method name
   - **Samples**: Number of profiling hits
   - **Time**: Total CPU time (ms or %)
   - **%**: Percentage of total CPU time

3. **Record Top 5**:
   ```
   1. [method_name] - X.XX% CPU
   2. [method_name] - X.XX% CPU
   3. [method_name] - X.XX% CPU
   4. [method_name] - X.XX% CPU
   5. [method_name] - X.XX% CPU
   ```

### Step 6: Interpretation

#### Expected Results (Balanced System):
1. `DatabaseManager.write()` - 25-35% CPU (I/O bound, acceptable)
2. `OrderBook.match()` - 20-30% CPU (Matching logic)
3. `Session.sendToTarget()` - 10-15% CPU (FIX serialization)
4. `java.util.concurrent slots` - 5-10% CPU (Queue operations)
5. `Garbage Collector` - 5-10% CPU (Memory management)

#### Bottleneck Indicator (Database/IO):
- `DatabaseManager.write()` > 50%
- `PreparedStatement.execute()` > 40%
- **Solution**: Implement batch inserts (STEP E)

#### Bottleneck Indicator (Logging):
- `System.out.println()` > 30%
- `PrintStream.write()` > 25%
- **Solution**: Reduce console logging frequency

### Step 7: Screenshot CPU Sampling
1. Click **"Stop"** button to stop sampling
2. Wait for the results table to populate completely
3. **Take Screenshot** showing top 10 methods
4. Ensure you can read:
   - Method names
   - CPU percentages
   - Sample counts

**Save As:**
- `jvisualvm_cpu_sampling.png`

---

## PART 5: AUTOMATED MONITORING (OPTIONAL)

For hands-off profiling data collection, use the monitoring utilities:

### Option A: Runtime MBean Monitoring
JVisualVM automatically collects MBean data:
- Heap Statistics
- Garbage Collection Stats
- Thread Count

**Export Data:**
1. Right-click process in left panel
2. Select "Heap Dump" or "Thread Dump"
3. Data saves to file

### Option B: Enable JMX Remote
If you want to profile from another machine:
```bash
java -Dcom.sun.management.jmxremote \
     -Dcom.sun.management.jmxremote.port=9010 \
     -Dcom.sun.management.jmxremote.authenticate=false \
     -Dcom.sun.management.jmxremote.ssl=false \
     -cp ... com.stocker.AppLauncher
```

Then in JVisualVM:
1. File → Add Remote Connection
2. Enter localhost:9010

---

## PART 6: COMPLETE PROFILING SESSION CHECKLIST

### Before Testing:
- [ ] JVisualVM opened and showing process list
- [ ] AppLauncher process identified and visible
- [ ] Monitor tab accessible
- [ ] Sampler tab accessible

### During Test 1 (100 ops/sec):
- [ ] Connect to AppLauncher
- [ ] Open Monitor tab
- [ ] Observe heap memory for 20 seconds
- [ ] **Screenshot 1**: Heap memory graph (sawtooth pattern)
- [ ] Switch to Sampler tab
- [ ] Click "CPU" to start sampling
- [ ] Run stress test
- [ ] Wait 30+ seconds for samples to accumulate
- [ ] **Screenshot 2**: Top CPU methods
- [ ] Note the top 5 methods and their percentages

### During Test 2 (500 ops/sec):
- [ ] Clear previous samples
- [ ] Run stress test at 500 ops/sec
- [ ] Repeat screenshot process
- [ ] Compare heap memory intensity vs 100 ops/sec test

### During Test 3 (1000 ops/sec):
- [ ] Clear previous samples
- [ ] Run stress test at 1000 ops/sec
- [ ] Repeat screenshot process
- [ ] Note if GC becomes more aggressive

### Analysis:
- [ ] Identify bottleneck (Database/Logging/Matching)
- [ ] Note GC frequency at each throughput level
- [ ] Collect latency data from PerformanceMonitor output files

---

## PART 7: EXPECTED PERFORMANCE PATTERNS

### Test 1: 100 ops/sec (10,000 orders)
**Heap Memory:**
- Sawtooth pattern present but infrequent
- ~20-30% GC in 100 seconds
- Memory drops sharply when GC runs

**CPU Sampling:**
- Database writer: 30-40%
- Order matching: 20-30%
- FIX serialization: 10-15%
- Average latency: 100-200 μs

### Test 2: 500 ops/sec (10,000 orders)
**Heap Memory:**
- Sawtooth becomes more frequent
- ~40-50% GC in 20 seconds
- Memory pressure increases

**CPU Sampling:**
- Database writer: 40-50% CPU
- Order matching: 20-25%
- FIX serialization: 10-12%
- Average latency: 200-400 μs

### Test 3: 1000 ops/sec (10,000 orders)
**Heap Memory:**
- Sawtooth very frequent (2-3 second intervals)
- GC dominates resource usage
- Memory near capacity

**CPU Sampling:**
- Database writer: 50-60% CPU
- Garbage collector: 15-20% CPU
- FIX serialization: 8-10%
- Average latency: 400-800 μs

---

## PART 8: TROUBLESHOOTING

### Issue: Process Not Showing in JVisualVM
**Solution:**
1. Ensure AppLauncher is running
2. Terminal: `jps -l` to list all Java processes
3. Look for `com.stocker.AppLauncher`
4. Refresh JVisualVM (F5 key)

### Issue: Cannot Connect to Process
**Solution:**
1. Check user permissions
2. Ensure JVisualVM and AppLauncher are same Java version
3. Try restarting JVisualVM

### Issue: CPU Sampling Shows No Data
**Solution:**
1. Wait at least 5-10 seconds after clicking CPU
2. Ensure stress test is actively running
3. CPU sampling needs execution activity to collect data

### Issue: Memory Graph Not Updating
**Solution:**
1. Check if Monitor tab is active (refresh rate is faster)
2. Try switching tabs and back
3. Disconnect and reconnect to process

---

## PART 9: DATA COLLECTION SUMMARY

After profiling all three tests, you should have:

| Metric | 100 ops/sec | 500 ops/sec | 1000 ops/sec |
|--------|-------------|-------------|--------------|
| Heap Memory Screenshot | ✓ | ✓ | ✓ |
| CPU Sampling Screenshot | ✓ | ✓ | ✓ |
| Top 5 CPU Methods | Recorded | Recorded | Recorded |
| Avg Latency (μs) | From PerformanceMonitor | From PerformanceMonitor | From PerformanceMonitor |
| GC Frequency | Observed | Observed | Observed |
| Bottleneck Identified | Yes | Yes | Yes |

This data goes into **LAB9_PERFORMANCE_REPORT.md**

---

## PART 10: NEXT STEPS

1. ✅ Complete profiling for all three test rates
2. 📊 Extract latency data from output files
3. 📈 Create Latency vs Throughput graph
4. 📝 Identify bottleneck
5. 🔧 (Optional) Implement STEP E optimization
6. 📄 Compile into performance report

---

## Quick Reference: Key Benchmarks

**Healthy System Indicators:**
- Avg latency < 500 μs at 100 ops/sec
- Avg latency < 1000 μs at 500 ops/sec
- Avg latency < 2000 μs at 1000 ops/sec
- GC runs < 1 per second
- Database writes < 50% CPU

**Bottleneck Red Flags:**
- GC runs > 10 per second → Need batch inserts
- Database > 60% CPU → Need optimization
- Latency spikes during GC pauses → Normal but monitor trend
