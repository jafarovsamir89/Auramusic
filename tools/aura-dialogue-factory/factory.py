#!/usr/bin/env python3
"""Build and validate AURA's data-backed dialogue catalog."""

import argparse
import json
import re
import sqlite3
import sys
import unicodedata
from collections import Counter
from pathlib import Path


def load(path):
    with Path(path).open(encoding="utf-8") as handle:
        value = json.load(handle)
    if not isinstance(value, list):
        raise ValueError("catalog root must be an array")
    return value


def normalize_text(value):
    value = unicodedata.normalize("NFKC", str(value)).strip().lower()
    return re.sub(r"\s+", " ", value)


def validate(nodes):
    errors = []
    ids = set()
    for index, node in enumerate(nodes):
        prefix = f"node {index}"
        if not isinstance(node, dict):
            errors.append(f"{prefix}: expected object")
            continue
        node_id = node.get("id")
        if not isinstance(node_id, str) or not node_id.strip():
            errors.append(f"{prefix}: missing id")
        elif node_id in ids:
            errors.append(f"{prefix}: duplicate id {node_id}")
        ids.add(node_id)
        if node.get("language") not in {"ru", "az", "en"}:
            errors.append(f"{prefix}: unsupported language")
        for field in ("intents", "patterns", "responses"):
            values = node.get(field)
            if not isinstance(values, list) or not values or any(
                not isinstance(item, str) or not item.strip() for item in values
            ):
                errors.append(f"{prefix}: {field} must contain non-empty strings")
    if errors:
        raise ValueError("\n".join(errors))


def normalized(nodes):
    result = []
    for node in nodes:
        copy = dict(node)
        for field in ("intents", "patterns", "responses", "context", "next"):
            if field in copy:
                copy[field] = list(dict.fromkeys(normalize_text(item) for item in copy[field]))
        copy["id"] = normalize_text(copy["id"])
        copy["language"] = normalize_text(copy["language"])
        result.append(copy)
    return result


def deduplicate(nodes):
    result = []
    seen = set()
    for node in nodes:
        key = (node["language"], tuple(sorted(normalize_text(p) for p in node["patterns"])))
        if key in seen:
            continue
        seen.add(key)
        result.append(node)
    return result


def write(path, nodes):
    Path(path).write_text(json.dumps(nodes, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def stats(nodes):
    languages = Counter(node["language"] for node in nodes)
    patterns = sum(len(node["patterns"]) for node in nodes)
    responses = sum(len(node["responses"]) for node in nodes)
    print(json.dumps({"nodes": len(nodes), "patterns": patterns, "responses": responses,
                      "languages": dict(sorted(languages.items()))}, ensure_ascii=False, indent=2))


def export_sqlite(path, nodes):
    connection = sqlite3.connect(path)
    connection.executescript("""
        PRAGMA user_version = 1;
        CREATE TABLE dialogue_node(id TEXT PRIMARY KEY, language TEXT NOT NULL,
          intents_json TEXT NOT NULL, responses_json TEXT NOT NULL,
          context_json TEXT NOT NULL, emotion TEXT NOT NULL);
        CREATE TABLE dialogue_pattern(node_id TEXT NOT NULL, pattern TEXT NOT NULL,
          PRIMARY KEY(node_id, pattern));
    """)
    for node in nodes:
        connection.execute("INSERT INTO dialogue_node VALUES (?, ?, ?, ?, ?, ?)", (
            node["id"], node["language"], json.dumps(node["intents"]),
            json.dumps(node["responses"]), json.dumps(node.get("context", [])),
            node.get("emotion", "neutral")))
        connection.executemany("INSERT INTO dialogue_pattern VALUES (?, ?)",
                               [(node["id"], pattern) for pattern in node["patterns"]])
    connection.commit()
    connection.close()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("command", choices=["validate", "normalize", "deduplicate", "compile",
                                             "statistics", "test-corpus", "export-sqlite"])
    parser.add_argument("input")
    parser.add_argument("output", nargs="?")
    args = parser.parse_args()
    nodes = load(args.input)
    validate(nodes)
    nodes = normalized(nodes)
    if args.command == "validate":
        print(f"valid: {len(nodes)} nodes")
    elif args.command == "normalize":
        write(args.output, nodes)
    elif args.command == "deduplicate":
        write(args.output, deduplicate(nodes))
    elif args.command == "compile":
        write(args.output, deduplicate(nodes))
    elif args.command == "statistics":
        stats(nodes)
    elif args.command == "test-corpus":
        write(args.output, [{"id": node["id"], "language": node["language"],
                             "text": pattern, "expected_intents": node["intents"]}
                            for node in nodes for pattern in node["patterns"]])
    else:
        export_sqlite(args.output, deduplicate(nodes))


if __name__ == "__main__":
    try:
        main()
    except (OSError, ValueError, json.JSONDecodeError) as error:
        print(f"error: {error}", file=sys.stderr)
        raise SystemExit(2)
