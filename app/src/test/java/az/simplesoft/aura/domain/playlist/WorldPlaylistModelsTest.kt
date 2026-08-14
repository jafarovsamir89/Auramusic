package az.simplesoft.aura.domain.playlist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WorldPlaylistModelsTest {
    private val catalog = WorldPlaylistCatalog(
        version = 1,
        generatedAt = "test",
        playlists = listOf(
            WorldPlaylist(
                id = "top-week",
                title = "Топ недели",
                description = "",
                region = "world",
                language = "multi",
                kind = WorldPlaylistKind.TOP_WEEK,
                source = "test",
                updatedAt = "test",
                items = listOf(WorldPlaylistItem(1, "Artist", "Song"))
            )
        )
    )

    @Test fun findMatchesIdAndTitle() {
        assertEquals("top-week", catalog.find("TOP-WEEK")?.id)
        assertEquals("top-week", catalog.find("топ")?.id)
    }

    @Test fun blankQueryDoesNotSelectAPlaylist() {
        assertNull(catalog.find(" "))
    }

    @Test fun sanitizedDropsMalformedAndDuplicateEntries() {
        val dirty = catalog.copy(
            playlists = listOf(
                catalog.playlists.first(),
                catalog.playlists.first(),
                WorldPlaylist("", "", "", "", "", WorldPlaylistKind.EDITORIAL, "", "", emptyList())
            )
        )
        val clean = dirty.sanitized()
        assertEquals(1, clean.playlists.size)
        assertEquals(1, clean.playlists.single().items.size)
    }
}
