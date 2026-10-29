package lab1;

/** Numbers of negative, positive and zero values. Not thread-safe: each thread fills its own. */
final class Counts {
    long negative;
    long positive;
    long zero;

    void count(long value) {
        if (value < 0) negative++;
        else if (value > 0) positive++;
        else zero++;
    }

    void add(Counts other) {
        negative += other.negative;
        positive += other.positive;
        zero += other.zero;
    }

    long total() {
        return negative + positive + zero;
    }

    @Override
    public String toString() {
        return "negative=" + negative + " positive=" + positive + " zero=" + zero + " total=" + total();
    }
}
