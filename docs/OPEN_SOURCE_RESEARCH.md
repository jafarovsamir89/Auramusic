# Open-source architecture research

Research date: 2026-07-31. Only official repositories/documentation were reviewed. No source code from these projects was copied into AURA.

## NewPipeExtractor

Repository: https://github.com/TeamNewPipe/NewPipeExtractor

License: GPL-3.0-or-later, as stated by the project README and repository license.

Useful architectural ideas:

- The extraction engine is a library independent from the NewPipe application UI.
- `StreamingService` provides a service-level abstraction.
- Search and stream extraction use separate extractor types.
- Network access is injected through a `Downloader` boundary.
- Link handling, localization, pagination, metadata collectors, and service-specific parsing are separated.
- Restricted states such as CAPTCHA, geographic restriction, paid content, login confirmation, and age restriction are typed rather than treated as generic parse errors.
- Parser/stream behavior has service-specific fixture and live tests.

Decision for AURA: do not add NewPipeExtractor as a dependency and do not copy implementation code without an explicit GPL licensing decision for AURA. Reuse only the general ideas of an injected HTTP boundary, typed failures, and isolated search/stream components.

## SimpMusic

Repository: https://github.com/maxrave-dev/SimpMusic

License: GPL-3.0.

Useful architectural ideas:

- The project declares clean architecture and MVVM for its UI application.
- Current modules separate `common`, `data`, `domain`, YouTube Music scraping, and platform-specific media layers.
- Android/desktop application shells consume shared domain/data modules instead of embedding extraction directly in UI.
- Playback errors are considered an expected integration condition and are handled with retry-oriented behavior.
- Metadata matching uses string and duration information, which aligns with AURA's identity resolver design.

Decision for AURA: no code, fixtures, parsers, or internal API constants will be copied. The high-level module separation and testing approach inform AURA's independently written Plugin Core.

## Spotube

Repository: https://github.com/KRTirtho/spotube

Documentation: https://docs.spotube.cc/

License: BSD-4-Clause.

Useful architectural ideas:

- Metadata providers and audio-source resolution are distinct concepts.
- Plugins are installable/replaceable rather than hard-wired into player UI.
- Playback is local even when metadata/source selection is plugin-driven.
- Source matches are persisted separately from canonical track metadata.
- Database, metadata plugin, player, local tracks, history, and sourced-track services are separated.

Decision for AURA: the permissive license still requires notices and an advertising acknowledgement if code is reused. AURA currently copies no Spotube code. The metadata-versus-audio-source separation is adopted only as an architectural concept through `UnifiedTrack.alternatives` and `PlayableSource`.

## Research boundaries

- Repository licenses govern software code, not rights to music or platform content.
- Publicly observable request structures are not treated as permission to bypass DRM, paid access, CAPTCHA, account security, geographic restrictions, or age gates.
- No third-party app will be embedded wholesale.
- Any future code reuse requires a per-file provenance entry and license review before the code enters the repository.

