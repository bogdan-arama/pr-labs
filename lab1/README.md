# Lab 1 – Concurrency in practice (Java)

One Java program (`lab1.Main`); every task and configuration is selected with command-line
parameters. Only the JDK is needed (Java 17 or newer, no build tool). The Python scripts in
`scripts/` are optional helpers for running all measurements and drawing the charts.

The report is in [`report/Lab1_BogdanArama_FAF-242.pdf`](report/Lab1_BogdanArama_FAF-242.pdf).

## Layout

| Path | What it is |
|---|---|
| `src/lab1/Main.java` | command line: chooses the task and its parameters, measures time and peak memory |
| `src/lab1/DataGen.java` | generates `numbers.txt` and the file with the correct counts |
| `src/lab1/Task1a.java` | 1a – each thread reads and parses its own byte range of the file |
| `src/lab1/Task1b.java` | 1b – shared counters without sync, with a lock, and per-thread counters |
| `src/lab1/Task1c.java` | 1c – one reader, a bounded `ArrayBlockingQueue` of batches, N workers |
| `src/lab1/LineParser.java`, `Counts.java`, `Memory.java` | parsing, counters, peak memory |
| `scripts/run_benchmarks.py` | runs every configuration in a fresh JVM, writes `results/*.csv` |
| `scripts/plot.py` | draws `report/figures/*` and the tables in `report/generated/*` |
| `results/` | the measurements used in the report |

## Build

From the `lab1` folder:

```bash
javac -d out src/lab1/*.java
```

## Prepare the data

```bash
java -cp out lab1.Main gen
```

`gen` writes `data/numbers.txt` (50 000 000 random integers from −1 000 000 to 1 000 000, seed 42)
and `data/numbers.txt.expected` with the correct counts; every task compares its totals with it
and prints `correct=true/false`. Options: `--count`, `--seed`, `--file`.

## Run a task

```bash
java -cp out lab1.Main 1a --threads 8
java -cp out lab1.Main 1b --mode unsync
java -cp out lab1.Main 1b --mode lock
java -cp out lab1.Main 1b --mode local --threads 12
java -cp out lab1.Main 1c --workers 4
java -cp out lab1.Main 1c --workers 4 --batch 1
java -cp out lab1.Main 1c --workers 4 --capacity 0
```

* `1b` uses as many threads as there are logical processors unless `--threads` is given.
* `1c --capacity 0` uses a queue with no size limit; `--batch` sets the lines per batch.

Every run prints one readable line and one `RESULT,...` line with the time (only the counting,
not JVM start-up or, in 1b, loading the array), the counts and the peak memory of the process
(Windows `PeakWorkingSet64`, Linux `VmHWM`).

## Run all measurements and draw the charts

```bash
python scripts/run_benchmarks.py
python scripts/plot.py
```

`run_benchmarks.py 1a 1c` runs only some tasks.

## Build the report

```bash
cd report
pdflatex Lab1_BogdanArama_FAF-242.tex
pdflatex Lab1_BogdanArama_FAF-242.tex
```
