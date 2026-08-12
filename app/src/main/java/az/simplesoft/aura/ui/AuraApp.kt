package az.simplesoft.aura.ui

import az.simplesoft.aura.BuildConfig
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ClearAll
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.content.ContextCompat
import az.simplesoft.aura.assistant.DefaultVoiceInputController
import az.simplesoft.aura.assistant.OfflineModelManager
import az.simplesoft.aura.assistant.OfflineModelState
import az.simplesoft.aura.assistant.VoicePackManager
import az.simplesoft.aura.assistant.VoiceLabCatalog
import az.simplesoft.aura.assistant.VoiceEngineMode
import az.simplesoft.aura.assistant.VoicePackStatus
import az.simplesoft.aura.assistant.llm.LocalLlmModelManager
import az.simplesoft.aura.assistant.AssistantRole
import az.simplesoft.aura.assistant.AuraWakeWordBus
import az.simplesoft.aura.assistant.VoiceInputState
import az.simplesoft.aura.data.DemoCatalog
import az.simplesoft.aura.data.Track
import az.simplesoft.aura.data.RadioCountry
import az.simplesoft.aura.data.database.AuraPlaylist
import az.simplesoft.aura.data.database.AuraQueueSnapshot
import az.simplesoft.aura.domain.music.AuraRepeatMode
import coil.compose.AsyncImage
import java.text.SimpleDateFormat
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import java.util.Date
import java.util.Locale

private val AuraBlack = Color(0xFF050A11)
private val DeepSurface = Color(0xFF0D131B)
private val ElevatedSurface = Color(0xFF171D27)
private val PrimaryText = Color(0xFFF7F6FC)
private val SecondaryText = Color(0xFFA9AFBB)
private val AccentSilver = Color(0xFFDCCBFF)
private val AuraAccent = Color(0xFF7C3CFF)
private val AuraAccentSoft = Color(0xFFC076FF)
private val AuraMint = Color(0xFF6FE3D1)
private val AuraBorder = Color.White.copy(.09f)
private val ReferencePurple = Color(0xFF7C3CFF)
private val ReferenceMagenta = Color(0xFFC13DFF)
private val ReferenceBlue = Color(0xFF2467FF)

@Composable
fun AuraApp(
    initialCommand: String? = null,
    speakInitialCommand: Boolean = false,
    initialVoicePreview: String? = null,
    initialVoicePreviewLanguage: String? = null,
    vm: AuraViewModel = viewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val voiceController = remember(context) {
        DefaultVoiceInputController(
            context = context,
            onTranscript = vm::submitVoice,
            onDiagnostics = vm::setVoiceDiagnostics
        )
    }
    val voiceState by voiceController.state.collectAsStateWithLifecycle()
    val voiceBackend by voiceController.backend.collectAsStateWithLifecycle()
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) vm.startPushToTalk(voiceController::start)
        else vm.setVoiceInputState(VoiceInputState.PermissionRequired(Manifest.permission.RECORD_AUDIO), voiceBackend)
    }
    val voiceInput = {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            vm.startPushToTalk(voiceController::start)
        }
    }
    var playlistTarget by remember { mutableStateOf<Track?>(null) }
    DisposableEffect(Unit) { onDispose(voiceController::destroy) }
    LaunchedEffect(voiceState, voiceBackend) {
        vm.setVoiceInputState(voiceState, voiceBackend)
        when (voiceState) {
            is VoiceInputState.TranscriptReady,
            is VoiceInputState.PermissionRequired,
            is VoiceInputState.ModelRequired,
            is VoiceInputState.NoSpeech,
            is VoiceInputState.Failed -> vm.resumeWakeWordAfterPushToTalk()
            else -> Unit
        }
    }
    LaunchedEffect(initialCommand, speakInitialCommand) {
        initialCommand?.takeIf(String::isNotBlank)?.let {
            if (speakInitialCommand) vm.submitVoice(it) else vm.submit(it)
        }
    }
    LaunchedEffect(initialVoicePreview, initialVoicePreviewLanguage) {
        initialVoicePreview?.takeIf(String::isNotBlank)?.let {
            vm.previewVoice(it, if (initialVoicePreviewLanguage == "ru") az.simplesoft.aura.assistant.AssistantLanguage.RUSSIAN else az.simplesoft.aura.assistant.AssistantLanguage.AZERBAIJANI)
        }
    }
    LaunchedEffect(Unit) {
        vm.resumeWakeWordServiceIfNeeded()
        AuraWakeWordBus.commands.collect { command -> vm.submitVoice(command) }
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) vm.refreshLocalMusic()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    BackHandler(state.isCarMode) { vm.toggleCarMode() }
    BackHandler(!state.isCarMode && state.isQueueOpen) { vm.closeQueue() }
    BackHandler(
        !state.isCarMode && !state.isQueueOpen && !state.isPlayerExpanded && state.selectedPlaylistId != null
    ) { vm.closePlaylist() }
    BackHandler(
        !state.isCarMode && !state.isQueueOpen && !state.isPlayerExpanded && state.destination == AuraDestination.DIAGNOSTICS
    ) { vm.navigate(AuraDestination.HOME) }
    BackHandler(
        !state.isCarMode && !state.isQueueOpen && !state.isPlayerExpanded && state.destination == AuraDestination.CHAT_SCRIPT_LAB
    ) { vm.navigate(AuraDestination.DIAGNOSTICS) }
    BackHandler(
        !state.isCarMode && !state.isQueueOpen && !state.isPlayerExpanded && state.destination == AuraDestination.RADIO
    ) { vm.navigate(AuraDestination.HOME) }
    BackHandler(
        !state.isCarMode && !state.isQueueOpen && state.isPlayerExpanded
    ) { vm.closePlayer() }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = AuraBlack,
        contentColor = PrimaryText
    ) {
        Box(Modifier.fillMaxSize().background(AuraBlack)) {
            playlistTarget?.let { track ->
                PlaylistPickerDialog(
                    track = track,
                    playlists = state.playlists,
                    onDismiss = { playlistTarget = null },
                    onAdd = { playlistId ->
                        vm.addTrackToPlaylist(playlistId, track)
                        playlistTarget = null
                    },
                    onCreate = { name ->
                        vm.createPlaylistWithTrack(name, track)
                        playlistTarget = null
                    }
                )
            }
            when {
            state.isCarMode -> CarModeScreen(
                state = state,
                onPrevious = vm::previous,
                onPlay = vm::togglePlay,
                onNext = vm::next,
                onLike = vm::toggleLike,
                onExit = vm::toggleCarMode,
                onVoice = voiceInput
            )
            state.isQueueOpen -> QueueScreen(
                state = state,
                onClose = vm::closeQueue,
                onPlay = vm::playFromQueue,
                onRemove = vm::removeFromQueue,
                onMove = vm::moveQueueTrack,
                onClear = vm::clearQueue,
                onShuffle = vm::toggleShuffle,
                onRepeat = vm::toggleRepeat,
                onAutoContinue = vm::toggleAutoContinue,
                onSave = vm::saveQueueAsPlaylist,
                onRestore = vm::restoreQueue,
                onDeleteHistory = vm::deleteQueueSnapshot,
                onAddToPlaylist = { playlistTarget = it }
            )
            state.isPlayerExpanded -> PlayerScreen(
                state = state,
                onClose = vm::closePlayer,
                onPrevious = vm::previous,
                onPlay = vm::togglePlay,
                onNext = vm::next,
                onLike = vm::toggleLike,
                onShuffle = vm::toggleShuffle,
                onRepeat = vm::toggleRepeat,
                onQueue = vm::openQueue,
                onSeek = vm::seekTo,
                onCar = vm::toggleCarMode,
                onAddToPlaylist = { playlistTarget = state.nowTrack }
            )
                else -> MainShell(
                    state = state,
                    vm = vm,
                    onVoice = voiceInput,
                    onOpenSettings = {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:${context.packageName}")
                            )
                        )
                    },
                    onAddToPlaylist = { playlistTarget = it }
                )
            }
        }
    }
}

@Composable
private fun MainShell(
    state: AuraUiState,
    vm: AuraViewModel,
    onVoice: () -> Unit,
    onOpenSettings: () -> Unit,
    onAddToPlaylist: (Track) -> Unit
) {
    Box(Modifier.fillMaxSize()) {
        AuraBackground()
        AnimatedContent(
            targetState = state.destination,
            label = "destination",
            modifier = Modifier.fillMaxSize()
        ) { destination ->
            when (destination) {
                AuraDestination.HOME -> HomeScreen(
                    state = state,
                    onVoice = onVoice,
                    onMyMix = vm::playMyMix,
                    onTrack = vm::play,
                    onPlaylists = {
                        vm.setLibrarySection(LibrarySection.PLAYLISTS)
                        vm.navigate(AuraDestination.LIBRARY)
                    },
                    onQueue = vm::openQueue,
                    onRadio = vm::openRadio,
                    onAddToPlaylist = onAddToPlaylist,
                    onDiagnostics = { vm.navigate(AuraDestination.DIAGNOSTICS) }
                )
                AuraDestination.SEARCH -> SearchScreen(
                    state = state,
                    onQuery = vm::setQuery,
                    onSubmit = vm::search,
                    onVoice = onVoice,
                    onTrack = vm::play,
                    onPlayNext = vm::playNext,
                    onAddQueue = vm::addToQueue,
                    onAddPlaylist = onAddToPlaylist,
                    onRecent = vm::search
                )
                AuraDestination.RADIO -> RadioScreen(
                    state = state,
                    onBack = { vm.navigate(AuraDestination.HOME) },
                    onCountry = vm::selectRadioCountry,
                    onRefresh = vm::refreshRadio,
                    onStation = vm::play,
                    onPlayNext = vm::playNext,
                    onAddQueue = vm::addToQueue,
                    onAddPlaylist = onAddToPlaylist
                )
                AuraDestination.LIBRARY -> LibraryScreen(
                    state = state,
                    onSection = vm::setLibrarySection,
                    onTrack = vm::play,
                    onPlayNext = vm::playNext,
                    onAddTrackToQueue = vm::addToQueue,
                    onAddTrackToPlaylist = onAddToPlaylist,
                    onOpenPlaylist = vm::openPlaylist,
                    onClosePlaylist = vm::closePlaylist,
                    onCreatePlaylist = vm::createPlaylist,
                    onRenamePlaylist = vm::renamePlaylist,
                    onDeletePlaylist = vm::deletePlaylist,
                    onAddCurrent = vm::addCurrentToPlaylist,
                    onAddQueue = vm::addQueueToPlaylist,
                    onRemoveTrack = vm::removeFromPlaylist,
                    onMoveTrack = vm::movePlaylistTrack,
                    onShufflePlaylist = vm::shufflePlaylist,
                    onPlayPlaylist = vm::playPlaylist,
                    onPlayPlaylistFrom = vm::playPlaylistFrom
                )
                AuraDestination.ASSISTANT -> AssistantScreen(
                    state = state,
                    onVoice = onVoice,
                    onCommand = vm::submit,
                    onWakeWord = vm::setWakeWordEnabled,
                    onOpenSettings = onOpenSettings
                )
                AuraDestination.DIAGNOSTICS -> DiagnosticsScreen(
                    diagnostics = state.diagnostics,
                    onBack = { vm.navigate(AuraDestination.HOME) },
                    voiceState = state.voiceInputState,
                    voiceBackend = state.recognitionBackend,
                    voiceDiagnostics = state.voiceDiagnostics,
                    assistantDiagnostics = state.assistantDiagnostics,
                    localLlmDiagnostics = state.localLlmDiagnostics,
                    voiceEngineMode = state.voiceEngineMode,
                    onVoiceEngineMode = vm::setVoiceEngineMode,
                    brainEnabled = state.brainEnabled,
                    onBrainEnabled = vm::setBrainEnabled,
                    onTestVoice = onVoice,
                    onOpenChatScriptLab = { vm.navigate(AuraDestination.CHAT_SCRIPT_LAB) }
                )
                AuraDestination.CHAT_SCRIPT_LAB -> if (BuildConfig.DEBUG) {
                    ChatScriptLabScreen(onBack = { vm.navigate(AuraDestination.DIAGNOSTICS) })
                } else {
                    LaunchedEffect(Unit) { vm.navigate(AuraDestination.HOME) }
                }
            }
        }

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(Color.Transparent, AuraBlack, AuraBlack)))
                .navigationBarsPadding()
                .padding(horizontal = 14.dp)
                .padding(top = 14.dp, bottom = 8.dp)
        ) {
            if (state.nowTrack.id != DemoCatalog.tracks.first().id) {
                MiniPlayer(
                    state = state,
                    onOpen = vm::openPlayer,
                    onPlay = vm::togglePlay,
                    onLike = vm::toggleLike
                )
                Spacer(Modifier.height(8.dp))
            }
            if (state.destination != AuraDestination.CHAT_SCRIPT_LAB) {
                BottomNavigation(state.destination, vm::navigate, vm::openQueue)
            }
        }
    }
}

@Composable
private fun HomeScreen(
    state: AuraUiState,
    onVoice: () -> Unit,
    onMyMix: () -> Unit,
    onTrack: (Track) -> Unit,
    onPlaylists: () -> Unit,
    onQueue: () -> Unit,
    onRadio: () -> Unit,
    onAddToPlaylist: (Track) -> Unit,
    onDiagnostics: () -> Unit
) {
    val recent = state.history.ifEmpty { state.queue }
        .filterNot { it.id == DemoCatalog.tracks.first().id }
        .take(4)
    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 16.dp, end = 16.dp, top = 10.dp, bottom = 174.dp
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDiagnostics, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Rounded.Menu, "Меню", tint = PrimaryText)
                }
                Text(
                    "A U R A",
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    color = PrimaryText,
                    letterSpacing = 3.sp,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                IconButton(onClick = onVoice, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Rounded.GraphicEq, "Голос AURA", tint = ReferenceMagenta)
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("${greeting()}, Эмиль!", fontSize = 22.sp, lineHeight = 27.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(
                "Готова подобрать музыку\nпод твоё настроение",
                color = SecondaryText,
            fontSize = 14.sp,
            lineHeight = 19.sp
            )
            Spacer(Modifier.height(10.dp))
            AuraReferenceOrb(
                listening = state.isListening,
                onClick = onVoice,
                modifier = Modifier.fillMaxWidth().height(224.dp)
            )
        }
        item {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                    .background(Color(0xB51A202B))
                    .border(1.dp, Color.White.copy(.06f), RoundedCornerShape(18.dp))
                    .padding(12.dp)
            ) {
                Text("Быстрые действия", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HomeQuickAction(Icons.Rounded.AutoAwesome, "Мой микс", Modifier.weight(1f), onMyMix)
                    HomeQuickAction(Icons.AutoMirrored.Rounded.PlaylistPlay, "Плейлисты", Modifier.weight(1f), onPlaylists)
                    HomeQuickAction(Icons.AutoMirrored.Rounded.QueueMusic, "Очередь", Modifier.weight(1f), onQueue)
                    HomeQuickAction(Icons.Rounded.Radio, "Радио", Modifier.weight(1f), onRadio)
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Недавно слушал", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                Text("Ещё", color = ReferenceMagenta, fontSize = 13.sp)
            }
            Spacer(Modifier.height(8.dp))
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                    .background(Color(0xB5121720)).padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                if (recent.isEmpty()) {
                    Text("Здесь появятся последние треки", color = SecondaryText, modifier = Modifier.padding(16.dp))
                } else recent.forEach { track ->
                    TrackRow(
                        track = track,
                        liked = track.id in state.likedIds,
                        onClick = { onTrack(track) },
                        onAddPlaylist = { onAddToPlaylist(track) },
                        compact = true
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeQuickAction(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier.height(72.dp).clip(RoundedCornerShape(14.dp)).background(Color.White.copy(.045f))
            .clickable(onClick = onClick).padding(horizontal = 4.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Icon(icon, label, Modifier.size(22.dp), tint = PrimaryText)
        Text(label, fontSize = 9.sp, color = PrimaryText, maxLines = 1)
    }
}

@Composable
private fun AuraReferenceOrb(listening: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "auraOrb")
    val pulse by transition.animateFloat(.94f, 1.04f, infiniteRepeatable(tween(1000), RepeatMode.Reverse), label = "orbPulse")
    Canvas(modifier.clickable(onClick = onClick)) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val base = size.minDimension * .30f * if (listening) pulse else 1f
        drawCircle(
            brush = Brush.radialGradient(listOf(ReferenceMagenta.copy(.26f), Color.Transparent), center, base * 1.8f),
            radius = base * 1.8f,
            center = center
        )
        repeat(6) { ring ->
            drawCircle(
                color = if (ring % 2 == 0) ReferenceMagenta.copy(.40f - ring * .045f)
                    else ReferenceBlue.copy(.34f - ring * .04f),
                radius = base * (.72f + ring * .16f),
                center = center,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = if (ring < 2) 2.5f else 1.2f)
            )
        }
        drawCircle(
            brush = Brush.radialGradient(
                listOf(Color(0xFFBD75FF), ReferencePurple, Color(0xFF19102E)),
                center = Offset(center.x - base * .25f, center.y - base * .25f),
                radius = base
            ),
            radius = base * .52f,
            center = center
        )
        drawCircle(Color.White, radius = base * .055f, center = Offset(center.x - base * .16f, center.y - base * .04f))
        drawCircle(Color.White, radius = base * .055f, center = Offset(center.x + base * .16f, center.y - base * .04f))
        drawArc(
            color = Color.White,
            startAngle = 18f,
            sweepAngle = 144f,
            useCenter = false,
            topLeft = Offset(center.x - base * .16f, center.y - base * .02f),
            size = androidx.compose.ui.geometry.Size(base * .32f, base * .24f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = base * .035f)
        )
    }
}

@Composable
private fun AuraHero(
    state: AuraUiState,
    onQuery: (String) -> Unit,
    onSubmit: () -> Unit,
    onVoice: () -> Unit,
    onMyMix: () -> Unit,
    onContinue: () -> Unit,
    onSimilar: () -> Unit
) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(34.dp))
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF29234A), Color(0xFF171A31), Color(0xFF112B2C)),
                    start = Offset.Zero,
                    end = Offset(900f, 850f)
                )
            )
            .border(1.dp, AuraAccent.copy(.24f), RoundedCornerShape(34.dp))
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).background(AuraMint, CircleShape))
            Spacer(Modifier.width(8.dp))
            Text("MUSIC BRAIN АКТИВЕН", color = AuraMint, fontSize = 10.sp, letterSpacing = 1.4.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(16.dp))
        Text("Твоя музыка.\nВ нужный момент.", style = MaterialTheme.typography.displayLarge)
        Spacer(Modifier.height(9.dp))
        Text(state.assistantText, color = SecondaryText, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(18.dp))
        SearchField(state.query, onQuery, onSubmit, onVoice, state.isListening)
        Spacer(Modifier.height(14.dp))
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            HeroAction("Мой микс", Icons.Rounded.AutoAwesome, onMyMix)
            HeroAction("Продолжить", Icons.Rounded.PlayArrow, onContinue)
            if (state.nowTrack.id != DemoCatalog.tracks.first().id) {
                HeroAction("Похожее", Icons.Rounded.GraphicEq, onSimilar)
            }
        }
    }
}

@Composable
private fun HeroAction(label: String, icon: ImageVector, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = Color.White.copy(.075f),
        contentColor = PrimaryText,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color.White.copy(.10f))
    ) {
        Row(Modifier.padding(horizontal = 13.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(17.dp), tint = AuraAccentSoft)
            Spacer(Modifier.width(7.dp))
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun DiagnosticsScreen(
    diagnostics: ProviderDiagnostics,
    onBack: () -> Unit,
    voiceState: VoiceInputState,
    voiceBackend: az.simplesoft.aura.assistant.RecognitionBackend,
    voiceDiagnostics: az.simplesoft.aura.assistant.VoiceCaptureDiagnostics?,
    assistantDiagnostics: az.simplesoft.aura.assistant.DecisionDiagnostics?,
    localLlmDiagnostics: az.simplesoft.aura.assistant.llm.LocalLlmDiagnostics?,
    voiceEngineMode: VoiceEngineMode,
    onVoiceEngineMode: (VoiceEngineMode) -> Unit,
    brainEnabled: Boolean,
    onBrainEnabled: (Boolean) -> Unit,
    onTestVoice: () -> Unit,
    onOpenChatScriptLab: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val whisper = remember(context) { OfflineModelManager(context) }
    val voices = remember(context) { VoicePackManager(context) }
    val brain = remember(context) { LocalLlmModelManager(context) }
    val whisperState by whisper.state.collectAsStateWithLifecycle()
    val whisperProgress by whisper.progress.collectAsStateWithLifecycle()
    val voiceProgress by voices.progress.collectAsStateWithLifecycle()
    var packs by remember { mutableStateOf(voices.packs()) }
    var whisperJob by remember { mutableStateOf<Job?>(null) }
    var voiceJob by remember { mutableStateOf<Job?>(null) }
    var activeVoiceId by remember { mutableStateOf<String?>(null) }
    var deleteTarget by remember { mutableStateOf<String?>(null) }
    var brainJob by remember { mutableStateOf<Job?>(null) }
    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Назад") }
                Text("Диагностика AURA", style = MaterialTheme.typography.headlineLarge)
            }
            Text("Только debug-сборка", color = SecondaryText, fontSize = 12.sp)
        }
        item { DiagnosticRow("Маршрут", diagnostics.engine) }
        item { DiagnosticRow("Источник", diagnostics.selectedProvider) }
        item { DiagnosticRow("Запрос", diagnostics.query) }
        item { DiagnosticRow("Поиск", diagnostics.searchTimeMs?.let { "$it мс" } ?: "—") }
        item { DiagnosticRow("Resolve", diagnostics.resolveTimeMs?.let { "$it мс" } ?: "—") }
        item { DiagnosticRow("Resolver", diagnostics.resolver) }
        item { DiagnosticRow("Проверка HTTP", diagnostics.validationStatus) }
        item { DiagnosticRow("MIME", diagnostics.mimeType) }
        item { DiagnosticRow("Истекает", diagnostics.expiresAt?.let { Date(it).toString() } ?: "—") }
        item { DiagnosticRow("Fallback", diagnostics.fallbackReason) }
        item {
            Text("Voice input", color = SecondaryText, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            DiagnosticRow("Backend", voiceBackend.toString())
            DiagnosticRow("State", voiceState.toString())
            voiceDiagnostics?.let { capture ->
                DiagnosticRow("RMS / peak", "${"%.4f".format(capture.averageRms)} / ${"%.4f".format(capture.peakRms)}")
                DiagnosticRow("Capture", "${capture.captureDurationMs} ms, ${capture.sampleCount} samples")
                DiagnosticRow("Speech", if (capture.heardSpeech) "heard" else "not heard")
                DiagnosticRow("Transcription", capture.transcriptionDurationMs?.let { "$it ms" } ?: "-")
            }
            TextButton(onClick = onTestVoice) { Text("Test microphone") }
        }
        item {
            Text("Voice engine", color = SecondaryText, fontSize = 12.sp)
            Spacer(Modifier.height(4.dp))
            VoiceEngineMode.entries.forEach { mode ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = voiceEngineMode == mode, onClick = { onVoiceEngineMode(mode) })
                    Column {
                        Text(if (mode == VoiceEngineMode.SYSTEM) "System TTS" else "Verified Silero Kseniya v1", fontSize = 13.sp)
                        Text(if (mode == VoiceEngineMode.SYSTEM) "Android device voice" else "Russian only; falls back to system if not installed", color = SecondaryText, fontSize = 11.sp)
                    }
                }
            }
        }
        assistantDiagnostics?.let { decision ->
            item {
                Text("Assistant decision", color = SecondaryText, fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                DiagnosticRow("Raw transcript", decision.originalText)
                DiagnosticRow("Normalized", decision.normalizedText)
                DiagnosticRow("Language", decision.language.tag)
                DiagnosticRow("Top intents", decision.topIntents.joinToString(" · ").ifBlank { "—" })
                DiagnosticRow("Selected", decision.selectedIntent ?: "—")
                DiagnosticRow("Confidence", "${"%.2f".format(decision.confidence)}")
                DiagnosticRow("Entities", decision.entities.joinToString { "${it.type}:${it.value}" }.ifBlank { "—" })
                DiagnosticRow("Context", decision.contextReferences.joinToString().ifBlank { "—" })
                DiagnosticRow("Assistant latency", "${decision.processingTimeMs} ms")
            }
        }
        localLlmDiagnostics?.let { brainDiagnostics ->
            item {
                Text("Local brain run", color = SecondaryText, fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                DiagnosticRow("Model", "${brainDiagnostics.model} · ${brainDiagnostics.quantization}")
                DiagnosticRow("Threads", brainDiagnostics.threads.toString())
                DiagnosticRow("RAM", "${brainDiagnostics.ramBeforeMb} → ${brainDiagnostics.ramAfterMb} MB")
                DiagnosticRow("Load", "${brainDiagnostics.loadMs} ms")
                DiagnosticRow("Tokens", "${brainDiagnostics.promptTokens} prompt / ${brainDiagnostics.outputTokens} output")
                DiagnosticRow("Speed", "${"%.1f".format(brainDiagnostics.tokensPerSecond)} tok/s")
                DiagnosticRow("TTFT / total", "${brainDiagnostics.timeToFirstTokenMs} / ${brainDiagnostics.totalMs} ms")
                DiagnosticRow("Route", brainDiagnostics.route)
            }
        }
        item {
            Text("Local brain", color = SecondaryText, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Hybrid Local LLM", fontSize = 13.sp)
                    Text(if (brainEnabled) "Fast path + Brain Pack fallback" else "Legacy LocalAssistantEngine only", color = SecondaryText, fontSize = 11.sp)
                }
                androidx.compose.material3.Switch(checked = brainEnabled, onCheckedChange = onBrainEnabled)
            }
            brain.models().forEach { model ->
                val state = brain.state(model.id)
                val progress = brain.progress(model.id)
                ResourceCard(
                    title = model.displayName,
                    status = state.name,
                    detail = "${model.quantization} · ${model.sizeBytes / 1_000_000} MB · ${progress.percent}%",
                    actionLabel = when (state) {
                        az.simplesoft.aura.assistant.llm.BrainModelState.READY -> "Delete"
                        az.simplesoft.aura.assistant.llm.BrainModelState.DOWNLOADING,
                        az.simplesoft.aura.assistant.llm.BrainModelState.VERIFYING -> "Cancel"
                        else -> "Install"
                    },
                    onAction = {
                        when (state) {
                            az.simplesoft.aura.assistant.llm.BrainModelState.READY -> brainJob = scope.launch { brain.delete(model.id) }
                            az.simplesoft.aura.assistant.llm.BrainModelState.DOWNLOADING,
                            az.simplesoft.aura.assistant.llm.BrainModelState.VERIFYING -> brainJob?.cancel()
                            else -> brainJob = scope.launch { runCatching { brain.install(model.id) }; brainJob = null }
                        }
                    }
                )
            }
        }
        if (BuildConfig.DEBUG) {
            item {
                Text("Experiments", color = SecondaryText, fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onOpenChatScriptLab, modifier = Modifier.fillMaxWidth()) {
                    Text("Open ChatScript Lab")
                }
                Text("Isolated debug-only rule brain; never used by AURA production routing.", color = SecondaryText, fontSize = 11.sp)
            }
        }
        item { DiagnosticRow("Страница", diagnostics.selectedPage) }
        item {
            Text("Кандидаты", color = SecondaryText, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            diagnostics.candidates.forEach { candidate ->
                Text(candidate, Modifier.padding(vertical = 5.dp), color = AccentSilver)
            }
        }
        item {
            Text("Offline storage", color = SecondaryText, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            ResourceCard(
                title = "Whisper speech recognition",
                status = whisperState.name,
                detail = "${whisperProgress.percent}% - ${whisper.metadata.sizeBytes / 1_000_000} MB",
                actionLabel = when {
                    whisperState == OfflineModelState.DOWNLOADING || whisperState == OfflineModelState.VERIFYING -> "Cancel"
                    whisperState == OfflineModelState.READY -> "Delete"
                    else -> "Install"
                },
                onAction = {
                    when {
                        whisperState == OfflineModelState.DOWNLOADING || whisperState == OfflineModelState.VERIFYING -> whisperJob?.cancel()
                        whisperState == OfflineModelState.READY -> deleteTarget = "whisper"
                        else -> whisperJob = scope.launch {
                            runCatching { whisper.download() }
                            whisperJob = null
                        }
                    }
                }
            )
            packs.forEach { pack ->
                val progress = voiceProgress[pack.id]
                ResourceCard(
                    title = "${pack.displayName} TTS (${pack.language.tag})",
                    status = pack.status.name,
                    detail = "${progress?.percent ?: 0}% - ${pack.sizeBytes / 1_000_000} MB - ${pack.sha256.take(12)}...",
                    actionLabel = if (activeVoiceId == pack.id) "Cancel" else if (pack.status == VoicePackStatus.READY) "Delete" else "Install",
                    onAction = {
                        when {
                            activeVoiceId == pack.id -> voiceJob?.cancel()
                            pack.status == VoicePackStatus.READY -> deleteTarget = pack.id
                            else -> {
                                activeVoiceId = pack.id
                                voiceJob = scope.launch {
                                    runCatching { voices.install(pack.id) }
                                    packs = voices.packs()
                                    activeVoiceId = null
                                    voiceJob = null
                                }
                            }
                        }
                    }
                )
            }
            Spacer(Modifier.height(8.dp))
            Text("Voice Lab · experimental", color = SecondaryText, fontSize = 12.sp)
            Text(
                "Официальные Silero v5 кандидаты. Пока не проверены и не подменяют системный AZ.",
                color = SecondaryText,
                fontSize = 11.sp
            )
            VoiceLabCatalog.candidates.forEach { candidate ->
                Surface(color = ElevatedSurface, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Text("${candidate.speaker} · ${candidate.language.tag}", fontSize = 13.sp)
                        Text("${candidate.model} · ${candidate.sampleRates} · ${candidate.status}", color = SecondaryText, fontSize = 11.sp)
                        Text(candidate.note, color = SecondaryText, fontSize = 11.sp)
                    }
                }
            }
        }
    }
    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete local voice resource?") },
            text = { Text("Music, playlists, queue, history, and favorites will not be changed.") },
            confirmButton = {
                TextButton(onClick = {
                    if (target == "whisper") {
                        scope.launch { whisper.delete() }
                    } else {
                        voices.delete(target)
                        packs = voices.packs()
                    }
                    deleteTarget = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun ResourceCard(
    title: String,
    status: String,
    detail: String,
    actionLabel: String,
    onAction: () -> Unit
) {
    Surface(color = ElevatedSurface, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 13.sp)
                Text("$status - $detail", color = SecondaryText, fontSize = 11.sp)
            }
            TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Composable
private fun DiagnosticRow(label: String, value: String) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(ElevatedSurface).padding(14.dp)) {
        Text(label, color = SecondaryText, fontSize = 11.sp)
        Spacer(Modifier.height(4.dp))
        Text(value, color = PrimaryText, fontSize = 14.sp)
    }
}

@Composable
private fun SearchScreen(
    state: AuraUiState,
    onQuery: (String) -> Unit,
    onSubmit: () -> Unit,
    onVoice: () -> Unit,
    onTrack: (Track) -> Unit,
    onPlayNext: (Track) -> Unit,
    onAddQueue: (Track) -> Unit,
    onAddPlaylist: (Track) -> Unit,
    onRecent: (String) -> Unit
) {
    Column(
        Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 20.dp).padding(top = 20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Поиск", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.weight(1f))
            ProviderStatusPill()
        }
        Spacer(Modifier.height(20.dp))
        SearchField(state.query, onQuery, onSubmit, onVoice, state.isListening)
        Spacer(Modifier.height(10.dp))
        Text(state.assistantText, color = SecondaryText, fontSize = 13.sp)
        AnimatedVisibility(state.recentSearches.isNotEmpty() && state.query.isBlank()) {
            Column {
                Spacer(Modifier.height(18.dp))
                Text("Недавние запросы", color = SecondaryText, fontSize = 13.sp)
                Spacer(Modifier.height(9.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.recentSearches.forEach { query -> MoodChip(query) { onRecent(query) } }
                }
            }
        }
        Spacer(Modifier.height(25.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text("Результаты", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            if (state.isLoading) {
                CircularProgressIndicator(Modifier.size(18.dp), color = AccentSilver, strokeWidth = 2.dp)
            } else {
                Text("${state.searchResults.size} результатов", color = SecondaryText, fontSize = 13.sp)
            }
        }
        Spacer(Modifier.height(10.dp))
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 180.dp)
        ) {
            itemsIndexed(state.searchResults, key = { _, track -> track.id }) { index, track ->
                TrackRow(
                    track = track,
                    liked = track.id in state.likedIds,
                    onClick = { onTrack(track) },
                    onPlayNext = { onPlayNext(track) },
                    onAddQueue = { onAddQueue(track) },
                    onAddPlaylist = { onAddPlaylist(track) },
                    card = true,
                    badge = if (index == 0) "ЛУЧШЕЕ" else null
                )
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun RadioScreen(
    state: AuraUiState,
    onBack: () -> Unit,
    onCountry: (RadioCountry) -> Unit,
    onRefresh: () -> Unit,
    onStation: (Track) -> Unit,
    onPlayNext: (Track) -> Unit,
    onAddQueue: (Track) -> Unit,
    onAddPlaylist: (Track) -> Unit
) {
    Column(
        Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 16.dp).padding(top = 8.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Назад") }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("AURA RADIO", letterSpacing = 2.5.sp, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text("Бесплатные станции мира", color = SecondaryText, fontSize = 11.sp)
            }
            IconButton(onClick = onRefresh) { Icon(Icons.Rounded.Refresh, "Обновить") }
        }
        Spacer(Modifier.height(16.dp))
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFF351360), Color(0xFF151A33), Color(0xFF071725)),
                        start = Offset.Zero,
                        end = Offset(900f, 400f)
                    )
                )
                .border(1.dp, ReferencePurple.copy(.5f), RoundedCornerShape(22.dp))
                .padding(18.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(48.dp).background(ReferencePurple.copy(.22f), CircleShape),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Rounded.Radio, null, tint = ReferenceMagenta) }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(state.selectedRadioCountry?.name ?: "Выбери страну", style = MaterialTheme.typography.titleLarge)
                    Text(
                        state.selectedRadioCountry?.let { "${it.stationCount} станций в открытом каталоге" }
                            ?: "Каталог Radio Browser",
                        color = SecondaryText,
                        fontSize = 12.sp
                    )
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Text("Страны", fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.radioCountries, key = RadioCountry::code) { country ->
                SelectableChip(country.name, country.code == state.selectedRadioCountry?.code) { onCountry(country) }
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Станции", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            Text("${state.radioStations.size}", color = SecondaryText, fontSize = 12.sp)
        }
        Spacer(Modifier.height(8.dp))
        when {
            state.isRadioLoading -> {
                Column(Modifier.fillMaxWidth().padding(top = 60.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = ReferenceMagenta, strokeWidth = 2.dp)
                    Spacer(Modifier.height(12.dp))
                    Text("Настраиваю эфир…", color = SecondaryText)
                }
            }
            state.radioError != null -> {
                Column(Modifier.fillMaxWidth().padding(top = 46.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(state.radioError, textAlign = TextAlign.Center, color = SecondaryText)
                    Spacer(Modifier.height(14.dp))
                    Button(onClick = onRefresh, shape = RoundedCornerShape(16.dp)) { Text("Повторить") }
                }
            }
            else -> LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 180.dp)
            ) {
                items(state.radioStations, key = Track::id) { station ->
                    TrackRow(
                        track = station,
                        liked = station.id in state.likedIds,
                        onClick = { onStation(station) },
                        onPlayNext = { onPlayNext(station) },
                        onAddQueue = { onAddQueue(station) },
                        onAddPlaylist = { onAddPlaylist(station) },
                        highlighted = station.id == state.nowTrack.id,
                        compact = true
                    )
                    HorizontalDivider(color = Color.White.copy(.05f))
                }
            }
        }
    }
}

@Composable
private fun LibraryScreen(
    state: AuraUiState,
    onSection: (LibrarySection) -> Unit,
    onTrack: (Track) -> Unit,
    onPlayNext: (Track) -> Unit,
    onAddTrackToQueue: (Track) -> Unit,
    onAddTrackToPlaylist: (Track) -> Unit,
    onOpenPlaylist: (String) -> Unit,
    onClosePlaylist: () -> Unit,
    onCreatePlaylist: (String, Boolean) -> Unit,
    onRenamePlaylist: (String, String) -> Unit,
    onDeletePlaylist: (String) -> Unit,
    onAddCurrent: (String) -> Unit,
    onAddQueue: (String) -> Unit,
    onRemoveTrack: (String, String) -> Unit,
    onMoveTrack: (String, Int, Int) -> Unit,
    onShufflePlaylist: (String) -> Unit,
    onPlayPlaylist: (AuraPlaylist, Boolean) -> Unit,
    onPlayPlaylistFrom: (AuraPlaylist, Int) -> Unit
) {
    var createDialog by rememberSaveable { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<AuraPlaylist?>(null) }
    var deleteTarget by remember { mutableStateOf<AuraPlaylist?>(null) }
    val selected = state.selectedPlaylist

    if (createDialog) {
        PlaylistNameDialog(
            title = "Новый плейлист",
            initialName = "",
            confirmLabel = "Создать",
            extraLabel = "Из очереди",
            onDismiss = { createDialog = false },
            onConfirm = { name ->
                onCreatePlaylist(name, false)
                createDialog = false
            },
            onExtra = { name ->
                onCreatePlaylist(name, true)
                createDialog = false
            }
        )
    }
    renameTarget?.let { playlist ->
        PlaylistNameDialog(
            title = "Переименовать",
            initialName = playlist.name,
            confirmLabel = "Сохранить",
            onDismiss = { renameTarget = null },
            onConfirm = { name ->
                onRenamePlaylist(playlist.id, name)
                renameTarget = null
            }
        )
    }
    deleteTarget?.let { playlist ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Удалить ${playlist.name}?") },
            text = { Text("Треки останутся в истории и других плейлистах.") },
            confirmButton = {
                TextButton(onClick = {
                    onDeletePlaylist(playlist.id)
                    deleteTarget = null
                }) { Text("Удалить") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Отмена") }
            }
        )
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 20.dp).padding(top = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (selected != null) {
                IconButton(onClick = onClosePlaylist) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Назад") }
            }
            Text(
                selected?.name ?: if (state.librarySection == LibrarySection.PLAYLISTS) "Плейлисты" else "Библиотека",
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (selected == null && state.librarySection == LibrarySection.PLAYLISTS) {
                CircleIconButton(Icons.Rounded.Add, 44.dp) { createDialog = true }
            }
        }
        Spacer(Modifier.height(20.dp))

        if (selected != null) {
            PlaylistDetail(
                playlist = selected,
                likedIds = state.likedIds,
                onTrack = { index -> onPlayPlaylistFrom(selected, index) },
                onPlayNext = { track -> onPlayNext(track) },
                onAddToQueue = { track -> onAddTrackToQueue(track) },
                onAddToPlaylist = onAddTrackToPlaylist,
                onPlay = { onPlayPlaylist(selected, false) },
                onShuffle = { onPlayPlaylist(selected, true) },
                onAddCurrent = { onAddCurrent(selected.id) },
                onAddQueue = { onAddQueue(selected.id) },
                onRename = { renameTarget = selected },
                onDelete = { deleteTarget = selected },
                onRemove = { trackId -> onRemoveTrack(selected.id, trackId) },
                onMove = { from, to -> onMoveTrack(selected.id, from, to) },
                onShuffleOrder = { onShufflePlaylist(selected.id) }
            )
            return@Column
        }

        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LibrarySection.entries.forEach { section ->
                val title = when (section) {
                    LibrarySection.FAVORITES -> "Любимые"
                    LibrarySection.HISTORY -> "История"
                    LibrarySection.LOCAL -> "На телефоне"
                    LibrarySection.PLAYLISTS -> "Плейлисты"
                }
                SelectableChip(title, section == state.librarySection) { onSection(section) }
            }
        }
        Spacer(Modifier.height(22.dp))
        val tracks = when (state.librarySection) {
            LibrarySection.FAVORITES -> state.favorites
            LibrarySection.HISTORY -> state.history
            LibrarySection.LOCAL -> state.localTracks
            LibrarySection.PLAYLISTS -> emptyList()
        }
        if (state.librarySection == LibrarySection.PLAYLISTS) {
            if (state.playlists.isEmpty()) {
                EmptyLibrary(Icons.AutoMirrored.Rounded.PlaylistPlay, "Плейлистов пока нет", "Создай пустой или сохрани текущую очередь")
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = { createDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp)
                ) { Text("Создать плейлист") }
            } else {
                LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 180.dp)) {
                    items(state.playlists, key = AuraPlaylist::id) { playlist ->
                        PlaylistCard(
                            playlist = playlist,
                            onOpen = { onOpenPlaylist(playlist.id) },
                            onPlay = { onPlayPlaylist(playlist, false) }
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                }
            }
        } else if (tracks.isEmpty()) {
            EmptyLibrary(
                when (state.librarySection) {
                    LibrarySection.FAVORITES -> Icons.Rounded.FavoriteBorder
                    LibrarySection.HISTORY -> Icons.Rounded.History
                    LibrarySection.LOCAL -> Icons.Rounded.Headphones
                    LibrarySection.PLAYLISTS -> Icons.AutoMirrored.Rounded.PlaylistPlay
                },
                when (state.librarySection) {
                    LibrarySection.FAVORITES -> "Здесь появятся любимые треки"
                    LibrarySection.HISTORY -> "История пока пуста"
                    LibrarySection.LOCAL -> "Музыкальные файлы не найдены"
                    LibrarySection.PLAYLISTS -> "Список пока пуст"
                },
                "Данные сохраняются только на этом устройстве"
            )
        } else {
            LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 180.dp)) {
                items(tracks, key = Track::id) { track ->
                    TrackRow(
                        track = track,
                        liked = track.id in state.likedIds,
                        onClick = { onTrack(track) },
                        onPlayNext = { onPlayNext(track) },
                        onAddQueue = { onAddTrackToQueue(track) },
                        onAddPlaylist = { onAddTrackToPlaylist(track) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaylistNameDialog(
    title: String,
    initialName: String,
    confirmLabel: String,
    extraLabel: String? = null,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    onExtra: ((String) -> Unit)? = null
) {
    var name by rememberSaveable(initialName) { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(60) },
                label = { Text("Название") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }) { Text(confirmLabel) }
        },
        dismissButton = {
            Row {
                if (extraLabel != null && onExtra != null) {
                    TextButton(onClick = { onExtra(name) }) { Text(extraLabel) }
                }
                TextButton(onClick = onDismiss) { Text("Отмена") }
            }
        }
    )
}

@Composable
private fun PlaylistPickerDialog(
    track: Track,
    playlists: List<AuraPlaylist>,
    onDismiss: () -> Unit,
    onAdd: (String) -> Unit,
    onCreate: (String) -> Unit
) {
    var creating by rememberSaveable(track.id) { mutableStateOf(false) }
    if (creating) {
        PlaylistNameDialog(
            title = "Новый плейлист",
            initialName = "",
            confirmLabel = "Создать и добавить",
            onDismiss = { creating = false },
            onConfirm = onCreate
        )
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Добавить в плейлист") },
        text = {
            Column {
                Text(track.title, color = SecondaryText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(12.dp))
                Surface(
                    onClick = { creating = true },
                    shape = RoundedCornerShape(16.dp),
                    color = ReferencePurple,
                    contentColor = Color.White,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Add, null)
                        Spacer(Modifier.width(10.dp))
                        Text("Создать новый плейлист", fontWeight = FontWeight.SemiBold)
                    }
                }
                if (playlists.isEmpty()) {
                    Text("Плейлистов пока нет", color = SecondaryText, modifier = Modifier.padding(top = 18.dp))
                } else {
                    Spacer(Modifier.height(10.dp))
                    LazyColumn(Modifier.height(if (playlists.size > 4) 260.dp else (playlists.size * 60).dp)) {
                        items(playlists, key = AuraPlaylist::id) { playlist ->
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                                    .clickable { onAdd(playlist.id) }.padding(horizontal = 10.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.PlaylistPlay, null, tint = AuraAccentSoft)
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(playlist.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${playlist.tracks.size} треков", color = SecondaryText, fontSize = 11.sp)
                                }
                                Icon(Icons.Rounded.Add, "Добавить")
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

@Composable
private fun PlaylistCard(playlist: AuraPlaylist, onOpen: () -> Unit, onPlay: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(ElevatedSurface)
            .border(1.dp, Color.White.copy(.08f), RoundedCornerShape(22.dp))
            .clickable(onClick = onOpen).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(54.dp).background(Color.White.copy(.07f), RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
            Icon(Icons.AutoMirrored.Rounded.PlaylistPlay, null, tint = AccentSilver)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(playlist.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${playlist.tracks.size} треков", color = SecondaryText, fontSize = 12.sp)
        }
        IconButton(onClick = onPlay, enabled = playlist.tracks.isNotEmpty()) {
            Icon(Icons.Rounded.PlayArrow, "Включить")
        }
    }
}

@Composable
private fun PlaylistDetail(
    playlist: AuraPlaylist,
    likedIds: Set<String>,
    onTrack: (Int) -> Unit,
    onPlayNext: (Track) -> Unit,
    onAddToQueue: (Track) -> Unit,
    onAddToPlaylist: (Track) -> Unit,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    onAddCurrent: () -> Unit,
    onAddQueue: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onRemove: (String) -> Unit,
    onMove: (Int, Int) -> Unit,
    onShuffleOrder: () -> Unit
) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        PlayerAction(Icons.Rounded.PlayArrow, "Играть", onPlay)
        PlayerAction(Icons.Rounded.Shuffle, "Вперемешку", onShuffle)
        PlayerAction(Icons.Rounded.Add, "Текущий", onAddCurrent)
        PlayerAction(Icons.AutoMirrored.Rounded.PlaylistAdd, "Очередь", onAddQueue)
        PlayerAction(Icons.Rounded.DragHandle, "Смешать", onShuffleOrder)
        PlayerAction(Icons.Rounded.Edit, "Название", onRename)
        PlayerAction(Icons.Rounded.Delete, "Удалить", onDelete)
    }
    Spacer(Modifier.height(18.dp))
    if (playlist.tracks.isEmpty()) {
        EmptyLibrary(Icons.AutoMirrored.Rounded.PlaylistPlay, "Плейлист пуст", "Добавь текущий трек или всю очередь")
        return
    }
    LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 180.dp)) {
        itemsIndexed(playlist.tracks, key = { _, track -> track.id }) { index, track ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column {
                    IconButton(onClick = { onMove(index, index - 1) }, enabled = index > 0) {
                        Icon(Icons.Rounded.ArrowUpward, "Выше", Modifier.size(18.dp))
                    }
                    IconButton(onClick = { onMove(index, index + 1) }, enabled = index < playlist.tracks.lastIndex) {
                        Icon(Icons.Rounded.ArrowDownward, "Ниже", Modifier.size(18.dp))
                    }
                }
                Box(Modifier.weight(1f)) {
                    TrackRow(
                        track = track,
                        liked = track.id in likedIds,
                        onClick = { onTrack(index) },
                        onPlayNext = { onPlayNext(track) },
                        onAddQueue = { onAddToQueue(track) },
                        onAddPlaylist = { onAddToPlaylist(track) }
                    )
                }
                IconButton(onClick = { onRemove(track.id) }) {
                    Icon(Icons.Rounded.Close, "Убрать")
                }
            }
            HorizontalDivider(color = Color.White.copy(.05f))
        }
    }
}

@Composable
private fun AssistantScreen(
    state: AuraUiState,
    onVoice: () -> Unit,
    onCommand: (String) -> Unit,
    onWakeWord: (Boolean) -> Unit,
    onOpenSettings: () -> Unit
) {
    var draft by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    LaunchedEffect(state.assistantMessages.size, state.isAssistantThinking) {
        val extra = if (state.isAssistantThinking) 1 else 0
        val target = state.assistantMessages.size + extra - 1
        if (target >= 0) listState.animateScrollToItem(target)
    }
    Column(
        Modifier.fillMaxSize().statusBarsPadding().imePadding().padding(horizontal = 16.dp).padding(top = 8.dp, bottom = 162.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.size(48.dp))
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("A U R A", textAlign = TextAlign.Center, letterSpacing = 3.sp, fontSize = 14.sp)
                Text(
                    when (state.recognitionBackend) {
                        az.simplesoft.aura.assistant.RecognitionBackend.Whisper -> "Whisper · полностью локально"
                        az.simplesoft.aura.assistant.RecognitionBackend.AndroidOnDevice -> "Android on-device"
                        az.simplesoft.aura.assistant.RecognitionBackend.AndroidSystem -> "Системное распознавание · сеть возможна"
                        az.simplesoft.aura.assistant.RecognitionBackend.Unavailable -> "Распознавание недоступно"
                    },
                    color = AuraMint,
                    fontSize = 9.sp
                )
            }
            IconButton(onClick = onVoice, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Rounded.GraphicEq, "Голос", tint = ReferenceMagenta)
            }
        }
        when (val voiceState = state.voiceInputState) {
            is VoiceInputState.NoSpeech,
            is VoiceInputState.Failed,
            is VoiceInputState.PermissionRequired,
            is VoiceInputState.ModelRequired -> Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF38202B),
                shape = RoundedCornerShape(14.dp)
            ) {
                Row(
                    Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(state.assistantText, Modifier.weight(1f), color = PrimaryText, fontSize = 12.sp)
                    TextButton(onClick = onVoice) { Text("Повторить") }
                    if (voiceState is VoiceInputState.PermissionRequired) {
                        TextButton(onClick = onOpenSettings) { Text("Настройки") }
                    }
                }
            }
            else -> Unit
        }
        Spacer(Modifier.height(12.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = ElevatedSurface,
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Mic, contentDescription = null, tint = if (state.wakeWordEnabled) AuraMint else SecondaryText)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Слушать «АУРА»", fontSize = 13.sp)
                    Text(
                        if (state.wakeWordEnabled) "Активно в фоне и при выключенном экране" else "Включается с постоянным уведомлением",
                        color = SecondaryText,
                        fontSize = 10.sp
                    )
                }
                Switch(
                    checked = state.wakeWordEnabled,
                    onCheckedChange = onWakeWord,
                    colors = SwitchDefaults.colors(checkedThumbColor = PrimaryText, checkedTrackColor = AuraAccent)
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        LazyColumn(
            Modifier.fillMaxWidth().weight(1f),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (state.assistantMessages.isEmpty()) {
                item(key = "welcome") {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                        MiniAuraFace(34.dp)
                        Spacer(Modifier.width(10.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp, 18.dp, 18.dp, 18.dp),
                            color = Color(0xFF202631),
                            modifier = Modifier.weight(1f, fill = false)
                        ) {
                            Text(state.assistantText, Modifier.padding(horizontal = 14.dp, vertical = 11.dp), fontSize = 13.sp)
                        }
                    }
                }
            }
            items(state.assistantMessages, key = { it.id }) { message ->
                if (message.role == AssistantRole.USER) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        Surface(
                            shape = RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp),
                            color = ReferencePurple,
                            modifier = Modifier.fillMaxWidth(.78f)
                        ) {
                            Text(message.text, Modifier.padding(horizontal = 15.dp, vertical = 12.dp), fontSize = 13.sp)
                        }
                    }
                } else {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                        MiniAuraFace(34.dp)
                        Spacer(Modifier.width(10.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp, 18.dp, 18.dp, 18.dp),
                            color = Color(0xFF202631),
                            modifier = Modifier.fillMaxWidth(.82f)
                        ) {
                            Text(message.text, Modifier.padding(horizontal = 14.dp, vertical = 11.dp), fontSize = 13.sp)
                        }
                    }
                }
            }
            if (state.isAssistantThinking) {
                item(key = "thinking") {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        MiniAuraFace(34.dp)
                        Spacer(Modifier.width(10.dp))
                        Surface(shape = RoundedCornerShape(4.dp, 18.dp, 18.dp, 18.dp), color = Color(0xFF202631)) {
                            Row(Modifier.padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = AuraAccentSoft)
                            }
                        }
                    }
                }
            }
            if (state.searchResults.isNotEmpty() && state.assistantMessages.isNotEmpty()) {
                item(key = "suggestions") {
                    Column(
                        Modifier.padding(start = 44.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp))
                            .background(Color(0xFF171D27)).padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        state.searchResults.take(3).forEach { track ->
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                                    .clickable { onCommand("Включи ${track.artist} ${track.title}") }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Artwork(track, Modifier.size(42.dp), 9.dp)
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(track.title, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(track.artist, color = SecondaryText, fontSize = 11.sp, maxLines = 1)
                                }
                                Icon(Icons.Rounded.PlayArrow, "Включить", Modifier.size(22.dp))
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Color(0xFF171D27))
                .border(1.dp, Color.White.copy(.08f), RoundedCornerShape(22.dp)).padding(start = 16.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                value = draft,
                onValueChange = { draft = it.take(700) },
                modifier = Modifier.weight(1f).padding(vertical = 14.dp),
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = PrimaryText),
                cursorBrush = SolidColor(AuraAccentSoft),
                decorationBox = { field ->
                    if (draft.isBlank()) Text("Скажи или напиши AURA…", color = SecondaryText, fontSize = 13.sp)
                    field()
                }
            )
            IconButton(
                onClick = {
                    draft.trim().takeIf(String::isNotBlank)?.let {
                        onCommand(it)
                        draft = ""
                    }
                }
            ) {
                Icon(Icons.Rounded.ArrowUpward, "Отправить", tint = if (draft.isBlank()) SecondaryText else AuraAccentSoft)
            }
            IconButton(onClick = onVoice) {
                Icon(Icons.Rounded.Mic, "Голос", tint = if (state.isListening) ReferenceMagenta else PrimaryText)
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(22.dp))
                .background(Brush.horizontalGradient(listOf(ReferencePurple.copy(.24f), ReferenceBlue.copy(.12f))))
                .border(1.dp, ReferencePurple.copy(.25f), RoundedCornerShape(22.dp)).clickable(onClick = onVoice),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            MiniAuraFace(34.dp)
            Spacer(Modifier.width(10.dp))
            Text(
                when (state.voiceInputState) {
                    VoiceInputState.Listening -> "Слушаю тебя..."
                    VoiceInputState.Processing -> "Распознаю..."
                    is VoiceInputState.TranscriptReady -> "Текст получен"
                    is VoiceInputState.NoSpeech,
                    is VoiceInputState.Failed,
                    is VoiceInputState.PermissionRequired,
                    is VoiceInputState.ModelRequired -> "Повторить голосовой ввод"
                    else -> "Нажми и говори"
                },
                color = PrimaryText,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun MiniAuraFace(size: Dp) {
    Box(
        Modifier.size(size).background(
            Brush.radialGradient(listOf(Color(0xFFD088FF), ReferencePurple, Color(0xFF251345))),
            CircleShape
        ).border(1.dp, Color.White.copy(.20f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text("••", color = Color.White, fontSize = (size.value * .34f).sp, letterSpacing = 1.sp)
    }
}

@Composable
private fun PlayerScreen(
    state: AuraUiState,
    onClose: () -> Unit,
    onPrevious: () -> Unit,
    onPlay: () -> Unit,
    onNext: () -> Unit,
    onLike: () -> Unit,
    onShuffle: () -> Unit,
    onRepeat: () -> Unit,
    onQueue: () -> Unit,
    onSeek: (Long) -> Unit,
    onCar: () -> Unit,
    onAddToPlaylist: () -> Unit
) {
    val track = state.nowTrack
    val duration = maxOf(state.playbackDurationMs, track.durationMs ?: 0L)
    val position = state.positionMs.coerceIn(0L, duration.coerceAtLeast(0L))
    val progress = if (duration > 0L) position.toFloat() / duration else 0f
    Box(Modifier.fillMaxSize().background(AuraBlack)) {
        if (!track.artworkUrl.isNullOrBlank()) {
            AsyncImage(
                model = track.artworkUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().scale(1.12f).alpha(.20f)
            )
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    listOf(Color.Black.copy(.20f), AuraBlack.copy(.70f), AuraBlack),
                    startY = 0f
                )
            )
        )
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
                .padding(horizontal = 22.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.Rounded.KeyboardArrowDown, "Закрыть") }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Играет из плейлиста", color = SecondaryText, fontSize = 10.sp)
                    Text("Мой микс", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
                IconButton(onClick = {}) { Icon(Icons.Rounded.MoreHoriz, "Ещё") }
            }
            Spacer(Modifier.height(10.dp))
            Artwork(track, Modifier.fillMaxWidth().aspectRatio(1.16f), 22.dp)
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(track.title, style = MaterialTheme.typography.headlineMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(3.dp))
                    Text(track.artist, color = ReferenceMagenta, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = onLike) {
                    Icon(if (state.liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, "Любимое", tint = ReferenceMagenta)
                }
            }
            Spacer(Modifier.height(14.dp))
            if (duration <= 0L) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White.copy(.06f))
                        .padding(horizontal = 15.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(8.dp).background(Color(0xFF73D99A), CircleShape))
                    Spacer(Modifier.width(9.dp))
                    Text(if (state.isBuffering) "ПОДКЛЮЧЕНИЕ…" else "ПРЯМОЙ ЭФИР", letterSpacing = 1.5.sp, fontSize = 11.sp)
                    Spacer(Modifier.weight(1f))
                    Text("RADIO", color = SecondaryText, fontSize = 11.sp)
                }
            } else {
                Slider(
                    value = progress.coerceIn(0f, 1f),
                    onValueChange = { onSeek((it * duration).toLong()) },
                    colors = SliderDefaults.colors(
                        thumbColor = AuraAccentSoft,
                        activeTrackColor = ReferenceMagenta,
                        inactiveTrackColor = Color.White.copy(.18f)
                    )
                )
                Row(Modifier.fillMaxWidth()) {
                    Text(formatDuration(position), color = SecondaryText, fontSize = 12.sp)
                    Spacer(Modifier.weight(1f))
                    Text("−${formatDuration((duration - position).coerceAtLeast(0L))}", color = SecondaryText, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                PlayerToggle(Icons.Rounded.Shuffle, state.isShuffleEnabled, onShuffle)
                IconButton(onClick = onPrevious, modifier = Modifier.size(62.dp)) {
                    Icon(Icons.Rounded.SkipPrevious, "Предыдущий", Modifier.size(38.dp))
                }
                IconButton(onClick = onPlay, modifier = Modifier.size(72.dp).background(ReferencePurple, CircleShape)) {
                    Icon(if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Воспроизведение", Modifier.size(38.dp), tint = Color.White)
                }
                IconButton(onClick = onNext, modifier = Modifier.size(62.dp)) {
                    Icon(Icons.Rounded.SkipNext, "Следующий", Modifier.size(38.dp))
                }
                PlayerToggle(
                    if (state.repeatMode == AuraRepeatMode.ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                    state.repeatMode != AuraRepeatMode.OFF,
                    onRepeat
                )
            }
            Spacer(Modifier.height(14.dp))
            Row(
                Modifier.fillMaxWidth().height(58.dp).clip(RoundedCornerShape(18.dp))
                    .background(Color.White.copy(.07f)).padding(horizontal = 8.dp, vertical = 3.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                PlayerAction(Icons.AutoMirrored.Rounded.QueueMusic, "Очередь", onQueue)
                PlayerAction(Icons.AutoMirrored.Rounded.PlaylistAdd, "В плейлист", onAddToPlaylist)
                PlayerAction(Icons.Rounded.DirectionsCar, "В машине", onCar)
            }
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                    .background(Brush.horizontalGradient(listOf(Color(0xFF241B35), Color(0xFF171A27))))
                    .border(1.dp, ReferencePurple.copy(.20f), RoundedCornerShape(20.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MiniAuraFace(42.dp)
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Text("AURA", color = AuraAccentSoft, fontSize = 9.sp, letterSpacing = 1.5.sp)
                    Text("Добавить трек в плейлист?", fontSize = 12.sp)
                }
                TextButton(onClick = onAddToPlaylist) { Text("Добавить", color = ReferenceMagenta) }
            }
        }
    }
}

@Composable
private fun QueueScreen(
    state: AuraUiState,
    onClose: () -> Unit,
    onPlay: (Track) -> Unit,
    onRemove: (Track) -> Unit,
    onMove: (Int, Int) -> Unit,
    onClear: () -> Unit,
    onShuffle: () -> Unit,
    onRepeat: () -> Unit,
    onAutoContinue: () -> Unit,
    onSave: (String) -> Unit,
    onRestore: (AuraQueueSnapshot) -> Unit,
    onDeleteHistory: (String) -> Unit,
    onAddToPlaylist: (Track) -> Unit
) {
    var historyVisible by rememberSaveable { mutableStateOf(false) }
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    var saveDialog by rememberSaveable { mutableStateOf(false) }
    if (saveDialog) {
        PlaylistNameDialog(
            title = "Сохранить очередь",
            initialName = "Сессия ${SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date())}",
            confirmLabel = "Сохранить",
            onDismiss = { saveDialog = false },
            onConfirm = { name -> onSave(name); saveDialog = false }
        )
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Очистить очередь?") },
            text = { Text("Останется только трек, который играет сейчас.") },
            confirmButton = {
                TextButton(onClick = { confirmClear = false; onClear() }) { Text("Очистить") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Отмена") } }
        )
    }
    Column(
        Modifier.fillMaxSize().background(AuraBlack).statusBarsPadding().navigationBarsPadding()
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Назад") }
            Column(Modifier.weight(1f)) {
                Text("Очередь", style = MaterialTheme.typography.headlineMedium)
                Text("${state.queue.size} треков в сессии", color = SecondaryText, fontSize = 12.sp)
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SelectableChip("Очередь", !historyVisible) { historyVisible = false }
            SelectableChip("История · ${state.queueHistory.size}", historyVisible) { historyVisible = true }
        }
        Spacer(Modifier.height(18.dp))
        if (historyVisible) {
            QueueHistory(
                history = state.queueHistory,
                onRestore = onRestore,
                onDelete = onDeleteHistory
            )
        } else {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QueueControlChip(
                    icon = Icons.Rounded.Shuffle,
                    label = "Перемешать",
                    active = state.isShuffleEnabled,
                    onClick = onShuffle
                )
                QueueControlChip(
                    icon = if (state.repeatMode == AuraRepeatMode.ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                    label = when (state.repeatMode) {
                        AuraRepeatMode.OFF -> "Без повтора"
                        AuraRepeatMode.ONE -> "Повтор трека"
                        AuraRepeatMode.ALL -> "Повтор очереди"
                    },
                    active = state.repeatMode != AuraRepeatMode.OFF,
                    onClick = onRepeat
                )
                QueueControlChip(Icons.Rounded.ClearAll, "Очистить", onClick = { confirmClear = true })
            }
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp).clip(RoundedCornerShape(20.dp))
                    .background(ElevatedSurface).border(1.dp, AuraBorder, RoundedCornerShape(20.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Умное продолжение", fontWeight = FontWeight.Medium)
                    Text("AURA добавит похожую музыку ближе к концу", color = SecondaryText, fontSize = 12.sp)
                }
                Switch(
                    checked = state.autoContinueEnabled,
                    onCheckedChange = { onAutoContinue() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = AuraBlack,
                        checkedTrackColor = AuraAccent,
                        uncheckedThumbColor = SecondaryText,
                        uncheckedTrackColor = DeepSurface
                    )
                )
            }
            Spacer(Modifier.height(12.dp))
            Surface(
                onClick = { saveDialog = true },
                shape = RoundedCornerShape(16.dp),
                color = ReferencePurple,
                contentColor = Color.White,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
            ) {
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, null, Modifier.size(19.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Сохранить очередь как плейлист", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
            }
            Spacer(Modifier.height(10.dp))
            ReorderableQueue(
                state = state,
                onPlay = onPlay,
                onRemove = onRemove,
                onMove = onMove,
                onAddToPlaylist = onAddToPlaylist
            )
        }
    }
}

@Composable
private fun QueueControlChip(
    icon: ImageVector,
    label: String,
    active: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = if (active) AuraAccent.copy(.18f) else ElevatedSurface,
        border = BorderStroke(1.dp, if (active) AuraAccent.copy(.55f) else AuraBorder)
    ) {
        Row(Modifier.padding(horizontal = 13.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(18.dp), tint = if (active) AuraAccentSoft else SecondaryText)
            Spacer(Modifier.width(7.dp))
            Text(label, fontSize = 12.sp, color = if (active) PrimaryText else SecondaryText)
        }
    }
}

@Composable
private fun ReorderableQueue(
    state: AuraUiState,
    onPlay: (Track) -> Unit,
    onRemove: (Track) -> Unit,
    onMove: (Int, Int) -> Unit,
    onAddToPlaylist: (Track) -> Unit
) {
    val listState = rememberLazyListState()
    val haptics = LocalHapticFeedback.current
    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableStateOf(0f) }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 12.dp, end = 12.dp, bottom = 28.dp)
    ) {
        itemsIndexed(state.queue, key = { _, track -> track.id }) { index, track ->
            val current = track.id == state.nowTrack.id
            val dragging = draggingIndex == index
            Row(
                Modifier.fillMaxWidth().animateItem().zIndex(if (dragging) 1f else 0f)
                    .graphicsLayer { translationY = if (dragging) dragOffset else 0f }
                    .clip(RoundedCornerShape(22.dp))
                    .background(if (current) AuraAccent.copy(.13f) else Color.Transparent)
                    .border(1.dp, if (current) AuraAccent.copy(.35f) else Color.Transparent, RoundedCornerShape(22.dp))
                    .semantics {
                        customActions = listOf(
                            CustomAccessibilityAction("Переместить выше") {
                                if (index > 0) { onMove(index, index - 1); true } else false
                            },
                            CustomAccessibilityAction("Переместить ниже") {
                                if (index < state.queue.lastIndex) { onMove(index, index + 1); true } else false
                            }
                        )
                    },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Rounded.DragHandle,
                    "Удерживай и перемещай",
                    tint = if (dragging) AuraAccentSoft else SecondaryText,
                    modifier = Modifier.size(48.dp).padding(13.dp).pointerInput(track.id) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                draggingIndex = index
                                dragOffset = 0f
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                            onDragCancel = { draggingIndex = null; dragOffset = 0f },
                            onDragEnd = { draggingIndex = null; dragOffset = 0f },
                            onDrag = { change, amount ->
                                change.consume()
                                dragOffset += amount.y
                                val from = draggingIndex ?: return@detectDragGesturesAfterLongPress
                                val threshold = size.height * .55f
                                val target = when {
                                    dragOffset > threshold && from < state.queue.lastIndex -> from + 1
                                    dragOffset < -threshold && from > 0 -> from - 1
                                    else -> from
                                }
                                if (target != from) {
                                    onMove(from, target)
                                    draggingIndex = target
                                    dragOffset = 0f
                                }
                            }
                        )
                    }
                )
                Box(Modifier.weight(1f)) {
                    TrackRow(
                        track,
                        track.id in state.likedIds,
                        { onPlay(track) },
                        onAddPlaylist = { onAddToPlaylist(track) },
                        highlighted = current,
                        compact = true
                    )
                }
                if (!current) {
                    IconButton(onClick = { onRemove(track) }) {
                        Icon(Icons.Rounded.Close, "Убрать ${track.title}", tint = SecondaryText)
                    }
                } else {
                    Text("СЕЙЧАС", color = AuraAccentSoft, fontSize = 9.sp, letterSpacing = 1.sp, modifier = Modifier.padding(end = 12.dp))
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun QueueHistory(
    history: List<AuraQueueSnapshot>,
    onRestore: (AuraQueueSnapshot) -> Unit,
    onDelete: (String) -> Unit
) {
    if (history.isEmpty()) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 60.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Rounded.History, null, Modifier.size(46.dp), tint = SecondaryText)
            Spacer(Modifier.height(14.dp))
            Text("История пока пустая", style = MaterialTheme.typography.titleLarge)
            Text("Предыдущие очереди появятся здесь автоматически", color = SecondaryText, textAlign = TextAlign.Center)
        }
        return
    }
    LazyColumn(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(history, key = AuraQueueSnapshot::id) { snapshot ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(ElevatedSurface)
                    .border(1.dp, AuraBorder, RoundedCornerShape(22.dp)).clickable { onRestore(snapshot) }
                    .padding(start = 14.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(48.dp).background(AuraAccent.copy(.14f), RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Rounded.Restore, null, tint = AuraAccentSoft) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(snapshot.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                    Text(
                        "${snapshot.tracks.size} треков · ${SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()).format(Date(snapshot.createdAt))}",
                        color = SecondaryText,
                        fontSize = 12.sp
                    )
                }
                IconButton(onClick = { onDelete(snapshot.id) }) {
                    Icon(Icons.Rounded.Delete, "Удалить снимок", tint = SecondaryText)
                }
            }
        }
    }
}

@Composable
private fun CarModeScreen(
    state: AuraUiState,
    onPrevious: () -> Unit,
    onPlay: () -> Unit,
    onNext: () -> Unit,
    onLike: () -> Unit,
    onExit: () -> Unit,
    onVoice: () -> Unit
) {
    Column(
        Modifier.fillMaxSize().background(AuraBlack).statusBarsPadding().navigationBarsPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("AURA · DRIVE", color = SecondaryText, letterSpacing = 3.sp, fontSize = 12.sp, modifier = Modifier.weight(1f))
            CircleIconButton(Icons.Rounded.Close, 64.dp, onClick = onExit)
        }
        Artwork(state.nowTrack, Modifier.size(260.dp), 52.dp)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(state.nowTrack.title, style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Text(state.nowTrack.artist, color = SecondaryText, fontSize = 18.sp)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            CircleIconButton(Icons.Rounded.SkipPrevious, 72.dp, onClick = onPrevious)
            CircleIconButton(if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, 96.dp, true, onPlay)
            CircleIconButton(Icons.Rounded.SkipNext, 72.dp, onClick = onNext)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = onLike,
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ElevatedSurface),
                modifier = Modifier.height(68.dp).weight(1f)
            ) { Icon(if (state.liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, null) }
            Button(
                onClick = onVoice,
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryText, contentColor = Color.Black),
                modifier = Modifier.height(68.dp).weight(2f)
            ) {
                Icon(Icons.Rounded.Mic, null)
                Spacer(Modifier.width(10.dp))
                Text("Команда", fontSize = 17.sp)
            }
        }
    }
}

@Composable
private fun BottomNavigation(
    selected: AuraDestination,
    onSelect: (AuraDestination) -> Unit,
    onQueue: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().height(62.dp).clip(RoundedCornerShape(20.dp)).background(Color(0xF20A1017))
            .border(1.dp, Color.White.copy(.07f), RoundedCornerShape(20.dp)).padding(horizontal = 3.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically
    ) {
        listOf(
            Triple(AuraDestination.HOME, Icons.Rounded.Home, "Главная"),
            Triple(AuraDestination.SEARCH, Icons.Rounded.Search, "Поиск"),
            Triple(AuraDestination.ASSISTANT, Icons.Rounded.Mic, "AURA"),
            Triple(null, Icons.AutoMirrored.Rounded.QueueMusic, "Очередь"),
            Triple(AuraDestination.LIBRARY, Icons.Rounded.LibraryMusic, "Библиотека")
        ).forEach { (destination, icon, title) ->
            val active = destination == selected
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(16.dp))
                    .background(if (active && destination != AuraDestination.ASSISTANT) ReferencePurple.copy(.14f) else Color.Transparent)
                    .clickable { if (destination == null) onQueue() else onSelect(destination) }.padding(vertical = 5.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (destination == AuraDestination.ASSISTANT) {
                    Box(
                        Modifier.size(34.dp).background(
                            Brush.radialGradient(listOf(ReferenceMagenta, ReferencePurple, Color(0xFF24134A))),
                            CircleShape
                        ).border(1.dp, Color.White.copy(.22f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) { Icon(icon, title, Modifier.size(18.dp), tint = Color.White) }
                } else {
                    Icon(icon, title, Modifier.size(20.dp), tint = if (active) ReferencePurple else SecondaryText.copy(.72f))
                }
                if (destination != AuraDestination.ASSISTANT) {
                    Text(title, color = if (active) ReferencePurple else SecondaryText.copy(.72f), fontSize = 8.sp, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun MiniPlayer(state: AuraUiState, onOpen: () -> Unit, onPlay: () -> Unit, onLike: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(64.dp).clip(RoundedCornerShape(18.dp))
            .background(Brush.horizontalGradient(listOf(Color(0xFF151B23), Color(0xFF0D1219))))
            .border(1.dp, Color.White.copy(.07f), RoundedCornerShape(18.dp)).clickable(onClick = onOpen).padding(7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Artwork(state.nowTrack, Modifier.size(50.dp), 11.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(state.nowTrack.title, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(state.nowTrack.artist, color = SecondaryText, fontSize = 12.sp, maxLines = 1)
        }
        IconButton(onClick = onLike, modifier = Modifier.size(42.dp)) {
            Icon(if (state.liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, "Любимое", tint = ReferenceMagenta)
        }
        IconButton(onClick = onPlay, modifier = Modifier.size(42.dp)) {
            if (state.isBuffering) {
                CircularProgressIndicator(Modifier.size(20.dp), color = ReferenceMagenta, strokeWidth = 2.dp)
            } else {
                Icon(if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Воспроизведение", tint = PrimaryText)
            }
        }
    }
}

@Composable
private fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onVoice: () -> Unit,
    listening: Boolean
) {
    val transition = rememberInfiniteTransition(label = "voice")
    val pulse by transition.animateFloat(
        .94f, 1.06f,
        infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "voicePulse"
    )
    Row(
        Modifier.fillMaxWidth().height(60.dp).clip(RoundedCornerShape(20.dp)).background(Color(0xE6121422))
            .border(1.dp, if (listening) AuraMint else AuraBorder, RoundedCornerShape(20.dp))
            .padding(start = 18.dp, end = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.Search, null, tint = AuraAccentSoft)
        Spacer(Modifier.width(12.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = PrimaryText),
            cursorBrush = SolidColor(PrimaryText),
            modifier = Modifier.weight(1f),
            decorationBox = { field ->
                if (value.isBlank()) Text("Артист, песня или настроение", color = SecondaryText.copy(.65f), maxLines = 1)
                field()
            }
        )
        if (value.isNotBlank()) {
            IconButton(onClick = onSubmit) { Icon(Icons.Rounded.Search, "Найти") }
        }
        IconButton(
            onClick = onVoice,
            modifier = Modifier.scale(if (listening) pulse else 1f).background(if (listening) AuraMint else AuraAccent, CircleShape)
        ) {
            Icon(if (listening) Icons.Rounded.GraphicEq else Icons.Rounded.Mic, "Голос", tint = Color(0xFF11111B))
        }
    }
}

@Composable
private fun RecommendationCard(track: Track, onClick: () -> Unit) {
    Column(Modifier.width(168.dp).clickable(onClick = onClick)) {
        Artwork(track, Modifier.size(168.dp), 30.dp)
        Spacer(Modifier.height(11.dp))
        Text(track.title, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(track.artist, color = SecondaryText, fontSize = 13.sp, maxLines = 1)
    }
}

@Composable
private fun StarterCard(onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(118.dp).clip(RoundedCornerShape(28.dp))
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF292D43), Color(0xFF171922)),
                    start = Offset.Zero,
                    end = Offset(900f, 300f)
                )
            )
            .border(1.dp, Color.White.copy(.10f), RoundedCornerShape(28.dp))
            .clickable(onClick = onClick).padding(18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(54.dp).background(Color.White.copy(.10f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.PlayArrow, "Включить пример", Modifier.size(30.dp), tint = PrimaryText)
        }
        Spacer(Modifier.width(15.dp))
        Column(Modifier.weight(1f)) {
            Text("Попробуй прямо сейчас", color = SecondaryText, fontSize = 11.sp, letterSpacing = 1.sp)
            Spacer(Modifier.height(5.dp))
            Text("Мот — Капкан", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(4.dp))
            Text("Поиск → источник → нативный плеер", color = SecondaryText, fontSize = 12.sp)
        }
    }
}

@Composable
private fun ProviderStatusPill() {
    Row(
        Modifier.clip(RoundedCornerShape(100.dp)).background(Color.White.copy(.06f))
            .border(1.dp, Color.White.copy(.08f), RoundedCornerShape(100.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(7.dp).background(Color(0xFF73D99A), CircleShape))
        Spacer(Modifier.width(7.dp))
        Text("YOUTUBE", color = AccentSilver, fontSize = 10.sp, letterSpacing = 1.2.sp)
    }
}

@Composable
private fun TrackRow(
    track: Track,
    liked: Boolean,
    onClick: () -> Unit,
    onPlayNext: (() -> Unit)? = null,
    onAddQueue: (() -> Unit)? = null,
    onAddPlaylist: (() -> Unit)? = null,
    highlighted: Boolean = false,
    card: Boolean = false,
    badge: String? = null,
    compact: Boolean = false
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(21.dp))
            .background(if (highlighted || card) Color.White.copy(if (highlighted) .08f else .045f) else Color.Transparent)
            .border(
                width = if (card) 1.dp else 0.dp,
                color = if (card) Color.White.copy(.07f) else Color.Transparent,
                shape = RoundedCornerShape(21.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = if (highlighted || card) 8.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Artwork(track, Modifier.size(if (compact) 44.dp else 56.dp), if (compact) 10.dp else 17.dp)
        Spacer(Modifier.width(if (compact) 10.dp else 12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    track.title,
                    Modifier.weight(1f, fill = false),
                    fontWeight = FontWeight.Medium,
                    fontSize = if (compact) 13.sp else 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                badge?.let {
                    Spacer(Modifier.width(7.dp))
                    Text(
                        it,
                        Modifier.clip(RoundedCornerShape(100.dp)).background(Color.White.copy(.10f))
                            .padding(horizontal = 7.dp, vertical = 3.dp),
                        color = AccentSilver,
                        fontSize = 8.sp,
                        letterSpacing = .8.sp
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    track.artist,
                    modifier = Modifier.weight(1f),
                    color = SecondaryText,
                    fontSize = if (compact) 11.sp else 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!compact) {
                    Text("  ·  ${sourceLabel(track)}", color = SecondaryText.copy(.55f), fontSize = 9.sp, maxLines = 1)
                }
            }
        }
        if (liked) Icon(Icons.Rounded.Favorite, null, tint = AccentSilver, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(5.dp))
        if (onPlayNext != null || onAddQueue != null || onAddPlaylist != null) {
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Rounded.MoreVert, "Действия с треком", tint = SecondaryText)
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    onPlayNext?.let { action ->
                        DropdownMenuItem(
                            text = { Text("Играть следующим") },
                            leadingIcon = { Icon(Icons.Rounded.PlayArrow, null) },
                            onClick = { menuOpen = false; action() }
                        )
                    }
                    onAddQueue?.let { action ->
                        DropdownMenuItem(
                            text = { Text("Добавить в конец") },
                            leadingIcon = { Icon(Icons.AutoMirrored.Rounded.QueueMusic, null) },
                            onClick = { menuOpen = false; action() }
                        )
                    }
                    onAddPlaylist?.let { action ->
                        DropdownMenuItem(
                            text = { Text("Добавить в плейлист") },
                            leadingIcon = { Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, null) },
                            onClick = { menuOpen = false; action() }
                        )
                    }
                }
            }
        } else {
            Icon(if (highlighted) Icons.Rounded.GraphicEq else Icons.Rounded.PlayArrow, "Включить", tint = PrimaryText)
        }
    }
}

@Composable
private fun Artwork(track: Track, modifier: Modifier, radius: Dp) {
    val colors = artworkColors(track)
    Box(
        modifier.clip(RoundedCornerShape(radius)).background(Brush.linearGradient(colors))
            .border(1.dp, Color.White.copy(.10f), RoundedCornerShape(radius)),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Rounded.AutoAwesome, null, tint = Color.White.copy(.82f), modifier = Modifier.size(34.dp))
        if (!track.artworkUrl.isNullOrBlank()) {
            AsyncImage(
                model = track.artworkUrl,
                contentDescription = "Обложка ${track.title}",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(.10f)))
                )
            )
        }
    }
}

private fun artworkColors(track: Track): List<Color> {
    val palettes = listOf(
        listOf(Color(0xFF8176D6), Color(0xFF27233F)),
        listOf(Color(0xFF4B9B91), Color(0xFF142E31)),
        listOf(Color(0xFFB56F91), Color(0xFF382132)),
        listOf(Color(0xFF5E7FB8), Color(0xFF1B2842)),
        listOf(Color(0xFF9B7953), Color(0xFF352719))
    )
    return palettes[(track.id.hashCode() and Int.MAX_VALUE) % palettes.size]
}

@Composable
private fun SectionHeader(title: String, subtitle: String) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        Text(subtitle, color = SecondaryText.copy(.72f), fontSize = 11.sp)
    }
}

@Composable
private fun MoodChip(text: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(100.dp),
        color = Color.White.copy(.055f),
        border = BorderStroke(1.dp, Color.White.copy(.09f))
    ) { Text(text, Modifier.padding(horizontal = 17.dp, vertical = 11.dp), color = AccentSilver) }
}

@Composable
private fun SelectableChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(100.dp),
        color = if (selected) ReferencePurple else Color.White.copy(.055f),
        contentColor = if (selected) Color.White else AccentSilver,
        border = BorderStroke(1.dp, if (selected) ReferenceMagenta.copy(.55f) else Color.White.copy(.09f))
    ) { Text(text, Modifier.padding(horizontal = 17.dp, vertical = 10.dp)) }
}

@Composable
private fun NativeSourceCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier.height(116.dp).clip(RoundedCornerShape(25.dp)).background(ElevatedSurface)
            .border(1.dp, Color.White.copy(.08f), RoundedCornerShape(25.dp))
            .clickable(onClick = onClick).padding(15.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Icon(icon, null, tint = AccentSilver)
        Column {
            Text(title, fontWeight = FontWeight.Medium, maxLines = 1)
            Text(subtitle, color = SecondaryText, fontSize = 12.sp, maxLines = 1)
        }
    }
}

@Composable
private fun VoiceOrb(listening: Boolean, onClick: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "orb")
    val scale by transition.animateFloat(.96f, 1.07f, infiniteRepeatable(tween(850), RepeatMode.Reverse), label = "orbScale")
    Box(
        Modifier.size(146.dp).scale(if (listening) scale else 1f).clip(CircleShape)
            .background(if (listening) AccentSilver else ElevatedSurface)
            .border(1.dp, Color.White.copy(.14f), CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(if (listening) Icons.Rounded.GraphicEq else Icons.Rounded.Mic, "Говорить", Modifier.size(52.dp), tint = if (listening) Color.Black else PrimaryText)
    }
}

@Composable
private fun EmptyLibrary(icon: ImageVector, title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth().padding(top = 90.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(82.dp).background(ElevatedSurface, CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = SecondaryText, modifier = Modifier.size(34.dp))
        }
        Spacer(Modifier.height(19.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(7.dp))
        Text(subtitle, color = SecondaryText, textAlign = TextAlign.Center, fontSize = 13.sp)
    }
}

@Composable
private fun PlayerToggle(icon: ImageVector, active: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick) { Icon(icon, null, tint = if (active) AuraAccentSoft else SecondaryText) }
}

@Composable
private fun PlayerAction(icon: ImageVector, text: String, onClick: () -> Unit) {
    Column(Modifier.clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick).padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, text, tint = AccentSilver)
        Spacer(Modifier.height(4.dp))
        Text(text, color = SecondaryText, fontSize = 10.sp)
    }
}

@Composable
private fun CircleIconButton(
    icon: ImageVector,
    size: Dp,
    light: Boolean = false,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(size).background(if (light) AuraAccent else ElevatedSurface, CircleShape)
            .border(1.dp, if (light) AuraAccentSoft.copy(.45f) else AuraBorder, CircleShape)
    ) {
        Icon(icon, null, tint = if (light) Color(0xFF12111B) else AccentSilver, modifier = Modifier.size(size * .42f))
    }
}

@Composable
private fun AuraBackground() {
    Canvas(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF07101A), AuraBlack)))) {
        drawCircle(
            Brush.radialGradient(listOf(ReferenceBlue.copy(.14f), Color.Transparent)),
            radius = size.minDimension * .72f,
            center = Offset(size.width * .92f, size.height * .08f)
        )
        drawCircle(
            Brush.radialGradient(listOf(ReferencePurple.copy(.12f), Color.Transparent)),
            radius = size.minDimension * .82f,
            center = Offset(size.width * .05f, size.height * .52f)
        )
        drawCircle(
            Brush.radialGradient(listOf(ReferenceMagenta.copy(.08f), Color.Transparent)),
            radius = size.minDimension * .68f,
            center = Offset(size.width, size.height * .82f)
        )
    }
}

private fun greeting(): String {
    val hour = SimpleDateFormat("H", Locale.getDefault()).format(Date()).toIntOrNull() ?: 12
    return when (hour) {
        in 5..11 -> "Доброе утро"
        in 12..17 -> "Добрый день"
        in 18..22 -> "Добрый вечер"
        else -> "Доброй ночи"
    }
}

private fun formatDuration(milliseconds: Long): String {
    val totalSeconds = milliseconds / 1000
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}

private fun sourceLabel(track: Track): String = when (track.sourceId) {
    "local" -> "НА ТЕЛЕФОНЕ"
    "radio_browser" -> "РАДИО"
    "youtube" -> "YOUTUBE"
    else -> "ИСТОЧНИК"
}
