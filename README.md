# AURA Music — Android prototype

AURA is a native Android music prototype. Online catalog search and playback use YouTube; local files and internet radio remain separate device features rather than fallback music providers.

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
- Local-device music, radio, voice intents and automotive UI.

The source review and the architecture derived from NewPipe, InnerTune, ViMusic and Harmony Music are recorded in [`docs/YOUTUBE_ARCHITECTURE_RESEARCH.md`](docs/YOUTUBE_ARCHITECTURE_RESEARCH.md).

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
