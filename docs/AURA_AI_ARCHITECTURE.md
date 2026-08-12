# AURA local assistant architecture

AURA has two explicit paths. Offline mode remains local and deterministic. When
debug Smart Voice is configured, Gemini Live is the conversational brain and
returns native audio over a persistent WebSocket. The online path never routes
through Whisper or Android/Silero TTS.

## Runtime path

1. Local fallback uses `OfflineSpeechRecognizer` for one bounded utterance. Android's
   `SpeechRecognizer` is an offline-preferred fallback, not a guaranteed
   always-on wake-word detector.
2. `LocalCommandClassifier` performs normalization, word-boundary matching,
   negation and discussion checks. Confidence >= 0.90 is safe only for the
   small set of transport-safe player commands.
3. `AssistantCommandCoordinator` writes a command envelope to Room before
   execution. Background-safe actions use Media3; UI actions remain pending
   until the Activity claims them.
4. `LocalIntentEngine` handles the existing music intent surface. The
   data-backed companion handles non-music dialogue.
5. `DialogueSeedImporter`, `LocalDialogueMatcher`, `DialogueStateMachine` and
   `ResponseVariantSelector` read the versioned assets under
   `app/src/main/assets/assistant` and persist state/statistics in Room.
6. Gemini Smart Voice uses `GeminiAudioInput` → `GeminiLiveSession` →
   `GeminiAudioOutput` for 16 kHz input and 24 kHz native output. The session
   handles all server content parts, transcriptions, synchronous tool calls,
   interruptions, resumption, compression and diagnostics.
7. `AuraSpeechSynthesizer` selects a local Silero pack when verified and falls
  back to Android TTS when it is unavailable.

## Online Smart Voice boundary

`GeminiAuthProvider` separates debug API-key authentication from the planned
ephemeral-token provider. Gemini sees only compact music context and narrow
function declarations. AURA validates and executes every function through the
existing ViewModel/MusicBrain/Playback code; the model never receives Android
objects, Room DAOs, the player, filesystem or shell access.

Room is durable storage. A Flow is only a notification channel and is never the
only copy of a voice command.

## Privacy boundaries

Only bounded confirmed facts, dialogue state, aggregate response statistics,
and normalized unknown utterances are stored. Raw microphone audio is not
stored. User memory, learning, history, and the music database are separate
controls.
