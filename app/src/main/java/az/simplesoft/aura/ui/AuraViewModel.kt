package az.simplesoft.aura.ui

import android.app.Application
import android.content.Context
import android.media.AudioManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.core.content.edit
import az.simplesoft.aura.assistant.AssistantMessage
import az.simplesoft.aura.assistant.AssistantReply
import az.simplesoft.aura.assistant.AssistantRole
import az.simplesoft.aura.assistant.AssistantSource
import az.simplesoft.aura.assistant.AuraAiContext
import az.simplesoft.aura.assistant.AuraAiEngine
import az.simplesoft.aura.assistant.AuraSpeechSynthesizer
import az.simplesoft.aura.assistant.AuraWakeWordService
import az.simplesoft.aura.assistant.RecognitionBackend
import az.simplesoft.aura.assistant.VoiceCaptureDiagnostics
import az.simplesoft.aura.assistant.VoiceInputState
import az.simplesoft.aura.assistant.CompactAssistantMemory
import az.simplesoft.aura.assistant.DataBackedCompanionEngine
import az.simplesoft.aura.assistant.AssistantCommandCoordinator
import az.simplesoft.aura.assistant.ActionExecutionResult
import az.simplesoft.aura.assistant.LocalIntentEngine
import az.simplesoft.aura.assistant.MusicIntent
import az.simplesoft.aura.assistant.Mood
import az.simplesoft.aura.assistant.RoomAssistantMemoryPersistence
import az.simplesoft.aura.data.DemoCatalog
import az.simplesoft.aura.data.LocalMusicProvider
import az.simplesoft.aura.data.PlaybackType
import az.simplesoft.aura.data.RadioBrowserProvider
import az.simplesoft.aura.data.RadioCountry
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
import az.simplesoft.aura.data.plugins.radio.RadioMusicPlugin
import az.simplesoft.aura.data.plugins.youtube.YouTubeMusicPlugin
import az.simplesoft.aura.data.providers.MusicSearchRequest
import az.simplesoft.aura.data.providers.PlayableSource
import az.simplesoft.aura.data.providers.ProviderResult
import az.simplesoft.aura.data.providers.ProviderFailureReason
import az.simplesoft.aura.data.providers.TrackCandidate
import az.simplesoft.aura.data.search.CandidateRankerV2
import az.simplesoft.aura.data.search.TrackIdentityResolver
import az.simplesoft.aura.data.search.UnifiedTrackSession
import az.simplesoft.aura.domain.music.MusicBrain
import az.simplesoft.aura.domain.music.AuraRepeatMode
import az.simplesoft.aura.domain.music.PersonalRecommendationEngine
import az.simplesoft.aura.domain.music.QueueEditResult
import az.simplesoft.aura.domain.music.QueueEditor
import az.simplesoft.aura.domain.music.RecommendationContext
import az.simplesoft.aura.domain.music.SearchOutcome
import az.simplesoft.aura.playback.PlaybackConnection
import az.simplesoft.aura.playback.PlaybackConnectionCoordinator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import java.util.Calendar
import kotlin.math.abs

enum class AuraDestination { HOME, SEARCH, RADIO, LIBRARY, ASSISTANT, DIAGNOSTICS }
enum class LibrarySection { FAVORITES, HISTORY, LOCAL, PLAYLISTS }
enum class SearchPhase { IDLE, SEARCHING, MATCHING, RESOLVING, BUFFERING, PLAYING, ERROR }

data class ProviderDiagnostics(
    val engine: String = "Plugin Core · YouTube",
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
    val fallbackReason: String = "—"
)

data class AuraUiState(
    val destination: AuraDestination = AuraDestination.HOME,
    val librarySection: LibrarySection = LibrarySection.FAVORITES,
    val query: String = "",
    val recentSearches: List<String> = emptyList(),
    val searchResults: List<Track> = emptyList(),
    val radioCountries: List<RadioCountry> = emptyList(),
    val radioStations: List<Track> = emptyList(),
    val selectedRadioCountry: RadioCountry? = null,
    val isRadioLoading: Boolean = false,
    val radioError: String? = null,
    val personalMix: List<Track> = emptyList(),
    val playlists: List<AuraPlaylist> = emptyList(),
    val queueHistory: List<AuraQueueSnapshot> = emptyList(),
    val selectedPlaylistId: String? = null,
    val assistantText: String = "Привет! Я AURA. Что будем слушать?",
    val assistantMessages: List<AssistantMessage> = emptyList(),
    val isAssistantThinking: Boolean = false,
    val assistantSource: AssistantSource = AssistantSource.LOCAL,
    val isOfflineOnly: Boolean = true,
    val isListening: Boolean = false,
    val voiceInputState: VoiceInputState = VoiceInputState.Idle,
    val recognitionBackend: RecognitionBackend = RecognitionBackend.Unavailable,
    val voiceDiagnostics: VoiceCaptureDiagnostics? = null,
    val wakeWordEnabled: Boolean = false,
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

class AuraViewModel(application: Application) : AndroidViewModel(application), PlaybackConnection.Listener {
    private val intentEngine = LocalIntentEngine()
    private val localProvider = LocalMusicProvider(application)
    private val radioProvider = RadioBrowserProvider()
    private val preferences = application.getSharedPreferences("aura_state", Context.MODE_PRIVATE)
    private val stateRepository = AuraStateRepository(application)
    private val assistantMemory = CompactAssistantMemory(RoomAssistantMemoryPersistence(stateRepository))
    private val assistantCommandCoordinator = AssistantCommandCoordinator(application)
    private val auraAi = AuraAiEngine(
        local = intentEngine,
        memory = assistantMemory,
        dataBackedCompanion = DataBackedCompanionEngine(application)
    )
    private val speech = AuraSpeechSynthesizer(application)
    private val audio = application.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val playback = PlaybackConnection(application, this)
    private val providerManager = ProviderManager(
        setOf(
            LocalMusicPlugin(localProvider),
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
    private val playbackCoordinator = PlaybackConnectionCoordinator(playback) { state.value.queue }
    private val musicBrain = MusicBrain(
        providerManager = providerManager,
        candidateRanker = candidateRanker,
        identityResolver = identityResolver,
        recommendationEngine = recommendationEngine,
        playbackCoordinator = playbackCoordinator
    )
    private val unifiedTrackSession = UnifiedTrackSession()
    private val candidatesByTrackId = mutableMapOf<String, TrackCandidate>()
    private val playbackRecoveryAttempts = mutableMapOf<String, Int>()
    private var latestCandidates: List<TrackCandidate> = emptyList()
    private var hasPreparedMedia = false
    private val persistenceQueue = Channel<AuraPlaybackSnapshot>(Channel.CONFLATED)
    private var positionPersistenceTick = 0
    private var isExtendingQueue = false
    private var stateRestored = false
    private var pendingCommand: String? = null
    private var pendingCommandShouldSpeak = false
    private val claimedPendingCommands = mutableSetOf<String>()

    private val initialLiked = preferences.getStringSet("liked", emptySet()).orEmpty().toSet()
    private val initialHistory = preferences.getString("history", "")
        .orEmpty().split('|').filter(String::isNotBlank)
    private val initialWakeWordEnabled = preferences.getBoolean("wake_word_enabled", false)

    private val _state = MutableStateFlow(
        AuraUiState(
            likedIds = initialLiked,
            historyIds = initialHistory,
            wakeWordEnabled = initialWakeWordEnabled
        )
    )
    val state = _state.asStateFlow()

    init {
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
                    current.copy(
                        queue = restored.queue.ifEmpty { current.queue },
                        memoryTracks = restored.memoryTracks,
                        currentIndex = if (restored.queue.isEmpty()) current.currentIndex else restored.currentIndex,
                        positionMs = restored.positionMs,
                        playbackDurationMs = restored.queue.getOrNull(restored.currentIndex)?.durationMs ?: 0L,
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
            librarySection = if (destination == AuraDestination.LIBRARY) LibrarySection.PLAYLISTS else it.librarySection,
            isPlayerExpanded = false,
            isQueueOpen = false
        )
    }

    fun openRadio() {
        _state.update { it.copy(destination = AuraDestination.RADIO, isPlayerExpanded = false, isQueueOpen = false) }
        if (state.value.radioCountries.isEmpty()) loadRadioCountries() else if (state.value.radioStations.isEmpty()) {
            state.value.selectedRadioCountry?.let(::selectRadioCountry)
        }
    }

    fun loadRadioCountries() {
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
                    if (selected != null) loadRadioStations(selected)
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

    fun selectRadioCountry(country: RadioCountry) {
        _state.update { it.copy(selectedRadioCountry = country, isRadioLoading = true, radioError = null) }
        loadRadioStations(country)
    }

    fun refreshRadio() {
        val country = state.value.selectedRadioCountry
        if (country == null) loadRadioCountries() else selectRadioCountry(country)
    }

    private fun loadRadioStations(country: RadioCountry) {
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
                voiceInputState = value,
                recognitionBackend = backend
            )
        }
    }

    fun setVoiceDiagnostics(value: VoiceCaptureDiagnostics) = _state.update {
        it.copy(voiceDiagnostics = value)
    }

    fun startPushToTalk(start: () -> Unit) {
        viewModelScope.launch {
            if (state.value.wakeWordEnabled) {
                runCatching { AuraWakeWordService.stop(getApplication()) }
                delay(350L)
            }
            start()
        }
    }

    fun resumeWakeWordAfterPushToTalk() {
        if (!state.value.wakeWordEnabled) return
        runCatching { AuraWakeWordService.start(getApplication()) }
    }

    /** Must be invoked while the activity is visible: Android blocks background microphone starts. */
    fun setWakeWordEnabled(enabled: Boolean) {
        runCatching {
            if (enabled) AuraWakeWordService.start(getApplication())
            else AuraWakeWordService.stop(getApplication())
        }.onSuccess {
            preferences.edit { putBoolean("wake_word_enabled", enabled) }
            _state.update { it.copy(wakeWordEnabled = enabled) }
        }.onFailure {
            _state.update { current ->
                current.copy(
                    assistantText = "Не удалось включить голосовую активацию. Проверь разрешение на микрофон."
                )
            }
        }
    }

    fun resumeWakeWordServiceIfNeeded() {
        if (!state.value.wakeWordEnabled) return
        runCatching { AuraWakeWordService.start(getApplication()) }
            .onFailure {
                _state.update { current ->
                    current.copy(
                        wakeWordEnabled = false,
                        assistantText = "Голосовая активация выключена: разреши доступ к микрофону."
                    )
                }
            }
    }

    fun submit(text: String = state.value.query) = submitAssistant(text, speakResponse = false)

    fun submitVoice(text: String) = submitAssistant(text, speakResponse = true)

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

    private fun submitAssistant(text: String, speakResponse: Boolean, pendingCommandId: String? = null) {
        val input = text.trim()
        if (input.isBlank()) return
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
                assistantText = when (language) {
                    az.simplesoft.aura.assistant.AssistantLanguage.RUSSIAN -> "Думаю…"
                    az.simplesoft.aura.assistant.AssistantLanguage.AZERBAIJANI -> "Düşünürəm…"
                    az.simplesoft.aura.assistant.AssistantLanguage.ENGLISH -> "Thinking…"
                },
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
                    playlists = current.playlists.map { it.name }
                )
            )
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
            if (speakResponse) speech.speak(responseText, answer.language)
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
            MusicIntent.OpenQueue -> {
                _state.update { it.copy(isQueueOpen = true, isPlayerExpanded = false, assistantText = answer.text) }
                return null
            }
            MusicIntent.OpenRadio -> {
                openRadio()
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
                    searchPhase = SearchPhase.SEARCHING
                )
            }
            persist(_state.value)
            return searchTracks(MusicSearchRequest(rawQuery = query, artist = requestedSearch.artist))
        }

        if (answer.intent == MusicIntent.Similar) {
            playSimilarMix()
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
                MusicIntent.OpenRadio,
                is MusicIntent.CreatePlaylist,
                is MusicIntent.PlayPlaylist,
                MusicIntent.OpenQueue,
                MusicIntent.ClearQueue,
                is MusicIntent.QueueTrack,
                is MusicIntent.AutoContinue -> current
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

    fun togglePlay() {
        if (state.value.isPlaying) playback.pause() else playCurrent()
    }

    fun seekTo(positionMs: Long) = playback.seekTo(positionMs)

    fun next() {
        if (!hasPreparedMedia) return
        val current = state.value
        val earlySkip = current.positionMs in 1 until EARLY_SKIP_THRESHOLD_MS &&
            (current.playbackDurationMs == 0L || current.positionMs * 4 < current.playbackDurationMs)
        if (earlySkip) recordRecommendationEvent(current.nowTrack, RecommendationEventType.SKIP)
        playback.next()
    }

    fun previous() {
        if (hasPreparedMedia) playback.previous()
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
        playback.playAt(index)
    }

    fun removeFromQueue(track: Track) = applyQueueEdit(
        QueueEditor.remove(state.value.queue, state.value.currentIndex, track.id),
        "Убрано из очереди: ${track.title}"
    )

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
            _state.update { it.copy(localTracks = tracks) }
        }
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

    private fun searchTracks(request: MusicSearchRequest): Job {
        return viewModelScope.launch {
            val startedAt = System.currentTimeMillis()
            when (val result = searchCandidates(request)) {
                is ProviderResult.Success -> {
                    latestCandidates = result.value
                    candidatesByTrackId.clear()
                    playbackRecoveryAttempts.clear()
                    val tracks = result.value.map { candidate ->
                        candidate.toTrack().also { candidatesByTrackId[it.id] = candidate }
                    }
                    _state.update {
                        it.copy(
                            searchResults = tracks,
                            isLoading = false,
                            searchPhase = SearchPhase.MATCHING,
                            diagnostics = it.diagnostics.copy(
                                query = request.rawQuery,
                                candidates = result.value.map { candidate ->
                                    "${candidate.artist} — ${candidate.title}: ${(candidate.confidence * 100).toInt()}%"
                                },
                                searchTimeMs = System.currentTimeMillis() - startedAt
                            ),
                            assistantText = if (tracks.isEmpty()) "Ничего не нашла." else "Нашла ${tracks.size} треков. Сверяю лучший результат…"
                        )
                    }
                    val best = result.value.firstOrNull()
                    if (request.autoPlay && best != null && best.confidence >= AUTO_PLAY_CONFIDENCE) {
                        resolveAndPlay(best).join()
                    } else {
                        _state.update { it.copy(searchPhase = SearchPhase.IDLE, assistantText = "Выбери нужный трек.") }
                    }
                }
                is ProviderResult.Failure -> _state.update {
                    unifiedTrackSession.clear()
                    it.copy(
                        isLoading = false,
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
                                    selectedProvider = result.value.providerId,
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
        hasPreparedMedia = true
        playback.play(playbackQueue, track)
        if (track.sourceId == "radio_browser") {
            viewModelScope.launch { radioProvider.registerClick(track.id.removePrefix("radio_browser:")) }
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
        maybeExtendQueue()
    }

    override fun onPlaybackError(trackId: String?, message: String) {
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

    override fun onCleared() {
        speech.shutdown()
        playback.release()
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

    private fun durationCompatible(left: Long?, right: Long?): Boolean =
        left == null || right == null || abs(left - right) <= 10_000L

    private fun String.isGenericMoodQuery(mood: Mood): Boolean {
        val withoutMoodWords = lowercase()
            .replace(Regex("спокойн\\p{L}*|расслаб\\p{L}*|релакс\\p{L}*|дорог\\p{L}*|поездк\\p{L}*|энерг\\p{L}*|трениров\\p{L}*|груст\\p{L}*|печал\\p{L}*|вес[её]л\\p{L}*|радост\\p{L}*|ночн\\p{L}*|вечерн\\p{L}*|calm|relax|drive|focus|energy|sad|happy|night|sakit|kədərli|şad|gecə"), " ")
            .replace(Regex("музык\\p{L}*|песн\\p{L}*|трек\\p{L}*|music|song|track|musiqi|mahnı"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        return withoutMoodWords.isBlank() || trim().equals(mood.title, ignoreCase = true)
    }

    companion object {
        private const val AUTO_PLAY_CONFIDENCE = 0.72
        private const val EARLY_SKIP_THRESHOLD_MS = 30_000L
        private const val AUTO_CONTINUE_THRESHOLD = 2
        private const val AUTO_CONTINUE_BATCH = 6
        private val YOUTUBE_VIDEO_ID = Regex("[A-Za-z0-9_-]{11}")
    }
}
