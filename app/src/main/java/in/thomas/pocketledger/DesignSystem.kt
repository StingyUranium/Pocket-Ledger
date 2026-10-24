package `in`.thomas.pocketledger

import android.os.Build
import android.provider.Settings
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.core.*
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.error
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import dev.chrisbanes.haze.blur.hazeBlur
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import kotlin.math.cos
import kotlin.math.sin

val pine = Color(0xFF007AFF)
/** Negative amounts, overspending and destructive actions. */
val danger: Color
    @Composable get() = MaterialTheme.colorScheme.error
val cream = Color(0xFFF2F2F7)
val positive = Color(0xFF248A3D)
private val warningLight = Color(0xFFAC6500)
// 0xFFAC6500 is only about 3.8:1 on the #1C1C1E groups, so dark mode uses a lighter amber.
private val warningDark = Color(0xFFFFB74D)
/** Attention that is not an error, such as a message waiting for review. */
val warning: Color
    @Composable get() = if (MaterialTheme.colorScheme.background.luminance() < .5f) warningDark else warningLight

/**
 * Appearance preferences are loaded once on a background thread at launch, so the later
 * synchronous reads in [PocketTheme] and Settings hit memory instead of the disk.
 */
internal object AppearancePreferences {
    private const val Name = "appearance"
    @Volatile private var cached: android.content.SharedPreferences? = null
    fun get(context: android.content.Context): android.content.SharedPreferences =
        cached ?: context.applicationContext.getSharedPreferences(Name, android.content.Context.MODE_PRIVATE).also { cached = it }
    fun preload(context: android.content.Context) {
        val app = context.applicationContext
        Thread({ get(app).all }, "appearance-preload").start()
    }
}

@Composable fun PocketTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val preferences = remember { AppearancePreferences.get(context) }
    var appearance by remember { mutableStateOf(preferences.getString("mode", "SYSTEM") ?: "SYSTEM") }
    var dynamic by remember { mutableStateOf(preferences.getBoolean("dynamic", true)) }
    var contrast by remember { mutableStateOf(preferences.getBoolean("contrast", false)) }
    var reduceMotion by remember { mutableStateOf(preferences.getBoolean("reduceMotion", false)) }
    var systemMotionDisabled by remember { mutableStateOf(Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f) }
    DisposableEffect(context) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                systemMotionDisabled = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
            }
        }
        context.contentResolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }
    DisposableEffect(preferences) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { store, key ->
            if (key == "mode") appearance = store.getString("mode", "SYSTEM") ?: "SYSTEM"
            dynamic = store.getBoolean("dynamic", true)
            contrast = store.getBoolean("contrast", false)
            reduceMotion = store.getBoolean("reduceMotion", false)
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val dark = when (appearance) { "LIGHT" -> false; "DARK" -> true; else -> isSystemInDarkTheme() }
    val view = LocalView.current
    val activity = LocalActivity.current
    SideEffect {
        activity?.let {
            WindowCompat.getInsetsController(it.window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    val fallback = if (dark) darkColorScheme(
        primary = Color(0xFF64ADFF), onPrimary = Color(0xFF00244C), secondary = Color(0xFF64ADFF), tertiary = Color(0xFF30D158),
        background = Color(0xFF000000), surface = Color(0xFF1C1C1E), surfaceVariant = Color(0xFF2C2C2E),
        surfaceContainerLowest = Color(0xFF000000), surfaceContainerLow = Color(0xFF1C1C1E),
        surfaceContainer = Color(0xFF1C1C1E), surfaceContainerHigh = Color(0xFF2C2C2E), surfaceContainerHighest = Color(0xFF3A3A3C),
        onBackground = Color(0xFFFFFFFF), onSurface = Color(0xFFFFFFFF), onSurfaceVariant = Color(0xFF9A9AA0),
        outlineVariant = Color(0xFF38383A), primaryContainer = Color(0xFF142A45), onPrimaryContainer = Color(0xFF9AC9FF)
    ) else lightColorScheme(
        primary = Color(0xFF005AC1), onPrimary = Color.White, secondary = pine, tertiary = positive,
        background = cream, surface = Color.White, surfaceVariant = Color(0xFFE8EBF0),
        surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF8F9FC),
        surfaceContainer = Color(0xFFF0F2F6), surfaceContainerHigh = Color(0xFFE8EBF0), surfaceContainerHighest = Color(0xFFE0E4EB),
        onBackground = Color(0xFF17171B), onSurface = Color(0xFF17171B), onSurfaceVariant = Color(0xFF44474F),
        outlineVariant = Color(0xFFD8D8DC), primaryContainer = Color(0xFFE8F2FF), onPrimaryContainer = Color(0xFF005BC1)
    )
    val selected = remember(dark, dynamic, context) {
        if (dynamic && Build.VERSION.SDK_INT >= 31) {
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } else null
    } ?: fallback
    val scheme = if (contrast) selected.copy(
        onSurface = if (dark) Color.White else Color.Black,
        onBackground = if (dark) Color.White else Color.Black,
        onSurfaceVariant = if (dark) Color.White else Color.Black,
        outline = if (dark) Color.White else Color.Black
    ) else selected
    CompositionLocalProvider(LocalReducedMotion provides (reduceMotion || systemMotionDisabled)) {
    val base = TextStyle(fontFamily = FontFamily.SansSerif, letterSpacing = 0.sp)
    MaterialTheme(
        colorScheme = scheme,
        typography = Typography(
            // iOS-like scale: Large Title 34, Title 22, Headline 17, Body 17, Callout 16, Subhead 15, Footnote 13, Caption 12.
            headlineLarge = base.copy(fontSize = 34.sp, lineHeight = 41.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-.6).sp),
            headlineMedium = base.copy(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-.4).sp),
            headlineSmall = base.copy(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.Medium, letterSpacing = (-.3).sp),
            titleLarge = base.copy(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-.3).sp),
            titleMedium = base.copy(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-.2).sp),
            titleSmall = base.copy(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
            bodyLarge = base.copy(fontSize = 17.sp, lineHeight = 22.sp),
            bodyMedium = base.copy(fontSize = 15.sp, lineHeight = 20.sp),
            bodySmall = base.copy(fontSize = 13.sp, lineHeight = 18.sp),
            labelLarge = base.copy(fontSize = 16.sp, lineHeight = 21.sp, fontWeight = FontWeight.Medium),
            labelMedium = base.copy(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
            labelSmall = base.copy(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium)
        ),
        // Radius scale: 8 controls, 12 grouped containers and fields, 20 sheets.
        shapes = Shapes(extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(8.dp), medium = RoundedCornerShape(12.dp), large = RoundedCornerShape(12.dp), extraLarge = RoundedCornerShape(20.dp)),
        content = content
    )
    }
}

private class IconGeometry(val stroke: Path, val fill: Path)
private val iconGeometries = HashMap<String, IconGeometry>()

/** Several semantic names share one glyph; unknown names fall back to the receipt glyph. */
private fun iconKey(name: String) = when (name) {
    "transfer" -> "entries"
    "accounts" -> "wallet"
    "chart" -> "budgets"
    "home", "entries", "wallet", "budgets", "sms", "settings", "more", "plus", "close", "left", "right", "down",
    "income", "expense", "check", "search", "lock", "shield", "calendar", "backup",
    "utensils", "plate", "basket", "car", "bus", "fuel", "house", "bag", "bolt", "phone",
    "medical", "pill", "book", "play", "repeat", "plane", "sparkles", "tools", "people",
    "gift", "heart", "paw", "bank", "percent", "trend", "briefcase", "star", "laptop",
    "store", "bars", "return", "receipt-check", "coins" -> name
    else -> "receipt"
}

/** Glyph geometry in a 24-unit grid, built once per glyph and drawn with the caller's tint. */
private fun buildIcon(key: String): IconGeometry {
    val stroke = Path()
    val fill = Path()
    fun line(x: Float, y: Float, xx: Float, yy: Float) { stroke.moveTo(x, y); stroke.lineTo(xx, yy) }
    fun box(x: Float, y: Float, w: Float, h: Float, radius: Float = 3f) { stroke.addRoundRect(RoundRect(x, y, x + w, y + h, radius, radius)) }
    fun ring(cx: Float, cy: Float, r: Float) { stroke.addOval(Rect(cx - r, cy - r, cx + r, cy + r)) }
    fun dot(cx: Float, cy: Float, r: Float) { fill.addOval(Rect(cx - r, cy - r, cx + r, cy + r)) }
    fun arc(x: Float, y: Float, w: Float, h: Float, start: Float, sweep: Float) { stroke.arcTo(Rect(x, y, x + w, y + h), start, sweep, true) }
    when (key) {
        "home" -> { box(3f, 3f, 7f, 7f, 2f); box(14f, 3f, 7f, 7f, 2f); box(3f, 14f, 7f, 7f, 2f); box(14f, 14f, 7f, 7f, 2f) }
        "entries" -> { line(7f, 3f, 7f, 19f); line(3f, 7f, 7f, 3f); line(11f, 7f, 7f, 3f); line(17f, 21f, 17f, 5f); line(13f, 17f, 17f, 21f); line(21f, 17f, 17f, 21f) }
        "wallet" -> { box(3f, 5f, 18f, 15f); line(5f, 5f, 17f, 2f); box(15f, 10f, 7f, 6f, 2f); dot(18f, 13f, 1f) }
        "budgets" -> { arc(3f, 3f, 18f, 18f, -90f, 275f); line(12f, 12f, 12f, 3f); line(12f, 12f, 20f, 15f) }
        "sms" -> { box(3f, 4f, 18f, 14f, 4f); line(7f, 18f, 7f, 22f); line(7f, 22f, 12f, 18f); line(7f, 9f, 17f, 9f); line(7f, 13f, 13f, 13f) }
        "settings" -> { ring(12f, 12f, 7f); ring(12f, 12f, 2.5f); repeat(8) { val angle = it * Math.PI / 4; line(12 + cos(angle).toFloat() * 7, 12 + sin(angle).toFloat() * 7, 12 + cos(angle).toFloat() * 9.5f, 12 + sin(angle).toFloat() * 9.5f) } }
        "more" -> repeat(3) { dot(5f + it * 7f, 12f, 1.8f) }
        "plus" -> { line(12f, 5f, 12f, 19f); line(5f, 12f, 19f, 12f) }
        "close" -> { line(6f, 6f, 18f, 18f); line(18f, 6f, 6f, 18f) }
        "left" -> { line(15f, 5f, 8f, 12f); line(8f, 12f, 15f, 19f) }
        "right" -> { line(9f, 5f, 16f, 12f); line(16f, 12f, 9f, 19f) }
        "down" -> { line(5f, 9f, 12f, 16f); line(12f, 16f, 19f, 9f) }
        "income" -> { line(18f, 6f, 6f, 18f); line(6f, 8f, 6f, 18f); line(6f, 18f, 16f, 18f) }
        "expense" -> { line(6f, 18f, 18f, 6f); line(8f, 6f, 18f, 6f); line(18f, 6f, 18f, 16f) }
        "check" -> { line(5f, 12f, 10f, 17f); line(10f, 17f, 20f, 7f) }
        "search" -> { ring(10f, 10f, 6.5f); line(15f, 15f, 21f, 21f) }
        "lock" -> { box(5f, 10f, 14f, 11f, 3f); arc(8f, 2f, 8f, 14f, 180f, 180f); line(12f, 14f, 12f, 17f) }
        "shield" -> {
            stroke.moveTo(12f, 2f); stroke.lineTo(21f, 6f); stroke.lineTo(20f, 14f); stroke.quadraticTo(18f, 20f, 12f, 23f)
            stroke.quadraticTo(6f, 20f, 4f, 14f); stroke.lineTo(3f, 6f); stroke.close()
            line(8f, 12f, 11f, 15f); line(11f, 15f, 16f, 9f)
        }
        "calendar" -> { box(3f, 5f, 18f, 16f); line(3f, 10f, 21f, 10f); line(8f, 2f, 8f, 7f); line(16f, 2f, 16f, 7f); dot(8f, 14f, 1f); dot(12f, 14f, 1f); dot(16f, 14f, 1f); dot(8f, 18f, 1f) }
        "backup" -> { arc(3f, 3f, 18f, 18f, -120f, 300f); line(3f, 3f, 3f, 9f); line(3f, 9f, 9f, 9f); line(12f, 7f, 12f, 12f); line(12f, 12f, 16f, 14f) }
        "utensils" -> { line(5f, 3f, 5f, 8f); line(8f, 3f, 8f, 8f); line(11f, 3f, 11f, 8f); arc(5f, 5f, 6f, 6f, 0f, 180f); line(8f, 11f, 8f, 21f); line(18f, 3f, 18f, 21f); line(18f, 3f, 15f, 11f); line(15f, 11f, 18f, 11f) }
        "plate" -> { ring(12f, 12f, 7f); ring(12f, 12f, 4f); line(2f, 4f, 2f, 20f); line(22f, 4f, 22f, 20f) }
        "basket" -> { line(3f, 9f, 21f, 9f); line(4f, 9f, 7f, 20f); line(7f, 20f, 17f, 20f); line(17f, 20f, 20f, 9f); line(7f, 9f, 10f, 3f); line(17f, 9f, 14f, 3f); line(9f, 13f, 10f, 17f); line(15f, 13f, 14f, 17f) }
        "car" -> { box(3f, 10f, 18f, 9f, 2f); line(5f, 10f, 8f, 4f); line(8f, 4f, 16f, 4f); line(16f, 4f, 19f, 10f); line(5f, 19f, 5f, 21f); line(19f, 19f, 19f, 21f); dot(7f, 14f, 1f); dot(17f, 14f, 1f) }
        "bus" -> { box(5f, 3f, 14f, 17f, 3f); line(5f, 12f, 19f, 12f); line(12f, 3f, 12f, 12f); dot(8f, 16f, 1f); dot(16f, 16f, 1f); line(7f, 20f, 7f, 22f); line(17f, 20f, 17f, 22f) }
        "fuel" -> { box(4f, 3f, 10f, 17f, 1f); box(6f, 5f, 6f, 5f, 1f); line(2f, 21f, 16f, 21f); line(14f, 11f, 18f, 11f); line(18f, 11f, 18f, 18f); arc(18f, 16f, 4f, 4f, 0f, 180f); line(22f, 18f, 22f, 8f); line(22f, 8f, 18f, 4f) }
        "house" -> { line(2f, 11f, 12f, 3f); line(12f, 3f, 22f, 11f); line(5f, 9f, 5f, 21f); line(5f, 21f, 19f, 21f); line(19f, 21f, 19f, 9f); box(10f, 14f, 4f, 7f, 0f) }
        "bag" -> { box(4f, 7f, 16f, 14f, 2f); arc(8f, 2f, 8f, 10f, 180f, 180f); line(8f, 7f, 8f, 10f); line(16f, 7f, 16f, 10f) }
        "bolt" -> { stroke.moveTo(14f, 2f); stroke.lineTo(4f, 14f); stroke.lineTo(11f, 14f); stroke.lineTo(10f, 22f); stroke.lineTo(20f, 10f); stroke.lineTo(13f, 10f); stroke.close() }
        "phone" -> { box(6f, 2f, 12f, 20f, 2f); line(10f, 5f, 14f, 5f); dot(12f, 19f, .8f) }
        "medical" -> { box(3f, 3f, 18f, 18f, 4f); line(12f, 7f, 12f, 17f); line(7f, 12f, 17f, 12f) }
        "pill" -> { stroke.moveTo(4f, 12f); stroke.cubicTo(-2f, 18f, 6f, 26f, 12f, 20f); stroke.lineTo(20f, 12f); stroke.cubicTo(26f, 6f, 18f, -2f, 12f, 4f); stroke.close(); line(8f, 8f, 16f, 16f) }
        "book" -> { box(4f, 3f, 16f, 18f, 2f); line(8f, 3f, 8f, 21f); line(11f, 8f, 17f, 8f); line(11f, 12f, 17f, 12f) }
        "play" -> { ring(12f, 12f, 9f); stroke.moveTo(10f, 7f); stroke.lineTo(17f, 12f); stroke.lineTo(10f, 17f); stroke.close() }
        "repeat" -> { line(4f, 8f, 19f, 8f); line(19f, 8f, 15f, 4f); line(19f, 8f, 15f, 12f); line(20f, 16f, 5f, 16f); line(5f, 16f, 9f, 12f); line(5f, 16f, 9f, 20f) }
        "plane" -> { stroke.moveTo(21f, 3f); stroke.lineTo(15f, 21f); stroke.lineTo(11f, 14f); stroke.lineTo(3f, 10f); stroke.close(); line(11f, 14f, 21f, 3f) }
        "sparkles" -> { stroke.moveTo(11f, 3f); stroke.lineTo(14f, 9f); stroke.lineTo(20f, 12f); stroke.lineTo(14f, 15f); stroke.lineTo(11f, 21f); stroke.lineTo(8f, 15f); stroke.lineTo(2f, 12f); stroke.lineTo(8f, 9f); stroke.close(); line(20f, 2f, 20f, 6f); line(18f, 4f, 22f, 4f) }
        "tools" -> { arc(12f, 2f, 10f, 10f, -30f, 270f); line(13f, 10f, 3f, 20f); line(3f, 20f, 5f, 22f); line(5f, 22f, 15f, 12f); line(16f, 2f, 16f, 6f); line(16f, 6f, 21f, 6f) }
        "people" -> { ring(9f, 7f, 3f); arc(2f, 12f, 14f, 16f, 180f, 180f); ring(18f, 8f, 2f); arc(15f, 13f, 8f, 14f, 200f, 160f) }
        "gift" -> { box(4f, 10f, 16f, 11f, 1f); box(3f, 7f, 18f, 4f, 1f); line(12f, 7f, 12f, 21f); arc(5f, 2f, 7f, 6f, 0f, 300f); arc(12f, 2f, 7f, 6f, -120f, 300f) }
        "heart" -> { stroke.moveTo(12f, 21f); stroke.cubicTo(8f, 17f, 2f, 13f, 2f, 8f); stroke.cubicTo(2f, 2f, 9f, 1f, 12f, 6f); stroke.cubicTo(15f, 1f, 22f, 2f, 22f, 8f); stroke.cubicTo(22f, 13f, 16f, 17f, 12f, 21f) }
        "paw" -> { ring(5f, 8f, 2f); ring(10f, 4f, 2f); ring(16f, 5f, 2f); ring(20f, 10f, 2f); stroke.moveTo(6f, 17f); stroke.cubicTo(6f, 12f, 12f, 10f, 14f, 13f); stroke.cubicTo(21f, 15f, 20f, 23f, 14f, 20f); stroke.cubicTo(8f, 23f, 4f, 21f, 6f, 17f) }
        "bank" -> { line(2f, 8f, 12f, 2f); line(12f, 2f, 22f, 8f); line(2f, 8f, 22f, 8f); line(5f, 11f, 5f, 18f); line(12f, 11f, 12f, 18f); line(19f, 11f, 19f, 18f); line(3f, 21f, 21f, 21f) }
        "percent" -> { ring(7f, 7f, 3f); ring(17f, 17f, 3f); line(5f, 21f, 19f, 3f) }
        "trend" -> { line(3f, 18f, 9f, 12f); line(9f, 12f, 13f, 15f); line(13f, 15f, 21f, 5f); line(15f, 5f, 21f, 5f); line(21f, 5f, 21f, 11f) }
        "briefcase" -> { box(3f, 7f, 18f, 14f, 2f); box(8f, 3f, 8f, 4f, 1f); line(3f, 13f, 21f, 13f); line(10f, 13f, 14f, 13f); line(12f, 11f, 12f, 15f) }
        "star" -> { stroke.moveTo(12f, 2f); stroke.lineTo(15f, 8f); stroke.lineTo(22f, 9f); stroke.lineTo(17f, 14f); stroke.lineTo(18f, 21f); stroke.lineTo(12f, 18f); stroke.lineTo(6f, 21f); stroke.lineTo(7f, 14f); stroke.lineTo(2f, 9f); stroke.lineTo(9f, 8f); stroke.close() }
        "laptop" -> { box(5f, 3f, 14f, 13f, 1f); stroke.moveTo(5f, 16f); stroke.lineTo(2f, 21f); stroke.lineTo(22f, 21f); stroke.lineTo(19f, 16f); stroke.close() }
        "store" -> { box(4f, 10f, 16f, 11f, 1f); stroke.moveTo(3f, 10f); stroke.lineTo(5f, 3f); stroke.lineTo(19f, 3f); stroke.lineTo(21f, 10f); stroke.close(); line(9f, 3f, 8f, 10f); line(15f, 3f, 16f, 10f); box(9f, 15f, 6f, 6f, 0f) }
        "bars" -> { line(3f, 21f, 21f, 21f); box(4f, 12f, 3f, 6f, .5f); box(10f, 8f, 3f, 10f, .5f); box(16f, 3f, 3f, 15f, .5f) }
        "return" -> { line(9f, 4f, 3f, 10f); line(3f, 10f, 9f, 16f); line(3f, 10f, 15f, 10f); arc(10f, 10f, 12f, 12f, -90f, 180f); line(16f, 22f, 10f, 22f) }
        "receipt-check" -> { box(4f, 2f, 16f, 20f, 2f); line(8f, 7f, 16f, 7f); line(8f, 13f, 11f, 16f); line(11f, 16f, 17f, 10f) }
        "coins" -> { stroke.addOval(Rect(3f, 3f, 17f, 9f)); arc(3f, 7f, 14f, 6f, 0f, 180f); line(3f, 6f, 3f, 10f); line(17f, 6f, 17f, 10f); stroke.addOval(Rect(8f, 12f, 22f, 18f)); arc(8f, 16f, 14f, 6f, 0f, 180f); line(8f, 15f, 8f, 19f); line(22f, 15f, 22f, 19f) }
        else -> { box(5f, 2f, 14f, 20f, 2f); line(9f, 7f, 15f, 7f); line(9f, 11f, 15f, 11f); line(9f, 15f, 13f, 15f) }
    }
    return IconGeometry(stroke, fill)
}

@Composable fun AppIcon(name: String, modifier: Modifier = Modifier, tint: Color = MaterialTheme.colorScheme.onSurface) {
    val geometry = remember(name) { val key = iconKey(name); iconGeometries.getOrPut(key) { buildIcon(key) } }
    Canvas(modifier.size(22.dp)) {
        withTransform({ scale(size.width / 24, size.height / 24, Offset.Zero) }) {
            drawPath(geometry.stroke, tint, style = Stroke(1.7f, cap = StrokeCap.Round))
            if (!geometry.fill.isEmpty) drawPath(geometry.fill, tint)
        }
    }
}

/** Plain 48dp toolbar icon; feedback comes from the shared press effect rather than a ripple. */
@Composable fun BarIconButton(icon: String, description: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Box(Modifier.size(LedgerSpace.touch).premiumPressEffect(interaction, pressedScale = .92f)
        .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
        .semantics { contentDescription = description }, contentAlignment = Alignment.Center) {
        AppIcon(icon, tint = MaterialTheme.colorScheme.primary)
    }
}

/**
 * Pinned compact bar. The inline title fades in as the large title collapses, so the bar never
 * remeasures while scrolling; only alpha is read at draw time.
 */
@Composable fun PocketNavigationBar(
    title: String, collapsed: () -> Float, back: (() -> Unit)?, backLabel: String,
    settings: (() -> Unit)?, add: (() -> Unit)?, addLabel: String = "Add transaction"
) {
    val largeText = LocalDensity.current.fontScale > 1.3f
    // Laid out as a row so the title takes whatever the back label and actions leave, at any font scale.
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = LedgerSpace.xs), verticalAlignment = Alignment.CenterVertically) {
        if (back != null) {
            val interaction = remember { MutableInteractionSource() }
            Row(Modifier.heightIn(min = LedgerSpace.touch).premiumPressEffect(interaction)
                .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = back)
                .semantics(mergeDescendants = true) { contentDescription = "Back to $backLabel" }.padding(start = LedgerSpace.xs, end = LedgerSpace.medium),
                verticalAlignment = Alignment.CenterVertically) {
                AppIcon("left", tint = MaterialTheme.colorScheme.primary)
                Text(backLabel, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary, maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = if (largeText) 180.dp else 120.dp))
            }
        } else Spacer(Modifier.width(LedgerSpace.small))
        Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.weight(1f).padding(horizontal = LedgerSpace.xs)
                .graphicsLayer { alpha = ((collapsed() - .55f) / .35f).coerceIn(0f, 1f) }
                .clearAndSetSemantics { })
        Row(verticalAlignment = Alignment.CenterVertically) {
            add?.let { BarIconButton("plus", addLabel, it) }
            settings?.let { TextButton(onClick = it) { Text("Manage") } }
        }
    }
}

@Composable fun PocketLargeTitle(title: String) {
    Text(title, style = MaterialTheme.typography.headlineLarge, maxLines = 2,
        modifier = Modifier.fillMaxWidth().padding(start = LedgerSpace.margin, end = LedgerSpace.margin, top = 0.dp, bottom = LedgerSpace.small).semantics { heading() })
}

@Composable internal fun PocketTabBar(selected: Destination, pending: Int, modifier: Modifier = Modifier, haze: dev.chrisbanes.haze.HazeState? = null, change: (Destination) -> Unit) {
    val haptic = LocalHapticFeedback.current
    val chrome = rememberChromeStyle()
    Surface(
        modifier = if (haze != null) modifier.hazeBlur(dev.chrisbanes.haze.HazeInput.Sources(haze), chrome) else modifier,
        color = if (haze != null) Color.Transparent else MaterialTheme.colorScheme.surface.copy(alpha = .94f), tonalElevation = 0.dp
    ) {
        Column {
            HorizontalDivider(thickness = androidx.compose.ui.unit.Dp.Hairline, color = MaterialTheme.colorScheme.outlineVariant)
            Column(Modifier.navigationBarsPadding().selectableGroup()) {
            Destination.primary.chunked(if (LocalDensity.current.fontScale > 1.3f) 2 else 4).forEach { destinations ->
            Row(Modifier.fillMaxWidth().padding(horizontal = LedgerSpace.small, vertical = LedgerSpace.xs)) {
                destinations.forEach { index ->
                    val active = selected == index
                    val interaction = remember { MutableInteractionSource() }
                    val color by androidx.compose.animation.animateColorAsState(
                        if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        if (LocalReducedMotion.current) snap() else tween(LedgerMotion.fast), label = "tab color")
                    Column(Modifier.weight(1f).premiumPressEffect(interaction, pressedScale = .96f)
                        .clip(MaterialTheme.shapes.medium)
                        .selectable(active, interactionSource = interaction, indication = null, role = Role.Tab, onClick = {
                            if (selected != index) { haptic.performHapticFeedback(HapticFeedbackType.SegmentTick); change(index) }
                        }).heightIn(min = 56.dp).padding(vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        BadgedBox(badge = { if (index == Destination.Inbox && pending > 0) Badge { Text(if (pending > 99) "99+" else "$pending") } }) {
                            AppIcon(index.icon, tint = color)
                        }
                        Text(index.title, style = MaterialTheme.typography.labelMedium, color = color,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 2, softWrap = true, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium)
                    }
                }
            }
            }
            }
        }
    }
}

@Composable internal fun PocketNavigationRail(selected: Destination, pending: Int, change: (Destination) -> Unit, settings: () -> Unit) {
    NavigationRail(Modifier.fillMaxHeight(), containerColor = MaterialTheme.colorScheme.surface) {
        Spacer(Modifier.height(16.dp))
        Destination.primary.forEach { index ->
            NavigationRailItem(selected = selected == index, onClick = { change(index) },
                icon = { BadgedBox(badge = { if (index == Destination.Inbox && pending > 0) Badge { Text("$pending") } }) { AppIcon(index.icon) } },
                label = { Text(index.title) })
        }
        Spacer(Modifier.weight(1f))
        NavigationRailItem(selected = false, onClick = settings,
            icon = { AppIcon("settings") }, label = { Text("Manage") })
    }
}

@Composable internal fun MoreScreen(pending: Int, open: (Destination) -> Unit, settings: () -> Unit) {
    FluidList(contentPadding = PaddingValues(LedgerSpace.margin), verticalArrangement = Arrangement.spacedBy(LedgerSpace.section)) {
        item { GroupedSection("Manage your ledger") {
            GroupedRow("Accounts", subtitle = "Calculated and opening balances", leading = { IconTile("accounts") }, chevron = true, onClick = { open(Destination.Accounts) })
            GroupedDivider()
            GroupedRow("Income plan", subtitle = "Separate 50/30/20 planning targets", leading = { IconTile("budgets") }, chevron = true, onClick = { open(Destination.IncomePlan) })
            GroupedDivider()
            GroupedRow("Settings and backups", subtitle = "Appearance, permissions and saved rules", leading = { IconTile("settings") }, chevron = true, onClick = settings)
        } }
        item { Text("Everything stays on this device.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable fun SectionTitle(text: String) { Text(text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() }) }
@Composable fun SectionLabel(text: String) { Text(text.uppercase(), style = MaterialTheme.typography.labelMedium, letterSpacing = .8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp, top = 4.dp).semantics { heading() }) }

/**
 * Grouped inset list: an optional caption above, one rounded container, hairline separators
 * between rows and an optional footnote below. Prefer this to a stack of separate cards.
 */
@Composable fun GroupedSection(header: String? = null, footer: String? = null, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth()) {
        header?.let {
            Text(it.uppercase(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = LedgerSpace.margin, bottom = LedgerSpace.small).semantics { heading() })
        }
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surface) { Column(content = content) }
        footer?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = LedgerSpace.margin, end = LedgerSpace.margin, top = LedgerSpace.small))
        }
    }
}

/** Free-form content (text, buttons, bars) inside a grouped container, in place of a standalone card. */
@Composable fun GroupedBlock(header: String? = null, footer: String? = null, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    GroupedSection(header, footer, modifier) {
        GroupedItem { Column(Modifier.weight(1f).padding(vertical = LedgerSpace.xs), verticalArrangement = Arrangement.spacedBy(LedgerSpace.medium), content = content) }
    }
}

@Composable fun GroupedDivider(inset: Dp = LedgerSpace.margin) {
    HorizontalDivider(Modifier.padding(start = inset), thickness = Dp.Hairline, color = MaterialTheme.colorScheme.outlineVariant)
}

/** A row with custom content. Rows that hold their own control (a switch) should not pass [onClick]. */
@Composable fun GroupedItem(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, onClickLabel: String? = null, content: @Composable RowScope.() -> Unit) {
    val base = modifier.fillMaxWidth().heightIn(min = 52.dp)
    Row((if (onClick != null) base.ledgerClickable(onClickLabel = onClickLabel, onClick = onClick) else base)
        .padding(horizontal = LedgerSpace.margin, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(LedgerSpace.medium), content = content)
}

/** Borderless text input for a grouped row: small label above the value, no filled container. */
@Composable fun GroupedField(
    label: String, value: String, change: (String) -> Unit, modifier: Modifier = Modifier, numeric: Boolean = false,
    imeAction: androidx.compose.ui.text.input.ImeAction = androidx.compose.ui.text.input.ImeAction.Next,
    keyboardType: androidx.compose.ui.text.input.KeyboardType? = null,
    visualTransformation: androidx.compose.ui.text.input.VisualTransformation = androidx.compose.ui.text.input.VisualTransformation.None,
    isError: Boolean = false, trailing: (@Composable () -> Unit)? = null
) {
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    val requester = remember { androidx.compose.ui.focus.FocusRequester() }
    // Tapping anywhere in the row, including its padding, focuses the field.
    GroupedItem(modifier.pointerInput(requester) { detectTapGestures { requester.requestFocus() } }) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            androidx.compose.foundation.text.BasicTextField(value, change,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary), singleLine = true,
                visualTransformation = visualTransformation,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = keyboardType ?: if (numeric) androidx.compose.ui.text.input.KeyboardType.Decimal else androidx.compose.ui.text.input.KeyboardType.Text,
                    imeAction = imeAction),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                    onNext = { focus.moveFocus(androidx.compose.ui.focus.FocusDirection.Next) }, onDone = { focus.clearFocus() }),
                modifier = Modifier.fillMaxWidth().focusRequester(requester).semantics {
                    contentDescription = label
                    if (isError) error("Check $label")
                })
        }
        trailing?.invoke()
    }
}

@Composable fun GroupedRow(
    title: String, modifier: Modifier = Modifier, subtitle: String? = null, leading: (@Composable () -> Unit)? = null,
    value: String? = null, trailing: (@Composable () -> Unit)? = null, chevron: Boolean = false,
    titleColor: Color = MaterialTheme.colorScheme.onSurface, onClick: (() -> Unit)? = null, onClickLabel: String? = null
) {
    val stackValue = LocalDensity.current.fontScale > 1.3f
    GroupedItem(modifier, onClick, onClickLabel) {
        leading?.invoke()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = titleColor)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (stackValue) value?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        if (!stackValue) value?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        trailing?.invoke()
        if (chevron) AppIcon("right", Modifier.size(14.dp), MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable fun StatusPill(text: String, color: Color = MaterialTheme.colorScheme.primary) {
    Text(text, color = color, style = MaterialTheme.typography.labelMedium, modifier = Modifier.clip(CircleShape).background(color.copy(alpha = .1f)).padding(horizontal = 10.dp, vertical = 5.dp))
}

@Composable fun IconTile(icon: String, color: Color = MaterialTheme.colorScheme.primary, size: Int = 44) {
    Box(Modifier.size(size.dp).clip(RoundedCornerShape((size * .28).dp)).background(color.copy(alpha = .1f)), contentAlignment = Alignment.Center) { AppIcon(icon, tint = color) }
}

@Composable fun Empty(text: String, title: String = "A little space for what's next", icon: String = "receipt") {
    Box(Modifier.fillMaxWidth().padding(horizontal = LedgerSpace.content, vertical = LedgerSpace.generous)) {
        Column(Modifier.fillMaxWidth().padding(vertical = LedgerSpace.content), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AppIcon(icon, Modifier.size(32.dp), MaterialTheme.colorScheme.onSurfaceVariant)
            Text(title, style = MaterialTheme.typography.titleMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.widthIn(max = 290.dp))
        }
    }
}

@Composable fun Segments(values: List<Pair<String, String>>, selected: String, change: (String) -> Unit) {
    if (values.isEmpty()) return
    val reduced = LocalReducedMotion.current
    val haptic = LocalHapticFeedback.current
    val position by animateFloatAsState(values.indexOfFirst { it.first == selected }.coerceAtLeast(0).toFloat(),
        if (reduced) snap() else LedgerMotion.gentle(), label = "segment position")
    // The indicator is drawn behind the row, so multiline labels determine the height together.
    BoxWithConstraints(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium)
        .background(MaterialTheme.colorScheme.surfaceContainerHighest).padding(3.dp).selectableGroup()) {
        val thumb = if (MaterialTheme.colorScheme.background.luminance() < .5f) Color(0xFF636366) else MaterialTheme.colorScheme.surface
        val segmentWidth = maxWidth / values.size
        Box(Modifier.matchParentSize().offset { androidx.compose.ui.unit.IntOffset((segmentWidth.toPx() * position).toInt(), 0) }) {
            Box(Modifier.width(segmentWidth).fillMaxHeight().clip(MaterialTheme.shapes.small).background(thumb))
        }
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            values.forEach { (value, title) ->
                val active = selected == value
                val color by androidx.compose.animation.animateColorAsState(
                    if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    if (reduced) snap() else tween(LedgerMotion.fast), label = "segment text")
                Box(Modifier.weight(1f).fillMaxHeight().heightIn(min = LedgerSpace.touch).clip(MaterialTheme.shapes.small)
                    .selectable(active, onClick = {
                        if (!active) { haptic.performHapticFeedback(HapticFeedbackType.SegmentTick); change(value) }
                    }, role = Role.Tab).padding(vertical = 10.dp, horizontal = 4.dp), contentAlignment = Alignment.Center) {
                    Text(title, style = MaterialTheme.typography.bodyMedium, color = color,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal)
                }
            }
        }
    }
}

@Composable fun PocketSwitch(checked: Boolean, description: String, change: (Boolean) -> Unit) {
    val haptic = LocalHapticFeedback.current
    Switch(checked = checked, onCheckedChange = {
        haptic.performHapticFeedback(if (it) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
        change(it)
    }, modifier = Modifier.semantics { contentDescription = description })
}

val LocalInlineSheet = staticCompositionLocalOf { false }

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun LedgerSheet(title: String, dismiss: () -> Unit, actionLabel: String = "Done", action: () -> Unit = dismiss, closeLabel: String = "Cancel", scrollContent: Boolean = true, actionEnabled: Boolean = true, showAction: Boolean = true, contentPadding: Dp = 20.dp, contentScroll: androidx.compose.foundation.gestures.ScrollableState? = null, content: @Composable ColumnScope.() -> Unit) {
    val clearError = LocalLedgerClearError.current
    LaunchedEffect(Unit) { clearError() }
    val scroll = rememberScrollState()
    val body: @Composable (() -> Unit) -> Unit = { close ->
        // Sheets sit above the page chrome, so the header and tab-bar insets must not leak into them.
        CompositionLocalProvider(LocalInlineSheet provides false, LocalLedgerHeaderInset provides 0.dp, LocalLedgerBottomInset provides 0.dp) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(.94f).imePadding()) {
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = close) { Text(closeLabel, fontWeight = FontWeight.Normal) }
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 2)
                if (showAction) TextButton(onClick = action, enabled = actionEnabled && !LocalLedgerBusy.current) { Text(actionLabel) }
                else TextButton(onClick = {}, enabled = false, modifier = Modifier.clearAndSetSemantics { }.alpha(0f)) { Text(closeLabel) }
            }
            HorizontalDivider(thickness = androidx.compose.ui.unit.Dp.Hairline, color = MaterialTheme.colorScheme.outlineVariant)
            LocalLedgerOperationError.current?.let { error ->
                Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
            }
            val contentModifier = Modifier.weight(1f).then(if (scrollContent) Modifier.verticalScroll(scroll) else Modifier).padding(contentPadding).padding(bottom = 24.dp)
            Column(contentModifier, verticalArrangement = Arrangement.spacedBy(18.dp), content = content)
        }
        }
    }
    if (LocalInlineSheet.current) Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { body(dismiss) }
    else FluidModalSheet(dismiss, contentScroll = contentScroll ?: scroll.takeIf { scrollContent }, content = body)
}
