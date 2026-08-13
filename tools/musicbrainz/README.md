# AURA MusicBrainz artist index

This directory contains the reproducible **offline preprocessing** step. The Android app does not call MusicBrainz and does not ship the raw dump. Only the generated `aura_artists.db` is copied into app-private storage on first use.

```bash
python3 tools/musicbrainz/build_artist_index.py download \
  --output tools/musicbrainz/cache/artist.tar.xz
python3 tools/musicbrainz/build_artist_index.py build \
  --input tools/musicbrainz/cache/artist.tar.xz \
  --output app/src/main/assets/aura_artists.db \
  --snapshot 2026-08-13
```

The builder keeps musical artist entities, canonical fields and aliases, creates indexed normalized/folded columns, and attempts an FTS5 table. Its report includes parsed/retained artists, aliases, duplicate removal, database bytes, checksum and build time. It never extracts raw JSON to disk.

Benchmark generated files without loading the artist table into memory:

```bash
python3 tools/musicbrainz/benchmark_artist_index.py app/src/main/assets/aura_artists.db
```

The committed app asset is a small seed database for development and offline smoke tests. Generate the full index locally from the latest official artist dump when the APK/AAB size budget is approved. Do not commit the dump or generated files under `tools/musicbrainz/cache/`.
