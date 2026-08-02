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
| Silero `v5_cis_base_nostress` / `aze_gamat` | Local Azerbaijani text-to-speech | Downloaded on first Azerbaijani voice response; SHA-256 pinned in the app | MIT for CIS Base models | https://github.com/snakers4/silero-models |

The Silero model is not bundled into the base APK. AURA downloads the official 91,695,221-byte artifact over HTTPS, verifies SHA-256 `d7d361caf78b8480bcd65a0c367af665a2bf6f06c8507306e3781dc7c6ce781b`, and stores it in private application storage.

Transitive dependencies must be captured from the final release dependency graph before distribution.

## External data services

| Service | Purpose | Data terms | Source |
|---|---|---|---|
| Radio Browser | Country/station directory and click counting | Collected station data is dedicated to the public domain; the service is free for use in free and non-free apps. Individual station streams remain subject to their broadcasters' terms and regional availability. | https://www.radio-browser.info/ |
| OpenRouter | Optional gateway for AURA AI chat completions | Usage, model pricing, retention and provider routing are governed by the user's OpenRouter account and current service terms. | https://openrouter.ai/docs |
| DeepSeek models | Optional multilingual conversation and structured action planning through OpenRouter | Model access and output are governed by the selected OpenRouter model/provider terms. | https://openrouter.ai/models |

AURA calls the public API directly with a descriptive User-Agent, discovers distributed servers through `all.api.radio-browser.info`, reports station clicks, retries across hosts, and does not copy Radio Browser server source code.

AURA does not contain an OpenRouter credential in source control. The optional development credential is read from ignored `local.properties` or an environment variable. A permanent provider key must not be shipped in a public APK.

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
