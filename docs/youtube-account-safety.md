# YouTube и подключение аккаунта в AURA

Дата проверки: 2026-08-12

Документ фиксирует безопасную границу интеграции AURA с YouTube и YouTube Music. Использованы официальные материалы Google/YouTube; отдельные возможности YouTube Music, для которых нет публичной developer-документации, не считаются доступным API-контрактом.

## Короткий вывод

Подключение Google/YouTube-аккаунта возможно, но его нужно делать как отдельную опциональную функцию. Для первой версии достаточно:

1. искать видео и получать метаданные через YouTube Data API v3;
2. открывать выбранный результат в видимом официальном YouTube IFrame Player или в приложении/браузере YouTube;
3. запрашивать только `youtube.readonly`, когда пользователь явно включает синхронизацию;
4. хранить OAuth-токены только в Android Keystore и давать пользователю кнопку «Отключить YouTube».

Нельзя строить AURA на извлечении MP3/потока из YouTube, скрытом проигрывателе или фоновой аудиодорожке. Политики YouTube прямо запрещают отделять audio/video компоненты, скачивание, блокировку рекламы и background play.

## Что даёт YouTube Data API

[Обзор YouTube Data API v3](https://developers.google.com/youtube/v3/getting-started) описывает ресурсы поиска, каналов, видео, плейлистов, подписок и активностей. Для поиска AURA должна использовать `search.list` с `type=video`, а язык и регион передавать явно (`relevanceLanguage`/`regionCode`: `ru`, `az`, `en`, `AZ`, `RU`, `US` по ситуации).

[Документация `search.list`](https://developers.google.com/youtube/v3/docs/search/list) указывает отдельный квотный бакет поиска. Поиск нужно кэшировать, дебаунсить и не перебирать запросами каждую фразу пользователя; точный лимит проекта следует контролировать в Google Cloud Console.

Данные, которые подходят для музыкального брейна:

- название, канал, описание, thumbnail и `videoId`;
- длительность и признаки видео после `videos.list`;
- публичные плейлисты и каналы;
- список понравившихся видео пользователя через связанный playlist ID канала при наличии разрешения.

История просмотра не должна считаться доступным источником предпочтений. В [документации `playlistItems.list`](https://developers.google.com/youtube/v3/docs/playlistItems/list) есть ошибки `watchHistoryNotAccessible` и `watchLaterNotAccessible`; в [истории изменений API](https://developers.google.com/youtube/v3/revision_history) также указано, что watch history и watch later нельзя получить через API.

## OAuth: разрешения и риск

[YouTube OAuth 2.0](https://developers.google.com/youtube/v3/guides/authentication) нужен для приватных данных и действий от имени пользователя. Service Account для обычного YouTube-пользователя не подходит: YouTube API не умеет связывать service account с YouTube-аккаунтом и возвращает `NoLinkedYouTubeAccount`.

Для Android используем [OAuth 2.0 для installed/mobile apps](https://developers.google.com/youtube/v3/guides/auth/installed-apps): системный браузер/Custom Tab, Android OAuth client, PKCE (`S256`) и параметр `state`. OAuth нельзя проводить внутри Android WebView. В установленном приложении client secret нельзя считать секретом.

Официальный список scopes:

| Scope | Возможность | Решение для AURA |
| --- | --- | --- |
| `https://www.googleapis.com/auth/youtube.readonly` | просмотр данных YouTube-аккаунта | единственный рекомендуемый scope для первой синхронизации |
| `https://www.googleapis.com/auth/youtube` | управление YouTube-аккаунтом | не запрашивать без отдельной функции и явного согласия |
| `https://www.googleapis.com/auth/youtube.force-ssl` | просмотр/изменение/удаление видео, оценок, комментариев и captions | не запрашивать для музыкального поиска |
| `https://www.googleapis.com/auth/youtube.upload` | управление загрузками видео | AURA не нужна |

Источник описаний scopes: [OAuth 2.0 Scopes for Google APIs](https://developers.google.com/identity/protocols/oauth2/scopes) и таблица scopes в [installed-apps guide](https://developers.google.com/youtube/v3/guides/auth/installed-apps).

Google рекомендует не хардкодить credentials, не коммитить их в репозиторий, хранить пользовательские access/refresh tokens в защищённом хранилище, отзывать ненужные токены и проверять `state`. Для Android это означает [Android Keystore](https://developer.android.com/privacy-and-security/keystore). Подробности: [OAuth best practices](https://developers.google.com/identity/protocols/oauth2/resources/best-practices).

Публичное приложение с чувствительными scopes может потребовать OAuth verification. Если пользователь отказал в scope, функция должна отключаться, а повторный запрос разрешения допустим только в контексте конкретной функции. Важно: для installed apps Google отдельно отмечает, что incremental authorization не поддерживается, поэтому нужно заранее проектировать минимальный набор scopes.

## Воспроизведение: что разрешено

Для встроенного воспроизведения используем [YouTube IFrame Player API](https://developers.google.com/youtube/iframe_api_reference) в видимом player. Он поддерживает play/pause/seek/volume и события состояния. Для Android WebView YouTube описывает Media Integrity attestation и требования к идентификации приложения.

[Required Minimum Functionality](https://developers.google.com/youtube/terms/required-minimum-functionality) требует корректно показывать embedded player и его стандартные элементы. Для embedded player нужен идентификатор клиента через `HTTP Referer`/эквивалентную идентификацию.

[Developer Policies](https://developers.google.com/youtube/terms/developer-policies-guide) запрещают:

- извлекать или отделять audio из video и отдавать MP3;
- скачивать видео для offline playback вне YouTube Premium;
- запускать background/non-visible player или продолжать воспроизведение после закрытия/сворачивания окна;
- блокировать или заменять рекламу;
- скрывать стандартные элементы и метаданные YouTube;
- делать приложение клоном YouTube без самостоятельной ценности.

Следовательно, команда «включи песню с YouTube» в AURA должна означать: найти ролик, показать карточку с источником и запустить видимый официальный player/открыть YouTube. Нельзя превращать YouTube в нелегальный аудиопровайдер для фонового проигрывания.

## Аккаунт YouTube Music

Официальная справка [YouTube Music: персонализация и аккаунт](https://support.google.com/youtubemusic/answer/9231765) подтверждает, что рекомендации и персонализация зависят от входа пользователя; casting может работать как удалённое управление, а на общем устройстве нужно учитывать приватность.

Для стороннего Android-приложения не следует предполагать наличие публичного YouTube Music API только потому, что функции есть в официальном приложении. В архитектуре AURA нельзя зависеть от внутренних/непубличных endpoints YouTube Music. Надёжный контракт — YouTube Data API v3 плюс официальный player/deep link.

## Минимально безопасная архитектура AURA

```text
Пользователь нажал «Подключить YouTube»
        |
        v
Системный браузер + OAuth PKCE + state
        |
        v
youtube.readonly (только при включении синхронизации)
        |
        +--> загрузить разрешённые публичные данные/likes
        |
        +--> локально нормализовать предпочтения (без хранения лишних персональных данных)
        |
        v
YouTube Data API search.list -> карточки -> видимый IFrame/YouTube app
```

Практические правила:

1. Режим без аккаунта остаётся полностью рабочим: поиск по публичным данным и локальная история AURA.
2. Перед OAuth показать короткое объяснение: что читаем, зачем, где храним и как отключить.
3. Не отправлять refresh token в Gemini и не класть его в логи, аналитику или `SharedPreferences`.
4. Хранить только агрегированные музыкальные предпочтения (жанры, языки, исполнители), а не сырые просмотры и полные идентификаторы аккаунта без необходимости.
5. Добавить «Отключить YouTube»: отозвать токен, удалить локальные данные синхронизации и вернуть AURA в режим без аккаунта.
6. Обрабатывать `401/403`, `quotaExceeded`, отзыв доступа и отсутствие канала без бесконечных повторов.
7. Для каждого результата сохранять источник (`YouTube`) и не изменять title/thumbnail/ссылку.

## Как обучить музыкальный brain искать «колыбельную», а не «чил»

Поиск должен быть двухэтапным:

1. Gemini/локальный классификатор превращает запрос в структурированный профиль: `intent=music_search`, `mood=gentle`, `activity=sleep`, `genre=lullaby`, `language=ru|az|en`, `vocal=optional`, `children_safe=true`.
2. YouTube search выполняется несколькими узкими запросами (`колыбельная`, `lullaby`, `layla mahnısı`) с `type=video`, языком и регионом. Затем AURA фильтрует ролики по title/description/duration/channel и ранжирует совпадение с intent. Слово `chill` нельзя считать синонимом `lullaby`.

Для азербайджанского нужны отдельные нормализации и запросы (`layla`, `layla mahnısı`, `uşaq laylası`, `sakit musiqi`), а не машинная подстановка русского слова. Английские запросы (`lullaby`, `sleep music`, `nursery lullaby`) выполняются только как дополнительные, если язык не определён или пользователь разрешил смешанный поиск.

Результаты должны иметь объяснимую причину выбора: «выбрала именно колыбельную: в названии есть `layla/lullaby`, спокойная длительность и детская категория». Если уверенность низкая, AURA спрашивает уточнение, а не включает случайный chill-микс.

## Решение

Подключение аккаунта безопасно внедрять только как opt-in синхронизацию с минимальным readonly scope. Для MVP не запрашивать `youtube`/`youtube.force-ssl`, не синхронизировать watch history (API этого не позволяет), не извлекать аудио и не обещать фоновое YouTube-воспроизведение. Сначала реализовать точный multilingual search/ranking и видимый официальный player; затем отдельно пройти OAuth verification и policy review перед публикацией.

