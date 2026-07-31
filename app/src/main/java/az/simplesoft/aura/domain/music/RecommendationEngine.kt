package az.simplesoft.aura.domain.music

import az.simplesoft.aura.assistant.Mood
import az.simplesoft.aura.data.Track

interface RecommendationEngine {
    suspend fun continueListening(): List<Track>
    suspend fun buildMoodQueue(mood: Mood): List<Track>
}

object EmptyRecommendationEngine : RecommendationEngine {
    override suspend fun continueListening(): List<Track> = emptyList()
    override suspend fun buildMoodQueue(mood: Mood): List<Track> = emptyList()
}
