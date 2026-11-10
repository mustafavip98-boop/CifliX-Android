package com.ciflix.player.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class StremioAddon(val name: String, val manifestUrl: String, val types: List<String>)
data class StreamItem(val title: String, val url: String?, val infoHash: String?, val fileIdx: Int?)

object StremioAddonClient {
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    suspend fun install(manifestUrl: String): StremioAddon = withContext(Dispatchers.IO) {
        val clean = manifestUrl.trim()
        require(clean.startsWith("https://") || clean.startsWith("http://")) { "أدخل رابط HTTP أو HTTPS صحيح" }
        val request = Request.Builder().url(clean).header("User-Agent", "CifliXPlayer/0.1").build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("فشل تحميل Manifest: HTTP ${response.code}")
            val json = JSONObject(response.body?.string() ?: error("الاستجابة فارغة"))
            val addon = json.optJSONObject("addon") ?: json
            val name = addon.optString("name", "Stremio Addon")
            val typesJson = addon.optJSONArray("types")
            val types = buildList {
                if (typesJson != null) for (i in 0 until typesJson.length()) add(typesJson.optString(i))
            }
            StremioAddon(name, clean, types)
        }
    }

    suspend fun getStreams(manifestUrl: String, type: String, contentId: String): List<StreamItem> =
        withContext(Dispatchers.IO) {
            val base = manifestUrl.substringBefore("/manifest.json").trimEnd('/')
            val encodedId = URLEncoder.encode(contentId.trim(), "UTF-8").replace("+", "%20")
            val endpoint = "$base/stream/$type/$encodedId.json"
            val request = Request.Builder().url(endpoint).header("User-Agent", "CifliXPlayer/0.1").build()
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("فشل جلب مصادر التشغيل: HTTP ${response.code}")
                val root = JSONObject(response.body?.string() ?: error("الاستجابة فارغة"))
                val array = root.optJSONArray("streams") ?: return@withContext emptyList()
                buildList {
                    for (i in 0 until array.length()) {
                        val item = array.optJSONObject(i) ?: continue
                        add(StreamItem(
                            title = item.optString("title").ifBlank { item.optString("name", "مصدر ${i + 1}") },
                            url = item.optString("url").takeIf { it.startsWith("http") },
                            infoHash = item.optString("infoHash").takeIf { it.isNotBlank() },
                            fileIdx = if (item.has("fileIdx")) item.optInt("fileIdx") else null
                        ))
                    }
                }
            }
        }
}
