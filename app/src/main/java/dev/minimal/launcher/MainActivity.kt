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
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.NotificationManagerCompat
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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

private const val MAX_FAVORITES = 6

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

private fun openApp(ctx: Context, app: App) {
    val intent = Intent(Intent.ACTION_MAIN)
        .addCategory(Intent.CATEGORY_LAUNCHER)
        .setComponent(app.component)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
    val v = launchView
    val options = if (v != null) {
        android.app.ActivityOptions.makeScaleUpAnimation(
            v, lastTouch.x.toInt() - 48, lastTouch.y.toInt() - 48, 96, 96,
        ).toBundle()
    } else null
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
        Toast.makeText(ctx, "Turn on Minimal gestures in Accessibility settings", Toast.LENGTH_LONG).show()
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
    val drawerSpec = remember { spring<Float>(dampingRatio = 0.9f, stiffness = 420f) }
    LaunchedEffect(drawerOpen) { drawerP.animateTo(if (drawerOpen) 1f else 0f, drawerSpec) }
    val widgetsP = remember { Animatable(0f) }
    LaunchedEffect(widgetsOpen) { widgetsP.animateTo(if (widgetsOpen) 1f else 0f, drawerSpec) }
    val widgetsVisible by remember { derivedStateOf { widgetsP.value > 0.001f } }
    val drawerVisible by remember { derivedStateOf { drawerP.value > 0.001f } }

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
        if (!pinned && current.size >= MAX_FAVORITES) {
            Toast.makeText(ctx, "Home fits $MAX_FAVORITES favorites. Remove one in Settings.", Toast.LENGTH_SHORT).show()
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
        LaunchedEffect(resumes) { launching = false }
        LaunchedEffect(launching) { if (launching) { delay(2500); launching = false } }
        val zoom by animateFloatAsState(
            if (launching) 0.9f else 1f,
            tween(if (launching) 280 else 380, easing = EmphasizedDecel), label = "zoom",
        )
        val fade by animateFloatAsState(
            if (launching) 0f else 1f,
            tween(if (launching) 220 else 300, easing = EmphasizedDecel), label = "fade",
        )
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { scaleX = zoom; scaleY = zoom; alpha = fade }
                .background(p.bg)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val d = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        lastTouch = d.position
                    }
                },
        ) {
            Box(Modifier.fillMaxSize().graphicsLayer {
                val s = 1f - 0.05f * drawerP.value
                scaleX = s; scaleY = s
                translationX = widgetsP.value * size.width * 0.22f
                alpha = 1f - 0.5f * drawerP.value - 0.5f * widgetsP.value
            }) {
            Home(
                p, cfg, favorites, weather, event,
                onOpenDrawer = { drawerOpen = true },
                onDrawerDrag = { f -> pageScope.launch { drawerP.snapTo(f.coerceIn(0f, 1f)) } },
                onDrawerEnd = { velocity ->
                    val open = drawerP.value > 0.25f && velocity > -200f || velocity < -900f
                    drawerOpen = open
                    pageScope.launch { drawerP.animateTo(if (open) 1f else 0f, drawerSpec, initialVelocity = 0f) }
                },
                drawerProgress = { drawerP.value },
                onOpenSearch = { searchOpen = true },
                onSettings = { settingsOpen = true },
                onSleep = { globalAction(ctx, AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN) },
                onNotifications = { globalAction(ctx, AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS) },
                gesturesEnabled = !drawerOpen && !settingsOpen && !searchOpen && !widgetsOpen,
                onLaunch = { openApp(ctx, it) },
                onSlot = openSlot,
                onOpenWidgets = { widgetsOpen = true },
                onWidgetsDrag = { f -> pageScope.launch { widgetsP.snapTo(f.coerceIn(0f, 1f)) } },
                onWidgetsEnd = { v ->
                    val open = if (v > 900f) true else if (v < -900f) false else widgetsP.value > 0.5f
                    widgetsOpen = open
                    pageScope.launch { widgetsP.animateTo(if (open) 1f else 0f, drawerSpec) }
                },
            )
            }
            if (widgetsVisible) {
                Box(Modifier.fillMaxSize().graphicsLayer { translationX = -(1f - widgetsP.value) * size.width }) {
                    WidgetsScreen(
                        p,
                        onClose = { widgetsOpen = false },
                        onDrag = { f -> pageScope.launch { widgetsP.snapTo(f.coerceIn(0f, 1f)) } },
                        onEnd = { v ->
                            val open = if (v > 900f) true else if (v < -900f) false else widgetsP.value > 0.5f
                            widgetsOpen = open
                            pageScope.launch { widgetsP.animateTo(if (open) 1f else 0f, drawerSpec) }
                        },
                    )
                }
            }
            if (drawerVisible) {
                // A page pulled up over Home.
                Box(Modifier.fillMaxSize().graphicsLayer { translationY = (1f - drawerP.value) * size.height }) {
                    Drawer(
                        p, apps, favorites.map { it.key }.toSet(),
                        progress = { drawerP.value },
                        onLaunch = { openApp(ctx, it) },
                        onTogglePin = togglePin,
                        onClose = { drawerOpen = false },
                    )
                }
            }
            AnimatedVisibility(
                visible = searchOpen,
                enter = screenEnter,
                exit = screenExit,
            ) {
                SearchScreen(p, cfg, apps, onClose = { searchOpen = false })
            }
            AnimatedVisibility(
                visible = settingsOpen,
                enter = screenEnter,
                exit = screenExit,
            ) {
                SettingsScreen(
                    p, cfg, updateCfg,
                    favorites = favorites, apps = apps, onSetFavs = setFavs,
                    onPickSlot = { pickSlot = it },
                    onBack = { settingsOpen = false },
                )
            }
            var lastSlot by remember { mutableStateOf<Slot?>(null) }
            if (pickSlot != null) lastSlot = pickSlot
            AnimatedVisibility(visible = pickSlot != null, enter = pushEnter, exit = pushExit) {
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
            BasicText(f(if (is24) "HH" else "h"), style = ts(p.ink, 88.sp, FontWeight.Light, (-2).sp, 84.sp))
            BasicText(f("mm"), style = ts(p.accent, 88.sp, FontWeight.Light, (-2).sp, 84.sp))
        }
    }
}

private val EmphasizedDecel = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
private val EmphasizedAccel = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

// Full screens (Settings, Search): a gentle rise with a soft zoom.
private val screenEnter = slideInVertically(tween(380, easing = EmphasizedDecel)) { it / 14 } +
    scaleIn(tween(380, easing = EmphasizedDecel), initialScale = 0.95f) + fadeIn(tween(260, easing = EmphasizedDecel))
private val screenExit = slideOutVertically(tween(240, easing = EmphasizedAccel)) { it / 14 } +
    scaleOut(tween(240, easing = EmphasizedAccel), targetScale = 0.97f) + fadeOut(tween(180, easing = EmphasizedAccel))

// Sub-menus pushed on top of a screen: slide in from the right.
private val pushEnter = slideInHorizontally(tween(360, easing = EmphasizedDecel)) { it / 3 } + fadeIn(tween(240, easing = EmphasizedDecel))
private val pushExit = slideOutHorizontally(tween(260, easing = EmphasizedAccel)) { it / 3 } + fadeOut(tween(180, easing = EmphasizedAccel))

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.softCombined(onClick: () -> Unit, onLongClick: (() -> Unit)?): Modifier = composed {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val a by animateFloatAsState(if (pressed) 0.5f else 1f, tween(if (pressed) 60 else 240), label = "press")
    this.graphicsLayer { alpha = a }.combinedClickable(
        interactionSource = src, indication = null, role = Role.Button, onClick = onClick, onLongClick = onLongClick,
    )
}

/** A tap target that dims a little while pressed, then eases back. */
private fun Modifier.softClick(role: Role? = null, onClick: () -> Unit): Modifier = composed {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val a by animateFloatAsState(if (pressed) 0.5f else 1f, tween(if (pressed) 60 else 240), label = "press")
    this.graphicsLayer { alpha = a }.clickable(interactionSource = src, indication = null, role = role, onClick = onClick)
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
    onSettings: () -> Unit,
    onSleep: () -> Unit,
    onNotifications: () -> Unit,
    gesturesEnabled: Boolean,
    onLaunch: (App) -> Unit,
    onSlot: (Slot) -> Unit,
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
    val curDrag by rememberUpdatedState(onDrawerDrag)
    val curEnd by rememberUpdatedState(onDrawerEnd)
    val curNotifications by rememberUpdatedState(onNotifications)
    val curWidgets by rememberUpdatedState(onOpenWidgets)
    val showBatteryLine = cfg.hideStatusBar && cfg.showBattery
    val status by rememberStatus(showBatteryLine)

    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout))
            .padding(horizontal = 32.dp)
            // Blank space: double tap = sleep, long press = settings.
            .pointerInput(gesturesEnabled) {
                if (gesturesEnabled) {
                    detectTapGestures(onDoubleTap = { onSleep() }, onLongPress = { onSettings() })
                }
            }
            // One detector for all swipes so they can't steal from each other:
            // down = notifications, up = app list, right = widgets page (if enabled).
            // It watches without consuming and fires as soon as the swipe is clearly intentional.
            .pointerInput(gesturesEnabled, cfg.widgetsOn) {
                if (!gesturesEnabled) return@pointerInput
                awaitEachGesture {
                    val first = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    val startTime = first.uptimeMillis
                    val tracker = androidx.compose.ui.input.pointer.util.VelocityTracker()
                    tracker.addPosition(first.uptimeMillis, first.position)
                    var dx = 0f
                    var dy = 0f
                    var held = false
                    var axis = 0 // 0 = undecided, 1 = pulling the app list up, 2 = pulling the widgets page in
                    var swallow = false
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == first.id } ?: break
                        if (!change.pressed) break
                        tracker.addPosition(change.uptimeMillis, change.position)
                        val delta = change.positionChange()
                        dx += delta.x
                        dy += delta.y
                        if (axis != 0 || swallow) {
                            change.consume() // the rest of this gesture belongs to the swipe, not the app under it
                            if (axis == 1) curDrag(-dy / size.height)
                            if (axis == 2) curWDrag(dx / size.width)
                            continue
                        }
                        // A press-and-hold is a long press (Settings), not a swipe.
                        if (change.uptimeMillis - startTime >= 450L && abs(dx) + abs(dy) < 24f) held = true
                        if (held) continue
                        if (dy < -36f && abs(dy) > 1.5f * abs(dx)) {
                            axis = 1
                            change.consume()
                            curDrag(-dy / size.height)
                        } else if (cfg.widgetsOn && dx > 28f && dx > 1.2f * abs(dy)) {
                            axis = 2
                            change.consume()
                            curWDrag(dx / size.width)
                        } else if (dy > 90f && dy > 1.5f * abs(dx)) {
                            change.consume()
                            curNotifications()
                            swallow = true
                        }
                    }
                    if (axis == 1) curEnd(tracker.calculateVelocity().y)
                    if (axis == 2) curWEnd(tracker.calculateVelocity().x)
                }
            },
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
            Box(Modifier.softClick(role = Role.Button) { onSlot(Slot.CLOCK) }) {
                ClockText(p, cfg.clock, now, is24)
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
                        BatteryIcon(status.battery, p.muted, fill)
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
        favorites.forEach {
            AppRow(
                it, 30.sp, p.ink, onClick = { onLaunch(it) }, weight = FontWeight.Normal,
                marker = if (cfg.notifDots && it.component.packageName in NotifState.active) p.accent else null,
            )
        }
        Spacer(Modifier.height(32.dp))
        Pill(p, Modifier.graphicsLayer { alpha = (1f - drawerProgress() * 4f).coerceIn(0f, 1f) }.softClick(role = Role.Button, onClick = onOpenSearch)) {
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
    progress: () -> Float,
    onLaunch: (App) -> Unit,
    onTogglePin: (App) -> Unit,
    onClose: () -> Unit,
) {
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
    val haptic = LocalHapticFeedback.current
    var activeLetter by remember { mutableStateOf<Char?>(null) }
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
            .windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout))
            .imePadding()
            .padding(horizontal = 32.dp)
            // Close the drawer with a clear vertical drag that starts and ends with the list at its
            // top (drag down) or bottom (drag up). Watches without consuming, so scrolling is unaffected.
            .pointerInput(listState, letters.size) {
                val stripPx = 28.dp.toPx()
                awaitEachGesture {
                    val first = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    val startedAtTop = !listState.canScrollBackward
                    val startedAtBottom = !listState.canScrollForward
                    val inStrip = letters.size > 1 && first.position.x > size.width - stripPx
                    var dx = 0f
                    var dy = 0f
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == first.id } ?: break
                        if (!change.pressed) break
                        val delta = change.positionChange()
                        dx += delta.x
                        dy += delta.y
                    }
                    if (!inStrip && abs(dy) > 140f && abs(dy) > 2f * abs(dx)) {
                        if (dy > 0f && startedAtTop && !listState.canScrollBackward) onClose()
                    }
                }
            },
    ) {
        Spacer(Modifier.height(24.dp))
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
        Spacer(Modifier.height(8.dp))
        Box(Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(
                Modifier.fillMaxSize(),
                state = listState,
                contentPadding = PaddingValues(end = 28.dp, bottom = 24.dp),
            ) {
                groups.forEach { (letter, list) ->
                    stickyHeader(key = "h$letter", contentType = "header") {
                        Box(Modifier.fillMaxWidth().background(p.bg).padding(top = 12.dp, bottom = 4.dp)) {
                            BasicText(letter.toString(), style = ts(p.accent, 13.sp, FontWeight.Medium, 1.sp))
                        }
                    }
                    items(list, key = { it.key }, contentType = { "app" }) { app ->
                        val isPinned = app.key in pinned
                        AppRow(
                            app, 22.sp, p.ink,
                            onClick = { onLaunch(app) },
                            onLongClick = { onTogglePin(app) },
                            weight = if (isPinned) FontWeight.Medium else FontWeight.Light,
                            marker = if (isPinned) p.accent else null,
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
    }
}

// ---------- Universal search ----------

@Composable
private fun SearchScreen(p: Palette, cfg: Config, apps: List<App>, onClose: () -> Unit) {
    val ctx = LocalContext.current
    var query by remember { mutableStateOf("") }
    var contacts by remember { mutableStateOf(emptyList<ContactHit>()) }
    var files by remember { mutableStateOf(emptyList<FileHit>()) }
    val focus = remember { FocusRequester() }
    val q = query.trim()

    val appHits = remember(apps, q, cfg.searchApps) {
        if (!cfg.searchApps || q.isEmpty()) emptyList<App>()
        else apps.filter { it.label.contains(q, ignoreCase = true) }.take(6)
    }

    LaunchedEffect(Unit) { focus.requestFocus() }
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
            .windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout))
            .imePadding()
            .padding(horizontal = 32.dp)
            .pointerInput(Unit) {
                var total = 0f
                detectVerticalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = { if (total > 120f) onClose() },
                    onDragCancel = {},
                    onVerticalDrag = { _, dy -> total += dy },
                )
            },
    ) {
        Spacer(Modifier.height(24.dp))
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
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
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
    onBack: () -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val now by rememberNow()
    var adding by remember { mutableStateOf(false) }
    var cityInput by remember { mutableStateOf(cfg.city) }
    val favKeys = favorites.map { it.key }
    val full = favKeys.size >= MAX_FAVORITES
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

    Column(
        Modifier
            .fillMaxSize()
            .background(p.bg)
            .windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout))
            .padding(horizontal = 32.dp),
    ) {
        BasicText(
            "Back",
            style = ts(p.accent, 15.sp, FontWeight.Medium),
            modifier = Modifier
                .padding(top = 8.dp)
                .heightIn(min = 48.dp)
                .softClick(role = Role.Button, onClick = onBack)
                .wrapContentHeight(Alignment.CenterVertically),
        )
        BasicText("Settings", style = ts(p.ink, 34.sp))
        LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
            // Clock
            item { SectionTitle("Clock style", p) }
            items(ClockStyle.entries) { s ->
                OptionRow(s.label, s == cfg.clock, p) { onConfig(cfg.copy(clock = s)) }
            }
            item { SectionTitle("Time format", p) }
            items(HourFormat.entries) { h ->
                OptionRow(h.label, h == cfg.hours, p) { onConfig(cfg.copy(hours = h)) }
            }
            item { SectionTitle("Date format", p) }
            items(DateStyle.entries) { d ->
                val pattern = d.pattern
                val label = if (pattern == null) "Hidden" else now.format(DateTimeFormatter.ofPattern(pattern))
                OptionRow(label, d == cfg.date, p) { onConfig(cfg.copy(date = d)) }
            }

            // Weather
            item { SectionTitle("Weather", p) }
            item { SwitchRow("Show weather on Home", cfg.weatherOn, p) { onConfig(cfg.copy(weatherOn = it)) } }
            item {
                Pill(p, Modifier.padding(vertical = 4.dp).alpha(if (cfg.weatherLocation) 0.4f else 1f)) {
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
            }
            if (!cfg.weatherLocation) item {
                BasicText(
                    if (cfg.city.isBlank()) "Type a city and tap Set. Only the city name is sent, to Open-Meteo."
                    else "Showing weather for ${cfg.city}.",
                    style = ts(p.muted, 14.sp, FontWeight.Normal),
                )
            }
            item {
                SwitchRow("Use my current location", cfg.weatherLocation, p) { on ->
                    if (on && !has(Manifest.permission.ACCESS_COARSE_LOCATION)) locationPerm.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                    else onConfig(cfg.copy(weatherLocation = on, weatherOn = cfg.weatherOn || on))
                }
            }
            if (cfg.weatherLocation) {
                item {
                    BasicText(
                        "Using your approximate location. Only coordinates are sent, to Open-Meteo.",
                        style = ts(p.muted, 14.sp, FontWeight.Normal),
                    )
                }
            }
            item { SwitchRow("Use Fahrenheit", cfg.fahrenheit, p) { onConfig(cfg.copy(fahrenheit = it)) } }

            // Calendar
            item { SectionTitle("Calendar", p) }
            item {
                SwitchRow("Show next event on Home", cfg.calendarOn, p) { on ->
                    if (on && !has(Manifest.permission.READ_CALENDAR)) calendarPerm.launch(Manifest.permission.READ_CALENDAR)
                    else onConfig(cfg.copy(calendarOn = on))
                }
            }

            // Status bar
            item { SectionTitle("Status bar", p) }
            item { SwitchRow("Hide status bar", cfg.hideStatusBar, p) { onConfig(cfg.copy(hideStatusBar = it)) } }
            if (cfg.hideStatusBar) {
                item { SwitchRow("Battery on Home", cfg.showBattery, p) { onConfig(cfg.copy(showBattery = it)) } }
                item {
                    BasicText(
                        "The battery level shows next to the weather on Home. Swipe down from the very top edge to peek at the real status bar.",
                        style = ts(p.muted, 14.sp, FontWeight.Normal),
                    )
                }
            }

            // Widgets page
            item { SectionTitle("Widgets page", p) }
            item { SwitchRow("Page to the left of Home", cfg.widgetsOn, p) { onConfig(cfg.copy(widgetsOn = it)) } }
            item {
                BasicText(
                    "Swipe right on Home to open it. Add and remove widgets there.",
                    style = ts(p.muted, 14.sp, FontWeight.Normal),
                )
            }

            // Notification dots
            item { SectionTitle("Notification dots", p) }
            item {
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
            item {
                BasicText(
                    if (notifGranted) "Notification access is on. Minimal only checks which apps have a notification and never reads its content."
                    else "Needs notification access. Turn on Minimal notification dots in the list that opens. On Android 13+ you may first need App info > three dots > Allow restricted settings.",
                    style = ts(p.muted, 14.sp, FontWeight.Normal),
                )
            }

            // Tap actions
            item { SectionTitle("Tap actions", p) }
            items(Slot.entries) { slot ->
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
                }
            }

            // Search
            item { SectionTitle("Search", p) }
            item { SwitchRow("Apps", cfg.searchApps, p) { onConfig(cfg.copy(searchApps = it)) } }
            item {
                SwitchRow("Contacts", cfg.searchContacts, p) { on ->
                    if (on && !has(Manifest.permission.READ_CONTACTS)) contactsPerm.launch(Manifest.permission.READ_CONTACTS)
                    else onConfig(cfg.copy(searchContacts = on))
                }
            }
            item {
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
            }
            item { SwitchRow("Web search", cfg.searchWeb, p) { onConfig(cfg.copy(searchWeb = it)) } }
            item {
                BasicText(
                    "Search engine",
                    style = ts(p.muted, 14.sp, FontWeight.Normal),
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            items(Engine.entries) { e ->
                OptionRow(e.label, e == cfg.engine, p) { onConfig(cfg.copy(engine = e)) }
            }

            // Font
            item { SectionTitle("Font", p) }
            items(AppFont.entries) { f ->
                OptionRow(f.label, f == cfg.font, p, family = f.family) { onConfig(cfg.copy(font = f)) }
            }

            // Favorites
            item { SectionTitle("Favorites", p) }
            if (favorites.isEmpty()) {
                item { BasicText("No favorites yet. Add some below.", style = ts(p.muted, 15.sp, FontWeight.Normal)) }
            }
            itemsIndexed(favorites, key = { _, a -> a.key }) { i, app ->
                Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
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
            item {
                BasicText(
                    if (adding) "Done" else if (full) "Home fits $MAX_FAVORITES favorites" else "Add app",
                    style = ts(if (full && !adding) p.muted else p.accent, 16.sp, FontWeight.Medium),
                    modifier = Modifier
                        .heightIn(min = 52.dp)
                        .clickable(enabled = adding || !full, role = Role.Button) { adding = !adding }
                        .wrapContentHeight(Alignment.CenterVertically),
                )
            }
            if (adding) {
                items(addable, key = { "add" + it.key }) { app ->
                    AppRow(app, 20.sp, p.ink, onClick = {
                        onSetFavs(favKeys + app.key)
                        if (favKeys.size + 1 >= MAX_FAVORITES) adding = false
                    })
                }
            }

            // Gestures (kept at the bottom)
            item { SectionTitle("Gestures", p) }
            item {
                BasicText(
                    "On Home: double tap a blank spot to sleep, long-press it to open Settings, swipe down for notifications, swipe up for your apps. Sleep and notifications need the Minimal gestures accessibility service.",
                    style = ts(p.muted, 15.sp, FontWeight.Normal),
                )
            }
            item {
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
        }
    }
}

// ---------- Pieces ----------

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
        val t by animateFloatAsState(if (checked) 1f else 0f, spring(dampingRatio = 0.8f, stiffness = 500f), label = "switch")
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
    val color by animateColorAsState(if (selected) p.accent else p.muted, tween(200), label = "radioColor")
    val dot by animateFloatAsState(if (selected) 0.24f else 0f, spring(dampingRatio = 0.7f, stiffness = 600f), label = "radioDot")
    Canvas(Modifier.size(20.dp)) {
        val s = size.minDimension
        drawCircle(color, radius = s * 0.45f, style = Stroke(s * 0.1f))
        if (dot > 0.001f) drawCircle(p.accent, radius = s * dot)
    }
}

private enum class Glyph { UP, DOWN, CLOSE }

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
                Glyph.CLOSE -> { line(Offset(s * .2f, s * .2f), Offset(s * .8f, s * .8f)); line(Offset(s * .8f, s * .2f), Offset(s * .2f, s * .8f)) }
            }
        }
    }
}

@Composable
private fun Pill(p: Palette, modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(p.pill, RoundedCornerShape(28.dp))
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
) {
    Row(
        Modifier
            .fillMaxWidth()
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
    val rot by animateFloatAsState(if (up) 180f else 0f, tween(280, easing = EmphasizedDecel), label = "chevron")
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
                .windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout))
                .padding(horizontal = 32.dp)
                // Swipe left to push the page back off to the left. Follows the finger; widgets keep their touches
                // unless the drag is clearly horizontal.
                .pointerInput(editing) {
                    if (editing) return@pointerInput
                    awaitEachGesture {
                        val first = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        val tracker = androidx.compose.ui.input.pointer.util.VelocityTracker()
                        tracker.addPosition(first.uptimeMillis, first.position)
                        var dx = 0f
                        var dy = 0f
                        var pulling = false
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == first.id } ?: break
                            if (!change.pressed) break
                            tracker.addPosition(change.uptimeMillis, change.position)
                            val d = change.positionChange()
                            dx += d.x; dy += d.y
                            if (!pulling && !innerScrolling && dx < -36f && abs(dx) > 2f * abs(dy)) pulling = true
                            if (pulling) {
                                change.consume()
                                curDrag(1f + dx / size.width)
                            }
                        }
                        if (pulling) curEnd(tracker.calculateVelocity().x)
                    }
                },
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

        AnimatedVisibility(visible = choosing, enter = pushEnter, exit = pushExit) {
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
                                enter = expandVertically(tween(320, easing = EmphasizedDecel)) + fadeIn(tween(240)),
                                exit = shrinkVertically(tween(240, easing = EmphasizedAccel)) + fadeOut(tween(120)),
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
                delay(10_000L)
            }
        }
    }
    return status
}

/** Battery glyph drawn on whole device pixels so edges stay crisp on any density. */
@Composable
private fun BatteryIcon(percent: Int, outline: Color, fill: Color) {
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
    }
}
