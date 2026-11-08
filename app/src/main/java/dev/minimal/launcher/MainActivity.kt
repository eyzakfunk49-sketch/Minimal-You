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
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { LauncherApp(homePress) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        homePress++
    }
}

// ---------- Actions ----------

private fun openApp(ctx: Context, app: App) {
    val intent = Intent(Intent.ACTION_MAIN)
        .addCategory(Intent.CATEGORY_LAUNCHER)
        .setComponent(app.component)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
    try { ctx.startActivity(intent) } catch (_: Exception) { }
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
fun LauncherApp(homePress: Int) {
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

    val updateCfg: (Config) -> Unit = { next ->
        cfg = next
        saveConfig(ctx, next)
    }

    LaunchedEffect(drawerOpen, settingsOpen, searchOpen, homePress) {
        apps = withContext(Dispatchers.Default) { loadApps(ctx) }
    }
    LaunchedEffect(homePress) { drawerOpen = false; settingsOpen = false; searchOpen = false; pickSlot = null }
    BackHandler(enabled = drawerOpen) { drawerOpen = false }
    BackHandler(enabled = searchOpen) { searchOpen = false }
    BackHandler(enabled = settingsOpen) { settingsOpen = false }
    BackHandler(enabled = pickSlot != null) { pickSlot = null } // registered last = handled first

    // Weather: refresh every 30 minutes while a city is set.
    LaunchedEffect(cfg.weatherOn, cfg.city, cfg.lat, cfg.lon, cfg.fahrenheit) {
        if (!cfg.weatherOn || cfg.city.isBlank()) {
            weather = null
            return@LaunchedEffect
        }
        while (true) {
            val w = withContext(Dispatchers.IO) { fetchWeather(cfg.lat, cfg.lon, cfg.fahrenheit) }
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
        Box(Modifier.fillMaxSize().background(p.bg)) {
            Home(
                p, cfg, favorites, weather, event,
                onOpenDrawer = { drawerOpen = true },
                onOpenSearch = { searchOpen = true },
                onSettings = { settingsOpen = true },
                onSleep = { globalAction(ctx, AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN) },
                onNotifications = { globalAction(ctx, AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS) },
                gesturesEnabled = !drawerOpen && !settingsOpen && !searchOpen,
                onLaunch = { openApp(ctx, it) },
                onSlot = openSlot,
            )
            AnimatedVisibility(
                visible = drawerOpen,
                enter = slideInVertically { it / 3 } + fadeIn(),
                exit = slideOutVertically { it / 3 } + fadeOut(),
            ) {
                Drawer(
                    p, apps, favorites.map { it.key }.toSet(),
                    onLaunch = { openApp(ctx, it) },
                    onTogglePin = togglePin,
                    onClose = { drawerOpen = false },
                )
            }
            AnimatedVisibility(
                visible = searchOpen,
                enter = slideInVertically { it / 3 } + fadeIn(),
                exit = slideOutVertically { it / 3 } + fadeOut(),
            ) {
                SearchScreen(p, cfg, apps, onClose = { searchOpen = false })
            }
            AnimatedVisibility(
                visible = settingsOpen,
                enter = slideInVertically { it / 3 } + fadeIn(),
                exit = slideOutVertically { it / 3 } + fadeOut(),
            ) {
                SettingsScreen(
                    p, cfg, updateCfg,
                    favorites = favorites, apps = apps, onSetFavs = setFavs,
                    onPickSlot = { pickSlot = it },
                    onBack = { settingsOpen = false },
                )
            }
            val slot = pickSlot
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

@Composable
private fun Home(
    p: Palette,
    cfg: Config,
    favorites: List<App>,
    weather: Weather?,
    event: Event?,
    onOpenDrawer: () -> Unit,
    onOpenSearch: () -> Unit,
    onSettings: () -> Unit,
    onSleep: () -> Unit,
    onNotifications: () -> Unit,
    gesturesEnabled: Boolean,
    onLaunch: (App) -> Unit,
    onSlot: (Slot) -> Unit,
) {
    val ctx = LocalContext.current
    val now by rememberNow()
    val is24 = when (cfg.hours) {
        HourFormat.SYSTEM -> DateFormat.is24HourFormat(ctx)
        HourFormat.H12 -> false
        HourFormat.H24 -> true
    }

    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(horizontal = 32.dp)
            // Blank space: double tap = sleep, long press = settings.
            .pointerInput(gesturesEnabled) {
                if (gesturesEnabled) {
                    detectTapGestures(onDoubleTap = { onSleep() }, onLongPress = { onSettings() })
                }
            }
            // Swipe up = app list, swipe down = notifications.
            .pointerInput(gesturesEnabled) {
                if (gesturesEnabled) {
                    var total = 0f
                    detectVerticalDragGestures(
                        onDragStart = { total = 0f },
                        onDragEnd = {
                            if (total < -120f) onOpenDrawer() else if (total > 120f) onNotifications()
                        },
                        onDragCancel = {},
                        onVerticalDrag = { _, dy -> total += dy },
                    )
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
                        .clickable(role = Role.Button) { onSlot(Slot.CALENDAR) }
                        .wrapContentHeight(Alignment.CenterVertically),
                )
            }
            Box(Modifier.clickable(role = Role.Button) { onSlot(Slot.CLOCK) }) {
                ClockText(p, cfg.clock, now, is24)
            }
            if (weather != null) {
                val line = listOf("${weather.temp}°", describe(weather.code))
                    .filter { it.isNotEmpty() }.joinToString(" · ")
                BasicText(
                    line,
                    style = ts(p.accent, 15.sp, FontWeight.Medium),
                    modifier = Modifier
                        .heightIn(min = 40.dp)
                        .clickable(role = Role.Button) { onSlot(Slot.WEATHER) }
                        .wrapContentHeight(Alignment.CenterVertically),
                )
            }
            if (event != null) {
                BasicText(
                    formatEvent(event, is24),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = ts(p.muted, 15.sp, FontWeight.Normal),
                    modifier = Modifier
                        .heightIn(min = 40.dp)
                        .clickable(role = Role.Button) { onSlot(Slot.CALENDAR) }
                        .wrapContentHeight(Alignment.CenterVertically),
                )
            }
        }
        Spacer(Modifier.weight(1f))
        favorites.forEach {
            AppRow(it, 30.sp, p.ink, onClick = { onLaunch(it) }, weight = FontWeight.Normal)
        }
        Spacer(Modifier.height(32.dp))
        Pill(p, Modifier.clickable(role = Role.Button, onClick = onOpenSearch)) {
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
    onLaunch: (App) -> Unit,
    onTogglePin: (App) -> Unit,
    onClose: () -> Unit,
) {
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

    // Pulling down while the list is already at the top closes the drawer.
    val pullToClose = remember(onClose) {
        object : NestedScrollConnection {
            var pulled = 0f
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y > 0f) {
                    pulled += available.y
                    if (pulled > 150f) { pulled = 0f; onClose() }
                } else if (available.y <= 0f) {
                    pulled = 0f
                }
                return Offset.Zero
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(p.bg)
            .windowInsetsPadding(WindowInsets.systemBars)
            .imePadding()
            .padding(horizontal = 32.dp)
            // Swipe down on the search area or any blank spot also closes it.
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
                keyboardActions = KeyboardActions(onSearch = { shown.firstOrNull()?.let(onLaunch) }),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (query.isEmpty()) BasicText("Search apps", style = ts(p.muted, 16.sp, FontWeight.Normal))
                        inner()
                    }
                },
            )
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.nestedScroll(pullToClose), contentPadding = PaddingValues(bottom = 24.dp)) {
            groups.forEach { (letter, list) ->
                stickyHeader(key = "h$letter") {
                    Box(Modifier.fillMaxWidth().background(p.bg).padding(top = 12.dp, bottom = 4.dp)) {
                        BasicText(letter.toString(), style = ts(p.accent, 13.sp, FontWeight.Medium, 1.sp))
                    }
                }
                items(list, key = { it.key }) { app ->
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
            .windowInsetsPadding(WindowInsets.systemBars)
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
                onConfig(cfg.copy(city = hit.first, lat = hit.second, lon = hit.third, weatherOn = true))
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
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(horizontal = 32.dp),
    ) {
        BasicText(
            "Back",
            style = ts(p.accent, 15.sp, FontWeight.Medium),
            modifier = Modifier
                .padding(top = 8.dp)
                .heightIn(min = 48.dp)
                .clickable(role = Role.Button, onClick = onBack)
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
                Pill(p, Modifier.padding(vertical = 4.dp)) {
                    BasicTextField(
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
                            .clickable(role = Role.Button) { setCity() }
                            .wrapContentHeight(Alignment.CenterVertically)
                            .padding(start = 12.dp),
                    )
                }
            }
            item {
                BasicText(
                    if (cfg.city.isBlank()) "Type a city and tap Set. Only the city name is sent, to Open-Meteo."
                    else "Showing weather for ${cfg.city}.",
                    style = ts(p.muted, 14.sp, FontWeight.Normal),
                )
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

            // Tap actions
            item { SectionTitle("Tap actions", p) }
            items(Slot.entries) { slot ->
                val name = apps.firstOrNull { it.key == cfg.appFor(slot) }?.label ?: "Default"
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .clickable(role = Role.Button) { onPickSlot(slot) },
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
                        .clickable(role = Role.Button) {
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
            .clickable(role = Role.RadioButton, onClick = onClick),
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
        Canvas(Modifier.size(width = 44.dp, height = 26.dp)) {
            val r = size.height / 2f
            drawRoundRect(if (checked) p.accent else p.pill, cornerRadius = CornerRadius(r, r))
            drawCircle(
                if (checked) p.bg else p.muted,
                radius = r * 0.7f,
                center = Offset(if (checked) size.width - r else r, r),
            )
        }
    }
}

@Composable
private fun RadioDot(selected: Boolean, p: Palette) {
    Canvas(Modifier.size(20.dp)) {
        val s = size.minDimension
        drawCircle(if (selected) p.accent else p.muted, radius = s * 0.45f, style = Stroke(s * 0.1f))
        if (selected) drawCircle(p.accent, radius = s * 0.24f)
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
            .clickable(role = Role.Button, onClick = onClick),
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
            .clickable(role = Role.Button, onClick = onClick)
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
            .combinedClickable(role = Role.Button, onClick = onClick, onLongClick = onLongClick),
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
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(horizontal = 32.dp)
            .pointerInput(Unit) { detectTapGestures { } },
    ) {
        BasicText(
            "Cancel",
            style = ts(p.accent, 16.sp, FontWeight.Medium),
            modifier = Modifier
                .padding(top = 8.dp)
                .heightIn(min = 52.dp)
                .clickable(role = Role.Button, onClick = onCancel)
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
                        .clickable(role = Role.Button) { onPick(null) }
                        .wrapContentHeight(Alignment.CenterVertically),
                )
            }
            items(apps, key = { it.key }) { app ->
                AppRow(app, 22.sp, p.ink, onClick = { onPick(app) })
            }
        }
    }
}
