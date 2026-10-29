"""Runs every task and configuration of the lab and writes the results to results/<task>.csv.

Every configuration runs in a fresh JVM, so the peak memory belongs to that configuration only.
Repetitions are interleaved (all thread counts once, then all again, ...) so that slow drift of
the machine (temperature, background work) spreads over all configurations.

Usage (from the lab1 folder, after building into out/):
    python scripts/run_benchmarks.py                 # everything
    python scripts/run_benchmarks.py 1a 1c           # only some tasks
"""
import argparse
import csv
import os
import subprocess
import sys
from pathlib import Path

LAB = Path(__file__).resolve().parent.parent
THREADS = [1, 2, 4, 6, 8, 12, 16, 32, 64]
LOGICAL = os.cpu_count()


def run_java(args):
    cmd = ["java", "-cp", str(LAB / "out"), "lab1.Main", *map(str, args)]
    out = subprocess.run(cmd, cwd=LAB, capture_output=True, text=True, check=True).stdout
    line = next(l for l in out.splitlines() if l.startswith("RESULT,"))
    row = dict(kv.split("=", 1) for kv in line.split(",")[1:])
    print("  " + " ".join(f"{k}={row[k]}" for k in ("task", "threads", "variant", "time_ms", "total", "correct")))
    if row["correct"] != "true" and not (row["task"] == "1b" and row["variant"] == "unsync"):
        sys.exit(f"WRONG TOTALS: {line}")
    return row


def save(name, rows):
    path = LAB / "results" / f"{name}.csv"
    path.parent.mkdir(exist_ok=True)
    keys = list(dict.fromkeys(k for r in rows for k in r))
    with open(path, "w", newline="") as f:
        w = csv.DictWriter(f, fieldnames=["rep", *[k for k in keys if k != "rep"]])
        w.writeheader()
        w.writerows(rows)
    print(f"-> {path}")


def task_1a(reps):
    run_java(["1a", "--threads", 4])  # warm-up: brings numbers.txt into the file cache
    rows = []
    for rep in range(1, reps + 1):
        for t in THREADS:
            rows.append({"rep": rep, **run_java(["1a", "--threads", t])})
    save("task1a", rows)


def task_1b(reps):
    rows = []
    for rep in range(1, reps + 1):
        for mode in ("unsync", "lock", "local"):
            for t in THREADS:
                rows.append({"rep": rep, **run_java(["1b", "--mode", mode, "--threads", t])})
    save("task1b", rows)


def task_1c(reps):
    run_java(["1c", "--workers", 4])  # warm-up
    rows = []
    for rep in range(1, reps + 1):
        for w in THREADS:
            rows.append({"rep": rep, **run_java(["1c", "--workers", w])})
    save("task1c", rows)
    # Question 6: batches of a single line, and a queue with no size limit
    rows = []
    for w in (1, 4, LOGICAL):
        rows.append({"rep": 1, **run_java(["1c", "--workers", w, "--batch", 1])})
        rows.append({"rep": 1, **run_java(["1c", "--workers", w, "--capacity", 0])})
    save("task1c_variants", rows)


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("tasks", nargs="*", default=["1a", "1b", "1c"])
    ap.add_argument("--reps", type=int, default=5)
    a = ap.parse_args()
    for task in a.tasks:
        print(f"== Task {task}")
        {"1a": lambda: task_1a(a.reps),
         "1b": lambda: task_1b(min(a.reps, 3)),
         "1c": lambda: task_1c(a.reps)}[task]()
