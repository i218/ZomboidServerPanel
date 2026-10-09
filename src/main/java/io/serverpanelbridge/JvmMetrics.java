package io.serverpanelbridge;

import java.lang.management.ManagementFactory;
import java.time.Duration;

public final class JvmMetrics {
    private long previousWall = -1, previousCpu = -1;
    public synchronized double cpuPercent() {
        long wall = System.nanoTime();
        long cpu = ProcessHandle.current().info().totalCpuDuration().orElse(Duration.ZERO).toNanos();
        double percent = 0;
        if (previousWall >= 0 && wall > previousWall && cpu >= previousCpu)
            percent = normalizeCpu(cpu - previousCpu, wall - previousWall, Runtime.getRuntime().availableProcessors());
        previousWall = wall; previousCpu = cpu;
        return percent;
    }
    static double normalizeCpu(long cpuDelta, long wallDelta, int processors) {
        if (wallDelta <= 0 || cpuDelta < 0 || processors < 1) return 0;
        return Math.min(100, Math.max(0, 100.0 * cpuDelta / wallDelta / processors));
    }
    public double memoryMb() { return ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed() / (1024.0 * 1024); }
    public long uptimeSeconds() { return ManagementFactory.getRuntimeMXBean().getUptime() / 1000; }
}
