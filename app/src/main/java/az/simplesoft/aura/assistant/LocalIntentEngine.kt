package az.simplesoft.aura.assistant

/**
 * Fast, private first-pass understanding. Only explicit music and device commands become actions.
 * Ambiguous phrases are deliberately returned as local conversation and never become a search.
 */
class LocalIntentEngine {

    fun understand(raw: String): AssistantReply {
        val original = raw.trim()
        val text = original.lowercase().replace(Regex("\\s+"), " ")
        val language = AssistantLanguage.detect(text)
        if (text.isBlank()) return conversation(language, phrase(language, "Я тебя не расслышала.", "Səni eşidə bilmədim.", "I didn't catch that."))

        val compoundParts = text.split(Regex("\\s+(?:и|and|həm|sonra)\\s+"))
            .map(String::trim)
            .filter(String::isNotBlank)
        if (compoundParts.size in 2..3) {
            val commands = compoundParts.map { part -> understand(part).intent }
                .filter { it != MusicIntent.Unknown && it !is MusicIntent.Search }
            if (commands.size >= 2) {
                return action(
                    MusicIntent.Composite(commands), language,
                    "Выполняю несколько команд.", "Bir neçə əmri yerinə yetirirəm.", "Running a few commands."
                )
            }
        }

        Regex("(?:громкость|звук)\\s+(?:на\\s+)?(\\d{1,3})\\s*(?:%|процент(?:ов|а)?)?").find(text)
            ?.groupValues?.getOrNull(1)?.toIntOrNull()?.takeIf { it in 0..100 }?.let { percent ->
                return action(MusicIntent.SetVolume(percent), language, "Громкость $percent процентов.", "Səs $percent faizdir.", "Volume set to $percent percent.")
            }
        Regex("(?:volume|səs)\\s+(?:to|at|\\s)?(\\d{1,3})\\s*%?").find(text)
            ?.groupValues?.getOrNull(1)?.toIntOrNull()?.takeIf { it in 0..100 }?.let { percent ->
                return action(MusicIntent.SetVolume(percent), language, "Громкость $percent процентов.", "Səs $percent faizdir.", "Volume set to $percent percent.")
            }

        explicitQueueIntent(text, language)?.let { return it }
        explicitLibraryIntent(text, language)?.let { return it }
        equalizerIntent(text, language)?.let { return it }
        localPlaybackIntent(text, language)?.let { return it }
        localConversation(text, language)?.let { return it }
        explicitMusicSearch(text, language)?.let { return it }

        return AssistantReply(
            intent = MusicIntent.Unknown,
            text = phrase(
                language,
                "Секунду, я подумаю…",
                "Bir saniyə, düşünüm…",
                "One moment, let me think…"
            ),
            language = language,
            route = AssistantRoute.LOCAL_CONVERSATION,
            memoryInsights = extractMemoryInsights(original, language)
        )
    }

    private fun explicitQueueIntent(text: String, language: AssistantLanguage): AssistantReply? {
        listOf(
            Regex("таймер\\s+сна\\s+(\\d{1,3})"),
            Regex("выключи\\s+через\\s+(\\d{1,3})"),
            Regex("sleep\\s+timer\\s+(\\d{1,3})"),
            Regex("yuxu\\s+taymeri\\s+(\\d{1,3})")
        ).firstNotNullOfOrNull { regex -> regex.find(text)?.groupValues?.getOrNull(1)?.toIntOrNull() }
            ?.takeIf { it in 1..240 }?.let { minutes ->
                return action(MusicIntent.SleepTimer(minutes), language, "Выключу через $minutes минут.", "$minutes dəqiqəyə söndürəcəyəm.", "I'll stop in $minutes minutes.")
            }
        if (matchesAny(text, "отмени таймер", "выключи таймер сна", "cancel sleep timer", "stop sleep timer", "yuxu taymerini ləğv et")) {
            return action(MusicIntent.CancelSleepTimer, language, "Таймер сна выключен.", "Yuxu taymeri söndürüldü.", "Sleep timer cancelled.")
        }
        if (matchesAny(text, "останови после этой песни", "выключись после трека", "stop after this song", "stop after the track", "mahnıdan sonra dayan")) {
            return action(MusicIntent.StopAfterTrack, language, "Остановлюсь после этой песни.", "Bu mahnıdan sonra dayanacağam.", "I'll stop after this song.")
        }
        if (matchesAny(text, "удали последний трек", "удали последнюю песню", "remove last track", "remove the last song", "son mahnını sil")) {
            return action(MusicIntent.RemoveLastFromQueue, language, "Удаляю последний трек из очереди.", "Son mahnını növbədən silirəm.", "Removing the last track from the queue.")
        }
        listOf(
            Regex("(?:включи|поставь|сыграй)\\s+следующим\\s+(.+)"),
            Regex("(?:play|put)\\s+(.+?)\\s+next"),
            Regex("(.+?)\\s+növbəti\\s+çal")
        ).firstNotNullOfOrNull { it.find(text)?.groupValues?.getOrNull(1)?.trim() }?.let { query ->
            return action(MusicIntent.QueueTrack(query, true), language,
                "Добавляю следующим: $query.", "$query növbəti səslənəcək.", "I'll play $query next.")
        }
        listOf(
            Regex("добавь\\s+(?:в\\s+очередь\\s+(.+)|(.+?)\\s+в\\s+очередь)"),
            Regex("add\\s+(.+?)\\s+to\\s+(?:the\\s+)?queue"),
            Regex("(.+?)\\s+növbəyə\\s+əlavə\\s+et")
        ).forEach { regex ->
            regex.find(text)?.let { match ->
                val query = match.groupValues.drop(1).firstOrNull(String::isNotBlank)?.trim().orEmpty()
                if (query.isNotBlank()) return action(MusicIntent.QueueTrack(query, false), language,
                    "Добавляю в очередь: $query.", "$query növbəyə əlavə olunur.", "Adding $query to the queue.")
            }
        }
        return when {
            matchesAny(text, "очисти очередь", "очистить очередь", "очисть очередь", "удали очередь", "clear queue", "clear the queue", "növbəni təmizlə") ->
                action(MusicIntent.ClearQueue, language, "Очищаю очередь.", "Növbəni təmizləyirəm.", "Clearing the queue.")
            matchesAny(text, "открой очередь", "покажи очередь", "open queue", "show queue", "show the queue", "növbəni göstər") || text == "очередь" ->
                action(MusicIntent.OpenQueue, language, "Открываю очередь.", "Növbəni açıram.", "Opening the queue.")
            matchesAny(text, "выключи автопродолжение", "отключи автопродолжение", "disable autoplay") ->
                action(MusicIntent.AutoContinue(false), language, "Автопродолжение выключено.", "Avtomatik davam söndürüldü.", "Auto-continue is off.")
            matchesAny(text, "включи автопродолжение", "enable autoplay") ->
                action(MusicIntent.AutoContinue(true), language, "Автопродолжение включено.", "Avtomatik davam aktivdir.", "Auto-continue is on.")
            else -> null
        }
    }

    private fun explicitLibraryIntent(text: String, language: AssistantLanguage): AssistantReply? {
        listOf(
            Regex("(?:включи|запусти|воспроизведи)\\s+плейлист\\s+(.+)"),
            Regex("play\\s+(?:the\\s+)?playlist\\s+(.+)"),
            Regex("(.+?)\\s+pleylistini\\s+çal")
        ).firstNotNullOfOrNull { it.find(text)?.groupValues?.getOrNull(1)?.trim() }?.let { name ->
            val display = name.replaceFirstChar { it.titlecase() }
            return action(MusicIntent.PlayPlaylist(display), language,
                "Включаю плейлист $display.", "$display pleylistini açıram.", "Playing playlist $display.")
        }
        listOf(
            Regex("(?:перемешай|перемешать)\\s+плейлист\\s+(.+)"),
            Regex("shuffle\\s+(?:the\\s+)?playlist\\s+(.+)")
        ).firstNotNullOfOrNull { it.find(text)?.groupValues?.getOrNull(1)?.trim() }?.let { name ->
            val display = name.replaceFirstChar { it.titlecase() }
            return action(MusicIntent.PlayPlaylist(display, true), language,
                "Перемешиваю плейлист $display.", "$display pleylistini qarışdırıram.", "Shuffling playlist $display.")
        }
        if (matchesAny(text, "открой плейлисты", "покажи плейлисты", "мои плейлисты", "плейлисты", "open playlists", "show playlists", "my playlists", "pleylistlərimi göstər")) {
            return action(MusicIntent.OpenPlaylists, language, "Открываю твои плейлисты.", "Pleylistlərini açıram.", "Opening your playlists.")
        }
        if (matchesAny(text, "открой радио", "покажи радио", "радиостанции", "радио", "включи радио", "запусти радио", "поставь радио", "включи радиостанцию", "радио по странам", "open radio", "show radio", "play radio", "radio stations", "turn on the radio", "radionu aç")) {
            return action(MusicIntent.OpenRadio, language, "Открываю радио по странам.", "Radionu açıram.", "Opening radio stations.")
        }
        if (matchesAny(text, "сохрани очередь", "save queue")) {
            val name = text.substringAfter("плейлист", "Сохранённая очередь").trim()
                .ifBlank { "Сохранённая очередь" }.replaceFirstChar { it.titlecase() }
            return action(MusicIntent.CreatePlaylist(name, true), language,
                "Сохраняю очередь в плейлист.", "Növbəni pleylist kimi saxlayıram.", "Saving the queue as a playlist.")
        }
        listOf(Regex("(?:создай|создать)\\s+плейлист\\s*(.*)"), Regex("create\\s+(?:a\\s+)?playlist\\s*(.*)"))
            .firstNotNullOfOrNull { it.find(text)?.groupValues?.getOrNull(1)?.trim() }?.let { rawName ->
                val name = rawName.ifBlank { "Новый плейлист" }.replaceFirstChar { it.titlecase() }
                return action(MusicIntent.CreatePlaylist(name, false), language,
                    "Создаю плейлист $name.", "$name pleylistini yaradıram.", "Creating playlist $name.")
            }
        return null
    }

    private fun equalizerIntent(text: String, language: AssistantLanguage): AssistantReply? {
        if (!matchesAny(text, "эквалайзер", "equalizer", "eq", "ekvalayzer")) return null
        if (matchesAny(text, "выключи эквалайзер", "отключи эквалайзер", "выключи eq", "disable equalizer", "eq off")) {
            return action(MusicIntent.DisableEqualizer, language, "Эквалайзер выключен.", "Ekvalayzer söndürüldü.", "Equalizer off.")
        }
        val preset = EqualizerPreset.fromText(text)
        return if (preset != null) {
            action(MusicIntent.SetEqualizer(preset), language, "Эквалайзер: ${preset.label}.", "Ekvalayzer: ${preset.label}.", "Equalizer: ${preset.name.lowercase()}.")
        } else {
            action(MusicIntent.CycleEqualizer, language, "Переключаю пресет эквалайзера.", "Ekvalayzer preseti dəyişir.", "Changing equalizer preset.")
        }
    }

    private fun localPlaybackIntent(text: String, language: AssistantLanguage): AssistantReply? = when {
        matchesAny(text, "мой микс", "включи мой микс", "музыка для меня", "подбери мне музыку", "поставь микс", "поставь микс песен", "поставить микс", "поставить микс песен", "включи микс песен", "включить микс", "включить микс песен", "микс песен", "подборку песен", "my mix", "play my mix", "play a mix", "mix of songs", "mənim miksim", "mahnı miksini qoş") -> action(MusicIntent.MyMix, language, "Собираю твой микс.", "Sənin miksini hazırlayıram.", "Building your mix.")
        matchesAny(text, "продолжить прослушивание", "продолжи слушать", "возобнови прослушивание", "continue listening", "resume listening", "musiqini davam etdir") -> action(MusicIntent.ContinueListening, language, "Продолжаю с того, что тебе нравится.", "Sevdiyin musiqidən davam edirəm.", "Continuing with music you like.")
        matchesAny(text, "пауза", "поставь на паузу", "приостанови", "останови воспроизведение", "останови музыку", "выключи музыку", "выключи песню", "стоп музыка", "pauza", "стоп", "pause", "pause the music", "stop the music", "stop playback", "dayandır") -> action(MusicIntent.Pause, language, "Ставлю на паузу.", "Pauza edirəm.", "Pausing.")
        matchesAny(text, "продолжи", "играй дальше", "возобнови", "сними с паузы", "включи воспроизведение", "resume", "resume music", "continue playing", "davam et") -> action(MusicIntent.Play, language, "Продолжаю.", "Davam edirəm.", "Resuming.")
        matchesAny(text, "следующая", "следующий", "следующий трек", "следующий канал", "переключи канал", "дальше", "переключи песню", "переключи трек", "next", "next song", "next track", "next station", "change channel", "növbəti mahnı", "növbəti kanal") -> action(MusicIntent.Next, language, "Переключаю дальше.", "Növbəti kanala keçirəm.", "Switching to the next one.")
        matchesAny(text, "предыдущая", "предыдущий", "предыдущий трек", "предыдущий канал", "назад", "верни предыдущий", "previous", "previous track", "previous song", "previous station", "əvvəlki mahnı", "əvvəlki kanal") -> action(MusicIntent.Previous, language, "Возвращаю предыдущий.", "Əvvəlki kanala qayıdıram.", "Going back one.")
        matchesAny(text, "громче", "прибавь звук", "прибавить звук", "увеличь громкость", "увеличить громкость", "сделай погромче", "сделай звук погромче", "громкость выше", "louder", "turn it up", "volume up", "increase volume", "səsi artır") -> action(MusicIntent.Louder, language, "Делаю громче.", "Səsi artırıram.", "Turning it up.")
        matchesAny(text, "тише", "убавь звук", "убавить звук", "уменьши громкость", "уменьшить громкость", "сделай потише", "сделай звук потише", "громкость ниже", "quieter", "turn it down", "volume down", "decrease volume", "səsi azalt") -> action(MusicIntent.Quieter, language, "Делаю тише.", "Səsi azaldıram.", "Turning it down.")
        matchesAny(text, "без звука", "выключи звук", "заглуши", "mute", "mute music", "səsi söndür") -> action(MusicIntent.Mute, language, "Выключаю звук.", "Səsi söndürürəm.", "Muting.")
        matchesAny(text, "включи звук", "верни звук", "сними без звука", "unmute", "unmute music", "səsi aç") -> action(MusicIntent.Unmute, language, "Включаю звук.", "Səsi açıram.", "Unmuting.")
        matchesAny(text, "убери из любимых", "убери из избранного", "сними лайк", "не нравится", "dislike", "unlike", "remove from favorites", "xoşum gəlmir") -> action(MusicIntent.Unlike, language, "Убрала из любимых.", "Seçilmişlərdən sildim.", "Removed from favorites.")
        matchesAny(text, "добавь в любимые", "добавь в избранное", "сохрани эту песню", "мне нравится", "лайк", "add to favorites", "add this to favorites", "like this", "xoşuma gəlir") -> action(MusicIntent.Like, language, "Добавила в любимые.", "Seçilmişlərə əlavə etdim.", "Added to favorites.")
        matchesAny(text, "повтор", "повторяй", "повтори песню", "зацикли", "repeat", "repeat this", "repeat song", "təkrar et") -> action(MusicIntent.Repeat, language, "Переключаю режим повтора.", "Təkrar rejimini dəyişirəm.", "Changing repeat mode.")
        matchesAny(text, "случайный порядок", "в случайном порядке", "перемешай", "перемешай песни", "шаффл", "shuffle", "shuffle the queue", "qarışdır") -> action(MusicIntent.Shuffle, language, "Перемешиваю очередь.", "Növbəni qarışdırıram.", "Shuffling the queue.")
        matchesAny(text, "что сейчас играет", "что играет", "какая песня играет", "что за трек", "what is playing", "what's playing", "now playing", "nə səslənir") -> action(MusicIntent.NowPlaying, language, "Сейчас играет.", "Hazırda səslənir.", "Now playing.")
        matchesAny(text, "открой историю", "покажи историю", "история прослушивания", "open history", "show history", "listening history", "tarixçəni göstər") -> action(MusicIntent.OpenHistory, language, "Открываю историю.", "Tarixçəni açıram.", "Opening history.")
        matchesAny(text, "больше такого", "ещё такого", "мне нравится такое", "more like this", "play more like this", "daha belə") -> action(MusicIntent.MoreLikeThis, language, "Подбираю ещё похожее.", "Buna oxşar daha çox musiqi seçirəm.", "Finding more like this.")
        matchesAny(text, "не это", "не такое", "убери эту песню", "не нравится эта песня", "not this", "don't play this", "bu deyil", "bunu istəmirəm") -> action(MusicIntent.NotThis, language, "Убираю этот вариант и подберу другой.", "Bu variantı silib başqasını seçirəm.", "I'll skip this and find another option.")
        matchesAny(text, "похожее", "что-то похожее", "похожие песни", "похожую музыку", "similar music", "find similar", "oxşar musiqi") -> action(MusicIntent.Similar, language, "Подбираю похожее.", "Oxşar musiqi seçirəm.", "Finding similar music.")
        matchesAny(text, "режим машины", "автомобильный режим", "режим вождения", "я за рулём", "в машине", "car mode", "driving mode", "driving", "maşın rejimi") -> action(MusicIntent.CarMode, language, "Включаю автомобильный режим.", "Avtomobil rejimini açıram.", "Turning on car mode.")
        else -> null
    }

    private fun localConversation(text: String, language: AssistantLanguage): AssistantReply? {
        if (matchesAny(text, "забудь всё обо мне", "забудь все обо мне", "удали мою память", "forget everything about me", "clear my memory", "məni unut")) {
            return action(MusicIntent.ClearMemory, language, "Удаляю сохранённую память о тебе.", "Sənin haqqındakı yaddaşı silirəm.", "Clearing the memory saved about you.")
        }
        val response = when {
            text.matches(Regex("(?:привет|здравствуй|доброе утро|добрый вечер|salam|sabahın xeyir|hello|hi|hey)[!. ]*")) ->
                phrase(language, "Привет! Как настроение? Что будем слушать?", "Salam! Əhvalın necədir? Nə dinləyək?", "Hi! How are you feeling? What shall we listen to?")
            matchesAny(text, "как дела", "как ты", "necəsən", "how are you") ->
                phrase(language, "У меня всё отлично — я рядом и готова помочь. А ты как?", "Məndə hər şey əladır, yanındayam. Bəs sən necəsən?", "I'm great and ready to help. How are you?")
            matchesAny(text, "спасибо", "благодарю", "təşəkkür", "sağ ol", "thank you", "thanks") ->
                phrase(language, "Всегда пожалуйста ♥", "Həmişə məmnuniyyətlə ♥", "Always happy to help ♥")
            matchesAny(text, "расскажи шутку", "пошути", "zarafat et", "tell me a joke") ->
                phrase(language, "Почему музыканты не спорят с метрономом? Он всё равно всегда прав по такту.", "Musiqiçilər niyə metronomla mübahisə etmir? Çünki o, həmişə ritmdə haqlıdır.", "Why don't musicians argue with a metronome? It always has perfect timing.")
            else -> null
        }
        return response?.let { conversation(language, it, extractMemoryInsights(text, language)) }
    }

    private fun explicitMusicSearch(text: String, language: AssistantLanguage): AssistantReply? {
        // Mixed speech is common in ASR; command cues must not depend on one detected language.
        val musicCue = Regex("\\b(включи|поставь|сыграй|найди|cal|qos|qoş|seslendir|tap|play|put on|find)\\b")
        if (!musicCue.containsMatchIn(text)) return null

        val mood = detectMood(text)
        val decade = Regex("(19|20)\\d0").find(text)?.value?.toIntOrNull()
        val cleaned = text
            .replace(Regex("\\b(включи|поставь|найди|сыграй|музыку|песни|песню|трек|треки|play|put|on|find|song|music|track|çal|qoş|tap|mahnı|musiqi|mahni|mahnisi|mahnini)\\b"), " ")
            .replace(Regex("\\b(пожалуйста|пожалста|please|zəhmət\\s+olmasa)\\b"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        val query = cleaned.ifBlank { mood?.title ?: phrase(language, "музыка", "musiqi", "music") }
        return AssistantReply(
            intent = MusicIntent.Search(query = query, mood = mood, decade = decade),
            text = phrase(language, "Хорошо, ищу $query.", "Oldu, $query axtarıram.", "Okay, looking for $query."),
            language = language,
            route = AssistantRoute.LOCAL_ACTION
        )
    }

    private fun detectMood(text: String): Mood? = when {
        matchesAny(text, "колыбельн", "баю", "усып", "песн для сна ребен", "детск песн на ночь", "lullaby", "bedtime", "nursery song", "sleep song", "baby sleep", "layla", "beşik", "uşaq yuxu") -> Mood.LULLABY
        matchesAny(text, "спокойн", "расслаб", "релакс", "calm", "relax", "sakit") -> Mood.CALM
        matchesAny(text, "дорог", "поездк", "за рул", "driving", "road", "yol") -> Mood.DRIVE
        matchesAny(text, "работ", "концентрац", "фокус", "focus", "work", "diqqət") -> Mood.FOCUS
        matchesAny(text, "энерг", "трениров", "бодр", "energy", "workout", "enerj") -> Mood.ENERGY
        matchesAny(text, "ноч", "вечер", "night", "gecə") -> Mood.NIGHT
        matchesAny(text, "груст", "печал", "sad", "kədər") -> Mood.SAD
        matchesAny(text, "весёл", "радост", "позитив", "happy", "cheerful", "şad") -> Mood.HAPPY
        else -> null
    }

    private fun extractMemoryInsights(text: String, language: AssistantLanguage): List<MemoryInsight> {
        val normalized = text.trim()
        val name = listOf(
            Regex("(?i)меня зовут\\s+([\\p{L}\\-]{2,30})"),
            Regex("(?i)моё имя\\s+([\\p{L}\\-]{2,30})"),
            Regex("(?i)mənim adım\\s+([\\p{L}\\-]{2,30})"),
            Regex("(?i)my name is\\s+([\\p{L}\\-]{2,30})")
        ).firstNotNullOfOrNull { it.find(normalized)?.groupValues?.getOrNull(1) }
        return buildList {
            if (!name.isNullOrBlank()) add(MemoryInsight("identity", "name", name.replaceFirstChar { it.titlecase() }))
            add(MemoryInsight("language", "preferred", language.tag))
        }
    }

    private fun action(intent: MusicIntent, language: AssistantLanguage, ru: String, az: String, en: String) =
        AssistantReply(intent, phrase(language, ru, az, en), language, AssistantRoute.LOCAL_ACTION)

    private fun conversation(language: AssistantLanguage, text: String, insights: List<MemoryInsight> = emptyList()) =
        AssistantReply(MusicIntent.Unknown, text, language, AssistantRoute.LOCAL_CONVERSATION, insights)

    private fun phrase(language: AssistantLanguage, ru: String, az: String, en: String) = when (language) {
        AssistantLanguage.RUSSIAN -> ru
        AssistantLanguage.AZERBAIJANI -> az
        AssistantLanguage.ENGLISH -> en
    }

    private fun matchesAny(text: String, vararg values: String) = values.any { text == it || text.contains(it) }
}
