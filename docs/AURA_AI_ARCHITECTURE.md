# AURA AI Core 0.3

## Goal

AURA is an assistant that can operate music, not a search box with voice input. A phrase can be conversation, a local action, or a request that needs language-model reasoning. Only an explicit music intention may create a music search.

## Deep module

The external seam is `AuraAiEngine.respond(input, context) -> AssistantReply`. UI code does not know whether a result came from the local router, compact memory, or DeepSeek. `AssistantReply` contains a human response and one allow-listed `MusicIntent` for the existing executor.

```text
AuraViewModel
    |
    v
AuraAiEngine
    +-- LocalIntentEngine (private, fast, deterministic)
    +-- CompactAssistantMemory (Room user_preferences adapter)
    +-- OpenRouterAssistantAdapter (optional reasoning adapter)
    |
    v
validated MusicIntent
    |
    +-- Music Brain / YouTube
    +-- PlaybackService / Media3
    +-- playlists / queue / radio
    +-- Android AudioManager
```

The language model never invokes Android or playback code directly. It returns JSON which is parsed into the existing sealed intent model. Unknown action names are discarded as conversation.

## Routing invariant

- Explicit commands such as `Поставь Руки Вверх`, `Növbəti mahnı`, and `pause` are handled locally.
- Greetings and common small talk are answered locally without network cost.
- Ambiguous conversation is sent to DeepSeek only when an OpenRouter key is configured.
- A missing key or network failure produces a conversation fallback. It never produces `MusicIntent.Search`.
- The dedicated Search screen still performs a direct catalog search.

## Compact memory

Memory is stored under one `user_preferences` entry, so no Room migration is required.

- maximum encoded size: 16,000 characters;
- maximum durable facts: 64;
- maximum recent messages: 10;
- maximum message length: 320 characters;
- facts are deduplicated by `category:key`;
- complete transcripts, secrets and transient small talk are not durable facts;
- malformed memory safely resets to an empty snapshot.

## Languages and speech

The first supported languages are Russian (`ru-RU`), Azerbaijani (`az-AZ`) and English (`en-US`). Android 14+ speech recognition requests automatic switching among those languages when supported by the installed recognizer. Earlier Android versions use the device language. Replies use the installed Android TTS voice closest to the detected language.

## OpenRouter

The development adapter uses `POST https://openrouter.ai/api/v1/chat/completions`, Bearer authentication, app-attribution headers and JSON response format. The model is configurable and defaults to the current DeepSeek V4 Flash alias. `provider.require_parameters` ensures the chosen endpoint supports requested structured output parameters.

Production builds must not ship a permanent OpenRouter key. Use an authenticated server-side token broker with quotas before public release.
