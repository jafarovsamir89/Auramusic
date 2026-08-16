#!/usr/bin/env python3
"""Build a small APK artist index from the full local MusicBrainz index.

Popularity is collected at build time only. The Android app never calls these APIs.
ListenBrainz supplies MusicBrainz IDs directly. YouTube charts are optional because
the Data API requires the builder owner's key; chart data is never shipped as-is.
"""
from __future__ import annotations

import argparse
import json
import math
import sqlite3
import time
import urllib.parse
import urllib.request
from pathlib import Path

LISTENBRAINZ = "https://api.listenbrainz.org/1/stats/sitewide/artists"
YOUTUBE_VIDEOS = "https://www.googleapis.com/youtube/v3/videos"
DEFAULT_COUNTRIES = "AZ,RU,UA,KZ,BY,AM,GE,MD,KG,UZ,TJ,TM,TR"


def get_json(url: str) -> dict:
    request = urllib.request.Request(url, headers={"User-Agent": "AURA-MusicBrain/1.0"})
    with urllib.request.urlopen(request, timeout=45) as response:
        return json.load(response)


def fetch_listenbrainz(limit: int) -> list[dict]:
    # The public endpoint currently caps one response at 1,000 rows.
    count = min(max(limit, 1), 1000)
    payload = get_json(f"{LISTENBRAINZ}?{urllib.parse.urlencode({'range': 'month', 'count': count})}").get("payload", {})
    return [row for row in payload.get("artists", []) if row.get("artist_mbid") and row.get("listen_count")]


def fetch_youtube_chart(api_key: str, regions: list[str], limit_per_region: int) -> list[dict]:
    scores: dict[str, dict] = {}
    for region in regions:
        query = urllib.parse.urlencode({
            "part": "snippet,statistics",
            "chart": "mostPopular",
            "videoCategoryId": "10",
            "regionCode": region,
            "maxResults": min(limit_per_region, 50),
            "key": api_key,
        })
        data = get_json(f"{YOUTUBE_VIDEOS}?{query}")
        for rank, item in enumerate(data.get("items", [])):
            snippet = item.get("snippet", {})
            title = str(snippet.get("title", ""))
            channel = str(snippet.get("channelTitle", ""))
            # Preserve title/channel as matching hints; no video IDs are stored in the APK index.
            for name in (channel, title.split(" - ", 1)[0], title.split(" – ", 1)[0]):
                if name.strip():
                    key = name.strip()
                    record = scores.setdefault(key, {"name": key, "youtube_score": 0.0, "regions": set()})
                    record["youtube_score"] += 1.0 / (rank + 1)
                    record["regions"].add(region)
    return list(scores.values())


def build(args: argparse.Namespace) -> dict:
    source = sqlite3.connect(args.full_index)
    output = Path(args.output)
    if output.exists(): output.unlink()
    selected: dict[int, float] = {}
    listen_rows = fetch_listenbrainz(args.listen_count) if not args.no_listenbrainz else []
    for rank, row in enumerate(listen_rows):
        found = source.execute("SELECT id FROM artists WHERE mbid = ?", (row["artist_mbid"],)).fetchone()
        if found:
            selected[found[0]] = max(selected.get(found[0], 0.0), 1.0 / (rank + 1))
    countries = [part.strip().upper() for part in args.countries.split(",") if part.strip()]
    if countries:
        marks = ",".join("?" for _ in countries)
        for artist_id, in source.execute(f"SELECT id FROM artists WHERE country IN ({marks})", countries):
            selected[artist_id] = max(selected.get(artist_id, 0.0), args.region_score)
    youtube_rows = []
    if args.youtube_key:
        youtube_rows = fetch_youtube_chart(args.youtube_key, args.youtube_regions.split(","), args.youtube_limit)
        for row in youtube_rows:
            normalized = row["name"].strip().lower()
            found = source.execute(
                "SELECT a.id FROM artists a LEFT JOIN artist_aliases al ON al.artist_id = a.id WHERE lower(a.canonical_name) = ? OR lower(al.alias) = ? LIMIT 1",
                (normalized, normalized),
            ).fetchone()
            if found: selected[found[0]] = max(selected.get(found[0], 0.0), args.youtube_score * row["youtube_score"])
    output.parent.mkdir(parents=True, exist_ok=True)
    db = sqlite3.connect(output)
    db.executescript("""
        PRAGMA journal_mode=OFF;
        PRAGMA synchronous=OFF;
        CREATE TABLE artist_index_metadata(version TEXT NOT NULL, snapshot_date TEXT NOT NULL, artist_count INTEGER NOT NULL, alias_count INTEGER NOT NULL, checksum TEXT);
        CREATE TABLE artists(id INTEGER PRIMARY KEY, mbid TEXT NOT NULL UNIQUE, canonical_name TEXT NOT NULL, normalized_name TEXT NOT NULL, folded_name TEXT NOT NULL, sort_name TEXT, country TEXT, type TEXT, disambiguation TEXT);
        CREATE TABLE artist_aliases(artist_id INTEGER NOT NULL, alias TEXT NOT NULL, normalized_alias TEXT NOT NULL, folded_alias TEXT NOT NULL, PRIMARY KEY(artist_id, alias));
        CREATE INDEX artists_normalized_idx ON artists(normalized_name);
        CREATE INDEX artists_folded_idx ON artists(folded_name);
        CREATE INDEX aliases_normalized_idx ON artist_aliases(normalized_alias);
        CREATE INDEX aliases_folded_idx ON artist_aliases(folded_alias);
        CREATE VIRTUAL TABLE artist_search USING fts5(canonical_name, aliases, normalized_name, folded_name, content='');
    """)
    alias_count = 0
    for local_id, (source_id, score) in enumerate(sorted(selected.items(), key=lambda item: (-item[1], item[0])), 1):
        row = source.execute("SELECT mbid, canonical_name, normalized_name, folded_name, sort_name, country, type, disambiguation FROM artists WHERE id = ?", (source_id,)).fetchone()
        if not row: continue
        mbid, canonical, normalized, folded, sort_name, country, artist_type, disambiguation = row
        db.execute("INSERT INTO artists VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)", (local_id, mbid, canonical, normalized, folded, sort_name, country, artist_type, disambiguation))
        aliases = [a[0] for a in source.execute("SELECT alias FROM artist_aliases WHERE artist_id = ? ORDER BY folded_alias, alias", (source_id,))]
        for alias in aliases:
            values = source.execute("SELECT normalized_alias, folded_alias FROM artist_aliases WHERE artist_id = ? AND alias = ?", (source_id, alias)).fetchone()
            db.execute("INSERT INTO artist_aliases VALUES (?, ?, ?, ?)", (local_id, alias, values[0], values[1])); alias_count += 1
        db.execute("INSERT INTO artist_search(rowid, canonical_name, aliases, normalized_name, folded_name) VALUES (?, ?, ?, ?, ?)", (local_id, canonical, " ".join(aliases), normalized, folded))
    db.execute("INSERT INTO artist_index_metadata VALUES (?, ?, ?, ?, ?)", ("priority-1.0", args.snapshot, len(selected), alias_count, None))
    db.commit(); db.execute("VACUUM"); db.commit(); db.close(); source.close()
    return {"listenbrainz_rows": len(listen_rows), "youtube_hints": len(youtube_rows), "countries": countries, "selected_artists": len(selected), "aliases": alias_count, "db_bytes": output.stat().st_size}


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--full-index", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--snapshot", default="unknown")
    parser.add_argument("--listen-count", type=int, default=1000)
    parser.add_argument("--no-listenbrainz", action="store_true")
    parser.add_argument("--countries", default=DEFAULT_COUNTRIES)
    parser.add_argument("--region-score", type=float, default=0.75)
    parser.add_argument("--youtube-key")
    parser.add_argument("--youtube-regions", default="AZ,RU,TR,US,GB")
    parser.add_argument("--youtube-limit", type=int, default=50)
    parser.add_argument("--youtube-score", type=float, default=0.25)
    args = parser.parse_args()
    print(json.dumps(build(args), ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
