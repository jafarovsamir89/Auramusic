package az.simplesoft.aura.assistant

sealed interface MusicIntent {
    data class Search(
        val query: String,
        val artist: String? = null,
        val mood: Mood? = null,
        val decade: Int? = null
    ) : MusicIntent
    data object Play : MusicIntent
    data object Pause : MusicIntent
    data object Next : MusicIntent
    data object Previous : MusicIntent
    data object Like : MusicIntent
    data object Unlike : MusicIntent
    data object Louder : MusicIntent
    data object Quieter : MusicIntent
    data object Mute : MusicIntent
    data object Repeat : MusicIntent
    data object Shuffle : MusicIntent
    data object NowPlaying : MusicIntent
    data object OpenHistory : MusicIntent
    data object OpenPlaylists : MusicIntent
    data object OpenRadio : MusicIntent
    data object OpenQueue : MusicIntent
    data object ClearQueue : MusicIntent
    data class QueueTrack(val query: String, val playNext: Boolean) : MusicIntent
    data class AutoContinue(val enabled: Boolean) : MusicIntent
    data class CreatePlaylist(val name: String, val includeQueue: Boolean) : MusicIntent
    data class PlayPlaylist(val name: String, val shuffled: Boolean = false) : MusicIntent
    data object Similar : MusicIntent
    data object MyMix : MusicIntent
    data object ContinueListening : MusicIntent
    data object CarMode : MusicIntent
    data object Unknown : MusicIntent
}

enum class AssistantLanguage(val tag: String) {
    RUSSIAN("ru-RU"),
    AZERBAIJANI("az-AZ"),
    ENGLISH("en-US");

    companion object {
        fun detect(text: String): AssistantLanguage {
            val normalized = text.lowercase()
            val tokens = TextNormalizer.normalizeForMatching(normalized).split(' ').filter(String::isNotBlank)
            var russian = normalized.count { it in 'а'..'я' || it == 'ё' }.toDouble() * 0.04
            var azerbaijani = normalized.count { it in "əıöüşıçğ" }.toDouble() * 0.35
            var english = normalized.count { it in 'a'..'z' }.toDouble() * 0.01
            russian += tokens.count { it in RUSSIAN_WORDS } * 2.2
            azerbaijani += tokens.count { it in AZERBAIJANI_WORDS } * 2.4
            english += tokens.count { it in ENGLISH_WORDS } * 1.4
            // ASCII transliteration is common in speech transcripts and must remain AZ, not EN.
            if (tokens.any { it in AZERBAIJANI_TRANSLITERATIONS }) azerbaijani += 3.2
            return when {
                azerbaijani >= russian && azerbaijani >= english && azerbaijani > 0.5 -> AZERBAIJANI
                russian >= english && russian > 0.5 -> RUSSIAN
                else -> ENGLISH
            }
        }

        private val RUSSIAN_WORDS = setOf(
            "включи", "поставь", "сыграй", "найди", "песня", "песню", "музыку", "трек",
            "очередь", "плейлист", "громче", "тише", "пауза", "следующая", "предыдущая",
            "привет", "здравствуй", "как", "дела", "сегодня", "день", "мне", "нравится"
        )
        private val AZERBAIJANI_WORDS = setOf(
            "salam", "necəsən", "mahni", "musiqi", "qoş", "qos", "novbeti", "sesi",
            "artir", "azalt", "goster", "radionu", "pleylist", "mahnini", "bunu", "onu"
        )
        private val AZERBAIJANI_TRANSLITERATIONS = setOf(
            "salam", "mahni", "mahnini", "qos", "gosh", "sesi", "artir", "azalt", "novbeti", "goster", "ac"
        )
        private val ENGLISH_WORDS = setOf(
            "hello", "hi", "play", "pause", "next", "previous", "track", "song", "music", "queue", "please", "thanks"
        )
    }
}

enum class AssistantRoute {
    LOCAL_ACTION,
    LOCAL_CONVERSATION,
    NEEDS_REASONING
}

enum class AssistantRole { USER, AURA }

enum class AssistantSource { LOCAL, REMOTE, FALLBACK, DEEPSEEK }

enum class AssistantEntityType {
    TRACK, ARTIST, PLAYLIST, MOOD, DECADE, GENRE, DURATION, ORDINAL, TIME, DATE,
    APP_NAME, CONTACT, LOCATION
}

data class AssistantEntity(
    val type: AssistantEntityType,
    val value: String,
    val confidence: Double = 1.0
)

data class DecisionDiagnostics(
    val originalText: String,
    val normalizedText: String,
    val language: AssistantLanguage,
    val topIntents: List<String> = emptyList(),
    val selectedIntent: String? = null,
    val confidence: Double = 0.0,
    val entities: List<AssistantEntity> = emptyList(),
    val contextReferences: List<String> = emptyList(),
    val reason: String = "",
    val processingTimeMs: Long = 0L
)

data class AssistantDecision(
    val intentId: String,
    val confidence: Double,
    val entities: List<AssistantEntity>,
    val language: AssistantLanguage,
    val reply: String,
    val action: MusicIntent?,
    val memoryInsights: List<MemoryInsight> = emptyList(),
    val needsClarification: Boolean = false,
    val clarification: String? = null,
    val diagnostics: DecisionDiagnostics
) {
    val isUnresolved: Boolean
        get() = action == null && !needsClarification && diagnostics.reason !in setOf("local-knowledge", "local-conversation")
}

data class AssistantMessage(
    val id: String,
    val role: AssistantRole,
    val text: String,
    val language: AssistantLanguage,
    val createdAt: Long
)

data class MemoryInsight(
    val category: String,
    val key: String,
    val value: String
)

enum class Mood(val title: String) {
    CALM("спокойное"),
    DRIVE("для поездки"),
    FOCUS("для концентрации"),
    ENERGY("энергичное"),
    NIGHT("ночное"),
    SAD("грустное"),
    HAPPY("весёлое")
}

data class AssistantReply(
    val intent: MusicIntent,
    val text: String,
    val language: AssistantLanguage = AssistantLanguage.RUSSIAN,
    val route: AssistantRoute = if (intent == MusicIntent.Unknown) {
        AssistantRoute.NEEDS_REASONING
    } else {
        AssistantRoute.LOCAL_ACTION
    },
    val memoryInsights: List<MemoryInsight> = emptyList(),
    val source: AssistantSource = AssistantSource.LOCAL,
    val diagnostics: DecisionDiagnostics? = null
)
