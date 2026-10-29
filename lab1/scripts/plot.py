"""Draws the report charts (report/figures/*.pdf) and result tables (report/generated/*.tex)
from the CSV files written by run_benchmarks.py.

For every configuration the median of the repetitions is plotted, and the shaded band shows
the fastest and slowest repetition. Speed-up = median time on 1 thread / median time on N.
"""
import csv
import os
import statistics
from collections import defaultdict
from pathlib import Path

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt  # noqa: E402
from matplotlib.ticker import FixedLocator, NullLocator  # noqa: E402

LAB = Path(__file__).resolve().parent.parent
RESULTS = LAB / "results"
FIG = LAB / "report" / "figures"
GEN = LAB / "report" / "generated"
PHYSICAL, LOGICAL = 6, os.cpu_count()

SERIES = ["#2a78d6", "#eb6834", "#1baf7a"]  # categorical slots 1-3, in order
MARKERS = ["o", "s", "^"]
REFERENCE = "#8a8984"
INK, INK2, GRID = "#0b0b0b", "#52514e", "#e4e3df"

plt.rcParams.update({
    "font.family": "serif", "font.serif": ["Times New Roman", "DejaVu Serif"], "font.size": 8.5,
    "axes.edgecolor": INK2, "axes.labelcolor": INK, "axes.linewidth": 0.6,
    "xtick.color": INK2, "ytick.color": INK2, "xtick.labelcolor": INK, "ytick.labelcolor": INK,
    "axes.grid": True, "grid.color": GRID, "grid.linewidth": 0.6,
    "axes.spines.top": False, "axes.spines.right": False,
    "legend.frameon": False, "legend.fontsize": 8, "pdf.fonttype": 42,
})


def load(name):
    with open(RESULTS / f"{name}.csv") as f:
        return list(csv.DictReader(f))


def group(rows, key=lambda r: ""):
    """{series: {threads: [rows]}}"""
    out = defaultdict(lambda: defaultdict(list))
    for r in rows:
        out[key(r)][int(r["threads"])].append(r)
    return out


def stats(by_threads, field, scale=1.0):
    ts = sorted(by_threads)
    vals = [[float(r[field]) / scale for r in by_threads[t]] for t in ts]
    return ts, [statistics.median(v) for v in vals], [min(v) for v in vals], [max(v) for v in vals]


def axes_row(xs, xlabel):
    fig, axs = plt.subplots(1, 3, figsize=(7.1, 2.35), layout="constrained")
    titles = ["Execution time (ms)", "Speed-up vs. 1 thread", "Peak memory (MB)"]
    for ax, title in zip(axs, titles):
        ax.set_xscale("log", base=2)
        ax.xaxis.set_major_locator(FixedLocator(xs))
        ax.xaxis.set_minor_locator(NullLocator())
        ax.set_xticklabels([str(x) for x in xs])
        ax.set_xlabel(xlabel)
        ax.set_title(title, fontsize=9, color=INK, loc="left")
        ax.set_ylim(bottom=0, auto=True)
        if max(xs) >= LOGICAL:
            for x in (PHYSICAL, LOGICAL):
                ax.axvline(x, color=REFERENCE, lw=0.8, ls=":", zorder=0)
    if max(xs) >= LOGICAL:
        top = axs[0].get_xaxis_transform()
        axs[0].text(PHYSICAL, 1.0, f"{PHYSICAL} cores ", transform=top, ha="right", va="top", fontsize=7, color=INK2)
        axs[0].text(LOGICAL, 1.0, f" {LOGICAL} logical", transform=top, ha="left", va="top", fontsize=7, color=INK2)
    return fig, axs


def plot_series(axs, by_threads, i, label, speedup=True):
    color, marker = SERIES[i], MARKERS[i]
    style = dict(color=color, marker=marker, ms=4, lw=1.6, label=label)
    ts, med, lo, hi = stats(by_threads, "time_ms")
    axs[0].plot(ts, med, **style)
    axs[0].fill_between(ts, lo, hi, color=color, alpha=0.15, lw=0)
    if speedup:
        axs[1].plot(ts, [med[0] / m for m in med], **style)
    ts, mem, mlo, mhi = stats(by_threads, "peak_rss_bytes", 1e6)
    axs[2].plot(ts, mem, **style)
    axs[2].fill_between(ts, mlo, mhi, color=color, alpha=0.15, lw=0)


def ideal_speedup(ax, xs):
    """Dashed y = x line; the axis is cut a little above the best measured speed-up."""
    best = max(max(line.get_ydata()) for line in ax.get_lines())
    ax.plot(xs, xs, color=REFERENCE, lw=1, ls="--", label="ideal (= threads)")
    ax.set_ylim(0, max(best * 1.5, 2))


def finish(fig, axs, name, legend=True):
    for ax in axs:
        ax.set_ylim(bottom=0)
    if legend:
        found = {}
        for ax in axs:
            for h, label in zip(*ax.get_legend_handles_labels()):
                found.setdefault(label, h)
        fig.legend(found.values(), found.keys(), loc="outside lower center", ncol=len(found))
    FIG.mkdir(parents=True, exist_ok=True)
    fig.savefig(FIG / f"{name}.pdf")
    fig.savefig(FIG / f"{name}.png", dpi=160)
    plt.close(fig)


def table(name, header, rows, align):
    GEN.mkdir(parents=True, exist_ok=True)
    lines = [r"\begin{tabular}{" + align + "}", r"\toprule", " & ".join(header) + r" \\", r"\midrule"]
    lines += [" & ".join(str(c) for c in row) + r" \\" for row in rows]
    lines += [r"\bottomrule", r"\end{tabular}"]
    (GEN / f"{name}.tex").write_text("\n".join(lines) + "\n")


def thread_table(name, by_threads):
    ts, med, lo, hi = stats(by_threads, "time_ms")
    _, mem, _, _ = stats(by_threads, "peak_rss_bytes", 1e6)
    rows = [[t, f"{m:.0f}", f"{a:.0f}--{b:.0f}", f"{med[0] / m:.2f}", f"{mm:.0f}"]
            for t, m, a, b, mm in zip(ts, med, lo, hi, mem)]
    table(name, ["Threads", "Median time (ms)", "Min--max (ms)", "Speed-up", "Peak memory (MB)"], rows, "rrrrr")
    return dict(zip(ts, med))


def task_1a():
    g = group(load("task1a"))[""]
    xs = sorted(g)
    fig, axs = axes_row(xs, "threads")
    plot_series(axs, g, 0, "Task 1a")
    ideal_speedup(axs[1], xs)
    finish(fig, axs, "task1a")
    return thread_table("task1a", g)


def task_1b():
    rows = load("task1b")
    g = group(rows, lambda r: r["variant"])
    xs = sorted(g["local"])
    fig, axs = axes_row(xs, "threads")
    names = {"unsync": "1: shared, no sync (wrong totals)", "lock": "2: shared + lock", "local": "3: per-thread counters"}
    for i, mode in enumerate(("unsync", "lock", "local")):
        plot_series(axs, g[mode], i, names[mode], speedup=mode != "unsync")
    ideal_speedup(axs[1], xs)
    finish(fig, axs, "task1b")

    # every run at the number of logical processors, with its totals
    out = []
    for mode in ("unsync", "lock", "local"):
        for r in sorted(g[mode][LOGICAL], key=lambda r: int(r["rep"])):
            out.append([names[mode].split(" (")[0], r["rep"], f"{float(r['time_ms']):.1f}",
                        f"{int(r['negative']):,}", f"{int(r['positive']):,}", r["zero"], f"{int(r['total']):,}",
                        "yes" if r["correct"] == "true" else "\\textbf{no}"])
    table("task1b_runs", ["Version", "Run", "Time (ms)", "Negative", "Positive", "Zero", "Total", "Correct"], out,
          "lrrrrrrc")
    tab = []
    for t in xs:
        tab.append([t] + [f"{statistics.median(float(r['time_ms']) for r in g[m][t]):.0f}" for m in ("unsync", "lock", "local")]
                   + [f"{statistics.median(int(r['total']) for r in g['unsync'][t]) / 1e6:.1f}M"])
    table("task1b_threads", ["Threads", "1: no sync (ms)", "2: lock (ms)", "3: per-thread (ms)", "1: median total"],
          tab, "rrrrr")


def task_1c(t1a):
    g = group(load("task1c"))[""]
    xs = sorted(g)
    fig, axs = axes_row(xs, "worker threads (plus 1 reader)")
    plot_series(axs, g, 0, "Task 1c (producer–consumer)")
    a = group(load("task1a"))[""]
    ts, med, _, _ = stats(a, "time_ms")
    axs[0].plot(ts, med, color=REFERENCE, lw=1.2, ls="-.", label="Task 1a (for comparison)")
    ideal_speedup(axs[1], xs)
    finish(fig, axs, "task1c")
    thread_table("task1c", g)
    waits = [[t, statistics.median(int(r["worker_waits"]) for r in g[t]),
              statistics.median(int(r["reader_waits"]) for r in g[t]),
              max(int(r["max_queued"]) for r in g[t])] for t in xs]
    table("task1c_waits", ["Workers", "Worker waits (empty queue)", "Reader waits (full queue)",
                           "Max batches in queue"], waits, "rrrr")

    v = load("task1c_variants")
    rows = []
    for r in v:
        batch, cap = (kv.split("=")[1] for kv in r["variant"].split(";"))
        rows.append([f"{int(batch):,}", "no limit" if cap == "0" else cap, r["threads"], f"{float(r['time_ms']):.0f}",
                     f"{int(r['peak_rss_bytes']) / 1e6:.0f}", r["max_queued"], r["worker_waits"]])
    base = [[f"{10000:,}", "100", t, f"{statistics.median(float(r['time_ms']) for r in g[t]):.0f}",
             f"{statistics.median(int(r['peak_rss_bytes']) for r in g[t]) / 1e6:.0f}",
             max(int(r["max_queued"]) for r in g[t]), f"{statistics.median(int(r['worker_waits']) for r in g[t]):.0f}"]
            for t in sorted({int(r["threads"]) for r in v})]
    rows = sorted(base + rows, key=lambda r: (int(r[2]), r[0] != "10,000" or r[1] != "100", r[0]))
    table("task1c_variants", ["Batch (lines)", "Queue limit", "Workers", "Time (ms)", "Peak memory (MB)",
                              "Max batches in queue", "Worker waits"], rows, "rrrrrrr")


if __name__ == "__main__":
    t1a = task_1a()
    task_1b()
    task_1c(t1a)
    print("figures in", FIG, "and tables in", GEN)
