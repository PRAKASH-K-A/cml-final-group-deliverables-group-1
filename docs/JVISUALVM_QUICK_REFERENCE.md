# JVISUALVM PROFILING - QUICK REFERENCE

## ⚡ Quick Start (30 seconds)

1. **Open Terminal**
   ```powershell
   cd stocker\cmt
   jvisualvm
   ```

2. **In JVisualVM**
   - Find `AppLauncher` in left panel
   - Double-click to connect
   - Click "Monitor" tab
   - Watch heap memory (sawtooth = normal)

3. **Start Profiling**
   - Click "Sampler" tab
   - Click "CPU" button
   - Run test in another terminal

4. **Take Screenshots**
   - Monitor tab: Heap memory during test
   - Sampler tab: Top 10 methods after test completes

---

## 🔍 WHAT TO LOOK FOR

### Heap Memory Graph (Monitor Tab)

| Pattern | Meaning | Action |
|---------|---------|--------|
| **Smooth rise + sharp drop** | Normal GC | ✓ Healthy |
| **Frequent sawtooth** | GC every 5 sec | ⚠ Watch trend |
| **Continuous sawtooth** | GC every 1 sec | ❌ Bottleneck |
| **Memory climbing to 100%** | Heap pressure | ❌ Investigate |

### CPU Sampling (Sampler Tab)

Look for Top 5 Methods:

1. **DatabaseManager** - Expected 25-55%
   - Too high (>70%) → Use batch inserts
   - Too low (<15%) → Something else is slow

2. **OrderBook.match** - Expected 15-35%
   - Low latency matching algorithm
   - Normal CPU usage

3. **Session.sendToTarget** - Expected 10-20%
   - FIX message serialization
   - Normal CPU usage

4. **Java Collections** - Expected 5-15%
   - ArrayList, HashMap operations
   - Normal background work

5. **Garbage Collector** - Expected 5-20%
   - Indicates memory pressure if >15%
   - Correlates with heap fullness

---

## 📊 TEST CONFIGURATION

### 3 Stress Tests Required

```
TEST 1: 100 ops/sec  → ~100 seconds (~20ms avg latency)
TEST 2: 500 ops/sec  → ~20 seconds  (~100ms avg latency)
TEST 3: 1000 ops/sec → ~10 seconds  (~200ms avg latency)
```

Each generates 10,000 orders with rotating symbols (GOOG/MSFT/IBM).

---

## 📸 SCREENSHOTS NEEDED

### Test 1 (100 ops/sec):
- [ ] `jvisualvm_heap_100ops.png` - Monitor tab, show sawtooth
- [ ] `jvisualvm_cpu_100ops.png` - Sampler tab, show top methods

### Test 2 (500 ops/sec):
- [ ] `jvisualvm_heap_500ops.png` - Monitor tab
- [ ] `jvisualvm_cpu_500ops.png` - Sampler tab

### Test 3 (1000 ops/sec):
- [ ] `jvisualvm_heap_1000ops.png` - Monitor tab
- [ ] `jvisualvm_cpu_1000ops.png` - Sampler tab

---

## 🎯 EXPECTED RESULTS

### Healthy System Signature:
- Heap: 30-60% utilization at all rates
- GC: < 5 runs per minute
- CPU: Database 30-40%, Matching 20-30%
- Latency scaling: Linear increase with throughput

### Database Bottleneck Signature:
- DatabaseManager CPU: >50-60%
- Heap: Rising toward 100%
- GC: Frequent (>10 per minute)
- Latency: Sharp increase at 500+ ops/sec

### Logging Bottleneck Signature:
- System.out: >30% CPU
- Thread contention on console lock
- GC less frequent but latency still high
- Latency: Inconsistent spikes

---

## 🔧 TROUBLESHOOTING

### Problem: Process not showing in JVisualVM
**Solution:**
```powershell
jps -l
# Should show com.stocker.AppLauncher
```

### Problem: Cannot connect to process
**Solution:**
- Restart JVisualVM and AppLauncher
- Check Java versions match
- Verify no firewall blocking

### Problem: CPU Sampling shows no data
**Solution:**
- Ensure AppLauncher is actively processing
- Wait 10+ seconds for samples
- Ensure stress test is running

### Problem: Heap memory not showing
**Solution:**
- Click Monitor tab repeatedly to refresh
- Verify process is connected
- Try disconnecting and reconnecting

---

## 📝 DATA RECORDING TEMPLATE

```
TEST RATE: __ ops/sec

HEAP MEMORY:
- Utilization: __% 
- GC Frequency: __ runs per minute
- Sawtooth Pattern: [Smooth / Frequent / Continuous]
- Visual: [Attach heap screenshot]

CPU SAMPLING:
1. __________________ - __._%
2. __________________ - __._%
3. __________________ - __._%
4. __________________ - __._%
5. __________________ - __._%

PERFORMANCE MONITOR OUTPUT:
- Average Latency: __ μs
- Min Latency: __ μs
- Max Latency: __ μs
- Orders Processed: ____

OBSERVATIONS:
- Bottleneck: [Database / Logging / Matching / Memory]
- Severity: [Low / Medium / High]
- Notes: _______________________________________
```

---

## ✅ PROCESS CHECKLIST

### Setup Phase:
- [ ] AppLauncher launched and running
- [ ] JVisualVM opened
- [ ] Process connected (green indicator)
- [ ] Monitor tab accessible
- [ ] Sampler tab accessible

### Test Phase (Per Test):
- [ ] Started JVisualVM CPU sampling
- [ ] Launched stress test
- [ ] Waited for test to complete
- [ ] Stopped CPU sampling
- [ ] Recorded top 5 CPU methods

### Profiling Phase:
- [ ] Screenshot heap memory
- [ ] Screenshot CPU methods
- [ ] Recorded latency data
- [ ] Noted GC frequency
- [ ] Identified bottleneck

### Analysis Phase:
- [ ] Extracted 3 data points (100/500/1000 ops)
- [ ] Created latency vs throughput graph
- [ ] Identified root bottleneck
- [ ] Documented conclusions

---

## 🎓 EXPECTED LEARNING OUTCOMES

After completing this profiling exercise, you should understand:

1. **How to measure latency** - Tick-to-Trade metric
2. **How to identify bottlenecks** - CPU profiling and memory graphs
3. **How GC affects performance** - Memory pressure and pause times
4. **How to optimize systems** - Database batching, logging reduction
5. **How to use profiling tools** - JVisualVM workflow

---

## 📚 NEXT: LAB ASSESSMENT

Required deliverables:
1. **Latency Graph**: Plot average latency vs throughput (3 data points)
2. **Memory Screenshots**: JVisualVM heap graphs for all 3 rates
3. **CPU Screenshots**: JVisualVM sampling results for all 3 rates
4. **Performance Report**: Document findings and conclusions
   - What was the bottleneck?
   - Why does latency increase nonlinearly?
   - How would you fix it?

**Success Criteria:**
- ✓ PASS (60+): All screenshots collected, bottleneck identified
- ✓ EXCELLENT (80+): Detailed analysis, optimization suggestions
- ✓ DISTINCTION (90+): Implement optimization and re-test showing improvement

---

## 🚀 ADVANCED (OPTIONAL)

If you want to implement the optimization:

1. **Batch Inserts** (STEP E):
   ```java
   // In DatabaseManager.java
   // Instead of 1 insert per order:
   // Queue 100 orders, insert in single transaction
   ```

2. **Reduce Logging**:
   ```java
   // Instead of every order:
   PerformanceMonitor.recordLatency(latency); // Every 1000
   ```

3. **Re-profile**:
   - Run tests again with changes
   - Compare latency improvements
   - Measure CPU reduction

---

Generated for: LAB 9: Performance Engineering and Telemetry
Date: 2026-04-02
