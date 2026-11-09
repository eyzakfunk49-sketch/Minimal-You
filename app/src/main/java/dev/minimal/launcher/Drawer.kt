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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
// ---------- Drawer ----------

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun Drawer(
    p: Palette,
    apps: List<App>,
    pinned: Set<String>,
    fromRight: Boolean,
    active: Boolean,
    progress: () -> Float,
    onLaunch: (App) -> Unit,
    onTogglePin: (App) -> Unit,
    onClose: () -> Unit,
    onDrag: (Float) -> Unit,
    onEnd: (Float) -> Unit,
    /** Most recently opened apps, shown above the list while not searching. */
    recent: List<App> = emptyList(),
) {
    val curDragDrawer by rememberUpdatedState(onDrag)
    val rt = remember { RootTracker() }
    val curEndDrawer by rememberUpdatedState(onEnd)
    val asApps by remember { derivedStateOf { progress() > 0.55f } }
    var query by remember { mutableStateOf("") }
    val shown = remember(apps, query) {
        val q = query.trim()
        if (q.isEmpty()) apps else apps.filter { it.label.contains(q, ignoreCase = true) }
    }
    val groups = remember(shown) {
        shown.groupBy { app ->
            app.label.firstOrNull()?.uppercaseChar()?.takeIf { it.isLetter() } ?: '#'
        }.toSortedMap()
    }
    val listState = rememberLazyListState()
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    // Put the keyboard away whenever the panel is leaving, so it never stays up over Home.
    LaunchedEffect(active) {
        if (!active) {
            keyboard?.hide()
            focusManager.clearFocus()
            query = ""
            listState.scrollToItem(0)
        }
    }
    val haptic = LocalHapticFeedback.current
    var activeLetter by remember { mutableStateOf<Char?>(null) }
    // The last letter picked on the strip, kept while the dimming fades back out.
    var litLetter by remember { mutableStateOf<Char?>(null) }
    val focus = animateFloatAsState(if (activeLetter != null) 1f else 0f, tw(160), label = "letterFocus")
    val letters = remember(groups) { groups.keys.toList() }
    val headerIndex = remember(groups) {
        val map = mutableMapOf<Char, Int>()
        var i = 0
        groups.forEach { (letter, list) -> map[letter] = i; i += 1 + list.size }
        map
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(p.bg)
            .onGloballyPositioned { rt.coords = it }
            // Drag the page back the way it came (down, or right when it comes from the right). It follows
            // the finger. Starts only when the list is at its top (bottom mode), so scrolling is unaffected.
            .pointerInput(listState, letters.size, fromRight) {
                val stripPx = 60.dp.toPx()
                val slop = 3.dp.toPx()
                awaitEachGesture {
                    val first = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    val startedAtTop = !listState.canScrollBackward
                    val inStrip = letters.size > 1 && first.position.x > size.width - stripPx
                    val tracker = androidx.compose.ui.input.pointer.util.VelocityTracker()
                    val r0 = rt.toRoot(first.position)
                    tracker.addPosition(first.uptimeMillis, r0)
                    var dx = 0f
                    var dy = 0f
                    var pulling = false
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == first.id } ?: break
                        if (!change.pressed) break
                        val r = rt.toRoot(change.position)
                        tracker.addPosition(change.uptimeMillis, r)
                        dx = r.x - r0.x
                        dy = r.y - r0.y
                        if (!pulling && !inStrip) {
                            pulling = if (fromRight) dx > slop && dx > 0.8f * abs(dy)
                            else startedAtTop && dy > slop && dy > 0.9f * abs(dx)
                            if (pulling) { keyboard?.hide(); focusManager.clearFocus() }
                        }
                        if (pulling) {
                            change.consume()
                            curDragDrawer(1f - (if (fromRight) dx / size.width else dy / size.height))
                        }
                    }
                    if (pulling) {
                        val v = tracker.calculateVelocity()
                        curEndDrawer(if (fromRight) v.x else v.y)
                    }
                }
            }
            .windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout))
            .imePadding()
            .padding(horizontal = 32.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        if (recent.isNotEmpty() && query.isBlank() && asApps) {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 4.dp).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                recent.forEach { app ->
                    BasicText(
                        if (LocalLowercase.current) app.label.lowercase() else app.label,
                        maxLines = 1,
                        style = ts(p.ink, 16.sp, FontWeight.Normal),
                        modifier = Modifier
                            .background(p.pill, RoundedCornerShape(20.dp))
                            .softClick(role = Role.Button) { onLaunch(app) }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    )
                }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(
                Modifier.fillMaxSize().fadeEdges(listState),
                state = listState,
                contentPadding = PaddingValues(end = 28.dp, bottom = 8.dp),
            ) {
                groups.forEach { (letter, list) ->
                    stickyHeader(key = "h$letter", contentType = "header") {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .background(p.bg)
                                .padding(top = 12.dp, bottom = 4.dp)
                                .graphicsLayer { alpha = if (litLetter == letter) 1f else 1f - 0.65f * focus.value },
                        ) {
                            BasicText(letter.toString(), style = ts(p.accent, 13.sp, FontWeight.Medium, 1.sp))
                        }
                    }
                    items(list, key = { it.key }, contentType = { "app" }) { app ->
                        val isPinned = app.key in pinned
                        AppRow(
                            app, 22.sp, p.ink,
                            onClick = { onLaunch(app) },
                            onLongClick = { onTogglePin(app) },
                            weight = if (activeLetter == letter) FontWeight.SemiBold else if (isPinned) FontWeight.Medium else FontWeight.Light,
                            marker = if (isPinned) p.accent else null,
                            fullWidth = true,
                            // Remembered so scrolling doesn't rebuild this for every row; it still reads the live values.
                            modifier = remember(letter) {
                                Modifier.graphicsLayer { alpha = if (litLetter == letter) 1f else 1f - 0.65f * focus.value }
                            },
                        )
                    }
                }
            }
            // Quick letter strip: tap or slide to jump.
            if (letters.size > 1) {
                Column(
                    Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .width(28.dp)
                        .pointerInput(letters) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull() ?: continue
                                    if (change.pressed) {
                                        val frac = (change.position.y / size.height).coerceIn(0f, 0.999f)
                                        val letter = letters[(frac * letters.size).toInt()]
                                        if (letter != activeLetter) {
                                            activeLetter = letter
                                            litLetter = letter
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            headerIndex[letter]?.let { listState.requestScrollToItem(it) }
                                        }
                                        change.consume()
                                    } else {
                                        activeLetter = null
                                    }
                                }
                            }
                        },
                    verticalArrangement = Arrangement.SpaceEvenly,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    letters.forEach { letter ->
                        BasicText(
                            letter.toString(),
                            style = ts(if (letter == activeLetter) p.ink else p.accent, 11.sp, FontWeight.Medium),
                        )
                    }
                }
                val shownLetter = activeLetter
                if (shownLetter != null) {
                    Box(
                        Modifier.align(Alignment.Center).size(88.dp).background(p.pill, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        BasicText(shownLetter.toString(), style = ts(p.ink, 44.sp, FontWeight.Light))
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Pill(p) {
            SearchIcon(p.accent)
            Spacer(Modifier.width(12.dp))
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                textStyle = ts(p.ink, 16.sp, FontWeight.Normal),
                cursorBrush = SolidColor(p.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { shown.firstOrNull()?.let(onLaunch) }),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (query.isEmpty()) BasicText(if (asApps) "Search apps" else "Search anything", style = ts(p.muted, 16.sp, FontWeight.Normal))
                        inner()
                    }
                },
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}


