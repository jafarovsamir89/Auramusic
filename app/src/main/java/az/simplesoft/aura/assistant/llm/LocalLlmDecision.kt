package az.simplesoft.aura.assistant.llm

import az.simplesoft.aura.assistant.Mood
import az.simplesoft.aura.assistant.MusicIntent

/** Closed set returned by the local model. It never contains executable callbacks. */
sealed interface LocalLlmDecision {
    data class Action(val action: LocalLlmAction, val reply: String? = null) : LocalLlmDecision
    data class Conversation(val reply: String) : LocalLlmDecision
    data class Clarification(val question: String) : LocalLlmDecision
    data object Unresolved : LocalLlmDecision
}

sealed interface LocalLlmAction {
    data object Play : LocalLlmAction
    data object Pause : LocalLlmAction
    data object Next : LocalLlmAction
    data object Previous : LocalLlmAction
    data class SearchMusic(val query: String, val artist: String? = null, val mood: Mood? = null) : LocalLlmAction
    data object PlaySimilar : LocalLlmAction
    data object MyMix : LocalLlmAction
    data object Like : LocalLlmAction
    data object Unlike : LocalLlmAction
    data class QueueNext(val query: String) : LocalLlmAction
    data class QueueAdd(val query: String) : LocalLlmAction
    data object OpenQueue : LocalLlmAction
    data class PlayPlaylist(val name: String, val shuffled: Boolean = false) : LocalLlmAction
    data class CreatePlaylist(val name: String, val includeQueue: Boolean = false) : LocalLlmAction
    data object VolumeUp : LocalLlmAction
    data object VolumeDown : LocalLlmAction
    data object Repeat : LocalLlmAction
    data object Shuffle : LocalLlmAction
    data object NowPlaying : LocalLlmAction
}

fun LocalLlmAction.toMusicIntent(): MusicIntent = when (this) {
    LocalLlmAction.Play -> MusicIntent.Play
    LocalLlmAction.Pause -> MusicIntent.Pause
    LocalLlmAction.Next -> MusicIntent.Next
    LocalLlmAction.Previous -> MusicIntent.Previous
    is LocalLlmAction.SearchMusic -> MusicIntent.Search(query, artist, mood)
    LocalLlmAction.PlaySimilar -> MusicIntent.Similar
    LocalLlmAction.MyMix -> MusicIntent.MyMix
    LocalLlmAction.Like -> MusicIntent.Like
    LocalLlmAction.Unlike -> MusicIntent.Unlike
    is LocalLlmAction.QueueNext -> MusicIntent.QueueTrack(query, playNext = true)
    is LocalLlmAction.QueueAdd -> MusicIntent.QueueTrack(query, playNext = false)
    LocalLlmAction.OpenQueue -> MusicIntent.OpenQueue
    is LocalLlmAction.PlayPlaylist -> MusicIntent.PlayPlaylist(name, shuffled)
    is LocalLlmAction.CreatePlaylist -> MusicIntent.CreatePlaylist(name, includeQueue)
    LocalLlmAction.VolumeUp -> MusicIntent.Louder
    LocalLlmAction.VolumeDown -> MusicIntent.Quieter
    LocalLlmAction.Repeat -> MusicIntent.Repeat
    LocalLlmAction.Shuffle -> MusicIntent.Shuffle
    LocalLlmAction.NowPlaying -> MusicIntent.NowPlaying
}
