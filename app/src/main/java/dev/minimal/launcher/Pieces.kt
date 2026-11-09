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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlin.math.abs
import androidx.compose.ui.unit.IntOffset
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
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
// ---------- Long-press menu on Home ----------

@Composable
internal fun AppMenu(
    p: Palette,
    app: App,
    onDismiss: () -> Unit,
    onInfo: () -> Unit,
    onRemove: () -> Unit,
    onHide: () -> Unit,
    onUninstall: () -> Unit,
    onRename: (() -> Unit)? = null,
    /** Latest notification (title, text) when "Notification peek" is on and the app has one. */
    peek: Pair<String, String>? = null,
    onPeek: () -> Unit = {},
    /** null = app locking is off; otherwise whether this app is locked. */
    locked: Boolean? = null,
    onToggleLock: () -> Unit = {},
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .pointerInput(Unit) { detectTapGestures { onDismiss() } }
            .windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout))
            .padding(horizontal = 32.dp, vertical = 24.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(p.pill, RoundedCornerShape(28.dp))
                .pointerInput(Unit) { detectTapGestures { } }
                .padding(horizontal = 24.dp, vertical = 12.dp),
        ) {
            if (peek != null) {
                // The latest notification sits at the very top, set apart from the actions by a faint line.
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .background(p.bg.copy(alpha = 0.5f), RoundedCornerShape(18.dp))
                        .softClick(role = Role.Button, onClick = onPeek)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    if (peek.first.isNotBlank()) BasicText(peek.first, maxLines = 1, overflow = TextOverflow.Ellipsis, style = ts(p.ink, 17.sp, FontWeight.Medium))
                    if (peek.second.isNotBlank()) BasicText(peek.second, maxLines = 3, overflow = TextOverflow.Ellipsis, style = ts(p.muted, 15.sp, FontWeight.Normal))
                }
                Box(Modifier.fillMaxWidth().padding(top = 10.dp).height(1.dp).background(p.muted.copy(alpha = 0.18f)))
            }
            BasicText(
                if (LocalLowercase.current) app.label.lowercase() else app.label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = ts(p.muted, 14.sp, FontWeight.Medium, 0.5.sp),
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
            )
            if (onRename != null) MenuItem("Edit name", p.ink, onRename)
            MenuItem("App info", p.ink, onInfo)
            if (locked != null) MenuItem(if (locked) "Unlock app" else "Lock app", p.ink, onToggleLock)
            MenuItem("Remove from Home", p.ink, onRemove)
            MenuItem("Hide app", p.ink, onHide)
            MenuItem("Uninstall", p.accent, onUninstall)
        }
    }
}

@Composable
internal fun MenuItem(label: String, color: Color, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).softClick(role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(label, style = ts(color, 20.sp))
    }
}

// ---------- Pieces ----------

internal enum class SettingsPage(val title: String) {
    FAVORITES("Favorites"),
    HOME("Home screen"),
    APPS("Apps"),
    GESTURES("Gestures and panels"),
    SEARCH("Search"),
    LOOK("Look and feel"),
    ORGANIZE("App organization"),
    EXTRAS("Extras"),
}

internal fun androidx.compose.foundation.lazy.LazyListScope.settingsBlock(content: @Composable ColumnScope.() -> Unit) {
    item { Column(Modifier.fillMaxWidth(), content = content) }
}

internal fun LazyItemScope.itemAnim(): Modifier = if (animationsOn) Modifier.animateItem() else Modifier

/** A titled, rounded block of related settings. */
@Composable
internal fun SettingsGroup(p: Palette, title: String? = null, content: @Composable ColumnScope.() -> Unit) {
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
internal fun Caption(p: Palette, text: String) {
    BasicText(
        text,
        style = ts(p.muted, 13.sp, FontWeight.Normal),
        modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp),
    )
}

@Composable
internal fun SideChevron(left: Boolean, color: Color) {
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
internal fun SectionTitle(text: String, p: Palette) {
    BasicText(
        text.uppercase(),
        style = ts(p.accent, 13.sp, FontWeight.Medium, 1.sp),
        modifier = Modifier.padding(top = 28.dp, bottom = 4.dp),
    )
}

@Composable
internal fun ActionRow(label: String, p: Palette, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).softClick(role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(label, style = ts(p.ink, 20.sp), modifier = Modifier.weight(1f))
        SideChevron(left = false, color = p.muted)
    }
}

@Composable
internal fun OptionRow(
    label: String,
    selected: Boolean,
    p: Palette,
    family: FontFamily? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            // Disabled rows are dimmed and ignore touches completely.
            .alpha(if (enabled) 1f else 0.4f)
            .then(if (enabled) Modifier.softClick(role = Role.RadioButton, onClick = onClick) else Modifier),
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
internal fun SwitchRow(label: String, checked: Boolean, p: Palette, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(label, style = ts(p.ink, 20.sp), modifier = Modifier.weight(1f).padding(end = 20.dp))
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
internal fun RadioDot(selected: Boolean, p: Palette) {
    val color by animateColorAsState(if (selected) p.accent else p.muted, tw(160), label = "radioColor")
    val dot by animateFloatAsState(if (selected) 0.24f else 0f, sp(0.7f, 800f), label = "radioDot")
    Canvas(Modifier.size(20.dp)) {
        val s = size.minDimension
        drawCircle(color, radius = s * 0.45f, style = Stroke(s * 0.1f))
        if (dot > 0.001f) drawCircle(p.accent, radius = s * dot)
    }
}

internal enum class Glyph { UP, DOWN, CLOSE, PLUS }

@Composable
internal fun IconBtn(glyph: Glyph, description: String, color: Color, enabled: Boolean = true, onClick: () -> Unit) {
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
internal fun Pill(p: Palette, modifier: Modifier = Modifier, color: Color = p.pill, content: @Composable RowScope.() -> Unit) {
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
internal fun SearchIcon(color: Color) {
    Canvas(Modifier.size(20.dp)) {
        val s = size.minDimension
        val w = s * 0.09f
        drawCircle(color, radius = s * 0.32f, center = Offset(s * 0.43f, s * 0.43f), style = Stroke(w))
        drawLine(color, Offset(s * 0.66f, s * 0.66f), Offset(s * 0.9f, s * 0.9f), strokeWidth = w, cap = StrokeCap.Round)
    }
}

@Composable
internal fun WebRow(p: Palette, text: String, onClick: () -> Unit) {
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
internal fun TextRow(text: String, sub: String?, p: Palette, onClick: () -> Unit) {
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
internal fun AppRow(
    app: App,
    size: TextUnit,
    color: Color,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    weight: FontWeight = FontWeight.Light,
    marker: Color? = null,
    modifier: Modifier = Modifier,
    // Home keeps the tap target on the name only; lists (drawer, search, pickers) use the whole row.
    fullWidth: Boolean = false,
    minHeight: androidx.compose.ui.unit.Dp? = null,
    // A wider tap area for short names on Home (still not the whole row), and where the name sits inside it.
    minWidth: androidx.compose.ui.unit.Dp = 0.dp,
    contentAlign: Alignment.Horizontal = Alignment.Start,
    // Text typed by the user to show instead of the app's own name.
    label: String? = null,
    // Folders only: null = not a folder, false = closed (arrow down), true = open (arrow up).
    arrowUp: Boolean? = null,
) {
    var textCoords by remember { mutableStateOf<androidx.compose.ui.layout.LayoutCoordinates?>(null) }
    // Where the middle of the letters really is inside the text box (it differs per font), so the dot lines up with them.
    var dotShift by remember { mutableStateOf(0f) }
    val lower = LocalLowercase.current
    val sizePx = with(LocalDensity.current) { size.toPx() }
    val click = {
        // Remember where this name is, so the opening app can grow out of it.
        textCoords?.takeIf { it.isAttached }?.let { tappedTextBounds = it.boundsInRoot() }
        onClick()
    }
    Row(
        modifier
            .then(if (fullWidth) Modifier.fillMaxWidth() else Modifier)
            .heightIn(min = minHeight ?: if (fullWidth) 56.dp else 48.dp)
            .widthIn(min = minWidth)
            .softCombined(onClick = click, onLongClick = onLongClick),
        horizontalArrangement = when (contentAlign) {
            Alignment.CenterHorizontally -> Arrangement.Center
            Alignment.End -> Arrangement.End
            else -> Arrangement.Start
        },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val shownLabel = label ?: app.label
        if (LocalShowIcons.current && app.component.packageName != "folder") {
            AppIcon(app.component, with(LocalDensity.current) { size.toDp() } * 0.85f)
            Spacer(Modifier.width(12.dp))
        }
        BasicText(
            if (lower) shownLabel.lowercase() else shownLabel,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = ts(color, size, weight),
            onTextLayout = { layout ->
                val letterMiddle = layout.firstBaseline - sizePx * 0.32f
                dotShift = letterMiddle - layout.size.height / 2f
            },
            modifier = Modifier.weight(1f, fill = false).onGloballyPositioned { textCoords = it },
        )
        if (marker != null) {
            // The dot takes no room in the row: it hangs just after the name, so the name itself stays exactly
            // where it would be without a dot (important when the list is centered).
            Canvas(
                Modifier
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        val gap = 12.dp.roundToPx()
                        layout(0, placeable.height) { placeable.place(gap, 0) }
                    }
                    .size(8.dp)
                    .offset { IntOffset(0, dotShift.roundToInt()) },
            ) { drawCircle(marker) }
        }
        if (arrowUp != null) {
            // Like the dot, the arrow hangs after the name and takes no room, so the name stays put.
            val turn by animateFloatAsState(if (arrowUp) 180f else 0f, sp(0.75f, 450f), label = "folderArrow")
            Canvas(
                Modifier
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        val gap = 12.dp.roundToPx()
                        layout(0, placeable.height) { placeable.place(gap, 0) }
                    }
                    .size(16.dp)
                    .offset { IntOffset(0, dotShift.roundToInt()) }
                    .graphicsLayer { rotationZ = turn },
            ) {
                // "this.size": the row's own text-size parameter is also called size.
                val w = this.size.width
                val h = this.size.height
                val sw = w * 0.14f
                val c = color.copy(alpha = 0.75f)
                drawLine(c, Offset(w * 0.15f, h * 0.35f), Offset(w * 0.5f, h * 0.7f), strokeWidth = sw, cap = StrokeCap.Round)
                drawLine(c, Offset(w * 0.5f, h * 0.7f), Offset(w * 0.85f, h * 0.35f), strokeWidth = sw, cap = StrokeCap.Round)
            }
        }
    }
}

@Composable
internal fun HiddenAppsScreen(p: Palette, apps: List<App>, hidden: Set<String>, onToggle: (String) -> Unit, onBack: () -> Unit, title: String = "Hidden apps") {
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
        BasicText(title, style = ts(p.ink, 28.sp))
        LazyColumn(contentPadding = PaddingValues(top = 12.dp, bottom = 32.dp)) {
            items(apps, key = { it.key }) { app ->
                SwitchRow(app.label, app.key in hidden, p) { onToggle(app.key) }
            }
        }
    }
}

@Composable
internal fun AppPicker(p: Palette, slot: Slot, apps: List<App>, onPick: (App?) -> Unit, onCancel: () -> Unit) {
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
                AppRow(app, 22.sp, p.ink, onClick = { onPick(app) }, fullWidth = true)
            }
        }
    }
}


/** A small card for typing the name shown on Home for one app. Emoji work too; the keyboard decides. */
@Composable
internal fun RenameOverlay(
    p: Palette,
    app: App,
    current: String?,
    onSave: (String) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    val start = current ?: app.label
    var value by remember { mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(start, androidx.compose.ui.text.TextRange(0, start.length))) }
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { focus.requestFocus(); keyboard?.show() }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .pointerInput(Unit) { detectTapGestures { onDismiss() } }
            .windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout))
            .imePadding()
            .padding(horizontal = 32.dp, vertical = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(p.pill, RoundedCornerShape(28.dp))
                .pointerInput(Unit) { detectTapGestures { } }
                .padding(horizontal = 24.dp, vertical = 20.dp),
        ) {
            BasicText("Edit name", style = ts(p.muted, 14.sp, FontWeight.Medium, 0.5.sp))
            Spacer(Modifier.height(12.dp))
            BasicTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                textStyle = ts(p.ink, 26.sp, FontWeight.Normal),
                cursorBrush = SolidColor(p.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onSave(value.text.trim()) }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
            Spacer(Modifier.height(4.dp))
            BasicText("Type a new name, or add an emoji.", style = ts(p.muted, 13.sp, FontWeight.Normal))
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (current != null) {
                    BasicText("Reset", style = ts(p.muted, 18.sp), modifier = Modifier.softClick(role = Role.Button, onClick = onReset).padding(horizontal = 16.dp, vertical = 12.dp))
                }
                BasicText("Save", style = ts(p.accent, 18.sp, FontWeight.Medium), modifier = Modifier.softClick(role = Role.Button) { onSave(value.text.trim()) }.padding(horizontal = 16.dp, vertical = 12.dp))
            }
        }
    }
}


// ---------- Extras: icons, folders, timer, tutorial ----------

/** True when small monochrome icons are shown beside app names. */
internal val LocalShowIcons = compositionLocalOf { false }

private val iconCache = HashMap<String, androidx.compose.ui.graphics.ImageBitmap>()

/** A small grayscale app icon, loaded off the main thread and remembered. */
@Composable
internal fun AppIcon(component: android.content.ComponentName, size: androidx.compose.ui.unit.Dp) {
    val ctx = LocalContext.current
    val key = component.flattenToString()
    var bmp by remember(key) { mutableStateOf(iconCache[key]) }
    LaunchedEffect(key) {
        if (bmp == null) {
            bmp = withContext(Dispatchers.Default) {
                try {
                    val d = ctx.packageManager.getActivityIcon(component)
                    val b = android.graphics.Bitmap.createBitmap(96, 96, android.graphics.Bitmap.Config.ARGB_8888)
                    d.setBounds(0, 0, 96, 96)
                    d.draw(android.graphics.Canvas(b))
                    b.asImageBitmap()
                } catch (_: Exception) { null }
            }?.also { iconCache[key] = it }
        }
    }
    Box(Modifier.size(size)) {
        val b = bmp
        if (b != null) {
            androidx.compose.foundation.Image(
                bitmap = b,
                contentDescription = null,
                colorFilter = androidx.compose.ui.graphics.ColorFilter.colorMatrix(androidx.compose.ui.graphics.ColorMatrix().apply { setToSaturation(0f) }),
                alpha = 0.85f,
                modifier = Modifier.fillMaxSize().clip(androidx.compose.foundation.shape.CircleShape),
            )
        }
    }
}

/** The apps in a folder, as a card at the bottom of the screen. */
@Composable
internal fun FolderSheet(p: Palette, name: String, apps: List<App>, onLaunch: (App) -> Unit, onEdit: () -> Unit, onDismiss: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .pointerInput(Unit) { detectTapGestures { onDismiss() } }
            .windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout))
            .padding(horizontal = 32.dp, vertical = 24.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(p.pill, RoundedCornerShape(28.dp))
                .pointerInput(Unit) { detectTapGestures { } }
                .padding(horizontal = 24.dp, vertical = 12.dp),
        ) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                BasicText(name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = ts(p.muted, 14.sp, FontWeight.Medium, 0.5.sp), modifier = Modifier.weight(1f))
                BasicText("Edit", style = ts(p.accent, 15.sp, FontWeight.Medium), modifier = Modifier.softClick(role = Role.Button, onClick = onEdit).padding(8.dp))
            }
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                apps.forEach { app ->
                    AppRow(app, 24.sp, p.ink, onClick = { onLaunch(app) }, fullWidth = true, weight = FontWeight.Normal)
                }
            }
        }
    }
}

/** Create or change a folder: a name and the apps in it. */
@Composable
internal fun FolderEditor(
    p: Palette,
    initialName: String?,
    apps: List<App>,
    initialKeys: List<String>,
    onSave: (String, List<String>) -> Unit,
    onDelete: (() -> Unit)?,
    onCancel: () -> Unit,
) {
    var name by remember { mutableStateOf(initialName ?: "") }
    var query by remember { mutableStateOf("") }
    val chosen = remember { mutableStateListOf<String>().apply { addAll(initialKeys) } }
    Column(
        Modifier
            .fillMaxSize()
            .background(p.bg)
            .windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout))
            .imePadding()
            .padding(horizontal = 32.dp)
            .pointerInput(Unit) { detectTapGestures { } },
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicText(
                "Cancel", style = ts(p.accent, 16.sp, FontWeight.Medium),
                modifier = Modifier.heightIn(min = 52.dp).softClick(role = Role.Button, onClick = onCancel).wrapContentHeight(Alignment.CenterVertically),
            )
            Spacer(Modifier.weight(1f))
            val ok = name.isNotBlank() && chosen.isNotEmpty()
            BasicText(
                "Save", style = ts(if (ok) p.accent else p.muted.copy(alpha = 0.5f), 16.sp, FontWeight.Medium),
                modifier = Modifier.heightIn(min = 52.dp).then(if (ok) Modifier.softClick(role = Role.Button) { onSave(name.trim(), chosen.toList()) } else Modifier).wrapContentHeight(Alignment.CenterVertically),
            )
        }
        BasicText(if (initialName == null) "New folder" else "Edit folder", style = ts(p.ink, 28.sp))
        Spacer(Modifier.height(12.dp))
        Pill(p) {
            BasicTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                textStyle = ts(p.ink, 18.sp, FontWeight.Normal),
                cursorBrush = SolidColor(p.accent),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (name.isEmpty()) BasicText("Folder name", style = ts(p.muted, 18.sp, FontWeight.Normal))
                        inner()
                    }
                },
            )
        }
        Spacer(Modifier.height(10.dp))
        Pill(p) {
            SearchIcon(p.accent)
            Spacer(Modifier.width(12.dp))
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                textStyle = ts(p.ink, 16.sp, FontWeight.Normal),
                cursorBrush = SolidColor(p.accent),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (query.isEmpty()) BasicText("Search apps to add", style = ts(p.muted, 16.sp, FontWeight.Normal))
                        inner()
                    }
                },
            )
        }
        BasicText(
            if (chosen.isEmpty()) "Pick at least one app" else "${chosen.size} selected",
            style = ts(p.muted, 14.sp, FontWeight.Normal),
            modifier = Modifier.padding(top = 10.dp),
        )
        val visible = remember(apps, query) { if (query.isBlank()) apps else apps.filter { it.label.contains(query.trim(), ignoreCase = true) } }
        LazyColumn(contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp), modifier = Modifier.weight(1f)) {
            items(visible, key = { it.key }) { app ->
                SwitchRow(app.label, app.key in chosen, p) { if (app.key in chosen) chosen.remove(app.key) else chosen.add(app.key) }
            }
            if (onDelete != null) {
                item {
                    BasicText(
                        "Delete folder", style = ts(p.accent, 18.sp, FontWeight.Medium),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).softClick(role = Role.Button, onClick = onDelete).wrapContentHeight(Alignment.CenterVertically),
                    )
                }
            }
        }
    }
}

/** Preset timers, started in the clock app without opening it. */
@Composable
internal fun TimerSheet(p: Palette, onStart: (Int) -> Unit, onDismiss: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .pointerInput(Unit) { detectTapGestures { onDismiss() } }
            .windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout))
            .padding(horizontal = 32.dp, vertical = 24.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(p.pill, RoundedCornerShape(28.dp))
                .pointerInput(Unit) { detectTapGestures { } }
                .padding(horizontal = 24.dp, vertical = 20.dp),
        ) {
            BasicText("Start a timer", style = ts(p.muted, 14.sp, FontWeight.Medium, 0.5.sp))
            Spacer(Modifier.height(12.dp))
            listOf(listOf(1, 5, 10), listOf(15, 30, 60)).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { m ->
                        Box(
                            Modifier
                                .weight(1f)
                                .height(56.dp)
                                .background(p.bg.copy(alpha = 0.55f), RoundedCornerShape(20.dp))
                                .softClick(role = Role.Button) { onStart(m) },
                            contentAlignment = Alignment.Center,
                        ) {
                            BasicText(if (m == 60) "1 hr" else "$m min", style = ts(p.ink, 20.sp, FontWeight.Normal))
                        }
                    }
                }
            }
        }
    }
}

/** A short walkthrough for a fresh install. Always skippable. */
@Composable
internal fun Tutorial(p: Palette, onDone: () -> Unit) {
    val steps = listOf(
        "Welcome to MinimalYou" to "A calm home screen made of words. This takes about 20 seconds, and you can skip it.",
        "Open apps" to "Tap a name on Home to open it. Touch and hold a name to edit it, lock it, hide it or remove it.",
        "All apps" to "Swipe up from the bottom for the full list, or from the right if you changed that in Settings. Slide along the letters on the edge to jump.",
        "Search" to "Tap the search bar. It finds apps, contacts, files and the web, and does sums: try 12*7.",
        "Widgets and settings" to "Swipe right from Home for widgets. Double-tap an empty spot to lock the screen, or touch and hold it to open Settings, where every feature has its own switch.",
    )
    var i by remember { mutableStateOf(0) }
    val last = i == steps.lastIndex
    Column(
        Modifier
            .fillMaxSize()
            .background(p.bg)
            .windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout))
            .pointerInput(Unit) { detectTapGestures { } }
            .padding(horizontal = 32.dp, vertical = 16.dp),
    ) {
        Row(Modifier.fillMaxWidth()) {
            Spacer(Modifier.weight(1f))
            BasicText(
                "Skip", style = ts(p.muted, 16.sp, FontWeight.Medium),
                modifier = Modifier.heightIn(min = 52.dp).softClick(role = Role.Button, onClick = onDone).padding(horizontal = 8.dp).wrapContentHeight(Alignment.CenterVertically),
            )
        }
        Spacer(Modifier.weight(1f))
        AnimatedContent(
            targetState = i,
            transitionSpec = { (fadeIn(tw(260)) + slideInHorizontally(tw(300, easing = EmphasizedDecel)) { it / 8 }) togetherWith fadeOut(tw(120)) },
            label = "tutorialStep",
        ) { step ->
            Column {
                BasicText(steps[step].first, style = ts(p.ink, 38.sp, FontWeight.Light, (-1).sp, 44.sp))
                Spacer(Modifier.height(16.dp))
                BasicText(steps[step].second, style = ts(p.muted, 18.sp, FontWeight.Normal, lineHeight = 26.sp))
            }
        }
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                steps.indices.forEach { n ->
                    Box(Modifier.size(8.dp).background(if (n == i) p.accent else p.muted.copy(alpha = 0.35f), CircleShape))
                }
            }
            BasicText(
                if (last) "Get started" else "Next",
                style = ts(p.bg, 17.sp, FontWeight.Medium),
                modifier = Modifier
                    .background(p.accent, RoundedCornerShape(28.dp))
                    .softClick(role = Role.Button) { if (last) onDone() else i++ }
                    .padding(horizontal = 28.dp, vertical = 14.dp),
            )
        }
    }
}
