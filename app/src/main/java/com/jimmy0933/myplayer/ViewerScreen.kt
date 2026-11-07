package com.jimmy0933.myplayer

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.hypot

private val Background = Color.Black
private val Accent = Color(0xFF91A9FF)
private val Muted = Color(0xFFC8D3E7)

@Composable
fun ViewerScreen(
    media: LocalMedia,
    onBack: () -> Unit,
    onChange: (Int) -> Boolean,
    onDelete: () -> Unit,
    onRepairMp4: () -> Unit,
    onPlaybackSettings: () -> Unit,
    progressScale: Float,
    progressOpacity: Float,
    isExternalVideo: Boolean = false
) {
    val context = LocalContext.current
    val config = LocalConfiguration.current
    val isLandscape = config.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    val player = remember(media.key) {
        if (media.isVideo) ExoPlayer.Builder(context).build() else null
    }
    DisposableEffect(player, media.key) {
        player?.apply {
            setMediaItem(MediaItem.fromUri(media.uri))
            prepare()
            playWhenReady = true
        }
        onDispose { player?.release() }
    }
    DisposableEffect(player, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) player?.pause()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    BackHandler { onBack() }
    var zoom by remember(media.key) { mutableFloatStateOf(1f) }
    var translation by remember(media.key) { mutableStateOf(Offset.Zero) }
    var currentMs by remember(media.key) { mutableLongStateOf(0L) }
    var playing by remember(media.key) { mutableStateOf(media.isVideo) }
    var controlsVisible by remember(media.key) { mutableStateOf(true) }
    var seekHint by remember(media.key) { mutableStateOf<String?>(null) }
    var switchHint by remember { mutableStateOf<Int?>(null) }
    var previewMenuOpen by remember(media.key) { mutableStateOf(false) }
    var interaction by remember(media.key) { mutableIntStateOf(0) }
    var lastTapTime by remember(media.key) { mutableLongStateOf(-1L) }
    var lastTapPoint by remember(media.key) { mutableStateOf(Offset.Zero) }
    var pendingTap by remember(media.key) { mutableStateOf<Job?>(null) }

    fun showControls() { controlsVisible = true; interaction++ }
    fun togglePlayback() {
        if (player != null) {
            if (player.isPlaying) player.pause() else {
                if (player.duration > 0 && player.currentPosition >= player.duration - 250)
                    player.seekTo(0)
                player.play()
            }
            playing = player.isPlaying
            showControls()
        }
    }
    DisposableEffect(media.key) {
        onDispose { pendingTap?.cancel() }
    }
    LaunchedEffect(player) {
        while (isActive && player != null) {
            currentMs = player.currentPosition.coerceAtLeast(0L)
            playing = player.isPlaying
            delay(250)
        }
    }
    LaunchedEffect(media.key, playing, interaction, previewMenuOpen) {
        if (previewMenuOpen) {
            controlsVisible = true
        } else if (media.isVideo && playing) {
            delay(2_000)
            controlsVisible = false
        } else controlsVisible = true
    }
    LaunchedEffect(switchHint) {
        if (switchHint != null) {
            delay(520)
            switchHint = null
        }
    }
    val duration = (player?.duration?.takeIf { it > 0L } ?: media.durationMs).coerceAtLeast(0L)
    val fraction = if (duration > 0L) (currentMs.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val seekToFraction: (Float) -> Unit = { target ->
        if (duration > 0 && player != null) {
            currentMs = (target.coerceIn(0f, 1f) * duration).toLong()
            player.seekTo(currentMs)
            showControls()
        }
    }

    val playbackEngine = player
    Box(Modifier.fillMaxSize().background(Background), contentAlignment = Alignment.Center) {
        if (media.isVideo) {
            AndroidView(
                factory = { ctx -> PlayerView(ctx).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    this.player = playbackEngine
                } },
                update = { view -> view.player = player },
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    scaleX = zoom; scaleY = zoom
                    translationX = translation.x; translationY = translation.y
                }
            )
        } else {
            AsyncImage(
                model = ImageRequest.Builder(context).data(media.uri).crossfade(false).build(),
                contentDescription = media.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    scaleX = zoom; scaleY = zoom
                    translationX = translation.x; translationY = translation.y
                }
            )
        }
        // One-finger vertical: next/previous media. Horizontal video: direct seeking.
        // Two-finger: zoom and pan. Touching progress controls does not hit this layer.
        Box(Modifier.fillMaxSize().pointerInput(media.key, player) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                if (media.isVideo) showControls()
                var dx = 0f
                var dy = 0f
                var transformed = false
                var origin: Long? = null
                var wasSeek = false
                do {
                    val event = awaitPointerEvent()
                    val pressed = event.changes.filter { it.pressed }
                    when {
                        pressed.size >= 2 -> {
                            transformed = true
                            val a = pressed[0]; val b = pressed[1]
                            val before = (a.previousPosition - b.previousPosition).getDistance()
                            val after = (a.position - b.position).getDistance()
                            if (before > 1f && after > 1f) zoom = (zoom * after / before).coerceIn(1f, 5f)
                            val previousCenter = (a.previousPosition + b.previousPosition) / 2f
                            val newCenter = (a.position + b.position) / 2f
                            translation = boundedPan(translation + newCenter - previousCenter,
                                zoom, size.width.toFloat(), size.height.toFloat())
                            if (zoom <= 1.01f) translation = Offset.Zero
                            event.changes.forEach { it.consume() }
                        }
                        pressed.size == 1 -> {
                            val pointer = pressed[0]
                            val delta = pointer.position - pointer.previousPosition
                            if (!transformed) { dx += delta.x; dy += delta.y }
                            val threshold = with(density) { 18.dp.toPx() }
                            if (media.isVideo && !transformed &&
                                (wasSeek || (abs(dx) > threshold && abs(dx) > abs(dy) * 1.15f))) {
                                wasSeek = true
                                val start = origin ?: (player?.currentPosition ?: 0L)
                                origin = start
                                val target = PlayerMath.seek(start, dx, duration)
                                player?.seekTo(target)
                                currentMs = target
                                seekHint = "${PlayerMath.durationLabel(target)} / ${PlayerMath.durationLabel(duration)}"
                                pointer.consume()
                            } else if (zoom > 1.01f) {
                                val deltaPan = if (media.isVideo) Offset(0f, delta.y) else delta
                                translation = boundedPan(translation + deltaPan,
                                    zoom, size.width.toFloat(), size.height.toFloat())
                                pointer.consume()
                            }
                        }
                    }
                } while (event.changes.any { it.pressed })

                val tapLimit = with(density) { 10.dp.toPx() }
                val verticalLimit = with(density) { 85.dp.toPx() }
                val isTap = !transformed && !wasSeek &&
                    abs(dx) < tapLimit && abs(dy) < tapLimit
                if (!transformed && !wasSeek && zoom <= 1.01f &&
                    abs(dy) > verticalLimit && abs(dy) > abs(dx) * 1.2f) {
                    pendingTap?.cancel(); pendingTap = null; lastTapTime = -1
                    val direction = if (dy < 0) 1 else -1
                    if (onChange(direction)) switchHint = direction
                } else if (isTap) {
                    val isCentral = PlayerMath.centerTap(down.position.x, down.position.y,
                        size.width.toFloat(), size.height.toFloat())
                    fun singleTap() {
                        if (media.isVideo) {
                            if (isCentral) togglePlayback() else showControls()
                        } else controlsVisible = !controlsVisible
                    }
                    val now = down.uptimeMillis
                    val doubleTap = zoom > 1.01f && lastTapTime >= 0 &&
                        now - lastTapTime <= 330 &&
                        (down.position - lastTapPoint).getDistance() <= with(density) { 48.dp.toPx() }
                    if (doubleTap) {
                        pendingTap?.cancel(); pendingTap = null; lastTapTime = -1
                        zoom = 1f; translation = Offset.Zero; showControls()
                    } else if (zoom > 1.01f) {
                        pendingTap?.cancel(); lastTapTime = now; lastTapPoint = down.position
                        pendingTap = scope.launch {
                            delay(310); singleTap(); lastTapTime = -1; pendingTap = null
                        }
                    } else {
                        pendingTap?.cancel(); pendingTap = null
                        lastTapTime = -1; singleTap()
                    }
                } else {
                    pendingTap?.cancel(); pendingTap = null; lastTapTime = -1
                }
                seekHint = null
            }
        })
        seekHint?.let { hint ->
            Text(hint, color = Color.White, fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Center)
                    .background(Color.Black.copy(alpha = .78f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 18.dp, vertical = 10.dp))
        }
        switchHint?.let { direction ->
            Box(Modifier.align(Alignment.Center)
                .background(Color.Black.copy(alpha = .32f), CircleShape)
                .padding(7.dp), contentAlignment = Alignment.Center) {
                Icon(
                    if (direction > 0) Icons.Default.KeyboardArrowUp
                    else Icons.Default.KeyboardArrowDown,
                    if (direction > 0) "已切換下一個" else "已切換上一個",
                    tint = Color.White.copy(alpha = .87f),
                    modifier = Modifier.size(32.dp)
                )
            }
        }
        if (controlsVisible) {
            // The viewer remains edge-to-edge. Only a thin transparent top overlay
            // and (for video) a single playback timeline sit above the media.
            Row(
                Modifier.fillMaxWidth().align(Alignment.TopCenter)
                    .background(Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = .74f), Color.Transparent)))
                    .statusBarsPadding()
                    .padding(start = 2.dp, end = 2.dp, top = 2.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { previewMenuOpen = false; onBack() }) {
                    Icon(Icons.Default.ArrowBack, if (isExternalVideo) "返回原 App" else "返回相簿", tint = Color.White)
                }
                Text(
                    media.name,
                    color = Color.White,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Box {
                    IconButton(onClick = { showControls(); previewMenuOpen = true }) {
                        Icon(Icons.Default.MoreVert, "圖片與影片操作", tint = Color.White)
                    }
                    DropdownMenu(
                        expanded = previewMenuOpen,
                        onDismissRequest = { previewMenuOpen = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("進度條與播放器設定") },
                            onClick = {
                                previewMenuOpen = false
                                showControls()
                                onPlaybackSettings()
                            }
                        )
                        if (media.isVideo && !isExternalVideo) {
                            DropdownMenuItem(
                                text = { Text("建立可快轉的 MP4 副本") },
                                onClick = {
                                    previewMenuOpen = false
                                    player?.pause()
                                    onRepairMp4()
                                }
                            )
                        }
                        if (!isExternalVideo) DropdownMenuItem(
                            text = { Text("刪除目前檔案", color = Color(0xFFFFA4A9)) },
                            onClick = {
                                previewMenuOpen = false
                                player?.pause()
                                onDelete()
                            }
                        )
                    }
                }
            }
        }
        // Keep the timeline visible while the movie is playing. Previously it
        // disappeared with the control overlay after two seconds, making changes
        // in playback settings appear to have no effect.
        if (media.isVideo) {
            Column(
                Modifier.fillMaxWidth().align(Alignment.BottomCenter)
                    .then(
                        if (controlsVisible) Modifier.background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = .70f))
                            )
                        ) else Modifier
                    )
                    .navigationBarsPadding()
                    .padding(horizontal = if (isLandscape) 8.dp else 10.dp)
                    // Increase touch area toward the system navigation bar while
                    // preserving the progress line's center at its old position.
                    .padding(
                        top = 2.dp,
                        bottom = if (isLandscape) 6.dp else 10.dp
                    ),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Time labels occupy their own row so the timeline fills the
                // entire available screen width, independent of their lengths.
                if (controlsVisible) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(PlayerMath.durationLabel(currentMs), color = Color.White,
                            style = MaterialTheme.typography.labelSmall)
                        Text(PlayerMath.durationLabel(duration), color = Color.White,
                            style = MaterialTheme.typography.labelSmall)
                    }
                }
                Timeline(
                    fraction, seekToFraction, progressScale,
                    progressOpacity, controlsVisible,
                    touchHeight = if (isLandscape) 80.dp else 104.dp
                )
            }
        }

    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun Timeline(
    value: Float,
    onChange: (Float) -> Unit,
    size: Float,
    opacity: Float,
    controlsVisible: Boolean = true,
    touchHeight: Dp = 52.dp
) {
    val thickness = PlayerMath.progressThicknessDp(size)
    val alpha = PlayerMath.progressOpacity(opacity)
    val played = Accent.copy(alpha = alpha)
    val unplayed = Color.White.copy(alpha = .28f * alpha)
    // Transparent vertical hit target (up to 104dp) makes tapping or
    // dragging reliable even when the visible line is only 2dp thick.
    // Custom track keeps the saved thickness and opacity independent of touch size.
    Slider(
        value = value.coerceIn(0f, 1f),
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth().height(touchHeight),
        colors = SliderDefaults.colors(
            thumbColor = played,
            activeTrackColor = played,
            inactiveTrackColor = unplayed
        ),
        thumb = {
            if (controlsVisible) {
                Box(
                    Modifier.size((thickness + 8f).dp)
                        .background(played, CircleShape)
                )
            }
        },
        track = { state ->
            Box(
                Modifier.fillMaxWidth().height(thickness.dp)
                    .background(unplayed, CircleShape)
            ) {
                Box(
                    Modifier.fillMaxWidth(state.value.coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .background(played, CircleShape)
                )
            }
        }
    )
}

private fun boundedPan(pan: Offset, zoom: Float, width: Float, height: Float): Offset {
    val dx = (zoom - 1f).coerceAtLeast(0f) * width / 2f
    val dy = (zoom - 1f).coerceAtLeast(0f) * height / 2f
    return Offset(pan.x.coerceIn(-dx, dx), pan.y.coerceIn(-dy, dy))
}
