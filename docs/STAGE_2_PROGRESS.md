# Stage 2 progress — Plugin Core foundation

## Implemented without switching production wiring

- `MusicPlugin` contract and capability model.
- Typed `PluginResult`, failure categories, health status, policy, and provider statistics.
- `ProviderManager` with capability filtering, enable/disable state, parallel search, per-plugin timeout/cancellation, partial success, deterministic ordering, and resolve routing.
- `LegacyMusicPluginAdapter` for the unchanged `MusicProvider` contract.
- `CandidateRankerV2` with the specified positive factors and penalties for unwanted variants.
- `TrackIdentityResolver` and `UnifiedTrack` alternatives across sources.
- `MusicBrain`, outcome models, `PlaybackCoordinator` boundary, and recommendation boundary.
- `LocalMusicPlugin` adapter with local search and highest initial priority.
- `RadioMusicPlugin` with explicit `RADIO` search isolation, so radio stations cannot enter a normal concrete-track query.
- `SearchMediaKind` defaults to `TRACK`; all current call sites keep their behavior.

## Tests

The suite now contains 23 tests with 0 failures:

- original 9 Zaycev/parser/matcher/validation tests;
- ProviderManager parallel partial-success, timeout/cancellation, and disabled-plugin tests;
- CandidateRankerV2 official/original, requested remix, and duration tests;
- TrackIdentityResolver cross-provider merge, variant separation, and duration tests;
- MusicBrain ranking/deduplication and failure tests;
- legacy adapter preservation test;
- local-versus-radio capability routing tests.

The tests found and prevented a real adapter bug where a receiver track ID was accidentally emitted as `providerId`.

## Verification

- The new APK was installed on the physical Xiaomi `2201117SG`.
- The unchanged production Zaycev route played `Капкан — Мот`, exposed a four-item MediaSession queue, and produced no FATAL/ANR entries.
- Resume position in Music Brain is retained only when the replacement resolves to the same track identity and compatible duration; a live/remix/other recording restarts at zero.
- `lintDebug`, `testDebugUnitTest`, and `assembleDebug` pass together: 0 lint errors, 19 non-blocking recommendations, and 23 passing tests.
- The remaining lint recommendations concern dependency updates, portrait orientation, the missing launcher icon, and optional KTX style changes.

## Deliberately not done yet

- `AuraViewModel` still uses the proven `UnifiedSearchEngine(listOf(zaycevProvider))` path.
- Music Brain does not yet control production playback.
- No second player or service was created.
- Zaycev code was not moved or rewritten.
- YouTube implementation has not started.
- Room, Vosk, TTS, and Bluetooth activation have not started.

## Next gate

Add a `PlaybackCoordinator` adapter around the existing `PlaybackConnection`, run Zaycev through `LegacyMusicPluginAdapter -> ProviderManager -> MusicBrain` behind a debug-only switch, preserve four-item queue preparation, and repeat the physical search/background/next checks before changing the normal route.
