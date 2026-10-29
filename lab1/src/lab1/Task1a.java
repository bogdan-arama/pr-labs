package lab1;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Task 1a: the file is divided into {@code threads} byte ranges and every thread reads and
 * parses only its own range.
 *
 * <p>Boundary rule: a line belongs to the range in which its first byte lies. So a thread whose
 * range does not start right after a '\n' skips the partial line at its start (the previous
 * thread counts it), and a thread keeps reading past its end to finish the last line it started.
 * Every line therefore has exactly one owner: none is counted twice and none is skipped.
 */
final class Task1a {
    static final int BUFFER_SIZE = 1 << 20;

    private Task1a() {
    }

    static Counts run(Path file, int threads) throws Exception {
        long size = Files.size(file);
        Counts[] perThread = new Counts[threads];
        Thread[] workers = new Thread[threads];
        for (int t = 0; t < threads; t++) {
            long start = size * t / threads;
            long end = size * (t + 1) / threads;
            Counts counts = perThread[t] = new Counts();
            workers[t] = new Thread(() -> countRange(file, start, end, counts), "reader-" + t);
            workers[t].start();
        }
        Counts total = new Counts();
        for (int t = 0; t < threads; t++) {
            workers[t].join();
            total.add(perThread[t]);
        }
        return total;
    }

    /** Counts every line whose first byte lies in [start, end). */
    static void countRange(Path file, long start, long end, Counts counts) {
        try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "r")) {
            byte[] buf = new byte[BUFFER_SIZE];
            LineParser parser = new LineParser(counts);

            // Do we start in the middle of a line? Then that line belongs to the previous thread.
            boolean skipping = false;
            if (start > 0) {
                raf.seek(start - 1);
                skipping = raf.read() != '\n';
            }
            long lineStart = start; // file offset of the first byte of the current line
            if (!skipping && lineStart >= end) return;

            long pos = start;
            raf.seek(pos);
            while (true) {
                int n = raf.read(buf);
                if (n <= 0) { // end of file: the last line may have no '\n'
                    if (!skipping) parser.endLine();
                    return;
                }
                for (int i = 0; i < n; i++) {
                    byte b = buf[i];
                    if (skipping) {
                        if (b == '\n') {
                            skipping = false;
                            lineStart = pos + i + 1;
                            if (lineStart >= end) return;
                        }
                    } else if (parser.feed(b)) {
                        lineStart = pos + i + 1;
                        if (lineStart >= end) return; // the next line belongs to the next thread
                    }
                }
                pos += n;
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
