# Independent YouTube Music Plugin plan

The YouTube plugin will be independently implemented inside AURA. It will not embed NewPipe, SimpMusic, Spotube, or another complete client.

## Product boundary

The plugin may search public music results and resolve audio streams available to an ordinary user client. It must stop with a typed restriction when content requires CAPTCHA, age verification, paid subscription, DRM, login/security bypass, or an unavailable region. It will not download catalogs or proxy media through an AURA server.

## Package structure

```text
data/plugins/youtube/
  YouTubeMusicPlugin.kt
  YouTubeSearchClient.kt
  YouTubeSearchParser.kt
  YouTubePlayerClient.kt
  YouTubeStreamResolver.kt
  YouTubeSignatureResolver.kt
  YouTubeModels.kt
  YouTubeRequestContext.kt
  YouTubeCookieStore.kt
  YouTubeSelectors.kt
  YouTubeHealthCheck.kt
```

## Responsibilities

- `YouTubeMusicPlugin`: maps Plugin Core calls to the search/player components and exposes capabilities.
- `YouTubeRequestContext`: locale, country, visitor/client context, user agent, and request headers without UI or account state.
- `YouTubeSearchClient`: performs bounded search/trending/related requests through an injected OkHttp boundary.
- `YouTubeSearchParser`: converts songs, videos, albums, artists, and playlists into typed models; only playable track-like items become `TrackCandidate`.
- `YouTubePlayerClient`: requests playback metadata for a selected `videoId` and classifies restrictions.
- `YouTubeStreamResolver`: keeps audio-only formats, ranks supported formats, validates the selected URI, and returns `PlayableSource`.
- `YouTubeSignatureResolver`: isolates version-sensitive player transformation logic. It never handles DRM or authentication bypass and must fail closed when the transformation is unsupported.
- `YouTubeCookieStore`: optional, ephemeral, and off by default for the anonymous MVP. Never exposes values to diagnostics.
- `YouTubeHealthCheck`: lightweight search/player probe with cooldown; it does not continuously poll.

## MVP vertical slice

1. Anonymous search for `artist + title`.
2. Parse song/video candidates with `videoId`, title, artist/channel, album, artwork, duration, explicit flag, and popularity/views when present.
3. Apply CandidateRankerV2 penalties for live, cover, remix, slowed, reverb, karaoke, instrumental, reaction, lyric video, short, teaser, and interview unless requested.
4. Resolve non-restricted audio formats.
5. Prefer compatible Opus/WebM, then M4A/MP4 audio, then another Media3-compatible audio stream.
6. Capture MIME, bitrate, content length when available, URL expiry, and non-sensitive headers.
7. Validate with a small Range request.
8. Return the source to the existing PlaybackService through Music Brain/PlaybackCoordinator.
9. Build related queue without mixing provider details into UI.
10. Verify `Linkin Park — Numb` on the physical phone, including background playback and MediaSession metadata.

## Format scoring

Reject video-containing formats for an audio-only request. Score remaining formats by Media3 compatibility, audio codec, bitrate ceiling from user settings, content length, and validation latency. Keep at least one alternative format descriptor for same-result recovery, but never persist expired direct URLs.

## Caching

- Search results: bounded memory cache initially; Room later.
- Player response/format descriptors: short memory cache.
- Direct URI: only until its parsed expiry minus a safety window.
- Player transformation program: version-keyed and bounded; invalidate on failure.
- No full playback URLs, cookies, or tokens in release logs.

## Failure model

Map failures to typed categories:

```text
NETWORK
TIMEOUT
PARSE_CHANGED
NOT_FOUND
NO_AUDIO_FORMAT
SIGNATURE_UNSUPPORTED
ACCESS_RESTRICTED
LOGIN_REQUIRED
AGE_RESTRICTED
PAID_CONTENT
DRM
REGION_BLOCKED
RATE_LIMITED
```

Music Brain decides fallback. The plugin does not silently substitute a different song.

## Tests

Offline unit tests use sanitized fixtures and injected fake clients:

- `YouTubeSearchParserTest`
- `YouTubePlayerParserTest`
- `YouTubeStreamResolverTest`
- restriction classification tests
- expiry/header redaction tests

Internet-dependent tests use a separate Gradle task and are disabled by default:

- live search;
- live stream resolve;
- Range validation;
- physical Media3 playback;
- YouTube to Zaycev fallback.

## Rollout gates

1. Plugin Core contract tests pass.
2. Zaycev passes unchanged through ProviderManager/Music Brain.
3. Offline YouTube fixture tests pass.
4. Live search passes without cookies/account.
5. Live resolve returns a validated, non-DRM audio source.
6. Existing ExoPlayer plays it on the physical phone.
7. Forced YouTube failure resumes through Zaycev near the old position when identity/duration checks permit.
8. Only then enable YouTube by default.

## Known risks

- Public/internal response shapes and player transformation rules can change without notice.
- Region, consent, rate limits, and client context can change availability.
- A direct stream may expire during queue prefetch.
- Platform terms and distribution constraints require a separate product/legal review even when no DRM or authentication is bypassed.
- The safest operational design is a removable plugin with health scoring and immediate Zaycev/local fallback.

