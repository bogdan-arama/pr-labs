package lab1;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.locks.ReentrantLock;

/** Task 1b: three ways for threads to count the signs of an int array already in memory. */
final class Task1b {
    enum Mode { UNSYNC, LOCK, LOCAL }

    /** Counters shared by all threads (modes UNSYNC and LOCK). Plain fields, no volatile. */
    static final class Shared {
        long negative;
        long positive;
        long zero;
    }

    private Task1b() {
    }

    /** Loads every number of the file into an array. Not part of the measured time. */
    static int[] load(Path file) throws IOException {
        int[] data = new int[1 << 20];
        int size = 0;
        long value = 0;
        boolean negative = false;
        boolean hasDigits = false;
        byte[] buf = new byte[1 << 20];
        try (InputStream in = new BufferedInputStream(Files.newInputStream(file))) {
            int n;
            while ((n = in.read(buf)) > 0) {
                for (int i = 0; i < n; i++) {
                    byte b = buf[i];
                    if (b >= '0' && b <= '9') {
                        value = value * 10 + (b - '0');
                        hasDigits = true;
                    } else if (b == '-') {
                        negative = true;
                    } else if (b == '\n') {
                        if (hasDigits) {
                            if (size == data.length) data = Arrays.copyOf(data, size * 2);
                            data[size++] = (int) (negative ? -value : value);
                        }
                        value = 0;
                        negative = false;
                        hasDigits = false;
                    }
                }
            }
        }
        if (hasDigits) {
            if (size == data.length) data = Arrays.copyOf(data, size + 1);
            data[size++] = (int) (negative ? -value : value);
        }
        return Arrays.copyOf(data, size);
    }

    static Counts run(int[] data, int threads, Mode mode) throws InterruptedException {
        Shared shared = new Shared();
        ReentrantLock lock = new ReentrantLock();
        Counts[] perThread = new Counts[threads];
        Thread[] workers = new Thread[threads];
        for (int t = 0; t < threads; t++) {
            int from = (int) ((long) data.length * t / threads);
            int to = (int) ((long) data.length * (t + 1) / threads);
            Counts own = perThread[t] = new Counts();
            Runnable work = switch (mode) {
                case UNSYNC -> () -> countUnsync(data, from, to, shared);
                case LOCK -> () -> countLocked(data, from, to, shared, lock);
                case LOCAL -> () -> countLocal(data, from, to, own);
            };
            workers[t] = new Thread(work, "counter-" + t);
            workers[t].start();
        }
        for (Thread w : workers) w.join();

        Counts total = new Counts();
        if (mode == Mode.LOCAL) {
            for (Counts c : perThread) total.add(c); // added together after all threads finish
        } else {
            total.negative = shared.negative;
            total.positive = shared.positive;
            total.zero = shared.zero;
        }
        return total;
    }

    /** Version 1: every thread does read-modify-write on the shared fields with no synchronisation. */
    static void countUnsync(int[] data, int from, int to, Shared shared) {
        for (int i = from; i < to; i++) {
            int v = data[i];
            if (v < 0) shared.negative++;
            else if (v > 0) shared.positive++;
            else shared.zero++;
        }
    }

    /** Version 2: the same shared fields, but every increment holds the lock. */
    static void countLocked(int[] data, int from, int to, Shared shared, ReentrantLock lock) {
        for (int i = from; i < to; i++) {
            int v = data[i];
            lock.lock();
            try {
                if (v < 0) shared.negative++;
                else if (v > 0) shared.positive++;
                else shared.zero++;
            } finally {
                lock.unlock();
            }
        }
    }

    /** Version 3: each thread counts in its own local variables and publishes them once at the end. */
    static void countLocal(int[] data, int from, int to, Counts own) {
        long negative = 0, positive = 0, zero = 0;
        for (int i = from; i < to; i++) {
            int v = data[i];
            if (v < 0) negative++;
            else if (v > 0) positive++;
            else zero++;
        }
        own.negative = negative;
        own.positive = positive;
        own.zero = zero;
    }
}
