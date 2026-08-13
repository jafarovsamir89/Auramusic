#!/usr/bin/env python3
"""Small repeatable SQLite lookup benchmark for a generated AURA artist index."""
from __future__ import annotations

import argparse
import statistics
import sqlite3
import time
from pathlib import Path

QUERIES = ("aygun kazimova", "roya", "miri yusif", "yuriy shatunov", "the weeknd", "linkin park", "beyonce")


def percentile(values: list[float], p: float) -> float:
    values = sorted(values)
    return values[min(len(values) - 1, int((len(values) - 1) * p))]


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("db", type=Path)
    parser.add_argument("--iterations", type=int, default=1000)
    args = parser.parse_args()
    db = sqlite3.connect(f"file:{args.db}?mode=ro", uri=True)
    methods = {
        "normal-index": lambda q: db.execute("SELECT id FROM artists WHERE normalized_name = ? OR folded_name = ? LIMIT 20", (q, q)).fetchall(),
        "prefix-index": lambda q: db.execute("SELECT id FROM artists WHERE normalized_name LIKE ? OR folded_name LIKE ? LIMIT 20", (q + "%", q + "%")).fetchall(),
    }
    if db.execute("SELECT 1 FROM sqlite_master WHERE name='artist_search'").fetchone():
        methods["fts5"] = lambda q: db.execute("SELECT rowid FROM artist_search WHERE artist_search MATCH ? LIMIT 20", (q.replace(" ", " AND "),)).fetchall()
    for name, query in methods.items():
        samples = []
        for index in range(args.iterations):
            value = QUERIES[index % len(QUERIES)]
            start = time.perf_counter_ns()
            query(value)
            samples.append((time.perf_counter_ns() - start) / 1_000_000)
        print(f"{name}: median={statistics.median(samples):.3f}ms p95={percentile(samples, .95):.3f}ms max={max(samples):.3f}ms")
    db.close()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
