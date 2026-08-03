# AURA measurements

Measurements taken on 2026-08-03 from the connected arm64 device:

- Device model: `2201117SG` (reported by Android; MediaTek Helio G96 class)
- Debug APK: `108,101,849` bytes
- Fresh launch after install: process remained alive, PID `24493`
- Crash buffer after launch: empty
- App PSS after launch: about `346,525 kB`
- Whisper model on device: not installed during this smoke test
- Silero packs on device: not installed during this smoke test

The PSS number is a launch snapshot, not a sustained recognition benchmark.
Recognition, model load, TTS generation, audio focus and wake-word latency still
need a repeatable measurement harness on the target phone. No unmeasured value
is presented as a performance guarantee.
