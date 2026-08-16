package az.simplesoft.aura.ui

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.core.content.edit
import az.simplesoft.aura.BuildConfig
import az.simplesoft.aura.assistant.AssistantMessage
import az.simplesoft.aura.assistant.AssistantReply
import az.simplesoft.aura.assistant.AssistantRole
import az.simplesoft.aura.assistant.AssistantSource
import az.simplesoft.aura.assistant.AssistantTrackContext
import az.simplesoft.aura.assistant.AuraAiContext
import az.simplesoft.aura.assistant.AuraAiEngine
import az.simplesoft.aura.assistant.AuraSpeechSynthesizer
import az.simplesoft.aura.assistant.VoiceStyle
import az.simplesoft.aura.assistant.SpeechResponsePolicy
import az.simplesoft.aura.assistant.ResponseVerbosity
import az.simplesoft.aura.assistant.RecognitionBackend
import az.simplesoft.aura.assistant.VoiceCaptureDiagnostics
import az.simplesoft.aura.assistant.VoiceInputState
import az.simplesoft.aura.assistant.VoiceEngineMode
import az.simplesoft.aura.assistant.CompactAssistantMemory
import az.simplesoft.aura.assistant.AssistantMemoryFact
import az.simplesoft.aura.assistant.AssistantCommandCoordinator
import az.simplesoft.aura.assistant.ActionExecutionResult
import az.simplesoft.aura.assistant.gemini.GeminiAudioInput
import az.simplesoft.aura.assistant.gemini.GeminiAudioOutput
import az.simplesoft.aura.assistant.gemini.GeminiAuthProvider
import az.simplesoft.aura.assistant.gemini.GeminiDiagnostics
import az.simplesoft.aura.assistant.gemini.GeminiLiveSession
import az.simplesoft.aura.assistant.gemini.LocalDebugApiKeyProvider
import az.simplesoft.aura.assistant.gemini.GeminiSessionState
import az.simplesoft.aura.assistant.gemini.GeminiToolExecutor
import az.simplesoft.aura.assistant.gemini.GeminiToolResult
import az.simplesoft.aura.assistant.gemini.GeminiUsageStore
import az.simplesoft.aura.assistant.LocalIntentEngine
import az.simplesoft.aura.assistant.MusicIntent
import az.simplesoft.aura.assistant.Mood
import az.simplesoft.aura.assistant.EqualizerPreset
import az.simplesoft.aura.assistant.RoomAssistantMemoryPersistence
import az.simplesoft.aura.data.DemoCatalog
import az.simplesoft.aura.data.LocalMusicProvider
import az.simplesoft.aura.data.OfflineTrackStore
import az.simplesoft.aura.data.ArtistArtworkLookup
import az.simplesoft.aura.data.PlaybackType
import az.simplesoft.aura.data.RadioBrowserProvider
import az.simplesoft.aura.data.RadioCountry
import az.simplesoft.aura.data.plugins.muzofond.MuzofondCollectionsClient
import az.simplesoft.aura.data.Track
import az.simplesoft.aura.data.database.AuraPlaybackSnapshot
import az.simplesoft.aura.data.database.AuraPlaylist
import az.simplesoft.aura.data.database.AuraQueueSnapshot
import az.simplesoft.aura.data.database.AuraStateRepository
import az.simplesoft.aura.data.database.RecommendationEventType
import az.simplesoft.aura.data.plugins.core.PluginFailureReason as CoreFailureReason
import az.simplesoft.aura.data.plugins.core.PluginResult
import az.simplesoft.aura.data.plugins.core.ProviderManager
import az.simplesoft.aura.data.plugins.local.LocalMusicPlugin
import az.simplesoft.aura.data.plugins.muzofond.MuzofondMusicPlugin
import az.simplesoft.aura.data.plugins.radio.RadioMusicPlugin
import az.simplesoft.aura.data.plugins.vol.VolMusicPlugin
import az.simplesoft.aura.data.plugins.vol.VolCollectionsClient
import az.simplesoft.aura.data.plugins.youtube.YouTubeMusicPlugin
import az.simplesoft.aura.data.providers.MusicSearchRequest
import az.simplesoft.aura.data.providers.PlayableSource
import az.simplesoft.aura.data.providers.ProviderResult
import az.simplesoft.aura.data.providers.ProviderFailureReason
import az.simplesoft.aura.data.providers.TrackCandidate
import az.simplesoft.aura.data.search.CandidateRankerV2
import az.simplesoft.aura.data.search.TrackIdentityResolver
import az.simplesoft.aura.data.search.UnifiedTrackSession
import az.simplesoft.aura.data.artistindex.CompositeArtistResolver
import az.simplesoft.aura.domain.artist.ArtistResolveContext
import az.simplesoft.aura.domain.artist.ArtistResolveResult
import az.simplesoft.aura.domain.artist.ArtistSearchResultValidator
import az.simplesoft.aura.domain.music.MusicBrain
import az.simplesoft.aura.domain.music.AuraRepeatMode
import az.simplesoft.aura.domain.music.PersonalRecommendationEngine
import az.simplesoft.aura.domain.music.RadioPlaybackSelector
import az.simplesoft.aura.domain.music.VolumeMath
import az.simplesoft.aura.domain.music.QueueEditResult
import az.simplesoft.aura.domain.music.QueueEditor
import az.simplesoft.aura.domain.music.RecommendationContext
import az.simplesoft.aura.domain.music.SearchOutcome
import az.simplesoft.aura.domain.playlist.WorldPlaylist
import az.simplesoft.aura.domain.playlist.WorldPlaylistCandidateMatcher
import az.simplesoft.aura.playback.PlaybackConnection
import az.simplesoft.aura.playback.PlaybackConnectionCoordinator
import az.simplesoft.aura.voice.AuraVoiceForegroundService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.flow.first
import java.util.Calendar
import kotlin.math.abs
import org.json.JSONObject

enum class AuraDestination { HOME, SEARCH, RADIO, LIBRARY, ASSISTANT, SETTINGS, GEMINI, DIAGNOSTICS, COLLECTIONS, GENRES }
enum class LibrarySection { FAVORITES, HISTORY, LOCAL, PLAYLISTS, WORLD_PLAYLISTS }
enum class SearchPhase { IDLE, SEARCHING, MATCHING, RESOLVING, BUFFERING, PLAYING, ERROR }

enum class MusicSourceMode(val title: String, val description: String) {
    MUZOFOND("Популярный стабильный", "Быстрый стабильный каталог"),
    YOUTUBE("Глобальная библиотека", "Большой каталог для поиска и fallback"),
    BOTH("Оба источника", "Стабильный каталог и глобальная библиотека")
}

private fun providerDisplayName(id: String): String = when (id) {
    MuzofondMusicPlugin.ID -> "Популярный стабильный"
    VolMusicPlugin.ID -> "Популярный стабильный"
    YouTubeMusicPlugin.ID -> "Глобальная библиотека"
    else -> id
}

data class ProviderDiagnostics(
    val engine: String = "Plugin Core · три онлайн-источника",
    val selectedProvider: String = "—",
    val query: String = "—",
    val candidates: List<String> = emptyList(),
    val selectedPage: String = "—",
    val resolver: String = "—",
    val searchTimeMs: Long? = null,
    val resolveTimeMs: Long? = null,
    val mimeType: String = "—",
    val expiresAt: Long? = null,
    val validationStatus: String = "—",
    val fallbackReason: String = "—",
    val artistResolver: String = "—"
)

data class AuraUiState(
    val destination: AuraDestination = AuraDestination.HOME,
    val librarySection: LibrarySection = LibrarySection.FAVORITES,
    val query: String = "",
    val recentSearches: List<String> = emptyList(),
    val searchResults: List<Track> = emptyList(),
    val searchResultLimit: Int = SearchPaging.INITIAL_LIMIT,
    val isLoadingMore: Boolean = false,
    val canLoadMore: Boolean = false,
    val radioCountries: List<RadioCountry> = emptyList(),
    val radioStations: List<Track> = emptyList(),
    val selectedRadioCountry: RadioCountry? = null,
    val isRadioLoading: Boolean = false,
    val radioError: String? = null,
    val personalMix: List<Track> = emptyList(),
    val playlists: List<AuraPlaylist> = emptyList(),
    val worldPlaylists: List<WorldPlaylist> = emptyList(),
    val worldGenres: List<WorldPlaylist> = emptyList(),
    val likedWorldPlaylistIds: Set<String> = emptySet(),
    val activeWorldPlaylistTitle: String? = null,
    val queueHistory: List<AuraQueueSnapshot> = emptyList(),
    val selectedPlaylistId: String? = null,
    val assistantText: String = "Привет. Я AURA.",
    val assistantMessages: List<AssistantMessage> = emptyList(),
    val memoryFacts: List<AssistantMemoryFact> = emptyList(),
    val isAssistantThinking: Boolean = false,
    val assistantSource: AssistantSource = AssistantSource.LOCAL,
    val isOfflineOnly: Boolean = true,
    val geminiConfigured: Boolean = false,
    val geminiDiagnostics: GeminiDiagnostics = GeminiDiagnostics(),
    val onboardingComplete: Boolean = false,
    val userName: String? = null,
    val preferredLanguage: String = "ru",
    val musicSourceMode: MusicSourceMode = MusicSourceMode.BOTH,
    val assistantDiagnostics: az.simplesoft.aura.assistant.DecisionDiagnostics? = null,
    val voiceEngineMode: VoiceEngineMode = VoiceEngineMode.VERIFIED_SILERO,
    val isListening: Boolean = false,
    /** True while the conversational voice window is armed, including its 4 s idle grace period. */
    val isVoiceSessionActive: Boolean = false,
    val voiceInputState: VoiceInputState = VoiceInputState.Idle,
    val recognitionBackend: RecognitionBackend = RecognitionBackend.Unavailable,
    val voiceDiagnostics: VoiceCaptureDiagnostics? = null,
    val isLoading: Boolean = false,
    val isBuffering: Boolean = false,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val playbackDurationMs: Long = 0L,
    val isCarMode: Boolean = false,
    val isPlayerExpanded: Boolean = false,
    val isQueueOpen: Boolean = false,
    val isShuffleEnabled: Boolean = false,
    val repeatMode: AuraRepeatMode = AuraRepeatMode.OFF,
    val isRecommendationLoading: Boolean = false,
    val autoContinueEnabled: Boolean = true,
    val equalizerPreset: EqualizerPreset = EqualizerPreset.FLAT,
    val equalizerBands: List<Float> = EqualizerPreset.FLAT.defaultBands.toList(),
    val offlineSort: OfflineSort = OfflineSort.RECENT,
    val offlineStorageBytes: Long = 0L,
    val downloadedSourceTrackIds: Set<String> = emptySet(),
    val offlineDownloadProgress: Float? = null,
    val offlineDownloadTrackId: String? = null,
    val offlineDownloadBytes: Long = 0L,
    val offlineDownloadTotalBytes: Long = -1L,
    val offlineSearchQuery: String = "",
    val sleepTimerEndsAt: Long? = null,
    val stopAfterTrack: Boolean = false,
    val searchPhase: SearchPhase = SearchPhase.IDLE,
    val diagnostics: ProviderDiagnostics = ProviderDiagnostics(),
    val queue: List<Track> = DemoCatalog.tracks,
    val memoryTracks: List<Track> = emptyList(),
    val localTracks: List<Track> = emptyList(),
    val currentIndex: Int = 0,
    val likedIds: Set<String> = emptySet(),
    val historyIds: List<String> = emptyList(),
    val skippedTrackIds: Set<String> = emptySet()
) {
    val nowTrack: Track get() = queue.getOrElse(currentIndex) { DemoCatalog.tracks.first() }
    val liked: Boolean get() = nowTrack.id in likedIds
    private val knownTracks: Map<String, Track> get() = (memoryTracks + queue).associateBy(Track::id)
    val favorites: List<Track> get() = likedIds.mapNotNull(knownTracks::get)
    val history: List<Track> get() = historyIds.mapNotNull(knownTracks::get)
    val selectedPlaylist: AuraPlaylist? get() = playlists.firstOrNull { it.id == selectedPlaylistId }
    val isRepeatEnabled: Boolean get() = repeatMode != AuraRepeatMode.OFF
}

enum class OfflineSort { RECENT, TITLE, ARTIST }

class AuraViewModel(application: Application) : AndroidViewModel(application), PlaybackConnection.Listener {
    private val intentEngine = LocalIntentEngine()
    private val localProvider = LocalMusicProvider(application)
    private val offlineStore = OfflineTrackStore(application)
    private val radioProvider = RadioBrowserProvider()
    private val muzofondCollections = MuzofondCollectionsClient()
    private val volCollections = VolCollectionsClient()
    private var worldPlaylistJob: Job? = null
    private val preferences = application.getSharedPreferences("aura_state", Context.MODE_PRIVATE)
    private val stateRepository = AuraStateRepository(application)
    private val artistResolver = CompositeArtistResolver(application)
    private val artistSearchValidator = ArtistSearchResultValidator()
    private val assistantMemory = CompactAssistantMemory(RoomAssistantMemoryPersistence(stateRepository))
    private val assistantCommandCoordinator = AssistantCommandCoordinator(application)
    private val auraAi = AuraAiEngine(
        local = intentEngine,
        memory = assistantMemory
    )
    private val speech = AuraSpeechSynthesizer(application)
    private val audio = application.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val geminiAudioOutput = GeminiAudioOutput(application)
    private val geminiAudioInput = GeminiAudioInput(application)
    private val geminiUsageStore = GeminiUsageStore(application)
    @Volatile private var geminiUserContext: String = ""
    private var lastGeminiUserText = ""
    private var geminiMemoryJob: Job? = null
    private val geminiSession: GeminiLiveSession by lazy {
        GeminiLiveSession(
        authProvider = LocalDebugApiKeyProvider(),
        toolExecutor = GeminiToolExecutor { name, args -> executeGeminiTool(name, args) },
        audioOutput = geminiAudioOutput,
        scope = viewModelScope,
        userContextProvider = { geminiUserContext },
        usageStore = geminiUsageStore,
        onInputTranscription = ::onGeminiInputTranscription,
        onOutputTranscription = ::onGeminiOutputTranscription,
        onError = { message ->
            geminiAudioInput.stop()
            stopVoiceForegroundService()
            _state.update {
                it.copy(
                    assistantText = message,
                    isListening = false,
                    isVoiceSessionActive = false,
                    isAssistantThinking = false,
                    voiceInputState = VoiceInputState.Failed(message)
                )
            }
        }
        )
    }
    private val playback = PlaybackConnection(application, this)
    private val providerManager = ProviderManager(
        setOf(
            LocalMusicPlugin(localProvider),
            MuzofondMusicPlugin(),
            VolMusicPlugin(),
            YouTubeMusicPlugin(),
            RadioMusicPlugin(radioProvider)
        )
    )
    private val candidateRanker = CandidateRankerV2()
    private val identityResolver = TrackIdentityResolver()
    private val recommendationEngine = PersonalRecommendationEngine(
        providerManager = providerManager,
        candidateRanker = candidateRanker,
        identityResolver = identityResolver
    )
    private val artworkLookup = ArtistArtworkLookup()
    private val playbackCoordinator = PlaybackConnectionCoordinator(playback) { state.value.queue }
    private val musicBrain = MusicBrain(
        providerManager = providerManager,
        candidateRanker = candidateRanker,
        identityResolver = identityResolver,
        recommendationEngine = recommendationEngine,
        playbackCoordinator = playbackCoordinator,
        preferredProviderIds = setOf(MuzofondMusicPlugin.ID)
    )
    private val unifiedTrackSession = UnifiedTrackSession()
    private val candidatesByTrackId = mutableMapOf<String, TrackCandidate>()
    private val playbackRecoveryAttempts = mutableMapOf<String, Int>()
    private var latestCandidates: List<TrackCandidate> = emptyList()
    private var lastSearchRequest: MusicSearchRequest? = null
    private var searchJob: Job? = null
    private var searchGeneration = 0L
    private var hasPreparedMedia = false
    private val persistenceQueue = Channel<AuraPlaybackSnapshot>(Channel.CONFLATED)
    private var positionPersistenceTick = 0
    private var isExtendingQueue = false
    private var stateRestored = false
    private var pendingCommand: String? = null
    private var pendingCommandShouldSpeak = false
    private val claimedPendingCommands = mutableSetOf<String>()
    private var offlineDownloadJob: Job? = null

    private val initialLiked = preferences.getStringSet("liked", emptySet()).orEmpty().toSet()
    private val initialHistory = preferences.getString("history", "")
        .orEmpty().split('|').filter(String::isNotBlank)
    private val initialOnboardingComplete = preferences.getBoolean("onboarding_complete", false)
    private val initialUserName = preferences.getString("user_name", null)?.takeIf(String::isNotBlank)
    private val initialPreferredLanguage = preferences.getString("preferred_language", "ru").orEmpty().ifBlank { "ru" }
    private val initialMusicSourceMode = runCatching {
        MusicSourceMode.valueOf(preferences.getString("music_source_mode", MusicSourceMode.BOTH.name).orEmpty())
    }.getOrDefault(MusicSourceMode.BOTH)
    private val initialVoiceEngineMode = runCatching {
        VoiceEngineMode.valueOf(preferences.getString("voice_engine_mode", VoiceEngineMode.VERIFIED_SILERO.name).orEmpty())
    }.getOrDefault(VoiceEngineMode.VERIFIED_SILERO).let {
        if (it == VoiceEngineMode.SYSTEM) VoiceEngineMode.VERIFIED_SILERO else it
    }
    private val initialEqualizerPreset = EqualizerPreset.fromText(
        preferences.getString("equalizer_preset", EqualizerPreset.FLAT.name).orEmpty()
    ) ?: EqualizerPreset.FLAT
    private val initialEqualizerBands = preferences.getString("equalizer_bands", null)
        ?.split(',')?.mapNotNull { it.toFloatOrNull() }?.takeIf { it.size == 5 }
        ?: initialEqualizerPreset.defaultBands.toList()
    private val initialLikedWorldPlaylists = preferences.getStringSet("liked_world_playlists", emptySet()).orEmpty().toSet()

    private val _state = MutableStateFlow(
        AuraUiState(
            likedIds = initialLiked,
            historyIds = initialHistory,
            onboardingComplete = initialOnboardingComplete,
            userName = initialUserName,
            preferredLanguage = initialPreferredLanguage,
            musicSourceMode = initialMusicSourceMode,
            isOfflineOnly = BuildConfig.GEMINI_API_KEY.isBlank(),
            geminiConfigured = BuildConfig.GEMINI_API_KEY.isNotBlank(),
            geminiDiagnostics = geminiSession.diagnostics.value,
            voiceEngineMode = initialVoiceEngineMode,
            equalizerPreset = initialEqualizerPreset,
            equalizerBands = initialEqualizerBands,
            likedWorldPlaylistIds = initialLikedWorldPlaylists
        )
    )
    val state = _state.asStateFlow()

    init {
        configureMusicSources(initialMusicSourceMode)
        speech.setEngineMode(initialVoiceEngineMode)
        if (initialEqualizerPreset == EqualizerPreset.CUSTOM) {
            playback.setEqualizerBands(initialEqualizerBands)
        } else {
            playback.setEqualizer(initialEqualizerPreset)
        }
        refreshWorldPlaylists()
        viewModelScope.launch {
            val memory = assistantMemory.snapshot()
            geminiUserContext = memory.promptSummary()
            _state.update { it.copy(memoryFacts = memory.facts) }
        }
        viewModelScope.launch {
            geminiSession.diagnostics.collect { diagnostics ->
                _state.update { it.copy(geminiDiagnostics = diagnostics) }
            }
        }
        viewModelScope.launch {
            runCatching {
                stateRepository.importLegacy(
                    initialLiked,
                    initialHistory,
                    preferences.getInt("current_index", 0)
                )
                val restored = stateRepository.load()
                val skippedTrackIds = stateRepository.skippedTrackIds()
                val playlists = stateRepository.loadPlaylists()
                val queueHistory = stateRepository.loadQueueHistory()
                val assistantMessages = auraAi.memorySnapshot().recentMessages
                _state.update { current ->
                    val chartIsBuilding = worldPlaylistJob?.isActive == true
                    current.copy(
                        queue = if (chartIsBuilding) current.queue else restored.queue.ifEmpty { current.queue },
                        memoryTracks = restored.memoryTracks,
                        currentIndex = if (chartIsBuilding || restored.queue.isEmpty()) current.currentIndex else restored.currentIndex,
                        positionMs = if (chartIsBuilding) current.positionMs else restored.positionMs,
                        playbackDurationMs = if (chartIsBuilding) current.playbackDurationMs else restored.queue.getOrNull(restored.currentIndex)?.durationMs ?: 0L,
                        isShuffleEnabled = restored.shuffleEnabled,
                        repeatMode = restored.repeatMode,
                        autoContinueEnabled = restored.autoContinueEnabled,
                        likedIds = restored.likedIds,
                        historyIds = restored.historyIds,
                        recentSearches = restored.recentSearches,
                        skippedTrackIds = skippedTrackIds,
                        playlists = playlists,
                        queueHistory = queueHistory,
                        assistantMessages = assistantMessages
                    )
                }
                playback.setShuffle(restored.shuffleEnabled)
                playback.setRepeat(restored.repeatMode)
                restored.queue.getOrNull(restored.currentIndex)?.let { selected ->
                    playback.restore(restored.queue, selected, restored.positionMs)
                    hasPreparedMedia = true
                }
                preferences.edit {
                    remove("liked")
                    remove("history")
                    remove("current_index")
                    putBoolean("room_migration_complete", true)
                }
            }.onFailure { error ->
                _state.update { current ->
                    current.copy(
                        diagnostics = current.diagnostics.copy(
                            fallbackReason = "Room: ${error.javaClass.simpleName}"
                        )
                    )
                }
            }
            stateRestored = true
            runCatching { assistantCommandCoordinator.recoverStale() }
            pendingCommand?.also { pendingCommand = null }?.let { submitAssistant(it, pendingCommandShouldSpeak) }
            watchPendingCommands()
        }
        viewModelScope.launch {
            for (snapshot in persistenceQueue) runCatching { stateRepository.save(snapshot) }
        }
        refreshLocalMusic()
        viewModelScope.launch {
            while (isActive) {
                delay(500L)
                val timer = state.value.sleepTimerEndsAt
                if (timer != null && System.currentTimeMillis() >= timer) {
                    playback.pause()
                    _state.update { it.copy(sleepTimerEndsAt = null, assistantText = "Таймер сна остановил музыку.") }
                }
                val position = playback.currentPositionMs()
                val duration = playback.durationMs()
                if (!hasPreparedMedia && position == 0L && duration == 0L) continue
                _state.update { current ->
                    if (position == current.positionMs && duration == current.playbackDurationMs) current
                    else current.copy(positionMs = position, playbackDurationMs = duration)
                }
                positionPersistenceTick = (positionPersistenceTick + 1) % 10
                if (positionPersistenceTick == 0) persist(_state.value)
            }
        }
    }

    private fun watchPendingCommands() {
        viewModelScope.launch {
            assistantCommandCoordinator.observePendingUiCommands().collect { commands ->
                commands.forEach { pending ->
                    if (!claimedPendingCommands.add(pending.commandId)) return@forEach
                    val claimed = assistantCommandCoordinator.claim(pending.commandId)
                    if (claimed == null) {
                        claimedPendingCommands.remove(pending.commandId)
                    } else {
                        submitAssistant(claimed.text, speakResponse = true, pendingCommandId = claimed.commandId)
                    }
                }
            }
        }
    }

    fun navigate(destination: AuraDestination) = _state.update {
        it.copy(
            destination = destination,
            isPlayerExpanded = false,
            isQueueOpen = false
        )
    }

    fun setPreferredLanguage(language: String) {
        val cleanLanguage = language.takeIf { it in setOf("ru", "az", "en") } ?: "ru"
        preferences.edit { putString("preferred_language", cleanLanguage) }
        _state.update { it.copy(preferredLanguage = cleanLanguage) }
    }

    fun setMusicSourceMode(mode: MusicSourceMode) {
        configureMusicSources(mode)
        preferences.edit { putString("music_source_mode", mode.name) }
        _state.update {
            it.copy(
                musicSourceMode = mode,
                worldPlaylists = if (mode == MusicSourceMode.YOUTUBE) emptyList() else it.worldPlaylists,
                worldGenres = if (mode == MusicSourceMode.YOUTUBE) emptyList() else it.worldGenres,
                assistantText = "Источник: ${mode.title}"
            )
        }
        if (mode != MusicSourceMode.YOUTUBE && (state.value.worldPlaylists.isEmpty() || state.value.worldGenres.isEmpty())) refreshWorldPlaylists()
    }

    private fun refreshWorldPlaylists() {
        if (state.value.musicSourceMode == MusicSourceMode.YOUTUBE) return
        viewModelScope.launch {
            runCatching {
                coroutineScope {
                    val new2026 = async {
                        runCatching {
                            muzofondCollections.collection(
                                "https://muzofond.fm/collections/new/%D0%BD%D0%BE%D0%B2%D0%B8%D0%BD%D0%BA%D0%B8",
                                limit = 50
                            )
                        }.getOrNull()
                    }
                    val collections = async { runCatching { muzofondCollections.collections(limit = 240) }.getOrDefault(emptyList()) }
                    val genres = async { runCatching { muzofondCollections.genres() }.getOrDefault(emptyList()) }
                    val vol = async { runCatching { volCollections.featured() }.getOrDefault(emptyList()) }
                    val featured = listOfNotNull(new2026.await()) + vol.await()
                    val remaining = collections.await()
                        .filterNot { candidate ->
                            featured.any { it.id == candidate.id || it.collectionUrl == candidate.collectionUrl } ||
                                candidate.title.equals("Новинки музыки 2026", ignoreCase = true)
                        }
                        .shuffled()
                    (featured + remaining) to genres.await()
                }
            }
                .onSuccess { (collections, genres) ->
                    _state.update { it.copy(worldPlaylists = collections, worldGenres = genres) }
                }
                .onFailure { error ->
                    _state.update { current ->
                        current.copy(diagnostics = current.diagnostics.copy(fallbackReason = "Collections: ${error.javaClass.simpleName}"))
                    }
                }
        }
    }

    fun toggleWorldPlaylistLike(playlist: WorldPlaylist) {
        _state.update { current ->
            val liked = if (playlist.id in current.likedWorldPlaylistIds) {
                current.likedWorldPlaylistIds - playlist.id
            } else {
                current.likedWorldPlaylistIds + playlist.id
            }
            preferences.edit { putStringSet("liked_world_playlists", liked) }
            current.copy(likedWorldPlaylistIds = liked)
        }
    }

    private fun configureMusicSources(mode: MusicSourceMode) {
        providerManager.setEnabled(MuzofondMusicPlugin.ID, mode != MusicSourceMode.YOUTUBE)
        providerManager.setEnabled(VolMusicPlugin.ID, mode != MusicSourceMode.YOUTUBE)
        providerManager.setEnabled(YouTubeMusicPlugin.ID, mode != MusicSourceMode.MUZOFOND)
        val preferred = when (mode) {
                MusicSourceMode.MUZOFOND -> setOf(MuzofondMusicPlugin.ID, VolMusicPlugin.ID)
                MusicSourceMode.YOUTUBE -> setOf(YouTubeMusicPlugin.ID)
                MusicSourceMode.BOTH -> setOf(MuzofondMusicPlugin.ID, VolMusicPlugin.ID, YouTubeMusicPlugin.ID)
            }
        musicBrain.setPreferredProviderIds(preferred)
        recommendationEngine.setPreferredProviderIds(preferred)
    }

    fun openRadio(autoPlayFirst: Boolean = false) {
        _state.update { it.copy(destination = AuraDestination.RADIO, isPlayerExpanded = false, isQueueOpen = false) }
        if (state.value.radioCountries.isEmpty()) {
            loadRadioCountries(autoPlayFirst)
        } else if (state.value.radioStations.isEmpty()) {
            state.value.selectedRadioCountry?.let { selectRadioCountry(it, autoPlayFirst) }
        } else if (autoPlayFirst) {
            RadioPlaybackSelector.firstPlayable(state.value.radioStations)?.let(::startPlayback)
        }
    }

    fun loadRadioCountries(autoPlayFirst: Boolean = false) {
        if (state.value.isRadioLoading) return
        _state.update { it.copy(isRadioLoading = true, radioError = null) }
        viewModelScope.launch {
            runCatching { radioProvider.countries() }
                .onSuccess { countries ->
                    val localeCode = java.util.Locale.getDefault().country
                    val selected = countries.firstOrNull { it.code == "AZ" }
                        ?: countries.firstOrNull { it.code == localeCode }
                        ?: countries.firstOrNull()
                    _state.update {
                        it.copy(
                            radioCountries = countries,
                            selectedRadioCountry = selected,
                            isRadioLoading = selected != null,
                            radioError = if (countries.isEmpty()) "Страны пока недоступны" else null
                        )
                    }
                    if (selected != null) loadRadioStations(selected, autoPlayFirst)
                }
                .onFailure {
                    android.util.Log.w("AuraRadio", "Country catalog request failed", it)
                    _state.update { current ->
                        current.copy(
                            isRadioLoading = false,
                            radioError = "Не удалось загрузить каталог радио. Проверь интернет и повтори."
                        )
                    }
                }
        }
    }

    fun selectRadioCountry(country: RadioCountry, autoPlayFirst: Boolean = false) {
        _state.update { it.copy(selectedRadioCountry = country, isRadioLoading = true, radioError = null) }
        loadRadioStations(country, autoPlayFirst)
    }

    fun refreshRadio() {
        val country = state.value.selectedRadioCountry
        if (country == null) loadRadioCountries() else selectRadioCountry(country)
    }

    private fun loadRadioStations(country: RadioCountry, autoPlayFirst: Boolean = false) {
        viewModelScope.launch {
            runCatching { radioProvider.byCountry(country.code) }
                .onSuccess { stations ->
                    _state.update {
                        it.copy(
                            radioStations = stations,
                            isRadioLoading = false,
                            radioError = if (stations.isEmpty()) {
                                "Для ${country.name} сейчас нет доступных HTTPS-станций."
                            } else null
                        )
                    }
                    if (autoPlayFirst) {
                        RadioPlaybackSelector.firstPlayable(stations)?.let(::startPlayback)
                    }
                }
                .onFailure {
                    android.util.Log.w("AuraRadio", "Country station request failed for ${country.code}", it)
                    _state.update { current ->
                        current.copy(
                            isRadioLoading = false,
                            radioError = "Радиостанции не загрузились. Попробуй ещё раз."
                        )
                    }
                }
        }
    }

    fun setLibrarySection(section: LibrarySection) = _state.update { it.copy(librarySection = section) }
    fun setListening(value: Boolean) = _state.update { it.copy(isListening = value) }
    fun setQuery(value: String) = _state.update { it.copy(query = value) }

    fun setVoiceInputState(value: VoiceInputState, backend: RecognitionBackend) {
        val sessionActive = value !is VoiceInputState.Idle &&
            value !is VoiceInputState.TranscriptReady &&
            value !is VoiceInputState.PermissionRequired &&
            value !is VoiceInputState.ModelRequired &&
            value !is VoiceInputState.NoSpeech &&
            value !is VoiceInputState.Failed
        _state.update { current ->
            val status = when (value) {
                VoiceInputState.Idle -> current.assistantText
                VoiceInputState.CheckingAvailability -> "Checking voice recognition..."
                VoiceInputState.Listening -> "Listening..."
                VoiceInputState.Processing -> "Recognizing speech..."
                is VoiceInputState.TranscriptReady -> "Heard: \"${value.text}\""
                is VoiceInputState.PermissionRequired -> "Microphone permission is required"
                is VoiceInputState.ModelRequired -> "Whisper model is required: ${value.modelId}"
                is VoiceInputState.NoSpeech -> value.message
                is VoiceInputState.Failed -> value.message
            }
            current.copy(
                assistantText = status,
                isListening = value is VoiceInputState.Listening,
                isVoiceSessionActive = sessionActive,
                voiceInputState = value,
                recognitionBackend = backend
            )
        }
    }

    fun setVoiceDiagnostics(value: VoiceCaptureDiagnostics) = _state.update {
        it.copy(voiceDiagnostics = value)
    }

    fun startGeminiVoice() {
        if (!state.value.geminiConfigured) {
            _state.update { it.copy(assistantText = "Gemini Smart Voice не настроен. Добавь GEMINI_API_KEY в local.properties.") }
            return
        }
        if (state.value.isVoiceSessionActive ||
            geminiSession.state.value == GeminiSessionState.USER_SPEAKING ||
            geminiSession.state.value == GeminiSessionState.MODEL_SPEAKING ||
            geminiSession.state.value == GeminiSessionState.MODEL_THINKING
        ) {
            stopGeminiVoice()
            return
        }
        _state.update {
            it.copy(
                assistantText = "Smart Voice подключается…",
                isListening = false,
                isVoiceSessionActive = true,
                isAssistantThinking = true,
                voiceInputState = VoiceInputState.CheckingAvailability,
                recognitionBackend = RecognitionBackend.Unavailable
            )
        }
        startVoiceForegroundService()
        if (geminiSession.state.value == GeminiSessionState.DISCONNECTED || geminiSession.state.value == GeminiSessionState.ERROR) {
            geminiSession.connect()
        }
        viewModelScope.launch {
            runCatching {
                geminiSession.state.first { it == GeminiSessionState.READY || it == GeminiSessionState.ERROR }
                check(geminiSession.state.value == GeminiSessionState.READY) { "Gemini Live session failed" }
                geminiAudioOutput.start()
                geminiAudioInput.start(
                    scope = viewModelScope,
                    onChunk = geminiSession::sendAudio,
                    onSpeechEnd = {
                        geminiSession.markSpeechEnded()
                        _state.update {
                            it.copy(
                                isListening = false,
                                isVoiceSessionActive = true,
                                isAssistantThinking = true,
                                voiceInputState = VoiceInputState.Processing
                            )
                        }
                    },
                    onSpeechStart = {
                        _state.update {
                            it.copy(
                                isListening = true,
                                isVoiceSessionActive = true,
                                isAssistantThinking = false,
                                voiceInputState = VoiceInputState.Listening
                            )
                        }
                        if (geminiSession.state.value == GeminiSessionState.MODEL_SPEAKING) geminiSession.interrupt()
                    },
                    onState = { listening ->
                        if (listening) {
                            _state.update {
                                it.copy(
                                    isListening = true,
                                    isVoiceSessionActive = true,
                                    isAssistantThinking = false,
                                    voiceInputState = VoiceInputState.Listening
                                )
                            }
                        }
                    },
                    onError = { error ->
                        _state.update {
                            it.copy(
                                isListening = false,
                                isAssistantThinking = false,
                                voiceInputState = VoiceInputState.Failed(
                                    error.message ?: "Не удалось записать голос",
                                    error
                                )
                            )
                        }
                    }
                )
            }.onFailure { error ->
                geminiAudioInput.stop()
                stopVoiceForegroundService()
                _state.update { it.copy(isListening = false, isVoiceSessionActive = false, isAssistantThinking = false, voiceInputState = VoiceInputState.Failed(error.message ?: "Gemini Voice недоступен", error)) }
            }
        }
    }

    fun stopGeminiVoice() {
        geminiAudioInput.stop()
        geminiSession.markSpeechEnded()
        stopVoiceForegroundService()
        _state.update { it.copy(isListening = false, isVoiceSessionActive = false, isAssistantThinking = false, voiceInputState = VoiceInputState.Idle) }
    }

    private fun startVoiceForegroundService() {
        val intent = Intent(getApplication<Application>(), AuraVoiceForegroundService::class.java)
            .setAction(AuraVoiceForegroundService.ACTION_START)
        androidx.core.content.ContextCompat.startForegroundService(getApplication(), intent)
    }

    @SuppressLint("ImplicitSamInstance")
    private fun stopVoiceForegroundService() {
        getApplication<Application>().stopService(Intent(getApplication(), AuraVoiceForegroundService::class.java))
    }

    fun setGeminiVoice(voice: String) {
        val wasConnected = geminiSession.state.value != GeminiSessionState.DISCONNECTED
        val wasActive = state.value.isListening || state.value.isVoiceSessionActive || geminiSession.state.value in setOf(
            GeminiSessionState.USER_SPEAKING,
            GeminiSessionState.MODEL_SPEAKING,
            GeminiSessionState.MODEL_THINKING,
            GeminiSessionState.TOOL_EXECUTING
        )
        geminiAudioInput.stop()
        geminiSession.setVoice(voice)
        if (wasActive) startGeminiVoice()
        else if (wasConnected) geminiSession.connect()
    }

    fun sendGeminiText(text: String) {
        val input = text.trim()
        if (input.isBlank() || !state.value.geminiConfigured) return
        val timestamp = System.currentTimeMillis()
        lastGeminiUserText = input.take(320)
        geminiAudioOutput.start()
        _state.update { current ->
            current.copy(
                assistantText = current.assistantText,
                isAssistantThinking = true,
                assistantMessages = (current.assistantMessages + AssistantMessage(
                    id = "gemini:text:$timestamp",
                    role = AssistantRole.USER,
                    text = input.take(320),
                    language = az.simplesoft.aura.assistant.AssistantLanguage.detect(input),
                    createdAt = timestamp
                )).takeLast(20)
            )
        }
        if (geminiSession.state.value == GeminiSessionState.DISCONNECTED || geminiSession.state.value == GeminiSessionState.ERROR) {
            geminiSession.connect()
            viewModelScope.launch {
                geminiSession.state.first { it == GeminiSessionState.READY || it == GeminiSessionState.ERROR }
                check(geminiSession.state.value == GeminiSessionState.READY) { "Gemini Live session failed" }
                geminiSession.sendText(input)
            }
        } else {
            geminiSession.sendText(input)
        }
    }

    private fun onGeminiInputTranscription(text: String) {
        if (isVoiceDisableCommand(text)) {
            stopGeminiVoice()
            return
        }
        lastGeminiUserText = text.take(320)
        val timestamp = System.currentTimeMillis()
        _state.update { current ->
            val previous = current.assistantMessages.lastOrNull { it.role == AssistantRole.USER && it.id.startsWith("gemini:user:") }
            val messages = if (previous != null && previous.createdAt > timestamp - 5_000L) {
                current.assistantMessages.map { if (it.id == previous.id) it.copy(text = text.take(320)) else it }
            } else {
                current.assistantMessages + AssistantMessage(
                    id = "gemini:user:$timestamp",
                    role = AssistantRole.USER,
                    text = text.take(320),
                    language = az.simplesoft.aura.assistant.AssistantLanguage.detect(text),
                    createdAt = timestamp
                )
            }
            current.copy(
                query = "",
                assistantText = text,
                voiceInputState = VoiceInputState.TranscriptReady(text),
                assistantMessages = messages.takeLast(20)
            )
        }
    }

    private fun onGeminiOutputTranscription(text: String) {
        geminiMemoryJob?.cancel()
        geminiMemoryJob = viewModelScope.launch {
            delay(900L)
            val userText = lastGeminiUserText.trim()
            if (userText.isNotBlank()) {
                assistantMemory.record(
                    userText,
                    AssistantReply(
                        intent = MusicIntent.Unknown,
                        text = text.take(500),
                        language = az.simplesoft.aura.assistant.AssistantLanguage.detect(text),
                        source = AssistantSource.REMOTE
                    )
                )
                val memory = assistantMemory.snapshot()
                geminiUserContext = memory.promptSummary()
                _state.update { it.copy(memoryFacts = memory.facts) }
            }
        }
        val timestamp = System.currentTimeMillis()
        _state.update { current ->
            val previous = current.assistantMessages.lastOrNull { it.role == AssistantRole.AURA && it.id.startsWith("gemini:aura:") }
            val messages = if (previous != null && previous.createdAt > timestamp - 10_000L) {
                current.assistantMessages.map { if (it.id == previous.id) it.copy(text = text.take(500)) else it }
            } else {
                current.assistantMessages + AssistantMessage(
                    id = "gemini:aura:$timestamp",
                    role = AssistantRole.AURA,
                    text = text.take(500),
                    language = az.simplesoft.aura.assistant.AssistantLanguage.detect(text),
                    createdAt = timestamp
                )
            }
            current.copy(
                assistantText = text,
                isAssistantThinking = false,
                assistantSource = AssistantSource.REMOTE,
                assistantMessages = messages.takeLast(20)
            )
        }
    }

    private fun currentMusicVolumePercent(): Int = VolumeMath.percentForLevel(
        audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC),
        audio.getStreamVolume(AudioManager.STREAM_MUSIC)
    )

    private fun setMusicVolume(percent: Int): Int {
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        if (max <= 0 || audio.isVolumeFixed) return currentMusicVolumePercent()
        audio.setStreamVolume(
            AudioManager.STREAM_MUSIC,
            VolumeMath.levelForPercent(max, percent),
            AudioManager.FLAG_SHOW_UI
        )
        return currentMusicVolumePercent()
    }

    private fun adjustMusicVolume(direction: Int): Int {
        if (!audio.isVolumeFixed) {
            audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
        }
        return currentMusicVolumePercent()
    }

    private suspend fun executeGeminiTool(name: String, args: JSONObject): GeminiToolResult {
        fun ok(message: String, data: JSONObject = JSONObject()) = GeminiToolResult("success", message, data)
        fun unsupported() = GeminiToolResult("unsupported", "Эта функция пока недоступна в AURA.")
        return when (name) {
            "search_music" -> {
                val requestedMood = moodFromGemini(args.optString("mood"))
                val query = args.optString("query").trim().ifBlank { args.optString("mood") }
                if (query.isBlank()) return GeminiToolResult("error", "Не указан запрос")
                if (query.equals("радио", true) || query.equals("радиостанции", true) || query.equals("radio", true) || query.equals("radio stations", true)) {
                    openRadio(autoPlayFirst = true)
                    return ok("Открываю радио по странам")
                }
                if (query.contains("микс", true) || query.contains("mix", true)) {
                    playMyMix()
                    return ok("Запускаю твой микс")
                }
                if (!stateRestored) return GeminiToolResult("error", "Музыка ещё восстанавливается")
                if (requestedMood != null && args.optString("query").isBlank()) {
                    playMoodMix(requestedMood)
                    return ok("Запускаю подборку под настроение", JSONObject().put("mood", requestedMood.name.lowercase()))
                }
                searchTracks(
                    MusicSearchRequest(rawQuery = query, limit = SearchPaging.INITIAL_LIMIT)
                ).join()
                val results = state.value.searchResults
                if (results.isEmpty()) GeminiToolResult("not_found", "Ничего не нашла")
                else ok("Найдено ${results.size} результатов", JSONObject().put("count", results.size).put("tracks", results.take(5).joinToString { "${it.title} — ${it.artist}" }))
            }
            "play_artist" -> {
                val rawArtist = args.optString("artist").trim()
                if (rawArtist.isBlank()) return GeminiToolResult("error", "Не указан исполнитель")
                if (!stateRestored) return GeminiToolResult("error", "Музыка ещё восстанавливается")
                val learnedAliases = stateRepository.artistAliasCorrections().mapNotNull { correction ->
                    correction.artistId.toLongOrNull()?.let { correction.normalizedAlias to it }
                }.toMap()
                val resolved = artistResolver.resolve(
                    rawArtist,
                    ArtistResolveContext(
                        preferredCountry = if (state.value.preferredLanguage.equals("az", true)) "AZ" else null,
                        language = state.value.preferredLanguage,
                        userAliases = learnedAliases
                    )
                )
                val artist = when (resolved) {
                    is ArtistResolveResult.Resolved -> resolved.candidate.canonicalName
                    is ArtistResolveResult.NotFound -> rawArtist
                    is ArtistResolveResult.Ambiguous -> {
                        val names = resolved.candidates.take(3).joinToString(" или ") { it.canonicalName }
                        return GeminiToolResult("ambiguous", "Уточни исполнителя: $names?", JSONObject().put("candidates", names))
                    }
                }
                _state.update { current ->
                    current.copy(
                        diagnostics = current.diagnostics.copy(
                            artistResolver = when (resolved) {
                                is ArtistResolveResult.Resolved -> "raw=\"$rawArtist\" → ${resolved.candidate.canonicalName} (${resolved.candidate.matchType}, ${resolved.candidate.score})"
                                is ArtistResolveResult.NotFound -> "raw=\"$rawArtist\" → not_found"
                                is ArtistResolveResult.Ambiguous -> "raw=\"$rawArtist\" → ambiguous(${resolved.candidates.joinToString { it.canonicalName }})"
                            }
                        )
                    )
                }
                if (resolved is ArtistResolveResult.Resolved) {
                    viewModelScope.launch {
                        stateRepository.rememberArtistAlias(rawArtist, resolved.candidate.artistId.toString(), resolved.candidate.canonicalName)
                    }
                }
                val resolverData = when (resolved) {
                    is ArtistResolveResult.Resolved -> JSONObject().put("canonicalArtist", artist).put("artistId", resolved.candidate.artistId).put("matchType", resolved.candidate.matchType.name).put("confidence", resolved.candidate.score)
                    else -> JSONObject().put("canonicalArtist", artist)
                }
                searchTracks(
                    MusicSearchRequest(
                        rawQuery = artist,
                        artist = artist,
                        limit = SearchPaging.INITIAL_LIMIT,
                        autoPlay = true,
                        artistStrict = true,
                        allowUnattributedArtistMetadata = true
                    )
                ).join()
                val searchTrack = state.value.searchResults.firstOrNull()
                val track = state.value.nowTrack.takeIf {
                    it.id != DemoCatalog.tracks.first().id &&
                        searchTrack != null &&
                        state.value.searchPhase != SearchPhase.ERROR &&
                        state.value.searchResults.any { result -> result.id == it.id } &&
                        artistSearchValidator.validate(artist, listOf(TrackCandidate("state", it.id, it.title, it.artist, it.sourcePageUrl))).accepted.isNotEmpty()
                }
                if (track == null) GeminiToolResult("not_found", "Не нашла уверенный трек исполнителя $artist", resolverData)
                else ok("Музыка исполнителя запущена", resolverData.put("title", track.title).put("artist", track.artist))
            }
            "play_track" -> {
                val index = args.optInt("index", -1)
                val track = state.value.searchResults.getOrNull(index)
                if (track == null) GeminiToolResult("not_found", "Результат с таким номером не найден") else { play(track); ok("Трек выбран", JSONObject().put("title", track.title).put("artist", track.artist)) }
            }
            "play_playlist" -> {
                val name = args.optString("name").trim()
                val playlist = state.value.playlists.firstOrNull { it.name.equals(name, true) || it.name.contains(name, true) }
                if (playlist == null) GeminiToolResult("not_found", "Плейлист не найден") else { playPlaylist(playlist); ok("Плейлист запущен", JSONObject().put("name", playlist.name)) }
            }
            "next_track" -> { next(); ok("Следующий трек запущен") }
            "previous_track" -> { previous(); ok("Предыдущий трек запущен") }
            "seek_relative" -> {
                val seconds = args.optInt("seconds", 0).coerceIn(-600, 600)
                seekTo(state.value.positionMs + seconds * 1_000L)
                ok("Позиция изменена", JSONObject().put("seconds", seconds))
            }
            "pause_music" -> { playback.pause(); ok("Воспроизведение поставлено на паузу") }
            "resume_music" -> { playCurrent(); ok("Воспроизведение продолжено") }
            "open_radio" -> { openRadio(autoPlayFirst = true); ok("Запускаю радио") }
            "clear_queue" -> { clearQueue(); ok("Очередь очищена") }
            "remove_last_queue_track" -> { removeLastFromQueue(); ok("Последний трек удалён из очереди") }
            "set_sleep_timer" -> {
                val minutes = args.optInt("minutes", -1)
                if (minutes !in 1..240) GeminiToolResult("error", "Таймер должен быть от 1 до 240 минут")
                else { setSleepTimer(minutes); ok("Остановлю музыку через $minutes минут") }
            }
            "cancel_sleep_timer" -> { cancelSleepTimer(); ok("Таймер сна выключен") }
            "stop_after_track" -> { setStopAfterTrack(true); ok("Остановлюсь после текущей песни") }
            "volume_up" -> ok("Громкость ${adjustMusicVolume(AudioManager.ADJUST_RAISE)}%")
            "volume_down" -> ok("Громкость ${adjustMusicVolume(AudioManager.ADJUST_LOWER)}%")
            "set_volume" -> {
                val requested = args.optInt("percent", -1)
                if (requested !in 0..100) GeminiToolResult("error", "Громкость должна быть от 0 до 100")
                else ok("Громкость ${setMusicVolume(requested)}%")
            }
            "set_equalizer" -> {
                val preset = EqualizerPreset.fromText(args.optString("preset"))
                    ?: return GeminiToolResult("error", "Пресет: flat, bass, vocal, rock или acoustic")
                setEqualizer(preset)
                ok("Эквалайзер: ${preset.label}")
            }
            "disable_equalizer" -> { disableEqualizer(); ok("Эквалайзер выключен") }
            "mute_music" -> { audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, AudioManager.FLAG_SHOW_UI); ok("Звук выключен") }
            "unmute_music" -> { audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, AudioManager.FLAG_SHOW_UI); ok("Звук включён") }
            "set_car_mode" -> {
                val enabled = args.optBoolean("enabled", true)
                _state.update { it.copy(isCarMode = enabled) }
                ok(if (enabled) "Автомобильный режим включён" else "Автомобильный режим выключен")
            }
            "disable_voice_mode" -> {
                stopGeminiVoice()
                ok("Голосовой режим выключен")
            }
            "like_current_track" -> { if (!state.value.liked) toggleLike(); ok("Трек добавлен в любимые") }
            "unlike_current_track" -> { if (state.value.liked) toggleLike(); ok("Трек убран из любимых") }
            "add_current_to_queue" -> { addToQueue(state.value.nowTrack); ok("Трек добавлен в очередь") }
            "save_queue_as_playlist" -> {
                val name = args.optString("name").trim().ifBlank { "Сохранённая очередь" }
                saveQueueAsPlaylist(name)
                ok("Очередь сохранена в плейлист $name", JSONObject().put("name", name))
            }
            "play_next" -> { val query = args.optString("query").trim(); if (query.isBlank()) GeminiToolResult("error", "Не указан трек") else { searchAndQueue(query, playNext = true).join(); ok("Трек добавлен следующим") } }
            "get_now_playing" -> ok("Текущее состояние", JSONObject().put("title", state.value.nowTrack.title).put("artist", state.value.nowTrack.artist).put("isPlaying", state.value.isPlaying))
            "get_queue" -> ok("Очередь получена", JSONObject().put("tracks", state.value.queue.take(10).joinToString { "${it.title} — ${it.artist}" }))
            "get_recent_history" -> ok("История получена", JSONObject().put("tracks", state.value.history.takeLast(10).joinToString { "${it.title} — ${it.artist}" }))
            "find_similar_music" -> { playSimilarMix(); ok("Ищу похожую музыку") }
            "more_like_this" -> { playSimilarMix(); ok("Подбираю ещё похожее") }
            "reject_current_track" -> { rejectCurrentTrack(); ok("Трек пропущен и учтён") }
            "clear_memory" -> {
                assistantMemory.clear()
                geminiUserContext = ""
                _state.update { it.copy(memoryFacts = emptyList()) }
                ok("Локальная память очищена")
            }
            "play_mood_mix" -> {
                val mood = moodFromGemini(args.optString("mood"))
                    ?: return GeminiToolResult("error", "Не знаю такое настроение")
                playMoodMix(mood)
                ok("Запускаю подборку под настроение", JSONObject().put("mood", mood.name.lowercase()))
            }
            "play_my_mix" -> { playMyMix(); ok("Запускаю твой микс") }
            "shuffle" -> { _state.update { it.copy(isShuffleEnabled = !it.isShuffleEnabled) }; playback.setShuffle(state.value.isShuffleEnabled); ok("Перемешивание изменено") }
            "set_repeat" -> {
                val mode = when (args.optString("mode").lowercase()) {
                    "one" -> AuraRepeatMode.ONE
                    "all" -> AuraRepeatMode.ALL
                    "off" -> AuraRepeatMode.OFF
                    else -> return GeminiToolResult("error", "Режим повтора должен быть off, one или all")
                }
                _state.update { it.copy(repeatMode = mode) }; playback.setRepeat(mode); ok("Повтор изменён")
            }
            "open_queue" -> { _state.update { it.copy(isQueueOpen = true, isPlayerExpanded = false) }; ok("Очередь открыта") }
            "open_playlists" -> { _state.update { it.copy(destination = AuraDestination.LIBRARY, librarySection = LibrarySection.PLAYLISTS) }; ok("Плейлисты открыты") }
            "open_local_library" -> { _state.update { it.copy(destination = AuraDestination.LIBRARY, librarySection = LibrarySection.LOCAL) }; refreshLocalMusic(); ok("Музыка на телефоне открыта") }
            "play_offline_music" -> { playOfflineMusic("Включаю музыку с телефона."); ok("Запускаю офлайн-музыку") }
            "search_offline_library" -> {
                val query = args.optString("query").trim()
                if (query.isBlank()) GeminiToolResult("error", "Укажи песню или исполнителя") else { searchOffline(query); ok("Ищу в офлайн-библиотеке") }
            }
            "get_offline_library_status" -> {
                val local = state.value.localTracks
                ok("Офлайн-библиотека: ${local.size} песен, ${formatStorageSize(state.value.offlineStorageBytes)}")
            }
            "download_current_track" -> { downloadCurrentTrack(); ok("Сохраняю текущую песню на телефон") }
            "delete_offline_track" -> { deleteCurrentOfflineTrack(); ok("Удаляю локальную копию") }
            "delete_all_offline" -> { deleteAllOffline(); ok("Очищаю офлайн-библиотеку") }
            "open_history" -> { _state.update { it.copy(destination = AuraDestination.LIBRARY, librarySection = LibrarySection.HISTORY) }; ok("История открыта") }
            "set_auto_continue" -> {
                val enabled = args.optBoolean("enabled", true)
                _state.update { it.copy(autoContinueEnabled = enabled) }
                ok(if (enabled) "Автопродолжение включено" else "Автопродолжение выключено")
            }
            else -> unsupported()
        }
    }

    private fun moodFromGemini(value: String): Mood? = when (value.trim().lowercase()) {
        "calm", "спокойное", "спокойный", "relax" -> Mood.CALM
        "drive", "дорога", "поездка" -> Mood.DRIVE
        "focus", "фокус", "концентрация" -> Mood.FOCUS
        "energy", "энергия", "энергичное" -> Mood.ENERGY
        "night", "ночное", "ночь" -> Mood.NIGHT
        "sad", "грустное", "грусть", "печаль" -> Mood.SAD
        "happy", "весёлое", "радость" -> Mood.HAPPY
        "lullaby", "колыбельное", "колыбельная", "баю", "bedtime", "nursery", "layla", "beşik" -> Mood.LULLABY
        else -> null
    }

    fun startPushToTalk(start: () -> Unit) {
        viewModelScope.launch {
            // Barge-in: stop AURA immediately before opening the microphone.
            speech.stop()
            start()
        }
    }

    fun completeOnboarding(name: String, language: String) {
        val cleanName = name.trim().replace(Regex("\\s+"), " ").take(40).ifBlank { null }
        val cleanLanguage = language.takeIf { it in setOf("ru", "az", "en") } ?: "ru"
        preferences.edit {
            putBoolean("onboarding_complete", true)
            if (cleanName == null) remove("user_name") else putString("user_name", cleanName)
            putString("preferred_language", cleanLanguage)
        }
        viewModelScope.launch {
            val facts = buildList {
                cleanName?.let { add(az.simplesoft.aura.assistant.MemoryInsight("identity", "name", it)) }
                add(az.simplesoft.aura.assistant.MemoryInsight("language", "preferred", cleanLanguage))
            }
            assistantMemory.remember(facts)
            val memory = assistantMemory.snapshot()
            geminiUserContext = memory.promptSummary()
            _state.update { it.copy(memoryFacts = memory.facts) }
        }
        _state.update {
            it.copy(
                onboardingComplete = true,
                userName = cleanName,
                preferredLanguage = cleanLanguage,
                assistantText = if (cleanName == null) "Рада познакомиться. Я AURA." else "Рада познакомиться, $cleanName."
            )
        }
    }

    fun resetOnboarding() {
        preferences.edit { remove("onboarding_complete"); remove("user_name"); remove("preferred_language") }
        viewModelScope.launch {
            assistantMemory.clear()
            geminiUserContext = ""
            _state.update {
                it.copy(
                    onboardingComplete = false,
                    userName = null,
                    preferredLanguage = "ru",
                    memoryFacts = emptyList(),
                    assistantText = "Профиль и локальная память очищены."
                )
            }
        }
    }

    fun setVoiceEngineMode(mode: VoiceEngineMode) {
        speech.setEngineMode(mode)
        preferences.edit { putString("voice_engine_mode", mode.name) }
        _state.update { it.copy(voiceEngineMode = mode) }
    }

    fun resetGeminiUsage() {
        geminiSession.resetUsage()
    }

    fun submit(text: String = state.value.query) = submitAssistant(text, speakResponse = false)

    fun submitVoice(text: String) {
        if (isVoiceDisableCommand(text)) {
            speech.stop()
            stopGeminiVoice()
            return
        }
        submitAssistant(text, speakResponse = true)
    }

    private fun isVoiceDisableCommand(text: String): Boolean = Regex(
        "^\\s*(?:аура|aura)\\s*[,.:\\-]?\\s*(?:отключись|выключись|замолчи|стоп)\\s*$",
        RegexOption.IGNORE_CASE
    ).matches(text) || az.simplesoft.aura.assistant.VoiceModeCommand.isDisable(text)

    /** Debug/device verification hook. Production voice turns still go through the AI agent. */
    fun previewAzerbaijaniVoice(text: String) = speech.speak(
        text,
        az.simplesoft.aura.assistant.AssistantLanguage.AZERBAIJANI
    )

    fun previewVoice(text: String, language: az.simplesoft.aura.assistant.AssistantLanguage) = speech.speak(text, language)

    fun search(text: String = state.value.query) {
        val query = text.trim()
        if (query.isBlank()) return
        if (!stateRestored) {
            _state.update { it.copy(assistantText = "Восстанавливаю твою музыку…") }
            return
        }
        viewModelScope.launch {
            executeAssistantReply(
            AssistantReply(
                intent = MusicIntent.Search(query),
                text = "Ищу песню: $query"
            )
            )
        }
    }

    /** Requests the next search page while preserving the current query and ranking context. */
    fun loadMoreSearchResults() {
        val current = state.value
        val baseRequest = lastSearchRequest ?: return
        val nextLimit = SearchPaging.nextLimit(current.searchResultLimit) ?: run {
            _state.update { it.copy(canLoadMore = false) }
            return
        }
        if (current.isLoading || current.isLoadingMore || !current.canLoadMore) return
        val request = baseRequest.copy(limit = nextLimit, autoPlay = false)
        _state.update {
            it.copy(
                isLoadingMore = true,
                searchPhase = SearchPhase.SEARCHING,
                assistantText = "Загружаю ещё результаты…"
            )
        }
        searchTracks(request, append = true)
    }

    private fun submitAssistant(text: String, speakResponse: Boolean, pendingCommandId: String? = null) {
        val input = text.trim()
        if (input.isBlank()) return
        if (isVoiceDisableCommand(input)) {
            speech.stop()
            stopGeminiVoice()
            pendingCommandId?.let { viewModelScope.launch { assistantCommandCoordinator.complete(it) } }
            return
        }
        if (!stateRestored) {
            pendingCommand = input
            pendingCommandShouldSpeak = speakResponse
            _state.update { it.copy(assistantText = "Восстанавливаю твою музыку…") }
            return
        }
        val language = az.simplesoft.aura.assistant.AssistantLanguage.detect(input)
        val timestamp = System.currentTimeMillis()
        _state.update { current ->
            current.copy(
                query = "",
                destination = if (speakResponse) current.destination else AuraDestination.ASSISTANT,
                // Keep the existing answer visible while work runs; a synthetic
                // “thinking” phrase must never be presented as an assistant reply.
                assistantText = current.assistantText,
                isAssistantThinking = true,
                assistantMessages = (current.assistantMessages + AssistantMessage(
                    id = "ui:user:$timestamp",
                    role = AssistantRole.USER,
                    text = input.take(320),
                    language = language,
                    createdAt = timestamp
                )).takeLast(20)
            )
        }
        val commandJob = viewModelScope.launch {
            val current = state.value
            val track = current.nowTrack.takeIf { it.id != DemoCatalog.tracks.first().id }
            val answer = auraAi.respond(
                input = input,
                context = AuraAiContext(
                    currentTrack = track?.title,
                    currentArtist = track?.artist,
                    isPlaying = current.isPlaying,
                    hourOfDay = Calendar.getInstance().get(Calendar.HOUR_OF_DAY),
                    carMode = current.isCarMode,
                    queueSize = current.queue.size,
                    queue = current.queue.map { AssistantTrackContext(it.id, it.title, it.artist) },
                    currentIndex = current.currentIndex,
                    lastSearchQuery = current.diagnostics.query.takeIf { it != "—" },
                    lastSearchResults = current.searchResults.map { AssistantTrackContext(it.id, it.title, it.artist) },
                    playlists = current.playlists.map { it.name },
                    favoriteCount = current.favorites.size,
                    currentPlaylist = current.selectedPlaylist?.name,
                    currentTrackLiked = current.liked,
                    lastIntent = current.assistantSource.name,
                    recentTurns = current.assistantMessages.takeLast(6).map { it.role.name to it.text }
                )
            )
            geminiUserContext = assistantMemory.snapshot().promptSummary()
            val execution = executeAssistantReply(answer)
            val responseText = when (execution) {
                is ActionExecutionResult.Success -> answer.text
                is ActionExecutionResult.Duplicate -> execution.message
                is ActionExecutionResult.NeedsClarification -> execution.question
                is ActionExecutionResult.NotFound -> execution.message
                is ActionExecutionResult.PermissionRequired -> execution.message
                is ActionExecutionResult.TemporaryFailure -> execution.message
                is ActionExecutionResult.Unsupported -> execution.message
            }
            val replyTimestamp = System.currentTimeMillis()
            _state.update { value ->
                value.copy(
                    assistantText = responseText,
                    isAssistantThinking = false,
                    assistantSource = answer.source,
                    assistantDiagnostics = answer.diagnostics.takeIf { BuildConfig.DEBUG },
                    assistantMessages = (value.assistantMessages + AssistantMessage(
                        id = "ui:aura:$replyTimestamp",
                        role = AssistantRole.AURA,
                        text = responseText,
                        language = answer.language,
                        createdAt = replyTimestamp
                    )).takeLast(20)
                )
            }
            pendingCommandId?.let { commandId ->
                if (execution is ActionExecutionResult.Success) {
                    assistantCommandCoordinator.complete(commandId)
                } else if (execution !is ActionExecutionResult.Duplicate) {
                    assistantCommandCoordinator.fail(commandId)
                }
            }
            if (speakResponse) {
                val style = if (state.value.isCarMode) VoiceStyle.DRIVING else VoiceStyle.FRIENDLY
                if (SpeechResponsePolicy.verbosity(answer.intent, state.value.isCarMode) != ResponseVerbosity.SILENT) {
                    speech.speak(responseText, answer.language, style)
                }
            }
        }
        commandJob.invokeOnCompletion { cause ->
            pendingCommandId?.let { commandId ->
                claimedPendingCommands.remove(commandId)
                when (cause) {
                    is CancellationException -> viewModelScope.launch { assistantCommandCoordinator.requeue(commandId) }
                    null -> Unit
                    else -> viewModelScope.launch { assistantCommandCoordinator.fail(commandId) }
                }
            }
        }
    }

    private suspend fun executeAssistantReply(answer: AssistantReply): ActionExecutionResult {
        if (answer.intent is MusicIntent.PlayPlaylist) {
            val requested = answer.intent.name
            val exists = state.value.playlists.any {
                it.name.equals(requested, ignoreCase = true) ||
                    it.name.contains(requested, ignoreCase = true) ||
                    requested.contains(it.name, ignoreCase = true)
            }
            if (!exists) return ActionExecutionResult.NotFound("Плейлист $requested не найден")
        }
        dispatchAssistantReply(answer)?.join()
        return ActionExecutionResult.Success()
    }

    private fun dispatchAssistantReply(answer: AssistantReply): Job? {
        if (answer.intent is MusicIntent.Composite) {
            val commands = answer.intent.commands
            return viewModelScope.launch {
                commands.map { command ->
                    dispatchAssistantReply(answer.copy(intent = command, text = ""))
                }.filterNotNull().joinAll()
            }
        }
        when (val intent = answer.intent) {
            MusicIntent.OpenPlaylists -> {
                _state.update {
                    it.copy(
                        destination = AuraDestination.LIBRARY,
                        librarySection = LibrarySection.PLAYLISTS,
                        selectedPlaylistId = null,
                        assistantText = answer.text
                    )
                }
                return null
            }
            MusicIntent.OpenLocalLibrary -> {
                viewModelScope.launch {
                    refreshLocalMusic()
                    _state.update {
                        it.copy(
                            destination = AuraDestination.LIBRARY,
                            librarySection = LibrarySection.LOCAL,
                            selectedPlaylistId = null,
                            assistantText = answer.text
                        )
                    }
                }
                return null
            }
            MusicIntent.PlayOfflineMusic -> return playOfflineMusic(answer.text)
            MusicIntent.OfflineStatus -> return offlineStatus(answer.text)
            is MusicIntent.SearchOffline -> return searchOffline(intent.query)
            MusicIntent.DownloadCurrent -> return downloadCurrentTrack()
            MusicIntent.DownloadQueue -> return downloadQueue()
            MusicIntent.DeleteOfflineCurrent -> return deleteCurrentOfflineTrack()
            MusicIntent.DeleteAllOffline -> return deleteAllOffline()
            is MusicIntent.CreatePlaylist -> {
                createPlaylist(intent.name, intent.includeQueue)
                _state.update {
                    it.copy(
                        destination = AuraDestination.LIBRARY,
                        librarySection = LibrarySection.PLAYLISTS,
                        selectedPlaylistId = null,
                        assistantText = answer.text
                    )
                }
                return null
            }
            is MusicIntent.PlayPlaylist -> {
                val playlist = state.value.playlists.firstOrNull {
                    it.name.equals(intent.name, ignoreCase = true)
                } ?: state.value.playlists.firstOrNull {
                    it.name.contains(intent.name, ignoreCase = true) ||
                        intent.name.contains(it.name, ignoreCase = true)
                }
                if (playlist == null) {
                    _state.update {
                        it.copy(
                            destination = AuraDestination.LIBRARY,
                            librarySection = LibrarySection.PLAYLISTS,
                            selectedPlaylistId = null,
                            assistantText = "Не нашла плейлист ${intent.name}."
                        )
                    }
                } else {
                    return playPlaylist(playlist, intent.shuffled)
                }
                return null
            }
            is MusicIntent.PlayWorldPlaylist -> return playWorldPlaylist(intent.query, intent.shuffled)
            MusicIntent.OpenQueue -> {
                _state.update { it.copy(isQueueOpen = true, isPlayerExpanded = false, assistantText = answer.text) }
                return null
            }
            MusicIntent.OpenRadio -> {
                openRadio(autoPlayFirst = true)
                _state.update { it.copy(assistantText = answer.text) }
                return null
            }
            MusicIntent.ClearQueue -> {
                clearQueue()
                return null
            }
            is MusicIntent.QueueTrack -> {
                return searchAndQueue(intent.query, intent.playNext)
            }
            is MusicIntent.AutoContinue -> {
                _state.update {
                    it.copy(autoContinueEnabled = intent.enabled, assistantText = answer.text).also(::persist)
                }
                return null
            }
            else -> Unit
        }
        val requestedSearch = answer.intent as? MusicIntent.Search
        if (requestedSearch != null) {
            requestedSearch.mood?.takeIf {
                requestedSearch.artist.isNullOrBlank() && requestedSearch.query.isGenericMoodQuery(it)
             }?.let {
                 playMoodMix(it)
                 return null
            }
            val query = requestedSearch.query
            _state.update { current ->
                val recent = listOf(query) + current.recentSearches.filterNot { it.equals(query, true) }
                current.copy(
                    destination = AuraDestination.SEARCH,
                    query = query,
                    recentSearches = recent.take(5),
                    assistantText = "Ищу песню: $query",
                    isLoading = true,
                    isLoadingMore = false,
                    canLoadMore = false,
                    searchResultLimit = SearchPaging.INITIAL_LIMIT,
                    searchPhase = SearchPhase.SEARCHING
                )
            }
            persist(_state.value)
            return searchTracks(
                MusicSearchRequest(
                    rawQuery = query,
                    artist = requestedSearch.artist,
                    limit = SearchPaging.INITIAL_LIMIT
                )
            )
        }

        if (answer.intent == MusicIntent.MoreLikeThis || answer.intent == MusicIntent.Similar) {
            playSimilarMix()
            return null
        }

        if (answer.intent == MusicIntent.NotThis) {
            rejectCurrentTrack()
            return null
        }

        if (answer.intent is MusicIntent.SleepTimer) {
            setSleepTimer(answer.intent.minutes)
            return null
        }

        if (answer.intent == MusicIntent.CancelSleepTimer) {
            cancelSleepTimer()
            return null
        }

        if (answer.intent == MusicIntent.StopAfterTrack) {
            setStopAfterTrack(true)
            return null
        }

        if (answer.intent == MusicIntent.RemoveLastFromQueue) {
            removeLastFromQueue()
            return null
        }

        if (answer.intent == MusicIntent.ClearMemory) {
            viewModelScope.launch {
                assistantMemory.clear()
                geminiUserContext = ""
                _state.update { it.copy(memoryFacts = emptyList(), assistantText = "Локальная память очищена.") }
            }
            return null
        }

        if (answer.intent == MusicIntent.MyMix) {
            playMyMix()
            return null
        }

        if (answer.intent == MusicIntent.ContinueListening) {
            continueListening()
            return null
        }

        when (answer.intent) {
            MusicIntent.Louder -> {
                val actual = adjustMusicVolume(AudioManager.ADJUST_RAISE)
                _state.update { it.copy(assistantText = "Громкость $actual%") }
                return null
            }
            MusicIntent.Quieter -> {
                val actual = adjustMusicVolume(AudioManager.ADJUST_LOWER)
                _state.update { it.copy(assistantText = "Громкость $actual%") }
                return null
            }
            MusicIntent.Mute -> {
                audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, AudioManager.FLAG_SHOW_UI)
                _state.update { it.copy(assistantText = answer.text) }
                return null
            }
            MusicIntent.Unmute -> {
                audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, AudioManager.FLAG_SHOW_UI)
                _state.update { it.copy(assistantText = answer.text) }
                return null
            }
            is MusicIntent.SetVolume -> {
                val actual = setMusicVolume(answer.intent.percent)
                _state.update { it.copy(assistantText = "Громкость $actual%") }
                return null
            }
            is MusicIntent.SetEqualizer -> {
                setEqualizer(answer.intent.preset)
                return null
            }
            MusicIntent.DisableEqualizer -> {
                disableEqualizer()
                return null
            }
            MusicIntent.CycleEqualizer -> {
                cycleEqualizer()
                return null
            }
            else -> Unit
        }

        when (answer.intent) {
            MusicIntent.Play -> {
                _state.update { it.copy(assistantText = answer.text) }
                playCurrent()
                return null
            }
            MusicIntent.Pause -> {
                _state.update { it.copy(assistantText = answer.text) }
                playback.pause()
                return null
            }
            MusicIntent.Next -> {
                _state.update { it.copy(assistantText = answer.text) }
                next()
                return null
            }
            MusicIntent.Previous -> {
                _state.update { it.copy(assistantText = answer.text) }
                previous()
                return null
            }
            MusicIntent.Like -> {
                if (!state.value.liked) toggleLike()
                _state.update { it.copy(assistantText = answer.text) }
                return null
            }
            MusicIntent.Unlike -> {
                if (state.value.liked) toggleLike()
                _state.update { it.copy(assistantText = answer.text) }
                return null
            }
            else -> Unit
        }

        _state.update { current ->
            when (answer.intent) {
                MusicIntent.Play, MusicIntent.Pause, MusicIntent.Next, MusicIntent.Previous -> current
                MusicIntent.Like -> current.copy(
                    likedIds = current.likedIds + current.nowTrack.id,
                    assistantText = answer.text
                ).also(::persist)
                MusicIntent.Unlike -> current.copy(
                    likedIds = current.likedIds - current.nowTrack.id,
                    assistantText = answer.text
                ).also(::persist)
                MusicIntent.Repeat -> current.copy(
                    repeatMode = current.repeatMode.next(),
                    assistantText = repeatModeMessage(current.repeatMode.next())
                ).also {
                    playback.setRepeat(it.repeatMode)
                    persist(it)
                }
                MusicIntent.Shuffle -> current.copy(
                    isShuffleEnabled = !current.isShuffleEnabled,
                    assistantText = answer.text
                ).also {
                    playback.setShuffle(it.isShuffleEnabled)
                    persist(it)
                }
                MusicIntent.Louder -> current.copy(assistantText = answer.text).also {
                    audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, 0)
                }
                MusicIntent.Quieter -> current.copy(assistantText = answer.text).also {
                    audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, 0)
                }
                MusicIntent.Mute -> current.copy(assistantText = answer.text).also {
                    audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, 0)
                }
                MusicIntent.Unmute -> current.copy(assistantText = answer.text).also {
                    audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0)
                }
                is MusicIntent.SetVolume -> current.copy(assistantText = answer.text).also {
                    val level = (audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC) * answer.intent.percent / 100.0).toInt()
                    audio.setStreamVolume(AudioManager.STREAM_MUSIC, level, 0)
                }
                is MusicIntent.SetEqualizer,
                MusicIntent.DisableEqualizer,
                MusicIntent.CycleEqualizer -> current
                MusicIntent.OpenLocalLibrary,
                MusicIntent.PlayOfflineMusic,
                MusicIntent.OfflineStatus,
                is MusicIntent.SearchOffline,
                MusicIntent.DownloadCurrent,
                MusicIntent.DownloadQueue,
                MusicIntent.DeleteOfflineCurrent,
                MusicIntent.DeleteAllOffline -> current
                MusicIntent.CarMode -> current.copy(isCarMode = true, assistantText = answer.text)
                MusicIntent.NowPlaying -> current.copy(
                    assistantText = "Сейчас играет ${current.nowTrack.title} — ${current.nowTrack.artist}."
                )
                MusicIntent.OpenHistory -> current.copy(
                    destination = AuraDestination.LIBRARY,
                    librarySection = LibrarySection.HISTORY,
                    assistantText = answer.text
                )
                MusicIntent.OpenPlaylists,
                MusicIntent.OpenLocalLibrary,
                MusicIntent.PlayOfflineMusic,
                MusicIntent.OfflineStatus,
                is MusicIntent.SearchOffline,
                MusicIntent.DownloadCurrent,
                MusicIntent.DownloadQueue,
                MusicIntent.DeleteOfflineCurrent,
                MusicIntent.DeleteAllOffline,
                MusicIntent.OpenRadio,
                is MusicIntent.CreatePlaylist,
                is MusicIntent.PlayPlaylist,
                is MusicIntent.PlayWorldPlaylist,
                MusicIntent.OpenQueue,
                MusicIntent.ClearQueue,
                is MusicIntent.QueueTrack,
                is MusicIntent.AutoContinue,
                MusicIntent.MoreLikeThis,
                MusicIntent.NotThis,
                is MusicIntent.SleepTimer,
                MusicIntent.CancelSleepTimer,
                MusicIntent.StopAfterTrack,
                MusicIntent.RemoveLastFromQueue,
                MusicIntent.ClearMemory,
                is MusicIntent.Composite -> current
                MusicIntent.Similar,
                MusicIntent.MyMix,
                MusicIntent.ContinueListening,
                is MusicIntent.Search -> current
                MusicIntent.Unknown -> current.copy(assistantText = answer.text)
            }
        }
        return null
    }

    fun play(track: Track) {
        val candidate = candidatesByTrackId[track.id]
        when {
            !track.streamUrl.isNullOrBlank() -> startPlayback(track)
            candidate != null -> resolveAndPlay(candidate)
            track.id != DemoCatalog.tracks.first().id -> submit("${track.artist} ${track.title}")
            else -> _state.update { it.copy(assistantText = "Сначала найди конкретную песню.") }
        }
    }

    /** Resolves chart metadata lazily through the configured provider; no audio is bundled in the catalog. */
    /** Starts a catalog playlist directly; UI cards must not route through chat/search. */
    fun playWorldPlaylist(playlist: WorldPlaylist) {
        playWorldPlaylist(playlist.id, shuffled = false)
    }

    private fun playWorldPlaylist(query: String, shuffled: Boolean): Job = viewModelScope.launch {
        worldPlaylistJob?.cancel()
        worldPlaylistJob = coroutineContext[Job]
        val listed = findWorldPlaylist(query)
        val playlist = listed?.let { entry ->
            if (entry.items.isNotEmpty()) entry
            else entry.collectionUrl?.let { url -> runCatching { muzofondCollections.collection(url) }.getOrNull() }
        }
        if (playlist == null || playlist.items.isEmpty()) {
            _state.update { it.copy(assistantText = "Не нашла треки в этой подборке.") }
            return@launch
        }
        // Do not leave the previous song visible while a new chart is resolving.
        // Otherwise a failed search looks like every card starts the old track.
        playback.pause()
        _state.update {
            it.copy(
                activeWorldPlaylistTitle = playlist.title,
                queue = DemoCatalog.tracks,
                currentIndex = 0,
                isPlayerExpanded = false,
                isQueueOpen = true,
                positionMs = 0L,
                playbackDurationMs = 0L,
                isBuffering = false
            )
        }
        _state.update { it.copy(isLoading = true, assistantText = "Собираю ${playlist.title}…") }
        val items = playlist.items.let { if (shuffled) it.shuffled() else it }.take(MAX_WORLD_PLAYLIST_ITEMS)
        // Resolve several chart positions at once, but keep a small limit so an
        // online source is not flooded and one slow song cannot block the playlist.
        val resolverSlots = Semaphore(WORLD_PLAYLIST_CONCURRENCY)
        val resolved = coroutineScope {
            items.map { item ->
                async {
                    resolverSlots.withPermit { item to resolveWorldPlaylistItem(item) }
                }
            }.awaitAll()
        }.asSequence()
            .mapNotNull { (_, track) -> track }
            .distinctBy(Track::id)
            .toList()
        resolved.forEachIndexed { index, track ->
            if (index == 0) {
                startPlayback(track)
                _state.update { it.copy(isLoading = false, assistantText = "Запускаю ${playlist.title}…") }
            } else {
                appendWorldPlaylistTrack(track)
            }
        }
        if (resolved.isEmpty()) {
            _state.update { it.copy(isLoading = false, assistantText = "Не удалось найти песни из этой подборки.") }
            return@launch
        }
        _state.update { it.copy(isLoading = false, assistantText = "Запустила ${playlist.title}: ${resolved.size} треков.") }
    }

    /** Maps voice shortcuts and natural requests onto the live Muzofond catalog. */
    private fun findWorldPlaylist(query: String): WorldPlaylist? {
        val all = state.value.worldPlaylists + state.value.worldGenres
        val needle = query.trim().lowercase()
        if (needle.isBlank()) return null
        all.firstOrNull { entry ->
            entry.id.equals(needle, ignoreCase = true) ||
                entry.title.contains(needle, ignoreCase = true) ||
                needle.contains(entry.title.lowercase())
        }?.let { return it }
        val compact = needle.replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()
        val preferred = when {
            needle == "hits-90s" || compact.contains("90") || compact.contains("девяност") ->
                all.filter { it.title.lowercase().contains("90") || it.title.lowercase().contains("девяност") }
            needle == "new-this-week" -> all.filter { it.kind == az.simplesoft.aura.domain.playlist.WorldPlaylistKind.NEW_RELEASES }
            needle == "top-week-world" -> all.filter { it.kind == az.simplesoft.aura.domain.playlist.WorldPlaylistKind.TOP_WEEK }
            needle == "top-today-world" -> all.filter { it.title.lowercase().contains("сегодня") || it.title.lowercase().contains("today") }
            needle == "top-today-az" -> all.filter { it.title.lowercase().contains("азер") || it.title.lowercase().contains("azer") }
            needle == "artist-50cent" -> all.filter { it.title.lowercase().contains("50 cent") || it.title.lowercase().contains("50cent") }
            needle == "artist-ruki-vverh" -> all.filter { it.title.lowercase().contains("руки вверх") || it.title.lowercase().contains("ruki vverh") }
            else -> emptyList()
        }
        preferred.firstOrNull()?.let { return it }
        val tokens = compact.split(' ').filter { it.length >= 2 }
        return all.maxByOrNull { entry ->
            val title = entry.title.lowercase()
            tokens.fold(0) { score, token -> score + if (title.contains(token)) 3 else 0 } +
                if (entry.kind == az.simplesoft.aura.domain.playlist.WorldPlaylistKind.MOOD && tokens.any { title.contains(it) }) 2 else 0
        }?.takeIf { entry -> tokens.any { entry.title.lowercase().contains(it) } }
    }

    private suspend fun resolveWorldPlaylistItem(item: az.simplesoft.aura.domain.playlist.WorldPlaylistItem): Track? =
        withTimeoutOrNull(WORLD_PLAYLIST_ITEM_TIMEOUT_MS) {
            try {
                val volCandidate = if (
                    item.providerId == VolMusicPlugin.ID && !item.sourceUrl.isNullOrBlank()
                ) {
                    TrackCandidate(
                        providerId = VolMusicPlugin.ID,
                        id = "collection:${item.rank}:${item.artist}:${item.title}",
                        title = item.title,
                        artist = item.artist,
                        detailUrl = item.sourceUrl!!,
                        artworkUrl = item.artworkUrl,
                        durationMs = item.durationMs
                    )
                } else null
                if (volCandidate != null) {
                    return@withTimeoutOrNull when (val source = resolveCandidate(volCandidate)) {
                        is ProviderResult.Success -> source.value.track
                        is ProviderResult.Failure -> null
                    }
                }
                val directCandidate = if (
                    item.providerId == MuzofondMusicPlugin.ID && !item.playbackToken.isNullOrBlank()
                ) {
                    TrackCandidate(
                        providerId = MuzofondMusicPlugin.ID,
                        id = "collection:${item.rank}:${item.artist}:${item.title}",
                        title = item.title,
                        artist = item.artist,
                        detailUrl = item.sourceUrl ?: "https://muzofond.fm/",
                        artworkUrl = item.artworkUrl,
                        durationMs = item.durationMs,
                        playbackToken = item.playbackToken
                    )
                } else null
                if (directCandidate != null) {
                    return@withTimeoutOrNull when (val source = resolveCandidate(directCandidate)) {
                        is ProviderResult.Success -> source.value.track
                        is ProviderResult.Failure -> null
                    }
                }
                when (val result = providerManager.search(MusicSearchRequest(
                    rawQuery = "${item.artist} ${item.title}",
                    artist = item.artist,
                    title = item.title,
                    year = item.year,
                    autoPlay = false,
                    preferredProviderId = when (state.value.musicSourceMode) {
                        MusicSourceMode.MUZOFOND -> MuzofondMusicPlugin.ID
                        MusicSourceMode.YOUTUBE,
                        MusicSourceMode.BOTH -> YouTubeMusicPlugin.ID
                    },
                    limit = 8
                ))) {
                    is PluginResult.Success -> {
                        val candidates = WorldPlaylistCandidateMatcher.rank(item, result.value)
                        candidates.take(5).firstNotNullOfOrNull { candidate ->
                            when (val source = resolveCandidate(candidate)) {
                                is ProviderResult.Success -> {
                                    source.value.track
                                }
                                is ProviderResult.Failure -> {
                                    null
                                }
                            }
                        }
                    }
                    is PluginResult.Failure -> {
                        null
                    }
                }
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                null
            }
        }

    private fun appendWorldPlaylistTrack(track: Track) {
        _state.update { current ->
            if (current.queue.any { it.id == track.id }) current
            else current.copy(
                queue = current.queue + track,
                memoryTracks = (current.memoryTracks + track).distinctBy(Track::id).take(100)
            )
        }
        playback.append(track)
    }

    fun togglePlay() {
        if (state.value.isPlaying) playback.pause() else playCurrent()
    }

    fun seekTo(positionMs: Long) = playback.seekTo(positionMs)

    fun next() {
        if (!hasPreparedMedia) return
        val current = state.value
        if (current.nowTrack.sourceId == "radio_browser") {
            RadioPlaybackSelector.next(current.radioStations, current.nowTrack.id)?.let(::startPlayback)
            return
        }
        val earlySkip = current.positionMs in 1 until EARLY_SKIP_THRESHOLD_MS &&
            (current.playbackDurationMs == 0L || current.positionMs * 4 < current.playbackDurationMs)
        if (earlySkip) recordRecommendationEvent(current.nowTrack, RecommendationEventType.SKIP)
        playback.next()
    }

    fun previous() {
        if (!hasPreparedMedia) return
        val current = state.value
        if (current.nowTrack.sourceId == "radio_browser") {
            RadioPlaybackSelector.previous(current.radioStations, current.nowTrack.id)?.let(::startPlayback)
            return
        }
        playback.previous()
    }

    fun toggleLike() {
        val current = state.value
        val event = if (current.liked) RecommendationEventType.UNLIKE else RecommendationEventType.LIKE
        _state.update { value ->
            val liked = if (value.liked) value.likedIds - value.nowTrack.id else value.likedIds + value.nowTrack.id
            value.copy(
                likedIds = liked,
                memoryTracks = (listOf(value.nowTrack) + value.memoryTracks).distinctBy(Track::id)
            ).also(::persist)
        }
        recordRecommendationEvent(current.nowTrack, event)
    }

    /** Toggles a favorite from a queue row without changing the active track. */
    fun toggleLike(track: Track) {
        val wasLiked = track.id in state.value.likedIds
        _state.update { value ->
            val liked = if (wasLiked) value.likedIds - track.id else value.likedIds + track.id
            value.copy(
                likedIds = liked,
                memoryTracks = (listOf(track) + value.memoryTracks).distinctBy(Track::id)
            ).also(::persist)
        }
        recordRecommendationEvent(track, if (wasLiked) RecommendationEventType.UNLIKE else RecommendationEventType.LIKE)
    }

    fun toggleShuffle() = _state.update {
        it.copy(isShuffleEnabled = !it.isShuffleEnabled).also { next ->
            playback.setShuffle(next.isShuffleEnabled)
            persist(next)
        }
    }

    fun toggleRepeat() = _state.update {
        it.copy(repeatMode = it.repeatMode.next()).also { next ->
            playback.setRepeat(next.repeatMode)
            persist(next)
        }
    }

    override fun onAudioSessionIdChanged(sessionId: Int) {
        // EQ is applied in the Media3 PCM pipeline, not through a device-specific
        // AudioEffect session. This callback is retained for the playback seam.
    }

    fun downloadCurrentTrack(): Job = downloadTrack(state.value.nowTrack)

    /** Saves every eligible track currently in the queue one by one. */
    fun downloadQueue(): Job = viewModelScope.launch {
        val queue = state.value.queue
            .filter { it.id != DemoCatalog.tracks.first().id }
            .filter { it.sourceId in setOf("muzofond", "vol") && it.streamUrl?.startsWith("https://") == true }
            .distinctBy(Track::id)
        if (queue.isEmpty()) {
            _state.update { it.copy(assistantText = "В очереди нет треков, доступных для офлайн-сохранения.") }
            return@launch
        }
        var saved = 0
        _state.update { it.copy(assistantText = "Сохраняю очередь: 0 из ${queue.size}…") }
        queue.forEach { track ->
            if (track.id !in state.value.downloadedSourceTrackIds) {
                downloadTrack(track).join()
                if (track.id in state.value.downloadedSourceTrackIds) saved++
            } else {
                saved++
            }
            _state.update { it.copy(assistantText = "Сохраняю очередь: $saved из ${queue.size}…") }
        }
        _state.update { it.copy(assistantText = "Готово: $saved песен сохранено офлайн.") }
    }

    fun downloadTrack(track: Track): Job {
        offlineDownloadJob?.cancel()
        val job = viewModelScope.launch {
        if (track.id in state.value.downloadedSourceTrackIds) {
            _state.update { it.copy(assistantText = "Эта песня уже сохранена на телефоне.") }
            return@launch
        }
        if (track.id == DemoCatalog.tracks.first().id) {
            _state.update { it.copy(assistantText = "Сначала включи конкретную песню.") }
            return@launch
        }
        _state.update {
            it.copy(
                isLoading = true,
                offlineDownloadProgress = 0f,
                offlineDownloadTrackId = track.id,
                offlineDownloadBytes = 0L,
                offlineDownloadTotalBytes = -1L,
                assistantText = "Сохраняю ${track.title} на телефон…"
            )
        }
        try {
            offlineStore.download(track) { downloaded, total ->
                _state.update {
                    it.copy(
                        offlineDownloadProgress = if (total > 0L) downloaded.toFloat() / total else 0f,
                        offlineDownloadBytes = downloaded,
                        offlineDownloadTotalBytes = total
                    )
                }
            }
            refreshLocalMusic()
            _state.update { current -> current.copy(isLoading = false, assistantText = "Сохранила ${track.title} для офлайн-прослушивания.") }
        } catch (error: CancellationException) {
            _state.update { it.copy(isLoading = false, assistantText = "Загрузка отменена.") }
            throw error
        } catch (error: Throwable) {
            _state.update { current ->
                current.copy(
                    isLoading = false,
                    assistantText = when {
                        track.sourceId == "youtube" -> "Эту песню нельзя скачать из YouTube."
                        else -> "Не удалось сохранить песню: ${error.message ?: "поток недоступен"}"
                    }
                )
            }
        } finally {
            _state.update { it.copy(offlineDownloadProgress = null, offlineDownloadTrackId = null, offlineDownloadBytes = 0L, offlineDownloadTotalBytes = -1L) }
        }
        }
        offlineDownloadJob = job
        return job
    }

    fun cancelOfflineDownload() {
        offlineDownloadJob?.cancel()
        offlineDownloadJob = null
    }

    fun deleteOfflineTrack(track: Track): Job = viewModelScope.launch {
        if (track.sourceId != LocalMusicPlugin.ID || !track.id.startsWith("offline-")) return@launch
        offlineStore.remove(track)
        refreshLocalMusic()
        _state.update { it.copy(assistantText = "Удалена ${track.title} с телефона.") }
    }

    fun deleteAllOffline(): Job = viewModelScope.launch {
        val removed = offlineStore.removeAll()
        refreshLocalMusic()
        _state.update { it.copy(assistantText = if (removed == 0) "Офлайн-библиотека уже пуста." else "Удалено песен: $removed.") }
    }

    private fun deleteCurrentOfflineTrack(): Job = viewModelScope.launch {
        val track = state.value.nowTrack
        if (track.sourceId != LocalMusicPlugin.ID || !track.id.startsWith("offline-")) {
            _state.update { it.copy(assistantText = "Текущая песня не сохранена офлайн.") }
            return@launch
        }
        deleteOfflineTrack(track).join()
    }

    private fun playOfflineMusic(message: String): Job = viewModelScope.launch {
        val tracks = offlineStore.load()
        _state.update {
            it.copy(
                localTracks = sortOfflineTracks(tracks, it.offlineSort),
                offlineStorageBytes = offlineStore.storageBytes()
            )
        }
        if (tracks.isEmpty()) {
            _state.update { it.copy(destination = AuraDestination.LIBRARY, librarySection = LibrarySection.LOCAL, assistantText = "На телефоне пока нет скачанных песен.") }
            return@launch
        }
        val queue = tracks.shuffled()
        val first = queue.first()
        _state.update {
            it.copy(
                queue = queue,
                currentIndex = 0,
                destination = AuraDestination.LIBRARY,
                librarySection = LibrarySection.LOCAL,
                assistantText = message
            ).also(::persist)
        }
        startPlayback(first)
    }

    private fun offlineStatus(message: String): Job = viewModelScope.launch {
        val tracks = offlineStore.load()
        val bytes = offlineStore.storageBytes()
        _state.update {
            it.copy(
                localTracks = sortOfflineTracks(tracks, it.offlineSort),
                offlineStorageBytes = bytes,
                assistantText = "$message ${tracks.size} песен, ${formatStorageSize(bytes)}."
            )
        }
    }

    private fun searchOffline(query: String): Job = viewModelScope.launch {
        val tracks = offlineStore.load()
        _state.update {
            it.copy(
                localTracks = sortOfflineTracks(tracks, it.offlineSort),
                offlineStorageBytes = offlineStore.storageBytes(),
                offlineSearchQuery = query,
                destination = AuraDestination.LIBRARY,
                librarySection = LibrarySection.LOCAL,
                assistantText = if (tracks.any { track ->
                    track.title.contains(query, ignoreCase = true) || track.artist.contains(query, ignoreCase = true)
                }) "Нашла в офлайн-библиотеке." else "В офлайн-библиотеке ничего не нашла."
            )
        }
    }

    fun setEqualizer(preset: EqualizerPreset) {
        playback.setEqualizer(preset)
        preferences.edit {
            putString("equalizer_preset", preset.name.lowercase())
            putString("equalizer_bands", preset.defaultBands.joinToString(","))
        }
        _state.update {
            it.copy(
                equalizerPreset = preset,
                equalizerBands = preset.defaultBands.toList(),
                assistantText = "Эквалайзер: ${preset.label}"
            )
        }
    }

    fun setCustomEqualizer(bands: List<Float>) {
        val normalized = bands.take(5).map { it.coerceIn(-12f, 12f) }
            .let { values -> values + List(5 - values.size) { 0f } }
        playback.setEqualizerBands(normalized)
        preferences.edit {
            putString("equalizer_preset", EqualizerPreset.CUSTOM.name.lowercase())
            putString("equalizer_bands", normalized.joinToString(","))
        }
        _state.update {
            it.copy(
                equalizerPreset = EqualizerPreset.CUSTOM,
                equalizerBands = normalized,
                assistantText = "Пользовательский эквалайзер применён"
            )
        }
    }

    fun disableEqualizer() {
        playback.setEqualizer(EqualizerPreset.FLAT)
        preferences.edit {
            putString("equalizer_preset", EqualizerPreset.FLAT.name.lowercase())
            putString("equalizer_bands", EqualizerPreset.FLAT.defaultBands.joinToString(","))
        }
        _state.update {
            it.copy(
                equalizerPreset = EqualizerPreset.FLAT,
                equalizerBands = EqualizerPreset.FLAT.defaultBands.toList(),
                assistantText = "Эквалайзер выключен"
            )
        }
    }

    fun cycleEqualizer() {
        val next = EqualizerPreset.entries[(state.value.equalizerPreset.ordinal + 1) % EqualizerPreset.entries.size]
        if (next == EqualizerPreset.FLAT) disableEqualizer() else setEqualizer(next)
    }

    fun toggleCarMode() = _state.update { it.copy(isCarMode = !it.isCarMode) }
    fun openPlayer() = _state.update { it.copy(isPlayerExpanded = true, isQueueOpen = false) }
    fun closePlayer() = _state.update { it.copy(isPlayerExpanded = false) }
    fun openQueue() = _state.update { it.copy(isQueueOpen = true, isPlayerExpanded = false) }
    fun closeQueue() = _state.update { it.copy(isQueueOpen = false) }

    fun playNext(track: Track) = prepareQueueTrack(track) { ready ->
        applyQueueEdit(QueueEditor.playNext(state.value.queue, state.value.currentIndex, ready), "Следующим: ${ready.title}")
    }

    fun addToQueue(track: Track) = prepareQueueTrack(track) { ready ->
        applyQueueEdit(QueueEditor.addToEnd(state.value.queue, state.value.currentIndex, ready), "Добавлено в очередь: ${ready.title}")
    }

    fun playFromQueue(track: Track) {
        val index = state.value.queue.indexOfFirst { it.id == track.id }
        if (index < 0) return
        _state.update {
            it.copy(
                currentIndex = index,
                positionMs = 0L,
                playbackDurationMs = track.durationMs ?: 0L,
                isPlaying = true,
                assistantText = "Играет ${track.title}"
            ).also(::persist)
        }
        playback.playTrack(track.id)
    }

    fun removeFromQueue(track: Track) = applyQueueEdit(
        QueueEditor.remove(state.value.queue, state.value.currentIndex, track.id),
        "Убрано из очереди: ${track.title}"
    )

    fun removeLastFromQueue() = applyQueueEdit(
        QueueEditor.removeLast(state.value.queue, state.value.currentIndex),
        "Последний трек удалён из очереди"
    )

    fun setSleepTimer(minutes: Int) {
        val safeMinutes = minutes.coerceIn(1, 240)
        _state.update {
            it.copy(
                sleepTimerEndsAt = System.currentTimeMillis() + safeMinutes * 60_000L,
                assistantText = "Остановлю музыку через $safeMinutes минут."
            )
        }
    }

    fun cancelSleepTimer() = _state.update {
        it.copy(sleepTimerEndsAt = null, assistantText = "Таймер сна выключен.")
    }

    fun setStopAfterTrack(enabled: Boolean) = _state.update {
        it.copy(
            stopAfterTrack = enabled,
            assistantText = if (enabled) "Остановлюсь после этой песни." else "Продолжу после этой песни."
        )
    }

    fun rejectCurrentTrack() {
        val current = state.value.nowTrack
        if (current.id == DemoCatalog.tracks.first().id) return
        recordRecommendationEvent(current, RecommendationEventType.SKIP)
        _state.update { it.copy(skippedTrackIds = it.skippedTrackIds + current.id, assistantText = "Убрала этот вариант.") }
        if (hasPreparedMedia) next()
    }

    fun moveQueueTrack(from: Int, to: Int) = applyQueueEdit(
        QueueEditor.move(state.value.queue, state.value.currentIndex, from, to),
        "Порядок очереди изменён"
    )

    fun clearQueue() = applyQueueEdit(
        QueueEditor.clear(state.value.queue, state.value.currentIndex),
        "Очередь очищена"
    )

    fun toggleAutoContinue() = _state.update { current ->
        current.copy(
            autoContinueEnabled = !current.autoContinueEnabled,
            assistantText = if (current.autoContinueEnabled) "Автопродолжение выключено."
            else "Автопродолжение включено."
        ).also(::persist)
    }

    fun restoreQueue(snapshot: AuraQueueSnapshot) {
        if (snapshot.tracks.isEmpty()) return
        archiveCurrentQueue("До восстановления")
        val index = snapshot.currentIndex.coerceIn(0, snapshot.tracks.lastIndex)
        val selected = snapshot.tracks[index]
        _state.update {
            it.copy(
                queue = snapshot.tracks,
                currentIndex = index,
                positionMs = snapshot.positionMs,
                playbackDurationMs = selected.durationMs ?: 0L,
                isQueueOpen = false,
                isPlayerExpanded = true,
                isPlaying = false,
                assistantText = "Очередь восстановлена: ${snapshot.title}"
            ).also(::persist)
        }
        playback.restore(snapshot.tracks, selected, snapshot.positionMs)
        hasPreparedMedia = true
    }

    fun deleteQueueSnapshot(snapshotId: String) {
        viewModelScope.launch {
            stateRepository.deleteQueueSnapshot(snapshotId)
            refreshQueueHistory()
        }
    }

    private fun prepareQueueTrack(track: Track, onReady: (Track) -> Unit) {
        if (!track.streamUrl.isNullOrBlank()) {
            onReady(track)
            return
        }
        val candidate = candidatesByTrackId[track.id]
        if (candidate == null) {
            _state.update { it.copy(assistantText = "Сначала найди доступную версию ${track.title}.") }
            return
        }
        _state.update {
            it.copy(isLoading = true, searchPhase = SearchPhase.RESOLVING, assistantText = "Готовлю ${track.title} для очереди…")
        }
        viewModelScope.launch {
            when (val result = resolveCandidate(candidate)) {
                is ProviderResult.Success -> {
                    val ready = unifiedTrackSession.canonicalize(candidate, result.value.track)
                    _state.update { value -> value.copy(isLoading = false, searchPhase = SearchPhase.IDLE) }
                    onReady(ready)
                }
                is ProviderResult.Failure -> _state.update {
                    it.copy(
                        isLoading = false,
                        searchPhase = SearchPhase.ERROR,
                        assistantText = friendlyFailure(result.reason, searching = false)
                    )
                }
            }
        }
    }

    private fun applyQueueEdit(result: QueueEditResult, message: String) {
        val current = state.value
        if (result.tracks == current.queue && result.currentIndex == current.currentIndex) return
        _state.update {
            it.copy(
                queue = result.tracks,
                currentIndex = result.currentIndex,
                assistantText = message
            ).also(::persist)
        }
        playback.syncQueue(result.tracks)
    }

    private fun archiveCurrentQueue(reason: String) {
        val current = state.value
        val snapshot = playbackSnapshot(current)
        val title = "$reason · ${current.nowTrack.title}"
        viewModelScope.launch {
            stateRepository.archiveQueue(snapshot, title)
            refreshQueueHistory()
        }
    }

    private suspend fun refreshQueueHistory() {
        val history = stateRepository.loadQueueHistory()
        _state.update { it.copy(queueHistory = history) }
    }

    fun openPlaylist(playlistId: String) = _state.update { it.copy(selectedPlaylistId = playlistId) }

    fun closePlaylist() = _state.update { it.copy(selectedPlaylistId = null) }

    fun createPlaylist(name: String, includeQueue: Boolean = false) {
        val tracks = if (includeQueue) state.value.queue else emptyList()
        updatePlaylists { stateRepository.createPlaylist(name, tracks) }
    }

    fun createPlaylistWithTrack(name: String, track: Track) = updatePlaylists {
        stateRepository.createPlaylist(name, listOf(track))
        _state.update { it.copy(assistantText = "Создан плейлист «${name.trim()}» с треком ${track.title}.") }
    }

    fun addTrackToPlaylist(playlistId: String, track: Track) = updatePlaylists {
        stateRepository.addToPlaylist(playlistId, listOf(track))
        val playlistName = state.value.playlists.firstOrNull { it.id == playlistId }?.name ?: "плейлист"
        _state.update { it.copy(assistantText = "${track.title} добавлен в «$playlistName».") }
    }

    fun saveQueueAsPlaylist(name: String) = createPlaylist(name, includeQueue = true)

    fun renamePlaylist(playlistId: String, name: String) = updatePlaylists {
        stateRepository.renamePlaylist(playlistId, name)
    }

    fun deletePlaylist(playlistId: String) = updatePlaylists {
        stateRepository.deletePlaylist(playlistId)
        _state.update { value ->
            value.copy(selectedPlaylistId = value.selectedPlaylistId.takeUnless { it == playlistId })
        }
    }

    fun addCurrentToPlaylist(playlistId: String) = updatePlaylists {
        stateRepository.addToPlaylist(playlistId, listOf(state.value.nowTrack))
    }

    fun addQueueToPlaylist(playlistId: String) = updatePlaylists {
        stateRepository.addToPlaylist(playlistId, state.value.queue)
    }

    fun removeFromPlaylist(playlistId: String, trackId: String) = updatePlaylists {
        stateRepository.removeFromPlaylist(playlistId, trackId)
    }

    fun movePlaylistTrack(playlistId: String, from: Int, to: Int) = updatePlaylists {
        stateRepository.movePlaylistTrack(playlistId, from, to)
    }

    fun shufflePlaylist(playlistId: String) = updatePlaylists {
        stateRepository.shufflePlaylist(playlistId)
    }

    fun playPlaylist(playlist: AuraPlaylist, shuffled: Boolean = false) =
        playPlaylistInternal(playlist, shuffled, 0)

    fun playPlaylistFrom(playlist: AuraPlaylist, index: Int) =
        playPlaylistInternal(playlist, shuffled = false, startIndex = index)

    private fun playPlaylistInternal(playlist: AuraPlaylist, shuffled: Boolean, startIndex: Int): Job? {
        if (playlist.tracks.isEmpty()) return null
        _state.update {
            it.copy(
                isLoading = true,
                assistantText = "Готовлю плейлист ${playlist.name}…",
                searchPhase = SearchPhase.RESOLVING
            )
        }
        return viewModelScope.launch {
            val source = if (shuffled) playlist.tracks.shuffled() else playlist.tracks
            val requestedId = source.getOrNull(if (shuffled) 0 else startIndex)?.id
            val queue = preparePlaylistTracks(source)
            if (queue.isEmpty()) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        searchPhase = SearchPhase.ERROR,
                        assistantText = "В плейлисте пока нет доступных треков."
                    )
                }
                return@launch
            }
            val selected = queue.firstOrNull { it.id == requestedId } ?: queue.first()
            val selectedIndex = queue.indexOfFirst { it.id == selected.id }.coerceAtLeast(0)
            archiveCurrentQueue("До плейлиста")
            _state.update {
                it.copy(
                    queue = queue,
                    currentIndex = selectedIndex,
                    positionMs = 0L,
                    playbackDurationMs = selected.durationMs ?: 0L,
                    isLoading = false,
                    isBuffering = true,
                    isPlayerExpanded = true,
                    searchPhase = SearchPhase.BUFFERING,
                    assistantText = "Играет плейлист ${playlist.name}."
                ).also(::persist)
            }
            hasPreparedMedia = true
            playback.play(queue, selected)
        }
    }

    private fun updatePlaylists(action: suspend () -> Unit) {
        viewModelScope.launch {
            runCatching { action() }
                .onSuccess { refreshPlaylists() }
                .onFailure {
                    _state.update { value -> value.copy(assistantText = "Не удалось изменить плейлист.") }
                }
        }
    }

    private suspend fun refreshPlaylists() {
        val playlists = stateRepository.loadPlaylists()
        _state.update { value ->
            value.copy(
                playlists = playlists,
                selectedPlaylistId = value.selectedPlaylistId?.takeIf { id -> playlists.any { it.id == id } }
            )
        }
    }

    private suspend fun preparePlaylistTracks(tracks: List<Track>): List<Track> = buildList {
        for (track in tracks) {
            if (!track.streamUrl.isNullOrBlank()) {
                add(track)
                continue
            }
            if (track.sourceId != "youtube") continue
            val videoId = track.id.removePrefix("youtube:")
            if (!YOUTUBE_VIDEO_ID.matches(videoId)) continue
            val candidate = TrackCandidate(
                providerId = "youtube",
                id = videoId,
                title = track.title,
                artist = track.artist,
                detailUrl = track.sourcePageUrl,
                artworkUrl = track.artworkUrl,
                durationMs = track.durationMs,
                playbackToken = videoId,
                year = track.year,
                popularity = track.popularity?.toLong()
            )
            val resolved = resolveCandidate(candidate)
            if (resolved is ProviderResult.Success) add(resolved.value.track)
        }
    }

    fun playMyMix() = startRecommendation("Собираю твой микс…") { context ->
        musicBrain.buildMyMix(context)
    }

    fun continueListening() = startRecommendation("Продолжаю твою музыку…") { context ->
        musicBrain.buildContinueQueue(context)
    }

    fun playSimilarMix() {
        val track = state.value.nowTrack
        if (track.id == DemoCatalog.tracks.first().id) {
            playMyMix()
            return
        }
        startRecommendation("Подбираю похожее на ${track.title}…") { context ->
            musicBrain.buildSimilarQueue(track, context)
        }
    }

    fun playMoodMix(mood: Mood) = startRecommendation("Подбираю настроение…") { context ->
        musicBrain.buildMoodQueue(mood, context)
    }

    private fun startRecommendation(
        message: String,
        loader: suspend (RecommendationContext) -> List<Track>
    ) {
        if (state.value.isRecommendationLoading) return
        _state.update {
            it.copy(
                isRecommendationLoading = true,
                isLoading = true,
                assistantText = message,
                searchPhase = SearchPhase.SEARCHING
            )
        }
        viewModelScope.launch {
            val queue = runCatching { loader(recommendationContext()) }.getOrElse { emptyList() }
            if (queue.isEmpty()) {
                _state.update {
                    it.copy(
                        isRecommendationLoading = false,
                        isLoading = false,
                        searchPhase = SearchPhase.ERROR,
                        assistantText = "Пока мало истории для персонального микса. Включи несколько любимых песен."
                    )
                }
                return@launch
            }
            val first = queue.first()
            archiveCurrentQueue("До нового микса")
            _state.update {
                it.copy(
                    queue = queue,
                    personalMix = queue,
                    currentIndex = 0,
                    positionMs = 0L,
                    playbackDurationMs = first.durationMs ?: 0L,
                    isRecommendationLoading = false,
                    isLoading = false,
                    isBuffering = true,
                    isPlayerExpanded = true,
                    searchPhase = SearchPhase.BUFFERING,
                    assistantText = "Готово. Начинаю с ${first.title}."
                ).also(::persist)
            }
            hasPreparedMedia = true
            playback.play(queue, first)
        }
    }

    fun refreshLocalMusic() {
        viewModelScope.launch {
            val tracks = localProvider.load()
            val current = state.value
            _state.update {
                it.copy(
                localTracks = sortOfflineTracks(tracks, current.offlineSort),
                offlineStorageBytes = offlineStore.storageBytes(),
                downloadedSourceTrackIds = offlineStore.downloadedSourceTrackIds()
                )
            }
        }
    }

    fun setOfflineSort(sort: OfflineSort) {
        _state.update { current ->
            current.copy(
                offlineSort = sort,
                localTracks = sortOfflineTracks(current.localTracks, sort)
            )
        }
    }

    fun setOfflineSearchQuery(query: String) {
        _state.update { it.copy(offlineSearchQuery = query) }
    }

    fun isTrackDownloaded(track: Track): Boolean = track.id in state.value.downloadedSourceTrackIds

    private fun sortOfflineTracks(tracks: List<Track>, sort: OfflineSort): List<Track> = when (sort) {
        OfflineSort.RECENT -> tracks.sortedByDescending { it.addedAt ?: 0L }
        OfflineSort.TITLE -> tracks.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
        OfflineSort.ARTIST -> tracks.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.artist })
    }

    private fun searchAndQueue(query: String, playNext: Boolean): Job {
        _state.update {
            it.copy(
                isLoading = true,
                searchPhase = SearchPhase.SEARCHING,
                assistantText = if (playNext) "Ищу трек, который сыграет следующим…" else "Ищу трек для очереди…"
            )
        }
        return viewModelScope.launch {
            when (val search = searchCandidates(MusicSearchRequest(rawQuery = query, autoPlay = false))) {
                is ProviderResult.Success -> {
                    val candidate = search.value.firstOrNull()
                    if (candidate == null) {
                        _state.update { it.copy(isLoading = false, searchPhase = SearchPhase.ERROR, assistantText = "Трек не найден.") }
                        return@launch
                    }
                    when (val resolved = resolveCandidate(candidate)) {
                        is ProviderResult.Success -> {
                            val track = unifiedTrackSession.canonicalize(candidate, resolved.value.track)
                            _state.update { it.copy(isLoading = false, searchPhase = SearchPhase.IDLE) }
                            val current = state.value
                            val edit = if (playNext) {
                                QueueEditor.playNext(current.queue, current.currentIndex, track)
                            } else {
                                QueueEditor.addToEnd(current.queue, current.currentIndex, track)
                            }
                            applyQueueEdit(edit, if (playNext) "Следующим: ${track.title}" else "Добавлено в очередь: ${track.title}")
                        }
                        is ProviderResult.Failure -> _state.update {
                            it.copy(
                                isLoading = false,
                                searchPhase = SearchPhase.ERROR,
                                assistantText = friendlyFailure(resolved.reason, searching = false)
                            )
                        }
                    }
                }
                is ProviderResult.Failure -> _state.update {
                    it.copy(
                        isLoading = false,
                        searchPhase = SearchPhase.ERROR,
                        assistantText = friendlyFailure(search.reason, searching = true)
                    )
                }
            }
        }
    }

    private fun searchTracks(request: MusicSearchRequest, append: Boolean = false): Job {
        if (!append) {
            searchJob?.cancel()
            searchGeneration += 1
        }
        val generation = searchGeneration
        lastSearchRequest = request
        val job = viewModelScope.launch {
            val startedAt = System.currentTimeMillis()
            when (val result = searchCandidates(request)) {
                is ProviderResult.Success -> {
                    if (generation != searchGeneration) return@launch
                    if (!append) {
                        latestCandidates = result.value
                        candidatesByTrackId.clear()
                        playbackRecoveryAttempts.clear()
                    } else {
                        latestCandidates = (latestCandidates + result.value)
                            .distinctBy { "${it.providerId}:${it.id}" }
                    }
                    val pageTracks = result.value.map { candidate ->
                        candidate.toTrack().also { candidatesByTrackId[it.id] = candidate }
                    }
                    _state.update {
                        val tracks = if (append) {
                            (it.searchResults + pageTracks).distinctBy(Track::id)
                        } else {
                            pageTracks
                        }
                        it.copy(
                            searchResults = tracks,
                            isLoading = false,
                            isLoadingMore = false,
                            searchResultLimit = request.limit,
                            canLoadMore = result.value.size >= request.limit && request.limit < SearchPaging.MAX_LIMIT,
                            searchPhase = SearchPhase.MATCHING,
                            diagnostics = it.diagnostics.copy(
                                query = request.rawQuery,
                                candidates = result.value.map { candidate ->
                                    "${candidate.artist} — ${candidate.title}: ${(candidate.confidence * 100).toInt()}%"
                                },
                                searchTimeMs = System.currentTimeMillis() - startedAt
                            ),
                            assistantText = when {
                                tracks.isEmpty() -> "Ничего не нашла."
                                append -> "Загрузила ещё ${pageTracks.size} результатов."
                                else -> "Нашла ${tracks.size} треков. Сверяю лучший результат…"
                            }
                        )
                    }
                    enrichMissingArtwork(pageTracks)
                    if (generation != searchGeneration) return@launch
                    val best = result.value.firstOrNull()
                    if (!append && request.autoPlay && best != null && best.confidence >= AUTO_PLAY_CONFIDENCE) {
                        resolveAndPlay(best).join()
                    } else if (!append) {
                        _state.update { it.copy(searchPhase = SearchPhase.IDLE, assistantText = "Выбери нужный трек.") }
                    } else {
                        _state.update { it.copy(searchPhase = SearchPhase.IDLE) }
                    }
                }
                is ProviderResult.Failure -> {
                    if (generation != searchGeneration) return@launch
                    _state.update {
                    if (!append) unifiedTrackSession.clear()
                    it.copy(
                        isLoading = false,
                        isLoadingMore = false,
                        searchPhase = SearchPhase.ERROR,
                        diagnostics = it.diagnostics.copy(
                            query = request.rawQuery,
                            searchTimeMs = System.currentTimeMillis() - startedAt,
                            fallbackReason = result.message
                        ),
                        assistantText = friendlyFailure(result.reason, searching = true)
                    )
                    }
                }
            }
        }
        searchJob = job
        return job
    }

    private fun resolveAndPlay(
        candidate: TrackCandidate,
        resumeTrackId: String? = null,
        resumePositionMs: Long = 0L
    ): Job {
        _state.update {
            it.copy(
                isLoading = true,
                searchPhase = SearchPhase.RESOLVING,
                assistantText = "Получаю источник ${candidate.artist} — ${candidate.title}…"
            )
        }
        return viewModelScope.launch {
            val resolveStartedAt = System.currentTimeMillis()
            val ordered = unifiedTrackSession.fallbackOrder(candidate, latestCandidates, 4)
            var lastError = "Подходящий источник не найден"
            for ((index, option) in ordered.take(4).withIndex()) {
                if (index > 0) {
                    _state.update {
                        it.copy(
                            assistantText = "Первый источник недоступен. Ищу другой вариант…",
                            searchPhase = SearchPhase.RESOLVING,
                            diagnostics = it.diagnostics.copy(fallbackReason = lastError)
                        )
                    }
                }
                when (val result = resolveCandidate(option)) {
                    is ProviderResult.Success -> {
                        val resolved = unifiedTrackSession.canonicalize(option, result.value.track)
                        _state.update {
                            it.copy(
                                searchResults = it.searchResults.map { track -> if (track.id == resolved.id) resolved else track },
                                isLoading = false,
                                searchPhase = SearchPhase.BUFFERING,
                                diagnostics = it.diagnostics.copy(
                                    selectedProvider = providerDisplayName(result.value.providerId),
                                    selectedPage = result.value.sourcePageUrl,
                                    resolver = result.diagnostics["stage"] ?: "unknown",
                                    resolveTimeMs = System.currentTimeMillis() - resolveStartedAt,
                                    mimeType = result.value.mimeType ?: "unknown",
                                    expiresAt = result.value.expiresAt,
                                    validationStatus = result.diagnostics["httpCode"] ?: "WebView",
                                    fallbackReason = if (index == 0) "—" else lastError
                                ),
                                assistantText = "Источник готов. Запускаю ${resolved.title}…"
                            )
                        }
                        val failedTrack = resumeTrackId?.let { id -> state.value.queue.find { it.id == id } }
                        val canResume = resumeTrackId != null &&
                            resolved.id == resumeTrackId &&
                            durationCompatible(failedTrack?.durationMs, resolved.durationMs)
                        if (canResume) {
                            replacePlaybackSource(resolved, resumePositionMs)
                        } else {
                            startPlayback(resolved)
                        }
                        prefetchFollowing(resolved.id)
                        return@launch
                    }
                    is ProviderResult.Failure -> lastError = friendlyFailure(result.reason, searching = false)
                }
            }
            _state.update {
                it.copy(
                    isLoading = false,
                    isBuffering = false,
                    searchPhase = SearchPhase.ERROR,
                    diagnostics = it.diagnostics.copy(
                        resolveTimeMs = System.currentTimeMillis() - resolveStartedAt,
                        fallbackReason = lastError
                    ),
                    assistantText = "Не удалось включить трек: $lastError"
                )
            }
        }
    }

    private fun prefetchFollowing(currentTrackId: String) {
        viewModelScope.launch {
            latestCandidates
                .filterNot { unifiedTrackSession.canonicalId(it) == currentTrackId }
                .take(3)
                .forEach { candidate ->
                    val result = resolveCandidate(candidate)
                    if (result is ProviderResult.Success) {
                        val resolved = unifiedTrackSession.canonicalize(candidate, result.value.track)
                        val alreadyPrepared = state.value.queue.any { it.id == resolved.id && !it.streamUrl.isNullOrBlank() }
                        if (!alreadyPrepared) {
                            _state.update { current ->
                                current.copy(
                                    queue = current.queue.map { if (it.id == resolved.id) resolved else it },
                                    searchResults = current.searchResults.map { if (it.id == resolved.id) resolved else it }
                                )
                            }
                            persist(_state.value)
                            playback.append(resolved)
                        }
                    }
                }
        }
    }

    private fun startPlayback(track: Track) {
        val current = state.value
        val playbackQueue = when {
            current.localTracks.any { it.id == track.id } -> current.localTracks
            current.searchResults.any { it.id == track.id } -> current.searchResults
            else -> listOf(track)
        }.map { if (it.id == track.id) track else it }
        val index = playbackQueue.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
        val history = listOf(track.id) + current.historyIds.filterNot { it == track.id }
        if (current.queue.map(Track::id) != playbackQueue.map(Track::id)) {
            archiveCurrentQueue("До нового поиска")
        }
        _state.update {
            it.copy(
                queue = playbackQueue,
                currentIndex = index,
                historyIds = history.take(30),
                memoryTracks = (listOf(track) + current.memoryTracks).distinctBy(Track::id).take(100),
                isPlayerExpanded = true,
                isBuffering = true,
                searchPhase = SearchPhase.BUFFERING,
                positionMs = 0L,
                playbackDurationMs = track.durationMs ?: 0L,
                assistantText = "Буферизация ${track.title}…"
            ).also(::persist)
        }
        enrichMissingArtwork(listOf(track))
        hasPreparedMedia = true
        playback.play(playbackQueue, track)
        if (track.sourceId == "radio_browser") {
            viewModelScope.launch { radioProvider.registerClick(track.id.removePrefix("radio_browser:")) }
        }
    }

    private fun enrichMissingArtwork(tracks: List<Track>) {
        val missing = tracks
            .filterNot { ArtistArtworkLookup.isUsable(it.artworkUrl) }
            .distinctBy(Track::id)
            .take(8)
        if (missing.isEmpty()) return
        viewModelScope.launch {
            val replacements = coroutineScope {
                missing.map { track ->
                    async {
                        artworkLookup.lookup(track.artist, track.title)?.let { track.id to it }
                    }
                }.awaitAll().filterNotNull().toMap()
            }
            if (replacements.isEmpty()) return@launch
            _state.update { current ->
                fun updateArtwork(items: List<Track>): List<Track> = items.map { track ->
                    replacements[track.id]?.let { track.copy(artworkUrl = it) } ?: track
                }
                current.copy(
                    searchResults = updateArtwork(current.searchResults),
                    queue = updateArtwork(current.queue),
                    personalMix = updateArtwork(current.personalMix),
                    memoryTracks = updateArtwork(current.memoryTracks),
                    localTracks = updateArtwork(current.localTracks)
                ).also(::persist)
            }
        }
    }

    private fun replacePlaybackSource(track: Track, positionMs: Long) {
        _state.update { current ->
            current.copy(
                queue = current.queue.map { if (it.id == track.id) track else it },
                searchResults = current.searchResults.map { if (it.id == track.id) track else it },
                isLoading = false,
                isBuffering = true,
                searchPhase = SearchPhase.BUFFERING,
                positionMs = positionMs,
                playbackDurationMs = track.durationMs ?: current.playbackDurationMs,
                assistantText = "Источник изменён. Продолжаю ${track.title}…"
            ).also(::persist)
        }
        hasPreparedMedia = true
        playback.replaceCurrent(track, positionMs)
    }

    private fun playCurrent() {
        val current = state.value
        when {
            hasPreparedMedia -> playback.play()
            !current.nowTrack.streamUrl.isNullOrBlank() -> startPlayback(current.nowTrack)
            else -> _state.update { it.copy(assistantText = "Назови песню, которую хочешь включить.") }
        }
    }

    private suspend fun searchCandidates(request: MusicSearchRequest): ProviderResult<List<TrackCandidate>> {
        return when (val outcome = musicBrain.search(request)) {
            is SearchOutcome.Success -> ProviderResult.Success(
                value = unifiedTrackSession.replace(outcome.tracks).take(request.limit),
                diagnostics = outcome.diagnostics + mapOf(
                    "engine" to "plugin-core",
                    "unifiedTracks" to outcome.tracks.size.toString()
                )
            )
            is SearchOutcome.Failure -> ProviderResult.Failure(
                reason = outcome.reason.toProviderFailureReason(),
                message = outcome.message
            )
        }
    }

    private suspend fun resolveCandidate(candidate: TrackCandidate): ProviderResult<PlayableSource> {
        return when (val result = providerManager.resolve(candidate)) {
            is PluginResult.Success -> ProviderResult.Success(result.value, result.diagnostics)
            is PluginResult.Failure -> ProviderResult.Failure(
                result.reason.toProviderFailureReason(),
                result.message,
                result.cause,
                result.diagnostics
            )
        }
    }

    private fun TrackCandidate.toTrack() = Track(
        id = unifiedTrackSession.canonicalId(this),
        title = title,
        artist = artist,
        artworkUrl = artworkUrl,
        durationMs = durationMs,
        sourceId = providerId,
        sourcePageUrl = detailUrl,
        playbackType = PlaybackType.DIRECT_STREAM,
        isPlayable = true
    )

    private fun recommendationContext(): RecommendationContext {
        val current = state.value
        val knownTracks = (current.memoryTracks + current.queue + current.personalMix + current.searchResults + current.localTracks)
            .associateBy(Track::id)
        val recent = current.historyIds.mapNotNull(knownTracks::get)
            .ifEmpty { current.queue.asReversed() }
        return RecommendationContext(
            queue = current.queue,
            currentIndex = current.currentIndex,
            recentTracks = recent,
            likedTrackIds = current.likedIds,
            skippedTrackIds = current.skippedTrackIds,
            preferredArtists = current.memoryFacts
                .filter { it.category == "preference" && it.key == "artist" }
                .map { it.value }
                .toSet(),
            preferredGenres = current.memoryFacts
                .filter { it.category == "preference" && it.key == "genre" }
                .map { it.value }
                .toSet(),
            dislikedArtists = current.memoryFacts
                .filter { it.category == "dislike" && it.key == "artist" }
                .map { it.value }
                .toSet(),
            hourOfDay = Calendar.getInstance().get(Calendar.HOUR_OF_DAY),
            carMode = current.isCarMode
        )
    }

    private fun recordRecommendationEvent(track: Track, type: RecommendationEventType) {
        if (track.id == DemoCatalog.tracks.first().id) return
        if (type == RecommendationEventType.SKIP) {
            _state.update { it.copy(skippedTrackIds = it.skippedTrackIds + track.id) }
        }
        val context = recommendationContext()
        viewModelScope.launch {
            runCatching {
                stateRepository.recordRecommendationEvent(
                    track = track,
                    type = type,
                    context = "hour=${context.hourOfDay};car=${context.carMode}"
                )
            }
        }
    }

    private fun maybeExtendQueue() {
        val current = state.value
        if (!current.autoContinueEnabled || isExtendingQueue || current.queue.size < 2) return
        if (current.currentIndex < current.queue.lastIndex - AUTO_CONTINUE_THRESHOLD) return
        isExtendingQueue = true
        viewModelScope.launch {
            try {
                val existingIds = state.value.queue.mapTo(mutableSetOf(), Track::id)
                val additions = musicBrain.extendQueue(recommendationContext())
                    .filterNot { it.id in existingIds }
                    .take(AUTO_CONTINUE_BATCH)
                if (additions.isEmpty()) return@launch
                _state.update { value ->
                    value.copy(
                        queue = value.queue + additions,
                        assistantText = "Добавила похожие треки в продолжение очереди."
                    ).also(::persist)
                }
                additions.forEach(playback::append)
            } finally {
                isExtendingQueue = false
            }
        }
    }

    private fun CoreFailureReason.toProviderFailureReason(): ProviderFailureReason = when (this) {
        CoreFailureReason.NETWORK -> ProviderFailureReason.NETWORK
        CoreFailureReason.TIMEOUT -> ProviderFailureReason.TIMEOUT
        CoreFailureReason.PARSE -> ProviderFailureReason.PARSE
        CoreFailureReason.NOT_FOUND -> ProviderFailureReason.NOT_FOUND
        CoreFailureReason.NOT_PLAYABLE -> ProviderFailureReason.NOT_PLAYABLE
        CoreFailureReason.ACCESS_RESTRICTED -> ProviderFailureReason.ACCESS_RESTRICTED
        CoreFailureReason.RATE_LIMITED,
        CoreFailureReason.DISABLED,
        CoreFailureReason.UNSUPPORTED,
        CoreFailureReason.UNKNOWN -> ProviderFailureReason.UNKNOWN
    }

    override fun onPlaybackChanged(isPlaying: Boolean, isBuffering: Boolean) {
        _state.update {
            it.copy(
                isPlaying = isPlaying,
                isBuffering = isBuffering,
                searchPhase = when {
                    isBuffering -> SearchPhase.BUFFERING
                    isPlaying -> SearchPhase.PLAYING
                    else -> it.searchPhase
                }
            )
        }
    }

    override fun onTrackChanged(trackId: String) {
        val shouldStopAfterPrevious = state.value.stopAfterTrack && state.value.nowTrack.id != trackId
        _state.update { current ->
            val index = current.queue.indexOfFirst { it.id == trackId }
            if (index < 0) current else current.copy(
                currentIndex = index,
                positionMs = 0L,
                playbackDurationMs = current.queue[index].durationMs ?: 0L,
                historyIds = (listOf(trackId) + current.historyIds.filterNot { it == trackId }).take(30),
                memoryTracks = (listOf(current.queue[index]) + current.memoryTracks)
                    .distinctBy(Track::id)
                    .take(100),
                searchPhase = SearchPhase.PLAYING,
                assistantText = "Играет ${current.queue[index].title}."
            ).also(::persist)
        }
        state.value.queue.find { it.id == trackId }?.let {
            recordRecommendationEvent(it, RecommendationEventType.PLAY)
        }
        if (shouldStopAfterPrevious) {
            playback.pause()
            _state.update { it.copy(stopAfterTrack = false, assistantText = "Музыка остановлена после песни.") }
        }
        maybeExtendQueue()
    }

    override fun onPlaybackError(trackId: String?, message: String) {
        val failedTrack = trackId?.let { id -> state.value.queue.firstOrNull { it.id == id } }
        val offlineAlternative = failedTrack?.takeUnless { it.sourceId == LocalMusicPlugin.ID }?.let { failed ->
            state.value.localTracks.firstOrNull { local ->
                local.title.equals(failed.title, ignoreCase = true) &&
                    local.artist.equals(failed.artist, ignoreCase = true)
            }
        }
        if (offlineAlternative != null) {
            switchToOfflineCopy(offlineAlternative)
            return
        }
        val candidate = trackId?.let(candidatesByTrackId::get)
        val attempts = trackId?.let { playbackRecoveryAttempts[it] } ?: 0
        if (candidate != null && attempts < 1) {
            playbackRecoveryAttempts[trackId] = attempts + 1
            _state.update { it.copy(assistantText = message, searchPhase = SearchPhase.RESOLVING) }
            resolveAndPlay(
                candidate = candidate,
                resumeTrackId = trackId,
                resumePositionMs = playback.currentPositionMs()
            )
            return
        }
        val fallback = candidate?.let { failed ->
            latestCandidates.firstOrNull { option ->
                option.id != failed.id && playbackRecoveryAttempts["${option.providerId}:${option.id}"] == null
            }
        }
        if (trackId != null && fallback != null) {
            playbackRecoveryAttempts[trackId] = attempts + 1
            _state.update {
                it.copy(
                    assistantText = "Этот поток не играет. Переключаюсь на следующий подходящий вариант…",
                    searchPhase = SearchPhase.RESOLVING
                )
            }
            resolveAndPlay(fallback)
            return
        }
        _state.update {
            it.copy(isPlaying = false, isBuffering = false, searchPhase = SearchPhase.ERROR, assistantText = message)
        }
    }

    private fun switchToOfflineCopy(track: Track) {
        val current = state.value
        val index = current.currentIndex.coerceIn(0, current.queue.lastIndex.coerceAtLeast(0))
        val queue = current.queue.toMutableList().apply {
            if (isNotEmpty()) this[index] = track
        }
        _state.update {
            it.copy(
                queue = queue,
                currentIndex = index,
                isBuffering = true,
                searchPhase = SearchPhase.BUFFERING,
                assistantText = "Интернет недоступен. Включаю локальную копию ${track.title}."
            ).also(::persist)
        }
        hasPreparedMedia = true
        playback.replaceCurrent(track, playback.currentPositionMs())
    }

    override fun onCleared() {
        geminiAudioInput.stop()
        geminiSession.close()
        speech.shutdown()
        playback.release()
        stopVoiceForegroundService()
        super.onCleared()
    }

    private fun persist(state: AuraUiState) {
        persistenceQueue.trySend(playbackSnapshot(state))
    }

    private fun playbackSnapshot(state: AuraUiState) = AuraPlaybackSnapshot(
        queue = state.queue,
        currentIndex = state.currentIndex,
        positionMs = state.positionMs,
        shuffleEnabled = state.isShuffleEnabled,
        repeatMode = state.repeatMode,
        autoContinueEnabled = state.autoContinueEnabled,
        likedIds = state.likedIds,
        historyIds = state.historyIds,
        recentSearches = state.recentSearches,
        memoryTracks = state.memoryTracks
    )

    private fun repeatModeMessage(mode: AuraRepeatMode): String = when (mode) {
        AuraRepeatMode.OFF -> "Повтор выключен."
        AuraRepeatMode.ONE -> "Повторяю текущий трек."
        AuraRepeatMode.ALL -> "Повторяю всю очередь."
    }

    private fun friendlyFailure(reason: ProviderFailureReason, searching: Boolean): String = when (reason) {
        ProviderFailureReason.NOT_FOUND -> if (searching) "Песня не найдена. Уточни исполнителя и название." else "Другой вариант не найден."
        ProviderFailureReason.ACCESS_RESTRICTED -> "Этот трек сейчас недоступен для обычного прослушивания."
        ProviderFailureReason.TIMEOUT -> "Источник отвечает слишком долго. Попробуй ещё раз."
        ProviderFailureReason.NETWORK -> "Не удалось связаться с музыкальным источником. Проверь интернет."
        ProviderFailureReason.NOT_PLAYABLE -> "Источник не воспроизводится. Ищу другой вариант."
        ProviderFailureReason.PARSE, ProviderFailureReason.UNKNOWN -> "Музыкальный источник временно недоступен."
    }

    private fun formatStorageSize(bytes: Long): String = when {
        bytes >= 1_000_000_000L -> "%.1f ГБ".format(bytes / 1_000_000_000f)
        bytes >= 1_000_000L -> "%.1f МБ".format(bytes / 1_000_000f)
        bytes >= 1_000L -> "%.0f КБ".format(bytes / 1_000f)
        else -> "$bytes Б"
    }

    private fun durationCompatible(left: Long?, right: Long?): Boolean =
        left == null || right == null || abs(left - right) <= 10_000L

    private fun String.isGenericMoodQuery(mood: Mood): Boolean {
        val withoutMoodWords = lowercase()
            .replace(Regex("спокойн\\p{L}*|расслаб\\p{L}*|релакс\\p{L}*|дорог\\p{L}*|поездк\\p{L}*|энерг\\p{L}*|трениров\\p{L}*|груст\\p{L}*|печал\\p{L}*|вес[её]л\\p{L}*|радост\\p{L}*|ночн\\p{L}*|вечерн\\p{L}*|колыбельн\\p{L}*|баю|усып\\p{L}*|lullaby|bedtime|nursery|calm|relax|drive|focus|energy|sad|happy|night|sakit|kədərli|şad|gecə|beşik"), " ")
            .replace(Regex("музык\\p{L}*|песн\\p{L}*|трек\\p{L}*|music|song|track|musiqi|mahnı"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        return withoutMoodWords.isBlank() || trim().equals(mood.title, ignoreCase = true)
    }

    companion object {
        private const val WORLD_PLAYLIST_ITEM_TIMEOUT_MS = 12_000L
        private const val WORLD_PLAYLIST_CONCURRENCY = 3
        private const val MAX_WORLD_PLAYLIST_ITEMS = 32
        private const val AUTO_PLAY_CONFIDENCE = 0.72
        private const val EARLY_SKIP_THRESHOLD_MS = 30_000L
        private const val AUTO_CONTINUE_THRESHOLD = 2
        private const val AUTO_CONTINUE_BATCH = 6
        private val YOUTUBE_VIDEO_ID = Regex("[A-Za-z0-9_-]{11}")
    }
}
