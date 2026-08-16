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
                triggers = setOf("для сна", "уснуть", "сон", "sleep music", "music for sleep", "yuxu musiqisi"),
                providerTerms = "sleep soft piano bedtime music",
                semanticTags = setOf("sleep", "bedtime", "soft", "piano"),
                excludedTerms = setOf("workout", "hardstyle", "party", "remix")
            ),
            Profile(
                triggers = setOf("спокойн", "расслаб", "релакс", "calm music", "relaxing", "sakit musiqi"),
                providerTerms = "calm relaxing soft music",
                semanticTags = setOf("calm", "relaxing", "soft"),
                excludedTerms = setOf("hardstyle", "screamo", "reaction")
            ),
            Profile(
                triggers = setOf("грустн", "печаль", "sad music", "melancholy", "kədərli musiqi"),
                providerTerms = "melancholic acoustic piano sad song",
                semanticTags = setOf("sad", "melancholy", "acoustic", "piano"),
                excludedTerms = setOf("happy", "party", "workout", "comedy")
            ),
            Profile(
                triggers = setOf("для концентрации", "фокус", "focus music", "study music", "diqqət musiqisi"),
                providerTerms = "focus study instrumental music",
                semanticTags = setOf("focus", "study", "instrumental"),
                excludedTerms = setOf("lyrics", "party", "reaction")
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
            ),
            Profile(
                triggers = setOf("романтич", "любовн", "для двоих", "romantic", "love songs", "sevgı mahnıları", "sevgi mahnıları"),
                providerTerms = "romantic love songs playlist",
                semanticTags = setOf("romantic", "love"),
                excludedTerms = setOf("breakup", "angry", "hardstyle")
            ),
            Profile(
                triggers = setOf("для вечеринки", "танцевальн", "party music", "dance music", "rəqs musiqisi", "parti musiqisi"),
                providerTerms = "party dance hits playlist",
                semanticTags = setOf("party", "dance", "energetic"),
                excludedTerms = setOf("sleep", "lullaby", "ambient")
            ),
            Profile(
                triggers = setOf("песни 90", "музыка 90", "90-х", "90s music", "90s hits", "90-cı illər"),
                providerTerms = "90s hits playlist",
                semanticTags = setOf("decade-90s", "hits"),
                excludedTerms = setOf("2026", "new release")
            ),
            Profile(
                triggers = setOf("азербайджанск", "азербайджанская музыка", "azerbaijani music", "azərbaycan musiqisi"),
                providerTerms = "Azerbaijani music hits",
                semanticTags = setOf("region-az", "azerbaijani")
            ),
            Profile(
                triggers = setOf("турецк", "турецкая музыка", "turkish music", "türk musiqisi"),
                providerTerms = "Turkish music hits",
                semanticTags = setOf("region-tr", "turkish")
            )
        )
    }
}
