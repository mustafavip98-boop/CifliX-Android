package com.ciflix.player.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

object M3uPlaylistClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .build()

    suspend fun load(url: String): List<M3uChannel> = withContext(Dispatchers.IO) {
        val clean = url.trim()
        require(clean.startsWith("https://") || clean.startsWith("http://")) {
            "أدخل رابط قائمة HTTP أو HTTPS صحيح"
        }
        val request = Request.Builder()
            .url(clean)
            .header("User-Agent", "CifliXPlayer/1.0")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("تعذر تحميل القائمة: HTTP \${response.code}")
            val body = response.body?.string() ?: error("القائمة فارغة")
            require(body.trimStart().startsWith("#EXTM3U", ignoreCase = true)) {
                "الملف ليس قائمة M3U صحيحة"
            }
            M3uPlaylistParser.parse(body, clean)
        }
    }
}
