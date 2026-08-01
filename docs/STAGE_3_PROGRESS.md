# Stage 3 progress — isolated YouTube Music plugin

Date: 2026-08-01

## Implemented vertical slice

- Independent package `data/plugins/youtube`; no third-party extractor or application code was copied.
- Anonymous public HTML search with desktop/mobile `ytInitialData` decoding.
- Typed video/music result parsing into `TrackCandidate`.
- Anonymous Android player request with no API key, account cookies, or OAuth token.
- Typed restriction classification for login, age, paid content, DRM, region, CAPTCHA/bot/rate limit, parse changes, and missing audio formats.
- Audio-only format parsing with Opus/WebM preference, then M4A/MP4.
- Direct and already-signed HTTPS URL support; unknown signature transformations fail closed.
- Range validation before a URI becomes `PlayableSource`.
- MIME, bitrate, content length, duration, metadata, expiry, artwork, popularity, channel, and source-page handling.
- Related results through the bounded anonymous `next` request.
- Trending MVP through a localized top-songs search.
- Bounded LRU/TTL caches for search, player descriptors, and related results. No durable direct URL or cookie persistence.
- `YouTubeCookieStore` is off by default and accepts no account/security cookies.
- Provider capability, priority, health check, friendly failures, and diagnostics integration.

## Rollout state

The plugin is registered in `ProviderManager` but disabled by default. It cannot be enabled by a release launch. A debug build requires both opt-ins:

```bash
adb shell am start -n az.simplesoft.aura/.MainActivity \
  --ez aura_plugin_core true \
  --ez aura_youtube_plugin true \
  --es aura_command "Включи Linkin Park Numb"
```

Normal and release launches continue using the stable Zaycev route.

## Verification

The normal suite contains 39 tests: 38 pass and the network-dependent YouTube test is skipped unless explicitly enabled. Android lint passes with 0 errors and 19 non-blocking recommendations. The debug APK builds successfully.

```bash
./gradlew lintDebug testDebugUnitTest --rerun-tasks assembleDebug
AURA_YOUTUBE_LIVE_TEST=1 ./gradlew testDebugUnitTest \
  --tests 'az.simplesoft.aura.data.plugins.youtube.YouTubeLiveIntegrationTest' \
  --rerun-tasks
```

The explicit live test passed for `Linkin Park Numb` and verified:

- anonymous search;
- current player-response parsing;
- audio-only format selection;
- HTTPS Range validation;
- related result parsing.

The live test found and drove fixes for two real response differences: mobile search can encode `ytInitialData` as a hex-escaped JavaScript string, and manually setting `Accept-Encoding` disables OkHttp's transparent gzip decompression.

## Deliberate restrictions

- No CAPTCHA, bot challenge, login, age, paid-content, region, or DRM bypass.
- No account cookie import, credential storage, bulk download, catalog mirroring, proxy server, or durable stream URL storage.
- No JavaScript signature decipher implementation yet. Current live Android player responses provide direct URLs; if that changes, resolve returns `SIGNATURE_UNSUPPORTED` and Music Brain can fall back.
- YouTube remains disabled until physical Media3/background/queue verification passes.

## Remaining Stage 3 work

- Physical playback verification on the real phone.
- Replace the trending MVP query with a typed charts surface if a stable anonymous response is available.
- Add non-playable catalog entities for artist, album, and playlist browse UI; current plugin returns only playable track/video candidates to the common contract.
- Verify distribution and YouTube platform terms before any public release.

## External API note

The official YouTube Data API supports typed video/channel/playlist search but requires a developer key and quota. It does not provide playback audio formats. AURA does not embed a key. The current anonymous player boundary is version-sensitive and intentionally removable.
