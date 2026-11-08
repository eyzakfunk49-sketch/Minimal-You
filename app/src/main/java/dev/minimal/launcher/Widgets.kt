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
// ---------- Widgets page ----------

internal const val WIDGET_HOST_ID = 1024

internal fun readWidgetIds(ctx: Context): List<Int> =
    (appPrefs(ctx).getString("widget_ids", "") ?: "").split(",").mapNotNull { it.toIntOrNull() }

internal fun writeWidgetIds(ctx: Context, ids: List<Int>) {
    appPrefs(ctx).edit().putString("widget_ids", ids.joinToString(",")).apply()
}

/** A drag handle. Reports total drag distance in px since the touch began, measured on screen so it stays accurate while the handle itself moves. */
@Composable
internal fun ResizeHandle(
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
internal fun WidgetPreview(info: AppWidgetProviderInfo, ctx: Context) {
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
internal fun Chevron(up: Boolean, color: Color) {
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
internal class ScrollPassthroughFrame(ctx: Context) : android.widget.FrameLayout(ctx) {
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
internal fun WidgetsScreen(p: Palette, onClose: () -> Unit, onDrag: (Float) -> Unit, onEnd: (Float) -> Unit) {
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
