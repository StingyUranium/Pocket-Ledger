package `in`.thomas.pocketledger

import androidx.activity.compose.LocalActivity
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntOffset
import androidx.core.view.WindowCompat
import kotlin.math.cos
import kotlin.math.sin

val pine = Color(0xFF007AFF)
val rust = Color(0xFFFF453A)
val cream = Color(0xFFF2F2F7)
val positive = Color(0xFF248A3D)

@Composable fun PocketTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("appearance", android.content.Context.MODE_PRIVATE) }
    var appearance by remember { mutableStateOf(preferences.getString("mode", "SYSTEM") ?: "SYSTEM") }
    DisposableEffect(preferences) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { store, key ->
            if (key == "mode") appearance = store.getString("mode", "SYSTEM") ?: "SYSTEM"
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
    val scheme = if (dark) darkColorScheme(
        primary = Color(0xFF64ADFF), onPrimary = Color(0xFF00244C), secondary = Color(0xFF64ADFF), tertiary = Color(0xFF30D158),
        background = Color(0xFF000000), surface = Color(0xFF1C1C1E), surfaceVariant = Color(0xFF2C2C2E),
        onBackground = Color(0xFFF5F5F7), onSurface = Color(0xFFF5F5F7), onSurfaceVariant = Color(0xFFAEAEB2),
        outlineVariant = Color(0xFF38383A), primaryContainer = Color(0xFF142A45), onPrimaryContainer = Color(0xFF9AC9FF)
    ) else lightColorScheme(
        primary = pine, onPrimary = Color.White, secondary = pine, tertiary = positive,
        background = cream, surface = Color.White, surfaceVariant = Color(0xFFE9E9EF),
        onBackground = Color(0xFF17171B), onSurface = Color(0xFF17171B), onSurfaceVariant = Color(0xFF75757D),
        outlineVariant = Color(0xFFE5E5EA), primaryContainer = Color(0xFFE8F2FF), onPrimaryContainer = Color(0xFF005BC1)
    )
    val base = TextStyle(fontFamily = FontFamily.SansSerif, letterSpacing = 0.sp)
    MaterialTheme(
        colorScheme = scheme,
        typography = Typography(
            headlineLarge = base.copy(fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp),
            headlineMedium = base.copy(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-.7).sp),
            titleLarge = base.copy(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-.4).sp),
            titleMedium = base.copy(fontSize = 17.sp, lineHeight = 23.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-.2).sp),
            titleSmall = base.copy(fontSize = 15.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold),
            bodyLarge = base.copy(fontSize = 16.sp, lineHeight = 23.sp),
            bodyMedium = base.copy(fontSize = 14.sp, lineHeight = 20.sp),
            bodySmall = base.copy(fontSize = 12.sp, lineHeight = 17.sp),
            labelLarge = base.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
            labelMedium = base.copy(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
            labelSmall = base.copy(fontSize = 10.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium)
        ),
        shapes = Shapes(small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(16.dp), large = RoundedCornerShape(22.dp), extraLarge = RoundedCornerShape(28.dp)),
        content = content
    )
}

@Composable fun AppIcon(name: String, modifier: Modifier = Modifier, tint: Color = MaterialTheme.colorScheme.onSurface) {
    Canvas(modifier.size(22.dp)) {
        withTransform({ scale(size.width / 24, size.height / 24, Offset.Zero) }) {
            val stroke = Stroke(1.7f, cap = StrokeCap.Round)
            fun line(x: Float, y: Float, xx: Float, yy: Float) = drawLine(tint, Offset(x, y), Offset(xx, yy), 1.7f, StrokeCap.Round)
            fun box(x: Float, y: Float, w: Float, h: Float, radius: Float = 3f) = drawRoundRect(tint, Offset(x, y), Size(w, h), CornerRadius(radius), style = stroke)
            when (name) {
                "home" -> { box(3f, 3f, 7f, 7f, 2f); box(14f, 3f, 7f, 7f, 2f); box(3f, 14f, 7f, 7f, 2f); box(14f, 14f, 7f, 7f, 2f) }
                "entries", "transfer" -> { line(7f, 3f, 7f, 19f); line(3f, 7f, 7f, 3f); line(11f, 7f, 7f, 3f); line(17f, 21f, 17f, 5f); line(13f, 17f, 17f, 21f); line(21f, 17f, 17f, 21f) }
                "wallet", "accounts" -> { box(3f, 5f, 18f, 15f); line(5f, 5f, 17f, 2f); box(15f, 10f, 7f, 6f, 2f); drawCircle(tint, 1f, Offset(18f, 13f)) }
                "budgets", "chart" -> { drawArc(tint, -90f, 275f, false, Offset(3f, 3f), Size(18f, 18f), style = stroke); line(12f, 12f, 12f, 3f); line(12f, 12f, 20f, 15f) }
                "sms" -> { box(3f, 4f, 18f, 14f, 4f); line(7f, 18f, 7f, 22f); line(7f, 22f, 12f, 18f); line(7f, 9f, 17f, 9f); line(7f, 13f, 13f, 13f) }
                "settings" -> { drawCircle(tint, 7f, Offset(12f, 12f), style = stroke); drawCircle(tint, 2.5f, Offset(12f, 12f), style = stroke); repeat(8) { val angle = it * Math.PI / 4; line(12 + cos(angle).toFloat() * 7, 12 + sin(angle).toFloat() * 7, 12 + cos(angle).toFloat() * 9.5f, 12 + sin(angle).toFloat() * 9.5f) } }
                "plus" -> { line(12f, 5f, 12f, 19f); line(5f, 12f, 19f, 12f) }
                "close" -> { line(6f, 6f, 18f, 18f); line(18f, 6f, 6f, 18f) }
                "left" -> { line(15f, 5f, 8f, 12f); line(8f, 12f, 15f, 19f) }
                "right" -> { line(9f, 5f, 16f, 12f); line(16f, 12f, 9f, 19f) }
                "down" -> { line(5f, 9f, 12f, 16f); line(12f, 16f, 19f, 9f) }
                "income" -> { line(18f, 6f, 6f, 18f); line(6f, 8f, 6f, 18f); line(6f, 18f, 16f, 18f) }
                "expense" -> { line(6f, 18f, 18f, 6f); line(8f, 6f, 18f, 6f); line(18f, 6f, 18f, 16f) }
                "check" -> { line(5f, 12f, 10f, 17f); line(10f, 17f, 20f, 7f) }
                "search" -> { drawCircle(tint, 6.5f, Offset(10f, 10f), style = stroke); line(15f, 15f, 21f, 21f) }
                "lock" -> { box(5f, 10f, 14f, 11f, 3f); drawArc(tint, 180f, 180f, false, Offset(8f, 2f), Size(8f, 14f), style = stroke); line(12f, 14f, 12f, 17f) }
                "shield" -> { val path = Path().apply { moveTo(12f, 2f); lineTo(21f, 6f); lineTo(20f, 14f); quadraticTo(18f, 20f, 12f, 23f); quadraticTo(6f, 20f, 4f, 14f); lineTo(3f, 6f); close() }; drawPath(path, tint, style = stroke); line(8f, 12f, 11f, 15f); line(11f, 15f, 16f, 9f) }
                "calendar" -> { box(3f, 5f, 18f, 16f); line(3f, 10f, 21f, 10f); line(8f, 2f, 8f, 7f); line(16f, 2f, 16f, 7f); drawCircle(tint, 1f, Offset(8f, 14f)); drawCircle(tint, 1f, Offset(12f, 14f)); drawCircle(tint, 1f, Offset(16f, 14f)); drawCircle(tint, 1f, Offset(8f, 18f)) }
                "backup" -> { drawArc(tint, -120f, 300f, false, Offset(3f, 3f), Size(18f, 18f), style = stroke); line(3f, 3f, 3f, 9f); line(3f, 9f, 9f, 9f); line(12f, 7f, 12f, 12f); line(12f, 12f, 16f, 14f) }
                else -> { box(5f, 2f, 14f, 20f, 2f); line(9f, 7f, 15f, 7f); line(9f, 11f, 15f, 11f); line(9f, 15f, 13f, 15f) }
            }
        }
    }
}

@Composable fun RoundIconButton(icon: String, description: String, filled: Boolean = false, onClick: () -> Unit) {
    Surface(
        onClick = onClick, shape = CircleShape,
        color = if (filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
        modifier = Modifier.size(44.dp).semantics { contentDescription = description }
    ) { Box(contentAlignment = Alignment.Center) { AppIcon(icon, tint = if (filled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface) } }
}

@Composable fun PocketHeader(title: String, settings: () -> Unit, add: (() -> Unit)?) {
    Column(Modifier.statusBarsPadding().padding(horizontal = 22.dp).padding(top = 12.dp, bottom = 8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text("LEDGER", style = MaterialTheme.typography.labelMedium, letterSpacing = 1.6.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                AppIcon("lock", Modifier.size(11.dp), MaterialTheme.colorScheme.onSurfaceVariant)
                Text("On device", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.headlineLarge, modifier = Modifier.weight(1f))
            RoundIconButton("settings", "Settings", onClick = settings)
            add?.let { RoundIconButton("plus", "Add transaction", true, it) }
        }
    }
}

@Composable fun PocketTabBar(selected: Int, pending: Int, change: (Int) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = .98f)) {
        Column {
            HorizontalDivider(thickness = .5.dp, color = MaterialTheme.colorScheme.outlineVariant)
            Row(Modifier.navigationBarsPadding().padding(horizontal = 6.dp, vertical = 7.dp)) {
                listOf("Home", "Entries", "Accounts", "Budgets", "SMS").forEachIndexed { index, label ->
                    val color by animateColorAsState(if (selected == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, label = "tab tint")
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).selectable(selected == index, onClick = { change(index) }, role = Role.Tab).padding(vertical = 7.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Box {
                            AppIcon(listOf("home", "entries", "accounts", "budgets", "sms")[index], Modifier.size(23.dp), color)
                            if (index == 4 && pending > 0) Badge(Modifier.align(Alignment.TopEnd).offset(x = 12.dp, y = (-5).dp)) { Text(if (pending > 99) "99+" else "$pending") }
                        }
                        Text(label, style = MaterialTheme.typography.labelSmall, color = color)
                    }
                }
            }
        }
    }
}

@Composable fun CardBlock(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable fun SectionTitle(text: String) { Text(text, style = MaterialTheme.typography.titleLarge) }
@Composable fun SectionLabel(text: String) { Text(text.uppercase(), style = MaterialTheme.typography.labelMedium, letterSpacing = .8.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp, top = 4.dp)) }

@Composable fun StatusPill(text: String, color: Color = MaterialTheme.colorScheme.primary) {
    Text(text, color = color, style = MaterialTheme.typography.labelMedium, modifier = Modifier.clip(CircleShape).background(color.copy(alpha = .1f)).padding(horizontal = 10.dp, vertical = 5.dp))
}

@Composable fun IconTile(icon: String, color: Color = MaterialTheme.colorScheme.primary, size: Int = 44) {
    Box(Modifier.size(size.dp).clip(RoundedCornerShape((size * .28).dp)).background(color.copy(alpha = .1f)), contentAlignment = Alignment.Center) { AppIcon(icon, tint = color) }
}

@Composable fun Empty(text: String, title: String = "A little space for what's next", icon: String = "receipt") {
    CardBlock {
        Column(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            IconTile(icon, size = 56)
            Text(title, style = MaterialTheme.typography.titleMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.widthIn(max = 290.dp))
        }
    }
}

@Composable fun Segments(values: List<Pair<String, String>>, selected: String, change: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(3.dp)) {
        values.forEach { (value, title) ->
            val active = selected == value
            Box(
                Modifier.weight(1f).then(if (active) Modifier.shadow(2.dp, RoundedCornerShape(9.dp)) else Modifier).clip(RoundedCornerShape(9.dp))
                    .background(if (active) MaterialTheme.colorScheme.surface else Color.Transparent)
                    .selectable(active, onClick = { change(value) }, role = Role.Tab).padding(vertical = 9.dp, horizontal = 4.dp),
                contentAlignment = Alignment.Center
            ) { Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal) }
        }
    }
}

@Composable fun PocketSwitch(checked: Boolean, description: String, change: (Boolean) -> Unit) {
    val color by animateColorAsState(if (checked) Color(0xFF34C759) else MaterialTheme.colorScheme.outlineVariant, label = "switch track")
    val x by animateDpAsState(if (checked) 22.dp else 2.dp, label = "switch thumb")
    Box(Modifier.size(54.dp, 48.dp).semantics { contentDescription = description }.toggleable(checked, role = Role.Switch, onValueChange = change), contentAlignment = Alignment.Center) {
        Box(Modifier.size(52.dp, 32.dp).clip(CircleShape).background(color)) {
            Box(Modifier.offset { IntOffset(x.roundToPx(), 2.dp.roundToPx()) }.size(28.dp).shadow(2.dp, CircleShape).background(Color.White, CircleShape))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun LedgerSheet(title: String, dismiss: () -> Unit, actionLabel: String = "Done", action: () -> Unit = dismiss, closeLabel: String = "Cancel", scrollContent: Boolean = true, actionEnabled: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(
        onDismissRequest = dismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background, shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = { Box(Modifier.padding(top = 10.dp, bottom = 2.dp).size(34.dp, 5.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .3f))) }
    ) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(.94f).imePadding()) {
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = dismiss) { Text(closeLabel, fontWeight = FontWeight.Normal) }
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 2)
                TextButton(onClick = action, enabled = actionEnabled) { Text(actionLabel) }
            }
            HorizontalDivider(thickness = .5.dp, color = MaterialTheme.colorScheme.outlineVariant)
            val contentModifier = Modifier.weight(1f).then(if (scrollContent) Modifier.verticalScroll(rememberScrollState()) else Modifier).padding(20.dp).padding(bottom = 24.dp)
            Column(contentModifier, verticalArrangement = Arrangement.spacedBy(18.dp), content = content)
        }
    }
}
