package com.cocoa.launcher

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Border
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

private fun hiddenSet(ctx: Context): MutableSet<String> =
    ctx.getSharedPreferences("launcher", Context.MODE_PRIVATE)
        .getStringSet("hidden", emptySet())!!.toMutableSet()

private fun saveHidden(ctx: Context, set: Set<String>) {
    ctx.getSharedPreferences("launcher", Context.MODE_PRIVATE)
        .edit().putStringSet("hidden", set).apply()
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

    // Din ke hisaab se halka sa mood-gradient (tvOS ke dynamic wallpaper jaisa).
    val (top, bottom) = when (hour) {
        in 5..11 -> Color(0xFF3A2A5C) to Color(0xFF0B0B14)
        in 12..16 -> Color(0xFF1F3B6B) to Color(0xFF0A0F1C)
        in 17..21 -> Color(0xFF5C2A4E) to Color(0xFF0D0710)
        else -> Color(0xFF111A33) to Color(0xFF05060A)
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(top, bottom)))
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 64.dp, vertical = 48.dp)) {
            // Header: greeting + clock
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(greeting, color = Color.White.copy(alpha = 0.7f), fontSize = 22.sp)
                    Text(date, color = Color.White.copy(alpha = 0.5f), fontSize = 18.sp)
                }
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(time, color = Color.White, fontSize = 64.sp, fontWeight = FontWeight.Light)
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
                fontSize = 44.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                modifier = Modifier.padding(bottom = 20.dp).height(56.dp)
            )

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 24.dp, horizontal = 8.dp)
            ) {
                items(apps, key = { it.pkg }) { app ->
                    AppTile(
                        app = app,
                        onFocus = { focusedLabel = app.label },
                        onOpen = { runCatching { ctx.startActivity(app.launch) } },
                        onHide = {
                            val s = hiddenSet(ctx)
                            s.add(app.pkg)
                            saveHidden(ctx, s)
                            apps = apps.filter { it.pkg != app.pkg }
                        }
                    )
                }
                item(key = "settings") {
                    SettingsTile(
                        onFocus = { focusedLabel = "Settings (long-press: hidden apps wapas)" },
                        onOpen = {
                            runCatching {
                                ctx.startActivity(
                                    Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }
                        },
                        onRestore = {
                            saveHidden(ctx, emptySet())
                            // refresh
                            apps = loadApps(ctx)
                        }
                    )
                }
            }
            Text(
                "OK = open   •   OK long-press = hide app",
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
            .width(240.dp)
            .aspectRatio(16f / 9f)
            .onFocusChanged { if (it.isFocused) onFocus() },
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(20.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.12f),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(BorderStroke(3.dp, Color.White), shape = RoundedCornerShape(20.dp))
        ),
        glow = ClickableSurfaceDefaults.glow(
            focusedGlow = Glow(Color.White.copy(alpha = 0.25f), 18.dp)
        ),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.10f),
            focusedContainerColor = Color.White.copy(alpha = 0.18f)
        )
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (app.isBanner) {
                Image(
                    bitmap = app.image,
                    contentDescription = app.label,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(20.dp))
                )
            } else {
                Image(
                    bitmap = app.image,
                    contentDescription = app.label,
                    modifier = Modifier.height(96.dp).aspectRatio(1f)
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SettingsTile(onFocus: () -> Unit, onOpen: () -> Unit, onRestore: () -> Unit) {
    Surface(
        onClick = onOpen,
        onLongClick = onRestore,
        modifier = Modifier
            .width(240.dp)
            .aspectRatio(16f / 9f)
            .onFocusChanged { if (it.isFocused) onFocus() },
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(20.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.12f),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(BorderStroke(3.dp, Color.White), shape = RoundedCornerShape(20.dp))
        ),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.06f),
            focusedContainerColor = Color.White.copy(alpha = 0.18f)
        )
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Settings", color = Color.White, fontSize = 22.sp)
        }
    }
}
