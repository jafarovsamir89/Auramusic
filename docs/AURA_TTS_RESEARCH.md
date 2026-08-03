# AURA voice packs

The voice layer has one selection seam and two local implementations:

- Russian Silero `v5_cis_base_nostress.jit`, configured at about 91.7 MB.
- Azerbaijani Silero pack, configured by its verified download metadata.
- Android system TTS fallback for missing, corrupt, unsupported, or failed
  packs.

The app must show these as optional local resources, with size, language,
version, license and verification status. It must not silently download a pack
on the first spoken answer. Downloads use a partial file and checksum before
the final filename is made visible.

TTS must stop the previous utterance before starting a new one, release its
`AudioTrack`, and request assistant audio focus without stopping music. A
future pack manager should expose install, cancel, verify and delete actions
independently for each language.
