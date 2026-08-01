package az.simplesoft.aura.ui

import android.app.Application
import android.content.Context
import android.media.AudioManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.core.content.edit
import az.simplesoft.aura.assistant.LocalIntentEngine
import az.simplesoft.aura.assistant.MusicIntent
import az.simplesoft.aura.data.DemoCatalog
import az.simplesoft.aura.data.LocalMusicProvider
import az.simplesoft.aura.data.PlaybackType
import az.simplesoft.aura.data.RadioBrowserProvider
import az.simplesoft.aura.data.Track
import az.simplesoft.aura.data.database.AuraPlaybackSnapshot
import az.simplesoft.aura.data.database.AuraStateRepository
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
import az.simplesoft.aura.domain.music.EmptyRecommendationEngine
import az.simplesoft.aura.domain.music.MusicBrain
import az.simplesoft.aura.domain.music.SearchOutcome
import az.simplesoft.aura.playback.PlaybackConnection
import az.simplesoft.aura.playback.PlaybackConnectionCoordinator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.Channel
import kotlin.math.abs

enum class AuraDestination { HOME, SEARCH, LIBRARY, ASSISTANT, DIAGNOSTICS }
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
    val assistantText: String = "Назови песню, которую хочешь услышать",
    val isListening: Boolean = false,
    val isLoading: Boolean = false,
    val isBuffering: Boolean = false,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val playbackDurationMs: Long = 0L,
    val isCarMode: Boolean = false,
    val isPlayerExpanded: Boolean = false,
    val isQueueOpen: Boolean = false,
    val isShuffleEnabled: Boolean = false,
    val isRepeatEnabled: Boolean = false,
    val searchPhase: SearchPhase = SearchPhase.IDLE,
    val diagnostics: ProviderDiagnostics = ProviderDiagnostics(),
    val queue: List<Track> = DemoCatalog.tracks,
    val localTracks: List<Track> = emptyList(),
    val currentIndex: Int = 0,
    val likedIds: Set<String> = emptySet(),
    val historyIds: List<String> = emptyList()
) {
    val nowTrack: Track get() = queue.getOrElse(currentIndex) { DemoCatalog.tracks.first() }
    val liked: Boolean get() = nowTrack.id in likedIds
    val favorites: List<Track> get() = queue.filter { it.id in likedIds }
    val history: List<Track> get() = historyIds.mapNotNull { id -> queue.find { it.id == id } }
}

class AuraViewModel(application: Application) : AndroidViewModel(application), PlaybackConnection.Listener {
    private val intentEngine = LocalIntentEngine()
    private val localProvider = LocalMusicProvider(application)
    private val radioProvider = RadioBrowserProvider()
    private val preferences = application.getSharedPreferences("aura_state", Context.MODE_PRIVATE)
    private val stateRepository = AuraStateRepository(application)
    private val audio = application.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val playback = PlaybackConnection(application, this)
    private val providerManager = ProviderManager(
        setOf(
            LocalMusicPlugin(localProvider),
            YouTubeMusicPlugin(),
            RadioMusicPlugin(radioProvider)
        )
    )
    private val playbackCoordinator = PlaybackConnectionCoordinator(playback) { state.value.queue }
    private val musicBrain = MusicBrain(
        providerManager = providerManager,
        candidateRanker = CandidateRankerV2(),
        identityResolver = TrackIdentityResolver(),
        recommendationEngine = EmptyRecommendationEngine,
        playbackCoordinator = playbackCoordinator
    )
    private val unifiedTrackSession = UnifiedTrackSession()
    private val candidatesByTrackId = mutableMapOf<String, TrackCandidate>()
    private val playbackRecoveryAttempts = mutableMapOf<String, Int>()
    private var latestCandidates: List<TrackCandidate> = emptyList()
    private var hasPreparedMedia = false
    private val persistenceQueue = Channel<AuraPlaybackSnapshot>(Channel.CONFLATED)
    private var positionPersistenceTick = 0

    private val initialLiked = preferences.getStringSet("liked", emptySet()).orEmpty().toSet()
    private val initialHistory = preferences.getString("history", "")
        .orEmpty().split('|').filter(String::isNotBlank)

    private val _state = MutableStateFlow(
        AuraUiState(likedIds = initialLiked, historyIds = initialHistory)
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
                _state.update { current ->
                    current.copy(
                        queue = restored.queue.ifEmpty { current.queue },
                        currentIndex = if (restored.queue.isEmpty()) current.currentIndex else restored.currentIndex,
                        positionMs = restored.positionMs,
                        playbackDurationMs = restored.queue.getOrNull(restored.currentIndex)?.durationMs ?: 0L,
                        isShuffleEnabled = restored.shuffleEnabled,
                        isRepeatEnabled = restored.repeatEnabled,
                        likedIds = restored.likedIds,
                        historyIds = restored.historyIds,
                        recentSearches = restored.recentSearches
                    )
                }
                playback.setShuffle(restored.shuffleEnabled)
                playback.setRepeat(restored.repeatEnabled)
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

    fun navigate(destination: AuraDestination) = _state.update {
        it.copy(destination = destination, isPlayerExpanded = false, isQueueOpen = false)
    }

    fun setLibrarySection(section: LibrarySection) = _state.update { it.copy(librarySection = section) }
    fun setListening(value: Boolean) = _state.update { it.copy(isListening = value) }
    fun setQuery(value: String) = _state.update { it.copy(query = value) }

    fun submit(text: String = state.value.query) {
        val input = text.trim()
        if (input.isBlank()) return
        val answer = intentEngine.understand(input)
        val requestedSearch = answer.intent as? MusicIntent.Search
        if (requestedSearch != null) {
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
            searchTracks(MusicSearchRequest(rawQuery = query, artist = requestedSearch.artist))
            return
        }

        if (answer.intent == MusicIntent.Similar) {
            val current = state.value.nowTrack
            val query = current.artist.takeUnless { current.id == DemoCatalog.tracks.first().id } ?: "популярная музыка"
            _state.update {
                it.copy(
                    destination = AuraDestination.SEARCH,
                    query = query,
                    assistantText = "Ищу похожие треки…",
                    isLoading = true,
                    searchPhase = SearchPhase.SEARCHING
                )
            }
            searchTracks(MusicSearchRequest(rawQuery = query, autoPlay = false))
            return
        }

        when (answer.intent) {
            MusicIntent.Play -> {
                _state.update { it.copy(assistantText = answer.text) }
                playCurrent()
                return
            }
            MusicIntent.Pause -> {
                _state.update { it.copy(assistantText = answer.text) }
                playback.pause()
                return
            }
            MusicIntent.Next -> {
                _state.update { it.copy(assistantText = answer.text) }
                next()
                return
            }
            MusicIntent.Previous -> {
                _state.update { it.copy(assistantText = answer.text) }
                previous()
                return
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
                    isRepeatEnabled = !current.isRepeatEnabled,
                    assistantText = answer.text
                ).also {
                    playback.setRepeat(it.isRepeatEnabled)
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
                MusicIntent.Similar, is MusicIntent.Search -> current
                MusicIntent.Unknown -> current.copy(assistantText = answer.text)
            }
        }
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
        if (hasPreparedMedia) playback.next()
    }

    fun previous() {
        if (hasPreparedMedia) playback.previous()
    }

    fun toggleLike() = _state.update { current ->
        val liked = if (current.liked) current.likedIds - current.nowTrack.id else current.likedIds + current.nowTrack.id
        current.copy(likedIds = liked).also(::persist)
    }

    fun toggleShuffle() = _state.update {
        it.copy(isShuffleEnabled = !it.isShuffleEnabled).also { next ->
            playback.setShuffle(next.isShuffleEnabled)
            persist(next)
        }
    }

    fun toggleRepeat() = _state.update {
        it.copy(isRepeatEnabled = !it.isRepeatEnabled).also { next ->
            playback.setRepeat(next.isRepeatEnabled)
            persist(next)
        }
    }

    fun toggleCarMode() = _state.update { it.copy(isCarMode = !it.isCarMode) }
    fun openPlayer() = _state.update { it.copy(isPlayerExpanded = true, isQueueOpen = false) }
    fun closePlayer() = _state.update { it.copy(isPlayerExpanded = false) }
    fun openQueue() = _state.update { it.copy(isQueueOpen = true, isPlayerExpanded = false) }
    fun closeQueue() = _state.update { it.copy(isQueueOpen = false) }

    fun removeFromQueue(track: Track) = _state.update { current ->
        if (current.queue.size <= 1) current else {
            val oldIndex = current.currentIndex
            val removedIndex = current.queue.indexOfFirst { it.id == track.id }
            val nextQueue = current.queue.filterNot { it.id == track.id }
            val nextIndex = when {
                removedIndex < 0 -> oldIndex
                removedIndex < oldIndex -> oldIndex - 1
                oldIndex >= nextQueue.size -> nextQueue.lastIndex
                else -> oldIndex
            }
            current.copy(queue = nextQueue, currentIndex = nextIndex.coerceAtLeast(0)).also(::persist)
        }
    }

    fun clearQueue() = _state.update { current ->
        current.copy(queue = listOf(current.nowTrack), currentIndex = 0).also(::persist)
    }

    fun refreshLocalMusic() {
        viewModelScope.launch {
            val tracks = localProvider.load()
            _state.update { it.copy(localTracks = tracks) }
        }
    }

    private fun searchTracks(request: MusicSearchRequest) {
        viewModelScope.launch {
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
                        resolveAndPlay(best)
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
    ) {
        _state.update {
            it.copy(
                isLoading = true,
                searchPhase = SearchPhase.RESOLVING,
                assistantText = "Получаю источник ${candidate.artist} — ${candidate.title}…"
            )
        }
        viewModelScope.launch {
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
        _state.update {
            it.copy(
                queue = playbackQueue,
                currentIndex = index,
                historyIds = history.take(30),
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
                searchPhase = SearchPhase.PLAYING,
                assistantText = "Играет ${current.queue[index].title}."
            ).also(::persist)
        }
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
        playback.release()
        super.onCleared()
    }

    private fun persist(state: AuraUiState) {
        persistenceQueue.trySend(
            AuraPlaybackSnapshot(
                queue = state.queue,
                currentIndex = state.currentIndex,
                positionMs = state.positionMs,
                shuffleEnabled = state.isShuffleEnabled,
                repeatEnabled = state.isRepeatEnabled,
                likedIds = state.likedIds,
                historyIds = state.historyIds,
                recentSearches = state.recentSearches
            )
        )
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

    companion object {
        private const val AUTO_PLAY_CONFIDENCE = 0.72
    }
}
