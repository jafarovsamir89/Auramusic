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

The suite now contains 24 tests with 0 failures:

- original 9 Zaycev/parser/matcher/validation tests;
- ProviderManager parallel partial-success, timeout/cancellation, and disabled-plugin tests;
- CandidateRankerV2 official/original, requested remix, and duration tests;
- TrackIdentityResolver cross-provider merge, variant separation, and duration tests;
- MusicBrain ranking/deduplication and failure tests;
- legacy adapter preservation test;
- local-versus-radio capability routing tests.
- a live Plugin Core vertical search test through the Zaycev compatibility adapter.

The tests found and prevented a real adapter bug where a receiver track ID was accidentally emitted as `providerId`.

## Verification

- The new APK was installed on the physical Xiaomi `2201117SG`.
- The unchanged production Zaycev route played `Капкан — Мот`, exposed a four-item MediaSession queue, and produced no FATAL/ANR entries.
- Resume position in Music Brain is retained only when the replacement resolves to the same track identity and compatible duration; a live/remix/other recording restarts at zero.
- `lintDebug`, `testDebugUnitTest`, and `assembleDebug` pass together: 0 lint errors and 24 passing tests.
- The remaining lint recommendations concern dependency updates, portrait orientation, the missing launcher icon, and optional KTX style changes.

## Debug integration route

The original `UnifiedSearchEngine(listOf(zaycevProvider))` remains the default route. A debug APK can opt into the new route without changing saved user state:

```bash
adb shell am force-stop az.simplesoft.aura
adb shell am start -n az.simplesoft.aura/.MainActivity \
  --ez aura_plugin_core true \
  --es aura_command "Включи Мот Капкан"
```

In this mode:

- concrete-track search runs through `MusicBrain -> ProviderManager`;
- local music and the compatibility-wrapped Zaycev provider are searched in parallel;
- radio is excluded unless `SearchMediaKind.RADIO` is explicit;
- resolve runs through `ProviderManager`;
- the existing ViewModel queue preparation and the only `PlaybackConnection -> PlaybackService -> ExoPlayer` path remain unchanged;
- the debug diagnostics screen identifies the active route.

`PlaybackConnectionCoordinator` now implements the Music Brain playback boundary, including position-aware replacement, without creating another player or service.

## Deliberately not done yet

- Normal launches still use the proven `UnifiedSearchEngine(listOf(zaycevProvider))` path.
- Music Brain does not yet control the default production route.
- No second player or service was created.
- Zaycev code was not moved or rewritten.
- YouTube implementation has not started.
- Room, Vosk, TTS, and Bluetooth activation have not started.

## Next gate

Install the debug APK on the physical phone, run the opt-in command above, verify a four-item queue plus background/Next behavior, and only then consider making Plugin Core the normal route.
