package lab1;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.SplittableRandom;

/** Generates numbers.txt together with the correct counts. */
final class DataGen {
    static final int MIN = -1_000_000;
    static final int MAX = 1_000_000;

    private DataGen() {
    }

    static void generate(Path file, long count, long seed) throws IOException {
        Files.createDirectories(file.toAbsolutePath().getParent());
        SplittableRandom random = new SplittableRandom(seed);
        Counts expected = new Counts();
        byte[] digits = new byte[12];
        try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(file), 1 << 20)) {
            for (long i = 0; i < count; i++) {
                int value = random.nextInt(MIN, MAX + 1);
                expected.count(value);
                writeLine(out, value, digits);
            }
        }
        Files.writeString(expectedFile(file), expected.negative + " " + expected.positive + " " + expected.zero + "\n");
        System.out.println("Wrote " + file + " (" + Files.size(file) + " bytes), " + expected);
    }

    static Path expectedFile(Path numbersFile) {
        return numbersFile.resolveSibling(numbersFile.getFileName() + ".expected");
    }

    /** Reads the counts written by {@link #generate}, or null if the file is missing. */
    static Counts readExpected(Path numbersFile) throws IOException {
        Path f = expectedFile(numbersFile);
        if (!Files.exists(f)) return null;
        String[] parts = Files.readString(f, StandardCharsets.US_ASCII).trim().split(" ");
        Counts c = new Counts();
        c.negative = Long.parseLong(parts[0]);
        c.positive = Long.parseLong(parts[1]);
        c.zero = Long.parseLong(parts[2]);
        return c;
    }

    private static void writeLine(OutputStream out, int value, byte[] digits) throws IOException {
        if (value < 0) {
            out.write('-');
            value = -value;
        }
        int n = 0;
        do {
            digits[n++] = (byte) ('0' + value % 10);
            value /= 10;
        } while (value != 0);
        while (n > 0) out.write(digits[--n]);
        out.write('\n');
    }
}
