package az.simplesoft.aura.domain.music

import az.simplesoft.aura.data.Track
import az.simplesoft.aura.data.plugins.core.PluginFailureReason
import az.simplesoft.aura.data.search.UnifiedTrack

sealed interface SearchOutcome {
    data class Success(
        val tracks: List<UnifiedTrack>,
        val diagnostics: Map<String, String> = emptyMap()
    ) : SearchOutcome

    data class Failure(
        val reason: PluginFailureReason,
        val message: String
    ) : SearchOutcome
}

sealed interface PlaybackOutcome {
    data class Started(
        val track: Track,
        val resumedAtMs: Long = 0L,
        val alternativesAvailable: Int = 0
    ) : PlaybackOutcome

    data class Failure(val message: String) : PlaybackOutcome
}
