package az.simplesoft.aura.assistant

import org.junit.Assert.assertEquals
import org.junit.Test

class LocalIntentEngineTest {
    private val engine = LocalIntentEngine()

    @Test
    fun `opens country radio catalog`() {
        assertEquals(MusicIntent.OpenRadio, engine.understand("радио").intent)
    }

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

    @Test
    fun recognizesQueueManagementCommandsBeforeGenericPlayback() {
        assertEquals(
            MusicIntent.QueueTrack("numb linkin park", playNext = true),
            engine.understand("Поставь следующим numb linkin park").intent
        )
        assertEquals(
            MusicIntent.QueueTrack("numb linkin park", playNext = false),
            engine.understand("Добавь numb linkin park в очередь").intent
        )
        assertEquals(MusicIntent.OpenQueue, engine.understand("Открой очередь").intent)
        assertEquals(MusicIntent.ClearQueue, engine.understand("Очисти очередь").intent)
        assertEquals(MusicIntent.AutoContinue(false), engine.understand("Выключи автопродолжение").intent)
    }
}
