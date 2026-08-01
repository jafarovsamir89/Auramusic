# Stage 4 progress — cross-provider fallback

Date: 2026-08-01

## Implemented behind the debug Plugin Core route

- `UnifiedTrackSession` keeps one canonical queue/MediaSession ID for provider alternatives of the same recording.
- Resolve order is now: selected candidate, remaining alternatives of the same `UnifiedTrack`, then the next canonical search result.
- This supports both `YouTube -> Zaycev` and `Zaycev -> YouTube` without provider logic in Compose UI.
- When a provider alternative resolves, its actual `sourceId`, stream URL, headers, and source page are retained while the canonical track ID remains stable.
- A playback error records the current position, repeats resolution, and can replace the failed MediaItem at the same queue index.
- Position is transferred only when the canonical ID is unchanged and duration differs by no more than 10 seconds.
- Live/remix/cover/acoustic/extended variants stay in separate identity groups and cannot receive the old position.
- The user sees a short source-change message; technical plugin errors remain in diagnostics.
- Diagnostics now expose the selected provider separately from the active orchestration route.
- `PlaybackSourceRegistry` is bounded to the 64 most recent temporary URIs, limiting in-memory retention of old stream headers.

## Tests

The standard suite now contains 42 tests: 41 pass and the explicit YouTube live test is skipped by default.

New coverage includes:

- YouTube first, Zaycev second for one canonical recording;
- reverse Zaycev-to-YouTube ordering;
- canonical ID preservation when the actual provider changes;
- separation of live and studio recordings;
- fallback to M4A when preferred Opus validation fails;
- eviction of old temporary playback headers.

## Pending physical gate

- Trigger a YouTube stream failure during playback and confirm Zaycev resumes near the previous position.
- Repeat the reverse route.
- Confirm the four-item MediaSession queue and system Next remain stable.
- Tune a short audible fade. `PlaybackCoordinator` exposes a fade duration, but the current `PlaybackConnection` replacement is immediate.

The stable default route is still unchanged. Cross-provider fallback requires both debug extras `aura_plugin_core=true` and `aura_youtube_plugin=true`.
