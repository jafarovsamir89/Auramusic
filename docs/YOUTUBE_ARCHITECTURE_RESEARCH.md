# YouTube architecture research

Research date: 2026-08-01. The review was performed against source code, not screenshots or secondary articles.

| Project | Reviewed revision | Search | Stream resolution | Queue | Cache and playback |
|---|---|---|---|---|---|
| NewPipe / NewPipeExtractor | `10144cbf` / `5928eca7` | Search is owned by the extractor boundary and returns service-neutral info items. | Player responses are fetched from several Innertube clients. The extractor validates the returned video ID and playability, deciphers `signatureCipher`, transforms the `n` throttling parameter, appends `cpn` and optional PO tokens, and discards unusable formats. | `PlayQueue` owns stable stream identities and emits queue events. `MediaSourceManager` resolves the current item and a small neighbour window lazily and replaces expired sources. | Media source expiry is separate from queue identity. Cache keys are based on service/video/format properties and deliberately exclude transient signed URLs. YouTube-specific data sources use URL range parameters where appropriate. |
| InnerTune | `bfba5ecb` | A dedicated Innertube module sends typed YouTube Music search requests and maps responses into app models. | The Media3 `ResolvingDataSource` receives a stable media ID, fetches a player response only on a cache miss, selects an audio format, and substitutes the transient URL at `open()`. | Queue implementations expose initial state plus continuation-based `nextPage()`. The Media3 timeline contains stable `MediaItem`s and can be persisted without signed URLs. | Download cache and bounded playback cache are layered. Both are keyed by media ID; playback reads fixed-size chunks and ignores cache errors. |
| ViMusic | `6e83b8b8` | Innertube requests and response parsing are isolated from UI and playback. | A `ResolvingDataSource` maps `videoId` to a fresh player URL on demand. It verifies that the player response video ID matches the request and keeps only a tiny URL ring buffer. | Queue entries are normal Media3 items whose URI and custom cache key are the stable YouTube ID; persisted queue restores those IDs, not signed URLs. Radio appends more items close to the queue edge. | `SimpleCache` is bounded by an LRU policy. Reads are chunked and cache hits bypass stream resolution. |
| Harmony Music | `99d750fd` | YouTube Music search is a service with explicit filters and response parsing. | Stream extraction is isolated behind `StreamProvider`. The audio handler resolves by song ID immediately before playback, checks URL expiry, and explicitly requests a new URL after a playback error such as 403. | `audio_service` owns a metadata-only queue. Playback sources are built only for the selected item and the session queue is persisted independently. | Downloaded files are preferred, then full-song local cache, then a still-valid URL cache, then network extraction. URL expiry is checked with a safety margin. |

## Architecture adopted for AURA

1. Search results, queue entries and persisted state contain canonical YouTube video IDs and metadata only.
2. YouTube extraction is an isolated, AURA-owned provider boundary. It uses explicit Innertube client profiles and strict response validation. AURA prefers clients returning direct audio URLs and fails closed instead of importing another application's extractor or guessing unsupported cipher transformations.
3. Playback uses a synthetic stable URI and custom cache key derived from the video ID. A Media3 resolving data source exchanges that identity for a short-lived stream URL only when bytes are not already cached.
4. Stream URLs have a small bounded in-memory cache with an expiry safety margin. HTTP 403 invalidates the cached URL and retries the same stable queue item once with a fresh extraction.
5. Media bytes use a bounded `SimpleCache` with an LRU evictor and `FLAG_IGNORE_CACHE_ON_ERROR`. Signed URLs are never database keys and are never persisted.
6. The queue remains owned by Media3. Related/radio pagination can append stable items without pre-resolving every stream.

## Deliberately not copied

No source code or extractor dependency from the reviewed GPL projects is included in AURA. Their architectural patterns informed the boundaries and lifecycle, while AURA's request, parsing, queue, cache and retry implementation is independent.
