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
// ---------- Home ----------

internal fun HomeAlign.horizontal(): Alignment.Horizontal = when (this) {
    HomeAlign.LEFT -> Alignment.Start
    HomeAlign.CENTER -> Alignment.CenterHorizontally
    HomeAlign.RIGHT -> Alignment.End
}

/**
 * Thin digits in any font. Where the system has a real thin face ([thin]) it is used. Fonts that have no thin
 * weight (serif, mono, casual, custom files) get the same look by drawing the digits and then shaving a hair off
 * every edge with the background color.
 */
@Composable
private fun ThinClockText(text: String, base: TextStyle, thin: FontFamily?, bg: Color) {
    if (thin != null) {
        BasicText(text, style = base.copy(fontFamily = thin, fontWeight = FontWeight.Thin))
        return
    }
    val px = with(LocalDensity.current) { base.fontSize.toPx() }
    Box {
        BasicText(text, style = base)
        BasicText(
            text,
            style = base.copy(color = bg, drawStyle = Stroke(px * 0.034f, join = androidx.compose.ui.graphics.StrokeJoin.Round)),
        )
    }
}

@Composable
internal fun ClockText(
    p: Palette,
    style: ClockStyle,
    now: LocalDateTime,
    is24: Boolean,
    align: Alignment.Horizontal = Alignment.Start,
    thin: FontFamily? = null,
) {
    fun f(pattern: String): String = now.format(DateTimeFormatter.ofPattern(pattern))
    val hm = f(if (is24) "H:mm" else "h:mm")
    when (style) {
        ClockStyle.LARGE -> BasicText(hm, style = ts(p.ink, 96.sp, FontWeight.Light, (-4).sp, 104.sp))
        ClockStyle.THIN -> ThinClockText(hm, ts(p.ink, 104.sp, FontWeight.Light, (-3).sp, 110.sp), thin, p.bg)
        ClockStyle.MATERIAL_THIN -> ThinClockText(hm, ts(p.accent, 104.sp, FontWeight.Light, (-3).sp, 110.sp), thin, p.bg)
        ClockStyle.BOLD -> BasicText(hm, style = ts(p.ink, 96.sp, FontWeight.Bold, (-4).sp, 104.sp))
        // Hours in the text color, minutes in the accent color.
        ClockStyle.TWO_TONE -> BasicText(
            androidx.compose.ui.text.buildAnnotatedString {
                append(hm.substringBefore(':'))
                val mark = pushStyle(androidx.compose.ui.text.SpanStyle(color = p.accent))
                append(":" + hm.substringAfter(':'))
                pop(mark)
            },
            style = ts(p.ink, 96.sp, FontWeight.Light, (-4).sp, 104.sp),
        )
        ClockStyle.COMPACT -> BasicText(hm, style = ts(p.ink, 48.sp, FontWeight.Normal, (-1).sp, 56.sp))
        // The whole time in the accent color, with a firmer weight so it reads like a Material You clock.
        ClockStyle.MATERIAL -> BasicText(hm, style = ts(p.accent, 100.sp, FontWeight.Medium, (-4).sp, 108.sp))
    }
}

/** Set from Settings; when false every transition snaps instead of animating. */
internal var animationsOn = true

internal fun <T> tw(ms: Int, easing: Easing = androidx.compose.animation.core.FastOutSlowInEasing): FiniteAnimationSpec<T> =
    if (animationsOn) tween(ms, easing = easing) else snap()

internal fun sp(damping: Float, stiffness: Float): FiniteAnimationSpec<Float> =
    if (animationsOn) spring(damping, stiffness) else snap()

internal val EmphasizedDecel = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
internal val EmphasizedAccel = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

// Full screens (Settings, Search): a gentle rise with a soft zoom.
internal fun screenEnter(): EnterTransition = slideInVertically(tw(300, easing = EmphasizedDecel)) { it / 14 } +
    scaleIn(tw(300, easing = EmphasizedDecel), initialScale = 0.95f) + fadeIn(tw(210, easing = EmphasizedDecel))
internal fun screenExit(): ExitTransition = slideOutVertically(tw(190, easing = EmphasizedAccel)) { it / 14 } +
    scaleOut(tw(190, easing = EmphasizedAccel), targetScale = 0.97f) + fadeOut(tw(150, easing = EmphasizedAccel))

// Sub-menus pushed on top of a screen: slide in from the right.
internal fun pushEnter(): EnterTransition = slideInHorizontally(tw(290, easing = EmphasizedDecel)) { it / 3 } + fadeIn(tw(190, easing = EmphasizedDecel))
internal fun pushExit(): ExitTransition = slideOutHorizontally(tw(210, easing = EmphasizedAccel)) { it / 3 } + fadeOut(tw(150, easing = EmphasizedAccel))

@OptIn(ExperimentalFoundationApi::class)
internal fun Modifier.softCombined(onClick: () -> Unit, onLongClick: (() -> Unit)?): Modifier = composed {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    this.graphicsLayer { alpha = if (pressed) 0.5f else 1f }.combinedClickable(
        interactionSource = src, indication = null, role = Role.Button, onClick = onClick, onLongClick = onLongClick,
    )
}

/** A tap target that dims a little while pressed, then eases back. */
internal fun Modifier.softClick(role: Role? = null, onClick: () -> Unit): Modifier = composed {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    this.graphicsLayer { alpha = if (pressed) 0.5f else 1f }.clickable(interactionSource = src, indication = null, role = role, onClick = onClick)
}

@Composable
internal fun Home(
    p: Palette,
    cfg: Config,
    favorites: List<App>,
    weather: Weather?,
    event: Event?,
    onOpenDrawer: () -> Unit,
    onDrawerDrag: (Float) -> Unit,
    onDrawerEnd: (Float) -> Unit,
    drawerProgress: () -> Float,
    onOpenSearch: () -> Unit,
    onSearchDrag: (Float) -> Unit,
    onSearchEnd: (Float) -> Unit,
    searchProgress: () -> Float,
    onPillPos: (Float) -> Unit,
    onAppMenu: (App) -> Unit,
    media: MediaUi,
    onSettings: () -> Unit,
    onSleep: () -> Unit,
    onNotifications: () -> Unit,
    gesturesEnabled: Boolean,
    onLaunch: (App) -> Unit,
    onSlot: (Slot) -> Unit,
    onBuzz: () -> Unit,
    onOpenWidgets: () -> Unit,
    onWidgetsDrag: (Float) -> Unit,
    onWidgetsEnd: (Float) -> Unit,
) {
    val ctx = LocalContext.current
    val now by rememberNow()
    val is24 = when (cfg.hours) {
        HourFormat.SYSTEM -> DateFormat.is24HourFormat(ctx)
        HourFormat.H12 -> false
        HourFormat.H24 -> true
    }
    val curDrawer by rememberUpdatedState(onOpenDrawer)
    val curWDrag by rememberUpdatedState(onWidgetsDrag)
    val curWEnd by rememberUpdatedState(onWidgetsEnd)
    val curSDrag by rememberUpdatedState(onSearchDrag)
    val curSEnd by rememberUpdatedState(onSearchEnd)
    val curBuzz by rememberUpdatedState(onBuzz)
    val rt = remember { RootTracker() }
    var playerBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    val curDrag by rememberUpdatedState(onDrawerDrag)
    val curEnd by rememberUpdatedState(onDrawerEnd)
    val curNotifications by rememberUpdatedState(onNotifications)
    val curWidgets by rememberUpdatedState(onOpenWidgets)
    val showBatteryLine = cfg.hideStatusBar && cfg.showBattery
    val status by rememberStatus(showBatteryLine)

    Column(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { rt.coords = it }
            // Blank space: double tap = sleep, long press = settings.
            .pointerInput(gesturesEnabled) {
                if (gesturesEnabled) {
                    detectTapGestures(onDoubleTap = { onSleep() }, onLongPress = { onSettings() })
                }
            }
            // One detector for all swipes so they can't steal from each other:
            // down = notifications, up = app list, right = widgets page (if enabled).
            // It watches without consuming and fires as soon as the swipe is clearly intentional.
            .pointerInput(gesturesEnabled, cfg.widgetsOn, cfg.drawerRight, cfg.swipeUpSearch) {
                if (!gesturesEnabled) return@pointerInput
                awaitEachGesture {
                    val first = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    val startTime = first.uptimeMillis
                    val tracker = androidx.compose.ui.input.pointer.util.VelocityTracker()
                    val r0 = rt.toRoot(first.position)
                    // Touches that start on the media player belong to the player (seek bar, buttons), not to Home's swipes.
                    val pb = playerBounds
                    if (pb != null && cfg.mediaPlayer && media.visible && pb.contains(r0)) return@awaitEachGesture
                    tracker.addPosition(first.uptimeMillis, r0)
                    var dx = 0f
                    var dy = 0f
                    var held = false
                    val slop = 3.dp.toPx()
                    var axis = 0 // 0 = undecided, 1 = pulling the app list, 2 = pulling the widgets page in, 3 = pulling search up
                    var swallow = false
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == first.id } ?: break
                        if (!change.pressed) break
                        val r = rt.toRoot(change.position)
                        tracker.addPosition(change.uptimeMillis, r)
                        dx = r.x - r0.x
                        dy = r.y - r0.y
                        if (axis != 0 || swallow) {
                            change.consume() // the rest of this gesture belongs to the swipe, not the app under it
                            if (axis == 1) curDrag(if (cfg.drawerRight) -dx / size.width.toFloat() else -dy / size.height.toFloat())
                            if (axis == 2) curWDrag(dx / size.width.toFloat())
                            if (axis == 3) curSDrag(-dy / size.height.toFloat())
                            continue
                        }
                        // A press-and-hold is a long press (Settings), not a swipe.
                        if (change.uptimeMillis - startTime >= 450L && abs(dx) + abs(dy) < 24f) held = true
                        if (held) continue
                        if (!cfg.drawerRight && -dy > slop && -dy > 0.7f * abs(dx)) {
                            axis = 1
                            change.consume()
                            curBuzz()
                            curDrag(-dy / size.height.toFloat())
                        } else if (cfg.drawerRight && cfg.swipeUpSearch && -dy > slop && -dy > 0.9f * abs(dx)) {
                            axis = 3
                            change.consume()
                            curBuzz()
                            curSDrag(-dy / size.height.toFloat())
                        } else if (cfg.drawerRight && -dx > slop && -dx > 0.7f * abs(dy)) {
                            axis = 1
                            change.consume()
                            curBuzz()
                            curDrag(-dx / size.width.toFloat())
                        } else if (cfg.widgetsOn && dx > slop && dx > 0.7f * abs(dy)) {
                            axis = 2
                            change.consume()
                            curBuzz()
                            curWDrag(dx / size.width.toFloat())
                        } else if (dy > slop * 4f && dy > abs(dx)) {
                            change.consume()
                            curBuzz()
                            curNotifications()
                            swallow = true
                        }
                    }
                    if (axis == 1) curEnd(if (cfg.drawerRight) tracker.calculateVelocity().x else tracker.calculateVelocity().y)
                    if (axis == 2) curWEnd(tracker.calculateVelocity().x)
                    if (axis == 3) curSEnd(tracker.calculateVelocity().y)
                }
            }
            .windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout))
            .padding(horizontal = 32.dp),
        // Everything on Home lines up to the side chosen in Settings.
        horizontalAlignment = cfg.homeAlign.horizontal(),
    ) {
        Spacer(Modifier.height(64.dp))
        Column(Modifier.fillMaxWidth(), horizontalAlignment = cfg.homeAlign.horizontal()) {
            val datePattern = cfg.date.pattern
            if (datePattern != null || event != null) {
                // "Date | Next event" on one line.
                Row(Modifier.heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (datePattern != null) {
                        BasicText(
                            now.format(DateTimeFormatter.ofPattern(datePattern)),
                            maxLines = 1,
                            style = ts(p.muted, 15.sp, FontWeight.Normal),
                            modifier = Modifier.softClick(role = Role.Button) { onSlot(Slot.CALENDAR) },
                        )
                    }
                    if (datePattern != null && event != null) {
                        BasicText("  |  ", style = ts(p.muted.copy(alpha = 0.6f), 15.sp, FontWeight.Normal))
                    }
                    if (event != null) {
                        BasicText(
                            formatEvent(event, is24),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = ts(p.muted, 15.sp, FontWeight.Normal),
                            modifier = Modifier.weight(1f, fill = false).softClick(role = Role.Button) { onSlot(Slot.CALENDAR) },
                        )
                    }
                }
            }
            if (cfg.showClock) {
                Box(Modifier.softClick(role = Role.Button) { onSlot(Slot.CLOCK) }) {
                    ClockText(
                        p, cfg.clock, now, is24, cfg.homeAlign.horizontal(),
                        // Only swapped in for the built-in sans fonts; a serif, mono or custom font keeps its own look.
                        thin = if (cfg.font == AppFont.SANS || cfg.font == AppFont.CONDENSED) ThinFamily else null,
                    )
                }
            }
            if (weather != null || showBatteryLine) {
                Row(
                    Modifier.heightIn(min = 40.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (weather != null) {
                        val line = listOf("${weather.temp}°", describe(weather.code))
                            .filter { it.isNotEmpty() }.joinToString(" · ")
                        BasicText(
                            line,
                            style = ts(p.accent, 15.sp, FontWeight.Medium),
                            modifier = Modifier
                                .heightIn(min = 40.dp)
                                .softClick(role = Role.Button) { onSlot(Slot.WEATHER) }
                                .wrapContentHeight(Alignment.CenterVertically),
                        )
                    }
                    if (showBatteryLine) {
                        if (weather != null) {
                            BasicText("  \u00B7  ", style = ts(p.muted, 15.sp, FontWeight.Normal))
                        }
                        val low = status.battery <= 15 && !status.charging
                        val fill = if (status.charging) p.accent else if (low) Color(0xFFE57373) else p.ink
                        BatteryIcon(status.battery, p.muted, fill, status.charging, p.ink, p.bg)
                        Spacer(Modifier.width(6.dp))
                        BasicText("${status.battery}%", style = ts(p.muted, 15.sp, FontWeight.Normal))
                    }
                }
            }
        }
        // With the clock hidden, the favorites can sit in the middle of the screen instead of at the bottom.
        val centered = cfg.centerApps && !cfg.showClock
        // The favorites live in their own flexible area, so a tall list can never push the search bar off screen.
        BoxWithConstraints(
            Modifier.weight(1f).fillMaxWidth().clipToBounds(),
            contentAlignment = when (cfg.homeAlign) {
                HomeAlign.LEFT -> if (centered) Alignment.CenterStart else Alignment.BottomStart
                HomeAlign.CENTER -> if (centered) Alignment.Center else Alignment.BottomCenter
                HomeAlign.RIGHT -> if (centered) Alignment.CenterEnd else Alignment.BottomEnd
            },
        ) {
            // When the list plus the media player won't fit, the spacing between the apps tightens first.
            // Only if that still isn't enough do the names themselves shrink a little.
            val shown = favorites.take(maxFavorites(cfg))
            val rowDp = 48f
            val tightest = 34f
            val perRow = if (shown.isEmpty()) rowDp else maxHeight.value / shown.size
            val rowH = perRow.coerceIn(tightest, rowDp)
            val fit = (perRow / tightest).coerceIn(0.6f, 1f)
            Column(horizontalAlignment = cfg.homeAlign.horizontal()) {
                shown.forEach {
                    AppRow(
                        it, (30f * fit).sp, p.ink, minHeight = rowH.dp, onClick = { onLaunch(it) },
                        onLongClick = { onBuzz(); onAppMenu(it) }, weight = FontWeight.Normal,
                        marker = if (cfg.notifDots && it.component.packageName in NotifState.active) p.accent else null,
                    )
                }
            }
        }
        androidx.compose.animation.AnimatedVisibility(
            visible = cfg.mediaPlayer && media.visible,
            enter = androidx.compose.animation.fadeIn(tw(220)) + androidx.compose.animation.expandVertically(tw(320, easing = EmphasizedDecel)),
            exit = androidx.compose.animation.fadeOut(tw(140)) + androidx.compose.animation.shrinkVertically(tw(220)),
        ) {
            Column {
                Spacer(Modifier.height(12.dp))
                MediaPlayerCard(p, media, Modifier.onGloballyPositioned { playerBounds = it.boundsInRoot() })
            }
        }
        Spacer(Modifier.height(32.dp))
        Pill(
            p,
            Modifier
                .onGloballyPositioned { onPillPos(it.positionInRoot().y) }
                .graphicsLayer {
                    // Hidden the moment the flying copy takes over during the search transition.
                    alpha = if (searchProgress() > 0.001f) 0f else (1f - drawerProgress() * 4f).coerceIn(0f, 1f)
                }
                .softClick(role = Role.Button, onClick = onOpenSearch),
        ) {
            SearchIcon(p.accent)
            Spacer(Modifier.width(12.dp))
            BasicText("Search anything", style = ts(p.muted, 16.sp, FontWeight.Normal))
        }
        Spacer(Modifier.height(24.dp))
    }
}

// ---------- Home status line (shown when the system status bar is hidden) ----------

@Composable
internal fun rememberStatus(enabled: Boolean): State<SysStatus> {
    val ctx = LocalContext.current
    val status = remember { mutableStateOf(SysStatus()) }
    LaunchedEffect(enabled) {
        if (enabled) {
            while (true) {
                status.value = withContext(Dispatchers.Default) { readStatus(ctx) }
                delay(4_000L)
            }
        }
    }
    return status
}

/** Battery glyph drawn on whole device pixels so edges stay crisp on any density. */
@Composable
internal fun BatteryIcon(percent: Int, outline: Color, fill: Color, charging: Boolean = false, bolt: Color = Color.White, halo: Color = Color.Black) {
    Canvas(Modifier.size(width = 26.dp, height = 13.dp)) {
        fun px(dp: Float) = (dp * density).roundToInt().coerceAtLeast(1).toFloat()
        val w = size.width.roundToInt().toFloat()
        val h = size.height.roundToInt().toFloat()
        val sw = px(1.5f)
        val capW = px(2f)
        val bodyW = w - capW
        val radius = px(3.5f)
        // Outline: stroke centred half a stroke inside the bounds so it never blurs across pixels.
        drawRoundRect(
            color = outline,
            topLeft = Offset(sw / 2f, sw / 2f),
            size = Size(bodyW - sw, h - sw),
            cornerRadius = CornerRadius(radius - sw / 2f),
            style = Stroke(sw),
        )
        // Terminal cap
        val capH = (h * 0.4f).roundToInt().toFloat()
        drawRoundRect(
            color = outline,
            topLeft = Offset(bodyW, ((h - capH) / 2f).roundToInt().toFloat()),
            size = Size(capW, capH),
            cornerRadius = CornerRadius(px(1f)),
        )
        // Charge level
        val gap = px(1.5f)
        val inset = sw + gap
        val maxFill = bodyW - 2 * inset
        val fillW = (maxFill * percent.coerceIn(0, 100) / 100f).roundToInt().toFloat()
        if (fillW > 0f) {
            drawRoundRect(
                color = fill,
                topLeft = Offset(inset, inset),
                size = Size(fillW, h - 2 * inset),
                cornerRadius = CornerRadius(px(1.5f)),
            )
        }
        // Lightning bolt while charging, with a thin halo so it reads over both the filled and empty parts.
        if (charging) {
            val cx = bodyW / 2f
            val cy = h / 2f
            val bh = h * 0.78f
            val bw = bh * 0.55f
            val bp = androidx.compose.ui.graphics.Path().apply {
                moveTo(cx + bw * 0.12f, cy - bh / 2f)
                lineTo(cx - bw / 2f, cy + bh * 0.08f)
                lineTo(cx - bw * 0.04f, cy + bh * 0.08f)
                lineTo(cx - bw * 0.14f, cy + bh / 2f)
                lineTo(cx + bw / 2f, cy - bh * 0.10f)
                lineTo(cx + bw * 0.05f, cy - bh * 0.10f)
                close()
            }
            drawPath(bp, halo, style = Stroke(px(1.6f), join = androidx.compose.ui.graphics.StrokeJoin.Round))
            drawPath(bp, bolt)
        }
    }
}
