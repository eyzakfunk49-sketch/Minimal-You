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
import androidx.compose.ui.layout.onGloballyPositioned
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

/** Home fits 7 favorites, or 9 when the clock is hidden and frees up the space. */
private fun maxFavorites(cfg: Config) = if (cfg.showClock) 7 else 9

class MainActivity : ComponentActivity() {
    // Bumped whenever Home is pressed while we're already open, so overlays can close.
    private var homePress by mutableIntStateOf(0)
    private var resumes by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { LauncherApp(homePress, resumes) }
    }

    override fun onResume() {
        super.onResume()
        resumes++
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        homePress++
    }
}

// ---------- Actions ----------

// Where the last touch landed and the window view, so apps can zoom open from the tapped spot.
private var launchView: android.view.View? = null
private var lastTouch = Offset.Zero
private var launching by mutableStateOf(false)
/** Finger positions in window coordinates, so a page that moves under the finger doesn't distort the drag. */
private class RootTracker {
    var coords: androidx.compose.ui.layout.LayoutCoordinates? = null
    fun toRoot(p: androidx.compose.ui.geometry.Offset): androidx.compose.ui.geometry.Offset {
        val c = coords
        return if (c != null && c.isAttached) c.localToRoot(p) else p
    }
}

private var moveEdgeExclusion: (Float) -> Unit = {}

private fun openApp(ctx: Context, app: App) {
    val intent = Intent(Intent.ACTION_MAIN)
        .addCategory(Intent.CATEGORY_LAUNCHER)
        .setComponent(app.component)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
    val v = launchView
    val options = if (!animationsOn) {
        android.app.ActivityOptions.makeCustomAnimation(ctx, 0, 0).toBundle()
    } else {
        // Our own enter/exit animation: the app scales up and fades in while Home eases back. A custom
        // animation is honoured by the system, whereas reveal/scale-up options are ignored for third-party launchers.
        android.app.ActivityOptions.makeCustomAnimation(ctx, R.anim.app_open_enter, R.anim.app_open_exit).toBundle()
    }
    launching = true
    try { ctx.startActivity(intent, options) } catch (_: Exception) { launching = false }
}

private fun openUri(ctx: Context, uri: Uri, mime: String? = null) {
    val intent = Intent(Intent.ACTION_VIEW)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
    if (mime != null) intent.setDataAndType(uri, mime) else intent.data = uri
    try {
        ctx.startActivity(intent)
    } catch (_: Exception) {
        Toast.makeText(ctx, "No app can open this", Toast.LENGTH_SHORT).show()
    }
}

private fun openUrl(ctx: Context, url: String) = openUri(ctx, Uri.parse(url))

/** Things on Home that open an app of your choice when tapped. */
private enum class Slot(val label: String, val match: String) {
    CLOCK("Clock", "clock"),
    CALENDAR("Date and calendar", "calendar"),
    WEATHER("Weather", "weather"),
}

private fun Config.appFor(slot: Slot): String = when (slot) {
    Slot.CLOCK -> clockApp
    Slot.CALENDAR -> calendarApp
    Slot.WEATHER -> weatherApp
}

private fun Config.withApp(slot: Slot, key: String): Config = when (slot) {
    Slot.CLOCK -> copy(clockApp = key)
    Slot.CALENDAR -> copy(calendarApp = key)
    Slot.WEATHER -> copy(weatherApp = key)
}

/** Lock screen / notification shade need the accessibility service (see GestureService). */
private fun globalAction(ctx: Context, action: Int) {
    val service = GestureService.instance
    if (service != null) {
        service.performGlobalAction(action)
    } else {
        Toast.makeText(ctx, "Turn on MinimalYou gestures in Accessibility settings", Toast.LENGTH_LONG).show()
        ctx.startActivity(
            Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

// ---------- Look (Material You) ----------

data class Palette(val bg: Color, val ink: Color, val muted: Color, val accent: Color, val pill: Color)

/** Colors come from the system's wallpaper-based Material You palette (Android 12+). */
@Composable
private fun palette(): Palette {
    val ctx = LocalContext.current
    fun c(id: Int) = Color(ctx.getColor(id))
    return if (isSystemInDarkTheme()) Palette(
        bg = c(android.R.color.system_neutral1_900),
        ink = c(android.R.color.system_neutral1_10),
        muted = c(android.R.color.system_neutral2_200),
        accent = c(android.R.color.system_accent1_100),
        pill = c(android.R.color.system_neutral1_800),
    ) else Palette(
        bg = c(android.R.color.system_neutral1_50),
        ink = c(android.R.color.system_neutral1_1000),
        muted = c(android.R.color.system_neutral2_700),
        accent = c(android.R.color.system_accent1_700),
        pill = c(android.R.color.system_accent2_100),
    )
}

private val LocalFont = compositionLocalOf<FontFamily> { FontFamily.SansSerif }

@Composable
private fun ts(
    color: Color,
    size: TextUnit,
    weight: FontWeight = FontWeight.Light,
    spacing: TextUnit = TextUnit.Unspecified,
    lineHeight: TextUnit = TextUnit.Unspecified,
) = TextStyle(
    color = color, fontSize = size, fontWeight = weight, fontFamily = LocalFont.current,
    letterSpacing = spacing, lineHeight = lineHeight,
)

// ---------- Root ----------

@Composable
fun LauncherApp(homePress: Int, resumes: Int) {
    val ctx = LocalContext.current
    val p = palette()
    var apps by remember { mutableStateOf(emptyList<App>()) }
    var favKeys by remember { mutableStateOf(readFavs(ctx)) } // null = use defaults
    var cfg by remember { mutableStateOf(loadConfig(ctx)) }
    var weather by remember { mutableStateOf<Weather?>(null) }
    var event by remember { mutableStateOf<Event?>(null) }
    var drawerOpen by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }
    var searchOpen by remember { mutableStateOf(false) }
    var pickSlot by remember { mutableStateOf<Slot?>(null) }
    var widgetsOpen by remember { mutableStateOf(false) }
    // 0 = Home, 1 = app list fully pulled up. Follows the finger while dragging, then settles.
    val drawerP = remember { Animatable(0f) }
    val pageScope = rememberCoroutineScope()
    val drawerSpec = sp(1f, 650f)
    LaunchedEffect(drawerOpen) { drawerP.animateTo(if (drawerOpen) 1f else 0f, drawerSpec) }
    // The search page rises from the bottom exactly like the app list: it follows the finger, then settles.
    val searchP = remember { Animatable(0f) }
    LaunchedEffect(searchOpen) { searchP.animateTo(if (searchOpen) 1f else 0f, drawerSpec) }
    val settingsP = remember { Animatable(0f) }
    LaunchedEffect(settingsOpen) { settingsP.animateTo(if (settingsOpen) 1f else 0f, drawerSpec) }
    val settingsVisible by remember { derivedStateOf { settingsP.value > 0.001f } }
    // The Home search bar glides up into the search page's bar instead of a new one appearing.
    var homePillY by remember { mutableStateOf(-1f) }
    val density = LocalDensity.current
    val searchPillTop = with(density) {
        WindowInsets.systemBars.union(WindowInsets.displayCutout).getTop(density) + 24.dp.toPx()
    }
    val pillFlying by remember { derivedStateOf { searchP.value > 0.001f && searchP.value < 0.985f } }
    val pillSettled by remember { derivedStateOf { searchP.value >= 0.985f } }
    val searchVisible by remember { derivedStateOf { searchP.value > 0.001f } }
    val widgetsP = remember { Animatable(0f) }
    LaunchedEffect(widgetsOpen) { widgetsP.animateTo(if (widgetsOpen) 1f else 0f, drawerSpec) }
    val widgetsVisible by remember { derivedStateOf { widgetsP.value > 0.001f } }
    val drawerVisible by remember { derivedStateOf { drawerP.value > 0.001f } }

    animationsOn = cfg.animations
    val buzzView = LocalView.current
    val buzzWith: (Int) -> Unit = { kind ->
        if (!cfg.noHaptics) buzzView.performHapticFeedback(kind)
    }
    val buzz: () -> Unit = { buzzWith(android.view.HapticFeedbackConstants.GESTURE_START) }
    val visibleApps = remember(apps, cfg.hidden) { apps.filter { it.key !in cfg.hidden } }
    var hiddenOpen by remember { mutableStateOf(false) }

    val updateCfg: (Config) -> Unit = { next ->
        cfg = next
        saveConfig(ctx, next)
    }

    LaunchedEffect(drawerOpen, settingsOpen, searchOpen, homePress) {
        apps = withContext(Dispatchers.Default) { loadApps(ctx) }
    }
    LaunchedEffect(homePress) { drawerOpen = false; settingsOpen = false; searchOpen = false; pickSlot = null; widgetsOpen = false }
    // Hide or show the system status bar.
    val view = LocalView.current
    LaunchedEffect(cfg.hideStatusBar, resumes, homePress) {
        var c: Context? = view.context
        while (c is android.content.ContextWrapper && c !is Activity) c = c.baseContext
        val window = (c as? Activity)?.window ?: return@LaunchedEffect
        val controller = WindowCompat.getInsetsController(window, view)
        if (cfg.hideStatusBar) {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.statusBars())
        } else {
            controller.show(WindowInsetsCompat.Type.statusBars())
        }
    }
    BackHandler(enabled = drawerOpen) { drawerOpen = false }
    BackHandler(enabled = widgetsOpen) { widgetsOpen = false }
    BackHandler(enabled = searchOpen) { searchOpen = false }
    BackHandler(enabled = settingsOpen) { settingsOpen = false }
    BackHandler(enabled = hiddenOpen) { hiddenOpen = false }
    BackHandler(enabled = pickSlot != null) { pickSlot = null } // registered last = handled first

    // Weather: refresh every 30 minutes (every minute until the first success).
    LaunchedEffect(cfg.weatherOn, cfg.weatherLocation, cfg.city, cfg.lat, cfg.lon, cfg.fahrenheit) {
        if (!cfg.weatherOn || (!cfg.weatherLocation && cfg.city.isBlank())) {
            weather = null
            return@LaunchedEffect
        }
        while (true) {
            val pos = if (cfg.weatherLocation) currentLatLon(ctx) else (cfg.lat to cfg.lon)
            val w = if (pos != null) {
                withContext(Dispatchers.IO) { fetchWeather(pos.first, pos.second, cfg.fahrenheit) }
            } else null
            if (w != null) {
                weather = w
                delay(30 * 60 * 1000L)
            } else {
                delay(60 * 1000L)
            }
        }
    }

    // Calendar: next event, refreshed every minute and when returning from Settings.
    LaunchedEffect(cfg.calendarOn, settingsOpen, homePress) {
        if (!cfg.calendarOn) {
            event = null
            return@LaunchedEffect
        }
        while (true) {
            event = withContext(Dispatchers.IO) { queryNextEvent(ctx) }
            delay(60 * 1000L)
        }
    }

    val favorites = remember(apps, favKeys) {
        val keys = favKeys
        if (keys == null) defaultFavorites(apps)
        else { val byKey = apps.associateBy { it.key }; keys.mapNotNull { byKey[it] } }
    }

    val setFavs: (List<String>) -> Unit = { next ->
        favKeys = next
        writeFavs(ctx, next)
    }
    val togglePin: (App) -> Unit = { app ->
        val current = favorites.map { it.key }
        val pinned = app.key in current
        if (!pinned && current.size >= maxFavorites(cfg)) {
            Toast.makeText(ctx, "Home fits ${maxFavorites(cfg)} favorites. Remove one in Settings.", Toast.LENGTH_SHORT).show()
        } else {
            setFavs(if (pinned) current - app.key else current + app.key)
            Toast.makeText(ctx, if (pinned) "Removed from Home" else "Added to Home", Toast.LENGTH_SHORT).show()
        }
    }

    val openSlot: (Slot) -> Unit = { slot ->
        val chosen = apps.firstOrNull { it.key == cfg.appFor(slot) }
        val app = chosen ?: apps.firstOrNull { it.label.contains(slot.match, ignoreCase = true) }
        if (app != null) openApp(ctx, app)
        else Toast.makeText(ctx, "Pick a ${slot.label.lowercase()} app in Settings", Toast.LENGTH_SHORT).show()
    }

    CompositionLocalProvider(LocalFont provides cfg.font.family) {
        val view = LocalView.current
        SideEffect { launchView = view }
        // Android lets an app claim at most ~200dp of each screen edge from the system back gesture.
        // Keep that strip where your thumb last touched, so edge swipes reach the launcher instead of "back".
        val edgePx = with(LocalDensity.current) { 48.dp.roundToPx() }
        val halfPx = with(LocalDensity.current) { 100.dp.roundToPx() }
        // Only on Home: inside menus the system back gesture has to work, so give the edges back.
        val exclusionOn = !settingsOpen && !searchOpen && !drawerOpen && !widgetsOpen && pickSlot == null && !hiddenOpen
        moveEdgeExclusion = { y ->
            val w = view.width
            val h = view.height
            if (!exclusionOn) {
                view.systemGestureExclusionRects = emptyList()
            } else if (w > 0 && h > 0) {
                val top = (y.toInt() - halfPx).coerceIn(0, (h - 2 * halfPx).coerceAtLeast(0))
                view.systemGestureExclusionRects = listOf(
                    android.graphics.Rect(0, top, edgePx, top + 2 * halfPx),
                    android.graphics.Rect(w - edgePx, top, w, top + 2 * halfPx),
                )
            }
        }
        DisposableEffect(view) {
            view.post { moveEdgeExclusion(view.height * 0.65f) }
            onDispose { view.systemGestureExclusionRects = emptyList() }
        }
        LaunchedEffect(exclusionOn) { moveEdgeExclusion(if (lastTouch.y > 0f) lastTouch.y else view.height * 0.65f) }
        LaunchedEffect(resumes) { launching = false }
        LaunchedEffect(launching) { if (launching) { delay(2500); launching = false } }
        val zoom by animateFloatAsState(
            if (launching) 0.94f else 1f,
            tw(if (launching) 320 else 380, easing = EmphasizedDecel), label = "zoom",
        )
        val fade by animateFloatAsState(
            // Stays mostly visible so there is never a dark gap while the app window is still starting.
            if (launching) 0.45f else 1f,
            tw(if (launching) 300 else 320, easing = EmphasizedDecel), label = "fade",
        )
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(
                        (lastTouch.x / size.width.coerceAtLeast(1f)).coerceIn(0f, 1f),
                        (lastTouch.y / size.height.coerceAtLeast(1f)).coerceIn(0f, 1f),
                    )
                    scaleX = zoom; scaleY = zoom; alpha = fade
                }
                .background(p.bg)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val d = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        lastTouch = d.position
                        moveEdgeExclusion(d.position.y)
                    }
                },
        ) {
            Box(Modifier.fillMaxSize().graphicsLayer {
                translationX = widgetsP.value * size.width * 0.22f - (if (cfg.drawerRight) drawerP.value * size.width * 0.22f else 0f)
                translationY = -((if (cfg.drawerRight) 0f else drawerP.value) + searchP.value) * size.height * 0.22f
                alpha = (1f - drawerP.value - widgetsP.value - searchP.value - settingsP.value).coerceIn(0f, 1f)
            }) {
            Home(
                p, cfg, favorites, weather, event,
                onOpenDrawer = { drawerOpen = true },
                onDrawerDrag = { f -> pageScope.launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) { drawerP.snapTo(f.coerceIn(0f, 1f)) } },
                onDrawerEnd = { velocity ->
                    val open = if (velocity < -150f) true else if (velocity > 150f) false else drawerP.value > 0.1f
                    drawerOpen = open
                    pageScope.launch { drawerP.animateTo(if (open) 1f else 0f, drawerSpec, initialVelocity = 0f) }
                },
                drawerProgress = { maxOf(drawerP.value, searchP.value) },
                searchProgress = { searchP.value },
                onPillPos = { y ->
                    if (searchP.value < 0.001f && drawerP.value < 0.001f && widgetsP.value < 0.001f && settingsP.value < 0.001f) homePillY = y
                },
                onOpenSearch = { searchOpen = true },
                onSearchDrag = { f -> pageScope.launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) { searchP.snapTo(f.coerceIn(0f, 1f)) } },
                onSearchEnd = { velocity ->
                    val open = if (velocity < -150f) true else if (velocity > 150f) false else searchP.value > 0.1f
                    searchOpen = open
                    pageScope.launch { searchP.animateTo(if (open) 1f else 0f, drawerSpec) }
                },
                onSettings = {
                    buzzWith(android.view.HapticFeedbackConstants.LONG_PRESS)
                    settingsOpen = true
                },
                onSleep = { globalAction(ctx, AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN) },
                onNotifications = { globalAction(ctx, AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS) },
                gesturesEnabled = !drawerOpen && !settingsOpen && !searchOpen && !widgetsOpen,
                onLaunch = { openApp(ctx, it) },
                onSlot = openSlot,
                onBuzz = buzz,
                onOpenWidgets = { widgetsOpen = true },
                onWidgetsDrag = { f -> pageScope.launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) { widgetsP.snapTo(f.coerceIn(0f, 1f)) } },
                onWidgetsEnd = { v ->
                    val open = if (v > 150f) true else if (v < -150f) false else widgetsP.value > 0.1f
                    widgetsOpen = open
                    pageScope.launch { widgetsP.animateTo(if (open) 1f else 0f, drawerSpec) }
                },
            )
            }
            if (widgetsVisible || cfg.widgetsOn) {
                Box(Modifier.fillMaxSize().graphicsLayer {
                    translationX = -(1f - widgetsP.value) * size.width
                    alpha = (widgetsP.value * 1.6f).coerceIn(0f, 1f)
                }) {
                    WidgetsScreen(
                        p,
                        onClose = { widgetsOpen = false },
                        onDrag = { f -> pageScope.launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) { widgetsP.snapTo(f.coerceIn(0f, 1f)) } },
                        onEnd = { v ->
                            val open = if (v > 150f) true else if (v < -150f) false else widgetsP.value > 0.9f
                            widgetsOpen = open
                            pageScope.launch { widgetsP.animateTo(if (open) 1f else 0f, drawerSpec) }
                        },
                    )
                }
            }
            run {
                // The app list page, always ready so the first pull is smooth.
                Box(Modifier.fillMaxSize().graphicsLayer {
                    if (cfg.drawerRight) translationX = (1f - drawerP.value) * size.width
                    else translationY = (1f - drawerP.value) * size.height
                    alpha = (drawerP.value * 1.6f).coerceIn(0f, 1f)
                }) {
                    Drawer(
                        p, visibleApps, favorites.map { it.key }.toSet(),
                        fromRight = cfg.drawerRight,
                        active = drawerVisible,
                        progress = { drawerP.value },
                        onLaunch = { openApp(ctx, it) },
                        onTogglePin = togglePin,
                        onClose = { drawerOpen = false },
                        onDrag = { f -> pageScope.launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) { drawerP.snapTo(f.coerceIn(0f, 1f)) } },
                        onEnd = { v ->
                            // v is toward-closed positive (down / right)
                            val open = if (v > 150f) false else if (v < -150f) true else drawerP.value > 0.9f
                            drawerOpen = open
                            pageScope.launch { drawerP.animateTo(if (open) 1f else 0f, drawerSpec) }
                        },
                    )
                }
            }
            if (searchVisible) {
                Box(Modifier.fillMaxSize().graphicsLayer {
                    translationY = (1f - searchP.value) * size.height
                    alpha = (searchP.value * 1.6f).coerceIn(0f, 1f)
                }) {
                    SearchScreen(
                        p, cfg, visibleApps,
                        active = searchOpen,
                        pillShown = pillSettled,
                        onClose = { searchOpen = false },
                        onDrag = { f -> pageScope.launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) { searchP.snapTo(f.coerceIn(0f, 1f)) } },
                        onEnd = { v ->
                            // v is toward-closed positive (down)
                            val open = if (v > 150f) false else if (v < -150f) true else searchP.value > 0.9f
                            searchOpen = open
                            pageScope.launch { searchP.animateTo(if (open) 1f else 0f, drawerSpec) }
                        },
                    )
                }
            }
            if (pillFlying && homePillY >= 0f) {
                Box(Modifier.fillMaxSize().padding(horizontal = 32.dp)) {
                    Pill(p, Modifier.graphicsLayer {
                        translationY = homePillY + (searchPillTop - homePillY) * searchP.value
                    }) {
                        SearchIcon(p.accent)
                        Spacer(Modifier.width(12.dp))
                        BasicText("Search anything", style = ts(p.muted, 16.sp, FontWeight.Normal))
                    }
                }
            }
            if (settingsVisible) Box(Modifier.fillMaxSize().graphicsLayer {
                translationY = (1f - settingsP.value) * size.height * 0.05f
                alpha = (settingsP.value * 1.6f).coerceIn(0f, 1f)
            }) {
                SettingsScreen(
                    p, cfg, updateCfg,
                    favorites = favorites, apps = apps, onSetFavs = setFavs,
                    onPickSlot = { pickSlot = it },
                    onOpenHidden = { hiddenOpen = true },
                    coveredByScreen = hiddenOpen || pickSlot != null,
                    onBack = { settingsOpen = false },
                )
            }
            AnimatedVisibility(visible = hiddenOpen, enter = pushEnter(), exit = pushExit()) {
                HiddenAppsScreen(
                    p, apps, cfg.hidden,
                    onToggle = { key -> updateCfg(cfg.copy(hidden = if (key in cfg.hidden) cfg.hidden - key else cfg.hidden + key)) },
                    onBack = { hiddenOpen = false },
                )
            }
            var lastSlot by remember { mutableStateOf<Slot?>(null) }
            if (pickSlot != null) lastSlot = pickSlot
            AnimatedVisibility(visible = pickSlot != null, enter = pushEnter(), exit = pushExit()) {
                val slot = pickSlot ?: lastSlot
                if (slot != null) {
                    AppPicker(
                        p, slot, apps,
                        onPick = { app -> updateCfg(cfg.withApp(slot, app?.key ?: "")); pickSlot = null },
                        onCancel = { pickSlot = null },
                    )
                }
            }
        }
    }
}

@Composable
private fun rememberNow(): State<LocalDateTime> {
    val ctx = LocalContext.current
    val now = remember { mutableStateOf(LocalDateTime.now()) }
    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) { now.value = LocalDateTime.now() }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        ContextCompat.registerReceiver(ctx, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        now.value = LocalDateTime.now()
        onDispose { ctx.unregisterReceiver(receiver) }
    }
    return now
}

// ---------- Home ----------

@Composable
private fun ClockText(p: Palette, style: ClockStyle, now: LocalDateTime, is24: Boolean) {
    fun f(pattern: String): String = now.format(DateTimeFormatter.ofPattern(pattern))
    val hm = f(if (is24) "H:mm" else "h:mm")
    when (style) {
        ClockStyle.LARGE -> BasicText(hm, style = ts(p.ink, 96.sp, FontWeight.Light, (-4).sp, 104.sp))
        ClockStyle.BOLD -> BasicText(hm, style = ts(p.ink, 96.sp, FontWeight.Bold, (-4).sp, 104.sp))
        ClockStyle.COMPACT -> BasicText(hm, style = ts(p.ink, 48.sp, FontWeight.Normal, (-1).sp, 56.sp))
        ClockStyle.STACKED -> Column {
            BasicText(f(if (is24) "HH" else "hh"), style = ts(p.ink, 88.sp, FontWeight.Light, (-2).sp, 84.sp))
            BasicText(f("mm"), style = ts(p.accent, 88.sp, FontWeight.Light, (-2).sp, 84.sp))
        }
    }
}

/** Set from Settings; when false every transition snaps instead of animating. */
private var animationsOn = true

private fun <T> tw(ms: Int, easing: Easing = androidx.compose.animation.core.FastOutSlowInEasing): FiniteAnimationSpec<T> =
    if (animationsOn) tween(ms, easing = easing) else snap()

private fun sp(damping: Float, stiffness: Float): FiniteAnimationSpec<Float> =
    if (animationsOn) spring(damping, stiffness) else snap()

private val EmphasizedDecel = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
private val EmphasizedAccel = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

// Full screens (Settings, Search): a gentle rise with a soft zoom.
private fun screenEnter(): EnterTransition = slideInVertically(tw(300, easing = EmphasizedDecel)) { it / 14 } +
    scaleIn(tw(300, easing = EmphasizedDecel), initialScale = 0.95f) + fadeIn(tw(210, easing = EmphasizedDecel))
private fun screenExit(): ExitTransition = slideOutVertically(tw(190, easing = EmphasizedAccel)) { it / 14 } +
    scaleOut(tw(190, easing = EmphasizedAccel), targetScale = 0.97f) + fadeOut(tw(150, easing = EmphasizedAccel))

// Sub-menus pushed on top of a screen: slide in from the right.
private fun pushEnter(): EnterTransition = slideInHorizontally(tw(290, easing = EmphasizedDecel)) { it / 3 } + fadeIn(tw(190, easing = EmphasizedDecel))
private fun pushExit(): ExitTransition = slideOutHorizontally(tw(210, easing = EmphasizedAccel)) { it / 3 } + fadeOut(tw(150, easing = EmphasizedAccel))

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.softCombined(onClick: () -> Unit, onLongClick: (() -> Unit)?): Modifier = composed {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    this.graphicsLayer { alpha = if (pressed) 0.5f else 1f }.combinedClickable(
        interactionSource = src, indication = null, role = Role.Button, onClick = onClick, onLongClick = onLongClick,
    )
}

/** A tap target that dims a little while pressed, then eases back. */
private fun Modifier.softClick(role: Role? = null, onClick: () -> Unit): Modifier = composed {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    this.graphicsLayer { alpha = if (pressed) 0.5f else 1f }.clickable(interactionSource = src, indication = null, role = role, onClick = onClick)
}

@Composable
private fun Home(
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
    ) {
        Spacer(Modifier.height(64.dp))
        Column(Modifier.fillMaxWidth()) {
            val datePattern = cfg.date.pattern
            if (datePattern != null) {
                BasicText(
                    now.format(DateTimeFormatter.ofPattern(datePattern)),
                    style = ts(p.muted, 15.sp, FontWeight.Normal),
                    modifier = Modifier
                        .heightIn(min = 40.dp)
                        .softClick(role = Role.Button) { onSlot(Slot.CALENDAR) }
                        .wrapContentHeight(Alignment.CenterVertically),
                )
            }
            if (cfg.showClock) {
                Box(Modifier.softClick(role = Role.Button) { onSlot(Slot.CLOCK) }) {
                    ClockText(p, cfg.clock, now, is24)
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
            if (event != null) {
                BasicText(
                    formatEvent(event, is24),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = ts(p.muted, 15.sp, FontWeight.Normal),
                    modifier = Modifier
                        .heightIn(min = 40.dp)
                        .softClick(role = Role.Button) { onSlot(Slot.CALENDAR) }
                        .wrapContentHeight(Alignment.CenterVertically),
                )
            }
        }
        Spacer(Modifier.weight(1f))
        favorites.take(maxFavorites(cfg)).forEach {
            AppRow(
                it, 30.sp, p.ink, onClick = { onLaunch(it) }, weight = FontWeight.Normal,
                marker = if (cfg.notifDots && it.component.packageName in NotifState.active) p.accent else null,
            )
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

// ---------- Drawer ----------

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Drawer(
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
        Box(Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(
                Modifier.fillMaxSize(),
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
                            modifier = Modifier.graphicsLayer { alpha = if (litLetter == letter) 1f else 1f - 0.65f * focus.value },
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

// ---------- Universal search ----------

@Composable
private fun SearchScreen(
    p: Palette,
    cfg: Config,
    apps: List<App>,
    active: Boolean,
    pillShown: Boolean,
    onClose: () -> Unit,
    onDrag: (Float) -> Unit,
    onEnd: (Float) -> Unit,
) {
    val ctx = LocalContext.current
    val curDragSearch by rememberUpdatedState(onDrag)
    val curEndSearch by rememberUpdatedState(onEnd)
    val rt = remember { RootTracker() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val listState = rememberLazyListState()
    var query by remember { mutableStateOf("") }
    var contacts by remember { mutableStateOf(emptyList<ContactHit>()) }
    var files by remember { mutableStateOf(emptyList<FileHit>()) }
    val focus = remember { FocusRequester() }
    val q = query.trim()

    val appHits = remember(apps, q, cfg.searchApps) {
        if (!cfg.searchApps || q.isEmpty()) emptyList<App>()
        else apps.filter { it.label.contains(q, ignoreCase = true) }.take(6)
    }

    // Keyboard comes up once the page has settled, and goes away as soon as it is being dismissed.
    LaunchedEffect(active) {
        if (active) {
            delay(60)
            try { focus.requestFocus() } catch (_: Exception) { }
        } else {
            keyboard?.hide()
            focusManager.clearFocus()
        }
    }
    LaunchedEffect(q, cfg.searchContacts, cfg.searchFiles) {
        if (q.isEmpty()) {
            contacts = emptyList()
            files = emptyList()
            return@LaunchedEffect
        }
        delay(150)
        contacts = if (cfg.searchContacts) withContext(Dispatchers.IO) { searchContacts(ctx, q) } else emptyList()
        files = if (cfg.searchFiles) withContext(Dispatchers.IO) { searchFiles(ctx, q) } else emptyList()
    }

    val webSearchUrl = cfg.engine.base + URLEncoder.encode(q, "UTF-8")

    Column(
        Modifier
            .fillMaxSize()
            .background(p.bg)
            .onGloballyPositioned { rt.coords = it }
            // Drag the page back down. It follows the finger, and a flick closes it. Only starts when the
            // results are scrolled to the top, so scrolling the list is unaffected.
            .pointerInput(listState) {
                val slop = 3.dp.toPx()
                awaitEachGesture {
                    val first = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    val startedAtTop = !listState.canScrollBackward
                    val tracker = androidx.compose.ui.input.pointer.util.VelocityTracker()
                    val r0 = rt.toRoot(first.position)
                    tracker.addPosition(first.uptimeMillis, r0)
                    var pulling = false
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == first.id } ?: break
                        if (!change.pressed) break
                        val r = rt.toRoot(change.position)
                        tracker.addPosition(change.uptimeMillis, r)
                        val dx = r.x - r0.x
                        val dy = r.y - r0.y
                        if (!pulling && startedAtTop && dy > slop && dy > 0.9f * abs(dx)) {
                            pulling = true
                            keyboard?.hide()
                        }
                        if (pulling) {
                            change.consume()
                            curDragSearch(1f - dy / size.height)
                        }
                    }
                    if (pulling) curEndSearch(tracker.calculateVelocity().y)
                }
            }
            .windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout))
            .imePadding()
            .padding(horizontal = 32.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        Pill(p, Modifier.graphicsLayer { alpha = if (pillShown) 1f else 0f }) {
            SearchIcon(p.accent)
            Spacer(Modifier.width(12.dp))
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                textStyle = ts(p.ink, 16.sp, FontWeight.Normal),
                cursorBrush = SolidColor(p.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    val top = appHits.firstOrNull()
                    if (top != null) {
                        openApp(ctx, top)
                        onClose()
                    } else if (q.isNotEmpty() && cfg.searchWeb) {
                        openUrl(ctx, webSearchUrl)
                        onClose()
                    }
                }),
                modifier = Modifier.weight(1f).focusRequester(focus),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (query.isEmpty()) BasicText("Search anything", style = ts(p.muted, 16.sp, FontWeight.Normal))
                        inner()
                    }
                },
            )
        }
        LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 24.dp)) {
            if (q.isEmpty()) {
                item {
                    val sources = listOfNotNull(
                        "apps".takeIf { cfg.searchApps },
                        "contacts".takeIf { cfg.searchContacts },
                        "files".takeIf { cfg.searchFiles },
                        "the web".takeIf { cfg.searchWeb },
                    )
                    BasicText(
                        if (sources.isEmpty()) "Nothing is turned on. Pick search sources in Settings."
                        else "Searching " + sources.joinToString(", ") + ".",
                        style = ts(p.muted, 15.sp, FontWeight.Normal),
                        modifier = Modifier.padding(top = 24.dp),
                    )
                }
            } else {
                if (cfg.searchWeb) {
                    item {
                        WebRow(p, "Search ${cfg.engine.label} for “$q”") {
                            openUrl(ctx, webSearchUrl)
                            onClose()
                        }
                    }
                    if (isUrl(q)) {
                        item {
                            WebRow(p, "Open $q") {
                                openUrl(ctx, normalizeUrl(q))
                                onClose()
                            }
                        }
                    }
                }
                if (appHits.isNotEmpty()) {
                    item { SectionTitle("Apps", p) }
                    items(appHits, key = { "app" + it.key }) { app ->
                        AppRow(app, 22.sp, p.ink, onClick = { openApp(ctx, app); onClose() })
                    }
                }
                if (contacts.isNotEmpty()) {
                    item { SectionTitle("Contacts", p) }
                    items(contacts, key = { "contact" + it.uri.toString() }) { c ->
                        TextRow(c.name, null, p) { openUri(ctx, c.uri); onClose() }
                    }
                }
                if (files.isNotEmpty()) {
                    item { SectionTitle("Files", p) }
                    items(files, key = { "file" + it.uri.toString() }) { f ->
                        TextRow(f.name, f.mime, p) { openUri(ctx, f.uri, f.mime); onClose() }
                    }
                }
            }
        }
    }
}

// ---------- Settings ----------

@Composable
private fun SettingsScreen(
    p: Palette,
    cfg: Config,
    onConfig: (Config) -> Unit,
    favorites: List<App>,
    apps: List<App>,
    onSetFavs: (List<String>) -> Unit,
    onPickSlot: (Slot) -> Unit,
    onOpenHidden: () -> Unit,
    // True while a screen is open on top of Settings (hidden apps, app picker); Back then belongs to that screen.
    coveredByScreen: Boolean,
    onBack: () -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val now by rememberNow()
    var adding by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var page by remember { mutableStateOf<SettingsPage?>(null) }
    var cityInput by remember { mutableStateOf(cfg.city) }
    val favKeys = favorites.map { it.key }
    val maxFavs = maxFavorites(cfg)
    val full = favKeys.size >= maxFavs
    val addable = remember(apps, favKeys) { apps.filter { it.key !in favKeys } }

    fun has(permission: String) = ctx.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    val contactsPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) onConfig(cfg.copy(searchContacts = true))
        else Toast.makeText(ctx, "Contacts permission was denied", Toast.LENGTH_SHORT).show()
    }
    val calendarPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) onConfig(cfg.copy(calendarOn = true))
        else Toast.makeText(ctx, "Calendar permission was denied", Toast.LENGTH_SHORT).show()
    }

    val locationPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) onConfig(cfg.copy(weatherLocation = true, weatherOn = true))
        else Toast.makeText(ctx, "Location permission was denied", Toast.LENGTH_SHORT).show()
    }
    val notifGranted = remember(now.minute, cfg.notifDots) {
        NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)
    }

    fun move(i: Int, delta: Int) {
        val j = i + delta
        if (j !in favKeys.indices) return
        val list = favKeys.toMutableList()
        val t = list[i]; list[i] = list[j]; list[j] = t
        onSetFavs(list)
    }

    fun setCity() {
        val name = cityInput.trim()
        if (name.isEmpty()) return
        scope.launch {
            val hit = withContext(Dispatchers.IO) { geocode(name) }
            if (hit != null) {
                onConfig(cfg.copy(city = hit.first, lat = hit.second, lon = hit.third, weatherOn = true, weatherLocation = false))
                cityInput = hit.first
            } else {
                Toast.makeText(ctx, "Couldn't find that place (are you online?)", Toast.LENGTH_SHORT).show()
            }
        }
    }

    BackHandler(enabled = page != null && !coveredByScreen) { page = null }

    Column(
        Modifier
            .fillMaxSize()
            .background(p.bg)
            .windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout))
            .imePadding()
            .padding(horizontal = 24.dp),
    ) {
        AnimatedContent(
            targetState = page,
            transitionSpec = {
                val forward = targetState != null
                val spec = if (!animationsOn) {
                    EnterTransition.None togetherWith ExitTransition.None
                } else {
                    // The old page clears out quickly, then the new one glides in: they never overlap, so nothing ghosts.
                    (slideInHorizontally(tween(340, easing = EmphasizedDecel)) { if (forward) it / 10 else -it / 10 } +
                        fadeIn(tween(220, delayMillis = 90, easing = LinearOutSlowInEasing))) togetherWith
                        (slideOutHorizontally(tween(160, easing = EmphasizedAccel)) { if (forward) -it / 12 else it / 12 } +
                            fadeOut(tween(90, easing = LinearEasing)))
                }
                spec.using(SizeTransform(clip = false) { _, _ -> snap() })
            },
            label = "settingsPage",
        ) { target ->
            Column(Modifier.fillMaxSize()) {
                if (target == null) {
                    BasicText(
                        "Back",
                        style = ts(p.accent, 15.sp, FontWeight.Medium),
                        modifier = Modifier
                            .padding(top = 8.dp, start = 8.dp)
                            .heightIn(min = 48.dp)
                            .softClick(role = Role.Button, onClick = onBack)
                            .wrapContentHeight(Alignment.CenterVertically),
                    )
                    BasicText("Settings", style = ts(p.ink, 34.sp), modifier = Modifier.padding(start = 8.dp, bottom = 8.dp))
                } else {
                    Row(
                        Modifier
                            .padding(top = 8.dp)
                            .heightIn(min = 48.dp)
                            .softClick(role = Role.Button) { page = null },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SideChevron(left = true, color = p.accent)
                        Spacer(Modifier.width(2.dp))
                        BasicText("Settings", style = ts(p.accent, 15.sp, FontWeight.Medium))
                    }
                    BasicText(target.title, style = ts(p.ink, 34.sp), modifier = Modifier.padding(start = 8.dp, bottom = 8.dp))
                }

                val listState = rememberLazyListState()
                LazyColumn(Modifier.weight(1f), state = listState, contentPadding = PaddingValues(bottom = 40.dp)) {
                    when (target) {
                        null -> {
                            val cats = listOf(
                                Triple(SettingsPage.FAVORITES, "Favorites", if (favorites.isEmpty()) "None yet" else "${favorites.size} on Home"),
                                Triple(SettingsPage.HOME, "Home screen", "Clock, date, weather, calendar"),
                                Triple(SettingsPage.APPS, "Apps", "Hidden apps, dots, tap actions"),
                                Triple(SettingsPage.GESTURES, "Gestures and panels", if (cfg.drawerRight) "All apps from the right" else "All apps from the bottom"),
                                Triple(SettingsPage.SEARCH, "Search", "Sources and search engine"),
                                Triple(SettingsPage.LOOK, "Look and feel", "Font, wallpaper, animations"),
                            )
                            items(cats, key = { it.first.name }) { (pg, title, summary) ->
                                Row(
                                    Modifier
                                        .padding(vertical = 5.dp)
                                        .fillMaxWidth()
                                        .background(p.pill.copy(alpha = 0.55f), RoundedCornerShape(24.dp))
                                        .softClick(role = Role.Button) { page = pg }
                                        .padding(horizontal = 22.dp, vertical = 18.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        BasicText(title, style = ts(p.ink, 20.sp))
                                        Spacer(Modifier.height(2.dp))
                                        BasicText(summary, style = ts(p.muted, 14.sp, FontWeight.Normal))
                                    }
                                    SideChevron(left = false, color = p.muted)
                                }
                            }
                        }

                        SettingsPage.FAVORITES -> {
                            item(key = "cap") { Caption(p, "Up to $maxFavs apps show on Home. Reorder them with the arrows." + if (cfg.showClock) " Hide the clock to fit 9." else "") }
                            if (favorites.isEmpty()) {
                                item(key = "empty") { Caption(p, "No favorites yet. Add some below.") }
                            }
                            itemsIndexed(favorites, key = { _, a -> a.key }) { i, app ->
                                Row(
                                    itemAnim()
                                        .padding(vertical = 4.dp)
                                        .fillMaxWidth()
                                        .background(p.pill.copy(alpha = 0.55f), RoundedCornerShape(20.dp))
                                        .padding(start = 20.dp, end = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    BasicText(
                                        app.label, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        style = ts(p.ink, 20.sp),
                                        modifier = Modifier.weight(1f),
                                    )
                                    IconBtn(Glyph.UP, "Move ${app.label} up", if (i > 0) p.muted else p.muted.copy(alpha = 0.3f), i > 0) { move(i, -1) }
                                    IconBtn(Glyph.DOWN, "Move ${app.label} down", if (i < favorites.lastIndex) p.muted else p.muted.copy(alpha = 0.3f), i < favorites.lastIndex) { move(i, 1) }
                                    IconBtn(Glyph.CLOSE, "Remove ${app.label}", p.muted) { onSetFavs(favKeys - app.key) }
                                }
                            }
                            item(key = "addBtn") {
                                BasicText(
                                    if (adding) "Done" else if (full) "Home fits $maxFavs favorites" else "Add an app",
                                    style = ts(if (full && !adding) p.muted else p.accent, 16.sp, FontWeight.Medium),
                                    modifier = itemAnim()
                                        .padding(top = 8.dp)
                                        .heightIn(min = 52.dp)
                                        .padding(horizontal = 8.dp)
                                        .softClick(role = Role.Button) { if (adding || !full) { adding = !adding; if (!adding) query = "" } }
                                        .wrapContentHeight(Alignment.CenterVertically),
                                )
                            }
                            if (adding) {
                                item(key = "addSearch") {
                                    Pill(p, itemAnim().padding(bottom = 8.dp)) {
                                        SearchIcon(p.accent)
                                        Spacer(Modifier.width(12.dp))
                                        BasicTextField(
                                            value = query,
                                            onValueChange = { query = it },
                                            singleLine = true,
                                            textStyle = ts(p.ink, 16.sp, FontWeight.Normal),
                                            cursorBrush = SolidColor(p.accent),
                                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                            modifier = Modifier.weight(1f),
                                            decorationBox = { inner ->
                                                Box(contentAlignment = Alignment.CenterStart) {
                                                    if (query.isEmpty()) BasicText("Search apps to add", style = ts(p.muted, 16.sp, FontWeight.Normal))
                                                    inner()
                                                }
                                            },
                                        )
                                    }
                                }
                                val q = query.trim()
                                val shown = if (q.isEmpty()) addable else addable.filter { it.label.contains(q, ignoreCase = true) }
                                if (shown.isEmpty()) {
                                    item(key = "noMatch") { Caption(p, "No matching apps.") }
                                }
                                items(shown, key = { it.key }) { app ->
                                    Row(
                                        itemAnim()
                                            .fillMaxWidth()
                                            .heightIn(min = 52.dp)
                                            .softClick(role = Role.Button) {
                                                onSetFavs(favKeys + app.key)
                                                if (favKeys.size + 1 >= maxFavs) { adding = false; query = "" }
                                            }
                                            .padding(horizontal = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        BasicText(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis, style = ts(p.ink, 20.sp, FontWeight.Light), modifier = Modifier.weight(1f))
                                        IconBtn(Glyph.PLUS, "Add ${app.label}", p.accent) {
                                            onSetFavs(favKeys + app.key)
                                            if (favKeys.size + 1 >= maxFavs) { adding = false; query = "" }
                                        }
                                    }
                                }
                            }
                        }

                        SettingsPage.HOME -> {
                            settingsBlock {
                                SettingsGroup(p, "Clock style") {
                                    SwitchRow("Show clock", cfg.showClock, p) { onConfig(cfg.copy(showClock = it)) }
                                    ClockStyle.entries.forEach { s -> OptionRow(s.label, s == cfg.clock, p) { onConfig(cfg.copy(clock = s)) } }
                                }
                            }
                            settingsBlock {
                                SettingsGroup(p, "Time format") {
                                    HourFormat.entries.forEach { h -> OptionRow(h.label, h == cfg.hours, p) { onConfig(cfg.copy(hours = h)) } }
                                }
                            }
                            settingsBlock {
                                SettingsGroup(p, "Date format") {
                                    DateStyle.entries.forEach { d ->
                                        val pattern = d.pattern
                                        val label = if (pattern == null) "Hidden" else now.format(DateTimeFormatter.ofPattern(pattern))
                                        OptionRow(label, d == cfg.date, p) { onConfig(cfg.copy(date = d)) }
                                    }
                                }
                            }
                            settingsBlock {
                                SettingsGroup(p, "Weather") {
                                    SwitchRow("Show weather on Home", cfg.weatherOn, p) { onConfig(cfg.copy(weatherOn = it)) }
                                    SwitchRow("Use my current location", cfg.weatherLocation, p) { on ->
                                        if (on && !has(Manifest.permission.ACCESS_COARSE_LOCATION)) locationPerm.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                                        else onConfig(cfg.copy(weatherLocation = on, weatherOn = cfg.weatherOn || on))
                                    }
                                    Pill(p, Modifier.padding(vertical = 8.dp).alpha(if (cfg.weatherLocation) 0.4f else 1f), color = p.bg) {
                                        BasicTextField(
                                            enabled = !cfg.weatherLocation,
                                            value = cityInput,
                                            onValueChange = { cityInput = it },
                                            singleLine = true,
                                            textStyle = ts(p.ink, 16.sp, FontWeight.Normal),
                                            cursorBrush = SolidColor(p.accent),
                                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                            keyboardActions = KeyboardActions(onDone = { setCity() }),
                                            modifier = Modifier.weight(1f),
                                            decorationBox = { inner ->
                                                Box(contentAlignment = Alignment.CenterStart) {
                                                    if (cityInput.isEmpty()) BasicText("Your city", style = ts(p.muted, 16.sp, FontWeight.Normal))
                                                    inner()
                                                }
                                            },
                                        )
                                        BasicText(
                                            "Set",
                                            style = ts(p.accent, 16.sp, FontWeight.Medium),
                                            modifier = Modifier
                                                .heightIn(min = 48.dp)
                                                .clickable(enabled = !cfg.weatherLocation, role = Role.Button) { setCity() }
                                                .wrapContentHeight(Alignment.CenterVertically)
                                                .padding(start = 12.dp),
                                        )
                                    }
                                    SwitchRow("Use Fahrenheit", cfg.fahrenheit, p) { onConfig(cfg.copy(fahrenheit = it)) }
                                }
                                Caption(
                                    p,
                                    if (cfg.weatherLocation) "Using your approximate location. Only coordinates are sent, to Open-Meteo."
                                    else if (cfg.city.isBlank()) "Type a city and tap Set. Only the city name is sent, to Open-Meteo."
                                    else "Showing weather for ${cfg.city}.",
                                )
                            }
                            settingsBlock {
                                SettingsGroup(p, "Calendar") {
                                    SwitchRow("Show next event on Home", cfg.calendarOn, p) { on ->
                                        if (on && !has(Manifest.permission.READ_CALENDAR)) calendarPerm.launch(Manifest.permission.READ_CALENDAR)
                                        else onConfig(cfg.copy(calendarOn = on))
                                    }
                                }
                            }
                            settingsBlock {
                                SettingsGroup(p, "Status bar") {
                                    SwitchRow("Hide status bar", cfg.hideStatusBar, p) { onConfig(cfg.copy(hideStatusBar = it)) }
                                    if (cfg.hideStatusBar) {
                                        SwitchRow("Battery on Home", cfg.showBattery, p) { onConfig(cfg.copy(showBattery = it)) }
                                    }
                                }
                                if (cfg.hideStatusBar) {
                                    Caption(p, "The battery level shows next to the weather on Home. Swipe down from the very top edge to peek at the real status bar.")
                                }
                            }
                        }

                        SettingsPage.APPS -> {
                            settingsBlock {
                                SettingsGroup(p, "Hidden apps") {
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 52.dp)
                                            .softClick(role = Role.Button, onClick = onOpenHidden),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        BasicText("Choose apps to hide", style = ts(p.ink, 20.sp), modifier = Modifier.weight(1f))
                                        BasicText(if (cfg.hidden.isEmpty()) "None" else "${cfg.hidden.size}", style = ts(p.muted, 16.sp, FontWeight.Normal))
                                        Spacer(Modifier.width(8.dp))
                                        SideChevron(left = false, color = p.muted)
                                    }
                                }
                                Caption(p, "Hidden apps don't appear in the all apps panel or in search. You can still add them to Home as favorites.")
                            }
                            settingsBlock {
                                SettingsGroup(p, "Notification dots") {
                                    SwitchRow("Dot on favorites with notifications", cfg.notifDots, p) { on ->
                                        onConfig(cfg.copy(notifDots = on))
                                        if (on && !notifGranted) {
                                            ctx.startActivity(
                                                Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                            )
                                        }
                                    }
                                }
                                Caption(
                                    p,
                                    if (notifGranted) "Notification access is on. MinimalYou only checks which apps have a notification and never reads its content."
                                    else "Needs notification access. Turn on MinimalYou notification dots in the list that opens. On Android 13+ you may first need App info > three dots > Allow restricted settings.",
                                )
                            }
                            settingsBlock {
                                SettingsGroup(p, "Tap actions") {
                                    Slot.entries.forEach { slot ->
                                        val name = apps.firstOrNull { it.key == cfg.appFor(slot) }?.label ?: "Default"
                                        Row(
                                            Modifier
                                                .fillMaxWidth()
                                                .heightIn(min = 52.dp)
                                                .softClick(role = Role.Button) { onPickSlot(slot) },
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            BasicText(slot.label, style = ts(p.ink, 20.sp), modifier = Modifier.weight(1f))
                                            BasicText(name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = ts(p.muted, 16.sp, FontWeight.Normal))
                                            Spacer(Modifier.width(8.dp))
                                            SideChevron(left = false, color = p.muted)
                                        }
                                    }
                                }
                                Caption(p, "Pick which app opens when you tap the clock, the date, or the weather on Home.")
                            }
                        }

                        SettingsPage.GESTURES -> {
                            settingsBlock {
                                SettingsGroup(p, "All apps panel") {
                                    OptionRow("From the bottom (swipe up)", !cfg.drawerRight, p) { onConfig(cfg.copy(drawerRight = false)) }
                                    OptionRow("From the right (swipe left)", cfg.drawerRight, p) { onConfig(cfg.copy(drawerRight = true)) }
                                    if (cfg.drawerRight) {
                                        SwitchRow("Swipe up to start search", cfg.swipeUpSearch, p) { onConfig(cfg.copy(swipeUpSearch = it)) }
                                    }
                                }
                            }
                            settingsBlock {
                                SettingsGroup(p, "Widgets page") {
                                    SwitchRow("Page to the left of Home", cfg.widgetsOn, p) { onConfig(cfg.copy(widgetsOn = it)) }
                                }
                                Caption(p, "Swipe right on Home to open it. Add and remove widgets there.")
                            }
                            settingsBlock {
                                SettingsGroup(p, "Home gestures") {
                                    BasicText(
                                        "Double tap a blank spot to sleep. Long-press it to open Settings. Swipe down for notifications. Swipe ${if (cfg.drawerRight) "left" else "up"} for your apps${if (cfg.drawerRight && cfg.swipeUpSearch) ", swipe up to search" else ""}.",
                                        style = ts(p.ink, 15.sp, FontWeight.Normal),
                                        modifier = Modifier.padding(vertical = 14.dp),
                                    )
                                    BasicText(
                                        "Open accessibility settings",
                                        style = ts(p.accent, 16.sp, FontWeight.Medium),
                                        modifier = Modifier
                                            .heightIn(min = 52.dp)
                                            .softClick(role = Role.Button) {
                                                ctx.startActivity(
                                                    Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                                )
                                            }
                                            .wrapContentHeight(Alignment.CenterVertically),
                                    )
                                }
                                Caption(p, "Sleep and notifications need the MinimalYou gestures accessibility service.")
                            }
                        }

                        SettingsPage.SEARCH -> {
                            settingsBlock {
                                SettingsGroup(p, "Search in") {
                                    SwitchRow("Apps", cfg.searchApps, p) { onConfig(cfg.copy(searchApps = it)) }
                                    SwitchRow("Contacts", cfg.searchContacts, p) { on ->
                                        if (on && !has(Manifest.permission.READ_CONTACTS)) contactsPerm.launch(Manifest.permission.READ_CONTACTS)
                                        else onConfig(cfg.copy(searchContacts = on))
                                    }
                                    SwitchRow("Files and documents", cfg.searchFiles, p) { on ->
                                        onConfig(cfg.copy(searchFiles = on))
                                        if (on && !Environment.isExternalStorageManager()) {
                                            Toast.makeText(ctx, "Allow file access, then come back", Toast.LENGTH_LONG).show()
                                            ctx.startActivity(
                                                Intent(
                                                    android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                                    Uri.parse("package:" + ctx.packageName),
                                                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                            )
                                        }
                                    }
                                    SwitchRow("Web search", cfg.searchWeb, p) { onConfig(cfg.copy(searchWeb = it)) }
                                }
                            }
                            settingsBlock {
                                SettingsGroup(p, "Search engine") {
                                    Engine.entries.forEach { e -> OptionRow(e.label, e == cfg.engine, p) { onConfig(cfg.copy(engine = e)) } }
                                }
                            }
                        }

                        SettingsPage.LOOK -> {
                            settingsBlock {
                                SettingsGroup(p, "Font") {
                                    AppFont.entries.forEach { f -> OptionRow(f.label, f == cfg.font, p, family = f.family) { onConfig(cfg.copy(font = f)) } }
                                }
                            }
                            settingsBlock {
                                SettingsGroup(p, "Wallpaper") {
                                    val c = LocalContext.current
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 52.dp)
                                            .softClick(role = Role.Button) {
                                                appPrefs(c).edit().putInt("wp_seed", c.getColor(android.R.color.system_accent1_500)).apply()
                                                try {
                                                    c.startActivity(
                                                        Intent(android.app.WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
                                                            .putExtra(
                                                                android.app.WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                                                                android.content.ComponentName(c, MatchWallpaperService::class.java),
                                                            )
                                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                                    )
                                                } catch (_: Exception) {
                                                    Toast.makeText(c, "Couldn't open the wallpaper picker", Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        BasicText("Use matching wallpaper", style = ts(p.ink, 20.sp), modifier = Modifier.weight(1f))
                                        SideChevron(left = false, color = p.muted)
                                    }
                                }
                                Caption(p, "A plain wallpaper in the same color as Home. It switches between light and dark with your phone, and keeps your current Material You colors instead of recoloring them.")
                            }
                            settingsBlock {
                                SettingsGroup(p, "Animations and feedback") {
                                    SwitchRow("Animations", cfg.animations, p) { onConfig(cfg.copy(animations = it)) }
                                    SwitchRow("Disable vibration feedback", cfg.noHaptics, p) { onConfig(cfg.copy(noHaptics = it)) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------- Pieces ----------

private enum class SettingsPage(val title: String) {
    FAVORITES("Favorites"),
    HOME("Home screen"),
    APPS("Apps"),
    GESTURES("Gestures and panels"),
    SEARCH("Search"),
    LOOK("Look and feel"),
}

private fun androidx.compose.foundation.lazy.LazyListScope.settingsBlock(content: @Composable ColumnScope.() -> Unit) {
    item { Column(Modifier.fillMaxWidth(), content = content) }
}

private fun LazyItemScope.itemAnim(): Modifier = if (animationsOn) Modifier.animateItem() else Modifier

/** A titled, rounded block of related settings. */
@Composable
private fun SettingsGroup(p: Palette, title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 18.dp)) {
        if (title != null) {
            BasicText(
                title.uppercase(),
                style = ts(p.accent, 12.sp, FontWeight.Medium, 1.sp),
                modifier = Modifier.padding(start = 12.dp, bottom = 8.dp),
            )
        }
        Column(
            Modifier
                .fillMaxWidth()
                .background(p.pill.copy(alpha = 0.55f), RoundedCornerShape(24.dp))
                .padding(horizontal = 20.dp, vertical = 6.dp),
            content = content,
        )
    }
}

/** Small explanatory text under a group. */
@Composable
private fun Caption(p: Palette, text: String) {
    BasicText(
        text,
        style = ts(p.muted, 13.sp, FontWeight.Normal),
        modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp),
    )
}

@Composable
private fun SideChevron(left: Boolean, color: Color) {
    Canvas(Modifier.size(18.dp)) {
        val s = size.minDimension
        val w = s * 0.11f
        val x1 = if (left) 0.65f else 0.35f
        val x2 = if (left) 0.3f else 0.7f
        drawLine(color, Offset(s * x1, s * 0.15f), Offset(s * x2, s * 0.5f), strokeWidth = w, cap = StrokeCap.Round)
        drawLine(color, Offset(s * x2, s * 0.5f), Offset(s * x1, s * 0.85f), strokeWidth = w, cap = StrokeCap.Round)
    }
}

@Composable
private fun SectionTitle(text: String, p: Palette) {
    BasicText(
        text.uppercase(),
        style = ts(p.accent, 13.sp, FontWeight.Medium, 1.sp),
        modifier = Modifier.padding(top = 28.dp, bottom = 4.dp),
    )
}

@Composable
private fun OptionRow(label: String, selected: Boolean, p: Palette, family: FontFamily? = null, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .softClick(role = Role.RadioButton, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioDot(selected, p)
        Spacer(Modifier.width(16.dp))
        BasicText(
            label,
            style = TextStyle(
                color = p.ink, fontSize = 22.sp, fontWeight = FontWeight.Light,
                fontFamily = family ?: LocalFont.current,
            ),
        )
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, p: Palette, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(label, style = ts(p.ink, 20.sp), modifier = Modifier.weight(1f))
        val t by animateFloatAsState(if (checked) 1f else 0f, sp(0.8f, 700f), label = "switch")
        Canvas(Modifier.size(width = 44.dp, height = 26.dp)) {
            val r = size.height / 2f
            drawRoundRect(androidx.compose.ui.graphics.lerp(p.pill, p.accent, t.coerceIn(0f, 1f)), cornerRadius = CornerRadius(r, r))
            drawCircle(
                androidx.compose.ui.graphics.lerp(p.muted, p.bg, t.coerceIn(0f, 1f)),
                radius = r * (0.62f + 0.1f * t),
                center = Offset(r + (size.width - 2 * r) * t, r),
            )
        }
    }
}

@Composable
private fun RadioDot(selected: Boolean, p: Palette) {
    val color by animateColorAsState(if (selected) p.accent else p.muted, tw(160), label = "radioColor")
    val dot by animateFloatAsState(if (selected) 0.24f else 0f, sp(0.7f, 800f), label = "radioDot")
    Canvas(Modifier.size(20.dp)) {
        val s = size.minDimension
        drawCircle(color, radius = s * 0.45f, style = Stroke(s * 0.1f))
        if (dot > 0.001f) drawCircle(p.accent, radius = s * dot)
    }
}

private enum class Glyph { UP, DOWN, CLOSE, PLUS }

@Composable
private fun IconBtn(glyph: Glyph, description: String, color: Color, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier
            .size(48.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(18.dp)) {
            val s = size.minDimension
            val w = s * 0.11f
            fun line(a: Offset, b: Offset) = drawLine(color, a, b, strokeWidth = w, cap = StrokeCap.Round)
            when (glyph) {
                Glyph.UP -> { line(Offset(s * .15f, s * .65f), Offset(s * .5f, s * .3f)); line(Offset(s * .5f, s * .3f), Offset(s * .85f, s * .65f)) }
                Glyph.DOWN -> { line(Offset(s * .15f, s * .35f), Offset(s * .5f, s * .7f)); line(Offset(s * .5f, s * .7f), Offset(s * .85f, s * .35f)) }
                Glyph.PLUS -> { line(Offset(s * .5f, s * .15f), Offset(s * .5f, s * .85f)); line(Offset(s * .15f, s * .5f), Offset(s * .85f, s * .5f)) }
                Glyph.CLOSE -> { line(Offset(s * .2f, s * .2f), Offset(s * .8f, s * .8f)); line(Offset(s * .8f, s * .2f), Offset(s * .2f, s * .8f)) }
            }
        }
    }
}

@Composable
private fun Pill(p: Palette, modifier: Modifier = Modifier, color: Color = p.pill, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(color, RoundedCornerShape(28.dp))
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
private fun SearchIcon(color: Color) {
    Canvas(Modifier.size(20.dp)) {
        val s = size.minDimension
        val w = s * 0.09f
        drawCircle(color, radius = s * 0.32f, center = Offset(s * 0.43f, s * 0.43f), style = Stroke(w))
        drawLine(color, Offset(s * 0.66f, s * 0.66f), Offset(s * 0.9f, s * 0.9f), strokeWidth = w, cap = StrokeCap.Round)
    }
}

@Composable
private fun WebRow(p: Palette, text: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .softClick(role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SearchIcon(p.accent)
        Spacer(Modifier.width(16.dp))
        BasicText(text, maxLines = 2, overflow = TextOverflow.Ellipsis, style = ts(p.ink, 18.sp, FontWeight.Normal))
    }
}

@Composable
private fun TextRow(text: String, sub: String?, p: Palette, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .softClick(role = Role.Button, onClick = onClick)
            .padding(vertical = 6.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        BasicText(text, maxLines = 1, overflow = TextOverflow.Ellipsis, style = ts(p.ink, 20.sp))
        if (sub != null) {
            BasicText(sub, maxLines = 1, overflow = TextOverflow.Ellipsis, style = ts(p.muted, 13.sp, FontWeight.Normal))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppRow(
    app: App,
    size: TextUnit,
    color: Color,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    weight: FontWeight = FontWeight.Light,
    marker: Color? = null,
    modifier: Modifier = Modifier,
) {
    // No fillMaxWidth: only the app name itself (and its dot) is tappable, not the empty space beside it.
    Row(
        modifier
            .heightIn(min = 48.dp)
            .softCombined(onClick = onClick, onLongClick = onLongClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(
            app.label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = ts(color, size, weight),
            modifier = Modifier.weight(1f, fill = false),
        )
        if (marker != null) {
            Spacer(Modifier.width(12.dp))
            Canvas(Modifier.size(8.dp)) { drawCircle(marker) }
        }
    }
}

@Composable
private fun HiddenAppsScreen(p: Palette, apps: List<App>, hidden: Set<String>, onToggle: (String) -> Unit, onBack: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(p.bg)
            .windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout))
            .padding(horizontal = 32.dp)
            .pointerInput(Unit) { detectTapGestures { } },
    ) {
        BasicText(
            "Back",
            style = ts(p.accent, 16.sp, FontWeight.Medium),
            modifier = Modifier
                .padding(top = 8.dp)
                .heightIn(min = 52.dp)
                .softClick(role = Role.Button, onClick = onBack)
                .wrapContentHeight(Alignment.CenterVertically),
        )
        BasicText("Hidden apps", style = ts(p.ink, 28.sp))
        LazyColumn(contentPadding = PaddingValues(top = 12.dp, bottom = 32.dp)) {
            items(apps, key = { it.key }) { app ->
                SwitchRow(app.label, app.key in hidden, p) { onToggle(app.key) }
            }
        }
    }
}

@Composable
private fun AppPicker(p: Palette, slot: Slot, apps: List<App>, onPick: (App?) -> Unit, onCancel: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(p.bg)
            .windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout))
            .padding(horizontal = 32.dp)
            .pointerInput(Unit) { detectTapGestures { } },
    ) {
        BasicText(
            "Cancel",
            style = ts(p.accent, 16.sp, FontWeight.Medium),
            modifier = Modifier
                .padding(top = 8.dp)
                .heightIn(min = 52.dp)
                .softClick(role = Role.Button, onClick = onCancel)
                .wrapContentHeight(Alignment.CenterVertically),
        )
        BasicText("Open ${slot.label.lowercase()} with", style = ts(p.ink, 28.sp))
        LazyColumn(contentPadding = PaddingValues(top = 12.dp, bottom = 32.dp)) {
            item {
                BasicText(
                    "Default",
                    style = ts(p.accent, 22.sp, FontWeight.Medium),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .softClick(role = Role.Button) { onPick(null) }
                        .wrapContentHeight(Alignment.CenterVertically),
                )
            }
            items(apps, key = { it.key }) { app ->
                AppRow(app, 22.sp, p.ink, onClick = { onPick(app) })
            }
        }
    }
}

// ---------- Widgets page ----------

private const val WIDGET_HOST_ID = 1024

private fun readWidgetIds(ctx: Context): List<Int> =
    (appPrefs(ctx).getString("widget_ids", "") ?: "").split(",").mapNotNull { it.toIntOrNull() }

private fun writeWidgetIds(ctx: Context, ids: List<Int>) {
    appPrefs(ctx).edit().putString("widget_ids", ids.joinToString(",")).apply()
}

/** A drag handle. Reports total drag distance in px since the touch began, measured on screen so it stays accurate while the handle itself moves. */
@Composable
private fun ResizeHandle(
    horizontal: Boolean,
    p: Palette,
    description: String,
    onStart: () -> Unit,
    onTotal: (Float) -> Unit,
    onEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val start by rememberUpdatedState(onStart)
    val total by rememberUpdatedState(onTotal)
    val end by rememberUpdatedState(onEnd)
    val origin = remember { floatArrayOf(0f, 0f) }
    Box(
        modifier
            .size(width = if (horizontal) 40.dp else 140.dp, height = if (horizontal) 140.dp else 40.dp)
            .onGloballyPositioned { c ->
                val o = c.positionInRoot()
                origin[0] = o.x
                origin[1] = o.y
            }
            .pointerInput(horizontal) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    start()
                    val startAbs = if (horizontal) origin[0] + down.position.x else origin[1] + down.position.y
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        val abs = if (horizontal) origin[0] + change.position.x else origin[1] + change.position.y
                        total(abs - startAbs)
                        change.consume()
                    }
                    end()
                }
            }
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(width = if (horizontal) 20.dp else 64.dp, height = if (horizontal) 64.dp else 20.dp)
                .background(p.bg.copy(alpha = 0.75f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(width = if (horizontal) 4.dp else 36.dp, height = if (horizontal) 36.dp else 4.dp)
                    .background(p.accent, CircleShape),
            )
        }
    }
}

/** The widget's own preview picture (what Android shows in its widget picker), if it provides one. */
@Composable
private fun WidgetPreview(info: AppWidgetProviderInfo, ctx: Context) {
    val image = remember(info) {
        try {
            val d = info.loadPreviewImage(ctx, 0)
            if (d == null) null else {
                val w = d.intrinsicWidth.coerceAtLeast(1)
                val h = d.intrinsicHeight.coerceAtLeast(1)
                val scale = (480f / w).coerceAtMost(1f)
                d.toBitmap((w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1)).asImageBitmap()
            }
        } catch (_: Throwable) {
            null
        }
    }
    if (image != null) {
        Image(
            bitmap = image,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .padding(top = 8.dp)
                .heightIn(max = 110.dp)
                .widthIn(max = 260.dp)
                .clip(RoundedCornerShape(12.dp)),
        )
    }
}

@Composable
private fun Chevron(up: Boolean, color: Color) {
    val rot by animateFloatAsState(if (up) 180f else 0f, tw(220, easing = EmphasizedDecel), label = "chevron")
    Canvas(Modifier.size(18.dp).graphicsLayer { rotationZ = rot }) {
        val s = size.minDimension
        val w = s * 0.11f
        drawLine(color, Offset(s * 0.15f, s * 0.35f), Offset(s * 0.5f, s * 0.7f), strokeWidth = w, cap = StrokeCap.Round)
        drawLine(color, Offset(s * 0.5f, s * 0.7f), Offset(s * 0.85f, s * 0.35f), strokeWidth = w, cap = StrokeCap.Round)
    }
}

/**
 * Hosts a widget but lets the page scroll when you drag on it. Widgets swallow touches, so a vertical
 * drag is taken over here (unless something inside the widget can scroll itself) and forwarded to the list.
 */
private class ScrollPassthroughFrame(ctx: Context) : android.widget.FrameLayout(ctx) {
    var onScrollBy: (Float) -> Unit = {}
    var onInnerScroll: (Boolean) -> Unit = {}
    var onFling: (Float) -> Unit = {}
    private val slop = android.view.ViewConfiguration.get(ctx).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var lastY = 0f
    private var scrolling = false
    private var tracker: android.view.VelocityTracker? = null

    private fun track(ev: android.view.MotionEvent) {
        val t = tracker ?: android.view.VelocityTracker.obtain().also { tracker = it }
        val c = android.view.MotionEvent.obtain(ev)
        c.setLocation(ev.rawX, ev.rawY)
        t.addMovement(c)
        c.recycle()
    }

    private fun scrollableAt(v: android.view.View, x: Float, y: Float): Boolean {
        if (v.canScrollVertically(1) || v.canScrollVertically(-1)) return true
        if (v is android.view.ViewGroup) {
            for (i in v.childCount - 1 downTo 0) {
                val c = v.getChildAt(i)
                val cx = x + v.scrollX - c.left
                val cy = y + v.scrollY - c.top
                if (cx >= 0 && cy >= 0 && cx < c.width && cy < c.height && scrollableAt(c, cx, cy)) return true
            }
        }
        return false
    }

    override fun onInterceptTouchEvent(ev: android.view.MotionEvent): Boolean {
        when (ev.actionMasked) {
            android.view.MotionEvent.ACTION_DOWN -> {
                downX = ev.x; downY = ev.y; lastY = ev.rawY; scrolling = false
                tracker?.recycle(); tracker = null
                track(ev)
                // Something inside the widget scrolls: give it the whole gesture and park the page.
                if (scrollableAt(this, downX, downY)) {
                    onInnerScroll(true)
                    parent?.requestDisallowInterceptTouchEvent(true)
                }
            }
            android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> onInnerScroll(false)
            android.view.MotionEvent.ACTION_MOVE -> {
                track(ev)
                val dx = ev.x - downX
                val dy = ev.y - downY
                if (!scrolling && Math.abs(dy) > slop && Math.abs(dy) > Math.abs(dx) && !scrollableAt(this, downX, downY)) {
                    scrolling = true
                    lastY = ev.rawY
                    return true
                }
            }
        }
        return scrolling
    }

    override fun onTouchEvent(ev: android.view.MotionEvent): Boolean {
        if (!scrolling) return false
        track(ev)
        when (ev.actionMasked) {
            android.view.MotionEvent.ACTION_MOVE -> {
                onScrollBy(lastY - ev.rawY)
                lastY = ev.rawY
            }
            android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                val t = tracker
                if (t != null && ev.actionMasked == android.view.MotionEvent.ACTION_UP) {
                    t.computeCurrentVelocity(1000)
                    onFling(-t.yVelocity)
                }
                tracker?.recycle(); tracker = null
                scrolling = false
            }
        }
        return true
    }
}

@Composable
private fun WidgetsScreen(p: Palette, onClose: () -> Unit, onDrag: (Float) -> Unit, onEnd: (Float) -> Unit) {
    val curDrag by rememberUpdatedState(onDrag)
    val rt = remember { RootTracker() }
    val curEnd by rememberUpdatedState(onEnd)
    val ctx = LocalContext.current
    val host = remember { AppWidgetHost(ctx.applicationContext, WIDGET_HOST_ID) }
    val manager = remember { AppWidgetManager.getInstance(ctx) }
    val density = LocalDensity.current
    val screenWidthDp = LocalConfiguration.current.screenWidthDp - 64
    var ids by remember { mutableStateOf(readWidgetIds(ctx)) }
    var editing by remember { mutableStateOf(false) }
    var choosing by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf(-1) } // widget id waiting on a bind or configure step
    val listState = rememberLazyListState()
    var innerScrolling by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val fling = ScrollableDefaults.flingBehavior()

    // Saved size overrides: height in dp, width as a percent of the page.
    val heights = remember {
        mutableStateMapOf<Int, Int>().apply {
            readWidgetIds(ctx).forEach { id ->
                val v = appPrefs(ctx).getInt("widget_h_$id", -1)
                if (v > 0) put(id, v)
            }
        }
    }
    val widths = remember {
        mutableStateMapOf<Int, Int>().apply {
            readWidgetIds(ctx).forEach { id ->
                val v = appPrefs(ctx).getInt("widget_w_$id", -1)
                if (v > 0) put(id, v)
            }
        }
    }

    DisposableEffect(host) {
        try { host.startListening() } catch (_: Exception) { }
        onDispose { try { host.stopListening() } catch (_: Exception) { } }
    }
    BackHandler(enabled = choosing) { choosing = false }
    LaunchedEffect(ids.isEmpty()) { if (ids.isEmpty()) editing = false }

    fun addId(id: Int) {
        ids = ids + id
        writeWidgetIds(ctx, ids)
        pending = -1
    }
    fun dropId(id: Int) {
        try { host.deleteAppWidgetId(id) } catch (_: Exception) { }
        ids = ids - id
        writeWidgetIds(ctx, ids)
        heights.remove(id)
        widths.remove(id)
        appPrefs(ctx).edit().remove("widget_h_$id").remove("widget_w_$id").apply()
    }

    val configLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val id = pending
        if (id != -1) {
            if (r.resultCode == Activity.RESULT_OK) addId(id)
            else { try { host.deleteAppWidgetId(id) } catch (_: Exception) { }; pending = -1 }
        }
    }
    fun finishBind(id: Int) {
        val configure = manager.getAppWidgetInfo(id)?.configure
        if (configure != null) {
            pending = id
            try {
                configLauncher.launch(
                    Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
                        .setComponent(configure)
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id),
                )
            } catch (_: Exception) {
                addId(id)
            }
        } else {
            addId(id)
        }
    }
    val bindLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val id = pending
        if (id != -1) {
            if (r.resultCode == Activity.RESULT_OK) finishBind(id)
            else { try { host.deleteAppWidgetId(id) } catch (_: Exception) { }; pending = -1 }
        }
    }
    fun pick(info: AppWidgetProviderInfo) {
        val id = host.allocateAppWidgetId()
        choosing = false
        if (manager.bindAppWidgetIdIfAllowed(id, info.provider)) {
            finishBind(id)
        } else {
            pending = id
            bindLauncher.launch(
                Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider),
            )
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(p.bg)
            // Keep touches from reaching Home underneath, without consuming them so widgets stay interactive.
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent(PointerEventPass.Final) } },
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .onGloballyPositioned { rt.coords = it }
                // Swipe left to push the page back off to the left. Follows the finger; widgets keep their touches
                // unless the drag is clearly horizontal.
                .pointerInput(editing) {
                    if (editing) return@pointerInput
                    awaitEachGesture {
                        val first = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
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
                            dx = r.x - r0.x; dy = r.y - r0.y
                            // A widget with its own scrolling content only loses the gesture to a clear, flat swipe.
                            val clear = if (innerScrolling) dx < -12.dp.toPx() && abs(dx) > 2f * abs(dy)
                                else dx < -3.dp.toPx() && abs(dx) > 0.6f * abs(dy)
                            if (!pulling && clear) pulling = true
                            if (pulling) {
                                change.consume()
                                curDrag(1f + dx / size.width.toFloat())
                            }
                        }
                        // The widget view can swallow its own lift event, which used to leave this stuck on.
                        innerScrolling = false
                        if (pulling) curEnd(tracker.calculateVelocity().x)
                    }
                }
                .windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout))
                .padding(horizontal = 32.dp),
        ) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                BasicText("Widgets", style = ts(p.ink, 34.sp), modifier = Modifier.weight(1f))
                if (ids.isNotEmpty()) {
                    BasicText(
                        if (editing) "Done" else "Edit",
                        style = ts(p.accent, 16.sp, FontWeight.Medium),
                        modifier = Modifier
                            .heightIn(min = 52.dp)
                            .softClick(role = Role.Button) { editing = !editing }
                            .wrapContentHeight(Alignment.CenterVertically)
                            .padding(horizontal = 16.dp),
                    )
                }
                BasicText(
                    "Add",
                    style = ts(p.accent, 16.sp, FontWeight.Medium),
                    modifier = Modifier
                        .heightIn(min = 52.dp)
                        .softClick(role = Role.Button) { choosing = true }
                        .wrapContentHeight(Alignment.CenterVertically),
                )
            }
            if (ids.isEmpty()) {
                BasicText(
                    "No widgets yet. Tap Add to pick one.",
                    style = ts(p.muted, 15.sp, FontWeight.Normal),
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
            if (editing && ids.isNotEmpty()) {
                BasicText(
                    "Drag the handles on a widget to resize it.",
                    style = ts(p.muted, 14.sp, FontWeight.Normal),
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            LazyColumn(state = listState, userScrollEnabled = !innerScrolling, contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp)) {
                items(ids, key = { it }) { id ->
                    val info = manager.getAppWidgetInfo(id)
                    if (info == null) {
                        LaunchedEffect(id) { dropId(id) }
                    } else {
                        val defaultHeight = with(density) { info.minHeight.toDp() }.value.toInt().coerceAtLeast(96)
                        val heightDp = heights[id] ?: defaultHeight
                        val widthPct = widths[id] ?: 100
                        val widgetWidthDp = (screenWidthDp * widthPct / 100).coerceAtLeast(40)
                        val startSize = remember(id) { floatArrayOf(0f) }
                        Column {
                            Box(
                                Modifier
                                    .fillMaxWidth(widthPct / 100f)
                                    .height(heightDp.dp),
                            ) {
                                AndroidView(
                                    factory = { c ->
                                        ScrollPassthroughFrame(c).apply {
                                            addView(
                                                host.createView(c, id, info),
                                                android.view.ViewGroup.LayoutParams(
                                                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                                ),
                                            )
                                        }
                                    },
                                    update = { v ->
                                        v.onScrollBy = { dy -> listState.dispatchRawDelta(dy) }
                                        v.onInnerScroll = { innerScrolling = it }
                                        v.onFling = { vy ->
                                            scope.launch { listState.scroll { with(fling) { performFling(vy) } } }
                                        }
                                        (v.getChildAt(0) as? AppWidgetHostView)
                                            ?.updateAppWidgetSize(Bundle(), widgetWidthDp, heightDp, widgetWidthDp, heightDp)
                                    },
                                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(24.dp)),
                                )
                                if (editing) {
                                    Box(Modifier.fillMaxSize().border(2.dp, p.accent, RoundedCornerShape(24.dp)))
                                    ResizeHandle(
                                        horizontal = false,
                                        p = p,
                                        description = "Drag to change widget height",
                                        onStart = { startSize[0] = heightDp.toFloat() },
                                        onTotal = { px ->
                                            heights[id] = (startSize[0] + px / density.density).toInt().coerceIn(64, 640)
                                        },
                                        onEnd = {
                                            appPrefs(ctx).edit().putInt("widget_h_$id", heights[id] ?: heightDp).apply()
                                        },
                                        modifier = Modifier.align(Alignment.BottomCenter),
                                    )
                                    ResizeHandle(
                                        horizontal = true,
                                        p = p,
                                        description = "Drag to change widget width",
                                        onStart = { startSize[0] = widgetWidthDp.toFloat() },
                                        onTotal = { px ->
                                            val w = startSize[0] + px / density.density
                                            widths[id] = (w / screenWidthDp * 100f).toInt().coerceIn(40, 100)
                                        },
                                        onEnd = {
                                            appPrefs(ctx).edit().putInt("widget_w_$id", widths[id] ?: widthPct).apply()
                                        },
                                        modifier = Modifier.align(Alignment.CenterEnd),
                                    )
                                }
                            }
                            if (editing) {
                                BasicText(
                                    "Remove",
                                    style = ts(p.accent, 15.sp, FontWeight.Medium),
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .softClick(role = Role.Button) { dropId(id) }
                                        .wrapContentHeight(Alignment.CenterVertically),
                                )
                            }
                            Spacer(Modifier.height(16.dp))
                        }
                    }
                }
            }
        }

        AnimatedVisibility(visible = choosing, enter = pushEnter(), exit = pushExit()) {
            val pm = ctx.packageManager
            val dpi = ctx.resources.displayMetrics.density
            // One entry per app; apps with several widgets expand to show them.
            val groups = remember {
                manager.installedProviders
                    .groupBy { it.provider.packageName }
                    .map { (pkg, list) ->
                        val appLabel = try {
                            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
                        } catch (_: Exception) {
                            list.first().loadLabel(pm).toString()
                        }
                        Triple(pkg, appLabel, list.sortedBy { it.loadLabel(pm).toString().lowercase() })
                    }
                    .sortedBy { it.second.lowercase() }
            }
            var expanded by remember { mutableStateOf<String?>(null) }
            var query by remember { mutableStateOf("") }

            fun detail(info: AppWidgetProviderInfo): String {
                val cw = if (info.targetCellWidth > 0) info.targetCellWidth
                else (((info.minWidth / dpi) + 30) / 70).toInt().coerceAtLeast(1)
                val ch = if (info.targetCellHeight > 0) info.targetCellHeight
                else (((info.minHeight / dpi) + 30) / 70).toInt().coerceAtLeast(1)
                val desc = try { info.loadDescription(ctx)?.toString() } catch (_: Exception) { null }
                return listOfNotNull("$cw \u00D7 $ch", desc?.takeIf { it.isNotBlank() }).joinToString(" \u00B7 ")
            }

            Column(
                Modifier
                    .fillMaxSize()
                    .background(p.bg)
                    .windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout))
                    .imePadding()
                    .padding(horizontal = 32.dp)
                    .pointerInput(Unit) { detectTapGestures { } },
            ) {
                BasicText(
                    "Cancel",
                    style = ts(p.accent, 16.sp, FontWeight.Medium),
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .heightIn(min = 52.dp)
                        .softClick(role = Role.Button) { choosing = false }
                        .wrapContentHeight(Alignment.CenterVertically),
                )
                BasicText("Add a widget", style = ts(p.ink, 28.sp))
                val q = query.trim()
                val visible = remember(q, groups) {
                    if (q.isEmpty()) groups
                    else groups.mapNotNull { g ->
                        if (g.second.contains(q, ignoreCase = true)) g
                        else {
                            val hits = g.third.filter { it.loadLabel(pm).toString().contains(q, ignoreCase = true) }
                            if (hits.isEmpty()) null else Triple(g.first, g.second, hits)
                        }
                    }
                }
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(top = 12.dp, bottom = 12.dp)) {
                    items(visible, key = { it.first }) { (pkg, appLabel, list) ->
                        // Every app is a drop-down, even with a single widget.
                        val open = expanded == pkg || (q.isNotEmpty() && !appLabel.contains(q, ignoreCase = true))
                        Column {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 56.dp)
                                    .softClick(role = Role.Button) { expanded = if (expanded == pkg) null else pkg }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    BasicText(appLabel, maxLines = 1, overflow = TextOverflow.Ellipsis, style = ts(p.ink, 20.sp))
                                    BasicText(
                                        if (list.size == 1) "1 widget" else "${list.size} widgets",
                                        style = ts(p.muted, 13.sp, FontWeight.Normal),
                                    )
                                }
                                Chevron(open, p.accent)
                            }
                            AnimatedVisibility(
                                visible = open,
                                enter = expandVertically(tw(260, easing = EmphasizedDecel)) + fadeIn(tw(190)),
                                exit = shrinkVertically(tw(190, easing = EmphasizedAccel)) + fadeOut(tw(100)),
                            ) {
                              Column {
                                list.forEach { info ->
                                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(start = 8.dp)) {
                                        Box(Modifier.width(2.dp).fillMaxHeight().background(p.accent.copy(alpha = 0.5f)))
                                        Column(
                                            Modifier
                                                .fillMaxWidth()
                                                .heightIn(min = 52.dp)
                                                .softClick(role = Role.Button) { pick(info) }
                                                .padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
                                        ) {
                                            BasicText(
                                                info.loadLabel(pm).toString(),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                style = ts(p.ink, 18.sp),
                                            )
                                            BasicText(detail(info), maxLines = 2, overflow = TextOverflow.Ellipsis, style = ts(p.muted, 13.sp, FontWeight.Normal))
                                            WidgetPreview(info, ctx)
                                        }
                                    }
                                }
                              }
                            }
                        }
                    }
                }
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
                        modifier = Modifier.weight(1f),
                        decorationBox = { inner ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (query.isEmpty()) BasicText("Search widgets", style = ts(p.muted, 16.sp, FontWeight.Normal))
                                inner()
                            }
                        },
                    )
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

// ---------- Home status line (shown when the system status bar is hidden) ----------

@Composable
private fun rememberStatus(enabled: Boolean): State<SysStatus> {
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
private fun BatteryIcon(percent: Int, outline: Color, fill: Color, charging: Boolean = false, bolt: Color = Color.White, halo: Color = Color.Black) {
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
