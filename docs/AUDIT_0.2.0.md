# AURA Music 0.2.0 — baseline audit

Date: 2026-07-31

Checkpoint: `1fd5e59 checkpoint: AURA Music 0.2.0 working baseline`

## Scope and result

The existing project was inspected without replacing the current search, playback, voice, or UI stacks. The baseline builds successfully with Java 17, Kotlin 2.1.20, AGP 8.9.2, compile/target SDK 35 and min SDK 26.

The generated Gradle wrapper pins Gradle 8.11.1, so a checkout no longer depends on the ignored local `.codex-tools` directory.

## Current modules and ownership

- `MainActivity` owns permissions, edge-to-edge setup, the debug-only command extra, and the Compose root.
- `AuraApp` owns navigation and presentation for Home, Search, Library, Assistant, Player, Queue, Car Mode, and Diagnostics.
- `AuraViewModel` currently acts as both presentation state holder and orchestration layer.
- `LocalIntentEngine` parses all text locally; `OfflineSpeechRecognizer` wraps Android `SpeechRecognizer`.
- `UnifiedSearchEngine` owns in-memory search/source caches and delegates to `MusicProvider` implementations.
- `ZaycevProvider` owns Zaycev search and resolve while keeping the hidden WebView as the last, short-lived fallback.
- `PlaybackConnection` is the app-side `MediaController` boundary.
- `PlaybackService` is the only `MediaSessionService` and creates the only `ExoPlayer`.
- `PlaybackSourceRegistry` supplies per-URI request headers to Media3 through `ResolvingDataSource`.
- `LocalMusicProvider` reads playable audio from `MediaStore`.
- `RadioBrowserProvider` can query public stations but is not connected to the current UI/orchestrator.

## Confirmed strengths

- Concrete-track Zaycev search, ranking, resolve, Range validation, short-lived URL cache, and playback are implemented.
- Source-specific headers are separated from UI and forwarded by the existing player.
- No visible browser exists in the normal product flow.
- The hidden WebView is created only after HTTP resolve/retry failures and destroyed on completion, timeout, or cancellation.
- MediaSession metadata, queue transitions, background playback, seeking, shuffle, and repeat share one player.
- Voice intent parsing is local and does not call a paid AI API.
- The UI contains no fake catalog tracks; the single `DemoCatalog` entry is a non-playable empty-state placeholder.

## Automated verification

Command:

```bash
./gradlew testDebugUnitTest --rerun-tasks assembleDebug
```

Result: build successful, 9 tests passed, 0 failures. The live Zaycev search test also passed during this run.

Compiler warnings: five uses of deprecated non-auto-mirrored Material icons. They do not affect runtime but should be cleaned up during UI polish.

## Physical verification status

The mandatory fresh run passed on Xiaomi `2201117SG`:

- the debug command `Включи Мот Капкан` selected `Капкан — Мот`;
- MediaSession reported `PLAYING`, real metadata, and a four-item queue;
- position advanced from 49,839 ms to 52,838 ms while AURA was in the background;
- the system Next command moved to queue item 1, `Малая — Мот`;
- artwork was visible in the native UI;
- logcat contained no AURA fatal exception or ANR.

Playback was paused after verification and the application task was brought back to the foreground.

## Gaps relative to AURA 2.0

1. `UnifiedSearchEngine` calls providers sequentially and returns the first non-empty provider result.
2. Only `ZaycevProvider` is registered in the search engine.
3. Local and radio implementations do not implement the common provider/plugin contract.
4. Deduplication across sources does not exist.
5. Ranking has no negative variant penalties, provider health, user preference, or history factors.
6. Fallback is candidate-oriented inside one Zaycev result set, not track-identity-oriented across plugins.
7. `AuraViewModel` contains intent routing, search, resolution, fallback, queue preparation, persistence, and UI state; it is the main coupling point to reduce safely.
8. Favorites/history remain in SharedPreferences. Queue and position are not persisted.
9. Radio is implemented but not presented as a first-class explicit intent/plugin.
10. The old `MusicProviders` URL catalog is unused legacy code. It must not return to the UI; remove it only after replacement tests cover all consumers.
11. `PlaybackSourceRegistry` has no expiry/eviction and should be bounded when multiple short-lived providers exist.
12. Current source caches are memory-only and keyed too narrowly for multi-provider use.
13. Diagnostics model is Zaycev-shaped rather than Music Brain/provider agnostic.

## Regression-sensitive areas

- Do not create another ExoPlayer or PlaybackService.
- Preserve `Track.id` consistency between candidates, queue items, MediaItems, and fallback lookup.
- Preserve Zaycev `Referer`, `User-Agent`, and cookie forwarding.
- Do not persist short-lived playback URLs as durable track identity.
- Do not put provider selection into Compose screens.
- Do not move hidden WebView creation into a long-lived singleton.
- Any fallback that resumes position must verify track identity and compatible duration first.

## Release artifact

`app/build/outputs/apk/debug/app-debug.apk`
