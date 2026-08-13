# Full MusicBrainz artist index measurement

Snapshot: `20260812-001001` (official `artist.tar.xz`)

| Metric | Result |
|---|---:|
| Compressed source dump | 1,705,259,836 bytes (1.59 GiB) |
| Parsed artists | 2,957,002 |
| Retained artists | 2,956,941 |
| Aliases | 488,747 |
| Full SQLite with FTS5 | 801,337,344 bytes (765 MiB) |
| Full SQLite ZIP/gzip-9 estimate | ~381 MB (363 MiB) |
| SQLite without FTS5 | 681,218,048 bytes (650 MiB) |
| Compact SQLite (only fields used by resolver) | 485,847,040 bytes (463 MiB) |
| Compact SQLite gzip-9 | 263,817,107 bytes (252 MiB) |
| Exact indexed lookup median/p95 | 0.008 / 0.012 ms (1,000 lookups) |

Conclusion: the complete MusicBrainz artist index is too large for a normal APK. Even the compact compressed version is above the project's 100–150 MB budget and would also increase install size and update cost. Keep the small seed index in the APK for offline fallback; ship the complete index later as an optional downloadable asset/asset pack, or build a region/language subset after product requirements are fixed.

The raw dump and generated full databases are local-only files under `tools/musicbrainz/cache/` and are ignored by Git.
