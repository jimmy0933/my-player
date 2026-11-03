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
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.PauseCircleFilled
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.consume
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
    position: Int,
    total: Int,
    hasPreviousVideo: Boolean,
    hasNextVideo: Boolean,
    onBack: () -> Unit,
    onChange: (Int) -> Unit,
    onPreviousVideo: () -> Unit,
    onNextVideo: () -> Unit,
    onDelete: () -> Unit,
    onRepairMp4: () -> Unit,
    onPlaybackSettings: () -> Unit,
    progressScale: Float,
    progressWidth: Float
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
    LaunchedEffect(media.key, playing, interaction) {
        if (media.isVideo && playing) {
            delay(2_000)
            controlsVisible = false
        } else controlsVisible = true
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
                    player = playbackEngine
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
                    onChange(if (dy < 0) 1 else -1)
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
        if (controlsVisible) {
            Row(
                Modifier.fillMaxWidth().align(Alignment.TopCenter)
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .82f), Color.Transparent)))
                    .padding(horizontal = 8.dp, vertical = if (isLandscape) 2.dp else 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { showControls(); onBack() }) {
                    Icon(Icons.Default.ArrowBack, "返回相簿", tint = Color.White)
                }
                Column(Modifier.weight(1f)) {
                    Text(media.name, color = Color.White, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                    if (!isLandscape)
                        Text("${position + 1} / $total · ${if (media.isVideo) "影片" else "照片"}",
                            color = Muted, style = MaterialTheme.typography.labelSmall)
                }
                if (zoom > 1.01f) {
                    IconButton(onClick = { zoom = 1f; translation = Offset.Zero; showControls() }) {
                        Icon(Icons.Default.ZoomOutMap, "重設縮放", tint = Color.White)
                    }
                }
                IconButton(onClick = { showControls(); onPlaybackSettings() }) {
                    Icon(Icons.Default.Settings, "播放設定", tint = Color.White)
                }
                if (media.isVideo) {
                    TextButton(onClick = { showControls(); player?.pause(); onRepairMp4() }) {
                        Text("修復快轉", color = Accent, style = MaterialTheme.typography.labelSmall)
                    }
                }
                IconButton(onClick = { player?.pause(); onDelete() }) {
                    Icon(Icons.Default.DeleteOutline, "刪除目前媒體", tint = Color(0xFFFF9DA6))
                }
            }
            Column(
                Modifier.fillMaxWidth().align(Alignment.BottomCenter)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .95f))))
                    .padding(horizontal = if (isLandscape) 12.dp else 18.dp,
                        vertical = if (isLandscape) 4.dp else 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (media.isVideo) {
                    if (isLandscape) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            PlayerTransportButton(hasPreviousVideo, true) { showControls(); onPreviousVideo() }
                            PlayerPlayButton(playing) { togglePlayback() }
                            PlayerTransportButton(hasNextVideo, false) { showControls(); onNextVideo() }
                            Text(PlayerMath.durationLabel(currentMs), color = Color.White,
                                style = MaterialTheme.typography.labelSmall)
                            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                Timeline(fraction, seekToFraction, progressScale, progressWidth)
                            }
                            Text(PlayerMath.durationLabel(duration), color = Color.White,
                                style = MaterialTheme.typography.labelSmall)
                        }
                    } else {
                        Timeline(fraction, seekToFraction, progressScale, progressWidth)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(PlayerMath.durationLabel(currentMs), color = Color.White)
                            Text(PlayerMath.durationLabel(duration), color = Color.White)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            PlayerTransportButton(hasPreviousVideo, true) { showControls(); onPreviousVideo() }
                            Spacer(Modifier.width(22.dp))
                            PlayerPlayButton(playing) { togglePlayback() }
                            Spacer(Modifier.width(22.dp))
                            PlayerTransportButton(hasNextVideo, false) { showControls(); onNextVideo() }
                        }
                        Text("左右滑動快轉／倒退 · 上下滑動切換 · 雙指縮放",
                            color = Muted, style = MaterialTheme.typography.labelSmall)
                    }
                } else if (!isLandscape) {
                    Text("上下滑動切換照片／影片 · 雙指縮放",
                        color = Muted, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun PlayerTransportButton(enabled: Boolean, previous: Boolean, action: () -> Unit) {
    IconButton(onClick = action, enabled = enabled, modifier = Modifier.size(42.dp)) {
        Icon(if (previous) Icons.Default.SkipPrevious else Icons.Default.SkipNext,
            if (previous) "上一部影片" else "下一部影片",
            tint = if (enabled) Color.White else Color.Gray)
    }
}

@Composable
private fun PlayerPlayButton(playing: Boolean, action: () -> Unit) {
    IconButton(onClick = action, modifier = Modifier.size(50.dp)) {
        Icon(if (playing) Icons.Default.PauseCircleFilled else Icons.Default.PlayCircleFilled,
            if (playing) "暫停" else "播放", modifier = Modifier.size(44.dp), tint = Color.White)
    }
}

@Composable
private fun Timeline(value: Float, onChange: (Float) -> Unit, size: Float, width: Float) {
    val clampedSize = PlayerMath.progressScale(size)
    Slider(value = value.coerceIn(0f, 1f), onValueChange = onChange,
        modifier = Modifier.fillMaxWidth(PlayerMath.progressWidth(width))
            .height((30 * clampedSize).dp),
        colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent),
        thumb = {
            Box(Modifier.size((10 * clampedSize).dp).background(Accent, CircleShape))
        },
        track = { state ->
            Box(Modifier.fillMaxWidth().height((3 * clampedSize).dp)
                .background(Color.White.copy(alpha = .25f), CircleShape)) {
                Box(Modifier.fillMaxWidth(state.value.coerceIn(0f, 1f)).fillMaxHeight()
                    .background(Accent, CircleShape))
            }
        }
    )
}

private fun boundedPan(pan: Offset, zoom: Float, width: Float, height: Float): Offset {
    val dx = (zoom - 1f).coerceAtLeast(0f) * width / 2f
    val dy = (zoom - 1f).coerceAtLeast(0f) * height / 2f
    return Offset(pan.x.coerceIn(-dx, dx), pan.y.coerceIn(-dy, dy))
}
