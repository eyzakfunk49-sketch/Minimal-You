package dev.minimal.launcher

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Typeface
import android.os.Bundle
import android.text.format.DateFormat
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
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

// ---------- Model ----------

data class App(val label: String, val component: ComponentName) {
    val key: String get() = component.flattenToString()
}

/** Fonts that ship with Android, so nothing needs to be bundled. */
enum class AppFont(val label: String, val family: FontFamily) {
    SANS("Sans", FontFamily.SansSerif),
    CONDENSED("Condensed", FontFamily(Typeface.create("sans-serif-condensed", Typeface.NORMAL))),
    SERIF("Serif", FontFamily.Serif),
    MONO("Monospace", FontFamily.Monospace),
    CASUAL("Casual", FontFamily(Typeface.create("casual", Typeface.NORMAL))),
}

@Suppress("DEPRECATION")
private fun loadApps(ctx: Context): List
<App> {
    val pm = ctx.packageManager
    val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(query, 0)
        .filter { it.activityInfo.packageName != ctx.packageName }
        .map {
            App(
                it.loadLabel(pm).toString(),
                ComponentName(it.activityInfo.packageName, it.activityInfo.name),
            )
        }
        .sortedBy { it.label.lowercase() }
}

private fun launch(ctx: Context, app: App) {
    val intent = Intent(Intent.ACTION_MAIN)
        .addCategory(Intent.CATEGORY_LAUNCHER)
        .setComponent(app.component)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
    try { ctx.startActivity(intent) } catch (_: Exception) { }
}

private fun prefs(ctx: Context) = ctx.getSharedPreferences("minimal", Context.MODE_PRIVATE)

private fun readFavs(ctx: Context): List
    <String>? =
    prefs(ctx).getString("favs", null)?.split("\n")?.filter { it.isNotBlank() }

private fun writeFavs(ctx: Context, keys: List
        <String>) {
    prefs(ctx).edit().putString("favs", keys.joinToString("\n")).apply()
}

private fun readFont(ctx: Context): AppFont =
    AppFont.entries.firstOrNull { it.name == prefs(ctx).getString("font", null) } ?: AppFont.SANS

private fun writeFont(ctx: Context, font: AppFont) {
    prefs(ctx).edit().putString("font", font.name).apply()
}

private fun defaultFavorites(apps: List
            <App>): List
                <App> =
    listOf("phone", "messages", "camera", "notes")
        .mapNotNull { name -> apps.firstOrNull { it.label.contains(name, ignoreCase = true) } }
        .distinct()

// ---------- Look (Material You) ----------

data class Palette(val bg: Color, val ink: Color, val muted: Color, val accent: Color, val pill: Color)

/** Colors come from the system's wallpaper-based Material You palette (Android 12+). */
@Composable
private fun palette(): Palette {
    val ctx = LocalContext.current
    fun c(id: Int) = Color(ctx.getColor(id))
    return if (isSystemInDarkTheme()) Palette(
        bg = c(android.R.color.system_neutral1_900),
        ink = c(android.R.color.system_neutral1_100),
        muted = c(android.R.color.system_neutral2_300),
        accent = c(android.R.color.system_accent1_200),
        pill = c(android.R.color.system_neutral1_800),
    ) else Palette(
        bg = c(android.R.color.system_neutral1_50),
        ink = c(android.R.color.system_neutral1_900),
        muted = c(android.R.color.system_neutral2_600),
        accent = c(android.R.color.system_accent1_600),
        pill = c(android.R.color.system_accent2_100),
    )
}

private val LocalFont = compositionLocalOf
                    <FontFamily> { FontFamily.SansSerif }

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
    var apps by remember { mutableStateOf(emptyList
                        <App>()) }
    var favKeys by remember { mutableStateOf(readFavs(ctx)) } // null = use defaults
    var font by remember { mutableStateOf(readFont(ctx)) }
    var drawerOpen by remember { mutableStateOf(false) }
    var settingsOpen by remember { mutableStateOf(false) }

    LaunchedEffect(drawerOpen, settingsOpen, homePress) {
        apps = withContext(Dispatchers.Default) { loadApps(ctx) }
    }
    LaunchedEffect(homePress) { drawerOpen = false; settingsOpen = false }
    BackHandler(enabled = drawerOpen) { drawerOpen = false }
    BackHandler(enabled = settingsOpen) { settingsOpen = false } // registered last = handled first

    val favorites = remember(apps, favKeys) {
        val keys = favKeys
        if (keys == null) defaultFavorites(apps)
        else { val byKey = apps.associateBy { it.key }; keys.mapNotNull { byKey[it] } }
    }

    val setFavs: (List
                            <String>) -> Unit = { next ->
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

    CompositionLocalProvider(LocalFont provides font.family) {
        Box(Modifier.fillMaxSize().background(p.bg)) {
            Home(
                p, favorites,
                onOpenDrawer = { drawerOpen = true },
                onSettings = { settingsOpen = true },
                onLaunch = { launch(ctx, it) },
            )
            AnimatedVisibility(
                visible = drawerOpen,
                enter = slideInVertically { it / 3 } + fadeIn(),
                exit = slideOutVertically { it / 3 } + fadeOut(),
            ) {
                Drawer(
                    p, apps, favorites.map { it.key }.toSet(),
                    onLaunch = { launch(ctx, it) },
                    onTogglePin = togglePin,
                    onSettings = { settingsOpen = true },
                )
            }
            AnimatedVisibility(
                visible = settingsOpen,
                enter = slideInVertically { it / 3 } + fadeIn(),
                exit = slideOutVertically { it / 3 } + fadeOut(),
            ) {
                Settings(
                    p, font,
                    onFont = { font = it; writeFont(ctx, it) },
                    favorites = favorites, apps = apps, onSetFavs = setFavs,
                    onBack = { settingsOpen = false },
                )
            }
        }
    }
}

@Composable
private fun rememberNow(): State
                                <LocalDateTime> {
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Home(
    p: Palette,
    favorites: List
                                    <App>,
    onOpenDrawer: () -> Unit,
    onSettings: () -> Unit,
    onLaunch: (App) -> Unit,
) {
    val ctx = LocalContext.current
    val now by rememberNow()
    val time = now.format(DateTimeFormatter.ofPattern(if (DateFormat.is24HourFormat(ctx)) "H:mm" else "h:mm"))
    val date = now.format(DateTimeFormatter.ofPattern("EEEE, d MMMM"))

    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(horizontal = 32.dp)
            .pointerInput(Unit) {
                var total = 0f
                detectVerticalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = { if (total < -120f) onOpenDrawer() },
                    onDragCancel = {},
                    onVerticalDrag = { _, dy -> total += dy },
                )
            },
    ) {
        Spacer(Modifier.height(64.dp))
        // Long-press the clock to open Settings.
        Column(
            Modifier.fillMaxWidth().combinedClickable(
                onLongClickLabel = "Open settings", onLongClick = onSettings, onClick = {},
            ),
        ) {
            BasicText(date, style = ts(p.muted, 15.sp, FontWeight.Normal))
            BasicText(time, style = ts(p.ink, 96.sp, FontWeight.Light, (-4).sp, 104.sp))
        }
        Spacer(Modifier.weight(1f))
        favorites.forEach { AppRow(it, 30.sp, p.ink, onClick = { onLaunch(it) }) }
        Spacer(Modifier.height(32.dp))
        Pill(p, Modifier.clickable(role = Role.Button, onClick = onOpenDrawer)) {
            SearchIcon(p.accent)
            Spacer(Modifier.width(12.dp))
            BasicText("Search apps", style = ts(p.muted, 16.sp, FontWeight.Normal))
        }
        Spacer(Modifier.height(24.dp))
    }
}

// ---------- Drawer ----------

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Drawer(
    p: Palette,
    apps: List
                                        <App>,
    pinned: Set
                                            <String>,
    onLaunch: (App) -> Unit,
    onTogglePin: (App) -> Unit,
    onSettings: () -> Unit,
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

    Column(
        Modifier
            .fillMaxSize()
            .background(p.bg)
            .windowInsetsPadding(WindowInsets.systemBars)
            .imePadding()
            .padding(horizontal = 32.dp),
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
                        if (query.isEmpty()) BasicText("Search", style = ts(p.muted, 16.sp, FontWeight.Normal))
                        inner()
                    }
                },
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            BasicText(
                "Settings",
                style = ts(p.accent, 14.sp, FontWeight.Medium),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .clickable(role = Role.Button, onClick = onSettings)
                    .wrapContentHeight(Alignment.CenterVertically)
                    .padding(horizontal = 4.dp),
            )
        }
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            groups.forEach { (letter, list) ->
                stickyHeader(key = "h$letter") {
                    Box(Modifier.fillMaxWidth().background(p.bg).padding(top = 12.dp, bottom = 4.dp)) {
                        BasicText(letter.toString(), style = ts(p.accent, 13.sp, FontWeight.Medium, 1.sp))
                    }
                }
                items(list, key = { it.key }) { app ->
                    AppRow(
                        app, 22.sp, if (app.key in pinned) p.accent else p.ink,
                        onClick = { onLaunch(app) },
                        onLongClick = { onTogglePin(app) },
                    )
                }
            }
        }
    }
}

// ---------- Settings ----------

@Composable
private fun Settings(
    p: Palette,
    font: AppFont,
    onFont: (AppFont) -> Unit,
    favorites: List
                                                <App>,
    apps: List
                                                    <App>,
    onSetFavs: (List
                                                        <String>) -> Unit,
    onBack: () -> Unit,
) {
    var adding by remember { mutableStateOf(false) }
    val favKeys = favorites.map { it.key }
    val full = favKeys.size >= MAX_FAVORITES
    val addable = remember(apps, favKeys) { apps.filter { it.key !in favKeys } }

    fun move(i: Int, delta: Int) {
        val j = i + delta
        if (j !in favKeys.indices) return
        val list = favKeys.toMutableList()
        val t = list[i]; list[i] = list[j]; list[j] = t
        onSetFavs(list)
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
            item { SectionTitle("Font", p) }
            items(AppFont.entries) { f ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .clickable(role = Role.RadioButton) { onFont(f) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioDot(f == font, p)
                    Spacer(Modifier.width(16.dp))
                    BasicText(
                        f.label,
                        style = TextStyle(color = p.ink, fontSize = 22.sp, fontWeight = FontWeight.Light, fontFamily = f.family),
                    )
                }
            }
            item { SectionTitle("Favorites", p) }
            if (favorites.isEmpty()) {
                item { BasicText("No favorites yet. Add some below.", style = ts(p.muted, 15.sp, FontWeight.Normal)) }
            }
            itemsIndexed(favorites, key = { _, a -> a.key }) { i, app ->
      o Home", Toast.LENGTH_SHORT).show()
        }
    }

    CompositionLocalProvider(LocalFont provides font.family) {
        Box(Modifier.fillMaxSize().background(p.bg)) {
            Home(
                p, favorites,
                onOpenDrawer = { drawerOpen = true },
                onSettings = { settingsOpen = true },
                onLaunch = { launch(ctx, it) },
            )
            AnimatedVisibility(
                visible = drawerOpen,
                enter = slideInVertically { it / 3 } + fadeIn(),
                exit = slideOutVertically { it / 3 } + fadeOut(),
            ) {
                Drawer(
                    p, apps, favorites.map { it.key }.toSet(),
                    onLaunch = { launch(ctx, it) },
                    onTogglePin = togglePin,
                    onSettings = { settingsOpen = true },
                )
            }
            AnimatedVisibility(
                visible = settingsOpen,
                enter = slideInVertically { it / 3 } + fadeIn(),
                exit = slideOutVertically { it / 3 } + fadeOut(),
            ) {
                Settings(
                    p, font,
                    onFont = { font = it; writeFont(ctx, it) },
                    favorites = favorites, apps = apps, onSetFavs = setFavs,
                    onBack = { settingsOpen = false },
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Home(
    p: Palette,
    favorites: List<App>,
    onOpenDrawer: () -> Unit,
    onSettings: () -> Unit,
    onLaunch: (App) -> Unit,
) {
    val ctx = LocalContext.current
    val now by rememberNow()
    val time = now.format(DateTimeFormatter.ofPattern(if (DateFormat.is24HourFormat(ctx)) "H:mm" else "h:mm"))
    val date = now.format(DateTimeFormatter.ofPattern("EEEE, d MMMM"))

    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(horizontal = 32.dp)
            .pointerInput(Unit) {
                var total = 0f
                detectVerticalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = { if (total < -120f) onOpenDrawer() },
                    onDragCancel = {},
                    onVerticalDrag = { _, dy -> total += dy },
                )
            },
    ) {
        Spacer(Modifier.height(64.dp))
        // Long-press the clock to open Settings.
        Column(
            Modifier.fillMaxWidth().combinedClickable(
                onLongClickLabel = "Open settings", onLongClick = onSettings, onClick = {},
            ),
        ) {
            BasicText(date, style = ts(p.muted, 15.sp, FontWeight.Normal))
            BasicText(time, style = ts(p.ink, 96.sp, FontWeight.Light, (-4).sp, 104.sp))
        }
        Spacer(Modifier.weight(1f))
        favorites.forEach { AppRow(it, 30.sp, p.ink, onClick = { onLaunch(it) }) }
        Spacer(Modifier.height(32.dp))
        Pill(p, Modifier.clickable(role = Role.Button, onClick = onOpenDrawer)) {
            SearchIcon(p.accent)
            Spacer(Modifier.width(12.dp))
            BasicText("Search apps", style = ts(p.muted, 16.sp, FontWeight.Normal))
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
    onSettings: () -> Unit,
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

    Column(
        Modifier
            .fillMaxSize()
            .background(p.bg)
            .windowInsetsPadding(WindowInsets.systemBars)
            .imePadding()
            .padding(horizontal = 32.dp),
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
                        if (query.isEmpty()) BasicText("Search", style = ts(p.muted, 16.sp, FontWeight.Normal))
                        inner()
                    }
                },
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            BasicText(
                "Settings",
                style = ts(p.accent, 14.sp, FontWeight.Medium),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .clickable(role = Role.Button, onClick = onSettings)
                    .wrapContentHeight(Alignment.CenterVertically)
                    .padding(horizontal = 4.dp),
            )
        }
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            groups.forEach { (letter, list) ->
                stickyHeader(key = "h$letter") {
                    Box(Modifier.fillMaxWidth().background(p.bg).padding(top = 12.dp, bottom = 4.dp)) {
                        BasicText(letter.toString(), style = ts(p.accent, 13.sp, FontWeight.Medium, 1.sp))
                    }
                }
                items(list, key = { it.key }) { app ->
                    AppRow(
                        app, 22.sp, if (app.key in pinned) p.accent else p.ink,
                        onClick = { onLaunch(app) },
                        onLongClick = { onTogglePin(app) },
                    )
                }
            }
        }
    }
}

// ---------- Settings ----------

@Composable
private fun Settings(
    p: Palette,
    font: AppFont,
    onFont: (AppFont) -> Unit,
    favorites: List<App>,
    apps: List<App>,
    onSetFavs: (List<String>) -> Unit,
    onBack: () -> Unit,
) {
    var adding by remember { mutableStateOf(false) }
    val favKeys = favorites.map { it.key }
    val full = favKeys.size >= MAX_FAVORITES
    val addable = remember(apps, favKeys) { apps.filter { it.key !in favKeys } }

    fun move(i: Int, delta: Int) {
        val j = i + delta
        if (j !in favKeys.indices) return
        val list = favKeys.toMutableList()
        val t = list[i]; list[i] = list[j]; list[j] = t
        onSetFavs(list)
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
            item { SectionTitle("Font", p) }
            items(AppFont.entries) { f ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .clickable(role = Role.RadioButton) { onFont(f) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioDot(f == font, p)
                    Spacer(Modifier.width(16.dp))
                    BasicText(
                        f.label,
                        style = TextStyle(color = p.ink, fontSize = 22.sp, fontWeight = FontWeight.Light, fontFamily = f.family),
                    )
                }
            }
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
                    IconBtn(Glyph.UP, "Move ${app.label} up", if (i > 0) p.muted efavorites.forEach { AppRow(it, 30.sp, p.ink, onClick = { onLaunch(it) }) }
        Spacer(Modifier.height(32.dp))
        Pill(p, Modifier.clickable(role = Role.Button, onClick = onOpenDrawer)) {
            SearchIcon(p.accent)
            Spacer(Modifier.width(12.dp))
            BasicText("Search apps", style = TextStyle(color = p.muted, fontSize = 16.sp))
        }
        Spacer(Modifier.height(24.dp))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Drawer(
    p: Palette,
    apps: List<App>,
    pinned: Set<String>,
    onLaunch: (App) -> Unit,
    onTogglePin: (App) -> Unit,
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

    Column(
        Modifier
            .fillMaxSize()
            .background(p.bg)
            .windowInsetsPadding(WindowInsets.systemBars)
            .imePadding()
            .padding(horizontal = 32.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        Pill(p) {
            SearchIcon(p.accent)
            Spacer(Modifier.width(12.dp))
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                textStyle = TextStyle(color = p.ink, fontSize = 16.sp),
                cursorBrush = SolidColor(p.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { shown.firstOrNull()?.let(onLaunch) }),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (query.isEmpty()) BasicText("Search", style = TextStyle(color = p.muted, fontSize = 16.sp))
                        inner()
                    }
                },
            )
        }
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            groups.forEach { (letter, list) ->
                stickyHeader(key = "h$letter") {
                    Box(Modifier.fillMaxWidth().background(p.bg).padding(top = 20.dp, bottom = 4.dp)) {
                        BasicText(
                            letter.toString(),
                            style = TextStyle(color = p.accent, fontSize = 13.sp,
                                fontWeight = FontWeight.Medium, letterSpacing = 1.sp),
                        )
                    }
                }
                items(list, key = { it.key }) { app ->
                    AppRow(
                        app, 22.sp, if (app.key in pinned) p.accent else p.ink,
                        onClick = { onLaunch(app) },
                        onLongClick = { onTogglePin(app) },
                    )
                }
            }
        }
    }
}

// ---------- Pieces ----------

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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppRow(
    app: App,
    size: TextUnit,
    color: Color,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    BasicText(
        app.label,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = TextStyle(color = color, fontSize = size, fontWeight = FontWeight.Light),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .combinedClickable(role = Role.Button, onClick = onClick, onLongClick = onLongClick)
            .wrapContentHeight(Alignment.CenterVertically),
    )
}
