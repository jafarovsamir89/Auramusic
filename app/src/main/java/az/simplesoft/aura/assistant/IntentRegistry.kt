package az.simplesoft.aura.assistant

data class IntentPattern(
    val intentId: String,
    val language: AssistantLanguage? = null,
    val examples: List<String>,
    val keywords: Set<String> = emptySet(),
    val negativeKeywords: Set<String> = emptySet(),
    val minimumConfidence: Double = 0.80,
    val priority: Int = 0
)

data class RegistryIntentMatch(
    val intentId: String,
    val confidence: Double,
    val matchedPattern: String,
    val reason: String,
    val priority: Int = 0
)

/** Registry for compact, high-value commands. Long music queries remain with the legacy parser. */
class IntentRegistry(
    patterns: List<IntentPattern> = defaultPatterns()
) {
    private val patterns = patterns.toList()

    fun candidates(input: NormalizedAssistantInput, limit: Int = 3): List<RegistryIntentMatch> = patterns.asSequence()
            .filter { it.language == null || it.language == input.detectedLanguage }
            .mapNotNull { pattern ->
                val score = pattern.examples.maxOfOrNull { example ->
                    val exact = if (input.normalizedText == TextNormalizer.normalizeForMatching(example)) 1.0 else 0.0
                    val contains = if (input.normalizedText.contains(TextNormalizer.normalizeForMatching(example))) 0.94 else 0.0
                    maxOf(exact, contains, FuzzyMatching.combined(input.normalizedText, example))
                } ?: 0.0
                val keywordBonus = pattern.keywords.count(input.tokens::contains) * 0.04
                val negativePenalty = pattern.negativeKeywords.count(input.tokens::contains) * 0.15
                val confidence = (score + keywordBonus - negativePenalty).coerceIn(0.0, 1.0)
                if (confidence < pattern.minimumConfidence) null else RegistryIntentMatch(
                    pattern.intentId,
                    confidence,
                    pattern.examples.maxBy { FuzzyMatching.combined(input.normalizedText, it) },
                    "pattern=${pattern.intentId};score=${"%.3f".format(confidence)}",
                    pattern.priority
                )
            }
            .sortedWith(
                compareByDescending<RegistryIntentMatch> { it.confidence }
                    .thenByDescending { it.priority }
            )
            .take(limit.coerceAtLeast(1))
            .toList()

    fun match(input: NormalizedAssistantInput): RegistryIntentMatch? = candidates(input, 1).firstOrNull()

    companion object {
        private fun defaultPatterns() = listOf(
            IntentPattern("open_queue", examples = listOf("открой очередь", "покажи очередь", "open queue", "show the queue", "nobeni goster"), priority = 100),
            IntentPattern("open_playlists", examples = listOf("открой плейлисты", "мои плейлисты", "покажи плейлисты", "open playlists", "show playlists"), priority = 100),
            IntentPattern("open_radio", examples = listOf("открой радио", "включи радио", "запусти радио", "радиостанции", "radio stations", "show radio", "play radio", "radionu ac"), priority = 100),
            IntentPattern("clear_queue", examples = listOf("очисти очередь", "очистить очередь", "очисть очередь", "удали очередь", "clear queue", "clear the queue", "novbeni temizle"), priority = 100),
            IntentPattern("remove_last_queue", examples = listOf("удали последний трек", "удали последнюю песню", "remove last track", "remove the last song", "son mahnını sil"), priority = 100),
            IntentPattern("stop_after_track", examples = listOf("останови после этой песни", "выключись после трека", "stop after this song", "stop after the track", "mahnıdan sonra dayan"), priority = 100),
            IntentPattern("cancel_sleep_timer", examples = listOf("отмени таймер", "выключи таймер сна", "cancel sleep timer", "stop sleep timer", "yuxu taymerini ləğv et"), priority = 100),
            IntentPattern("next", examples = listOf("следующая", "следующий", "следующий трек", "следующий канал", "переключи канал", "дальше", "next", "next track", "next station", "change channel", "novbeti mahni", "növbəti track", "növbəti kanal"), priority = 90),
            IntentPattern("previous", examples = listOf("предыдущая", "предыдущий", "предыдущий трек", "предыдущий канал", "назад", "previous", "previous track", "previous station", "evvelki mahni", "əvvəlki mahnı", "əvvəlki kanal"), priority = 90),
            IntentPattern("louder", examples = listOf("громче", "прибавь звук", "прибавить звук", "увеличь громкость", "увеличить громкость", "сделай звук погромче", "громкость выше", "turn it up", "volume up", "increase volume", "sesi artir"), priority = 90),
            IntentPattern("quieter", examples = listOf("тише", "убавь звук", "убавить звук", "уменьши громкость", "уменьшить громкость", "сделай звук потише", "громкость ниже", "turn it down", "volume down", "decrease volume", "sesi azalt"), priority = 90),
            IntentPattern("pause", examples = listOf("пауза", "поставь на паузу", "останови музыку", "останови воспроизведение", "pause the music", "stop playback", "pauza", "pause", "dayandir"), priority = 90),
            IntentPattern("play", examples = listOf("продолжи", "возобнови", "сними с паузы", "играй дальше", "resume music", "continue playing", "resume", "davam et"), priority = 90),
            IntentPattern("mute", examples = listOf("выключи звук", "без звука", "заглуши", "mute music", "mute", "sesi söndür"), priority = 90),
            IntentPattern("unmute", examples = listOf("включи звук", "верни звук", "сними без звука", "unmute music", "unmute", "səsi aç"), priority = 90),
            IntentPattern("equalizer_off", examples = listOf("выключи эквалайзер", "отключи эквалайзер", "disable equalizer", "eq off"), priority = 95),
            IntentPattern("equalizer_bass", examples = listOf("эквалайзер бас", "басовый эквалайзер", "bass equalizer", "eq bass"), priority = 95),
            IntentPattern("equalizer_vocal", examples = listOf("эквалайзер вокал", "вокальный эквалайзер", "vocal equalizer", "eq vocal"), priority = 95),
            IntentPattern("equalizer_rock", examples = listOf("эквалайзер рок", "rock equalizer", "eq rock"), priority = 95),
            IntentPattern("equalizer_acoustic", examples = listOf("эквалайзер акустика", "acoustic equalizer", "eq acoustic"), priority = 95),
            IntentPattern("equalizer_cycle", examples = listOf("включи эквалайзер", "переключи эквалайзер", "change equalizer", "change eq"), priority = 90),
            IntentPattern("like", examples = listOf("добавь в любимые", "добавь в избранное", "сохрани эту песню", "мне нравится", "add to favorites", "like this", "xoşuma gəlir"), priority = 80),
            IntentPattern("unlike", examples = listOf("убери из любимых", "убери из избранного", "сними лайк", "не нравится", "unlike", "remove from favorites", "xoşum gəlmir"), priority = 80),
            IntentPattern("repeat", examples = listOf("повтор", "повтори песню", "зацикли", "repeat this", "təkrar et"), priority = 80),
            IntentPattern("shuffle", examples = listOf("перемешай", "в случайном порядке", "случайный порядок", "shuffle the queue", "shuffle", "qarışdır"), priority = 80),
            IntentPattern("more_like_this", examples = listOf("больше такого", "ещё такого", "more like this", "play more like this", "daha belə"), priority = 85),
            IntentPattern("not_this", examples = listOf("не это", "не такое", "убери эту песню", "not this", "don't play this", "bu deyil"), priority = 85),
            IntentPattern("clear_memory", examples = listOf("забудь всё обо мне", "удали мою память", "forget everything about me", "clear my memory", "məni unut"), priority = 110),
            IntentPattern("similar", examples = listOf("похожее", "oxşar musiqi"), priority = 80),
            IntentPattern("my_mix", examples = listOf("мой микс", "mənim miksim"), priority = 80),
            IntentPattern("continue_listening", examples = listOf("продолжить прослушивание", "musiqini davam etdir"), priority = 80),
            IntentPattern("car_mode", examples = listOf("режим машины", "maşın rejimi"), priority = 80)
        )
    }
}
