package com.skunk.snapper.util

import android.content.Context
import com.skunk.snapper.ai.AreaFish
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Disk-persistent cache for the "What's around" fish list (with photos), keyed by
 * "area|monthYear" so a new month or location re-fetches. Survives app restarts.
 */
object AreaFishStore {

    private const val TTL_MS = 7L * 24 * 60 * 60 * 1000
    private const val MAX_ENTRIES = 24

    private fun file(context: Context) = File(context.filesDir, "around-cache.json")

    @Synchronized
    fun get(context: Context, key: String): List<AreaFish>? {
        val f = file(context)
        if (!f.exists()) return null
        return runCatching {
            val entries = JSONObject(f.readText()).optJSONObject("entries") ?: return null
            val e = entries.optJSONObject(key) ?: return null
            if (e.optLong("expiresAt") < System.currentTimeMillis()) return null
            val arr = e.optJSONArray("fish") ?: return null
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                AreaFish(
                    name = o.optString("name"),
                    scientificName = o.optString("scientific"),
                    lengthRange = o.optString("length"),
                    weightRange = o.optString("weight"),
                    inSeason = o.optBoolean("inSeason", true),
                    seasonNote = o.optString("season"),
                    imageUrl = o.optString("imageUrl").ifBlank { null }
                )
            }
        }.getOrNull()
    }

    @Synchronized
    fun put(context: Context, key: String, fish: List<AreaFish>) {
        val f = file(context)
        runCatching {
            val root = if (f.exists()) JSONObject(f.readText()) else JSONObject()
            val entries = root.optJSONObject("entries") ?: JSONObject()

            val arr = JSONArray()
            fish.forEach { fr ->
                arr.put(JSONObject().apply {
                    put("name", fr.name)
                    put("scientific", fr.scientificName)
                    put("length", fr.lengthRange)
                    put("weight", fr.weightRange)
                    put("inSeason", fr.inSeason)
                    put("season", fr.seasonNote)
                    put("imageUrl", fr.imageUrl ?: "")
                })
            }
            entries.put(key, JSONObject().put("expiresAt", System.currentTimeMillis() + TTL_MS).put("fish", arr))

            // Trim oldest if we've grown too large.
            while (entries.length() > MAX_ENTRIES) {
                val oldest = entries.keys().next()
                entries.remove(oldest)
            }
            f.writeText(root.put("entries", entries).toString())
        }
    }
}
