# AURA local companion direction

The companion is intentionally a fast illusion of intelligence rather than a
text generator. A growing dialogue catalog provides branches, variants,
clarifications and short continuations. SQLite/Room supplies durable state;
the matcher supplies language, topic, expected reply, emotion and negation
signals.

The catalog is versioned and imported idempotently. Broken links or unknown
intent names fail the package before it is written. Updating the catalog does
not delete user memory, learned mappings, playback history, playlists, or
queue data.

Recognition and music search remain separate: ordinary conversation is never
silently converted to a music search. Missing tracks and playlists produce a
real not-found result rather than a false confirmation.

## Current limitations

- Android's system recognizer may use an installed system language service even
  when `EXTRA_PREFER_OFFLINE` is set. A fully guaranteed offline wake-word
  detector still requires a separate local detector integration.
- The first Whisper and Silero downloads are explicit model/voice-pack setup
  work, not hidden first-query work. Until a pack is verified, AURA stays
  silent rather than using robotic Android TTS.
- Actual latency, RAM and model sizes must be measured on the target Helio G96
  device; repository constants are not a benchmark.
