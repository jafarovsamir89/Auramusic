package az.simplesoft.aura.assistant

import org.junit.Assert.assertEquals
import org.junit.Test

class LocalIntentEngineTest {
    private val engine = LocalIntentEngine()

    @Test
    fun recognizesPersonalMixBeforeGenericSearch() {
        assertEquals(MusicIntent.MyMix, engine.understand("Включи мой микс").intent)
    }

    @Test
    fun recognizesContinueListeningAsContextualAction() {
        assertEquals(
            MusicIntent.ContinueListening,
            engine.understand("Продолжить прослушивание").intent
        )
    }

    @Test
    fun keepsSimilarAsLocalMusicIntent() {
        assertEquals(MusicIntent.Similar, engine.understand("Найди похожее").intent)
    }

    @Test
    fun recognizesPlaylistLibraryAndQueueImport() {
        assertEquals(MusicIntent.OpenPlaylists, engine.understand("Открой плейлисты").intent)
        assertEquals(
            MusicIntent.CreatePlaylist("Дорога", includeQueue = true),
            engine.understand("Сохрани очередь в плейлист Дорога").intent
        )
    }

    @Test
    fun recognizesPlaylistPlaybackAndShuffle() {
        assertEquals(
            MusicIntent.PlayPlaylist("Дорога"),
            engine.understand("Включи плейлист Дорога").intent
        )
        assertEquals(
            MusicIntent.PlayPlaylist("Дорога", shuffled = true),
            engine.understand("Перемешай плейлист Дорога").intent
        )
    }
}
