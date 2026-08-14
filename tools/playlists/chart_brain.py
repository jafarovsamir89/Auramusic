#!/usr/bin/env python3
"""A deterministic, build-time chart brain for AURA.

The Android app receives only a compact metadata snapshot.  Source adapters may
write JSON rows into ``tools/playlists/sources``; this module normalises titles,
deduplicates versions and merges ranks using reciprocal-rank fusion.  Network
access never happens in the app, so a broken chart page cannot break playback.
"""

from __future__ import annotations

import argparse
import json
import re
import unicodedata
from datetime import datetime, timezone
from pathlib import Path
from typing import Iterable


SOURCE_WEIGHTS = {
    "official-charts": 1.20,
    "apple-music": 1.15,
    "spotify": 1.15,
    "youtube-most-popular": 1.10,
    "listenbrainz": 0.90,
    "curated": 0.70,
}


def normalise(value: str) -> str:
    value = unicodedata.normalize("NFKD", value).encode("ascii", "ignore").decode()
    value = re.sub(r"[^a-z0-9а-яёәğıöşüç0-9]+", " ", value.lower())
    return re.sub(r"\s+", " ", value).strip()


def merge_sources(source_rows: dict[str, list[dict]], limit: int = 50) -> list[dict]:
    """Merge chart ranks without letting one source dominate the result."""
    merged: dict[str, dict] = {}
    for source, rows in source_rows.items():
        weight = SOURCE_WEIGHTS.get(source, 0.75)
        for fallback_rank, row in enumerate(rows, 1):
            artist = str(row.get("artist", "")).strip()
            title = str(row.get("title", "")).strip()
            if not artist or not title:
                continue
            key = f"{normalise(artist)}\0{normalise(title)}"
            rank = max(1, int(row.get("rank", fallback_rank) or fallback_rank))
            entry = merged.setdefault(key, {
                "artist": artist,
                "title": title,
                "score": 0.0,
                "sources": set(),
                "year": row.get("year"),
                "sourceUrl": row.get("sourceUrl"),
            })
            entry["score"] += weight / (rank + 4.0)
            entry["sources"].add(source)
            entry["year"] = entry["year"] or row.get("year")
            entry["sourceUrl"] = entry["sourceUrl"] or row.get("sourceUrl")
    ranked = sorted(merged.values(), key=lambda item: (-item["score"], item["artist"], item["title"]))[:limit]
    return [
        {
            "rank": index,
            "artist": item["artist"],
            "title": item["title"],
            **({"year": item["year"]} if item["year"] else {}),
            **({"sourceUrl": item["sourceUrl"]} if item["sourceUrl"] else {}),
            "sourceScore": round(item["score"], 5),
        }
        for index, item in enumerate(ranked, 1)
    ]


def read_source_snapshots(directory: Path) -> dict[str, list[dict]]:
    result: dict[str, list[dict]] = {}
    if not directory.exists():
        return result
    for path in sorted(directory.glob("*.json")):
        try:
            payload = json.loads(path.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError):
            continue
        source = str(payload.get("source", path.stem))
        rows = payload.get("items", payload if isinstance(payload, list) else [])
        if isinstance(rows, list):
            result[source] = rows
    return result


def playlist(identifier: str, title: str, kind: str, source: str, region: str, items: list[dict], visual: str, badge: str, description: str) -> dict:
    return {
        "id": identifier,
        "title": title,
        "description": description,
        "region": region,
        "language": "multi",
        "kind": kind,
        "source": source,
        "updatedAt": datetime.now(timezone.utc).date().isoformat(),
        "visualKey": visual,
        "badge": badge,
        "featured": kind != "ARTIST_HITS",
        "items": items,
    }


def classic_items(rows: Iterable[tuple[str, str]], year: int | None = None) -> list[dict]:
    return [
        {"rank": rank, "artist": artist, "title": title, **({"year": year} if year else {})}
        for rank, (artist, title) in enumerate(rows, 1)
    ]


def diversify_artists(rows: list[dict], max_per_artist: int = 3) -> list[dict]:
    """Prevent a malformed/provider-biased snapshot from becoming one-artist radio."""
    counts: dict[str, int] = {}
    selected: list[dict] = []
    for row in rows:
        artist_key = normalise(str(row.get("artist", "")))
        if not artist_key or counts.get(artist_key, 0) >= max_per_artist:
            continue
        counts[artist_key] = counts.get(artist_key, 0) + 1
        selected.append({**row, "rank": len(selected) + 1})
    return selected


REAL_TREND_FALLBACK = [
    ("The Weeknd", "Blinding Lights"), ("Billie Eilish", "BIRDS OF A FEATHER"),
    ("Sabrina Carpenter", "Espresso"), ("Lady Gaga", "Abracadabra"),
    ("ROSÉ & Bruno Mars", "APT."), ("Kendrick Lamar", "Not Like Us"),
    ("Benson Boone", "Beautiful Things"), ("Teddy Swims", "Lose Control"),
    ("Dua Lipa", "Houdini"), ("Taylor Swift", "Cruel Summer"),
    ("Harry Styles", "As It Was"), ("Miley Cyrus", "Flowers"),
    ("Post Malone", "Circles"), ("Ed Sheeran", "Shape of You"),
    ("Bruno Mars", "Just the Way You Are"), ("Adele", "Easy On Me"),
    ("Coldplay", "Viva La Vida"), ("Imagine Dragons", "Believer"),
    ("David Guetta", "Titanium"), ("Rihanna", "Diamonds"),
]


NINETIES = [
    ("Nirvana", "Smells Like Teen Spirit"), ("Oasis", "Wonderwall"), ("R.E.M.", "Losing My Religion"),
    ("The Cranberries", "Zombie"), ("Metallica", "Nothing Else Matters"), ("Red Hot Chili Peppers", "Under the Bridge"),
    ("Guns N' Roses", "November Rain"), ("Radiohead", "Creep"), ("No Doubt", "Don't Speak"),
    ("The Verve", "Bitter Sweet Symphony"), ("Dr. Dre", "Still D.R.E."), ("2Pac", "California Love"),
    ("The Notorious B.I.G.", "Juicy"), ("Eminem", "My Name Is"), ("Céline Dion", "My Heart Will Go On"),
    ("Britney Spears", "...Baby One More Time"), ("Backstreet Boys", "I Want It That Way"), ("Spice Girls", "Wannabe"),
    ("TLC", "No Scrubs"), ("Mariah Carey", "Fantasy"), ("Madonna", "Frozen"), ("Shania Twain", "Man! I Feel Like a Woman!"),
    ("Aqua", "Barbie Girl"), ("Haddaway", "What Is Love"), ("Roxette", "It Must Have Been Love"),
    ("The Prodigy", "Breathe"), ("Faithless", "Insomnia"), ("Jamiroquai", "Virtual Insanity"),
    ("Moby", "Porcelain"), ("Sting", "Fields of Gold"), ("ДДТ", "Что такое осень"), ("Кино", "Кукушка"),
    ("Руки Вверх!", "Крошка моя"), ("Иванушки International", "Тучи"), ("Ария", "Беспечный ангел"),
    ("Земфира", "Ариведерчи"), ("Алла Пугачёва", "Позови меня с собой"), ("Валерий Меладзе", "Сэра"),
    ("Вирус", "Ты меня не ищи"), ("Мумий Тролль", "Утекай"), ("A'Studio", "Улетаю"), ("Кар-Мэн", "Лондон, гудбай"),
]

FIFTY_CENT = [
    "In Da Club", "21 Questions", "P.I.M.P.", "Candy Shop", "Many Men (Wish Death)", "Just a Lil Bit",
    "Disco Inferno", "Hate It or Love It", "Outta Control (Remix)", "Ayo Technology", "Best Friend",
    "Wanksta", "Patiently Waiting", "If I Can't", "Window Shopper", "Life's on the Line",
]

RUKI = [
    "Крошка моя", "Он тебя целует", "18 мне уже", "Алёшка", "Студент", "Ай-яй-яй",
    "Лишь о тебе мечтая", "Чужие губы", "Здравствуй, это я", "Песенка", "Пропадаешь ты",
    "Маленькие девочки", "Мне с тобой хорошо", "Мой малыш", "Ну где же вы, девчонки",
]


def build(existing: dict, source_dir: Path) -> dict:
    snapshots = read_source_snapshots(source_dir)
    has_external_snapshot = bool(snapshots)
    old_top = next((item.get("items", []) for item in existing.get("playlists", []) if item.get("id") == "top-week-world"), [])
    if old_top:
        snapshots.setdefault("listenbrainz", old_top)
    merged_raw = merge_sources(snapshots, 50)
    raw_artist_counts: dict[str, int] = {}
    for row in merged_raw:
        key = normalise(str(row.get("artist", "")))
        raw_artist_counts[key] = raw_artist_counts.get(key, 0) + 1
    dominant = max(raw_artist_counts.values(), default=0) / max(len(merged_raw), 1)
    merged = diversify_artists(merged_raw, max_per_artist=3)
    if not has_external_snapshot or dominant >= 0.35 or len({normalise(str(row.get("artist", ""))) for row in merged}) < 8:
        merged = classic_items(REAL_TREND_FALLBACK)
    if not merged:
        merged = old_top[:50]
    top_today = merged[:50]
    new_items = merged[:20]
    az_items = snapshots.get("youtube-az", [])[:50]
    if not az_items:
        az_items = classic_items([
            ("Miri Yusif", "Ağ Qarğa"), ("Röya", "Sənə görə"), ("TuralTuranX", "Tell Me More"),
            ("Aygün Kazımova", "S.U.S."), ("Eldar Mansurov", "Bayatılar"), ("Miri Yusif", "Karma"),
            ("Röya", "Bənövşə"), ("Mugham Project", "Sarı Gəlin"),
        ])
    playlists = [
        playlist("top-today-world", "Топ сегодня · Мир", "TOP_TODAY", "chart-brain · 5 источников", "world", top_today, "sunset", "TODAY", "Ежедневный срез, собранный из нескольких чартов."),
        playlist("top-week-world", "Топ недели · Мир", "TOP_WEEK", "chart-brain · 5 источников", "world", merged, "neon", "WEEKLY", "Недельный рейтинг: совпадения между источниками получают больший вес."),
        playlist("top-today-az", "Топ сегодня · Азербайджан", "REGIONAL", "youtube + curated", "AZ", az_items, "azeri", "AZERBAIJAN", "Региональная подборка для Азербайджана."),
        playlist("new-this-week", "Новинки этой недели", "NEW_RELEASES", "chart-brain", "world", new_items, "gold", "NEW", "Свежие треки из текущих источников."),
        playlist("hits-90s", "Хиты 90-х", "DECADE", "AURA editorial", "world", classic_items(NINETIES), "classic", "1990s", "Постоянная редакционная коллекция — не меняется от недели к неделе."),
        playlist("artist-50cent", "Лучшее · 50 Cent", "ARTIST_HITS", "AURA editorial", "world", classic_items([("50 Cent", title) for title in FIFTY_CENT]), "gold", "50 CENT", "Постоянная подборка хитов артиста."),
        playlist("artist-ruki-vverh", "Лучшее · Руки Вверх!", "ARTIST_HITS", "AURA editorial", "RU", classic_items([("Руки Вверх!", title) for title in RUKI]), "sunset", "РУКИ ВВЕРХ!", "Постоянная подборка хитов группы."),
        playlist("aura-calm-night", "AURA · Спокойный вечер", "MOOD", "AURA editorial", "world", classic_items([
            ("Ludovico Einaudi", "Nuvole Bianche"), ("Ólafur Arnalds", "Near Light"), ("Yiruma", "River Flows in You"),
            ("Max Richter", "On the Nature of Daylight"), ("The Cinematic Orchestra", "To Build a Home"),
            ("Agnes Obel", "Riverside"), ("Cigarettes After Sex", "Nothing's Gonna Hurt You Baby"),
        ]), "calm", "AURA MOOD", "Мягкая подборка для спокойного вечера."),
    ]
    return {"version": 2, "generatedAt": datetime.now(timezone.utc).isoformat(), "playlists": playlists}


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path, default=Path("app/src/main/assets/playlists/world_playlists.json"))
    parser.add_argument("--source-dir", type=Path, default=Path("tools/playlists/sources"))
    args = parser.parse_args()
    existing = json.loads(args.output.read_text(encoding="utf-8")) if args.output.exists() else {}
    payload = json.dumps(build(existing, args.source_dir), ensure_ascii=False, indent=2) + "\n"
    args.output.parent.mkdir(parents=True, exist_ok=True)
    temporary = args.output.with_name(args.output.name + ".tmp")
    temporary.write_text(payload, encoding="utf-8")
    temporary.replace(args.output)
    print(f"wrote {args.output}: {len(payload)} bytes, {len(payload and json.loads(payload)['playlists'])} playlists")


if __name__ == "__main__":
    main()
