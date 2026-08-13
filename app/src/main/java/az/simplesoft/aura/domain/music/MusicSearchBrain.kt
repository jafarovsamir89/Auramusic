package az.simplesoft.aura.domain.music

import az.simplesoft.aura.data.providers.MusicSearchRequest
import java.util.Locale

/**
 * Converts natural-language music requests into provider-friendly queries and
 * ranking hints. The provider still supplies the candidates; this module only
 * makes the user's intent explicit and keeps search semantics deterministic.
 */
class MusicSearchBrain {
    fun interpret(request: MusicSearchRequest): MusicSearchRequest {
        val text = normalize(request.rawQuery)
        val profile = PROFILES.firstOrNull { profile ->
            profile.triggers.any { trigger -> text.contains(trigger) }
        } ?: return request

        val providerQuery = buildString {
            append(request.query)
            if (profile.providerTerms.isNotBlank()) {
                append(' ')
                append(profile.providerTerms)
            }
        }.trim()

        return request.copy(
            providerQuery = providerQuery,
            semanticTags = request.semanticTags + profile.semanticTags,
            excludedTerms = request.excludedTerms + profile.excludedTerms
        )
    }

    private fun normalize(value: String): String = value
        .lowercase(Locale.ROOT)
        .replace('ё', 'е')
        .replace(Regex("\\s+"), " ")
        .trim()

    private data class Profile(
        val triggers: Set<String>,
        val providerTerms: String,
        val semanticTags: Set<String>,
        val excludedTerms: Set<String> = emptySet()
    )

    companion object {
        private val PROFILES = listOf(
            // Keep lullaby separate from generic calm/chill: the user's example
            // must resolve to nursery/bedtime material, not a lo-fi mix.
            Profile(
                triggers = setOf(
                    "колыбельн", "баю", "усып", "песн для сна ребен", "детск песн на ночь",
                    "lullaby", "bedtime song", "nursery song", "sleep song", "baby sleep",
                    "layla", "laylay", "beşik mahnısı", "beşik nəğməsi", "uşaq yuxu mahnısı"
                ),
                providerTerms = "lullaby bedtime nursery baby sleep song",
                semanticTags = setOf("lullaby", "bedtime", "nursery", "sleep", "baby"),
                excludedTerms = setOf("chill", "lofi", "study", "ambient", "remix", "reaction")
            ),
            Profile(
                triggers = setOf("дожд", "rain sounds", "rain music", "yağış səsi", "yağış musiqisi"),
                providerTerms = "rain ambient nature sounds",
                semanticTags = setOf("rain", "nature", "ambient")
            ),
            Profile(
                triggers = setOf("для тренировки", "тренировоч", "workout", "gym music", "məşq musiqisi"),
                providerTerms = "workout gym energetic music",
                semanticTags = setOf("workout", "gym", "energy")
            ),
            Profile(
                triggers = setOf("для свадьб", "свадебн", "wedding music", "toy musiqisi"),
                providerTerms = "wedding music playlist",
                semanticTags = setOf("wedding", "celebration")
            ),
            Profile(
                triggers = setOf("без слов", "инструменталь", "instrumental", "no vocals", "sözsüz"),
                providerTerms = "instrumental no vocals",
                semanticTags = setOf("instrumental", "no-vocals")
            )
        )
    }
}
