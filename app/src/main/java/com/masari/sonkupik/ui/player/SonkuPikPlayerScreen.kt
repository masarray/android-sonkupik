package com.masari.sonkupik.ui.player

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.masari.arsonkupik.audio.ArSonKuPikMeters
import com.masari.arsonkupik.audio.ArSonKuPikPresetUi
import com.masari.arsonkupik.audio.ArSonKuPikSmartMusicEngine
import com.masari.arsonkupik.audio.AudioLibrarySource
import com.masari.arsonkupik.audio.AudioLibraryScanner
import com.masari.arsonkupik.audio.AudioLibraryTrack
import com.masari.arsonkupik.audio.PresetCatalog
import com.masari.sonkupik.R
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private val BgTop = Color(0xFFF3DDE3)
private val BgBottom = Color(0xFFE9E1F6)
private val Glass = Color(0xB8FFF8FF)
private val GlassDeep = Color(0x66F6ECF8)
private val PanelFill = Color(0x88FFF8FF)
private val PanelStroke = Color(0x82FFFFFF)
private val TextMain = Color(0xFF211C35)
private val TextSoft = Color(0xFF5C536F)
private val TextDim = Color(0xFF827A91)
private val Peach = Color(0xFFFFC4A1)
private val PeachStrong = Color(0xFFFFB28C)
private val Peach2 = Color(0xFFFFDEC8)
private val Violet = Color(0xFFA786FF)
private val Cyan = Color(0xFF76DDF2)
private val Green = Color(0xFF89F0B7)
private val Amber = Color(0xFFFFCB73)
private val Danger = Color(0xFFFF6E8D)

private enum class MainTab(val label: String) { Home("Home"), Player("Player"), Dsp("DSP"), Library("Library") }

private enum class LibraryFilter(val label: String) { All("All"), Local("Local"), Imported("Imported"), Demo("Demo") }

private enum class DspModule(val title: String, val subtitle: String) {
    Trim("Trim", "Output trim"),
    Bass("Smart Bass", "Low body and warmth"),
    Vocal("Vocal Body", "Center focus and presence"),
    Width("Stereo Magic", "Safe width and openness"),
    Air("Smart Air", "Detail and sparkle"),
    Loud("Loudness", "Enhance intensity"),
    Protect("Protect", "Limiter safety"),
}

private data class DemoTrack(
    val title: String,
    val artist: String,
    val format: String,
    val durationSec: Int,
    val toneHz: Double,
    val audioUri: Uri? = null,
    val sourceLabel: String = "Built-in",
)

private data class MacroState(
    val trimDb: Float,
    val bass: Float,
    val vocal: Float,
    val width: Float,
    val air: Float,
    val loud: Float,
    val protect: Boolean,
)

private data class DspProbe(
    val meters: ArSonKuPikMeters?,
    val error: String? = null,
)

@Composable
fun SonkuPikPlayerScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val smartEngine = remember(context) { ArSonKuPikSmartMusicEngine(context) }
    DisposableEffect(smartEngine) { onDispose { smartEngine.close() } }

    val presets = remember { PresetCatalog.builtIns }
    val demoTracks = remember {
        listOf(
            DemoTrack("Midnight Drive", "ArSonKuPik", "FLAC - 44.1 kHz - 16 bit", 222, 440.0),
            DemoTrack("Horeg Runner", "Mas Ari Lab", "WAV - 48 kHz - 24 bit", 218, 92.0),
            DemoTrack("Vocal Crown", "Signature Test", "MP3 - 320 kbps", 192, 330.0),
            DemoTrack("Relax Night Field", "ArSonKuPik", "AAC - 256 kbps", 275, 220.0),
            DemoTrack("Car Audio Loud", "Road Test", "FLAC - 48 kHz - 24 bit", 233, 132.0),
        )
    }
    var userTracks by remember { mutableStateOf<List<DemoTrack>>(emptyList()) }
    var isScanningLibrary by remember { mutableStateOf(false) }
    var libraryMessage by remember { mutableStateOf("Import audio files or scan device music.") }
    val tracks = remember(demoTracks, userTracks) {
        if (userTracks.isEmpty()) demoTracks else userTracks + demoTracks
    }

    var tab by remember { mutableStateOf(MainTab.Home) }
    var selectedPreset by remember { mutableStateOf(presets.first()) }
    var activeTrackIndex by remember { mutableIntStateOf(0) }
    var progress by remember { mutableFloatStateOf(0.32f) }
    var isPlaying by remember { mutableStateOf(false) }
    var favoriteOn by remember { mutableStateOf(false) }
    var shuffleOn by remember { mutableStateOf(false) }
    var repeatOn by remember { mutableStateOf(false) }
    var enhanceOn by remember { mutableStateOf(true) }
    var showHomeMenu by remember { mutableStateOf(false) }
    var compareSlot by remember { mutableStateOf("A") }
    var activeModule by remember { mutableStateOf(DspModule.Bass) }

    var trimDb by remember { mutableFloatStateOf(-0.55f) }
    var bass by remember { mutableFloatStateOf(selectedPreset.defaultBass) }
    var vocal by remember { mutableFloatStateOf(selectedPreset.defaultVocal) }
    var width by remember { mutableFloatStateOf(selectedPreset.defaultWidth) }
    var air by remember { mutableFloatStateOf(selectedPreset.defaultAir) }
    var loud by remember { mutableFloatStateOf(selectedPreset.defaultLoud) }
    var protect by remember { mutableStateOf(true) }
    var dspProbe by remember { mutableStateOf(DspProbe(smartEngine.meters())) }

    fun mergeUserTracks(incoming: List<DemoTrack>) {
        userTracks = mergeAudioTracks(userTracks, incoming)
    }

    fun hasAudioPermission(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    fun scanDeviceMusic() {
        scope.launch {
            isScanningLibrary = true
            libraryMessage = "Scanning Music folder in background..."
            runCatching {
                AudioLibraryScanner.scanDevice(context)
            }.onSuccess { found ->
                val mapped = found.map { it.toDemoTrack() }
                mergeUserTracks(mapped)
                libraryMessage = if (mapped.isEmpty()) {
                    "No local music found yet. Import MP3, WAV, or FLAC manually."
                } else {
                    "${mapped.size} local audio files found."
                }
            }.onFailure {
                libraryMessage = it.message ?: "Device music scan failed."
            }
            isScanningLibrary = false
        }
    }

    val scanPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            scanDeviceMusic()
        } else {
            libraryMessage = "Allow audio access to scan music stored on this phone."
        }
    }

    val importAudioLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        uris.forEach { uri ->
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
        }
        scope.launch {
            isScanningLibrary = true
            libraryMessage = "Reading imported audio metadata..."
            runCatching {
                AudioLibraryScanner.describeUris(context, uris)
            }.onSuccess { imported ->
                val mapped = imported.map { it.toDemoTrack() }
                mergeUserTracks(mapped)
                libraryMessage = if (mapped.isEmpty()) {
                    "Selected files could not be read as audio."
                } else {
                    "${mapped.size} imported audio files ready."
                }
            }.onFailure {
                libraryMessage = it.message ?: "Audio import failed."
            }
            isScanningLibrary = false
        }
    }

    fun requestDeviceScan() {
        if (hasAudioPermission()) {
            scanDeviceMusic()
        } else {
            val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Manifest.permission.READ_MEDIA_AUDIO
            } else {
                Manifest.permission.READ_EXTERNAL_STORAGE
            }
            scanPermissionLauncher.launch(permission)
        }
    }

    fun importAudioFiles() {
        importAudioLauncher.launch(arrayOf("audio/*"))
    }

    fun currentMacro() = MacroState(trimDb, bass, vocal, width, air, loud, protect)
    var slotA by remember { mutableStateOf(currentMacro()) }
    var slotB by remember { mutableStateOf(MacroState(-1.0f, 56f, 60f, 55f, 58f, 60f, true)) }

    fun applyMacro(m: MacroState) {
        trimDb = m.trimDb
        bass = m.bass
        vocal = m.vocal
        width = m.width
        air = m.air
        loud = m.loud
        protect = m.protect
    }

    fun applyPreset(preset: ArSonKuPikPresetUi) {
        selectedPreset = preset
        trimDb = -0.55f
        bass = preset.defaultBass
        vocal = preset.defaultVocal
        width = preset.defaultWidth
        air = preset.defaultAir
        loud = preset.defaultLoud
        protect = true
        activeModule = DspModule.Bass
        if (compareSlot == "A") slotA = currentMacro() else slotB = currentMacro()
    }

    fun nextTrack() {
        activeTrackIndex = if (shuffleOn) (activeTrackIndex + 2).mod(tracks.size) else (activeTrackIndex + 1).mod(tracks.size)
        progress = 0f
        favoriteOn = false
    }

    fun previousTrack() {
        activeTrackIndex = if (activeTrackIndex == 0) tracks.lastIndex else activeTrackIndex - 1
        progress = 0f
        favoriteOn = false
    }

    fun seekTo(newProgress: Float) {
        val safeProgress = newProgress.coerceIn(0f, 1f)
        progress = safeProgress
        smartEngine.seekToProgress(safeProgress)
    }

    val currentTrack = tracks[activeTrackIndex]

    LaunchedEffect(currentTrack) {
        val uri = currentTrack.audioUri
        if (uri != null) {
            smartEngine.loadAudioUri(
                uri = uri,
                durationSec = currentTrack.durationSec,
                progress = progress,
            )
        } else {
            smartEngine.loadTrack(
                toneHz = currentTrack.toneHz,
                durationSec = currentTrack.durationSec,
                progress = progress,
            )
        }
        smartEngine.setPlaying(isPlaying)
    }

    LaunchedEffect(context) {
        if (hasAudioPermission()) {
            scanDeviceMusic()
        }
    }

    LaunchedEffect(isPlaying) {
        smartEngine.setPlaying(isPlaying)
    }

    LaunchedEffect(enhanceOn, selectedPreset, trimDb, bass, vocal, width, air, loud, protect) {
        smartEngine.configureDsp(
            presetId = selectedPreset.id,
            bass = bass,
            vocal = vocal,
            width = width,
            air = air,
            loud = loud,
            trimDb = trimDb,
            bypass = !enhanceOn,
            protect = protect,
        )
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(180)
            dspProbe = DspProbe(smartEngine.meters(), smartEngine.error())
        }
    }

    LaunchedEffect(isPlaying, activeTrackIndex, repeatOn, shuffleOn) {
        while (isPlaying) {
            delay(250)
            val engineProgress = smartEngine.progress()
            if (engineProgress >= 0.998f) {
                if (repeatOn) {
                    seekTo(0f)
                    smartEngine.setPlaying(true)
                } else {
                    nextTrack()
                }
            } else {
                progress = engineProgress
            }
        }
    }

    val bgBrush = remember {
        Brush.verticalGradient(
            listOf(BgTop, Color(0xFFF7EAF0), Color(0xFFE7E2F8), BgBottom)
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgBrush)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Box(modifier = Modifier.weight(1f)) {
            when (tab) {
                MainTab.Home -> HomePage(
                    tracks = tracks,
                    activeTrackIndex = activeTrackIndex,
                    selectedPreset = selectedPreset,
                    enhanceOn = enhanceOn,
                    isPlaying = isPlaying,
                    progress = progress,
                    onOpenPlayer = { tab = MainTab.Player },
                    onOpenDsp = { tab = MainTab.Dsp },
                    onOpenLibrary = { tab = MainTab.Library },
                    onOpenMenu = { showHomeMenu = true },
                    onTrackSelect = {
                        activeTrackIndex = it
                        progress = 0f
                        isPlaying = true
                        tab = MainTab.Player
                    },
                    onTogglePlay = { isPlaying = !isPlaying }
                )
                MainTab.Player -> PlayerPage(
                    track = currentTrack,
                    trackIndex = activeTrackIndex,
                    trackCount = tracks.size,
                    progress = progress,
                    isPlaying = isPlaying,
                    favoriteOn = favoriteOn,
                    shuffleOn = shuffleOn,
                    repeatOn = repeatOn,
                    onToggleFavorite = { favoriteOn = !favoriteOn },
                    onTogglePlay = { isPlaying = !isPlaying },
                    onNext = { nextTrack() },
                    onPrevious = { previousTrack() },
                    onToggleShuffle = { shuffleOn = !shuffleOn },
                    onToggleRepeat = { repeatOn = !repeatOn },
                    onSeek = { seekTo(it) },
                    onOpenDsp = { tab = MainTab.Dsp },
                    onOpenLibrary = { tab = MainTab.Library },
                )
                MainTab.Dsp -> DspPage(
                    selectedPreset = selectedPreset,
                    presets = presets,
                    enhanceOn = enhanceOn,
                    compareSlot = compareSlot,
                    activeModule = activeModule,
                    trimDb = trimDb,
                    bass = bass,
                    vocal = vocal,
                    width = width,
                    air = air,
                    loud = loud,
                    protect = protect,
                    dspProbe = dspProbe,
                    onPresetSelected = { applyPreset(it) },
                    onEnhanceToggle = { enhanceOn = !enhanceOn },
                    onCompareSlotChange = { slot ->
                        if (compareSlot == "A") slotA = currentMacro() else slotB = currentMacro()
                        compareSlot = slot
                        applyMacro(if (slot == "A") slotA else slotB)
                    },
                    onModuleSelected = { activeModule = it },
                    onValueChange = { module, newValue ->
                        when (module) {
                            DspModule.Trim -> trimDb = newValue.coerceIn(-6f, 6f)
                            DspModule.Bass -> bass = newValue.coerceIn(0f, 100f)
                            DspModule.Vocal -> vocal = newValue.coerceIn(0f, 100f)
                            DspModule.Width -> width = newValue.coerceIn(0f, 100f)
                            DspModule.Air -> air = newValue.coerceIn(0f, 100f)
                            DspModule.Loud -> loud = newValue.coerceIn(0f, 100f)
                            DspModule.Protect -> protect = newValue > 0.5f
                        }
                        if (compareSlot == "A") slotA = currentMacro() else slotB = currentMacro()
                    },
                    onProtectToggle = {
                        protect = !protect
                        if (compareSlot == "A") slotA = currentMacro() else slotB = currentMacro()
                    }
                )
                MainTab.Library -> LibraryPage(
                    tracks = tracks,
                    activeTrackIndex = activeTrackIndex,
                    isScanning = isScanningLibrary,
                    libraryMessage = libraryMessage,
                    onImportAudio = { importAudioFiles() },
                    onScanDevice = { requestDeviceScan() },
                    onTrackSelect = {
                        activeTrackIndex = it
                        progress = 0f
                        isPlaying = true
                        tab = MainTab.Player
                    }
                )
            }

            if (showHomeMenu) {
                HomeOverflowSheet(
                    onDismiss = { showHomeMenu = false },
                    onImportAudio = {
                        showHomeMenu = false
                        importAudioFiles()
                    },
                    onScanDevice = {
                        showHomeMenu = false
                        requestDeviceScan()
                    },
                    onOpenLibrary = {
                        showHomeMenu = false
                        tab = MainTab.Library
                    },
                    onOpenDsp = {
                        showHomeMenu = false
                        tab = MainTab.Dsp
                    },
                    onOpenPlayer = {
                        showHomeMenu = false
                        tab = MainTab.Player
                    }
                )
            }
        }

        BottomNav(
            current = tab,
            onTabChange = { tab = it }
        )
    }
}

private val DeepBlue = Color(0xFFE9E1F6)

@Composable
private fun HomePage(
    tracks: List<DemoTrack>,
    activeTrackIndex: Int,
    selectedPreset: ArSonKuPikPresetUi,
    enhanceOn: Boolean,
    isPlaying: Boolean,
    progress: Float,
    onOpenPlayer: () -> Unit,
    onOpenDsp: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenMenu: () -> Unit,
    onTrackSelect: (Int) -> Unit,
    onTogglePlay: () -> Unit,
) {
    val activeTrack = tracks[activeTrackIndex]
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Good Evening", color = TextSoft, fontSize = 13.sp)
                Text(text = "SonKuPik Player", color = TextMain, fontSize = 27.sp, fontWeight = FontWeight.SemiBold)
            }
            RoundIconButton(icon = Icons.Rounded.MoreHoriz, tint = TextMain, onClick = onOpenMenu, size = 50.dp, iconSize = 24.dp)
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(184.dp)
                .padding(top = 18.dp)
                .clip(RoundedCornerShape(28.dp))
                .clickable { onOpenPlayer() }
        ) {
            Image(
                painter = painterResource(R.drawable.mood_reflective),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xB5071020))))
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(18.dp)
            ) {
                Text(text = "Calm & Reflective", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                Text(text = selectedPreset.name, color = Color(0xFFFFE8DC), fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
            }
            RoundIconButton(
                icon = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                tint = Color(0xFF30243A),
                onClick = onTogglePlay,
                background = Color(0xEFFFF3EA),
                size = 52.dp,
                iconSize = 27.dp,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
            )
        }

        Text(
            text = "Made for You",
            color = TextMain,
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(top = 22.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MoodCard("Cloud Nine", "Pastel clean", R.drawable.mood_city_cloud, onClick = onOpenLibrary)
            MoodCard("Midnight Drive", "Signature", R.drawable.cover_midnight_drive, onClick = onOpenPlayer)
            MoodCard("Smart DSP", if (enhanceOn) "Enhance on" else "Bypassed", R.drawable.mood_reflective, onClick = onOpenDsp)
        }

        GlassPanel(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 22.dp)
                .clickable { onOpenPlayer() },
            innerPadding = 14.dp
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(58.dp)
                        .clip(RoundedCornerShape(18.dp))
                ) {
                    Image(
                        painter = painterResource(R.drawable.cover_midnight_drive),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
                Column(
                    modifier = Modifier
                        .padding(start = 14.dp)
                        .weight(1f)
                ) {
                    Text(text = activeTrack.title, color = TextMain, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                    Text(text = activeTrack.artist, color = TextSoft, fontSize = 13.sp, modifier = Modifier.padding(top = 3.dp))
                    SeekBar(progress = progress, modifier = Modifier.padding(top = 10.dp), onSeek = {})
                }
                RoundIconButton(
                    icon = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    tint = Color(0xFF30243A),
                    onClick = onTogglePlay,
                    background = Peach2,
                    size = 48.dp,
                    iconSize = 25.dp
                )
            }
        }

        Text(
            text = "Recently Played",
            color = TextMain,
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(top = 22.dp)
        )

        tracks.take(4).forEachIndexed { index, track ->
            RecentTrackRow(
                track = track,
                active = index == activeTrackIndex,
                onClick = { onTrackSelect(index) }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun HomeOverflowSheet(
    onDismiss: () -> Unit,
    onImportAudio: () -> Unit,
    onScanDevice: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenDsp: () -> Unit,
    onOpenPlayer: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0x5A211C35))
                .clickable { onDismiss() }
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 18.dp, vertical = 10.dp)
                .clip(RoundedCornerShape(30.dp))
                .background(Color(0xF7FFF8FF))
                .border(1.dp, Color(0xE8FFFFFF), RoundedCornerShape(30.dp))
                .clickable { }
                .padding(18.dp)
        ) {
            Text(text = "Music actions", color = TextMain, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Text(
                text = "Add music, refresh local files, or jump to tuning.",
                color = TextDim,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 10.dp)
            )
            HomeMenuAction(
                icon = painterResource(R.drawable.ic_import_music),
                title = "Import music",
                subtitle = "Pick MP3, WAV, FLAC, or other audio files",
                onClick = onImportAudio,
            )
            HomeMenuAction(
                icon = Icons.Rounded.Search,
                title = "Scan device music",
                subtitle = "Refresh songs found in the phone Music folder",
                onClick = onScanDevice,
            )
            HomeMenuAction(
                icon = Icons.Rounded.LibraryMusic,
                title = "Open library",
                subtitle = "Browse local, imported, and built-in tracks",
                onClick = onOpenLibrary,
            )
            HomeMenuAction(
                icon = Icons.Rounded.Equalizer,
                title = "DSP workspace",
                subtitle = "Tune the active preset and smart protection",
                onClick = onOpenDsp,
            )
            HomeMenuAction(
                icon = Icons.Rounded.MusicNote,
                title = "Now playing",
                subtitle = "Return to the vinyl player",
                onClick = onOpenPlayer,
            )
        }
    }
}

@Composable
private fun HomeMenuAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(Color(0xFFFFE3D1)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = Color(0xFF31263A), modifier = Modifier.size(22.dp))
        }
        Column(
            modifier = Modifier
                .padding(start = 13.dp)
                .weight(1f)
        ) {
            Text(text = title, color = TextMain, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(text = subtitle, color = TextDim, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

@Composable
private fun HomeMenuAction(
    icon: Painter,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(Color(0xFFFFE3D1)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = Color(0xFF31263A), modifier = Modifier.size(22.dp))
        }
        Column(
            modifier = Modifier
                .padding(start = 13.dp)
                .weight(1f)
        ) {
            Text(text = title, color = TextMain, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(text = subtitle, color = TextDim, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

@Composable
private fun MoodCard(
    title: String,
    subtitle: String,
    imageRes: Int,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .width(138.dp)
            .height(116.dp)
            .clip(RoundedCornerShape(22.dp))
            .clickable { onClick() }
    ) {
        Image(
            painter = painterResource(imageRes),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xBA061126))))
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(12.dp)
        ) {
            Text(text = title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(text = subtitle, color = Color(0xFFEDE3F0), fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

@Composable
private fun RecentTrackRow(
    track: DemoTrack,
    active: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 11.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (active) Color(0x24FFFFFF) else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
        ) {
            Image(
                painter = painterResource(R.drawable.cover_midnight_drive),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
        Column(
            modifier = Modifier
                .padding(start = 12.dp)
                .weight(1f)
        ) {
            Text(text = track.title, color = TextMain, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(text = track.format, color = TextDim, fontSize = 11.sp, modifier = Modifier.padding(top = 3.dp))
        }
        Text(text = if (active) "Now" else formatTime(track.durationSec), color = if (active) Peach else TextDim, fontSize = 12.sp)
    }
}

@Composable
private fun PlayerPage(
    track: DemoTrack,
    trackIndex: Int,
    trackCount: Int,
    progress: Float,
    isPlaying: Boolean,
    favoriteOn: Boolean,
    shuffleOn: Boolean,
    repeatOn: Boolean,
    onToggleFavorite: () -> Unit,
    onTogglePlay: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleRepeat: () -> Unit,
    onSeek: (Float) -> Unit,
    onOpenDsp: () -> Unit,
    onOpenLibrary: () -> Unit,
) {
    val infinite = rememberInfiniteTransition(label = "vinyl")
    val spinningAngle by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "spin"
    )
    val displayedAngle = if (isPlaying) spinningAngle else 22f

    val platter: Painter = painterResource(R.drawable.turntable_platter)
    val tonearm: Painter = painterResource(R.drawable.turntable_tonearm)
    val coverArt: Painter = painterResource(R.drawable.cover_midnight_drive)
    val tonearmAngle = if (isPlaying) -5f + (progress * 9f) else -14f

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        TopHeroBar(track.title)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(330.dp),
            contentAlignment = Alignment.Center
        ) {
            TurntableAura(
                progress = progress,
                modifier = Modifier
                    .size(312.dp)
                    .align(Alignment.Center)
                    .offset(x = (-8).dp, y = 2.dp)
            )

            Image(
                painter = platter,
                contentDescription = "Vinyl platter",
                modifier = Modifier
                    .size(288.dp)
                    .graphicsLayer { rotationZ = displayedAngle },
                contentScale = ContentScale.Fit
            )

            Box(
                modifier = Modifier
                    .size(90.dp)
                    .clip(CircleShape)
                    .graphicsLayer { rotationZ = displayedAngle }
                    .border(2.dp, Color(0x22FFFFFF), CircleShape)
            ) {
                Image(
                    painter = coverArt,
                    contentDescription = "Album art",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(Color(0xFFF7EFE5), CircleShape)
            )

            Image(
                painter = tonearm,
                contentDescription = "Tonearm",
                modifier = Modifier
                    .size(222.dp)
                    .align(Alignment.TopEnd)
                    .offset(x = 30.dp, y = 2.dp)
                    .graphicsLayer {
                        rotationZ = tonearmAngle
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.82f, 0.18f)
                    },
                contentScale = ContentScale.Fit
            )
        }

        Text(
            text = track.title,
            color = TextMain,
            fontSize = 26.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = track.artist,
            color = Peach,
            fontSize = 18.sp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp)
        )
        Text(
            text = track.format,
            color = TextDim,
            fontSize = 13.sp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SeekBar(
                progress = progress,
                modifier = Modifier.weight(1f),
                onSeek = onSeek,
            )
            RoundIconButton(
                icon = if (favoriteOn) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                tint = if (favoriteOn) Peach else TextMain,
                onClick = onToggleFavorite,
                modifier = Modifier.padding(start = 18.dp)
            )
            RoundIconButton(
                icon = Icons.Rounded.GraphicEq,
                tint = Peach,
                onClick = onOpenDsp,
                modifier = Modifier.padding(start = 10.dp)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = formatTime((track.durationSec * progress).roundToInt()), color = TextDim, fontSize = 12.sp)
            Text(text = "${trackIndex + 1} / $trackCount", color = TextSoft, fontSize = 12.sp)
            Text(text = formatTime(track.durationSec), color = TextDim, fontSize = 12.sp)
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SmallPlayerButton(Icons.Rounded.Shuffle, active = shuffleOn, onClick = onToggleShuffle)
            SmallPlayerButton(Icons.Rounded.SkipPrevious, onClick = onPrevious)
            LargePlayButton(isPlaying = isPlaying, onClick = onTogglePlay)
            SmallPlayerButton(Icons.Rounded.SkipNext, onClick = onNext)
            SmallPlayerButton(Icons.Rounded.Repeat, active = repeatOn, onClick = onToggleRepeat)
        }

        Spacer(modifier = Modifier.height(34.dp))
    }
}

@Composable
private fun TurntableAura(progress: Float, modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val radius = size.minDimension / 2f
        drawCircle(
            brush = Brush.radialGradient(
                listOf(Color(0x66FFF1E8), Color(0x20FFC4A1), Color.Transparent),
                center = center,
                radius = radius
            ),
            radius = radius
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.26f),
            radius = radius * 0.89f,
            style = Stroke(width = 4f)
        )
        drawCircle(
            color = Peach.copy(alpha = 0.26f),
            radius = radius * 0.83f,
            style = Stroke(width = 2f)
        )
        for (i in 0 until 34) {
            val angle = ((i / 34f) * 360f + progress * 80f) * PI.toFloat() / 180f
            val dotRadius = if (i % 5 == 0) 3.6f else 2.2f
            val ringRadius = radius * if (i % 2 == 0) 0.93f else 0.78f
            val x = center.x + cos(angle) * ringRadius
            val y = center.y + sin(angle) * ringRadius
            drawCircle(
                color = if (i % 5 == 0) Peach.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.24f),
                radius = dotRadius,
                center = Offset(x, y)
            )
        }
    }
}

@Composable
private fun DspPage(
    selectedPreset: ArSonKuPikPresetUi,
    presets: List<ArSonKuPikPresetUi>,
    enhanceOn: Boolean,
    compareSlot: String,
    activeModule: DspModule,
    trimDb: Float,
    bass: Float,
    vocal: Float,
    width: Float,
    air: Float,
    loud: Float,
    protect: Boolean,
    dspProbe: DspProbe,
    onPresetSelected: (ArSonKuPikPresetUi) -> Unit,
    onEnhanceToggle: () -> Unit,
    onCompareSlotChange: (String) -> Unit,
    onModuleSelected: (DspModule) -> Unit,
    onValueChange: (DspModule, Float) -> Unit,
    onProtectToggle: () -> Unit,
) {
    val meter = dspProbe.meters
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RoundIconButton(icon = Icons.Rounded.KeyboardArrowDown, tint = TextMain, onClick = {}, size = 42.dp, iconSize = 22.dp)
            Text(
                text = selectedPreset.name,
                color = TextMain,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color(0xB8FFF8FF))
                    .border(1.dp, Color(0xAAFFFFFF), RoundedCornerShape(999.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp)
                    .weight(1f)
            )
            Spacer(Modifier.width(10.dp))
            ComparePill(label = "A", active = compareSlot == "A", onClick = { onCompareSlotChange("A") })
            Spacer(Modifier.width(8.dp))
            ComparePill(label = "B", active = compareSlot == "B", onClick = { onCompareSlotChange("B") })
            Spacer(Modifier.width(8.dp))
            RoundIconButton(icon = Icons.Rounded.MoreHoriz, tint = TextMain, onClick = {}, size = 42.dp, iconSize = 22.dp)
        }

        ChipRow(
            items = presets.map { it.name },
            selected = selectedPreset.name,
            onSelect = { label -> presets.firstOrNull { it.name == label }?.let(onPresetSelected) },
            topPadding = 12.dp
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "Bypass", color = TextSoft, fontSize = 13.sp, modifier = Modifier.padding(end = 8.dp))
            CompactSwitch(enabled = !enhanceOn, onToggle = onEnhanceToggle, activeColor = TextDim)
            Spacer(Modifier.weight(1f))
            Text(text = "Output", color = TextSoft, fontSize = 12.sp)
            Text(
                text = String.format(Locale.US, "%+.1f dB", meter?.outputPeakDb ?: trimDb),
                color = TextMain,
                fontSize = 12.sp,
                modifier = Modifier
                    .padding(start = 7.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0x92FFF8FF))
                    .padding(horizontal = 8.dp, vertical = 5.dp)
            )
        }

        DspCurvePanel(
            bass = bass,
            vocal = vocal,
            width = width,
            air = air,
            loud = loud,
            meter = meter,
            modifier = Modifier.padding(top = 12.dp)
        )

        GlassPanel(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            innerPadding = 10.dp
        ) {
            VstRowSlider(
                label = "Trim",
                value = trimDb,
                displayText = String.format(Locale.US, "%+.1f dB", trimDb),
                rangeStart = -6f,
                rangeEnd = 6f,
                accent = Amber,
                icon = Icons.Rounded.Equalizer,
                onValueChange = { onValueChange(DspModule.Trim, it) },
            )
            VstRowSlider(
                label = "Bass",
                value = bass,
                displayText = bass.roundToInt().toString(),
                rangeStart = 0f,
                rangeEnd = 100f,
                accent = Peach,
                icon = Icons.Rounded.GraphicEq,
                onValueChange = { onValueChange(DspModule.Bass, it) },
                modifier = Modifier.padding(top = 10.dp)
            )
            VstRowSlider(
                label = "Vocal",
                value = vocal,
                displayText = vocal.roundToInt().toString(),
                rangeStart = 0f,
                rangeEnd = 100f,
                accent = Violet,
                icon = Icons.Rounded.AutoAwesome,
                onValueChange = { onValueChange(DspModule.Vocal, it) },
                modifier = Modifier.padding(top = 10.dp)
            )
            VstRowSlider(
                label = "Width",
                value = width,
                displayText = width.roundToInt().toString(),
                rangeStart = 0f,
                rangeEnd = 100f,
                accent = Cyan,
                icon = Icons.Rounded.Equalizer,
                onValueChange = { onValueChange(DspModule.Width, it) },
                modifier = Modifier.padding(top = 10.dp)
            )
            VstRowSlider(
                label = "Air",
                value = air,
                displayText = air.roundToInt().toString(),
                rangeStart = 0f,
                rangeEnd = 100f,
                accent = Color(0xFFB6F6FF),
                icon = Icons.Rounded.AutoAwesome,
                onValueChange = { onValueChange(DspModule.Air, it) },
                modifier = Modifier.padding(top = 10.dp)
            )
            VstRowSlider(
                label = "Loudness",
                value = loud,
                displayText = loud.roundToInt().toString(),
                rangeStart = 0f,
                rangeEnd = 100f,
                accent = PeachStrong,
                icon = Icons.Rounded.GraphicEq,
                onValueChange = { onValueChange(DspModule.Loud, it) },
                modifier = Modifier.padding(top = 10.dp)
            )

            ProtectStripCard(
                enabled = protect,
                gainReductionDb = meter?.gainReductionDb ?: 0f,
                clipping = meter?.clipping == true,
                onToggle = onProtectToggle,
                modifier = Modifier.padding(top = 10.dp)
            )

            if (dspProbe.error != null) {
                Text(
                    text = "DSP Probe error: ${dspProbe.error}",
                    color = Danger,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 12.dp)
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Correlation ${String.format(Locale.US, "%.2f", meter?.correlation ?: 1f)}", color = TextDim, fontSize = 11.sp)
                    Text(text = "Clipping ${if (meter?.clipping == true) "Yes" else "No"}", color = if (meter?.clipping == true) Danger else Green, fontSize = 11.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun DspCurvePanel(
    bass: Float,
    vocal: Float,
    width: Float,
    air: Float,
    loud: Float,
    meter: ArSonKuPikMeters?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(142.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0x92FFF8FF))
            .border(1.dp, Color(0xA8FFFFFF), RoundedCornerShape(24.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        androidx.compose.foundation.Canvas(
            modifier = Modifier
                .weight(1f)
                .fillMaxSize()
        ) {
            val grid = Color(0x30211C35)
            for (i in 0..4) {
                val y = size.height * i / 4f
                drawLine(grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
            }
            for (i in 0..5) {
                val x = size.width * i / 5f
                drawLine(grid, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
            }
            val points = listOf(
                Offset(0f, size.height * (0.62f - bass / 500f)),
                Offset(size.width * 0.22f, size.height * (0.42f - vocal / 650f)),
                Offset(size.width * 0.46f, size.height * (0.54f - loud / 700f)),
                Offset(size.width * 0.72f, size.height * (0.38f - air / 650f)),
                Offset(size.width, size.height * (0.44f - width / 900f)),
            )
            val path = Path().apply {
                moveTo(points.first().x, points.first().y)
                for (i in 1 until points.size) {
                    val previous = points[i - 1]
                    val current = points[i]
                    cubicTo(
                        (previous.x + current.x) / 2f, previous.y,
                        (previous.x + current.x) / 2f, current.y,
                        current.x, current.y
                    )
                }
            }
            drawPath(path, color = Violet.copy(alpha = 0.26f), style = Stroke(width = 13f, cap = StrokeCap.Round))
            drawPath(path, color = PeachStrong, style = Stroke(width = 4f, cap = StrokeCap.Round))
            points.forEachIndexed { index, point ->
                val accent = listOf(Peach, Violet, Danger, Cyan, Color(0xFF5F8CFF))[index]
                drawCircle(color = Color.White, radius = 8f, center = point)
                drawCircle(color = accent, radius = 5f, center = point)
            }
        }
        Spacer(Modifier.width(12.dp))
        OutputMeterStrip(meter = meter)
    }
}

@Composable
private fun OutputMeterStrip(meter: ArSonKuPikMeters?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = "OUT", color = TextSoft, fontSize = 10.sp, fontWeight = FontWeight.Medium)
        androidx.compose.foundation.Canvas(
            modifier = Modifier
                .width(34.dp)
                .height(94.dp)
                .padding(top = 6.dp)
        ) {
            val bars = 2
            val gap = 6f
            val barWidth = (size.width - gap) / bars
            val level = ((meter?.outputPeakDb ?: -18f) + 42f) / 42f
            val normalized = level.coerceIn(0.12f, 1f)
            for (i in 0 until bars) {
                val x = i * (barWidth + gap)
                drawRoundRect(
                    color = Color(0x2A211C35),
                    topLeft = Offset(x, 0f),
                    size = androidx.compose.ui.geometry.Size(barWidth, size.height),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(5f, 5f)
                )
                val activeH = size.height * normalized * (if (i == 0) 0.92f else 1f)
                drawRoundRect(
                    brush = Brush.verticalGradient(listOf(Danger, Amber, Green)),
                    topLeft = Offset(x, size.height - activeH),
                    size = androidx.compose.ui.geometry.Size(barWidth, activeH),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(5f, 5f)
                )
            }
        }
    }
}

@Composable
private fun VstRowSlider(
    label: String,
    value: Float,
    displayText: String,
    rangeStart: Float,
    rangeEnd: Float,
    accent: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val normalized = ((value - rangeStart) / (rangeEnd - rangeStart)).coerceIn(0f, 1f)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xA8FFF8FF))
            .border(1.dp, Color(0xAAFFFFFF), RoundedCornerShape(18.dp))
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
        }
        Text(text = label, color = TextMain, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 10.dp).width(72.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(42.dp)
                .pointerInput(label, value, rangeStart, rangeEnd) {
                    fun xToValue(x: Float): Float {
                        val pct = (x / size.width.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)
                        return rangeStart + (rangeEnd - rangeStart) * pct
                    }
                    detectDragGestures(
                        onDragStart = { offset -> onValueChange(xToValue(offset.x)) },
                        onDrag = { change, _ ->
                            change.consume()
                            onValueChange(xToValue(change.position.x))
                        }
                    )
                }
        ) {
            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                val y = size.height / 2f
                val fillX = size.width * normalized
                drawLine(Color(0x2F211C35), Offset(0f, y), Offset(size.width, y), strokeWidth = 9f, cap = StrokeCap.Round)
                drawLine(
                    brush = Brush.horizontalGradient(listOf(accent, Peach2)),
                    start = Offset(0f, y),
                    end = Offset(fillX, y),
                    strokeWidth = 9f,
                    cap = StrokeCap.Round
                )
                for (i in 0..20) {
                    val x = size.width * i / 20f
                    drawLine(Color(0x20211C35), Offset(x, y + 9f), Offset(x, y + 15f), strokeWidth = 1f)
                }
                drawRoundRect(
                    color = Color(0xF2FFF8FF),
                    topLeft = Offset(fillX - 13f, y - 18f),
                    size = androidx.compose.ui.geometry.Size(26f, 36f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f)
                )
                drawRoundRect(
                    color = accent.copy(alpha = 0.55f),
                    topLeft = Offset(fillX - 4f, y - 10f),
                    size = androidx.compose.ui.geometry.Size(8f, 20f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f)
                )
            }
        }
        Text(text = displayText, color = TextMain, fontSize = 12.sp, textAlign = TextAlign.End, modifier = Modifier.width(52.dp))
    }
}

@Composable
private fun ProtectStripCard(
    enabled: Boolean,
    gainReductionDb: Float,
    clipping: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xA8FFF8FF))
            .border(1.dp, Color(0xAAFFFFFF), RoundedCornerShape(18.dp))
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = if (clipping) Danger else TextSoft, modifier = Modifier.size(22.dp))
        Column(modifier = Modifier.padding(start = 10.dp).weight(1f)) {
            Text(text = "Smart Protect", color = TextMain, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(text = if (enabled) "Safe listening - auto limiting" else "Protection off", color = TextDim, fontSize = 10.sp, modifier = Modifier.padding(top = 2.dp))
        }
        Text(text = "GR", color = TextSoft, fontSize = 10.sp, modifier = Modifier.padding(end = 6.dp))
        Text(text = String.format(Locale.US, "%.1f dB", gainReductionDb), color = if (gainReductionDb < -1f) PeachStrong else TextMain, fontSize = 12.sp, modifier = Modifier.padding(end = 10.dp))
        CompactSwitch(enabled = enabled, onToggle = onToggle)
    }
}

@Composable
private fun LibraryPage(
    tracks: List<DemoTrack>,
    activeTrackIndex: Int,
    isScanning: Boolean,
    libraryMessage: String,
    onImportAudio: () -> Unit,
    onScanDevice: () -> Unit,
    onTrackSelect: (Int) -> Unit,
) {
    var filter by remember { mutableStateOf(LibraryFilter.All) }
    val visibleTracks = remember(tracks, filter) {
        tracks.mapIndexed { index, track -> index to track }.filter { (_, track) ->
            when (filter) {
                LibraryFilter.All -> true
                LibraryFilter.Local -> track.sourceLabel == "Device"
                LibraryFilter.Imported -> track.sourceLabel == "Imported"
                LibraryFilter.Demo -> track.sourceLabel == "Built-in"
            }
        }
    }
    val localTrackCount = tracks.count { it.sourceLabel != "Built-in" }
    val statusText = when {
        isScanning -> "Scanning device music..."
        localTrackCount == 0 && filter == LibraryFilter.All -> "Demo tracks are shown until you import or scan local music."
        else -> libraryMessage
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Library",
                    color = TextMain,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Local music and imported audio.",
                    color = TextDim,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                RoundPainterButton(
                    icon = painterResource(R.drawable.ic_import_music),
                    tint = TextMain,
                    onClick = onImportAudio,
                    background = Color(0xE8FFF8FF),
                    size = 46.dp,
                    iconSize = 21.dp
                )
                RoundIconButton(
                    icon = Icons.Rounded.Search,
                    tint = if (isScanning) PeachStrong else TextMain,
                    onClick = onScanDevice,
                    background = Color(0xE8FFF8FF),
                    size = 46.dp,
                    iconSize = 21.dp
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LibraryFilter.entries.forEach { item ->
                LibraryFilterChip(
                    label = when (item) {
                        LibraryFilter.All -> "All ${tracks.size}"
                        LibraryFilter.Local -> "Local ${tracks.count { it.sourceLabel == "Device" }}"
                        LibraryFilter.Imported -> "Imported ${tracks.count { it.sourceLabel == "Imported" }}"
                        LibraryFilter.Demo -> "Demo ${tracks.count { it.sourceLabel == "Built-in" }}"
                    },
                    active = filter == item,
                    onClick = { filter = item }
                )
            }
        }

        Text(
            text = statusText,
            color = if (isScanning) PeachStrong else TextDim,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 12.dp)
        )

        if (visibleTracks.isEmpty()) {
            GlassPanel(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                innerPadding = 18.dp
            ) {
                Text(text = "No tracks here yet", color = TextMain, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                Text(
                    text = "Use the top actions to import audio or scan the phone music folder.",
                    color = TextDim,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }

        visibleTracks.forEach { (index, track) ->
            GlassPanel(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp)
                    .clickable { onTrackSelect(index) }
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(RoundedCornerShape(16.dp))
                    ) {
                        Image(
                            painter = painterResource(R.drawable.cover_midnight_drive),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                    Column(modifier = Modifier.padding(start = 14.dp).weight(1f)) {
                        Text(text = track.title, color = TextMain, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                        Text(text = track.artist, color = Peach, fontSize = 14.sp, modifier = Modifier.padding(top = 2.dp))
                        Text(
                            text = "${track.format} - ${track.sourceLabel}",
                            color = TextDim,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    Text(
                        text = if (index == activeTrackIndex) "Now" else formatTime(track.durationSec),
                        color = if (index == activeTrackIndex) Peach else TextDim,
                        fontSize = 12.sp
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun LibraryFilterChip(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (active) PeachStrong else Color(0x74FFF8FF))
            .border(1.dp, if (active) Color(0x44FFFFFF) else Color(0x90FFFFFF), RoundedCornerShape(18.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (active) Color(0xFF31263A) else TextSoft,
            fontSize = 12.sp,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium
        )
    }
}

@Composable
private fun PresetsPage(
    presets: List<ArSonKuPikPresetUi>,
    selected: ArSonKuPikPresetUi,
    onPresetSelected: (ArSonKuPikPresetUi) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp)
    ) {
        Text(
            text = "Presets",
            color = TextMain,
            fontSize = 34.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 18.dp)
        )
        Text(text = "Pilih preset lalu lanjut tuning di halaman DSP.", color = TextDim, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp))
        presets.forEach { preset ->
            GlassPanel(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp)
                    .clickable { onPresetSelected(preset) }
            ) {
                Text(text = preset.name, color = TextMain, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                Text(text = preset.shortCopy, color = TextDim, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
                Row(modifier = Modifier.padding(top = 10.dp)) {
                    PresetMetric(label = "Bass", value = preset.defaultBass)
                    Spacer(Modifier.width(10.dp))
                    PresetMetric(label = "Vocal", value = preset.defaultVocal)
                    Spacer(Modifier.width(10.dp))
                    PresetMetric(label = "Width", value = preset.defaultWidth)
                    Spacer(Modifier.width(10.dp))
                    PresetMetric(label = "Air", value = preset.defaultAir)
                    Spacer(Modifier.width(10.dp))
                    PresetMetric(label = "Loud", value = preset.defaultLoud)
                }
                if (preset.id == selected.id) {
                    Text(text = "Active preset", color = Peach, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun TopHeroBar(title: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RoundIconButton(icon = Icons.Rounded.KeyboardArrowDown, tint = TextMain, onClick = {})
        Text(
            text = "SonKuPik Player",
            color = TextMain,
            fontSize = 22.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center
        )
        RoundIconButton(icon = Icons.Rounded.MoreHoriz, tint = TextMain, onClick = {})
    }
}

@Composable
private fun SeekBar(
    progress: Float,
    modifier: Modifier = Modifier,
    onSeek: (Float) -> Unit,
) {
    Box(
        modifier = modifier
            .height(26.dp)
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    val x = change.position.x.coerceIn(0f, size.width.toFloat())
                    onSeek((x / size.width).coerceIn(0f, 1f))
                }
            }
    ) {
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            val y = size.height / 2f
            drawLine(
                color = Color(0x3DFFFFFF),
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 8f,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = Peach,
                start = Offset(0f, y),
                end = Offset(size.width * progress, y),
                strokeWidth = 8f,
                cap = StrokeCap.Round,
            )
            drawCircle(
                color = Color(0xFFF3ECE9),
                radius = 11f,
                center = Offset(size.width * progress, y)
            )
        }
    }
}

@Composable
private fun SmallPlayerButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    active: Boolean = false,
    onClick: () -> Unit,
) {
    RoundIconButton(
        icon = icon,
        tint = if (active) Peach else TextMain,
        onClick = onClick,
        background = Color(0x82FFFFFF),
        size = 56.dp,
        iconSize = 26.dp
    )
}

@Composable
private fun LargePlayButton(
    isPlaying: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(96.dp)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    listOf(Color(0xFFFFD7BC), PeachStrong),
                    radius = 280f
                )
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
            contentDescription = null,
            tint = Color(0xFFFAF5F0),
            modifier = Modifier.size(40.dp)
        )
    }
}

@Composable
private fun RoundIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    background: Color = Color(0x72FFFFFF),
    size: androidx.compose.ui.unit.Dp = 54.dp,
    iconSize: androidx.compose.ui.unit.Dp = 28.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(background)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(iconSize))
    }
}

@Composable
private fun RoundPainterButton(
    icon: Painter,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    background: Color = Color(0x72FFFFFF),
    size: androidx.compose.ui.unit.Dp = 54.dp,
    iconSize: androidx.compose.ui.unit.Dp = 28.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(background)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(iconSize))
    }
}

@Composable
private fun CompactSwitch(
    enabled: Boolean,
    onToggle: () -> Unit,
    activeColor: Color = PeachStrong,
) {
    Box(
        modifier = Modifier
            .width(58.dp)
            .height(32.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(if (enabled) activeColor else Color(0x24FFFFFF))
            .clickable { onToggle() }
            .padding(3.dp)
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .align(if (enabled) Alignment.CenterEnd else Alignment.CenterStart)
                .clip(CircleShape)
                .background(Color(0xFFF6F0EA))
        )
    }
}

@Composable
private fun EnhanceToggle(enhanceOn: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color(0x21FFFFFF))
            .clickable { onToggle() }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = Peach, modifier = Modifier.size(16.dp))
        Text(text = "Enhance", color = TextMain, fontSize = 15.sp, modifier = Modifier.padding(start = 8.dp, end = 10.dp))
        Box(
            modifier = Modifier
                .width(54.dp)
                .height(30.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(if (enhanceOn) PeachStrong else Color(0x24FFFFFF))
        ) {
            Box(
                modifier = Modifier
                    .padding(3.dp)
                    .size(24.dp)
                    .align(if (enhanceOn) Alignment.CenterEnd else Alignment.CenterStart)
                    .clip(CircleShape)
                    .background(Color(0xFFF6F0EA))
            )
        }
    }
}

@Composable
private fun ComparePill(label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (active) PeachStrong else Color(0x18FFFFFF))
            .border(1.dp, if (active) Color.Transparent else Color(0x2AFFFFFF), RoundedCornerShape(18.dp))
            .clickable { onClick() }
            .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = label, color = if (active) Color(0xFF2A2140) else TextSoft, fontSize = 16.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ChipRow(
    items: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    topPadding: androidx.compose.ui.unit.Dp,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = topPadding)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items.forEach { item ->
            val active = item == selected
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(22.dp))
                    .background(if (active) PeachStrong else Color(0x16FFFFFF))
                    .border(1.dp, if (active) Color(0x20FFFFFF) else Color(0x24FFFFFF), RoundedCornerShape(22.dp))
                    .clickable { onSelect(item) }
                    .padding(horizontal = 18.dp, vertical = 12.dp)
            ) {
                Text(text = item, color = if (active) Color(0xFF342844) else TextSoft, fontSize = 14.sp, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal)
            }
        }
    }
}

@Composable
private fun PremiumDspSlider(
    label: String,
    subtitle: String,
    value: Float,
    displayText: String,
    rangeStart: Float,
    rangeEnd: Float,
    accent: Color,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val normalized = ((value - rangeStart) / (rangeEnd - rangeStart)).coerceIn(0f, 1f)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0x1CFFFFFF))
            .border(1.dp, Color(0x18FFFFFF), RoundedCornerShape(24.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = label, color = TextMain, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Text(text = subtitle, color = TextDim, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
            }
            Text(text = displayText, color = accent, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .padding(top = 8.dp)
                .pointerInput(label, value, rangeStart, rangeEnd) {
                    fun xToValue(x: Float): Float {
                        val width = size.width.toFloat().coerceAtLeast(1f)
                        val pct = (x / width).coerceIn(0f, 1f)
                        return rangeStart + (rangeEnd - rangeStart) * pct
                    }
                    detectDragGestures(
                        onDragStart = { offset -> onValueChange(xToValue(offset.x)) },
                        onDrag = { change, _ ->
                            change.consume()
                            onValueChange(xToValue(change.position.x))
                        }
                    )
                }
        ) {
            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                val y = size.height * 0.55f
                val trackHeight = 18f
                val endX = size.width
                val fillX = (size.width * normalized).coerceIn(0f, size.width)
                val corner = androidx.compose.ui.geometry.CornerRadius(trackHeight, trackHeight)

                drawRoundRect(
                    color = Color.White.copy(alpha = 0.15f),
                    topLeft = Offset(0f, y - trackHeight / 2f),
                    size = androidx.compose.ui.geometry.Size(endX, trackHeight),
                    cornerRadius = corner
                )
                drawRoundRect(
                    brush = Brush.horizontalGradient(
                        listOf(accent.copy(alpha = 0.55f), Peach2, accent)
                    ),
                    topLeft = Offset(0f, y - trackHeight / 2f),
                    size = androidx.compose.ui.geometry.Size(fillX, trackHeight),
                    cornerRadius = corner
                )
                for (i in 0..10) {
                    val x = size.width * i / 10f
                    drawLine(
                        color = Color.White.copy(alpha = if (i % 5 == 0) 0.30f else 0.15f),
                        start = Offset(x, y + 19f),
                        end = Offset(x, y + if (i % 5 == 0) 30f else 25f),
                        strokeWidth = if (i % 5 == 0) 2.1f else 1.3f,
                        cap = StrokeCap.Round
                    )
                }
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(accent.copy(alpha = 0.42f), Color.Transparent),
                        center = Offset(fillX, y),
                        radius = 46f
                    ),
                    radius = 46f,
                    center = Offset(fillX, y)
                )
                drawCircle(
                    color = Color(0xFFFBF0EA),
                    radius = 18f,
                    center = Offset(fillX, y)
                )
                drawCircle(
                    color = accent.copy(alpha = 0.88f),
                    radius = 6.5f,
                    center = Offset(fillX, y)
                )
            }
        }
    }
}

@Composable
private fun SmartDial(
    label: String,
    value: Float,
    displayText: String,
    rangeStart: Float,
    rangeEnd: Float,
    accent: Color,
    onValueChange: (Float) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val normalized = ((value - rangeStart) / (rangeEnd - rangeStart)).coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .size(286.dp)
            .pointerInput(label, value) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    val delta = -dragAmount.y * (rangeEnd - rangeStart) / 430f
                    onValueChange((value + delta).coerceIn(rangeStart, rangeEnd))
                }
            }
            .clickable { onReset() },
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            val minDim = size.minDimension
            val outerRadius = minDim * 0.47f
            val knobRadius = minDim * 0.31f
            val arcRadius = minDim * 0.39f
            val startAngle = 145f
            val totalSweep = 250f

            // Soft outer glow.
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        accent.copy(alpha = 0.24f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = outerRadius
                ),
                radius = outerRadius
            )

            // Premium glass outer plate.
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0x442B3248),
                        Color(0xD50A1020),
                        Color(0xF0060A14)
                    ),
                    center = center,
                    radius = outerRadius
                ),
                radius = outerRadius
            )

            drawCircle(
                color = Color.White.copy(alpha = 0.10f),
                radius = outerRadius,
                style = Stroke(width = 2.2f)
            )

            // Subtle tick ring.
            for (i in 0..44) {
                val pct = i / 44f
                val angle = Math.toRadians((startAngle + totalSweep * pct).toDouble())
                val major = i % 4 == 0
                val inner = arcRadius - if (major) 16f else 9f
                val outer = arcRadius + if (major) 5f else 1f
                val sx = center.x + cos(angle).toFloat() * inner
                val sy = center.y + sin(angle).toFloat() * inner
                val ex = center.x + cos(angle).toFloat() * outer
                val ey = center.y + sin(angle).toFloat() * outer
                drawLine(
                    color = if (pct <= normalized) accent.copy(alpha = if (major) 0.95f else 0.55f)
                    else Color.White.copy(alpha = if (major) 0.22f else 0.11f),
                    start = Offset(sx, sy),
                    end = Offset(ex, ey),
                    strokeWidth = if (major) 2.6f else 1.4f,
                    cap = StrokeCap.Round
                )
            }

            // Track arc.
            drawArc(
                color = Color.White.copy(alpha = 0.13f),
                startAngle = startAngle,
                sweepAngle = totalSweep,
                useCenter = false,
                topLeft = Offset(center.x - arcRadius, center.y - arcRadius),
                size = androidx.compose.ui.geometry.Size(arcRadius * 2f, arcRadius * 2f),
                style = Stroke(width = 15f, cap = StrokeCap.Round)
            )

            // Active arc.
            drawArc(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        accent.copy(alpha = 0.35f),
                        accent,
                        Peach2,
                        accent
                    ),
                    center = center
                ),
                startAngle = startAngle,
                sweepAngle = totalSweep * normalized,
                useCenter = false,
                topLeft = Offset(center.x - arcRadius, center.y - arcRadius),
                size = androidx.compose.ui.geometry.Size(arcRadius * 2f, arcRadius * 2f),
                style = Stroke(width = 15f, cap = StrokeCap.Round)
            )

            // Physical knob body.
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF30374E),
                        Color(0xFF151A2B),
                        Color(0xFF070B14)
                    ),
                    center = Offset(center.x - knobRadius * 0.28f, center.y - knobRadius * 0.36f),
                    radius = knobRadius * 1.25f
                ),
                radius = knobRadius
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.18f),
                radius = knobRadius,
                style = Stroke(width = 2.1f)
            )
            drawCircle(
                color = Color.Black.copy(alpha = 0.35f),
                radius = knobRadius * 0.82f,
                style = Stroke(width = 3.5f)
            )

            // Knob highlight.
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.22f),
                        Color.Transparent
                    ),
                    center = Offset(center.x - knobRadius * 0.30f, center.y - knobRadius * 0.48f),
                    radius = knobRadius * 0.60f
                ),
                radius = knobRadius * 0.60f,
                center = Offset(center.x - knobRadius * 0.16f, center.y - knobRadius * 0.22f)
            )

            // Pointer dot.
            val pointerAngle = Math.toRadians((startAngle + totalSweep * normalized).toDouble())
            val pointerR = knobRadius * 0.78f
            val px = center.x + cos(pointerAngle).toFloat() * pointerR
            val py = center.y + sin(pointerAngle).toFloat() * pointerR
            drawCircle(
                color = accent,
                radius = 8.5f,
                center = Offset(px, py)
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.75f),
                radius = 4.8f,
                center = Offset(px, py)
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = label,
                color = TextDim,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = displayText,
                color = TextMain,
                fontSize = 38.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 6.dp)
            )
            Text(
                text = "drag up/down",
                color = TextDim,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
            Text(
                text = "tap to reset",
                color = accent.copy(alpha = 0.88f),
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@Composable
private fun ProtectCard(
    enabled: Boolean,
    clipping: Boolean,
    gainReductionDb: Float,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassPanel(modifier = modifier.fillMaxWidth(), innerPadding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Smart Protect", color = TextMain, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                Text(text = "Limiter safety and clipping guard", color = TextDim, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
            }
            EnhanceToggle(enhanceOn = enabled, onToggle = onToggle)
        }
        Row(modifier = Modifier.padding(top = 16.dp)) {
            MeterMiniCard("Limiter GR", gainReductionDb, Peach, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(12.dp))
            StatusMiniCard("Clipping", if (clipping) "Detected" else "Safe", if (clipping) Danger else Green, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun MeterMiniCard(label: String, valueDb: Float?, accent: Color, modifier: Modifier = Modifier) {
    val text = when {
        valueDb == null -> "--"
        label.contains("Gain", ignoreCase = true) -> String.format(Locale.US, "%.1f dB", valueDb)
        else -> String.format(Locale.US, "%.1f dB", valueDb)
    }
    GlassPanel(modifier = modifier, innerPadding = 14.dp) {
        Text(text = label, color = TextDim, fontSize = 12.sp)
        Text(text = text, color = TextMain, fontSize = 18.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 8.dp))
        androidx.compose.foundation.Canvas(modifier = Modifier
            .fillMaxWidth()
            .height(34.dp)
            .padding(top = 12.dp)) {
            val bars = 12
            val levelNorm = if (valueDb == null) 0.1f else ((valueDb + 36f) / 36f).coerceIn(0f, 1f)
            val activeBars = (bars * levelNorm).roundToInt().coerceIn(0, bars)
            val gap = 6f
            val barWidth = (size.width - gap * (bars - 1)) / bars
            for (i in 0 until bars) {
                val x = i * (barWidth + gap)
                val h = size.height * (0.28f + 0.72f * ((i + 1).toFloat() / bars.toFloat()))
                drawRoundRect(
                    color = if (i < activeBars) accent else Color(0x2AFFFFFF),
                    topLeft = Offset(x, size.height - h),
                    size = androidx.compose.ui.geometry.Size(barWidth, h),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f, 3f)
                )
            }
        }
    }
}

@Composable
private fun StatusMiniCard(label: String, value: String, accent: Color, modifier: Modifier = Modifier) {
    GlassPanel(modifier = modifier, innerPadding = 14.dp) {
        Text(text = label, color = TextDim, fontSize = 12.sp)
        Text(text = value, color = accent, fontSize = 18.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun PresetMetric(label: String, value: Float) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0x14FFFFFF))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = label, color = TextDim, fontSize = 10.sp)
        Text(text = value.roundToInt().toString(), color = TextMain, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun GlassPanel(
    modifier: Modifier = Modifier,
    innerPadding: androidx.compose.ui.unit.Dp = 18.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(28.dp))
            .background(
                brush = Brush.verticalGradient(
                    listOf(Color(0x55FFFFFF), GlassDeep)
                )
            )
            .border(1.dp, PanelStroke, RoundedCornerShape(28.dp))
            .padding(innerPadding)
    ) {
        content()
    }
}

@Composable
private fun BottomNav(current: MainTab, onTabChange: (MainTab) -> Unit) {
    val items = listOf(
        Triple(MainTab.Home, Icons.Rounded.Home, "Home"),
        Triple(MainTab.Player, Icons.Rounded.MusicNote, "Player"),
        Triple(MainTab.Dsp, Icons.Rounded.Equalizer, "DSP"),
        Triple(MainTab.Library, Icons.Rounded.Folder, "Library"),
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 12.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(Color(0xD8FFF8FF))
            .border(1.dp, Color(0xB5FFFFFF), RoundedCornerShape(28.dp))
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        items.forEach { (tab, icon, label) ->
            val active = tab == current
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .clickable { onTabChange(tab) }
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Icon(icon, contentDescription = label, tint = if (active) Peach else TextSoft, modifier = Modifier.size(24.dp))
                Text(text = label, color = if (active) Peach else TextSoft, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

private fun AudioLibraryTrack.toDemoTrack(): DemoTrack =
    DemoTrack(
        title = title,
        artist = artist,
        format = format,
        durationSec = durationSec,
        toneHz = fallbackToneFor(title),
        audioUri = uri,
        sourceLabel = when (source) {
            AudioLibrarySource.DeviceScan -> "Device"
            AudioLibrarySource.UserImport -> "Imported"
        },
    )

private fun mergeAudioTracks(current: List<DemoTrack>, incoming: List<DemoTrack>): List<DemoTrack> {
    if (incoming.isEmpty()) return current
    return (incoming + current)
        .distinctBy { it.audioUri?.toString() ?: "${it.title}:${it.artist}:${it.durationSec}" }
        .take(350)
}

private fun fallbackToneFor(title: String): Double {
    val positiveHash = title.hashCode() and 0x7fffffff
    return 96.0 + (positiveHash % 540)
}

private fun formatTime(totalSec: Int): String {
    val safe = totalSec.coerceAtLeast(0)
    val min = safe / 60
    val sec = safe % 60
    return String.format(Locale.US, "%02d:%02d", min, sec)
}
