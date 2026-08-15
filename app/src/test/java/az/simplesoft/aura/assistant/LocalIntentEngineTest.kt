package az.simplesoft.aura.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalIntentEngineTest {
    private val engine = LocalIntentEngine()

    @Test
    fun `opens country radio catalog`() {
        assertEquals(MusicIntent.OpenRadio, engine.understand("радио").intent)
        assertEquals(MusicIntent.OpenRadio, engine.understand("включи радио").intent)
        assertEquals(MusicIntent.OpenRadio, engine.understand("запусти радиостанции").intent)
    }

    @Test
    fun recognizesPersonalMixBeforeGenericSearch() {
        assertEquals(MusicIntent.MyMix, engine.understand("Включи мой микс").intent)
    }

    @Test
    fun recognizesNaturalPlaybackAliases() {
        assertEquals(MusicIntent.Pause, engine.understand("Останови музыку").intent)
        assertEquals(MusicIntent.Play, engine.understand("Сними с паузы").intent)
        assertEquals(MusicIntent.Next, engine.understand("Дальше").intent)
        assertEquals(MusicIntent.Previous, engine.understand("Верни предыдущий").intent)
        assertEquals(MusicIntent.NowPlaying, engine.understand("Что за трек").intent)
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
    fun recognizesOfflineLibraryCommands() {
        assertEquals(MusicIntent.DownloadCurrent, engine.understand("Скачай эту песню").intent)
        assertEquals(MusicIntent.DownloadQueue, engine.understand("Скачай очередь").intent)
        assertEquals(MusicIntent.DownloadQueue, engine.understand("download all queued songs").intent)
        assertEquals(MusicIntent.PlayOfflineMusic, engine.understand("Включи скачанную музыку").intent)
        assertEquals(MusicIntent.OpenLocalLibrary, engine.understand("Открой музыку на телефоне").intent)
        assertEquals(MusicIntent.DeleteOfflineCurrent, engine.understand("Удали скачанную песню").intent)
        assertEquals(MusicIntent.OfflineStatus, engine.understand("Сколько песен скачано").intent)
        assertEquals(MusicIntent.DeleteAllOffline, engine.understand("Очисти офлайн библиотеку").intent)
        assertEquals(MusicIntent.SearchOffline("bts"), engine.understand("Найди среди скачанных песен bts").intent)
    }

    @Test
    fun recognizesWorldPlaylistShortcuts() {
        assertEquals(MusicIntent.PlayWorldPlaylist("top-week-world"), engine.understand("Топ недели").intent)
        assertEquals(MusicIntent.PlayWorldPlaylist("hits-90s"), engine.understand("Хиты 90-х").intent)
        assertEquals(MusicIntent.PlayWorldPlaylist("new-this-week"), engine.understand("new this week").intent)
    }

    @Test
    fun recognizesPermanentEditorialPlaylists() {
        assertEquals(MusicIntent.PlayWorldPlaylist("artist-50cent"), engine.understand("включи хиты 50 cent").intent)
        assertEquals(MusicIntent.PlayWorldPlaylist("top-week-world"), engine.understand("поставь топ недели").intent)
        assertEquals(MusicIntent.PlayWorldPlaylist("artist-ruki-vverh"), engine.understand("поставь лучшее Руки Вверх").intent)
        assertEquals(MusicIntent.PlayWorldPlaylist("aura-calm-night"), engine.understand("включи спокойный вечер").intent)
    }

    @Test
    fun recognizesNaturalCollectionAndGenreRequests() {
        assertEquals(MusicIntent.PlayWorldPlaylist("90-х"), engine.understand("Включи подборку песен из 90-х").intent)
        assertEquals(MusicIntent.PlayWorldPlaylist("рок"), engine.understand("Включи жанр рок").intent)
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

    @Test
    fun `greeting is conversation and never music search`() {
        val result = engine.understand("Привет")

        assertEquals(MusicIntent.Unknown, result.intent)
        assertEquals(AssistantRoute.LOCAL_CONVERSATION, result.route)
        assertEquals(AssistantLanguage.RUSSIAN, result.language)
    }

    @Test
    fun `unknown question stays a local conversation instead of falling back to search`() {
        val result = engine.understand("Какой сегодня день?")

        assertEquals(MusicIntent.Unknown, result.intent)
        assertEquals(AssistantRoute.LOCAL_CONVERSATION, result.route)
    }

    @Test
    fun `explicit artist request becomes playable search`() {
        val result = engine.understand("Поставь Руки Вверх")

        assertEquals(MusicIntent.Search("руки вверх"), result.intent)
        assertEquals(AssistantRoute.LOCAL_ACTION, result.route)
    }

    @Test
    fun `polite word is not passed into music search`() {
        val result = engine.understand("Пожалуйста поставь Руки Вверх")

        assertEquals(MusicIntent.Search("руки вверх"), result.intent)
    }

    @Test
    fun `artist and sad mood stay together in music request`() {
        val result = engine.understand("Найди грустную песню МакSим")
        val search = result.intent as MusicIntent.Search

        assertEquals(Mood.SAD, search.mood)
        assertTrue(search.query.contains("макsим"))
        assertTrue(search.query.contains("груст"))
    }

    @Test
    fun `lullaby is not reduced to generic calm music`() {
        val search = engine.understand("Поставь настоящую колыбельную для ребёнка").intent as MusicIntent.Search

        assertEquals(Mood.LULLABY, search.mood)
        assertTrue(search.query.contains("колыбельн"))
    }

    @Test
    fun `azerbaijani and english lullaby phrases are understood`() {
        assertEquals(Mood.LULLABY, (engine.understand("qoş uşaq yuxu mahnısı").intent as MusicIntent.Search).mood)
        assertEquals(Mood.LULLABY, (engine.understand("play a lullaby").intent as MusicIntent.Search).mood)
    }

    @Test
    fun recognizes_feedback_and_timer_commands() {
        assertEquals(MusicIntent.MoreLikeThis, engine.understand("Больше такого").intent)
        assertEquals(MusicIntent.NotThis, engine.understand("Не это").intent)
        assertEquals(MusicIntent.SleepTimer(30), engine.understand("Таймер сна 30 минут").intent)
        assertEquals(MusicIntent.CancelSleepTimer, engine.understand("cancel sleep timer").intent)
        assertEquals(MusicIntent.StopAfterTrack, engine.understand("stop after this song").intent)
        assertEquals(MusicIntent.RemoveLastFromQueue, engine.understand("remove the last song").intent)
        assertEquals(MusicIntent.SetVolume(40), engine.understand("громкость 40 процентов").intent)
        assertEquals(MusicIntent.ClearMemory, engine.understand("forget everything about me").intent)
        assertEquals(MusicIntent.MyMix, engine.understand("Поставить микс песен").intent)
        assertEquals(MusicIntent.Louder, engine.understand("Увеличить громкость").intent)
        assertEquals(MusicIntent.Next, engine.understand("Следующий канал").intent)
        assertEquals(MusicIntent.Previous, engine.understand("Предыдущий канал").intent)
        assertEquals(MusicIntent.SetEqualizer(EqualizerPreset.ROCK), engine.understand("Эквалайзер рок").intent)
        assertEquals(MusicIntent.DisableEqualizer, engine.understand("Выключи эквалайзер").intent)
    }

    @Test
    fun recognizes_compound_commands() {
        val intent = engine.understand("включи радио и сделай тише").intent

        assertEquals(
            MusicIntent.Composite(listOf(MusicIntent.OpenRadio, MusicIntent.Quieter)),
            intent
        )
    }

    @Test
    fun `azerbaijani and english conversations do not search`() {
        val az = engine.understand("Salam")
        val en = engine.understand("How are you?")

        assertEquals(AssistantLanguage.AZERBAIJANI, az.language)
        assertEquals(AssistantRoute.LOCAL_CONVERSATION, az.route)
        assertEquals(AssistantLanguage.ENGLISH, en.language)
        assertEquals(AssistantRoute.LOCAL_CONVERSATION, en.route)
    }
}
