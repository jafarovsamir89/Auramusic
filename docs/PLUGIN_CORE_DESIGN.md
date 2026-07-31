# Plugin Core design

This document is the design gate before implementation. Names may be adjusted during compilation, but responsibilities and dependency direction are fixed.

## Package layout

```text
data/plugins/core/
  MusicPlugin.kt
  PluginModels.kt
  ProviderManager.kt
  ProviderPolicy.kt
  ProviderHealthStore.kt

data/search/
  CandidateRankerV2.kt
  TrackIdentityResolver.kt
  UnifiedTrack.kt

domain/music/
  MusicBrain.kt
  MusicBrainModels.kt
  RecommendationEngine.kt
  PlaybackCoordinator.kt
```

Provider implementations stay isolated:

```text
data/plugins/local/
data/plugins/youtube/
data/plugins/zaycev/
data/plugins/radio/
```

## Core contract

```kotlin
interface MusicPlugin {
    val id: String
    val displayName: String
    val capabilities: Set<PluginCapability>
    val priority: Int

    suspend fun search(request: MusicSearchRequest): PluginResult<List<TrackCandidate>>
    suspend fun resolve(candidate: TrackCandidate): PluginResult<PlayableSource>
    suspend fun getTrending(context: MusicContext): PluginResult<List<TrackCandidate>>
    suspend fun getRelated(track: Track): PluginResult<List<TrackCandidate>>
    suspend fun healthCheck(): PluginHealth
}
```

Unsupported optional operations return a typed `UNSUPPORTED` failure; they do not throw.

`PluginCapability` contains `SEARCH`, `STREAM`, `TRENDING`, `RELATED`, `ARTIST`, `ALBUM`, `PLAYLIST`, `RADIO`, `LOCAL`, and `LYRICS`.

## ProviderManager responsibilities

- Own an immutable registry of plugins and a mutable enabled-state map.
- Filter by requested capability before making network calls.
- Give local search the first short window, then run only relevant online plugins concurrently.
- Apply a per-plugin timeout and structured coroutine cancellation.
- Preserve partial success when another provider fails.
- Record latency, success/failure, last error category, and cooldown.
- Use a circuit breaker for repeatedly failing plugins.
- Return candidates plus diagnostics; never return raw cookies, tokens, or full temporary URLs.
- Deduplicate only after collecting results so alternatives remain attached to one identity.

Initial timeouts:

```text
local search: 500 ms
online search: 6 s per plugin
resolve: 10 s per attempt
health check: 4 s
```

These are policy values, not UI constants.

## Track identity and alternatives

```kotlin
data class TrackIdentity(
    val normalizedTitle: String,
    val normalizedArtist: String,
    val durationBucketSeconds: Int?,
    val album: String? = null,
    val isrc: String? = null,
    val musicBrainzId: String? = null
)

data class UnifiedTrack(
    val identity: TrackIdentity,
    val metadata: Track,
    val alternatives: List<TrackCandidate>,
    val score: Double
)
```

`TrackIdentityResolver` first uses ISRC/MusicBrainz IDs when present, then normalized artist/title plus a duration tolerance. It must not merge explicit variants such as live, remix, cover, acoustic, extended, slowed, reverb, or instrumental unless the request explicitly asks for that variant.

## CandidateRankerV2

The ranker is pure and deterministic. Inputs include the request, candidate, plugin health snapshot, and optional local preference/history snapshot. It returns a score plus human-readable debug reasons.

Initial positive weights follow the product specification:

```text
title similarity       0.32
artist similarity      0.26
duration similarity    0.10
source quality         0.09
source reliability     0.08
exact music match      0.08
artwork                0.02
user preference        0.03
playback history       0.02
```

Variant penalties are applied only when not requested. An official/artist channel hint is a positive signal but never a sole proof of identity.

## MusicBrain boundary

```kotlin
class MusicBrain(
    private val providerManager: ProviderManager,
    private val candidateRanker: CandidateRankerV2,
    private val identityResolver: TrackIdentityResolver,
    private val recommendationEngine: RecommendationEngine,
    private val playbackCoordinator: PlaybackCoordinator
)
```

Music Brain owns orchestration decisions: search, collect, rank, select, resolve, build queue, fallback, and recovery. It receives no `Context`, `WebView`, OkHttp type, MediaController, or Compose state.

`PlaybackCoordinator` is an interface implemented by an adapter over the existing `PlaybackConnection`. It accepts resolved `Track` objects and exposes position/queue state. It never creates a player.

## Safe migration of Zaycev

1. Freeze the current Zaycev tests and add contract tests using a fake plugin.
2. Add Plugin Core types without changing `AuraViewModel` wiring.
3. Wrap the existing `MusicProvider` with a temporary `LegacyMusicPluginAdapter`; do not copy Zaycev logic.
4. Add `ProviderManager` tests for timeout, partial success, disablement, ordering, and cancellation.
5. Add `TrackIdentityResolver` and CandidateRankerV2 tests.
6. Put Music Brain behind a debug feature flag and compare its result with the current engine.
7. Move Zaycev files from `data/providers/zaycev` to `data/plugins/zaycev` only after package-level behavior is covered. A package move is optional and must not be mixed with logic changes.
8. Make `ZaycevProvider` implement `MusicPlugin` directly, preserving its HTTP-first/retry/WebView sequence.
9. Switch `AuraViewModel` to Music Brain only after the physical Zaycev scenario passes through the adapter path.
10. Remove the legacy interface/adapter only when Zaycev, local, radio, and YouTube all use the new contract.

## Rollback strategy

The baseline commit remains runnable. During migration, the current engine stays available behind a debug flag. If ProviderManager or Music Brain fails the physical vertical test, restore old wiring without reverting the plugin types or tests.

