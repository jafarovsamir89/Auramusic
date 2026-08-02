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
}
