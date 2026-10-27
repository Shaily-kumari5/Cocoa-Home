package com.cocoa.launcher

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Glow
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Apps jo hamesha sabse pehle dikhne chahiye (agar installed hain). */
private val PRIORITY = listOf(
    "com.netflix.ninja",
    "com.amazon.amazonvideo.livingroom",
    "com.google.android.youtube.tv",
    "in.startv.hotstar",
    "com.jio.media.ondemand",
    "com.sonyliv",
    "com.graymatrix.did"
)

data class AppItem(
    val label: String,
    val pkg: String,
    val launch: Intent,
    val image: ImageBitmap,
    val isBanner: Boolean
)

data class Theme(val name: String, val colors: List<Color>, val accent: Color)

/** Theme 0 = Auto (din ke time ke hisaab se), baaki fixed. */
private fun buildThemes(hour: Int): List<Theme> {
    val auto = when (hour) {
        in 5..11 -> Theme("Auto", listOf(Color(0xFF3A2A5C), Color(0xFF1B2A4A), Color(0xFF0B0B14)), Color(0xFFB39DFF))
        in 12..16 -> Theme("Auto", listOf(Color(0xFF1F3B6B), Color(0xFF12284A), Color(0xFF0A0F1C)), Color(0xFF6EA8FF))
        in 17..21 -> Theme("Auto", listOf(Color(0xFF5C2A4E), Color(0xFF2E1A3E), Color(0xFF0D0710)), Color(0xFFFF8FB8))
        else -> Theme("Auto", listOf(Color(0xFF111A33), Color(0xFF0A1022), Color(0xFF05060A)), Color(0xFF7C8CFF))
    }
    return listOf(
        auto,
        Theme("Aurora", listOf(Color(0xFF0F3D3E), Color(0xFF2A1B5C), Color(0xFF07070F)), Color(0xFF5FFFD0)),
        Theme("Sunset", listOf(Color(0xFF7A2E1F), Color(0xFF4A1A4E), Color(0xFF0E0612)), Color(0xFFFFA45C)),
        Theme("Ocean", listOf(Color(0xFF0B3A66), Color(0xFF0A1F3E), Color(0xFF03060D)), Color(0xFF4FC3FF)),
        Theme("Graphite", listOf(Color(0xFF2B2B30), Color(0xFF16161A), Color(0xFF050506)), Color(0xFFE0E0E8))
    )
}

class MainActivity : ComponentActivity() {

    private var refresh by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Home screen par Back dabane se kuch nahi hona chahiye.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { /* stay home */ }
        })
        setContent { LauncherScreen(refresh) }
    }

    override fun onResume() {
        super.onResume()
        refresh++
    }
}

private fun prefs(ctx: Context) = ctx.getSharedPreferences("launcher", Context.MODE_PRIVATE)

private fun hiddenSet(ctx: Context): MutableSet<String> =
    prefs(ctx).getStringSet("hidden", emptySet())!!.toMutableSet()

private fun saveHidden(ctx: Context, set: Set<String>) {
    prefs(ctx).edit().putStringSet("hidden", set).apply()
}

private fun loadApps(ctx: Context): List<AppItem> {
    val pm = ctx.packageManager
    val hidden = hiddenSet(ctx)

    val lean = pm.queryIntentActivities(
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0
    )
    val leanPkgs = lean.map { it.activityInfo.packageName }.toSet()
    val phone = pm.queryIntentActivities(
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0
    ).filter { it.activityInfo.packageName !in leanPkgs }

    return (lean + phone)
        .filter { it.activityInfo.packageName != ctx.packageName }
        .filter { it.activityInfo.packageName !in hidden }
        .distinctBy { it.activityInfo.packageName }
        .map { ri ->
            val pkg = ri.activityInfo.packageName
            val banner = runCatching { ri.activityInfo.loadBanner(pm) }.getOrNull()
            val drawable = banner ?: ri.loadIcon(pm)
            val bmp = if (banner != null) drawable.toBitmap(320, 180) else drawable.toBitmap(160, 160)
            AppItem(
                label = ri.loadLabel(pm).toString(),
                pkg = pkg,
                launch = Intent(Intent.ACTION_MAIN)
                    .setClassName(pkg, ri.activityInfo.name)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                image = bmp.asImageBitmap(),
                isBanner = banner != null
            )
        }
        .sortedWith(
            compareBy<AppItem> {
                val i = PRIORITY.indexOf(it.pkg)
                if (i == -1) Int.MAX_VALUE else i
            }.thenBy { it.label.lowercase() }
        )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun LauncherScreen(refresh: Int) {
    val ctx = LocalContext.current
    var apps by remember { mutableStateOf(emptyList<AppItem>()) }
    var focusedLabel by remember { mutableStateOf("") }
    var focusedApp by remember { mutableStateOf<AppItem?>(null) }
    var themeIdx by remember { mutableIntStateOf(prefs(ctx).getInt("theme", 0)) }

    LaunchedEffect(refresh) {
        apps = withContext(Dispatchers.IO) { loadApps(ctx) }
    }

    val now by produceState(Date()) {
        while (true) {
            value = Date()
            delay(10_000)
        }
    }
    val hour = remember(now) { SimpleDateFormat("H", Locale.getDefault()).format(now).toInt() }
    val greeting = when (hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        in 17..21 -> "Good evening"
        else -> "Good night"
    }
    val time = remember(now) { SimpleDateFormat("h:mm", Locale.getDefault()).format(now) }
    val ampm = remember(now) { SimpleDateFormat("a", Locale.getDefault()).format(now) }
    val date = remember(now) { SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(now) }

    val themes = remember(hour) { buildThemes(hour) }
    val theme = themes[themeIdx.coerceIn(0, themes.lastIndex)]

    // Dheere dheere hilta hua gradient + do glow blobs (dynamic wallpaper jaisa)
    val t by rememberInfiniteTransition(label = "bg").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(22000, easing = LinearEasing), RepeatMode.Reverse),
        label = "bgT"
    )

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = theme.colors,
                    start = Offset(0f, 0f),
                    end = Offset(900f + 900f * t, 1600f - 500f * t)
                )
            )
    ) {
        // Glow blob 1
        Box(
            Modifier.fillMaxSize().background(
                Brush.radialGradient(
                    colors = listOf(theme.accent.copy(alpha = 0.28f), Color.Transparent),
                    center = Offset(300f + 900f * t, 250f + 150f * t),
                    radius = 900f
                )
            )
        )
        // Glow blob 2
        Box(
            Modifier.fillMaxSize().background(
                Brush.radialGradient(
                    colors = listOf(theme.accent.copy(alpha = 0.16f), Color.Transparent),
                    center = Offset(1500f - 800f * t, 900f - 200f * t),
                    radius = 800f
                )
            )
        )

        // Focus kiye gaye app ka banner halke se peeche (top-shelf feel)
        Crossfade(targetState = focusedApp, label = "backdrop") { app ->
            if (app != null && app.isBanner) {
                Image(
                    bitmap = app.image,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alpha = 0.30f,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(Modifier.fillMaxSize())
            }
        }
        // Neeche ki taraf dark fade, taaki text/tiles saaf dikhein
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0.0f to Color(0x66000000),
                    0.45f to Color.Transparent,
                    1.0f to Color(0xCC000000)
                )
            )
        )

        Column(Modifier.fillMaxSize().padding(horizontal = 64.dp, vertical = 48.dp)) {
            // Header: greeting + clock
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(greeting, color = Color.White.copy(alpha = 0.85f), fontSize = 24.sp, fontWeight = FontWeight.Medium)
                    Text(date, color = Color.White.copy(alpha = 0.55f), fontSize = 18.sp)
                }
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(time, color = Color.White, fontSize = 68.sp, fontWeight = FontWeight.Light)
                    Text(
                        " $ampm",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 22.sp,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            // Focused app ka bada naam
            Text(
                focusedLabel,
                color = Color.White,
                fontSize = 48.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                modifier = Modifier.padding(bottom = 20.dp).height(60.dp)
            )

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                contentPadding = PaddingValues(vertical = 28.dp, horizontal = 8.dp)
            ) {
                items(apps, key = { it.pkg }) { app ->
                    AppTile(
                        app = app,
                        onFocus = {
                            focusedLabel = app.label
                            focusedApp = app
                        },
                        onOpen = { runCatching { ctx.startActivity(app.launch) } },
                        onHide = {
                            val s = hiddenSet(ctx)
                            s.add(app.pkg)
                            saveHidden(ctx, s)
                            apps = apps.filter { it.pkg != app.pkg }
                        }
                    )
                }
                item(key = "theme") {
                    TextTile(
                        title = "Theme",
                        subtitle = theme.name,
                        accent = theme.accent,
                        onFocus = {
                            focusedLabel = "Theme: ${theme.name}"
                            focusedApp = null
                        },
                        onClick = {
                            val next = (themeIdx + 1) % themes.size
                            themeIdx = next
                            prefs(ctx).edit().putInt("theme", next).apply()
                            focusedLabel = "Theme: ${themes[next].name}"
                        },
                        onLongClick = {}
                    )
                }
                item(key = "settings") {
                    TextTile(
                        title = "Settings",
                        subtitle = "Long-press: hidden apps wapas",
                        accent = theme.accent,
                        onFocus = {
                            focusedLabel = "Settings"
                            focusedApp = null
                        },
                        onClick = {
                            runCatching {
                                ctx.startActivity(
                                    Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }
                        },
                        onLongClick = {
                            saveHidden(ctx, emptySet())
                            apps = loadApps(ctx)
                        }
                    )
                }
            }
            Text(
                "OK = open   •   OK long-press = hide app   •   Theme tile par OK = theme badlo",
                color = Color.White.copy(alpha = 0.35f),
                fontSize = 14.sp
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun AppTile(
    app: AppItem,
    onFocus: () -> Unit,
    onOpen: () -> Unit,
    onHide: () -> Unit
) {
    Surface(
        onClick = onOpen,
        onLongClick = onHide,
        modifier = Modifier
            .width(260.dp)
            .aspectRatio(16f / 9f)
            .onFocusChanged { if (it.isFocused) onFocus() },
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(22.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.14f),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(BorderStroke(3.dp, Color.White), shape = RoundedCornerShape(22.dp))
        ),
        glow = ClickableSurfaceDefaults.glow(
            focusedGlow = Glow(Color.White.copy(alpha = 0.30f), 22.dp)
        ),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.12f),
            focusedContainerColor = Color.White.copy(alpha = 0.20f)
        )
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (app.isBanner) {
                Image(
                    bitmap = app.image,
                    contentDescription = app.label,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(22.dp))
                )
            } else {
                Image(
                    bitmap = app.image,
                    contentDescription = app.label,
                    modifier = Modifier.height(100.dp).aspectRatio(1f)
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun TextTile(
    title: String,
    subtitle: String,
    accent: Color,
    onFocus: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = Modifier
            .width(260.dp)
            .aspectRatio(16f / 9f)
            .onFocusChanged { if (it.isFocused) onFocus() },
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(22.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.14f),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(BorderStroke(3.dp, Color.White), shape = RoundedCornerShape(22.dp))
        ),
        glow = ClickableSurfaceDefaults.glow(
            focusedGlow = Glow(accent.copy(alpha = 0.35f), 22.dp)
        ),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.07f),
            focusedContainerColor = Color.White.copy(alpha = 0.20f)
        )
    ) {
        Column(
            Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = accent, fontSize = 14.sp, maxLines = 1)
        }
    }
}
