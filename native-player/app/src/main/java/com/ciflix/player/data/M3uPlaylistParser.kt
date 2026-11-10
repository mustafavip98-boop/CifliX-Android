package com.ciflix.player.data

import java.net.URI

/** Parses M3U/M3U8 metadata and resolves relative stream/logo URLs. */
data class M3uChannel(
    val name: String,
    val url: String,
    val group: String? = null,
    val logo: String? = null,
    val tvgId: String? = null,
    val tvgName: String? = null,
    val durationSeconds: Double? = null
)

object M3uPlaylistParser {
    private val attributeRegex = Regex("""([\w-]+)\s*=\s*("(?:[^"]*)"|'(?:[^']*)'|[^\s,]+)""")

    fun parse(content: String, playlistUrl: String? = null): List<M3uChannel> {
        val channels = mutableListOf<M3uChannel>()
        var pendingName: String? = null
        var pendingGroup: String? = null
        var pendingLogo: String? = null
        var pendingTvgId: String? = null
        var pendingTvgName: String? = null
        var pendingDuration: Double? = null
        var currentGroup: String? = null

        content.removePrefix("\uFEFF").lineSequence().forEach { raw ->
            val line = raw.trim()
            if (line.isEmpty()) return@forEach

            when {
                line.startsWith("#EXTINF:", ignoreCase = true) -> {
                    val payload = line.substringAfter(':')
                    val comma = payload.lastIndexOf(',')
                    val metadata = if (comma >= 0) payload.substring(0, comma) else payload
                    val displayName = if (comma >= 0) payload.substring(comma + 1).trim() else ""
                    val attrs = attributeRegex.findAll(metadata).associate { match ->
                        val rawValue = match.groupValues[2].trim()
                        match.groupValues[1].lowercase() to rawValue.removeSurrounding("\"").removeSurrounding("'")
                    }
                    pendingName = displayName.ifBlank { attrs["tvg-name"].orEmpty() }.ifBlank { null }
                    pendingGroup = attrs["group-title"]?.takeIf { it.isNotBlank() } ?: currentGroup
                    pendingLogo = attrs["tvg-logo"]?.takeIf { it.isNotBlank() }
                    pendingTvgId = attrs["tvg-id"]?.takeIf { it.isNotBlank() }
                    pendingTvgName = attrs["tvg-name"]?.takeIf { it.isNotBlank() }
                    pendingDuration = metadata.substringBefore(' ').toDoubleOrNull()
                }
                line.startsWith("#EXTGRP:", ignoreCase = true) -> {
                    currentGroup = line.substringAfter(':').trim().takeIf { it.isNotBlank() }
                    if (pendingGroup == null) pendingGroup = currentGroup
                }
                line.startsWith("#") -> Unit
                else -> {
                    val resolved = resolveUrl(line, playlistUrl) ?: return@forEach
                    channels += M3uChannel(
                        name = pendingName ?: pendingTvgName ?: resolved.substringAfterLast('/').ifBlank { "قناة " + (channels.size + 1) },
                        url = resolved,
                        group = pendingGroup,
                        logo = pendingLogo?.let { resolveUrl(it, playlistUrl) ?: it },
                        tvgId = pendingTvgId,
                        tvgName = pendingTvgName,
                        durationSeconds = pendingDuration
                    )
                    pendingName = null
                    pendingGroup = currentGroup
                    pendingLogo = null
                    pendingTvgId = null
                    pendingTvgName = null
                    pendingDuration = null
                }
            }
        }
        return channels
    }

    private fun resolveUrl(value: String, baseUrl: String?): String? {
        if (value.startsWith("https://", true) || value.startsWith("http://", true)) return value
        if (baseUrl.isNullOrBlank()) return null
        return runCatching { URI(baseUrl).resolve(value).toString() }
            .getOrNull()
            ?.takeIf { it.startsWith("https://") || it.startsWith("http://") }
    }
}
