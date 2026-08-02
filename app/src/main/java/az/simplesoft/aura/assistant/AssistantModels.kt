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
            return when {
                normalized.any { it in 'ә'..'ә' || it in "çğıöşü" } ||
                    listOf("salam", "necəsən", "mahnı", "musiqi", "zəhmət").any(normalized::contains) -> AZERBAIJANI
                normalized.any { it in 'а'..'я' || it == 'ё' } -> RUSSIAN
                else -> ENGLISH
            }
        }
    }
}

enum class AssistantRoute {
    LOCAL_ACTION,
    LOCAL_CONVERSATION,
    NEEDS_REASONING
}

enum class AssistantRole { USER, AURA }

enum class AssistantSource { LOCAL, DEEPSEEK, FALLBACK }

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
    val source: AssistantSource = AssistantSource.LOCAL
)
