#!/usr/bin/env python3
"""Build a compact metadata-only playlist snapshot for AURA.

The script intentionally stores artist/title/rank and source evidence only.
It never downloads audio, extracts streams, or stores cookies.
"""

from __future__ import annotations

import argparse
import json
import subprocess
import time
import urllib.parse
import urllib.request
import urllib.error
from datetime import datetime, timezone
from pathlib import Path
from typing import Optional


def fetch_json(url: str) -> dict:
    request = urllib.request.Request(url, headers={"User-Agent": "AURA-PlaylistBuilder/1.0 (metadata only)"})
    last_error: Optional[Exception] = None
    for attempt in range(3):
        try:
            # macOS system Python can intermittently fail TLS negotiation; curl uses the
            # system trust store and is already available on the build machine.
            result = subprocess.run(
                ["curl", "--fail", "--silent", "--show-error", "--location", "--max-time", "30", "-A", "AURA-PlaylistBuilder/1.0 (metadata only)", url],
                check=True,
                capture_output=True,
                text=True,
            )
            return json.loads(result.stdout)
        except (OSError, subprocess.SubprocessError, urllib.error.HTTPError, json.JSONDecodeError) as error:
            last_error = error
            time.sleep(1.5 * (attempt + 1))
    raise RuntimeError(f"failed to fetch {url}: {last_error}")


def first_release_year(recording_mbid: str, cache: dict[str, int | None]) -> int | None:
    if not recording_mbid:
        return None
    if recording_mbid in cache:
        return cache[recording_mbid]
    url = f"https://musicbrainz.org/ws/2/recording/{urllib.parse.quote(recording_mbid)}?inc=releases&fmt=json"
    try:
        payload = fetch_json(url)
        value = str(payload.get("first-release-date", ""))[:4]
        year = int(value) if len(value) == 4 else None
    except (OSError, RuntimeError, ValueError, KeyError):
        year = None
    cache[recording_mbid] = year
    # MusicBrainz asks clients to stay around one request per second.
    time.sleep(1.05)
    return year


def listenbrainz_top(limit: int, range_name: str = "this_week") -> list[dict]:
    url = "https://api.listenbrainz.org/1/stats/sitewide/recordings?range=" + range_name + "&count=" + str(min(limit, 1000))
    payload = fetch_json(url)
    rows = payload.get("payload", {}).get("recordings", [])
    result = []
    for rank, row in enumerate(rows, 1):
        title = str(row.get("track_name", "")).strip()
        artist = str(row.get("artist_name", "")).strip()
        if title and artist:
            result.append({
                "rank": rank,
                "artist": artist,
                "title": title,
                "musicBrainzId": row.get("recording_mbid"),
                "sourceScore": row.get("listen_count"),
            })
    return result


def youtube_most_popular(api_key: str, region: str, limit: int) -> list[dict]:
    params = urllib.parse.urlencode({
        "part": "snippet,statistics",
        "chart": "mostPopular",
        "regionCode": region,
        "videoCategoryId": "10",
        "maxResults": min(limit, 50),
        "key": api_key,
    })
    payload = fetch_json("https://www.googleapis.com/youtube/v3/videos?" + params)
    result = []
    for rank, row in enumerate(payload.get("items", []), 1):
        snippet = row.get("snippet", {})
        title = str(snippet.get("title", "")).strip()
        artist = str(snippet.get("channelTitle", "")).strip()
        if title and artist:
            result.append({
                "rank": rank,
                "artist": artist,
                "title": title,
                "sourceUrl": "https://www.youtube.com/watch?v=" + str(row.get("id", "")),
                "sourceScore": int(row.get("statistics", {}).get("viewCount", 0) or 0),
            })
    return result


def playlist(identifier: str, title: str, kind: str, source: str, region: str, items: list[dict]) -> dict:
    return {
        "id": identifier,
        "title": title,
        "description": "Автоматически обновляемая metadata-only подборка AURA.",
        "region": region,
        "language": "multi",
        "kind": kind,
        "source": source,
        "updatedAt": datetime.now(timezone.utc).date().isoformat(),
        "items": items,
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path, default=Path("app/src/main/assets/playlists/world_playlists.json"))
    parser.add_argument("--limit", type=int, default=50)
    parser.add_argument("--all-time-limit", type=int, default=250)
    parser.add_argument("--enrich-years", action="store_true", help="Query MusicBrainz release years (rate limited).")
    parser.add_argument("--cache", type=Path, default=Path("tools/playlists/cache/recording_years.json"))
    parser.add_argument("--youtube-key", default="")
    args = parser.parse_args()

    top = listenbrainz_top(args.limit)
    all_time = listenbrainz_top(args.all_time_limit, "all_time") if args.enrich_years else []
    year_cache: dict[str, int | None] = {}
    if args.cache.exists():
        try:
            year_cache = json.loads(args.cache.read_text())
        except json.JSONDecodeError:
            year_cache = {}
    if args.enrich_years:
        for item in top:
            item["year"] = first_release_year(item.get("musicBrainzId", ""), year_cache)
        for item in all_time:
            item["year"] = first_release_year(item.get("musicBrainzId", ""), year_cache)
        args.cache.parent.mkdir(parents=True, exist_ok=True)
        args.cache.write_text(json.dumps(year_cache, ensure_ascii=False, indent=2) + "\n")
    decade = [item for item in all_time if item.get("year") is not None and 1990 <= item["year"] <= 1999]
    recent_year = datetime.now(timezone.utc).year
    new_releases = [item for item in top if item.get("year") in {recent_year, recent_year - 1}]
    playlists = [
        # ListenBrainz exposes a current-week aggregate; do not label it as a daily chart.
        playlist("top-today-world", "Топ сегодня · Мир", "TOP_TODAY", "youtube-most-popular", "world", []),
        playlist("top-week-world", "Топ недели · Мир", "TOP_WEEK", "listenbrainz", "world", top),
    ]
    if args.youtube_key:
        az = youtube_most_popular(args.youtube_key, "AZ", min(args.limit, 50))
        playlists.append(playlist("top-today-az", "Топ сегодня · Азербайджан", "REGIONAL", "youtube-most-popular", "AZ", az))
    else:
        playlists.append(playlist("top-today-az", "Топ сегодня · Азербайджан", "REGIONAL", "youtube-most-popular", "AZ", []))

    # These catalogs are deliberately empty until a source with release-year data is configured.
    # Never invent a decade or new-release ranking from a popularity-only response.
    playlists.extend([
        playlist("new-this-week", "Новинки этой недели", "NEW_RELEASES", "musicbrainz-plus-charts", "world", new_releases),
        playlist("hits-90s", "Хиты 90-х", "DECADE", "musicbrainz-plus-charts", "world", decade),
        playlist("aura-calm-night", "AURA · Спокойный вечер", "MOOD", "aura", "world", []),
    ])
    args.output.parent.mkdir(parents=True, exist_ok=True)
    snapshot = json.dumps({"version": 1, "generatedAt": datetime.now(timezone.utc).isoformat(), "playlists": playlists}, ensure_ascii=False, indent=2) + "\n"
    temporary = args.output.with_name(args.output.name + ".tmp")
    temporary.write_text(snapshot)
    temporary.replace(args.output)
    print(f"wrote {args.output} ({len(top)} ListenBrainz items)")
    time.sleep(0)


if __name__ == "__main__":
    main()
