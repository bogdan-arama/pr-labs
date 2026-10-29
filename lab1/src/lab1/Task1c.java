package lab1;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Task 1c: one reader thread puts batches of lines into a bounded blocking queue and the
 * worker threads take batches and count them.
 *
 * <p>Java's BlockingQueue has no close(), so the reader "closes" it by putting one empty
 * marker batch (END) per worker after the last real batch. The queue is FIFO, so a worker
 * only sees END after all real batches have been taken, and then it stops.
 */
final class Task1c {
    private static final String[] END = new String[0];

    /** How often the reader found the queue full and how often a worker found it empty. */
    static final AtomicLong readerWaits = new AtomicLong();
    static final AtomicLong workerWaits = new AtomicLong();
    /** The largest number of batches seen waiting in the queue. */
    static final AtomicLong maxQueued = new AtomicLong();

    private Task1c() {
    }

    /** @param capacity maximum number of batches in the queue, or 0 for a queue with no size limit */
    static Counts run(Path file, int workers, int batchSize, int capacity) throws Exception {
        BlockingQueue<String[]> queue = capacity > 0 ? new ArrayBlockingQueue<>(capacity) : new LinkedBlockingQueue<>();

        Thread reader = new Thread(() -> read(file, queue, batchSize, workers), "reader");
        reader.start();

        Counts[] perWorker = new Counts[workers];
        Thread[] threads = new Thread[workers];
        for (int w = 0; w < workers; w++) {
            Counts counts = perWorker[w] = new Counts();
            threads[w] = new Thread(() -> work(queue, counts), "worker-" + w);
            threads[w].start();
        }

        reader.join();
        Counts total = new Counts();
        for (int w = 0; w < workers; w++) {
            threads[w].join();
            total.add(perWorker[w]);
        }
        return total;
    }

    static void read(Path file, BlockingQueue<String[]> queue, int batchSize, int workers) {
        try (BufferedReader in = Files.newBufferedReader(file, StandardCharsets.US_ASCII)) {
            String[] batch = new String[batchSize];
            int n = 0;
            String line;
            while ((line = in.readLine()) != null) {
                batch[n++] = line;
                if (n == batchSize) {
                    put(queue, batch);
                    batch = new String[batchSize];
                    n = 0;
                }
            }
            if (n > 0) put(queue, Arrays.copyOf(batch, n));
            for (int w = 0; w < workers; w++) queue.put(END); // close the queue
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    static void work(BlockingQueue<String[]> queue, Counts counts) {
        try {
            while (true) {
                String[] batch = queue.poll();
                if (batch == null) { // queue empty: wait for the reader
                    workerWaits.incrementAndGet();
                    batch = queue.take();
                }
                if (batch == END) return;
                for (String line : batch) {
                    counts.count(LineParser.parse(line));
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void put(BlockingQueue<String[]> queue, String[] batch) throws InterruptedException {
        if (!queue.offer(batch)) { // queue full: wait for the workers
            readerWaits.incrementAndGet();
            queue.put(batch);
        }
        maxQueued.accumulateAndGet(queue.size(), Math::max);
    }
}
