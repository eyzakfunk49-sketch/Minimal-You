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
import androidx.compose.ui.draw.blur
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

/** Home fits 7 favorites, or 9 when the clock is hidden and frees up the space. */
internal fun maxFavorites(cfg: Config) = if (cfg.showClock) 7 else 9

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
internal var launchView: android.view.View? = null

/** Where the app name you just tapped is on screen, so the app can open from it. Set by [AppRow]. */
internal var tappedTextBounds: androidx.compose.ui.geometry.Rect? = null
internal var lastTouch = Offset.Zero
internal var launching by mutableStateOf(false)
/** Finger positions in window coordinates, so a page that moves under the finger doesn't distort the drag. */
internal class RootTracker {
    var coords: androidx.compose.ui.layout.LayoutCoordinates? = null
    fun toRoot(p: androidx.compose.ui.geometry.Offset): androidx.compose.ui.geometry.Offset {
        val c = coords
        return if (c != null && c.isAttached) c.localToRoot(p) else p
    }
}

internal var moveEdgeExclusion: (Float) -> Unit = {}

/** Kept in step with Settings so [openApp] knows which apps need a fingerprint first. */
internal var appLockOn = false
internal var lockedKeys: Set<String> = emptySet()

/** Asks for a fingerprint (or the screen lock) and runs [onOk] only if it succeeds. */
internal fun authenticate(ctx: Context, title: String, onOk: () -> Unit) {
    val auth = android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_STRONG or
        android.hardware.biometrics.BiometricManager.Authenticators.DEVICE_CREDENTIAL
    val bm = ctx.getSystemService(android.hardware.biometrics.BiometricManager::class.java)
    if (bm == null || bm.canAuthenticate(auth) != android.hardware.biometrics.BiometricManager.BIOMETRIC_SUCCESS) {
        Toast.makeText(ctx, "Set up a fingerprint or screen lock in system settings to use app locking", Toast.LENGTH_LONG).show()
        return
    }
    try {
        android.hardware.biometrics.BiometricPrompt.Builder(ctx)
            .setTitle(title)
            .setAllowedAuthenticators(auth)
            .build()
            .authenticate(
                android.os.CancellationSignal(), ctx.mainExecutor,
                object : android.hardware.biometrics.BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: android.hardware.biometrics.BiometricPrompt.AuthenticationResult?) { onOk() }
                },
            )
    } catch (_: Exception) {
        Toast.makeText(ctx, "Couldn't ask for your fingerprint", Toast.LENGTH_SHORT).show()
    }
}

/** Starts a timer in the clock app, silently when the clock app allows it. */
internal fun startTimer(ctx: Context, minutes: Int) {
    fun timerIntent(skipUi: Boolean) = Intent(android.provider.AlarmClock.ACTION_SET_TIMER)
        .putExtra(android.provider.AlarmClock.EXTRA_LENGTH, minutes * 60)
        .putExtra(android.provider.AlarmClock.EXTRA_MESSAGE, "Timer")
        .putExtra(android.provider.AlarmClock.EXTRA_SKIP_UI, skipUi)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    var error: String? = null
    // Some clock apps refuse to run silently, so fall back to showing their timer screen.
    for (skip in listOf(true, false)) {
        try {
            ctx.startActivity(timerIntent(skip))
            Toast.makeText(ctx, "Timer set for ${if (minutes == 60) "1 hour" else "$minutes min"}", Toast.LENGTH_SHORT).show()
            return
        } catch (e: Exception) {
            error = e.javaClass.simpleName + (e.message?.let { ": " + it.take(80) } ?: "")
        }
    }
    Toast.makeText(ctx, "Couldn't start a timer ($error)", Toast.LENGTH_LONG).show()
}

internal const val NEW_FOLDER = "\u0000new"

internal fun openApp(ctx: Context, app: App) {
    if (appLockOn && app.key in lockedKeys) authenticate(ctx, "Unlock ${app.label}") { launchApp(ctx, app) }
    else launchApp(ctx, app)
}

private fun launchApp(ctx: Context, app: App) {
    recordLaunch(ctx, app.key)
    val intent = Intent(Intent.ACTION_MAIN)
        .addCategory(Intent.CATEGORY_LAUNCHER)
        .setComponent(app.component)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
    val v = launchView
    // The app opens out of the name you tapped (or the spot you touched, for rows we have no bounds for).
    val b = tappedTextBounds
    tappedTextBounds = null
    val left = (b?.left ?: (lastTouch.x - 80f)).toInt()
    val top = (b?.top ?: (lastTouch.y - 24f)).toInt()
    val width = (b?.width ?: 160f).toInt().coerceAtLeast(1)
    val height = (b?.height ?: 48f).toInt().coerceAtLeast(1)
    val options = if (!animationsOn) {
        android.app.ActivityOptions.makeCustomAnimation(ctx, 0, 0).toBundle()
    } else if (v != null) {
        android.app.ActivityOptions.makeScaleUpAnimation(v, left, top, width, height).toBundle()
    } else null
    launching = true
    try {
        // Starting through LauncherApps is how a home app launches an app, and it hands the system the
        // source rectangle, so the system draws its launch animation from there instead of sliding in from the side.
        val la = ctx.getSystemService(android.content.pm.LauncherApps::class.java)
        val loc = IntArray(2)
        v?.getLocationOnScreen(loc)
        val src = android.graphics.Rect(left + loc[0], top + loc[1], left + loc[0] + width, top + loc[1] + height)
        la.startMainActivity(app.component, android.os.Process.myUserHandle(), src, options)
    } catch (_: Exception) {
        try { ctx.startActivity(intent, options) } catch (_: Exception) { launching = false }
    }
}

internal fun openUri(ctx: Context, uri: Uri, mime: String? = null) {
    val intent = Intent(Intent.ACTION_VIEW)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
    if (mime != null) intent.setDataAndType(uri, mime) else intent.data = uri
    try {
        ctx.startActivity(intent)
    } catch (_: Exception) {
        Toast.makeText(ctx, "No app can open this", Toast.LENGTH_SHORT).show()
    }
}

internal fun openUrl(ctx: Context, url: String) = openUri(ctx, Uri.parse(url))

/** Things on Home that open an app of your choice when tapped. */
internal enum class Slot(val label: String, val match: String) {
    CLOCK("Clock", "clock"),
    CALENDAR("Date and calendar", "calendar"),
    WEATHER("Weather", "weather"),
}

internal fun Config.appFor(slot: Slot): String = when (slot) {
    Slot.CLOCK -> clockApp
    Slot.CALENDAR -> calendarApp
    Slot.WEATHER -> weatherApp
}

internal fun Config.withApp(slot: Slot, key: String): Config = when (slot) {
    Slot.CLOCK -> copy(clockApp = key)
    Slot.CALENDAR -> copy(calendarApp = key)
    Slot.WEATHER -> copy(weatherApp = key)
}

/** Lock screen / notification shade need the accessibility service (see GestureService). */
internal fun globalAction(ctx: Context, action: Int) {
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
internal fun palette(pitchBlack: Boolean = false): Palette {
    val ctx = LocalContext.current
    fun c(id: Int) = Color(ctx.getColor(id))
    return if (isSystemInDarkTheme()) Palette(
        // Pitch black swaps the grey background for true black; cards stay a dark grey so they still stand out.
        bg = if (pitchBlack) Color.Black else c(android.R.color.system_neutral1_900),
        ink = c(android.R.color.system_neutral1_10),
        muted = c(android.R.color.system_neutral2_200),
        accent = c(android.R.color.system_accent1_100),
        pill = c(if (pitchBlack) android.R.color.system_neutral1_900 else android.R.color.system_neutral1_800),
    ) else Palette(
        bg = c(android.R.color.system_neutral1_50),
        ink = c(android.R.color.system_neutral1_1000),
        muted = c(android.R.color.system_neutral2_700),
        accent = c(android.R.color.system_accent1_700),
        pill = c(android.R.color.system_accent2_100),
    )
}

internal val LocalFont = compositionLocalOf<FontFamily> { FontFamily.SansSerif }

/** True when app names should be shown in lower case. */
internal val LocalLowercase = compositionLocalOf { false }

@Composable
internal fun ts(
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
    var apps by remember { mutableStateOf(emptyList<App>()) }
    var favKeys by remember { mutableStateOf(readFavs(ctx)) } // null = use defaults
    var cfg by remember { mutableStateOf(loadConfig(ctx)) }
    val p = palette(cfg.pitchBlack)
    val media = rememberMedia(cfg.mediaPlayer, resumes)
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

    val recentList = remember(visibleApps, searchOpen) { recentApps(ctx, visibleApps, 3) }
    val updateCfg: (Config) -> Unit = { next ->
        cfg = next
        saveConfig(ctx, next)
    }

    LaunchedEffect(drawerOpen, settingsOpen, searchOpen, homePress) {
        apps = withContext(Dispatchers.Default) { loadApps(ctx) }
    }
    var menuApp by remember { mutableStateOf<App?>(null) }
    LaunchedEffect(homePress) { menuApp = null; drawerOpen = false; settingsOpen = false; searchOpen = false; pickSlot = null; widgetsOpen = false }
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
    // The picked background image, loaded off the main thread.
    var bgImage by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    LaunchedEffect(cfg.wallpaperBg, cfg.bgStamp) {
        bgImage = if (cfg.wallpaperBg) withContext(Dispatchers.IO) {
            try { android.graphics.BitmapFactory.decodeFile(homeBgFile(ctx).path)?.asImageBitmap() } catch (_: Exception) { null }
        } else null
    }
    // Lowest priority: on Home, back does nothing, which also stops Android's "swipe back to leave the app" shrink animation.
    BackHandler(enabled = true) { }
    BackHandler(enabled = drawerOpen) { drawerOpen = false }
    BackHandler(enabled = widgetsOpen) { widgetsOpen = false }
    BackHandler(enabled = searchOpen) { searchOpen = false }
    BackHandler(enabled = settingsOpen) { settingsOpen = false }
    BackHandler(enabled = hiddenOpen) { hiddenOpen = false }
    var renameApp by remember { mutableStateOf<App?>(null) }
    // Extras
    var openFolder by remember { mutableStateOf<String?>(null) }
    var folderEdit by remember { mutableStateOf<String?>(null) } // a folder's name, or NEW_FOLDER for a new one
    var timerOpen by remember { mutableStateOf(false) }
    var lockPickerOpen by remember { mutableStateOf(false) }
    val fresh = remember {
        try { val pi = ctx.packageManager.getPackageInfo(ctx.packageName, 0); pi.firstInstallTime == pi.lastUpdateTime } catch (_: Exception) { false }
    }
    var tutorialOpen by remember { mutableStateOf(fresh && !appPrefs(ctx).getBoolean("tutorialDone", false)) }
    val finishTutorial = { tutorialOpen = false; appPrefs(ctx).edit().putBoolean("tutorialDone", true).apply() }
    SideEffect { appLockOn = cfg.appLock; lockedKeys = cfg.locked }
    // Notification peek only has text to show once it has been switched on and the list re-read.
    LaunchedEffect(cfg.notifPeek, resumes, homePress) { NotificationDotService.refreshNow() }
    // Swiping Home (or pressing it) closes these too, not only the main pages.
    LaunchedEffect(homePress) { openFolder = null; folderEdit = null; timerOpen = false; lockPickerOpen = false; renameApp = null; hiddenOpen = false }
    BackHandler(enabled = pickSlot != null) { pickSlot = null }
    BackHandler(enabled = openFolder != null) { openFolder = null }
    BackHandler(enabled = timerOpen) { timerOpen = false }
    BackHandler(enabled = lockPickerOpen) { lockPickerOpen = false }
    // The touch-and-hold menu can open over the app list, so it takes Back before the list does.
    BackHandler(enabled = menuApp != null) { menuApp = null }
    BackHandler(enabled = folderEdit != null) { folderEdit = null }
    BackHandler(enabled = renameApp != null) { renameApp = null }
    BackHandler(enabled = tutorialOpen) { finishTutorial() } // registered last = handled first

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

    // Folders sit among the favorites like apps do, using a made-up entry ("folder/Name") that is never launched.
    val favorites = remember(apps, favKeys, cfg.folders, cfg.foldersOn) {
        val keys = favKeys
        val byKey = apps.associateBy { it.key }
        val liveFolders = if (cfg.foldersOn) cfg.folders.filter { it.value.isNotEmpty() }.keys else emptySet()
        fun folderApp(n: String) = App(n, android.content.ComponentName("folder", n))
        val base = if (keys == null) defaultFavorites(apps) else keys.mapNotNull { k ->
            if (k.startsWith("folder/")) k.removePrefix("folder/").takeIf { it in liveFolders }?.let { folderApp(it) } else byKey[k]
        }
        // Folders that haven't been placed yet go at the end.
        base + liveFolders.filter { n -> base.none { it.key == "folder/$n" } }.map { folderApp(it) }
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

    CompositionLocalProvider(LocalFont provides cfg.fontFamily(), LocalLowercase provides cfg.lowercaseApps, LocalShowIcons provides cfg.iconsOn, LocalIconsColor provides cfg.iconsColor) {
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
            val bg = bgImage
            if (bg != null) {
                Image(
                    bitmap = bg, contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().then(if (cfg.wallpaperBlur) Modifier.blur(28.dp) else Modifier),
                )
                // A tint over the picture keeps the text easy to read.
                Box(Modifier.fillMaxSize().background(p.bg.copy(alpha = 0.55f)))
            }
            Box(Modifier.fillMaxSize().graphicsLayer {
                // Fading each draw op directly is much cheaper than rendering the whole page off-screen every frame.
                compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.ModulateAlpha
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
                onAppMenu = { menuApp = it },
                media = media,
                onSettings = {
                    buzzWith(android.view.HapticFeedbackConstants.LONG_PRESS)
                    settingsOpen = true
                },
                onSleep = { globalAction(ctx, AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN) },
                onNotifications = { globalAction(ctx, AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS) },
                gesturesEnabled = menuApp == null && !drawerOpen && !settingsOpen && !searchOpen && !widgetsOpen,
                onLaunch = { openApp(ctx, it) },
                onSlot = openSlot,
                onBuzz = buzz,
                onOpenWidgets = { widgetsOpen = true },
                onClockLong = { buzz(); timerOpen = true },
                openFolder = openFolder,
                onFolder = { name, long -> buzz(); if (long) folderEdit = name else openFolder = name },
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
                    compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.ModulateAlpha
                    if (cfg.drawerRight) translationX = (1f - drawerP.value) * size.width
                    else translationY = (1f - drawerP.value) * size.height
                    // Solid the whole way: the list slides over Home instead of cross-fading with it, so the two
                    // never show through each other (that double exposure was the glitch).
                    alpha = if (drawerP.value > 0.001f) 1f else 0f
                }) {
                    Drawer(
                        p, visibleApps, favorites.map { it.key }.toSet(),
                        fromRight = cfg.drawerRight,
                        active = drawerVisible,
                        progress = { drawerP.value },
                        onLaunch = { openApp(ctx, it) },
                        onAppMenu = { menuApp = it },
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
                    compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.ModulateAlpha
                    translationY = (1f - searchP.value) * size.height
                    alpha = (searchP.value * 1.6f).coerceIn(0f, 1f)
                }) {
                    SearchScreen(
                        p, cfg, visibleApps,
                        active = searchOpen,
                        pillShown = pillSettled,
                        recent = if (cfg.recentApps) recentList else emptyList(),
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
            var lastMenuApp by remember { mutableStateOf<App?>(null) }
            if (menuApp != null) lastMenuApp = menuApp
            AnimatedVisibility(
                visible = menuApp != null,
                enter = fadeIn(tw(160)) +
                    scaleIn(tw(280, easing = EmphasizedDecel), initialScale = 0.92f, transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)) +
                    slideInVertically(tw(300, easing = EmphasizedDecel)) { it / 6 },
                exit = fadeOut(tw(120)) +
                    scaleOut(tw(160, easing = EmphasizedAccel), targetScale = 0.95f, transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)) +
                    androidx.compose.animation.slideOutVertically(tw(160, easing = EmphasizedAccel)) { it / 8 },
            ) {
                val app = menuApp ?: lastMenuApp
                if (app != null) {
                    val pkg = app.component.packageName
                    AppMenu(
                        p, app,
                        onRename = if (favorites.any { it.key == app.key }) ({ menuApp = null; renameApp = app }) else null,
                        pinned = favorites.any { it.key == app.key },
                        canPin = favorites.size < maxFavorites(cfg),
                        onPin = { menuApp = null; togglePin(app) },
                        peek = if (cfg.notifPeek) NotifState.latest[pkg] else null,
                        onPeek = { menuApp = null; openApp(ctx, app) },
                        locked = if (cfg.appLock) app.key in cfg.locked else null,
                        onToggleLock = {
                            menuApp = null
                            if (app.key in cfg.locked) authenticate(ctx, "Unlock ${app.label}") { updateCfg(cfg.copy(locked = cfg.locked - app.key)) }
                            else { updateCfg(cfg.copy(locked = cfg.locked + app.key)); Toast.makeText(ctx, "${app.label} is locked", Toast.LENGTH_SHORT).show() }
                        },
                        onDismiss = { menuApp = null },
                        onInfo = {
                            menuApp = null
                            try {
                                ctx.startActivity(
                                    Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:$pkg"))
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            } catch (_: Exception) { }
                        },
                        onRemove = { menuApp = null; togglePin(app) },
                        onHide = {
                            menuApp = null
                            updateCfg(cfg.copy(hidden = cfg.hidden + app.key))
                            setFavs(favorites.map { it.key } - app.key)
                            Toast.makeText(ctx, "Hidden. Bring it back in Settings > Apps.", Toast.LENGTH_SHORT).show()
                        },
                        onUninstall = {
                            menuApp = null
                            try {
                                ctx.startActivity(
                                    Intent(Intent.ACTION_DELETE, android.net.Uri.parse("package:$pkg")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            } catch (_: Exception) {
                                Toast.makeText(ctx, "Can't uninstall this app", Toast.LENGTH_SHORT).show()
                            }
                        },
                    )
                }
            }
            renameApp?.let { app ->
                RenameOverlay(
                    p, app, cfg.labels[app.key],
                    onSave = { text ->
                        updateCfg(cfg.copy(labels = if (text.isEmpty() || text == app.label) cfg.labels - app.key else cfg.labels + (app.key to text)))
                        renameApp = null
                    },
                    onReset = { updateCfg(cfg.copy(labels = cfg.labels - app.key)); renameApp = null },
                    onDismiss = { renameApp = null },
                )
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
                compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.ModulateAlpha
                translationY = (1f - settingsP.value) * size.height * 0.05f
                alpha = (settingsP.value * 1.6f).coerceIn(0f, 1f)
            }) {
                SettingsScreen(
                    p, cfg, updateCfg,
                    favorites = favorites, apps = apps, onSetFavs = setFavs,
                    onPickSlot = { pickSlot = it },
                    onOpenHidden = { hiddenOpen = true },
                    coveredByScreen = hiddenOpen || pickSlot != null || lockPickerOpen || folderEdit != null,
                    onEditFolder = { folderEdit = it ?: NEW_FOLDER },
                    onPickLocked = { lockPickerOpen = true },
                    onShowTutorial = { settingsOpen = false; tutorialOpen = true },
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
            AnimatedVisibility(visible = lockPickerOpen, enter = pushEnter(), exit = pushExit()) {
                HiddenAppsScreen(
                    p, apps, cfg.locked,
                    onToggle = { key ->
                        if (key in cfg.locked) authenticate(ctx, "Unlock this app") { updateCfg(cfg.copy(locked = cfg.locked - key)) }
                        else updateCfg(cfg.copy(locked = cfg.locked + key))
                    },
                    onBack = { lockPickerOpen = false },
                    title = "Locked apps",
                )
            }
            if (folderEdit != null) {
                val editing = folderEdit!!.takeIf { it != NEW_FOLDER }
                FolderEditor(
                    p, editing, visibleApps, editing?.let { cfg.folders[it] } ?: emptyList(),
                    onSave = { n, keys ->
                        if (editing != null && editing != n) {
                            // Keep a renamed folder in the same spot on Home.
                            setFavs(favorites.map { it.key }.map { if (it == "folder/$editing") "folder/$n" else it })
                        }
                        updateCfg(cfg.copy(folders = cfg.folders.filterKeys { it != editing && it != n } + (n to keys)))
                        folderEdit = null
                    },
                    onDelete = editing?.let { { updateCfg(cfg.copy(folders = cfg.folders - it)); folderEdit = null } },
                    onCancel = { folderEdit = null },
                )
            }
            var lastFolder by remember { mutableStateOf<String?>(null) }
            if (openFolder != null) lastFolder = openFolder
            // The card grows up out of the bottom and shrinks back when it closes.
            AnimatedVisibility(
                visible = openFolder != null,
                enter = fadeIn(tw(180)) +
                    scaleIn(tw(300, easing = EmphasizedDecel), initialScale = 0.9f, transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)) +
                    slideInVertically(tw(320, easing = EmphasizedDecel)) { it / 8 },
                exit = fadeOut(tw(140)) +
                    scaleOut(tw(180, easing = EmphasizedAccel), targetScale = 0.94f, transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)) +
                    androidx.compose.animation.slideOutVertically(tw(180, easing = EmphasizedAccel)) { it / 10 },
            ) {
                val name = lastFolder
                if (name != null) {
                    val byKey = apps.associateBy { it.key }
                    FolderSheet(
                        p, name, (cfg.folders[name] ?: emptyList()).mapNotNull { byKey[it] },
                        onLaunch = { openFolder = null; openApp(ctx, it) },
                        onEdit = { openFolder = null; folderEdit = name },
                        onDismiss = { openFolder = null },
                    )
                }
            }
            AnimatedVisibility(
                visible = timerOpen,
                enter = fadeIn(tw(160)) +
                    scaleIn(tw(280, easing = EmphasizedDecel), initialScale = 0.92f, transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)) +
                    slideInVertically(tw(300, easing = EmphasizedDecel)) { it / 6 },
                exit = fadeOut(tw(120)) +
                    scaleOut(tw(160, easing = EmphasizedAccel), targetScale = 0.95f, transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)) +
                    androidx.compose.animation.slideOutVertically(tw(160, easing = EmphasizedAccel)) { it / 8 },
            ) {
                TimerSheet(p, onStart = { timerOpen = false; startTimer(ctx, it) }, onDismiss = { timerOpen = false })
            }
            if (tutorialOpen) Tutorial(p, onDone = finishTutorial)
        }
    }
}

@Composable
internal fun rememberNow(): State<LocalDateTime> {
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
