package az.simplesoft.aura.assistant

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class LocalKnowledgeAnswer(
    val text: String,
    val language: AssistantLanguage,
    val entities: List<AssistantEntity> = emptyList(),
    val memoryInsights: List<MemoryInsight> = emptyList()
)

/** Answers only facts available on-device; it never turns a general question into music search. */
class LocalKnowledgeEngine(
    private val clock: () -> Date = ::Date
) {
    fun answer(input: NormalizedAssistantInput, context: AssistantContext): LocalKnowledgeAnswer? {
        val text = input.normalizedText
        val language = input.detectedLanguage
        val name = context.memoryFacts.firstOrNull { it.category == "identity" && it.key == "name" }?.value
        return when {
            containsAny(text, "кто ты", "кто такая аура", "ты кто", "aura kimsən", "sən kimsən", "who are you") ->
                LocalKnowledgeAnswer(
                    greetingName(language, name), language,
                    listOf(AssistantEntity(AssistantEntityType.APP_NAME, "AURA"))
                )
            containsAny(text, "что умеешь", "что ты умеешь", "твои возможности", "помоги", "помощь", "nə bacarırsan", "kömək", "what can you do", "help") ->
                LocalKnowledgeAnswer(capabilities(language), language)
            containsAny(text, "который час", "сколько времени", "время", "saat neçədir", "saat necedir", "what time is it") ->
                LocalKnowledgeAnswer(timeText(language, clock()), language, listOf(AssistantEntity(AssistantEntityType.TIME, "now")))
            containsAny(text, "какая сегодня дата", "какое сегодня число", "сегодняшняя дата", "сегодня день", "какой сегодня день", "bu gün hansı gündür", "bu günün tarixi", "what day is it", "what is today's date") ->
                LocalKnowledgeAnswer(dateText(language, clock()), language, listOf(AssistantEntity(AssistantEntityType.DATE, "today")))
            containsAny(text, "что сейчас играет", "что играет", "сейчас играет", "nə səslənir", "what is playing") ->
                LocalKnowledgeAnswer(nowPlaying(language, context), language, context.currentTrack?.let {
                    listOf(AssistantEntity(AssistantEntityType.TRACK, it.title), AssistantEntity(AssistantEntityType.ARTIST, it.artist))
                }.orEmpty())
            containsAny(text, "сколько в очереди", "размер очереди", "очереди сколько", "növbədə neçə", "how many in queue") ->
                LocalKnowledgeAnswer(queueText(language, context.queue.size), language)
            containsAny(text, "мои любимые", "избранное", "сколько любимых", "sevimlilərim", "my favorites") ->
                LocalKnowledgeAnswer(favoritesText(language, context.favoriteCount), language)
            containsAny(text, "мои плейлисты", "сколько плейлистов", "pleylistlərim", "my playlists") ->
                LocalKnowledgeAnswer(playlistsText(language, context.playlists), language)
            containsAny(text, "что ты помнишь", "что помнишь обо мне", "nə xatırlayırsan", "what do you remember") ->
                LocalKnowledgeAnswer(memoryText(language, context.memoryFacts), language)
            else -> null
        }
    }

    private fun greetingName(language: AssistantLanguage, name: String?): String = when (language) {
        AssistantLanguage.RUSSIAN -> if (name.isNullOrBlank()) "Я AURA — твой локальный музыкальный помощник. Я понимаю команды, ищу музыку и управляю плеером без облака." else "Я AURA, приятно познакомиться, $name. Я локальный музыкальный помощник и работаю без облака."
        AssistantLanguage.AZERBAIJANI -> "Mən AURA-yam — musiqi axtaran və pleyeri idarə edən yerli köməkçiyəm."
        AssistantLanguage.ENGLISH -> "I'm AURA — your local music assistant. I can search music and control playback without the cloud."
    }

    private fun capabilities(language: AssistantLanguage): String = when (language) {
        AssistantLanguage.RUSSIAN -> "Я могу искать и включать музыку, управлять очередью и плейлистами, менять громкость, запоминать простые предпочтения и отвечать на вопросы о плеере."
        AssistantLanguage.AZERBAIJANI -> "Mən musiqi axtarıb qoşa, növbə və pleylistləri idarə edə, səsi dəyişə və pleyer haqqında məlumat verə bilərəm."
        AssistantLanguage.ENGLISH -> "I can search and play music, manage the queue and playlists, change volume, and answer questions about the player."
    }

    private fun timeText(language: AssistantLanguage, date: Date): String {
        val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
        return when (language) {
            AssistantLanguage.RUSSIAN -> "Сейчас $time."
            AssistantLanguage.AZERBAIJANI -> "İndi saat $time-dır."
            AssistantLanguage.ENGLISH -> "It’s $time."
        }
    }

    private fun dateText(language: AssistantLanguage, date: Date): String {
        val pattern = when (language) {
            AssistantLanguage.RUSSIAN -> "d MMMM, EEEE"
            AssistantLanguage.AZERBAIJANI -> "d MMMM, EEEE"
            AssistantLanguage.ENGLISH -> "EEEE, MMMM d"
        }
        val formatted = SimpleDateFormat(pattern, Locale.forLanguageTag(language.tag)).format(date)
        return when (language) {
            AssistantLanguage.RUSSIAN -> "Сегодня $formatted."
            AssistantLanguage.AZERBAIJANI -> "Bu gün $formatted."
            AssistantLanguage.ENGLISH -> "Today is $formatted."
        }
    }

    private fun nowPlaying(language: AssistantLanguage, context: AssistantContext): String {
        val track = context.currentTrack
        return when (language) {
            AssistantLanguage.RUSSIAN -> if (track == null) "Сейчас ничего не выбрано." else "Сейчас играет «${track.title}» — ${track.artist}."
            AssistantLanguage.AZERBAIJANI -> if (track == null) "Hazırda heç nə seçilməyib." else "Hazırda «${track.title}» — ${track.artist} səslənir."
            AssistantLanguage.ENGLISH -> if (track == null) "Nothing is selected right now." else "Now playing “${track.title}” by ${track.artist}."
        }
    }

    private fun queueText(language: AssistantLanguage, count: Int): String = when (language) {
        AssistantLanguage.RUSSIAN -> "В очереди $count треков."
        AssistantLanguage.AZERBAIJANI -> "Növbədə $count mahnı var."
        AssistantLanguage.ENGLISH -> "There are $count tracks in the queue."
    }

    private fun favoritesText(language: AssistantLanguage, count: Int): String = when (language) {
        AssistantLanguage.RUSSIAN -> "В любимых $count треков."
        AssistantLanguage.AZERBAIJANI -> "Sevimlilərdə $count mahnı var."
        AssistantLanguage.ENGLISH -> "You have $count favorite tracks."
    }

    private fun playlistsText(language: AssistantLanguage, playlists: List<String>): String = when (language) {
        AssistantLanguage.RUSSIAN -> if (playlists.isEmpty()) "Плейлистов пока нет." else "У тебя ${playlists.size} плейлистов: ${playlists.take(3).joinToString()}."
        AssistantLanguage.AZERBAIJANI -> if (playlists.isEmpty()) "Hələ pleylist yoxdur." else "${playlists.size} pleylistin var: ${playlists.take(3).joinToString()}."
        AssistantLanguage.ENGLISH -> if (playlists.isEmpty()) "You don't have any playlists yet." else "You have ${playlists.size} playlists: ${playlists.take(3).joinToString()}."
    }

    private fun memoryText(language: AssistantLanguage, facts: List<AssistantMemoryFact>): String {
        val values = facts.map { "${it.key}: ${it.value}" }
        return when (language) {
            AssistantLanguage.RUSSIAN -> if (values.isEmpty()) "Пока ничего устойчивого не запомнила." else "Я помню: ${values.take(4).joinToString()}."
            AssistantLanguage.AZERBAIJANI -> if (values.isEmpty()) "Hələ yadda saxladığım sabit məlumat yoxdur." else "Bunları xatırlayıram: ${values.take(4).joinToString()}."
            AssistantLanguage.ENGLISH -> if (values.isEmpty()) "I haven't saved any lasting details yet." else "I remember: ${values.take(4).joinToString()}."
        }
    }

    private fun containsAny(text: String, vararg phrases: String): Boolean = phrases.any {
        val normalized = TextNormalizer.normalizeForMatching(it)
        text == normalized || text.contains(normalized)
    }
}
