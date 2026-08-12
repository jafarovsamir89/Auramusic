# AURA Music — Android prototype

AURA is a native Android personal music assistant. Online catalog search and playback use YouTube; local files and internet radio remain separate device features rather than fallback music providers.

## Local Assistant Boundary

AURA keeps the existing deterministic/local assistant as an offline fallback and adds an optional Gemini Live Smart Voice mode. When configured, `gemini-3.1-flash-live-preview` is the online conversational brain and returns native audio directly; no Whisper → text → cloud → TTS chain is used in that mode. A cloud connection requires explicit user configuration and internet access:

```text
voice or text
  -> Gemini Live persistent WebSocket (online Smart Voice)
  -> native Gemini AUDIO + AURA function tools
  -> Music Brain / PlaybackService / Android API

offline voice/text
  -> existing deterministic local intent and dialogue library
  -> Music Brain / PlaybackService / Android API
  -> local Silero TTS or Android system TTS
```

Commands, dialogue branches, memory, and reply variants work without a network. Russian, Azerbaijani, and English are supported by the local dialogue layer. Durable user facts and recent messages are stored in bounded Room records; complete transcripts are not retained.

Music catalog search, YouTube playback, and Radio Browser station catalogs are network features. Local files, playlists, favorites, queue, history, and assistant memory remain usable without them. Android's system `SpeechRecognizer` only receives `EXTRA_PREFER_OFFLINE`; that flag is a preference and does not guarantee that the vendor recognizer stays off the network. Install the bundled Whisper resource for a fully local speech path.

## Gemini Smart Voice (debug)

For a local debug build, add `GEMINI_API_KEY=...` to the ignored `local.properties` file. The key is never committed. Without it, AURA keeps the offline voice fallback. Smart Voice uses a persistent WSS session, 16 kHz mono PCM input, 24 kHz mono PCM native output, automatic transcription, synchronous music function calling, session resumption and context-window compression. Production should replace the debug key provider with a backend-issued ephemeral token provider.

## Voice Resources

Downloads are explicit and visible in the debug Diagnostics storage screen. No speech request starts a download.

| Resource | Exact size | Languages | Install behavior |
| --- | ---: | --- | --- |
| Whisper `ggml-base-q5_1.bin` | 59,707,625 bytes | RU/AZ/EN | Manual install, SHA-256 verified |
| Silero Kseniya `v1_kseniya_16000.jit` | 142,264,026 bytes | RU | Manual install, SHA-256 verified |
| Android system `az-AZ` voice | Device-provided | AZ | Runtime fallback; no pseudo-AZ Silero model |

Each download uses a `.part` file, validates HTTP size and SHA-256, atomically moves the model first, then creates an atomic `.sha256` sidecar. Cancelled or failed downloads remove only the partial resource. On restart, stale `IN_PROGRESS` assistant commands are requeued up to three attempts for UI work or failed for background work.

## Playback flow

```text
text or voice request
  -> MusicBrain / YouTubeMusicPlugin
  -> canonical videoId + metadata
  -> Media3 queue with aura-youtube:// identity
  -> ResolvingDataSource at open()
  -> AURA multi-client stream resolver
  -> format-aware cache key (videoId + itag)
  -> ExoPlayer / MediaSessionService
```

Signed `googlevideo` URLs are not stored in the queue or Room. They are resolved immediately before playback, retained only in a small expiring memory cache, invalidated on HTTP 403, and resolved again once. HTTP 429 and transient 5xx failures use a bounded three-attempt backoff. Media bytes are cached independently under `youtube:<videoId>:<itag>`.

## Implemented

- Kotlin, Jetpack Compose and Android 8.0+.
- YouTube search, related items and audio-only playback.
- AURA-owned Innertube player client with isolated client profiles, typed failures, strict video-ID validation and direct audio-format selection.
- Media3/ExoPlayer background playback, MediaSession queue and system controls.
- 128 MiB LRU playback cache with URL-independent, format-aware keys.
- Room persistence for metadata, favorites, history, queue state and preferences; transient stream URLs are excluded.
- Local Room playlists with prominent creation, add-any-track picker, rename, delete, ordered items, queue import, shuffle and playback.
- Queue 2.0 with play-next/append actions, drag reordering, Media3 synchronization, three repeat modes, controllable auto-continue and restorable Room history.
- AURA Visual System 2.1: reference-led compact cinematic UI, violet/magenta energy accents, central AURA character and 48 dp interaction targets.
- Free Radio Browser catalog by country with secure station streams, retry states, playback and playlist persistence.
- Local-device music, voice intents and automotive UI.
- AURA assistant core: local intent recognition, dialogue branches, compact memory and spoken replies.
- Optional local Silero voice pack for Russian with pinned size/SHA-256 metadata. Azerbaijani uses the best available real system `az-AZ` voice until a properly trained local pack is validated; normal replies never start a hidden download.

The source review and the architecture derived from NewPipe, InnerTune, ViMusic and Harmony Music are recorded in [`docs/YOUTUBE_ARCHITECTURE_RESEARCH.md`](docs/YOUTUBE_ARCHITECTURE_RESEARCH.md).

The visual tokens and interaction principles are recorded in [`docs/AURA_DESIGN_SYSTEM.md`](docs/AURA_DESIGN_SYSTEM.md).

## Start on a clean computer

Everything required from the repository is committed, including the Gradle Wrapper and Room schema. Generated build outputs, IDE state and the machine-specific Android SDK path are intentionally excluded.

Install:

1. Android Studio with Android SDK Platform 35 and Build Tools.
2. JDK 17 (Android Studio's bundled JDK is suitable).
3. Git.

Clone and build on Windows:

```powershell
git clone https://github.com/jafarovsamir89/Auramusic.git
cd Auramusic
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --no-daemon --console=plain
```

On macOS or Linux, use `./gradlew` instead. Android Studio creates `local.properties` automatically; for command-line builds, set `ANDROID_HOME` or create `local.properties` containing `sdk.dir=<absolute Android SDK path>`.

The debug APK is generated at `app/build/outputs/apk/debug/app-debug.apk`.

Before public distribution, review YouTube platform/content terms and the dependency inventory in [`docs/THIRD_PARTY_NOTICES.md`](docs/THIRD_PARTY_NOTICES.md).
