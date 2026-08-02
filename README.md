# AURA Music — Android prototype

AURA is a native Android personal music assistant. Online catalog search and playback use YouTube; local files and internet radio remain separate device features rather than fallback music providers.

## AURA AI

AURA AI uses a hybrid architecture:

```text
voice or text
  -> local multilingual intent router
  -> local action OR OpenRouter / DeepSeek reasoning
  -> validated structured action
  -> Music Brain / PlaybackService / Android API
  -> concise reply + optional Android TTS
```

Greetings, questions and ordinary conversation never fall back to music search. Russian, Azerbaijani and English are supported by the intent layer, conversation context and TTS. Durable user facts and only ten recent messages are kept in a bounded Room-backed JSON record (maximum 16,000 characters); complete transcripts are not retained.

DeepSeek is optional. Without a key, local music/control commands and common conversation continue to work. For a private development build, add these untracked lines to `local.properties`:

```properties
OPENROUTER_API_KEY=your_key_here
OPENROUTER_MODEL=~deepseek/deepseek-v4-flash-latest
```

Never commit a real key. A key embedded in a client APK can be extracted, so public distribution will require a small authenticated token broker rather than a permanent provider key in the app.

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
- AURA AI Core 0.3: non-search conversation routing, structured DeepSeek actions through OpenRouter, compact bounded memory, multilingual dialogue history and spoken replies.

The source review and the architecture derived from NewPipe, InnerTune, ViMusic and Harmony Music are recorded in [`docs/YOUTUBE_ARCHITECTURE_RESEARCH.md`](docs/YOUTUBE_ARCHITECTURE_RESEARCH.md).

The product direction, priorities and explicit non-goals for AURA 2.0 are recorded in [`docs/AURA_V2_DIRECTION.md`](docs/AURA_V2_DIRECTION.md).

The visual tokens and interaction principles are recorded in [`docs/AURA_DESIGN_SYSTEM.md`](docs/AURA_DESIGN_SYSTEM.md).

The assistant seam, memory limits and action-safety rules are recorded in [`docs/AURA_AI_ARCHITECTURE.md`](docs/AURA_AI_ARCHITECTURE.md).

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
