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
    data object Similar : MusicIntent
    data object MyMix : MusicIntent
    data object ContinueListening : MusicIntent
    data object CarMode : MusicIntent
    data object Unknown : MusicIntent
}

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
    val text: String
)
