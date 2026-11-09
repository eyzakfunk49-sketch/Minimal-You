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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.zIndex
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.foundation.gestures.detectDragGestures
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
    onEditFolder: (String?) -> Unit = {},
    onPickLocked: () -> Unit = {},
    onShowTutorial: () -> Unit = {},
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

    val bgPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            if (saveHomeBackground(ctx, uri)) onConfig(cfg.copy(wallpaperBg = true, bgCurrent = false, bgStamp = System.currentTimeMillis()))
            else Toast.makeText(ctx, "That doesn't look like a picture", Toast.LENGTH_LONG).show()
        }
    }
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

    // Checked fresh on every tap, so a permission granted a moment ago is never asked for again.
    fun hasNotifAccess() = NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)

    fun move(i: Int, delta: Int) {
        val j = i + delta
        if (j !in favKeys.indices) return
        val list = favKeys.toMutableList()
        val t = list[i]; list[i] = list[j]; list[j] = t
        onSetFavs(list)
    }

    // Drag-to-reorder for the favorites list.
    var dragKey by remember { mutableStateOf<String?>(null) }
    var dragY by remember { mutableFloatStateOf(0f) }
    var rowStep by remember { mutableFloatStateOf(0f) }
    val latestKeys by rememberUpdatedState(favKeys)
    val latestSet by rememberUpdatedState(onSetFavs)

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

    BackHandler(enabled = page != null && !coveredByScreen) { page = settingsParent(page) }

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
                val forward = settingsDepth(targetState) > settingsDepth(initialState)
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
                            .softClick(role = Role.Button) { page = settingsParent(target) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SideChevron(left = true, color = p.accent)
                        Spacer(Modifier.width(2.dp))
                        BasicText(settingsParent(target)?.title ?: "Settings", style = ts(p.accent, 15.sp, FontWeight.Medium))
                    }
                    BasicText(target.title, style = ts(p.ink, 34.sp), modifier = Modifier.padding(start = 8.dp, bottom = 8.dp))
                }

                val listState = rememberLazyListState()
                LazyColumn(Modifier.weight(1f).fadeEdges(listState), state = listState, contentPadding = PaddingValues(bottom = 40.dp)) {
                    when (target) {
                        null -> {
                            val cats = listOf(
                                Triple(SettingsPage.HOME, "Home screen", "Clock, date, weather, calendar"),
                                Triple(SettingsPage.APPS, "Apps", "Hidden apps, folders, locking, dots"),
                                Triple(SettingsPage.GESTURES, "Gestures and panels", if (cfg.drawerRight) "All apps from the right" else "All apps from the bottom"),
                                Triple(SettingsPage.SEARCH, "Search", "Sources and search engine"),
                                Triple(SettingsPage.LOOK, "Look and feel", "Font, icons, wallpaper, animations"),
                                Triple(SettingsPage.EXTRAS, "Extras", "Timer, search extras, backup, tutorial"),
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
                            item(key = "cap") { Caption(p, "Up to $maxFavs apps show on Home. Reorder them, and any folders, with the arrows or by dragging the handle." + if (cfg.showClock) " Hide the clock to fit 9." else "") }
                            if (favorites.isEmpty()) {
                                item(key = "empty") { Caption(p, "No favorites yet. Add some below.") }
                            }
                            itemsIndexed(favorites, key = { _, a -> a.key }) { i, app ->
                                val dragging = dragKey == app.key
                                val isFolder = app.component.packageName == "folder"
                                Row(
                                    (if (dragging) Modifier else itemAnim())
                                        .zIndex(if (dragging) 1f else 0f)
                                        .graphicsLayer {
                                            translationY = if (dragging) dragY else 0f
                                            scaleX = if (dragging) 1.02f else 1f
                                            scaleY = if (dragging) 1.02f else 1f
                                        }
                                        .onSizeChanged { rowStep = it.height.toFloat() }
                                        .padding(vertical = 4.dp)
                                        .fillMaxWidth()
                                        .background(if (dragging) p.pill else p.pill.copy(alpha = 0.55f), RoundedCornerShape(20.dp))
                                        .padding(start = 20.dp, end = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                        BasicText(
                                            app.label, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                            style = ts(p.ink, 20.sp),
                                            modifier = Modifier.weight(1f, fill = false),
                                        )
                                        if (isFolder) {
                                            BasicText("folder", style = ts(p.muted, 14.sp, FontWeight.Normal), modifier = Modifier.padding(start = 8.dp))
                                        }
                                    }
                                    IconBtn(Glyph.UP, "Move ${app.label} up", if (i > 0) p.muted else p.muted.copy(alpha = 0.3f), i > 0) { move(i, -1) }
                                    IconBtn(Glyph.DOWN, "Move ${app.label} down", if (i < favorites.lastIndex) p.muted else p.muted.copy(alpha = 0.3f), i < favorites.lastIndex) { move(i, 1) }
                                    if (isFolder) Spacer(Modifier.width(48.dp))
                                    else IconBtn(Glyph.CLOSE, "Remove ${app.label}", p.muted) { onSetFavs(favKeys - app.key) }
                                    DragGrip(
                                        p.muted,
                                        Modifier.pointerInput(app.key) {
                                            detectDragGestures(
                                                onDragStart = { dragKey = app.key; dragY = 0f },
                                                onDragEnd = { dragKey = null; dragY = 0f },
                                                onDragCancel = { dragKey = null; dragY = 0f },
                                            ) { change, amount ->
                                                change.consume()
                                                dragY += amount.y
                                                val at = latestKeys.indexOf(app.key)
                                                if (rowStep > 0f && at >= 0) {
                                                    val to = when {
                                                        dragY > rowStep / 2 && at < latestKeys.lastIndex -> at + 1
                                                        dragY < -rowStep / 2 && at > 0 -> at - 1
                                                        else -> at
                                                    }
                                                    if (to != at) {
                                                        val list = latestKeys.toMutableList()
                                                        val t = list[at]; list[at] = list[to]; list[to] = t
                                                        latestSet(list)
                                                        dragY -= (to - at) * rowStep
                                                    }
                                                }
                                            }
                                        },
                                    )
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
                                SettingsGroup(p, "Media player") {
                                    SwitchRow("Show media player", cfg.mediaPlayer, p) { on ->
                                        onConfig(cfg.copy(mediaPlayer = on))
                                        if (on && !hasNotifAccess()) {
                                            ctx.startActivity(
                                                Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                            )
                                        }
                                    }
                                }
                                Caption(
                                    p,
                                    if (!cfg.mediaPlayer) "A small player with play, pause, skip and a seek bar appears on Home while something is playing."
                                    else if (notifGranted) "Shows on Home while something is playing. It only reads the song name, artist and controls, never notification text."
                                    else "Needs notification access to see what's playing. Turn on MinimalYou in the list that opens.",
                                )
                            }
                            settingsBlock {
                                SettingsGroup(p, "Alignment") {
                                    HomeAlign.entries.forEach { a -> OptionRow(a.label, a == cfg.homeAlign, p) { onConfig(cfg.copy(homeAlign = a)) } }
                                }
                                Caption(p, "Where the date, clock, weather and favorites sit on Home.")
                            }
                            settingsBlock {
                                SettingsGroup(p, "Clock style") {
                                    SwitchRow("Show clock", cfg.showClock, p) { onConfig(cfg.copy(showClock = it)) }
                                    Reveal(!cfg.showClock) {
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
                                    Reveal(cfg.hideStatusBar) {
                                        SwitchRow("Battery on Home", cfg.showBattery, p) { onConfig(cfg.copy(showBattery = it)) }
                                    }
                                }
                                Reveal(cfg.hideStatusBar) {
                                    Caption(p, "The battery level shows next to the weather on Home. Swipe down from the very top edge to peek at the real status bar.")
                                }
                            }
                        }

                        SettingsPage.APPS -> {
                            settingsBlock {
                                SettingsGroup(p, "Home") {
                                    Row(
                                        Modifier.fillMaxWidth().heightIn(min = 52.dp).softClick(role = Role.Button) { page = SettingsPage.FAVORITES },
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        BasicText("Favorites", style = ts(p.ink, 20.sp), modifier = Modifier.weight(1f))
                                        BasicText(if (favorites.isEmpty()) "None yet" else "${favorites.size} on Home", style = ts(p.muted, 16.sp, FontWeight.Normal))
                                        Spacer(Modifier.width(8.dp))
                                        SideChevron(left = false, color = p.muted)
                                    }
                                }
                            }
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
                                        if (on && !hasNotifAccess()) {
                                            ctx.startActivity(
                                                Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                            )
                                        }
                                    }
                                    SwitchRow("Notification peek", cfg.notifPeek, p) { on ->
                                        onConfig(cfg.copy(notifPeek = on))
                                        if (on && !hasNotifAccess()) {
                                            ctx.startActivity(
                                                Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                            )
                                        }
                                    }
                                }
                                Caption(
                                    p,
                                    if (notifGranted) "Notification access is on. Dots only check which apps have a notification. Notification peek also reads the latest title and text, shows it at the top of an app's touch-and-hold menu, and keeps it in memory only."
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
                            settingsBlock {
                                SettingsGroup(p, "Folders") {
                                    SwitchRow("Folders", cfg.foldersOn, p) { onConfig(cfg.copy(foldersOn = it)) }
                                    Reveal(cfg.foldersOn) {
                                        cfg.folders.keys.forEach { name ->
                                            Row(
                                                Modifier.fillMaxWidth().heightIn(min = 52.dp).softClick(role = Role.Button) { onEditFolder(name) },
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                BasicText(name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = ts(p.ink, 20.sp), modifier = Modifier.weight(1f))
                                                BasicText("${cfg.folders[name]?.size ?: 0} apps", style = ts(p.muted, 16.sp, FontWeight.Normal))
                                                Spacer(Modifier.width(8.dp))
                                                SideChevron(left = false, color = p.muted)
                                            }
                                        }
                                        ActionRow("New folder", p) { onEditFolder(null) }
                                    }
                                }
                                Caption(p, "Folders show on Home among your favorites and count toward its limit. Move them in Favorites. Tap one to open it, or touch and hold to edit it.")
                            }
                            settingsBlock {
                                SettingsGroup(p, "App locking") {
                                    SwitchRow("Lock apps with fingerprint", cfg.appLock, p) { onConfig(cfg.copy(appLock = it)) }
                                    Reveal(cfg.appLock) {
                                        Row(
                                            Modifier.fillMaxWidth().heightIn(min = 52.dp).softClick(role = Role.Button, onClick = onPickLocked),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            BasicText("Choose apps to lock", style = ts(p.ink, 20.sp), modifier = Modifier.weight(1f))
                                            BasicText(if (cfg.locked.isEmpty()) "None" else "${cfg.locked.size}", style = ts(p.muted, 16.sp, FontWeight.Normal))
                                            Spacer(Modifier.width(8.dp))
                                            SideChevron(left = false, color = p.muted)
                                        }
                                    }
                                }
                                Caption(p, "Locked apps ask for your fingerprint or screen lock before opening from Home, the app list or search. You can also touch and hold an app on Home to lock it.")
                            }
                        }

                        SettingsPage.EXTRAS -> {
                            settingsBlock {
                                SettingsGroup(p, "Clock") {
                                    SwitchRow("Quick timer on the clock", cfg.quickTimer, p) { onConfig(cfg.copy(quickTimer = it)) }
                                }
                                Caption(p, "Touch and hold the clock on Home to start a timer.")
                            }
                            settingsBlock {
                                SettingsGroup(p, "Search and apps") {
                                    SwitchRow("Search history", cfg.searchHistory, p) { onConfig(cfg.copy(searchHistory = it)) }
                                    SwitchRow("Calculator in search", cfg.calculator, p) { onConfig(cfg.copy(calculator = it)) }
                                    SwitchRow("Recent apps in the app list", cfg.recentApps, p) { onConfig(cfg.copy(recentApps = it)) }
                                }
                                Caption(p, "Type a sum such as 12*7 or (3+4)^2 in search to get an answer you can tap to copy.")
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
                                SettingsGroup(p, "Help") {
                                    ActionRow("Show the tutorial again", p, onShowTutorial)
                                }
                            }
                        }

                        SettingsPage.GESTURES -> {
                            settingsBlock {
                                SettingsGroup(p, "All apps panel") {
                                    OptionRow("From the bottom (swipe up)", !cfg.drawerRight, p) { onConfig(cfg.copy(drawerRight = false)) }
                                    OptionRow("From the right (swipe left)", cfg.drawerRight, p) { onConfig(cfg.copy(drawerRight = true)) }
                                    Reveal(cfg.drawerRight) {
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
                                SettingsGroup(p, "App names") {
                                    SwitchRow("Show in lowercase", cfg.lowercaseApps, p) { onConfig(cfg.copy(lowercaseApps = it)) }
                                }
                            }
                            settingsBlock {
                                SettingsGroup(p, "Background") {
                                    SwitchRow("Pitch black", cfg.pitchBlack, p) { onConfig(cfg.copy(pitchBlack = it)) }
                                }
                                Caption(p, "Turns the dark grey background true black. Only applies in dark mode.")
                            }
                            settingsBlock {
                                SettingsGroup(p, "Wallpaper") {
                                    SwitchRow("Custom wallpaper", cfg.wallpaperBg, p) { on ->
                                        onConfig(cfg.copy(wallpaperBg = on))
                                    }
                                    Reveal(cfg.wallpaperBg) {
                                        SwitchRow("Use current wallpaper", cfg.bgCurrent, p) { on ->
                                            if (!on) {
                                                onConfig(cfg.copy(bgCurrent = false))
                                            } else if (!Environment.isExternalStorageManager()) {
                                                Toast.makeText(ctx, "Allow file access, then try again", Toast.LENGTH_LONG).show()
                                                ctx.startActivity(
                                                    Intent(
                                                        android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                                        Uri.parse("package:" + ctx.packageName),
                                                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                                )
                                            } else if (saveSystemWallpaper(ctx)) {
                                                onConfig(cfg.copy(bgCurrent = true, bgStamp = System.currentTimeMillis()))
                                            } else {
                                                Toast.makeText(ctx, "Android won't let apps read the current wallpaper on this phone. Choose a custom image instead.", Toast.LENGTH_LONG).show()
                                            }
                                        }
                                        Reveal(!cfg.bgCurrent) {
                                            ActionRow("Choose a custom image", p) { bgPicker.launch(arrayOf("image/*")) }
                                        }
                                        SwitchRow("Blur wallpaper", cfg.wallpaperBlur, p) { onConfig(cfg.copy(wallpaperBlur = it)) }
                                    }
                                    Reveal(!cfg.wallpaperBg) {
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
                                }
                                Caption(p, if (cfg.wallpaperBg) "Shows a picture behind the Home screen, under a soft tint so the text stays easy to read. The app list, search and Settings keep their solid background." else "Use matching wallpaper sets a plain wallpaper in the same color as Home. It switches between light and dark with your phone, and keeps your current Material You colors instead of recoloring them.")
                            }
                            settingsBlock {
                                SettingsGroup(p, "Icons") {
                                    SwitchRow("Icons", cfg.iconsOn, p) { onConfig(cfg.copy(iconsOn = it)) }
                                    Reveal(cfg.iconsOn) {
                                        OptionRow("Black and white", !cfg.iconsColor, p) { onConfig(cfg.copy(iconsColor = false)) }
                                        OptionRow("Color", cfg.iconsColor, p) { onConfig(cfg.copy(iconsColor = true)) }
                                    }
                                }
                                Caption(p, "Shows a small icon beside each app name, in black and white or in color.")
                            }
                            settingsBlock {
                                SettingsGroup(p, "Font") {
                                    AppFont.entries.forEach { f ->
                                        OptionRow(f.label, f == cfg.font, p, family = f.family) { onConfig(cfg.copy(font = f)) }
                                    }
                                }
                                if (!googleSansInstalled()) {
                                    Caption(p, "Google Sans isn't installed on this phone, so it will look like Sans here.")
                                }
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

/** Favorites lives inside Apps; every other page hangs off the main Settings list. */
private fun settingsParent(pg: SettingsPage?): SettingsPage? = if (pg == SettingsPage.FAVORITES) SettingsPage.APPS else null

private fun settingsDepth(pg: SettingsPage?): Int = when (pg) { null -> 0; SettingsPage.FAVORITES -> 2; else -> 1 }
