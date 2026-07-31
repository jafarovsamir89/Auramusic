# Current playback flow

## Search-to-audio sequence

```text
Voice/Text
  -> LocalIntentEngine
  -> AuraViewModel.submit
  -> MusicSearchRequest
  -> UnifiedSearchEngine.search
  -> ZaycevProvider.search
  -> TrackMatcher.rank
  -> AuraViewModel.resolveAndPlay
  -> UnifiedSearchEngine.resolve
  -> ZaycevProvider.resolve
  -> HTTP resolver / retry / hidden WebView fallback
  -> PlayableSource
  -> Track with stream URL and request headers
  -> PlaybackConnection.play
  -> MediaController.setMediaItems + prepare + play
  -> PlaybackService
  -> ResolvingDataSource adds per-URI headers
  -> ExoPlayer
  -> MediaSession / notification / Bluetooth / lock screen
```

## Search and candidate lifecycle

1. `LocalIntentEngine` turns a command into `MusicIntent.Search`.
2. `AuraViewModel` creates `MusicSearchRequest`, updates UI state, and starts a coroutine.
3. `UnifiedSearchEngine` checks its two-hour memory cache.
4. `ZaycevProvider` calls the public search flow, with HTML parsing only when the public JSON result is empty.
5. `TrackMatcher` normalizes, transliterates, tolerates minor spelling differences, and sets `confidence`.
6. A result at or above `0.72` can auto-play; otherwise the user selects a candidate.
7. `AuraViewModel` retains the candidate by generated track ID so the displayed result can later be resolved.

## Resolve lifecycle

1. `UnifiedSearchEngine` checks the short-lived source cache and validates a cache hit.
2. `ZaycevProvider` enriches metadata from the public track page when possible.
3. `ZaycevPlaybackResolver` obtains a streaming token, obtains the temporary URL, builds required headers, and validates the source with a Range request.
4. On a direct failure, Zaycev retries after refreshing the page state.
5. A non-restricted failure may use a hidden, ten-second WebView resolver. The WebView is destroyed after success, error, timeout, or cancellation.
6. A successful `PlayableSource` becomes a `Track`; its temporary URL is not used as track identity.

## Player ownership

- `PlaybackService` is the sole owner of `ExoPlayer` and `MediaSession`.
- `PlaybackConnection` talks to the service through `MediaController`; it does not instantiate a player.
- `PlaybackSourceRegistry` maps exact playback URI strings to request headers.
- `ResolvingDataSource` adds those headers at Media3 request time.
- Each `Track` becomes one `MediaItem`, retaining title, artist, artwork, and stable ID.

## Queue and prefetch

- The selected result starts a queue derived from the current result set.
- Only already resolved tracks are initially playable MediaItems.
- The next three candidates are resolved in the background and appended to the same MediaSession queue.
- MediaItem transitions are reported back to `AuraViewModel`, which updates current index and history.

## Error recovery today

1. A Media3 error reports the current MediaItem ID.
2. The first failure re-resolves that candidate to refresh the temporary URL.
3. A repeated failure chooses another untried candidate from the latest Zaycev result set.
4. Resolve itself tries up to four ordered candidates.
5. Technical details are kept in debug diagnostics; user text stays short.

## Required extension point for 2.0

`PlaybackCoordinator` must wrap this existing controller boundary. It may add resume-position validation, fades, and session persistence, but it must not own a second player. Music Brain will request playback through the coordinator and remain unaware of Media3 implementation details.

