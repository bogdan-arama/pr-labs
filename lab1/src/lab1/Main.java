package lab1;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Laboratory work 1: Concurrency in practice. Every task and configuration is chosen on the
 * command line, for example:
 *
 * <pre>
 *   java -cp out lab1.Main gen
 *   java -cp out lab1.Main 1a --threads 8
 *   java -cp out lab1.Main 1b --mode unsync
 *   java -cp out lab1.Main 1c --workers 4
 * </pre>
 */
public final class Main {
    private static final String USAGE = """
            Usage: java -cp out lab1.Main <command> [options]

              gen    [--file data/numbers.txt] [--count 50000000] [--seed 42]
                     generate the input file (and <file>.expected with the correct counts)
              1a     [--file data/numbers.txt] [--threads 1]
                     each thread reads and parses its own part of the file
              1b     [--file data/numbers.txt] [--threads <logical CPUs>] --mode unsync|lock|local
                     count an in-memory array with shared, locked or per-thread counters
              1c     [--file data/numbers.txt] [--workers 1] [--batch 10000] [--capacity 100]
                     one reader thread, a bounded blocking queue, N worker threads
                     (--capacity 0 = a queue with no size limit)
            """;

    private Main() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0 || args[0].equals("-h") || args[0].equals("--help")) {
            System.out.print(USAGE);
            return;
        }
        String command = args[0];
        Map<String, String> opt = parseOptions(args);
        Path file = Path.of(opt.getOrDefault("file", "data/numbers.txt"));

        switch (command) {
            case "gen" -> DataGen.generate(file,
                    Long.parseLong(opt.getOrDefault("count", "50000000")),
                    Long.parseLong(opt.getOrDefault("seed", "42")));
            case "1a" -> {
                int threads = intOpt(opt, "threads", 1);
                long t0 = System.nanoTime();
                Counts c = Task1a.run(file, threads);
                report("1a", threads, "", System.nanoTime() - t0, c, file, "");
            }
            case "1b" -> {
                int threads = intOpt(opt, "threads", Runtime.getRuntime().availableProcessors());
                Task1b.Mode mode = Task1b.Mode.valueOf(required(opt, "mode").toUpperCase(Locale.ROOT));
                int[] data = Task1b.load(file); // not measured
                long t0 = System.nanoTime();
                Counts c = Task1b.run(data, threads, mode);
                report("1b", threads, mode.name().toLowerCase(Locale.ROOT), System.nanoTime() - t0, c, file, "");
            }
            case "1c" -> {
                int workers = intOpt(opt, "workers", 1);
                int batch = intOpt(opt, "batch", 10_000);
                int capacity = Integer.parseInt(opt.getOrDefault("capacity", "100"));
                long t0 = System.nanoTime();
                Counts c = Task1c.run(file, workers, batch, capacity);
                report("1c", workers, "batch=" + batch + ";capacity=" + capacity, System.nanoTime() - t0, c, file,
                        ",reader_waits=" + Task1c.readerWaits.get() + ",worker_waits=" + Task1c.workerWaits.get()
                        + ",max_queued=" + Task1c.maxQueued.get());
            }
            default -> {
                System.err.println("Unknown command: " + command);
                System.err.print(USAGE);
                System.exit(2);
            }
        }
    }

    /** Prints a readable line and a machine-readable RESULT line (parsed by scripts/run_benchmarks.py). */
    private static void report(String task, int threads, String variant, long nanos, Counts c, Path file,
                               String extra) throws Exception {
        Counts expected = DataGen.readExpected(file);
        String correct = expected == null ? "unknown"
                : String.valueOf(expected.negative == c.negative && expected.positive == c.positive
                && expected.zero == c.zero);
        long peakRss = Memory.processPeakBytes();
        long peakHeap = Memory.heapPeakBytes();
        double ms = nanos / 1e6;

        System.out.printf(Locale.ROOT, "Task %s | threads=%d %s| time=%.1f ms | %s | correct=%s | peak memory=%.1f MB (heap peak %.1f MB)%n",
                task, threads, variant.isEmpty() ? "" : variant + " ", ms, c, correct, peakRss / 1e6, peakHeap / 1e6);
        System.out.printf(Locale.ROOT, "RESULT,task=%s,threads=%d,variant=%s,time_ms=%.3f,negative=%d,positive=%d,zero=%d,total=%d,correct=%s,peak_rss_bytes=%d,peak_heap_bytes=%d%s%n",
                task, threads, variant, ms, c.negative, c.positive, c.zero, c.total(), correct, peakRss, peakHeap, extra);
    }

    private static Map<String, String> parseOptions(String[] args) {
        Map<String, String> opt = new HashMap<>();
        for (int i = 1; i < args.length; i++) {
            if (!args[i].startsWith("--") || i + 1 >= args.length) {
                throw new IllegalArgumentException("Expected --name value, got: " + args[i]);
            }
            opt.put(args[i].substring(2), args[++i]);
        }
        return opt;
    }

    private static int intOpt(Map<String, String> opt, String name, int def) {
        int v = opt.containsKey(name) ? Integer.parseInt(opt.get(name)) : def;
        if (v < 1) throw new IllegalArgumentException("--" + name + " must be at least 1");
        return v;
    }

    private static String required(Map<String, String> opt, String name) {
        String v = opt.get(name);
        if (v == null) throw new IllegalArgumentException("Missing --" + name);
        return v;
    }
}
