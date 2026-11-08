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
// ---------- Universal search ----------

@Composable
internal fun SearchScreen(
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
        else rankApps(ctx, apps, q, 6)
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
