package dev.minimal.launcher

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.text.format.DateFormat
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.animateColorAsState
import androidx.compose.ui.composed
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.layout
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlin.math.abs
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import androidx.compose.foundation.gestures.ScrollableDefaults
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.NotificationManagerCompat
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.net.URLEncoder
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.SystemClock
import android.content.ComponentName
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.ui.semantics.role

// ---------- Quick media player ----------

/** What the player shows. Fed from whichever media session is active. */
class MediaUi {
    private var active by mutableStateOf(false)
    var title by mutableStateOf("")
    var artist by mutableStateOf("")
    var art by mutableStateOf<android.graphics.Bitmap?>(null)
    var playing by mutableStateOf(false)
    var duration by mutableStateOf(0L)
    var canSeek by mutableStateOf(false)
    var canNext by mutableStateOf(false)
    var canPrev by mutableStateOf(false)
    /** Bumped on every playback update so the progress bar re-syncs, for example after a seek while paused. */
    var stamp by mutableStateOf(0)
    private var basePosition = 0L
    private var baseTime = 0L
    private var speed = 1f
    var controller: MediaController? = null

    val visible: Boolean get() = active && (title.isNotEmpty() || artist.isNotEmpty())

    fun position(): Long {
        val raw = if (playing) basePosition + ((SystemClock.elapsedRealtime() - baseTime) * speed).toLong() else basePosition
        return if (duration > 0) raw.coerceIn(0L, duration) else raw.coerceAtLeast(0L)
    }

    fun clear() {
        active = false; playing = false; title = ""; artist = ""; art = null; duration = 0L
        canSeek = false; canNext = false; canPrev = false; controller = null
    }

    fun updateState(s: PlaybackState?) {
        val st = s?.state ?: PlaybackState.STATE_NONE
        active = st == PlaybackState.STATE_PLAYING || st == PlaybackState.STATE_PAUSED || st == PlaybackState.STATE_BUFFERING
        playing = st == PlaybackState.STATE_PLAYING || st == PlaybackState.STATE_BUFFERING
        basePosition = (s?.position ?: 0L).coerceAtLeast(0L)
        baseTime = s?.lastPositionUpdateTime ?: SystemClock.elapsedRealtime()
        speed = s?.playbackSpeed ?: 1f
        val a = s?.actions ?: 0L
        canSeek = a and PlaybackState.ACTION_SEEK_TO != 0L
        canNext = a and PlaybackState.ACTION_SKIP_TO_NEXT != 0L
        canPrev = a and PlaybackState.ACTION_SKIP_TO_PREVIOUS != 0L
        stamp++
    }

    fun updateMeta(m: MediaMetadata?) {
        title = m?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE) ?: m?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: ""
        artist = m?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: m?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: m?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE) ?: ""
        duration = (m?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L).coerceAtLeast(0L)
        art = m?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: m?.getBitmap(MediaMetadata.METADATA_KEY_ART)
            ?: m?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        stamp++
    }
}

/**
 * Follows the active media session. Needs notification access (the same one the notification dots use);
 * without it nothing shows. Re-checked each time you come back to Home, so granting it takes effect right away.
 */
@Composable
internal fun rememberMedia(enabled: Boolean, resumes: Int): MediaUi {
    val ctx = LocalContext.current
    val ui = remember { MediaUi() }
    DisposableEffect(enabled, resumes) {
        if (!enabled) {
            ui.clear()
            return@DisposableEffect onDispose { }
        }
        val msm = ctx.getSystemService(MediaSessionManager::class.java)
        val component = ComponentName(ctx, NotificationDotService::class.java)
        var current: MediaController? = null
        lateinit var callback: MediaController.Callback
        fun pick(list: List<MediaController>?) {
            val c = list?.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
                ?: list?.firstOrNull {
                    val s = it.playbackState?.state
                    s == PlaybackState.STATE_PAUSED || s == PlaybackState.STATE_BUFFERING
                }
            current?.unregisterCallback(callback)
            current = c
            ui.controller = c
            if (c == null) {
                ui.clear()
            } else {
                c.registerCallback(callback)
                ui.updateMeta(c.metadata)
                ui.updateState(c.playbackState)
            }
        }
        callback = object : MediaController.Callback() {
            override fun onPlaybackStateChanged(state: PlaybackState?) {
                // A session that stopped may have another one behind it; look again.
                if (state == null || state.state == PlaybackState.STATE_STOPPED || state.state == PlaybackState.STATE_NONE) {
                    try { pick(msm.getActiveSessions(component)) } catch (_: Exception) { ui.clear() }
                } else ui.updateState(state)
            }
            override fun onMetadataChanged(metadata: MediaMetadata?) { ui.updateMeta(metadata) }
            override fun onSessionDestroyed() {
                try { pick(msm.getActiveSessions(component)) } catch (_: Exception) { ui.clear() }
            }
        }
        val listener = MediaSessionManager.OnActiveSessionsChangedListener { pick(it) }
        try {
            msm.addOnActiveSessionsChangedListener(listener, component)
            pick(msm.getActiveSessions(component))
        } catch (_: SecurityException) {
            ui.clear() // notification access isn't granted yet
        } catch (_: Exception) {
            ui.clear()
        }
        onDispose {
            try { msm.removeOnActiveSessionsChangedListener(listener) } catch (_: Exception) { }
            current?.unregisterCallback(callback)
        }
    }
    return ui
}

@Composable
internal fun MediaPlayerCard(p: Palette, ui: MediaUi, modifier: Modifier = Modifier) {
    val transport = ui.controller?.transportControls
    var pos by remember { mutableStateOf(0L) }
    var dragFrac by remember { mutableStateOf<Float?>(null) }
    LaunchedEffect(ui.stamp, ui.playing) {
        pos = ui.position()
        while (ui.playing) {
            delay(250)
            if (dragFrac == null) pos = ui.position()
        }
    }
    val frac = dragFrac ?: if (ui.duration > 0) (pos.toFloat() / ui.duration).coerceIn(0f, 1f) else 0f

    Column(
        modifier
            .fillMaxWidth()
            .background(p.pill, RoundedCornerShape(32.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(52.dp).clip(RoundedCornerShape(18.dp)).background(p.accent.copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center,
            ) {
                val bmp = ui.art
                if (bmp != null) {
                    val image = remember(bmp) { bmp.asImageBitmap() }
                    androidx.compose.foundation.Image(
                        image, null, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    )
                } else {
                    BasicText("\u266A", style = ts(p.accent, 24.sp, FontWeight.Normal))
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                BasicText(
                    ui.title.ifEmpty { "Playing" },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = ts(p.ink, 18.sp, FontWeight.SemiBold),
                )
                if (ui.artist.isNotEmpty()) {
                    BasicText(
                        ui.artist,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = ts(p.muted, 14.sp, FontWeight.Normal),
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            PlayPauseButton(p, ui.playing) { if (ui.playing) transport?.pause() else transport?.play() }
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            SkipButton(p, next = false, enabled = ui.canPrev) { transport?.skipToPrevious() }
            WavyProgress(
                p, frac, ui.playing, enabled = ui.canSeek && ui.duration > 0,
                onPreview = { dragFrac = it },
                onSeek = { f ->
                    dragFrac = null
                    val t = (f * ui.duration).toLong()
                    pos = t
                    transport?.seekTo(t)
                },
                modifier = Modifier.weight(1f).height(40.dp),
            )
            SkipButton(p, next = true, enabled = ui.canNext) { transport?.skipToNext() }
        }
    }
}

/** The main button: a rounded square while playing that relaxes into a circle when paused, and squishes when pressed. */
@Composable
private fun PlayPauseButton(p: Palette, playing: Boolean, onClick: () -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) 0.9f else 1f, sp(0.55f, 900f), label = "playScale")
    val corner by animateDpAsState(if (playing) 18.dp else 28.dp, tw(280, easing = EmphasizedDecel), label = "playCorner")
    val t by animateFloatAsState(if (playing) 1f else 0f, tw(200), label = "playIcon")
    Box(
        Modifier
            .size(56.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .background(p.accent, RoundedCornerShape(corner))
            .pointerInput(Unit) {
                detectTapGestures(onPress = { pressed = true; tryAwaitRelease(); pressed = false }, onTap = { onClick() })
            }
            .semantics { role = Role.Button; contentDescription = if (playing) "Pause" else "Play" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(26.dp)) {
            val w = size.width
            val h = size.height
            // Play triangle fades out as the pause bars fade in.
            if (t < 1f) {
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(w * 0.22f, h * 0.10f); lineTo(w * 0.92f, h * 0.5f); lineTo(w * 0.22f, h * 0.90f); close()
                }
                drawPath(path, p.bg.copy(alpha = 1f - t))
            }
            if (t > 0f) {
                val bar = w * 0.26f
                drawRoundRect(p.bg.copy(alpha = t), Offset(w * 0.12f, h * 0.08f), Size(bar, h * 0.84f), CornerRadius(bar / 2))
                drawRoundRect(p.bg.copy(alpha = t), Offset(w * 0.62f, h * 0.08f), Size(bar, h * 0.84f), CornerRadius(bar / 2))
            }
        }
    }
}

@Composable
private fun SkipButton(p: Palette, next: Boolean, enabled: Boolean, onClick: () -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) 0.88f else 1f, sp(0.55f, 900f), label = "skipScale")
    Box(
        Modifier
            .size(44.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale; alpha = if (enabled) 1f else 0.35f }
            .background(p.ink.copy(alpha = 0.09f), CircleShape)
            .pointerInput(enabled) {
                if (enabled) detectTapGestures(onPress = { pressed = true; tryAwaitRelease(); pressed = false }, onTap = { onClick() })
            }
            .semantics { role = Role.Button; contentDescription = if (next) "Next" else "Previous" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(18.dp)) {
            val w = size.width
            val h = size.height
            val tri = androidx.compose.ui.graphics.Path().apply {
                if (next) { moveTo(0f, h * 0.1f); lineTo(w * 0.72f, h * 0.5f); lineTo(0f, h * 0.9f) }
                else { moveTo(w, h * 0.1f); lineTo(w * 0.28f, h * 0.5f); lineTo(w, h * 0.9f) }
                close()
            }
            drawPath(tri, p.ink)
            val bar = w * 0.16f
            drawRoundRect(p.ink, Offset(if (next) w - bar else 0f, h * 0.1f), Size(bar, h * 0.8f), CornerRadius(bar / 2))
        }
    }
}

/**
 * Expressive progress bar: the played part is a gentle wave that flows while music plays and settles flat when
 * paused, with a handle that narrows as you hold it. Tap to jump, or drag to scrub; it seeks when you let go.
 */
@Composable
private fun WavyProgress(
    p: Palette,
    fraction: Float,
    playing: Boolean,
    enabled: Boolean,
    onPreview: (Float?) -> Unit,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var dragging by remember { mutableStateOf(false) }
    val running = playing && animationsOn
    val amp by animateFloatAsState(if (running && !dragging) 1f else 0f, tw(400), label = "waveAmp")
    val phase = remember { Animatable(0f) }
    LaunchedEffect(running) {
        if (running) while (true) phase.animateTo(phase.value + 6.2831855f, tween(1600, easing = LinearEasing))
    }
    val handleW by animateDpAsState(if (dragging) 3.dp else 5.dp, tw(160), label = "handleW")
    Canvas(
        modifier
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                val inset = 10.dp.toPx()
                detectTapGestures { off -> onSeek(((off.x - inset) / (size.width - 2 * inset)).coerceIn(0f, 1f)) }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                val inset = 10.dp.toPx()
                var last = 0f
                detectHorizontalDragGestures(
                    onDragStart = { off ->
                        dragging = true
                        last = ((off.x - inset) / (size.width - 2 * inset)).coerceIn(0f, 1f)
                        onPreview(last)
                    },
                    onDragEnd = { dragging = false; onSeek(last) },
                    onDragCancel = { dragging = false; onPreview(null) },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        last = ((change.position.x - inset) / (size.width - 2 * inset)).coerceIn(0f, 1f)
                        onPreview(last)
                    },
                )
            },
    ) {
        val stroke = 4.dp.toPx()
        val cy = size.height / 2f
        val inset = 10.dp.toPx()
        val usable = size.width - 2 * inset
        val x = inset + fraction.coerceIn(0f, 1f) * usable
        val gap = 7.dp.toPx()
        // What's left to play: a plain line.
        if (x + gap < size.width - inset) {
            drawLine(p.muted.copy(alpha = 0.35f), Offset(x + gap, cy), Offset(size.width - inset, cy), stroke, StrokeCap.Round)
        }
        // What's been played: the wave.
        val end = x - gap
        if (end > inset) {
            val a = 3.dp.toPx() * amp
            val len = 26.dp.toPx()
            val path = androidx.compose.ui.graphics.Path()
            var px = inset
            path.moveTo(px, cy + a * kotlin.math.sin(-phase.value.toDouble()).toFloat())
            while (px < end) {
                px = (px + 3f).coerceAtMost(end)
                path.lineTo(px, cy + a * kotlin.math.sin((2 * Math.PI * (px - inset) / len) - phase.value).toFloat())
            }
            drawPath(path, p.accent, style = Stroke(stroke, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
        } else if (end > inset - stroke) {
            drawLine(p.accent, Offset(inset, cy), Offset(inset + 0.1f, cy), stroke, StrokeCap.Round)
        }
        // The handle.
        val hw = handleW.toPx()
        val hh = 24.dp.toPx()
        drawRoundRect(p.accent, Offset(x - hw / 2f, cy - hh / 2f), Size(hw, hh), CornerRadius(hw / 2f))
    }
}
