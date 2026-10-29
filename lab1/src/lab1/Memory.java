package lab1;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Peak memory of the current process, read at the end of a run. */
final class Memory {
    private Memory() {
    }

    /**
     * Peak resident memory of the whole process as the OS sees it (JVM, heap, thread stacks,
     * native buffers): PeakWorkingSet64 on Windows, VmHWM on Linux. Returns -1 if unknown.
     */
    static long processPeakBytes() {
        String os = System.getProperty("os.name").toLowerCase();
        long pid = ProcessHandle.current().pid();
        try {
            if (os.contains("win")) {
                Process p = new ProcessBuilder("powershell", "-NoProfile", "-Command",
                        "(Get-Process -Id " + pid + ").PeakWorkingSet64")
                        .redirectErrorStream(true).start();
                String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.US_ASCII).trim();
                p.waitFor();
                return Long.parseLong(out);
            }
            if (os.contains("linux")) {
                for (String line : Files.readAllLines(Path.of("/proc/self/status"))) {
                    if (line.startsWith("VmHWM:")) {
                        return Long.parseLong(line.replaceAll("\\D", "")) * 1024;
                    }
                }
            }
        } catch (IOException | InterruptedException | NumberFormatException e) {
            return -1;
        }
        return -1;
    }

    /** Sum of the peak usage of the Java heap pools (an upper bound of the peak heap). */
    static long heapPeakBytes() {
        long sum = 0;
        for (MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
            if (pool.getType() == MemoryType.HEAP && pool.getPeakUsage() != null) {
                sum += pool.getPeakUsage().getUsed();
            }
        }
        return sum;
    }
}
