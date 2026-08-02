# AURA AI Core 0.4

## Goal

AURA is an assistant that can operate music, not a search box with voice input. When configured, DeepSeek plans every utterance as conversation or one allow-listed application action. Only an explicit music intention may create a music search. Local parsing is an offline/error fallback and never outranks the online agent.

## Deep module

The external seam is `AuraAiEngine.respond(input, context) -> AssistantReply`. UI code does not know whether a result came from the local router, compact memory, or DeepSeek. `AssistantReply` contains a human response and one allow-listed `MusicIntent` for the existing executor.

```text
AuraViewModel
    |
    v
AuraAiEngine
    +-- OpenRouterAssistantAdapter (primary decision adapter)
    +-- CompactAssistantMemory (Room user_preferences adapter)
    +-- LocalIntentEngine (private offline fallback)
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

- With an OpenRouter key, greetings, player commands, music requests and follow-ups all go to DeepSeek first.
- DeepSeek returns exactly one JSON plan containing a spoken reply, language, allow-listed action and at most three durable memory facts.
- Courtesy words such as `пожалуйста`, `zəhmət olmasa`, and `please` cannot become an executable query on their own.
- A missing key or network failure activates `LocalIntentEngine` for simple player commands. Unknown conversation never produces `MusicIntent.Search`.
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

The first supported languages are Russian (`ru-RU`), Azerbaijani (`az-AZ`) and English (`en-US`). Android 14+ speech recognition requests automatic switching among those languages when supported by the installed recognizer. Earlier Android versions use the device language. Azerbaijani replies prefer the local Silero V5 CIS Base `aze_gamat` voice. Its verified voice pack is downloaded once; Android system TTS is the fallback. Russian and English select the highest-quality matching Android voice available on the phone.

## OpenRouter

The development adapter uses `POST https://openrouter.ai/api/v1/chat/completions`, Bearer authentication, app-attribution headers and JSON response format. The model is configurable and defaults to the current DeepSeek V4 Flash alias. `provider.require_parameters` ensures the chosen endpoint supports requested structured output parameters.

Production builds must not ship a permanent OpenRouter key. Use an authenticated server-side token broker with quotas before public release.
