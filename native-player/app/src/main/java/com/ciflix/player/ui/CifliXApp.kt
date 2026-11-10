package com.ciflix.player.ui

import android.view.ViewGroup
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ciflix.player.data.StremioAddonClient
import com.ciflix.player.data.StreamItem
import com.ciflix.player.data.M3uPlaylistClient
import com.ciflix.player.data.M3uChannel
import kotlinx.coroutines.launch

private data class Shelf(val title: String, val items: List<String>)

@Composable
fun CifliXApp() {
    var selected by remember { mutableIntStateOf(0) }
    var showAddSource by remember { mutableStateOf(false) }
    var playingUrl by remember { mutableStateOf<String?>(null) }
    var playingTitle by remember { mutableStateOf("") }
    val shelves = listOf(
        Shelf("أكمل المشاهدة", listOf("متابعة المشاهدة", "آخر ما شاهدت")),
        Shelf("الأكثر مشاهدة", listOf("أكشن", "خيال علمي", "دراما", "أنمي")),
        Shelf("مصادرك", listOf("JSON Manifest", "CloudStream", "Stremio", "M3U / Xtream"))
    )
    Scaffold(
        containerColor = Color(0xFF090A10),
        bottomBar = {
            NavigationBar(containerColor = Color(0xFF11121B)) {
                listOf(Icons.Default.Home, Icons.Default.LiveTv, Icons.Default.Search, Icons.Default.Settings).forEachIndexed { i, icon ->
                    NavigationBarItem(selected = selected == i, onClick = { selected = i },
                        icon = { Icon(icon, null) },
                        label = { Text(listOf("الرئيسية", "مباشر", "بحث", "الإعدادات")[i]) })
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddSource = true }, containerColor = Color(0xFF9B7BFF)) {
                Icon(Icons.Default.Add, contentDescription = "إضافة مصدر", tint = Color.Black)
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            Box(
                Modifier.fillMaxWidth().height(270.dp).background(
                    Brush.linearGradient(listOf(Color(0xFF33205D), Color(0xFF151827), Color(0xFF090A10)))
                ).padding(22.dp),
                contentAlignment = Alignment.BottomStart
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("CIFLIX ORIGINAL", color = Color(0xFFBBA6FF), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text("عالم الترفيه\nبين يديك", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 39.sp)
                    Text("أضف إضافات Stremio واكتشف مصادر التشغيل", color = Color.LightGray)
                    Button(onClick = { showAddSource = true }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9B7BFF))) {
                        Text("إضافة إضافة", color = Color.Black)
                    }
                }
            }
            shelves.forEach { shelf ->
                Column(Modifier.padding(top = 22.dp)) {
                    Text(shelf.title, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 18.dp))
                    LazyRow(contentPadding = PaddingValues(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(shelf.items) { item ->
                            Card(
                                modifier = Modifier.width(145.dp).height(100.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1C2B))
                            ) {
                                Box(Modifier.fillMaxSize().padding(12.dp), contentAlignment = Alignment.BottomStart) {
                                    Text(item, color = Color.White, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
    playingUrl?.let { url ->
        val context = LocalContext.current
        val player = remember(url) {
            ExoPlayer.Builder(context).build().apply {
                setMediaItem(MediaItem.fromUri(url))
                prepare()
                playWhenReady = true
            }
        }
        DisposableEffect(player) { onDispose { player.release() } }
        Dialog(onDismissRequest = { playingUrl = null }) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF11121B))
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(playingTitle, color = Color.White, fontWeight = FontWeight.Bold)
                    AndroidView(
                        factory = { ctx -> PlayerView(ctx).apply { this.player = player; useController = true } },
                        modifier = Modifier.fillMaxWidth().height(230.dp),
                        update = { it.player = player }
                    )
                    TextButton(onClick = { playingUrl = null }) { Text("إغلاق المشغّل") }
                }
            }
        }
    }

    if (showAddSource) {
        var sourceMode by remember { mutableStateOf("Stremio") }
        var playlistUrl by remember { mutableStateOf("") }
        var channels by remember { mutableStateOf<List<M3uChannel>>(emptyList()) }
        var addonUrl by remember { mutableStateOf("") }
        var contentId by remember { mutableStateOf("tt") }
        var status by remember { mutableStateOf("أدخل رابط Manifest لإضافة Stremio، ثم IMDb ID لجلب مصادر التشغيل.") }
        var loading by remember { mutableStateOf(false) }
        var streams by remember { mutableStateOf<List<StreamItem>>(emptyList()) }
        val scope = rememberCoroutineScope()
        AlertDialog(
            onDismissRequest = { if (!loading) showAddSource = false },
            title = { Text("إضافة مصدر") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.heightIn(max = 430.dp).verticalScroll(rememberScrollState())) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = sourceMode == "Stremio", onClick = { sourceMode = "Stremio" }, label = { Text("Stremio") })
                        FilterChip(selected = sourceMode == "M3U", onClick = { sourceMode = "M3U" }, label = { Text("M3U / M3U8") })
                    }
                    if (sourceMode == "M3U") {
                        OutlinedTextField(playlistUrl, { playlistUrl = it }, label = { Text("رابط قائمة M3U") }, placeholder = { Text("https://example.com/list.m3u") }, singleLine = true)
                        Button(enabled = !loading && playlistUrl.isNotBlank(), onClick = {
                            scope.launch {
                                loading = true
                                channels = emptyList()
                                status = "جارٍ تحميل قائمة القنوات..."
                                try {
                                    channels = M3uPlaylistClient.load(playlistUrl)
                                    status = "نجح تحميل ${channels.size} قناة"
                                } catch (e: Exception) {
                                    status = "تعذّر تحميل القائمة: ${e.message ?: "خطأ غير معروف"}"
                                } finally { loading = false }
                            }
                        }) {
                            if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("تحميل القنوات")
                        }
                        channels.forEach { channel ->
                            Card(Modifier.fillMaxWidth().clickable {
                                playingUrl = channel.url
                                playingTitle = channel.name
                                showAddSource = false
                            }, colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1C2B))) {
                                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                                    Text(channel.name, color = Color.White, fontWeight = FontWeight.SemiBold)
                                    Text(channel.group ?: "بدون تصنيف", color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    } else {
                    OutlinedTextField(addonUrl, { addonUrl = it }, label = { Text("رابط Manifest") }, placeholder = { Text("https://addon.example/manifest.json") }, singleLine = true)
                    OutlinedTextField(contentId, { contentId = it }, label = { Text("IMDb ID للفيلم") }, placeholder = { Text("tt1234567") }, singleLine = true)
                    Text(status, style = MaterialTheme.typography.bodySmall, color = if (status.startsWith("نجح")) Color(0xFF8FE3B0) else MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(
                        enabled = !loading && addonUrl.isNotBlank(),
                        onClick = {
                            scope.launch {
                                loading = true
                                streams = emptyList()
                                status = "جارٍ التحقق من الإضافة..."
                                try {
                                    val addon = StremioAddonClient.install(addonUrl)
                                    status = "نجح التحقق: ${addon.name} • الأنواع: ${addon.types.joinToString().ifBlank { "غير محددة" }}\nجارٍ جلب المصادر..."
                                    if (contentId.startsWith("tt")) {
                                        streams = StremioAddonClient.getStreams(addon.manifestUrl, "movie", contentId)
                                        status = "نجح الاتصال • تم العثور على ${streams.size} مصدر"
                                    } else {
                                        status = "نجح التحقق من الإضافة. أدخل IMDb ID يبدأ بـ tt لجلب مصادر الفيلم."
                                    }
                                } catch (e: Exception) {
                                    status = "تعذّر الاتصال: ${e.message ?: "خطأ غير معروف"}"
                                } finally { loading = false }
                            }
                        }
                    ) {
                        if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Text("التحقق وجلب المصادر")
                    }
                    streams.forEachIndexed { index, stream ->
                        Card(
                            modifier = Modifier.fillMaxWidth().clickable(enabled = stream.url != null) {
                                playingUrl = stream.url
                                playingTitle = stream.title
                                showAddSource = false
                            },
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1C2B))
                        ) {
                            Column(Modifier.fillMaxWidth().padding(10.dp)) {
                                Text(stream.title, fontWeight = FontWeight.SemiBold)
                                Text(
                                    if (stream.url != null) "اضغط للتشغيل داخل التطبيق" else if (stream.infoHash != null) "مصدر تورنت غير مدعوم حالياً" else "لا يحتوي رابط تشغيل مباشر",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { if (!loading) showAddSource = false }) { Text("إغلاق") } }
        )
    }
}
