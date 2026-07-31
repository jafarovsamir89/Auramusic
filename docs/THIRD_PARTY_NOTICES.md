# Third-party notices and license inventory

This file is an engineering inventory, not legal advice. Full license texts and final attribution packaging must be verified before a release build is distributed.

## Current direct dependencies

| Component | Purpose | License family | Source |
|---|---|---|---|
| AndroidX Activity/Core/Lifecycle/WebKit | Android platform integration | Apache-2.0 | https://github.com/androidx/androidx |
| Jetpack Compose / Material 3 / Material Icons | Native UI | Apache-2.0 | https://github.com/androidx/androidx |
| AndroidX Media3 | ExoPlayer, MediaSession, service | Apache-2.0 | https://github.com/androidx/media |
| OkHttp | HTTP client | Apache-2.0 | https://github.com/square/okhttp |
| jsoup | HTML parsing | MIT | https://github.com/jhy/jsoup |
| Coil 2 | Artwork loading | Apache-2.0 | https://github.com/coil-kt/coil |
| JSON-java (`org.json`) | JSON parsing | JSON License | https://github.com/stleary/JSON-java |
| JUnit 4 | Unit testing only | EPL-1.0 | https://github.com/junit-team/junit4 |
| MockWebServer | Test HTTP server only | Apache-2.0 | https://github.com/square/okhttp |

Transitive dependencies must be captured from the final release dependency graph before distribution.

## Researched but not included

| Project | License | Current AURA status |
|---|---|---|
| NewPipeExtractor | GPL-3.0-or-later | Architectural research only; no dependency or copied code |
| SimpMusic | GPL-3.0 | Architectural research only; no dependency or copied code |
| Spotube | BSD-4-Clause | Architectural research only; no dependency or copied code |

## Required release work

1. Generate the complete release-runtime dependency inventory.
2. Bundle required license texts and notices.
3. Verify JSON-java terms against the exact resolved artifact.
4. Record any future copied/adapted source file with repository URL, commit hash, source path, modifications, and license.
5. Do not claim that an open-source software license grants permission to redistribute or download music.

