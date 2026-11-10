package com.ciflix.player.ui

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

private data class Shelf(val title: String, val items: List<String>)

@Composable
fun CifliXApp() {
    var selected by remember { mutableIntStateOf(0) }
    var showAddSource by remember { mutableStateOf(false) }
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
                    Text("أضف مصادرك وشاهد محتواك من مكان واحد", color = Color.LightGray)
                    Button(onClick = { showAddSource = true }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9B7BFF))) {
                        Text("إضافة مصدر", color = Color.Black)
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
    if (showAddSource) {
        var name by remember { mutableStateOf("") }
        var url by remember { mutableStateOf("") }
        var type by remember { mutableStateOf("JSON Manifest") }
        AlertDialog(
            onDismissRequest = { showAddSource = false },
            title = { Text("إضافة مصدر") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(name, { name = it }, label = { Text("اسم المصدر") }, singleLine = true)
                    OutlinedTextField(url, { url = it }, label = { Text("رابط المصدر") }, singleLine = true)
                    Text("نوع المصدر: $type", style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("JSON Manifest", "M3U", "Stremio").forEach {
                            FilterChip(selected = type == it, onClick = { type = it }, label = { Text(it) })
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showAddSource = false }) { Text("حفظ مبدئي") } },
            dismissButton = { TextButton(onClick = { showAddSource = false }) { Text("إلغاء") } }
        )
    }
}
