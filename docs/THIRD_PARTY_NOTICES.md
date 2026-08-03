# Third-party notices and license inventory

This file is an engineering inventory, not legal advice. Full license texts and final attribution packaging must be verified before a release build is distributed.

## Current direct dependencies

| Component | Purpose | License family | Source |
|---|---|---|---|
| AndroidX Activity/Core/Lifecycle/WebKit | Android platform integration | Apache-2.0 | https://github.com/androidx/androidx |
| Jetpack Compose / Material 3 / Material Icons | Native UI | Apache-2.0 | https://github.com/androidx/androidx |
| AndroidX Media3 | ExoPlayer, MediaSession, service | Apache-2.0 | https://github.com/androidx/media |
| OkHttp | HTTP client | Apache-2.0 | https://github.com/square/okhttp |
| Coil 2 | Artwork loading | Apache-2.0 | https://github.com/coil-kt/coil |
| JSON-java (`org.json`) | JSON parsing | JSON License | https://github.com/stleary/JSON-java |
| JUnit 4 | Unit testing only | EPL-1.0 | https://github.com/junit-team/junit4 |
| MockWebServer | Test HTTP server only | Apache-2.0 | https://github.com/square/okhttp |
| PyTorch Android 2.1 | TorchScript runtime for the optional local Silero voice pack | BSD-style PyTorch license | https://github.com/pytorch/pytorch |

## Optional voice models

| Model | Purpose | Distribution | License | Source |
|---|---|---|---|---|
| Silero `v1_kseniya_16000` | Optional local Russian text-to-speech | Explicit HTTPS install; SHA-256 pinned in the app | MIT | https://github.com/snakers4/silero-models |
| Silero `v5_cis_base_nostress` | Optional local Azerbaijani text-to-speech | Explicit HTTPS install; SHA-256 pinned in the app | MIT for CIS Base models | https://github.com/snakers4/silero-models |

The Silero models are not bundled into the base APK. AURA installs official artifacts only after an explicit user action, verifies the exact size and SHA-256, writes a sidecar checksum, and publishes the model through an atomic rename into private application storage.

| Artifact | Bytes | SHA-256 | URL |
|---|---:|---|---|
| `v1_kseniya_16000.jit` | 142264026 | `3d5359561e10dc27e9fe031197872f35857085fcfd1d1e36aaa624b01b3aa74f` | https://models.silero.ai/models/tts/ru/v1_kseniya_16000.jit |
| `v5_cis_base_nostress.jit` | 91695221 | `d7d361caf78b8480bcd65a0c367af665a2bf6f06c8507306e3781dc7c6ce781b` | https://models.silero.ai/models/tts/ru/v5_cis_base_nostress.jit |

Whisper `ggml-base-q5_1.bin` is also optional and is never downloaded by recognition implicitly. Its exact metadata is kept in `OfflineModelManager`; the same explicit-install, temporary-file, size, checksum, cancellation, and retry rules apply.

Transitive dependencies must be captured from the final release dependency graph before distribution.

## External data services

| Service | Purpose | Data terms | Source |
|---|---|---|---|
| Radio Browser | Country/station directory and click counting | Collected station data is dedicated to the public domain; the service is free for use in free and non-free apps. Individual station streams remain subject to their broadcasters' terms and regional availability. | https://www.radio-browser.info/ |

AURA calls the public API directly with a descriptive User-Agent, discovers distributed servers through `all.api.radio-browser.info`, reports station clicks, retries across hosts, and does not copy Radio Browser server source code.


## Architectural references

| Project | License | Current AURA status |
|---|---|---|
| NewPipe / NewPipeExtractor | GPL-3.0-or-later | Source-level architecture reference only; no dependency or source copied |
| InnerTune | GPL-3.0 | Source-level architecture reference; no source copied |
| ViMusic | GPL-3.0 | Source-level architecture reference; no source copied |
| Harmony Music | GPL-3.0 | Source-level architecture reference; no source copied |

## Required release work

1. Generate the complete release-runtime dependency inventory.
2. Bundle required license texts and notices.
3. Verify JSON-java terms against the exact resolved artifact.
4. Record any future copied/adapted source file with repository URL, commit hash, source path, modifications, and license.
5. Do not claim that an open-source software license grants permission to redistribute or download music.

## YouTube integration status

AURA's YouTube request, parsing and playback implementation is independently written. The reviewed applications and NewPipeExtractor are not dependencies and no reviewed source was copied. Platform terms, branding, content rights, and distribution constraints still require a product/legal review before public release.
