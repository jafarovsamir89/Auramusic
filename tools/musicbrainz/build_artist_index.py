#!/usr/bin/env python3
"""Build AURA's compact offline artist identity index from MusicBrainz artist.tar.xz.

The raw dump never enters the Android project. The resulting SQLite schema is intentionally
shared with AssetArtistResolver.kt. MusicBrainz core data is CC0; see LICENSE-MUSICBRAINZ.md.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import re
import sqlite3
import sys
import tarfile
import tempfile
import time
import unicodedata
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
LATEST_INDEX = "https://ftp.musicbrainz.org/pub/musicbrainz/data/json-dumps/"
ARTIST_TYPES = {"Person", "Group", "Orchestra", "Choir", "Character", "Other"}
BAD_NAMES = {"test", "unknown", "unknown artist", "various artists", "various"}
AZ_FOLD = str.maketrans({"ə": "e", "ı": "i", "ö": "o", "ü": "u", "ş": "s", "ç": "c", "ğ": "g"})
CYRILLIC = {"а":"a","б":"b","в":"v","г":"g","д":"d","е":"e","ё":"yo","ж":"zh","з":"z","и":"i","й":"y","к":"k","л":"l","м":"m","н":"n","о":"o","п":"p","р":"r","с":"s","т":"t","у":"u","ф":"f","х":"kh","ц":"ts","ч":"ch","ш":"sh","щ":"shch","ъ":"","ы":"y","ь":"","э":"e","ю":"yu","я":"ya"}


def normalize(value: str) -> str:
    value = unicodedata.normalize("NFKC", value).lower().strip()
    value = re.sub(r"[’‘`']", "", value)
    value = re.sub(r"[‐‑‒–—−-]", " ", value)
    value = "".join(CYRILLIC.get(c, c) for c in value.translate(AZ_FOLD))
    value = unicodedata.normalize("NFKD", value)
    value = "".join(c for c in value if not unicodedata.combining(c))
    value = re.sub(r"[^\w]+", " ", value, flags=re.UNICODE)
    return re.sub(r"\s+", " ", value).strip()


def folded(value: str) -> str:
    return normalize(value)


def latest_dump_url() -> str:
    with urllib.request.urlopen(LATEST_INDEX, timeout=30) as response:
        html = response.read().decode("utf-8", "replace")
    versions = sorted(set(re.findall(r"href=\"(\d{8}-\d{6})/\"", html)))
    if not versions:
        raise RuntimeError("No timestamped MusicBrainz JSON dump found")
    return f"{LATEST_INDEX}{versions[-1]}/artist.tar.xz"


def download(url: str, output: Path) -> None:
    output.parent.mkdir(parents=True, exist_ok=True)
    with urllib.request.urlopen(url, timeout=60) as source, output.open("wb") as target:
        while chunk := source.read(1024 * 1024):
            target.write(chunk)


def iter_artists(archive: Path):
    with tarfile.open(archive, "r:xz") as tar:
        for member in tar:
            if not member.isfile() or not member.name.endswith((".json", ".jsonl")):
                continue
            stream = tar.extractfile(member)
            if stream is None:
                continue
            for raw in stream:
                line = raw.decode("utf-8", "replace").strip().rstrip(",")
                if not line or line in {"[", "]"}:
                    continue
                try:
                    value = json.loads(line)
                except json.JSONDecodeError:
                    continue
                if isinstance(value, dict):
                    yield value


def retained_artist(value: dict) -> bool:
    name = str(value.get("name", "")).strip()
    return bool(name) and name.lower() not in BAD_NAMES and (value.get("type") in ARTIST_TYPES or not value.get("type"))


def build(input_path: Path, output_path: Path, snapshot: str = "unknown") -> dict:
    started = time.monotonic()
    artists: dict[str, dict] = {}
    aliases: list[tuple[str, str, str, str]] = []
    parsed = retained = 0
    for value in iter_artists(input_path):
        parsed += 1
        if not retained_artist(value):
            continue
        mbid = str(value.get("id", "")).strip()
        name = str(value.get("name", "")).strip()
        if not mbid or not name:
            continue
        retained += 1
        artists[mbid] = {
            "mbid": mbid,
            "name": name,
            "normalized": normalize(name),
            "folded": folded(name),
            "sort": value.get("sort-name"),
            "country": value.get("country"),
            "type": value.get("type"),
            "disambiguation": value.get("disambiguation"),
            "aliases": set(),
        }
        for alias in value.get("aliases", []) or []:
            alias_name = str(alias.get("name", "")).strip() if isinstance(alias, dict) else str(alias).strip()
            if alias_name and alias_name != name:
                artists[mbid]["aliases"].add(alias_name)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    if output_path.exists():
        output_path.unlink()
    db = sqlite3.connect(output_path)
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
    """)
    try:
        db.execute("CREATE VIRTUAL TABLE artist_search USING fts5(canonical_name, aliases, normalized_name, folded_name, content='')")
    except sqlite3.OperationalError:
        pass
    alias_count = 0
    for local_id, item in enumerate(sorted(artists.values(), key=lambda row: row["mbid"]), 1):
        db.execute("INSERT INTO artists VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)", (local_id, item["mbid"], item["name"], item["normalized"], item["folded"], item["sort"], item["country"], item["type"], item["disambiguation"]))
        alias_values = sorted(item["aliases"], key=lambda alias: (folded(alias), alias))
        for alias in alias_values:
            db.execute("INSERT INTO artist_aliases VALUES (?, ?, ?, ?)", (local_id, alias, normalize(alias), folded(alias)))
            alias_count += 1
        try:
            db.execute("INSERT INTO artist_search(rowid, canonical_name, aliases, normalized_name, folded_name) VALUES (?, ?, ?, ?, ?)", (local_id, item["name"], " ".join(alias_values), item["normalized"], item["folded"]))
        except sqlite3.OperationalError:
            pass
    db.commit()
    db.execute("VACUUM")
    db.commit()
    checksum = hashlib.sha256(output_path.read_bytes()).hexdigest()
    db.execute("INSERT INTO artist_index_metadata VALUES (?, ?, ?, ?, ?)", ("1.0", snapshot, len(artists), alias_count, checksum))
    db.commit()
    db.close()
    return {"parsed": parsed, "retained": len(artists), "aliases": alias_count, "duplicates_removed": max(0, retained - len(artists)), "db_bytes": output_path.stat().st_size, "build_seconds": round(time.monotonic() - started, 2), "checksum": checksum}


def main() -> int:
    parser = argparse.ArgumentParser()
    sub = parser.add_subparsers(dest="command", required=True)
    dl = sub.add_parser("download")
    dl.add_argument("--url", default=None)
    dl.add_argument("--output", type=Path, default=Path("tools/musicbrainz/cache/artist.tar.xz"))
    b = sub.add_parser("build")
    b.add_argument("--input", type=Path, required=True)
    b.add_argument("--output", type=Path, default=Path("app/src/main/assets/aura_artists.db"))
    b.add_argument("--snapshot", default="unknown")
    args = parser.parse_args()
    if args.command == "download":
        url = args.url or latest_dump_url()
        download(url, args.output)
        print(json.dumps({"url": url, "output": str(args.output), "bytes": args.output.stat().st_size}, ensure_ascii=False))
        return 0
    report = build(args.input, args.output, args.snapshot)
    print(json.dumps(report, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
