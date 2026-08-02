package az.simplesoft.aura.assistant

/**
 * Полностью локальный движок команд.
 * Не отправляет текст на сервер и не расходует токены.
 */
class LocalIntentEngine {

    fun understand(raw: String): AssistantReply {
        val text = raw.lowercase().trim()
        if (text.isBlank()) return reply(MusicIntent.Unknown, "Я тебя не расслышала.")

        Regex("""(?:включи|поставь|сыграй)\s+следующим\s+(.+)""").find(text)?.let { match ->
            val query = match.groupValues[1].trim()
            return reply(MusicIntent.QueueTrack(query, playNext = true), "Добавляю следующим: $query.")
        }
        Regex("""добавь\s+(?:в\s+очередь\s+(.+)|(.+?)\s+в\s+очередь)""").find(text)?.let { match ->
            val query = match.groupValues.drop(1).first(String::isNotBlank).trim()
            return reply(MusicIntent.QueueTrack(query, playNext = false), "Добавляю в очередь: $query.")
        }
        if (containsAny(text, "открой очередь", "покажи очередь") || text == "очередь") {
            return reply(MusicIntent.OpenQueue, "Открываю очередь.")
        }
        if (containsAny(text, "открой радио", "покажи радио", "радиостанции", "радио по странам") || text == "радио") {
            return reply(MusicIntent.OpenRadio, "Открываю радио по странам.")
        }
        if (containsAny(text, "очисти очередь", "очистить очередь")) {
            return reply(MusicIntent.ClearQueue, "Очищаю очередь.")
        }
        if (containsAny(text, "выключи автопродолжение", "отключи автопродолжение")) {
            return reply(MusicIntent.AutoContinue(false), "Автопродолжение выключено.")
        }
        if (containsAny(text, "включи автопродолжение")) {
            return reply(MusicIntent.AutoContinue(true), "Автопродолжение включено.")
        }
        Regex("""(?:включи|запусти|воспроизведи)\s+плейлист\s+(.+)""").find(text)?.let { match ->
            val name = match.groupValues[1].trim().replaceFirstChar { it.titlecase() }
            return reply(MusicIntent.PlayPlaylist(name), "Включаю плейлист $name.")
        }
        Regex("""(?:перемешай|перемешать)\s+плейлист\s+(.+)""").find(text)?.let { match ->
            val name = match.groupValues[1].trim().replaceFirstChar { it.titlecase() }
            return reply(MusicIntent.PlayPlaylist(name, shuffled = true), "Перемешиваю плейлист $name.")
        }
        if (containsAny(text, "открой плейлисты", "покажи плейлисты", "мои плейлисты") || text == "плейлисты") {
            return reply(MusicIntent.OpenPlaylists, "Открываю твои плейлисты.")
        }
        if (containsAny(text, "сохрани очередь", "сохранить очередь")) {
            val name = text.substringAfter("плейлист", "Сохранённая очередь").trim()
                .ifBlank { "Сохранённая очередь" }
                .replaceFirstChar { it.titlecase() }
            return reply(MusicIntent.CreatePlaylist(name, includeQueue = true), "Сохраняю очередь в плейлист.")
        }
        Regex("""(?:создай|создать)\s+плейлист\s*(.*)""").find(text)?.let { match ->
            val name = match.groupValues[1].trim().ifBlank { "Новый плейлист" }
                .replaceFirstChar { it.titlecase() }
            return reply(MusicIntent.CreatePlaylist(name, includeQueue = false), "Создаю плейлист $name.")
        }

        when {
            containsAny(text, "мой микс", "включи мой микс", "музыка для меня") ->
                return reply(MusicIntent.MyMix, "Собираю твой микс.")
            containsAny(text, "продолжить прослушивание", "продолжи слушать", "продолжи мою музыку") ->
                return reply(MusicIntent.ContinueListening, "Продолжаю с того, что тебе нравится.")
            containsAny(text, "пауза", "останови", "стоп") ->
                return reply(MusicIntent.Pause, "Ставлю на паузу.")
            containsAny(text, "продолжи", "играй дальше", "воспроизведи") ->
                return reply(MusicIntent.Play, "Продолжаю.")
            containsAny(text, "следующая", "следующий трек", "переключи") ->
                return reply(MusicIntent.Next, "Следующий трек.")
            containsAny(text, "предыдущая", "назад") ->
                return reply(MusicIntent.Previous, "Возвращаю предыдущий трек.")
            containsAny(text, "громче", "прибавь") ->
                return reply(MusicIntent.Louder, "Делаю громче.")
            containsAny(text, "тише", "убавь") ->
                return reply(MusicIntent.Quieter, "Делаю тише.")
            containsAny(text, "без звука", "выключи звук", "mute") ->
                return reply(MusicIntent.Mute, "Выключаю звук.")
            containsAny(text, "убери из любимых", "удали из любимых", "не нравится") ->
                return reply(MusicIntent.Unlike, "Убрала из любимых.")
            containsAny(text, "нравится", "в любимые", "лайк") ->
                return reply(MusicIntent.Like, "Добавила в любимые.")
            containsAny(text, "повтор", "повторяй") ->
                return reply(MusicIntent.Repeat, "Повтор включён.")
            containsAny(text, "случайный порядок", "перемешай", "шаффл") ->
                return reply(MusicIntent.Shuffle, "Перемешиваю очередь.")
            containsAny(text, "что сейчас играет", "что играет") ->
                return reply(MusicIntent.NowPlaying, "Сейчас играет.")
            containsAny(text, "открой историю", "покажи историю") ->
                return reply(MusicIntent.OpenHistory, "Открываю историю.")
            containsAny(text, "похожее", "ещё такое", "похожие песни") ->
                return reply(MusicIntent.Similar, "Подбираю похожее.")
            containsAny(text, "режим машины", "автомобильный режим", "я за рулём") ->
                return reply(MusicIntent.CarMode, "Включаю автомобильный режим.")
        }

        val mood = when {
            containsAny(text, "спокойн", "расслаб", "релакс") -> Mood.CALM
            containsAny(text, "дорог", "поездк", "за рул") -> Mood.DRIVE
            containsAny(text, "работ", "концентрац", "фокус") -> Mood.FOCUS
            containsAny(text, "энерг", "трениров", "бодр") -> Mood.ENERGY
            containsAny(text, "ноч", "вечер") -> Mood.NIGHT
            containsAny(text, "груст", "печаль") -> Mood.SAD
            containsAny(text, "весёл", "радост", "позитив") -> Mood.HAPPY
            else -> null
        }

        val decade = Regex("""(19|20)\d0""").find(text)?.value?.toIntOrNull()
        val cleaned = text
            .replace(Regex("""\b(включи|поставь|найди|сыграй|музыку|песни|песню|трек|треки)\b"""), "")
            .replace(Regex("""\s+"""), " ")
            .trim()

        if (containsAny(text, "включи", "поставь", "найди", "сыграй") || mood != null) {
            val query = cleaned.ifBlank { mood?.title ?: "музыка" }
            return reply(
                MusicIntent.Search(query = query, mood = mood, decade = decade),
                "Ищу: $query"
            )
        }

        return reply(MusicIntent.Search(text), "Попробую найти: $text")
    }

    private fun containsAny(text: String, vararg values: String) =
        values.any(text::contains)

    private fun reply(intent: MusicIntent, text: String) =
        AssistantReply(intent, text)
}
