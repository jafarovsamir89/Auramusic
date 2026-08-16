# Аудит источников музыкальных чартов для build-time metadata

Дата проверки: 13 августа 2026 года. Цель — выбрать источники, из которых можно собирать только метаданные чартов (название, исполнитель, позиция, регион, дата), не извлекая аудио и не превращая сайт в копию закрытого сервиса. «Build-time» означает запуск сборщика в CI/на машине разработчика с сохранением небольшого проверенного snapshot; приложение не должно зависеть от источника при старте.

## Краткий вывод

| Источник | Доступный формат | Стабильность для сборщика | Регион/история | Правовой риск | Рекомендация |
|---|---|---:|---|---:|---|
| [Billboard](https://www.billboard.com/charts/) | HTML, часть страниц за подпиской; публичного поддерживаемого API не найдено | Низкая–средняя | США, Billboard Global и национальные/жанровые чарты; архив неполный/частично paywall | Высокий | Только ручная выгрузка или лицензия; не скрейпить как CI-зависимость |
| [Official Charts](https://www.officialcharts.com/charts/) | HTML; B2B/licensed data, API/CSV публично не обещаны | Средняя–высокая | UK (50+ еженедельных чартов), архив с 1950-х через сайт | Высокий | Лучший официальный UK-источник при лицензии; для прототипа — минимальный ручной snapshot с атрибуцией |
| [Spotify Charts](https://charts.spotify.com/) | Веб-таблица и кнопка CSV; отдельный API для чартов не документирован | Средняя | Global, страны/города; daily/weekly; история доступна через UI, но URLs/схема меняются | Высокий | Разрешённый CSV для внутреннего build-time только после проверки условий; не использовать Spotify Web API для обхода чартов |
| [Apple Music API Charts](https://developer.apple.com/documentation/applemusicapi/charts) | Официальный JSON API (JWT developer token) | Высокая для текущих данных | ISO storefronts, global/city charts; архивные даты не обещаны | Средний–высокий | Приемлемо для build-time внутри приложения, продумать Apple attribution и ограничения Feed |
| [Kworb](https://kworb.net/) | Статический HTML, удобен для чтения таблиц; JSON/CSV/API не заявлены | Средняя–низкая | Много стран, Spotify/iTunes/YouTube; есть даты и накопительные ряды | Высокий/неясный | Только исследовательский fallback; не считать данные лицензированными без разрешения автора и первичных платформ |
| [YouTube Music Charts](https://charts.youtube.com/) | Веб-интерфейс; YouTube Data API даёт `mostPopular`, но не официальный Music Charts endpoint | Средняя | 61 страна/регион, global/local, daily/weekly; история через UI не гарантирована | Высокий при скрейпинге | Использовать только официально опубликованные ссылки/ручной экспорт; API — для видео-метаданных, не для эмуляции чартов |
| [ListenBrainz](https://listenbrainz.org/data/) | JSON API, PostgreSQL/Spark full и incremental dumps | Высокая (open-source, но community-operated) | Sitewide/user stats, country datasets; all-time/year/week/range; dumps дважды в месяц + daily incremental | Низкий для listen data (CC0), но учитывать персональные данные и MusicBrainz attribution | Основной открытый build-time источник агрегированной популярности, с лимитами и кэшированием |

Рейтинг «стабильность» относится к способности воспроизводимо получить метаданные, а не к точности/репрезентативности чарта. Всегда сохраняйте `source`, `source_url`, `region`, `period`, `retrieved_at`, `rank` и checksum snapshot.

## 1. Billboard

**Что публикуется.** Billboard заявляет более 200 индустриальных чартов, включая Hot 100, Billboard 200, жанровые, национальные и Billboard Global; доступ к «all 200+ charts» включён в платный Pro-доступ ([страница подписки](https://subscribe.billboard.com/var-a)). Отдельная официальная локальная страница Hot 100 показывает позицию, прошлую позицию, пик и недели, а методология ссылается на потоковые, радио- и sales-данные Luminate ([пример Billboard Canada Hot 100](https://ca.billboard.com/charts/billboard-canadian-hot-100)).

**Формат и стабильность.** Страницы обычно рендерят HTML (иногда через JS), но Billboard не публикует поддерживаемый публичный charts API или официальный CSV feed. Архивные даты и полный топ могут требовать аккаунт/подписку; HTML и доступность меняются между доменами (US, Canada, Africa). Это делает автоматический сбор хрупким и непригодным как обязательный шаг сборки.

**Лицензия/ToS.** В локальной версии Billboard Canada (оператор той же Billboard Media сети) прямо сказано, что контент, включая чарты, нельзя модифицировать, воспроизводить, распространять или коммерчески использовать без письменного разрешения ([Terms and Conditions](https://ca.billboard.com/static-page/terms-of-use)). Это не лицензия на датасет и не следует автоматически считать дословными условиями для US-домена — это консервативный сигнал о риске. Для коммерческого или регулярно обновляемого каталога нужен договор с Billboard/Luminate; «публично видно» не означает право на массовое копирование.

**Регион и история.** Сильная сторона — США и глобальные/жанровые версии; канадские и другие локальные редакции имеют отдельные страницы. Исторические чарты существуют, но глубина и полнота зависят от конкретного чарта и подписки.

**Решение для AURA.** Не скрейпить Billboard в CI. Если нужен престижный Hot 100, хранить небольшой вручную проверенный snapshot с ссылкой и датой либо купить лицензированный feed; при ошибке сборки использовать предыдущий snapshot.

## 2. Official Charts Company (UK)

**Что публикуется.** Official Charts описывает более 50 чартов каждую неделю (Singles, Albums, жанры, Vinyl, Streaming и др.), собирает продажи/стримы из 8 000 источников и покрывает свыше 99% singles и 98% albums consumption UK ([описание и методология](https://www.officialcharts.com/about/)). Сайт даёт текущий Top 100 и поле «Access the archive» ([страница Singles Chart](https://www.officialcharts.com/charts/singles-chart/)).

**Формат и стабильность.** Публичные страницы доступны как HTML и имеют предсказуемую недельную периодичность. Внутренние JSON endpoints не являются публичным контрактом; CSV/API на consumer-сайте не обещаны. Для длительной интеграции есть официальные B2B-продукты, а не scraping.

**Лицензия/ToS.** Copyright notice запрещает несанкционированное воспроизведение, передачу и распространение за пределами subscription/licensing agreements ([copyright notice](https://www.officialcharts.com/who-we-are/copyright-notice/)). Компания продаёт current и historic chart data и предлагает chart licensing ([our services](https://www.officialcharts.com/our-business-services/our-services/)); контакт для лицензии — `commercial@officialcharts.com`.

**Регион и история.** Это официальный UK-источник: еженедельные чарты, жанровые и форматные списки, а также архив с первой UK Singles Chart от ноября 1952 года ([история Official Charts](https://www.officialcharts.com/who-we-are/history-official-charts/)). Не подменяет национальные чарты других стран и не даёт гарантии ежедневной granularity.

**Решение для AURA.** Наиболее качественный источник британского сигнала, но только через лицензирование или небольшой ручной snapshot для прототипа. При сохранении позиций не копировать обложки/тексты и не обещать полноту архива.

## 3. Spotify Charts

**Что публикуется.** Spotify Charts — официальный сайт с global, regional и city charts. Spotify объясняет, что chart streams проходят фильтры eligibility и что daily обычно публикуются до 18:00 ET следующего дня; weekly-период — пятница–четверг ([Understanding Spotify charts](https://support.spotify.com/us/artists/article/understanding-spotify-charts/)). В веб-интерфейсе доступна выгрузка `.csv` через кнопку Download (подтверждение в [ответе Spotify Community](https://community.spotify.com/t5/Desktop-Windows/How-to-save-Spotify-charts-as-playlist/td-p/6659610)); URL download и схема не являются документированным API.

**Формат и стабильность.** UI даёт HTML/JS-таблицу и CSV для выбранного чарта/даты. Исторические даты могут исчезать или менять URL; не следует хардкодить старый `spotifycharts.com` endpoint. Spotify Web API документирует каталог, playlists и user data, но не выдаёт официальный endpoint для чтения Spotify Charts.

**Лицензия/ToS.** [Spotify Developer Policy](https://developer.spotify.com/policy) требует атрибуции и ссылки назад для Spotify metadata/cover art и запрещает предоставлять metadata как standalone service, анализировать Spotify Content/Service для производных listenership metrics и коммерческое использование за пределами разрешённых случаев. Общие [Terms of Use](https://www.spotify.com/us/legal/end-user-agreement/) дают лишь ограниченное, отзывное, personal/non-commercial право доступа и запрещают redistribution. Поэтому CSV, скачанный человеком, не является свободной лицензией на перепубликацию.

**Регион и история.** Global, многие страны и city charts; availability отдельных регионов зависит от локальной аудитории. Daily/weekly — сильная текущая оперативность, но архивная глубина не гарантируется.

**Решение для AURA.** Допустим только контролируемый внутренний build-time импорт CSV с атрибуцией и проверкой актуальных условий; не скрейпить UI, не хранить cover art и не использовать Spotify Web API для построения независимых рейтингов. Для публичного коммерческого каталога запросить письменное разрешение.

## 4. Apple Music charts и Apple Music Feed

**Что публикуется.** Официальный endpoint `GET /v1/catalog/{storefront}/charts` возвращает JSON с popularity-ordered songs/albums/music videos, принимает ISO 3166-1 storefront, `limit` до 200 и `with=cityCharts,dailyGlobalTopCharts` ([Apple Music API Charts](https://developer.apple.com/documentation/applemusicapi/charts)). Нужен developer token; без Authorization API отвечает 401. Apple прямо описывает API как способ получать charts, recommendations и catalog metadata ([Apple Music API overview](https://developer.apple.com/documentation/applemusicapi)).

**Формат и стабильность.** JSON-схема и endpoint документированы, pagination (`next`) предусмотрена. Это наиболее удобный официальный machine-readable источник текущих чартов. API даёт storefront/global/city срезы, но не обещает запрос произвольной исторической даты; для offline bulk есть Apple Music Feed, который обновляется каждые 24 часа ([Apple Music Feed](https://developer.apple.com/documentation/AppleMusicFeed)).

**Лицензия/ToS.** Критическое ограничение Feed: Apple разрешает его только для публичного продвижения Apple Music-контента внутри приложения и запрещает внутренние системы, передачу третьим лицам и анализ музыки/артистов вне этой цели (см. раздел Important в [Feed documentation](https://developer.apple.com/documentation/AppleMusicFeed)). Для обычного API нужно соблюдать Apple Developer Program/Media Services terms, attribution и ссылки на Apple Music; нельзя считать JSON общественным доменом.

**Регион и история.** Storefront-модель покрывает страны/территории Apple Music; city и daily global доступны там, где Apple публикует соответствующий chart. Historical snapshots придётся сохранять самостоятельно с датой retrieval, если это допускает договор.

**Решение для AURA.** Хороший технический источник для build-time snapshot (JWT в CI, rate limit, retries), если продукт действительно продвигает Apple Music и соблюдает attribution. Не использовать Feed как общий аналитический датасет и не объединять его с независимым рейтингом без проверки условий.

## 5. Kworb

**Что публикуется.** Kworb — независимый персональный сайт-агрегатор: разделы Spotify, iTunes, YouTube, radio и worldwide. Страницы вроде [Spotify Daily Chart — US](https://kworb.net/spotify/country/us_daily.html) отдают обычную HTML-таблицу с rank, daily/7-day/total streams и датой; на сайте есть страны и накопительные ряды. В FAQ автор говорит, что статические страницы сделаны, чтобы не перегружать сервер, и допускает строить собственные графики ([FAQ](https://kworb.net/faq.html)).

**Формат и стабильность.** HTML очень легко разобрать, JSON/CSV/API контракт не заявлен. Сайт держится на одном операторе и upstream-платформах; возможны задержки, блокировки и изменения разметки. Это medium/low для production dependency, хотя исторические daily pages иногда удобнее официальных UI.

**Лицензия/ToS.** У сайта есть privacy/terms страница, где указано использование YouTube API ([privacy](https://kworb.net/privacy.html)), но нет явной лицензии на весь агрегированный датасет. Фраза «feel free to use data to make graphs» — не полноценная лицензия на массовую републикацию, коммерческое использование или upstream Spotify/iTunes data. YouTube API policy отдельно запрещает scraping (см. [Developer Policies](https://developers.google.com/youtube/terms/developer-policies)).

**Регион и история.** Широкое покрытие стран и платформ, daily и all-time показатели; это не официальный unified chart, методология и correction policy могут отличаться от платформ.

**Решение для AURA.** Использовать только для разведки, cross-check и ручного прототипа. Для snapshot запросить разрешение Kworb и отдельно проверить права исходных платформ; не ставить сайт в критический CI path.

## 6. YouTube Music Charts / YouTube Data API

**Что публикуется.** YouTube Music Help описывает daily Trending, Daily Top Music Videos, Daily/Weekly Top Songs, Shorts и Weekly Top Artists. Top Songs объединяет просмотры official music video, user-made videos с official song и lyric videos; paid advertising views не учитываются ([YouTube Charts & Insights](https://support.google.com/youtubemusic/answer/9014376?hl=en)). На той же странице перечислены 61 доступная страна/регион и local/global ranking.

**Формат и стабильность.** `charts.youtube.com` — web UI; публичного documented CSV/JSON charts feed нет. [YouTube Data API `videos.list`](https://developers.google.com/youtube/v3/guides/implementation/videos) поддерживает `chart=mostPopular` (trending videos), но это не тот же самый Music Top Songs/Artists chart и не даёт исторические chart snapshots. Для музыки API полезен для разрешения video ID и текущих view counts, а не для копирования ранжирования.

**Лицензия/ToS.** [YouTube API Developer Policies](https://developers.google.com/youtube/terms/developer-policies) прямо запрещают API-клиентам прямо или косвенно scraping YouTube/Google applications или получение scraped data. [YouTube Terms](https://www.youtube.com/t/terms) также ограничивают автоматизированный доступ. Нельзя обходить отсутствие chart endpoint через HTML/внутренние RPC.

**Регион и история.** 61 country/region, daily и weekly, local/global; критерии могут обновляться, а песни после нескольких месяцев без роста могут автоматически удаляться. Исторический архив и стабильный date API не обещаны.

**Решение для AURA.** Хранить только собственные snapshot, полученные из разрешённого интерфейса/партнёрского доступа, либо использовать YouTube API для отдельных video metadata с соблюдением действующих политик хранения/кэширования и attribution. Не объявлять `mostPopular` эквивалентом Music Charts.

## 7. ListenBrainz

**Что публикуется.** ListenBrainz предоставляет открытые агрегаты: `GET /1/stats/sitewide/recordings` с top recordings, listen counts и диапазонами (`week`, `month`, `year`, `all_time`), а также popularity endpoints по artist/recording ([Statistics API](https://listenbrainz.readthedocs.io/en/latest/users/api/statistics.html), [Popularity API](https://listenbrainz.readthedocs.io/en/latest/users/api/popularity.html)). Есть country-oriented datasets и готовые JSON examples на [MetaBrainz Dataset Hoster](https://datasets.listenbrainz.org/).

**Формат и стабильность.** API — JSON, dumps — PostgreSQL/Spark/JSON. [Data page](https://listenbrainz.org/data/) обещает fullexport примерно дважды в месяц, daily incremental dumps и spark export; актуальные файлы доступны через mirrors. Это открытый проект, поэтому закладывайте rate limits, 429/5xx, backup mirror и schema versioning. All-time sitewide top ограничен top 1000, а user Year in Music может иметь архивную нестабильную структуру (см. API docs).

**Лицензия/приватность.** ListenBrainz указывает, что user listen data и text публикуются под [CC0](https://listenbrainz.org/data/) и что входящие пользователи соглашаются на включение истории в публичные dumps ([sign-in notice](https://listenbrainz.org/feed/)). Это допускает build-time использование агрегатов, но не отменяет минимизацию персональных идентификаторов, GDPR/удаление snapshots и атрибуцию MusicBrainz metadata. CC0 ListenBrainz не лицензирует обложки, аудио или чужие upstream каталоги.

**Регион и история.** Сильная историческая глубина для community listening data и диапазонов времени; это не официальный sales/streaming chart и выборка смещена в сторону пользователей ListenBrainz. Country popularity доступна в datasets, но полнота страны зависит от MBID/area mapping.

**Решение для AURA.** Основной безопасный открытый источник для мировых и all-time рекомендационных метаданных. Собирать агрегаты на сервере/в CI, сохранять только нужные поля, кэшировать и показывать объяснение «по данным ListenBrainz», не выдавая его за Billboard/Spotify chart.

## Рекомендуемый pipeline для build-time snapshot

1. Сначала использовать ListenBrainz JSON/dumps (CC0) как открытый базовый слой; отдельно добавить Apple Music API только при выполнении Apple-условий.
2. Billboard, Official Charts и Spotify подключать только через ручной CSV/лицензированный feed; Kworb — только как cross-check.
3. YouTube использовать для ссылок/video metadata через официальный API, не скрейпить Charts UI и не подменять Music Chart параметром `mostPopular`.
4. Нормализовать строки в `artist`, `title`, `rank`, `chart`, `region`, `period_start`, `period_end`, `source`, `source_url`, `retrieved_at`; сохранять raw response вне APK для аудита.
5. Генерировать компактный JSON/SQLite snapshot атомарно. При недоступности сети сборка должна продолжать использовать последний проверенный snapshot.
6. Показывать пользователю источник и дату («Spotify Global weekly, week …», «ListenBrainz all-time»), не смешивать несопоставимые метрики в один «официальный мировой рейтинг».

Этот документ является техническим и лицензионным ориентиром, а не юридическим заключением. Перед публичным/коммерческим распространением получить письменное подтверждение правообладателей.
