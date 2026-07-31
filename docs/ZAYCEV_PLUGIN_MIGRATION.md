# Zaycev to Plugin Core migration

The migration must preserve all existing Zaycev behavior and keep each change reversible.

## Frozen behavior

- Public search API, then HTML search fallback when empty.
- Optional artist search to build a useful queue.
- Existing normalization/transliteration confidence behavior until CandidateRankerV2 equivalence is proven.
- Track-page enrichment.
- File metadata to streaming token to temporary play URL.
- Exact Referer/User-Agent/cookie forwarding.
- Range validation.
- Direct HTTP resolve, refresh/retry, then short-lived hidden WebView fallback.
- Restricted responses do not trigger bypass behavior.
- Temporary URL expiry and cache validation.

## Incremental steps

1. Add Plugin Core models and fake-plugin tests without wiring the app to them.
2. Create an adapter delegating `search`, `resolve`, and `validate` to the unchanged `ZaycevProvider`.
3. Derive capabilities as `SEARCH`, `STREAM`, and `RELATED` only where behavior is actually implemented.
4. ProviderManager initially contains only the Zaycev adapter; compare ordered candidates and chosen source with the legacy engine.
5. Route a debug-only command through Music Brain while keeping the normal user path unchanged.
6. Run all nine existing tests plus new contract tests.
7. Run the exact physical query, queue, background, next, and failure-recovery checks.
8. Switch normal search to Music Brain only after parity.
9. Add local and radio adapters/plugins.
10. Move/rename the Zaycev package only as a separate mechanical commit, if still useful.

## Compatibility rules

- Preserve generated ID `zaycev:<source-id>` until durable TrackIdentity migration exists.
- Preserve all `PlayableSource` headers and expiry values.
- Never store the temporary URL in durable identity/history tables.
- Preserve current friendly user errors while expanding debug diagnostics.
- Do not run Zaycev and YouTube when an exact local result already satisfies the request unless queue enrichment is requested.

## Acceptance test

The migration is accepted only when the current nine tests pass and the physical Zaycev scenario behaves identically through Music Brain, including MediaSession metadata, four-item queue preparation, background playback, next, and stream-error recovery.

