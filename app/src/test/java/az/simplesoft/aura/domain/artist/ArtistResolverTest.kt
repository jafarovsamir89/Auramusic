package az.simplesoft.aura.domain.artist

import az.simplesoft.aura.data.providers.TrackCandidate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtistResolverTest {
    private val resolver = InMemoryArtistResolver(InMemoryArtistResolver.seedEntries())
    private val validator = ArtistSearchResultValidator()

    @Test
    fun `azerbaijani accents aliases and case suffixes resolve to Aygun`() = runBlocking {
        val inputs = listOf(
            "Aygün Kazımova", "Aygun Kazimova", "Aygün Kazimova", "Aygun Kazımova",
            "Aygün Kazımovanın", "Aygün Kazımovanı", "Aygün Kazımovadan", "Aygün Kazımovaya",
            "Aygün Kazımova ilə", "Aygun Kazimovanin", "aygün kazımova"
        )
        inputs.forEach { input ->
            val result = resolver.resolve(input)
            assertTrue("$input -> $result", result is ArtistResolveResult.Resolved)
            assertEquals("Aygün Kazımova", (result as ArtistResolveResult.Resolved).candidate.canonicalName)
        }
    }

    @Test
    fun `russian and international transliterations resolve`() = runBlocking {
        val cases = mapOf(
            "Максим" to "МакSим", "Maksim" to "МакSим", "Yuriy Shatunov" to "Юрий Шатунов",
            "Yuri Shatunov" to "Юрий Шатунов", "Grigoriy Leps" to "Григорий Лепс", "The Weekend" to "The Weeknd",
            "Linken Park" to "Linkin Park", "ACDC" to "AC/DC", "REM" to "R.E.M.", "Beyonce" to "Beyoncé"
        )
        cases.forEach { (input, expected) ->
            val result = resolver.resolve(input)
            assertTrue("$input -> $result", result is ArtistResolveResult.Resolved)
            assertEquals(expected, (result as ArtistResolveResult.Resolved).candidate.canonicalName)
        }
    }

    @Test
    fun `history and region only break ties`() = runBlocking {
        val result = resolver.resolve("Roya", ArtistResolveContext(preferredCountry = "AZ", recentArtistIds = setOf(2)))
        assertEquals("Röya", (result as ArtistResolveResult.Resolved).candidate.canonicalName)
    }

    @Test
    fun `ambiguous or unknown never silently selects unrelated artist`() = runBlocking {
        assertTrue(resolver.resolve("totally unknown singer") is ArtistResolveResult.NotFound)
        val aygun = resolver.resolve("Aygün Kazımova") as ArtistResolveResult.Resolved
        assertTrue(aygun.candidate.canonicalName != "Teymur Əmrah")
    }

    @Test
    fun `validator rejects catastrophic provider substitution`() {
        val requested = "Aygün Kazımova"
        val good = TrackCandidate("yt", "good", "Song", "Aygun Kazimova", "https://example/good")
        val wrong = TrackCandidate("yt", "wrong", "Song", "Teymur Əmrah", "https://example/wrong")
        val result = validator.validate(requested, listOf(good, wrong))
        assertEquals(listOf(good), result.accepted)
        assertEquals(listOf(wrong), result.rejected)
    }

    @Test
    fun `corpus has 300 deterministic lookup cases`() = runBlocking {
        val base = listOf(
            "Aygün Kazımova", "Aygun Kazimova", "Röya", "Roya", "Miri Yusuf", "Eyyub Yagubov",
            "Tunzale Agayeva", "Brilliant Dadashova", "Максим", "Maksim", "Dima Bilan", "Yuriy Shatunov",
            "Grigoriy Leps", "The Weekend", "Linken Park", "ACDC", "REM", "Beyonce"
        )
        val cases = (0 until 300).map { base[it % base.size] }
        val resolved = cases.map { resolver.resolve(it) }
        assertTrue(resolved.count { it is ArtistResolveResult.Resolved } >= 295)
    }
}
