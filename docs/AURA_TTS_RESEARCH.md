# AURA voice packs

The voice layer has one selection seam and explicit capability-based fallback:

- Russian Silero Kseniya `v1_kseniya_16000.jit`, installed explicitly and
  checksum-verified.
- Android system TTS for Azerbaijani `az-AZ` and English. A Russian model is
  never presented as Azerbaijani.

The app must show these as optional local resources, with size, language,
version, license and verification status. It must not silently download a pack
on the first spoken answer. Downloads use a partial file and checksum before
the final filename is made visible.

TTS stops the previous utterance before starting a new one, releases its
`AudioTrack`, and exposes a barge-in stop path. Audio focus/ducking still needs
device-level validation. The pack manager exposes install, verify and delete
for the Russian pack; an AZ pack will only be added after a properly trained
model passes real pronunciation checks.

## AURA 0.6 Voice Lab findings

The official Silero model inventory lists the Russian `v5_5_ru` model with
speakers `baya`, `kseniya`, and `xenia` at 8/24/48 kHz. It also lists the CIS
`v5_cis_base` / `v5_cis_base_nostress` family with the Azerbaijani speaker
`aze_gamat`. These names are visible in the debug Voice Lab as explicit
experimental candidates.

They are deliberately not presented as verified installable packs yet. Before
shipping one, AURA must pin the exact official artifact, size, checksum, and
runtime format, then run physical-device A/B checks for naturalness,
pronunciation, latency, and clipping. Azerbaijani remains on the real Android
`az-AZ` fallback until `aze_gamat` passes those checks; no Russian model is
used as pseudo-Azerbaijani.
