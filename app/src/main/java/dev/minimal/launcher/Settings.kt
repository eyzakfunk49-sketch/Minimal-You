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
// ---------- Settings ----------

@Composable
internal fun SettingsScreen(
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
                                SettingsGroup(p, "Alignment") {
                                    HomeAlign.entries.forEach { a -> OptionRow(a.label, a == cfg.homeAlign, p) { onConfig(cfg.copy(homeAlign = a)) } }
                                }
                                Caption(p, "Where the date, clock, weather and favorites sit on Home.")
                            }
                            settingsBlock {
                                SettingsGroup(p, "Clock style") {
                                    SwitchRow("Show clock", cfg.showClock, p) { onConfig(cfg.copy(showClock = it)) }
                                    if (!cfg.showClock) {
                                        SwitchRow("Center apps on screen", cfg.centerApps, p) { onConfig(cfg.copy(centerApps = it)) }
                                    }
                                    ClockStyle.entries.forEach { s ->
                                        OptionRow(s.label, s == cfg.clock, p, enabled = cfg.showClock) { onConfig(cfg.copy(clock = s)) }
                                    }
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
                                SettingsGroup(p, "Background") {
                                    SwitchRow("Pitch black", cfg.pitchBlack, p) { onConfig(cfg.copy(pitchBlack = it)) }
                                }
                                Caption(p, "Turns the dark grey background true black. Only applies in dark mode.")
                            }
                            settingsBlock {
                                SettingsGroup(p, "Font") {
                                    val fc = LocalContext.current
                                    val fontPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                                        if (uri != null) {
                                            val name = CustomFont.import(fc, uri)
                                            if (name != null) {
                                                onConfig(cfg.copy(font = AppFont.CUSTOM, customFontName = name, customFontStamp = System.currentTimeMillis()))
                                            } else {
                                                Toast.makeText(fc, "That doesn't look like a font file (.ttf or .otf)", Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    }
                                    AppFont.entries.filter { it != AppFont.CUSTOM }.forEach { f ->
                                        OptionRow(f.label, f == cfg.font, p, family = f.family) { onConfig(cfg.copy(font = f)) }
                                    }
                                    if (cfg.customFontName.isNotEmpty()) {
                                        OptionRow(cfg.customFontName, cfg.font == AppFont.CUSTOM, p, family = CustomFont.family) {
                                            onConfig(cfg.copy(font = AppFont.CUSTOM))
                                        }
                                    }
                                    ActionRow(if (cfg.customFontName.isEmpty()) "Add your own font file" else "Replace custom font", p) {
                                        fontPicker.launch(arrayOf("*/*"))
                                    }
                                }
                                Caption(p, "Pick a .ttf or .otf file. It is copied into the app, so you can delete the original.")
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
                                SettingsGroup(p, "Backup") {
                                    val c = LocalContext.current
                                    val exportLauncher = rememberLauncherForActivityResult(
                                        ActivityResultContracts.CreateDocument("application/json"),
                                    ) { uri ->
                                        if (uri != null) {
                                            val ok = try {
                                                c.contentResolver.openOutputStream(uri)?.use { it.write(exportSettings(c).toByteArray()) }
                                                true
                                            } catch (_: Exception) { false }
                                            Toast.makeText(c, if (ok) "Settings saved" else "Couldn't save the file", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                    val importLauncher = rememberLauncherForActivityResult(
                                        ActivityResultContracts.OpenDocument(),
                                    ) { uri ->
                                        if (uri != null) {
                                            val text = try {
                                                c.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                                            } catch (_: Exception) { null }
                                            if (text != null && importSettings(c, text)) {
                                                Toast.makeText(c, "Settings restored", Toast.LENGTH_SHORT).show()
                                                var a: Context? = c
                                                while (a is android.content.ContextWrapper && a !is Activity) a = a.baseContext
                                                (a as? Activity)?.recreate()
                                            } else {
                                                Toast.makeText(c, "That isn't a MinimalYou backup", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                    ActionRow("Export settings", p) { exportLauncher.launch("minimalyou-backup.json") }
                                    ActionRow("Import settings", p) { importLauncher.launch(arrayOf("*/*")) }
                                }
                                Caption(p, "Saves your favorites and every setting to a file you can restore later or on another phone. Widgets aren't included.")
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
