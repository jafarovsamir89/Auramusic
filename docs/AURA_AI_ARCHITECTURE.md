# AURA local assistant architecture

AURA's production path is local and deterministic. It does not require a
remote model, an API key, a server, or token billing.

## Runtime path

1. `OfflineSpeechRecognizer` captures one bounded utterance. Android's
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
6. `AuraSpeechSynthesizer` selects a local Silero pack when verified and falls
   back to Android TTS when it is unavailable.

Room is durable storage. A Flow is only a notification channel and is never the
only copy of a voice command.

## Privacy boundaries

Only bounded confirmed facts, dialogue state, aggregate response statistics,
and normalized unknown utterances are stored. Raw microphone audio is not
stored. User memory, learning, history, and the music database are separate
controls.
