# AURA Music — полный инженерный аудит

Дата: 15 августа 2026
Ветка: `agent/chatscript-lab`
Последний коммит: `c4a5a31 Improve offline playback and artwork fallback`

> Этот документ фиксирует исходное состояние аудита. После него внесены исправления по всем пунктам, кроме двух явно исключённых задач: постоянного Gemini API key в клиенте и замены текущего YouTube playback протокола. Изменения проверены повторным unit-test/lint/build прогоном.

## 1. Резюме

Проект собирается и проходит текущий набор JVM-тестов и lint. На подключённом Android-телефоне выполнен холодный запуск `az.simplesoft.aura/.MainActivity`: система вернула `Status: ok`, время запуска — около 2,1 с, `FATAL EXCEPTION` в журнале не обнаружен.

Это не означает готовность к production. Главные риски находятся не в компиляции, а в безопасности, жизненном цикле голоса, сетевых каталогах и отсутствии end-to-end тестов. Самый серьёзный дефект — API-ключ Gemini компилируется в APK и передаётся в query-параметре WebSocket. Такой ключ можно извлечь из опубликованного APK, после чего получить неконтролируемые расходы и потерю доступа.

### Приоритеты

| Приоритет | Количество | Что входит |
|---|---:|---|
| P0 — блокирует релиз | 4 | секрет в APK, backup пользовательских данных, нестабильный YouTube playback, ложный foreground voice lifecycle |
| P1 — высокий риск | 10 | фоновые токены/микрофон, race conditions, скачивание, источники, память, permissions, батарея |
| P2 — важно исправить | 13 | UI/state-монолит, качество ASR/TTS, EQ, Room-консистентность, artwork, адаптивность |
| P3 — улучшения | 8 | обновление зависимостей, ABI, иконка, логирование, тестовая инфраструктура |

## 2. Проверки и воспроизводимость

Выполнено:

```text
./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --no-daemon --console=plain
BUILD SUCCESSFUL in 1m 21s
```

Проверено также:

- `git diff --check` — ошибок форматирования нет;
- рабочее дерево перед отчётом чистое;
- `adb devices` — телефон `4XBEG6YHSKZ5AYWC` виден;
- холодный запуск Activity — успешен, crash signature не найден;
- lint завершился успешно, но выдал предупреждения, перечисленные ниже.

Ограничение: без длительного сценария на физическом телефоне нельзя честно подтвердить качество микрофона, TTS, Doze/screen-off, слышимость эквалайзера и стабильность реальных каталогов. Это отдельный этап device QA.

## 3. P0 — блокирующие проблемы

### P0.1. Gemini API key находится в APK

Доказательства: `app/build.gradle.kts:22-40`, `GeminiAuthProvider.kt:11-18`, `GeminiLiveSession.kt:85-92`.

`GEMINI_API_KEY` читается из `local.properties`, встраивается через `BuildConfig` и затем добавляется в URL WebSocket как `?key=...`. Любой пользователь может декомпилировать APK или увидеть URL в прокси/сетевом логе.

Последствия: кража ключа, чужие запросы на вашем биллинге, невозможность ограничить клиента, утечка через crash/network logs.

Исправление: release-клиент не должен содержать постоянный ключ. Нужен backend, который после авторизации выдаёт короткоживущий ephemeral token с ограничением модели и квот; ключ хранится только на сервере. В CI добавить проверку, что release APK не содержит `GEMINI_API_KEY` и `AIza`.

### P0.2. Android Backup может вынести память и персональные данные

`AndroidManifest.xml` разрешает `android:allowBackup="true"`. В Room и preferences хранятся имя пользователя, факты, история, избранное, диалоги/ответы, неизвестные фразы и usage counters (`AssistantMemory.kt`, `CompactAssistantMemory.kt`, `AuraDatabase`).

Последствия: перенос на новый телефон или облачный backup может экспортировать личные данные без понятного пользователю согласия.

Исправление: отключить backup для чувствительного варианта либо добавить `dataExtractionRules`/`fullBackupContent` с исключением Room DB, assistant memory, токенов, кэшей и моделей. Добавить отдельную кнопку «Удалить всю память».

### P0.3. YouTube resolver использует нестабильный неофициальный протокол

`AuraYouTubePlaybackResolver.kt:103-435` имитирует несколько client profiles, вызывает `youtubei/v1/player`, извлекает прямые `googlevideo` audio URLs и кэширует их. Поиск (`YouTubeSearchClient.kt`, `YouTubeSearchParser.kt`) скрапит HTML и зависит от `ytInitialData`.

Это может сломаться после любого изменения YouTube, а также создаёт policy/licensing риск. Прямой audio-only/background playback не равен официальному YouTube player flow.

Исправление: зафиксировать разрешённый способ воспроизведения (официальный YouTube player/deep link либо документированное разрешение владельца каталога), убрать обходные client impersonation из production-пути и иметь graceful fallback.

### P0.4. Foreground notification не гарантирует живой голосовой сеанс

`AuraVoiceForegroundService.kt` держит notification и wake lock, но Gemini session и `GeminiAudioInput` принадлежат `AuraViewModel` (`AuraViewModel.kt:689-762`). При уничтожении Activity/ViewModel сервис может остаться видимым, а WebSocket/микрофон — исчезнуть.

Пользователь получает ощущение, что AURA «активна», хотя ответы уже не придут.

Исправление: перенести ownership voice session в сервис либо ввести явный supervisor/reconnect contract, который проверяет состояние socket/audio и сообщает UI о фактическом состоянии.

## 4. P1 — высокие риски

### P1.1. Wake lock без timeout

`AuraVoiceForegroundService.kt:46` вызывает `wakeLock?.acquire()` без ограничения времени. Lint сообщает `WakelockTimeout`.

Если stop path не отработает, телефон будет постоянно потреблять батарею. Использовать timeout, гарантированный `finally`, foreground-service state machine и тест на аварийное завершение Activity.

### P1.2. Неправильная граница API для microphone FGS

`AuraVoiceForegroundService.kt:41-44` выбирает `FOREGROUND_SERVICE_TYPE_MICROPHONE` уже при `SDK_INT >= Q`, хотя lint отмечает требование API 30. Проверить API 29 на реальном/эмулированном устройстве и заменить guard на корректный уровень API с совместимым fallback.

### P1.3. `sendText()` может оставить UI в THINKING навсегда

В `GeminiLiveSession.kt:156-185` состояние меняется на `MODEL_THINKING`, после чего `send()` тихо ничего не делает, если socket отсутствует. Между проверкой READY и фактической отправкой возможен race.

Метод должен возвращать результат (`sent/rejected/queued`), восстанавливать состояние при отказе и иметь bounded reconnect/timeout.

### P1.4. Usage token accounting не дедуплицирует события

`GeminiLiveSession.kt:328-339` включает `++usageSequence` в fingerprint. Два одинаковых usage payload получают разные fingerprints, поэтому повторное сообщение может быть посчитано второй раз.

Нужны event/message id от сервера, идемпотентная запись и отдельное отображение estimated vs authoritative usage. Сейчас локальный счётчик не является источником биллинговой истины.

### P1.5. Непредсказуемый VAD

`GeminiAudioInput.kt:70-87` использует фиксированный RMS threshold `700` и 400 ms тишины. Нет калибровки под микрофон, максимальной длины фразы, общего timeout и полноценного error callback.

На разных телефонах возможны обрывы, постоянная отправка тишины или слишком раннее завершение. Нужны adaptive threshold, max utterance timeout, тестовые WAV/шумовые профили и метрики false start/false stop.

### P1.6. Wake word «Аура» не реализован как настоящий detector

В основном runtime голос запускается вручную через avatar/button (`AuraApp.kt:253-260`), а активная Gemini-сессия слушает микрофон непрерывно. `VoiceCommandGate` не является wake-word engine.

Требование «работать только после слова Аура» сейчас не обеспечено: это одновременно privacy, battery и token-cost риск. Нужен локальный маломощный wake-word detector или честный plan-B с явным режимом «слушать».

### P1.7. Скачивание не поддерживает YouTube и может зависнуть/заполнить диск

`OfflineTrackStore.kt:14-17,32-42,195-197` намеренно запрещает `youtube` и разрешает только `muzofond`/`vol`. При этом UI/assistant могут обещать «скачать эту песню», даже когда текущий результат YouTube.

У `OkHttpClient()` нет явного call timeout, нет preflight по свободному месту, нет уборки старых `.part` после перезапуска и нет mutex для параллельных download/remove.

Нужно показывать capability до действия, использовать StorageManager allocatable bytes, ограничить размер/время и сериализовать операции.

### P1.8. Поиск допускает race старых результатов

`AuraViewModel` запускает много `viewModelScope.launch`. Между поисками/refresh нет единого отменяемого `searchJob` и generation id. Медленный ответ старого запроса способен перезаписать новый список.

Нужна отмена предыдущего job, request id в state и применение результата только если id актуален; провайдеры должны поддерживать cancellation.

### P1.9. Сетевые клиенты без явных timeout

`ProviderManager.kt` запускает все включённые плагины параллельно, но отдельные клиенты (`MuzofondSearchClient.kt`, `MuzofondCollectionsClient.kt`, `VolSearchClient.kt`, `YouTubeSearchClient.kt`, `PlaybackService.kt`) создают обычный `OkHttpClient()`.

Coroutine timeout не всегда прерывает блокирующий `Call.execute()`. Это может удерживать worker threads и задерживать UI/queue. Ввести общий настроенный client, connect/read/write/call timeout, cancellation bridge и circuit breaker.

### P1.10. Recommendation engine игнорирует выбранный источник

`PersonalRecommendationEngine.kt:52-60,112-120,122-159,203-210` жёстко задаёт `ONLINE_PROVIDER = "youtube"` и фильтрует рекомендации на YouTube. Режим каталога или «оба источника» может получить пустой/неожиданный результат.

Источник должен приходить из `MusicSourceMode`, а UI и диагностика должны показывать реальную provenance track.

### P1.11. Резервные варианты треков теряются после перезапуска

`MusicBrain` держит `alternativesByTrackId` в памяти. `AuraStateRepository` сохраняет треки и очередь, но не сохраняет сущности альтернатив/источников. После restart playback recovery теряет часть fallback-кандидатов.

Сохранять нормализованные source variants в Room или строить их детерминированно при загрузке.

### P1.12. Две независимые системы распознавания команд

`LocalIntentEngine` и `LocalCommandClassifier` могут по-разному интерпретировать одну фразу. В `LocalIntentEngine.matchesAny` (`LocalIntentEngine.kt:387`) используются substring matches, а classifier применяет границы слов.

Это создаёт false positives («назад»/«нравится» внутри другой фразы) и объясняет случаи, когда разговорный запрос превращается в музыкальную команду. Нужен один arbitration layer, confidence/negative examples и таблица приоритетов.

## 5. P2 — важные функциональные и архитектурные проблемы

1. **TTS не соответствует заявленному качеству.** `AuraSpeechSynthesizer.kt:19-42` всегда сообщает `isReady=true`; для AZ/EN offline voice явно не готов, а `profileFor(style)` не используется. Скорость, pitch и style фактически не применяются. Нужны реальная readiness, выбранный engine/voice pack и тесты native audio output.
2. **Азербайджанский offline tokenizer есть, модели нет.** `SileroAzerbaijaniTokenizer.kt` — compatibility-only код. Это должно быть видно в UI, а не выглядеть как поддерживаемый offline AZ TTS.
3. **Android SpeechRecognizer не гарантирует offline privacy.** В `VoiceInput.kt:42-53` fallback идёт в системный recognizer. Нужно показывать пользователю, когда используется cloud/system recognition, и иметь явное разрешение/настройку.
4. **EQ не подтверждён реальным аудиотрактом.** Unit tests для `AuraEqualizerAudioProcessor` и Media3 command не доказывают, что каждый codec/device реально пропускает процессор. Нужны instrumented tests с измеримым sine sweep и экраном active/inactive.
5. **Очередь Media3 может смещать current item.** `PlaybackConnection.kt:81-141` синхронизирует список по индексам, но не гарантирует сохранение current item по стабильному track id во время reorder. `pendingActions` не ограничен, если controller не подключается.
6. **Room persistence тяжёлая и неатомарная на уровне snapshot.** `AuraStateDao.replacePlaybackState` удаляет и заново пишет большие наборы favorites/history/queue; параллельный stale snapshot может затереть изменения. Нет foreign keys/cascade, поэтому возможны orphan rows.
7. **История поиска растёт без нормальной retention policy.** Ограничения применяются к чтению/выборке, но старые строки не удаляются системно.
8. **Reset onboarding не очищает всю память.** `AuraViewModel.resetOnboarding()` сбрасывает prefs/state, но user facts, assistant memory и learned phrases могут остаться. Нужны два явно названных действия: reset profile и delete all data.
9. **Artwork fallback может быть нерелевантным.** Каскад iTunes в `ArtistArtworkLookup.kt` улучшен, но artist-only/title-only fallback не валидирует совпадение результата. Можно получить обложку другого исполнителя с похожим названием; результат нужно оценивать по relevance score и кэшировать.
10. **HTML-каталоги хрупки.** Muzofond, Vol и YouTube parser зависят от DOM/скриптов. Нужны fixture snapshots, contract tests, мониторинг нулевых результатов и отключение источника при schema drift.
11. **Лицензии и ToS не закреплены в runtime.** Для Muzofond/Vol и прямых stream URL есть отдельные исследовательские документы, но код не содержит policy gate/permission metadata. До production нужны письменные разрешения, ограничения кэширования и удалённый kill switch.
12. **Permissions UX слабый.** `MainActivity.kt:33-41` запрашивает notification/media permissions на каждом создании Activity; callback почти не используется. Нет полноценной rationale/denied/permanently-denied ветки и централизованного состояния разрешений.
13. **Voice data и memory retention должны быть документированы точнее.** `CompactAssistantMemory.kt:59-91` хранит до 10 пар пользовательских текстов и ответов по 320 символов. Это bounded, но не «ничего не хранится». Нужны срок хранения, очистка и privacy copy.

## 6. P3 — качество кода, поддерживаемость и релиз

- `AuraApp.kt` — около 3 859 строк, `AuraViewModel.kt` — около 3 251 строки. UI, navigation, playback, assistant, search и persistence сильно связаны. Это главный источник регрессий.
- `ProviderManager` хранит health statistics только в памяти; после restart обучение надёжности источников теряется.
- В `GeminiLiveSession.kt:108-111` логируются первые 500 символов серверных сообщений. Даже без ключа там могут быть transcript/tool payloads. В release нужны redaction и отключение verbose logs.
- В manifest отсутствует `android:icon` (`MissingApplicationIcon`).
- ABI ограничен `arm64-v8a`; нет `x86_64`, поэтому эмулятор/ChromeOS coverage хуже.
- Жёсткий `screenOrientation="portrait"` (`AndroidManifest.xml:34`) ухудшает работу на планшетах и больших экранах.
- Lint предупреждает о `usableSpace` вместо `StorageManager.getAllocatableBytes` в `OfflineModelManager.kt:75` и `VoicePackManager.kt:109`.
- Есть obsolete API checks, `UseKtx`, `AutoboxingStateCreation`, `OldTargetApi` и устаревающие зависимости. Это не блокирует текущую сборку, но увеличивает стоимость обновлений и security review.
- В `AssistantCommandCoordinator.kt:280-323` `BackgroundMusicActionExecutor` не найден среди production call sites; похоже на мёртвый/незавершённый путь.

## 7. Тестовое покрытие: что есть и чего нет

Есть 48 JVM unit tests и 1 migration test. Они полезны для parser/ranker/database/processor базовых сценариев, но не покрывают основные пользовательские риски.

Не хватает:

1. Compose UI tests: onboarding, menu, sources, queue, favorites, download states, back navigation.
2. Instrumented Media3/MediaSession tests: notification next/previous, screen-off, service restart, EQ audible path.
3. Real-device voice matrix: RU/AZ/EN, шум, Bluetooth, lock screen, Doze, permission denial, interruption phone call.
4. Gemini WebSocket tests: reconnect, missing socket, duplicate usage events, malformed server frames, quota/error states.
5. Provider contract tests с сохранёнными HTML fixtures и schema-change alarm.
6. Offline stress tests: disk full, network loss, concurrent download/remove, stale `.part`, process kill during rename.
7. Property/fuzz tests for HTML parser, query normalizer and intent classifier, включая negative phrases.
8. Release security tests: scan APK/AAB for secrets, backup extraction test, cleartext/network policy test.

## 8. Рекомендуемый план исправления

### Фаза A — до следующего публичного APK

1. Убрать постоянный Gemini key из APK; добавить ephemeral-token backend и CI secret scan.
2. Закрыть Android backup для memory/DB/token data и добавить «удалить всю память».
3. Зафиксировать разрешённую YouTube playback architecture и отключить нестабильный путь при ошибке.
4. Переделать ownership voice session и wake-lock lifecycle; добавить bounded timeouts.
5. Исправить API 29/30 FGS guard и permissions state machine.
6. Добавить search cancellation/generation id и общий OkHttp timeout policy.

### Фаза B — функциональная стабильность

1. Объединить intent engines в один classifier с confidence и regression corpus.
2. Согласовать recommendation source mode, queue identity и сохранение alternatives.
3. Сделать честные states `downloadable/not downloadable/downloading/downloaded/error`.
4. Добавить Room transactions/foreign keys/retention и concurrency tests.
5. Довести TTS readiness и RU/AZ/EN voice fallback до явно поддерживаемого UX.
6. Реально проверить EQ на нескольких Android audio paths.

### Фаза C — масштабирование проекта

1. Разделить `AuraApp` и `AuraViewModel` на feature-модули: Assistant, Search, Playback, Library, Settings, Onboarding.
2. Ввести typed domain models вместо stringly-typed source/action ids.
3. Добавить provider contract monitoring и persisted health metrics.
4. Расширить CI до instrumented/device smoke, APK secret scan и baseline performance.
5. Добавить app icon, x86_64 debug ABI и adaptive layouts.

## 9. Итоговая оценка

Текущее состояние: **функциональный прототип, пригодный для внутреннего тестирования на телефоне; не готов к публичному production-релизу**.

Сильные стороны: рабочая Gradle-сборка, базовый набор unit-тестов, модульные provider/playback/assistant компоненты, локальная память, offline store, artwork cascade и уже работающий cold start.

Главные стоп-факторы: секрет в клиенте, незащищённые backup-данные, неофициальная YouTube playback цепочка, несогласованный lifecycle фонового голоса и отсутствие device/integration доказательств. Пока эти пункты не закрыты, дальнейшая полировка карточек и добавление новых команд будет увеличивать поверхность регрессий быстрее, чем качество продукта.
