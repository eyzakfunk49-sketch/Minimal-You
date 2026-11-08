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
            BasicText(
                app.label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = ts(p.muted, 14.sp, FontWeight.Medium, 0.5.sp),
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
            )
            MenuItem("App info", p.ink, onInfo)
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
internal fun OptionRow(label: String, selected: Boolean, p: Palette, family: FontFamily? = null, onClick: () -> Unit) {
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
internal fun SwitchRow(label: String, checked: Boolean, p: Palette, onChange: (Boolean) -> Unit) {
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
) {
    Row(
        modifier
            .then(if (fullWidth) Modifier.fillMaxWidth() else Modifier)
            .heightIn(min = if (fullWidth) 56.dp else 48.dp)
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
internal fun HiddenAppsScreen(p: Palette, apps: List<App>, hidden: Set<String>, onToggle: (String) -> Unit, onBack: () -> Unit) {
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
