# AURA ChatScript Lab

This is a throwaway, debug-only experiment on branch `agent/chatscript-lab`. It
answers one question: can a small, deterministic ChatScript brain provide a
fast local dialogue loop on the physical arm64 Android device before we invest
in production integration?

## Scope

- Official upstream ChatScript source, pinned at `9f5eec4736ba22bd992a6498c1e0052e2a795125`.
- MIT license retained in `third_party/chatscript/license.txt`.
- No calls from `LocalAssistantEngine`, `MusicBrain`, voice capture, TTS, or
  production fallback routing.
- The native library and screen are reachable only from the debug Diagnostics
  screen (`Open ChatScript Lab`). Release builds do not expose the route.
- English uses the upstream prebuilt demo. RU/AZ are small curated lab brains;
  their topic packs are compiled on first use into the app-private runtime.

## Run one command

```bash
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Open AURA → Diagnostics → Open ChatScript Lab. Select English, Русский AURA,
or Azərbaycan AURA. The screen shows response latency and action markers such
as `PLAY_CALM`; reset exercises the same user file again.

## Fast GO / NO-GO matrix

| Check | Result in this branch |
|---|---|
| arm64 JNI + Gradle debug build | GO — `assembleDebug` passes |
| APK installs on the connected arm64 phone | GO — verified with `adb install -r` |
| production assistant routing changed | GO — no integration |
| deterministic English demo | GO — upstream Build1 is bundled |
| RU/AZ UTF-8 scripts and action marker path | Lab verification on device required |
| ≤1 s warm reply target | Measure on the phone; cold startup is intentionally separate |
| ship to production | NO-GO until the device matrix and latency/RAM budget pass |

The Lab is deliberately captured on this branch so the experiment can be
discarded or promoted as a separate decision after physical QA.
