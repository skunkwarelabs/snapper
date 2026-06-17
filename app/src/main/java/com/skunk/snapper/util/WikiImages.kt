package com.skunk.snapper.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Fetches a representative photo URL for a species from Wikipedia (free, no key). */
object WikiImages {

    private val cache = SimpleCache<String, String>(maxSize = 256, ttlMillis = 30L * 24 * 60 * 60 * 1000)

    /** Returns a thumbnail image URL for the title, or null. Cached. */
    suspend fun thumbnail(title: String): String? = withContext(Dispatchers.IO) {
        val key = title.trim()
        if (key.isBlank()) return@withContext null
        cache.get(key)?.let { return@withContext it.ifBlank { null } }

        val url = runCatching {
            val page = key.replace(" ", "_")
            val endpoint = "https://en.wikipedia.org/api/rest_v1/page/summary/" +
                java.net.URLEncoder.encode(page, "UTF-8").replace("+", "%20")
            val body = get(endpoint)
            JSONObject(body).optJSONObject("thumbnail")?.optString("source")?.ifBlank { null }
        }.getOrNull()

        // Cache the result (even "" for "no image") so we don't refetch misses.
        cache.put(key, url ?: "")
        url
    }

    private fun get(endpoint: String): String {
        val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 15_000
            setRequestProperty("User-Agent", "Snapper/1.0 (Android fishing app)")
        }
        try {
            if (conn.responseCode !in 200..299) error("Wiki ${conn.responseCode}")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}
