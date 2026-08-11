# AURA local assistant architecture

AURA's default path is local and deterministic. It does not require a remote
model, an API key, a server, or token billing. AURA 0.6 adds an explicit,
opt-in local reasoning layer: the deterministic engine remains the fast path,
and a downloaded Qwen3 GGUF Brain Pack is consulted only when that engine
cannot resolve a natural-language request.

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
5. `LocalLlmReasoningProvider` is an optional fallback behind the deterministic
   path. It uses the official llama.cpp Android binding in-process (no
   localhost server), the separately installed Qwen3 0.6B Q8_0 GGUF pack, a
   compact six-turn context, `/no_think`, and a strict JSON decision parser.
   The model can propose a supported intent or a short conversation reply; it
   cannot call tools or execute actions.
6. `DialogueSeedImporter`, `LocalDialogueMatcher`, `DialogueStateMachine` and
   `ResponseVariantSelector` read the versioned assets under
   `app/src/main/assets/assistant` and persist state/statistics in Room.
7. `AuraSpeechSynthesizer` selects a local Silero pack when verified and falls
  back to Android TTS when it is unavailable.

## Brain Pack lifecycle

The Diagnostics screen exposes the Brain Pack as a visible resource. Install,
checksum verification, cancellation, deletion, and loading are explicit user
actions. The 0.6B pack is the product default; a separately listed 1.7B Q8_0
pack is benchmark-only and is never downloaded implicitly. If the pack is not
installed, AURA continues to use the deterministic engine without changing
voice input or playback behavior.

Room is durable storage. A Flow is only a notification channel and is never the
only copy of a voice command.

## Privacy boundaries

Only bounded confirmed facts, dialogue state, aggregate response statistics,
and normalized unknown utterances are stored. Raw microphone audio is not
stored. User memory, learning, history, and the music database are separate
controls.
