#!/usr/bin/env python3
"""
LAB 9: Latency vs. Throughput Graph Generator
Extracts latency data from stress test output files and creates a performance graph
"""

import re
import matplotlib.pyplot as plt
import os

def extract_latency_from_file(filename):
    """
    Extract the final average latency from stress test output file
    Returns latency in microseconds or None if not found
    """
    if not os.path.exists(filename):
        print(f"❌ File not found: {filename}")
        return None
    
    try:
        with open(filename, 'r') as f:
            content = f.read()
        
        # Look for the final PerformanceMonitor summary
        # Pattern: "[PERF] Final Summary: Avg Latency: X.XX us"
        # or: "[PERF] Processed Y orders. Avg Latency: X.XX us"
        matches = re.findall(r'Avg Latency: ([\d.]+)\s*us', content)
        
        if matches:
            # Get the last occurrence (final summary)
            final_latency = float(matches[-1])
            return final_latency
        else:
            print(f"⚠️  Could not find latency data in {filename}")
            print("   Looking for pattern: 'Avg Latency: X.XX us'")
            return None
            
    except Exception as e:
        print(f"❌ Error reading {filename}: {e}")
        return None

def main():
    print("=" * 80)
    print(" LAB 9: LATENCY GRAPH GENERATOR")
    print("=" * 80)
    print()
    
    # Test configuration
    tests = [
        {"rate": 100, "filename": "stress_test_100ops_output.txt"},
        {"rate": 500, "filename": "stress_test_500ops_output.txt"},
        {"rate": 1000, "filename": "stress_test_1000ops_output.txt"}
    ]
    
    throughputs = []
    latencies = []
    
    print("Extracting latency data from test output files...")
    print()
    
    for test in tests:
        rate = test["rate"]
        filename = test["filename"]
        
        latency = extract_latency_from_file(filename)
        
        if latency is not None:
            throughputs.append(rate)
            latencies.append(latency)
            print(f"✓ {rate:4d} ops/sec: {latency:7.2f} microseconds")
        else:
            print(f"✗ {rate:4d} ops/sec: FAILED TO EXTRACT")
    
    print()
    
    if len(throughputs) < 2:
        print("❌ Not enough data points to create graph (need at least 2)")
        return
    
    print(f"✓ Successfully extracted {len(latencies)} data points")
    print()
    
    # Create the graph
    print("Creating latency graph...")
    
    plt.figure(figsize=(12, 7))
    
    # Plot the main line
    plt.plot(throughputs, latencies, 
             marker='o', 
             linewidth=2.5, 
             markersize=10, 
             color='#0066cc',
             label='Average Latency')
    
    # Add value labels on each point
    for i, (x, y) in enumerate(zip(throughputs, latencies)):
        plt.text(x, y + max(latencies)*0.05, 
                f'{y:.2f}μs', 
                ha='center', 
                fontsize=11,
                fontweight='bold',
                bbox=dict(boxstyle='round,pad=0.3', facecolor='yellow', alpha=0.3))
    
    # Formatting
    plt.xlabel('Throughput (orders/sec)', fontsize=13, fontweight='bold')
    plt.ylabel('Average Latency (microseconds)', fontsize=13, fontweight='bold')
    plt.title('LAB 9: Average Latency vs. Throughput\nOrder Management System Performance', 
              fontsize=15, fontweight='bold', pad=20)
    
    # Grid
    plt.grid(True, alpha=0.3, linestyle='--', linewidth=0.7)
    
    # Styling
    plt.tight_layout()
    ax = plt.gca()
    ax.set_facecolor('#f9f9f9')
    
    # Set x-axis to show all test points
    plt.xticks(throughputs, [f'{x}' for x in throughputs], fontsize=11)
    plt.yticks(fontsize=11)
    
    # Add a subtle background
    ax.spines['top'].set_visible(False)
    ax.spines['right'].set_visible(False)
    
    # Save the figure
    output_file = 'latency_graph.png'
    plt.savefig(output_file, dpi=300, bbox_inches='tight', facecolor='white')
    print(f"✓ Graph saved as: {output_file}")
    
    # Show the figure
    plt.show()
    
    # Print summary statistics
    print()
    print("=" * 80)
    print(" SUMMARY STATISTICS")
    print("=" * 80)
    print(f"Throughput Range:      {min(throughputs):,} - {max(throughputs):,} ops/sec")
    print(f"Latency Range:         {min(latencies):.2f} - {max(latencies):.2f} microseconds")
    print(f"Latency Increase:      {((max(latencies) - min(latencies)) / min(latencies) * 100):.1f}%")
    print(f"Average of Averages:   {sum(latencies) / len(latencies):.2f} microseconds")
    print()
    
    # Analysis
    print("=" * 80)
    print(" ANALYSIS")
    print("=" * 80)
    print()
    
    if len(latencies) >= 2:
        latency_increase = latencies[-1] - latencies[0]
        throughput_increase = throughputs[-1] - throughputs[0]
        
        print("Latency Trend:")
        if latency_increase > 0:
            increase_pct = (latency_increase / latencies[0]) * 100
            print(f"  ⚠️  Latency INCREASED by {increase_pct:.1f}% from 100 to 1000 ops/sec")
            print(f"     This indicates a potential bottleneck under high load")
        else:
            print(f"  ✓ Latency remained stable or decreased")
        
        print()
        print("Scalability Analysis:")
        if latency_increase > latencies[0] * 0.20:  # > 20% increase
            print("  ⚠️  POOR SCALABILITY - Latency increases significantly with load")
            print("     Possible bottlenecks: Database, GC, I/O operations")
        elif latency_increase > latencies[0] * 0.10:  # > 10% increase
            print("  ⚠️  MODERATE SCALABILITY - Some degradation at high load")
        else:
            print("  ✓ EXCELLENT SCALABILITY - Latency remains stable")
    
    print()

if __name__ == "__main__":
    main()
