package com.skunk.snapper.water

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.osmdroid.util.GeoPoint
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** A highlighted body of water from OpenStreetMap. */
data class WaterFeature(
    val name: String?,
    val points: List<GeoPoint>,
    /** true = polygon (lake/pond), false = line (river/stream). */
    val isArea: Boolean
)

/** Queries the Overpass API for fishable water inside a bounding box. */
object WaterFinder {

    // Public Overpass instances rotate through on rate-limit (429) / gateway-timeout (504).
    private val ENDPOINTS = listOf(
        "https://overpass-api.de/api/interpreter",
        "https://overpass.kumi.systems/api/interpreter"
    )

    /** Raised when an endpoint is rate-limited; carries any server-suggested wait. */
    private class RateLimited(val retryAfterMs: Long?) : Exception("Overpass busy")

    // --- Disk-backed cache: bbox-snapped key → features, persisted across restarts. ---
    private const val TTL_MS = 7L * 24 * 60 * 60 * 1000  // water rarely changes; keep a week
    private const val MAX_ENTRIES = 256
    private data class Cached(val features: List<WaterFeature>, val expiresAt: Long)
    private val mem = LinkedHashMap<String, Cached>()
    private var cacheFile: File? = null
    private var loaded = false

    /** Point the cache at on-disk storage. Cheap; safe to call on the main thread. */
    fun init(context: Context) {
        // v2: schema/query changed to named-only + relations; ignore any old cache.
        if (cacheFile == null) cacheFile = File(context.filesDir, "water-cache-v2.json")
    }

    suspend fun fishableWater(
        north: Double,
        south: Double,
        east: Double,
        west: Double
    ): Result<List<WaterFeature>> = withContext(Dispatchers.IO) {
        runCatching {
            val key = cacheKey(north, south, east, west)
            getCached(key)?.let { return@runCatching it }

            val bbox = "$south,$west,$north,$east"
            // Only NAMED water, and include relations (many lakes/ponds are multipolygon
            // relations — e.g. Lake Renwick — which a way-only query silently drops).
            val query = """
                [out:json][timeout:25];
                (
                  way["natural"="water"]["name"]($bbox);
                  relation["natural"="water"]["name"]($bbox);
                  way["landuse"="reservoir"]["name"]($bbox);
                  relation["landuse"="reservoir"]["name"]($bbox);
                  way["waterway"~"^(river|stream|canal)${'$'}"]["name"]($bbox);
                  relation["waterway"~"^(river|canal)${'$'}"]["name"]($bbox);
                );
                out geom;
            """.trimIndent()

            parse(requestWithRetry(query)).also { putCached(key, it) }
        }
    }

    @Synchronized
    private fun getCached(key: String): List<WaterFeature>? {
        ensureLoaded()
        val c = mem[key] ?: return null
        if (c.expiresAt < System.currentTimeMillis()) {
            mem.remove(key)
            return null
        }
        return c.features
    }

    @Synchronized
    private fun putCached(key: String, features: List<WaterFeature>) {
        ensureLoaded()
        mem[key] = Cached(features, System.currentTimeMillis() + TTL_MS)
        while (mem.size > MAX_ENTRIES) {
            val oldest = mem.keys.firstOrNull() ?: break
            mem.remove(oldest)
        }
        save()
    }

    /** Load the persisted cache on first access (runs on the IO dispatcher via fishableWater). */
    private fun ensureLoaded() {
        if (loaded) return
        loaded = true
        val file = cacheFile ?: return
        if (!file.exists()) return
        runCatching {
            val entries = JSONObject(file.readText()).optJSONObject("entries") ?: return
            val now = System.currentTimeMillis()
            val keys = entries.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val e = entries.getJSONObject(key)
                val expiresAt = e.optLong("expiresAt")
                if (expiresAt < now) continue
                val arr = e.optJSONArray("features") ?: continue
                val features = ArrayList<WaterFeature>(arr.length())
                for (i in 0 until arr.length()) {
                    val fo = arr.getJSONObject(i)
                    val ptsArr = fo.optJSONArray("points") ?: continue
                    val pts = ArrayList<GeoPoint>(ptsArr.length())
                    for (j in 0 until ptsArr.length()) {
                        val pair = ptsArr.getJSONArray(j)
                        pts.add(GeoPoint(pair.getDouble(0), pair.getDouble(1)))
                    }
                    val name = if (fo.isNull("name")) null else fo.optString("name").ifBlank { null }
                    features.add(WaterFeature(name, pts, fo.optBoolean("isArea")))
                }
                mem[key] = Cached(features, expiresAt)
            }
        }
    }

    /** Persist the whole cache (small — a few hundred entries at most). */
    private fun save() {
        val file = cacheFile ?: return
        val entries = JSONObject()
        for ((key, cached) in mem) {
            val arr = JSONArray()
            for (f in cached.features) {
                val pts = JSONArray()
                for (p in f.points) pts.put(JSONArray().put(p.latitude).put(p.longitude))
                arr.put(JSONObject().apply {
                    put("name", f.name ?: JSONObject.NULL)
                    put("isArea", f.isArea)
                    put("points", pts)
                })
            }
            entries.put(key, JSONObject().put("expiresAt", cached.expiresAt).put("features", arr))
        }
        runCatching { file.writeText(JSONObject().put("entries", entries).toString()) }
    }

    /** Snap the bbox to a ~1 km grid so near-identical viewports share a cache entry. */
    private fun cacheKey(north: Double, south: Double, east: Double, west: Double): String {
        fun snap(d: Double) = Math.round(d * 100.0) / 100.0
        return "${snap(south)},${snap(west)},${snap(north)},${snap(east)}"
    }

    /** POST the query, rotating endpoints and backing off when rate-limited. */
    private suspend fun requestWithRetry(query: String): String {
        var lastError: Exception? = null
        for (attempt in 0 until 3) {
            try {
                return post(ENDPOINTS[attempt % ENDPOINTS.size], query)
            } catch (e: RateLimited) {
                lastError = e
                // Honour Retry-After if present, else exponential-ish backoff.
                delay(e.retryAfterMs ?: (800L * (attempt + 1)))
            }
        }
        throw lastError ?: IllegalStateException("Overpass unavailable")
    }

    private fun post(endpoint: String, query: String): String {
        val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 30_000
            readTimeout = 60_000
            doOutput = true
            // Overpass blocks requests without a descriptive User-Agent.
            setRequestProperty("User-Agent", "Snapper/1.0 (Android fishing app)")
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        }
        try {
            conn.outputStream.use { it.write("data=${URLEncoder.encode(query, "UTF-8")}".toByteArray()) }
            val code = conn.responseCode
            if (code == 429 || code == 504) {
                val retryAfter = conn.getHeaderField("Retry-After")?.trim()?.toLongOrNull()?.times(1000)
                throw RateLimited(retryAfter)
            }
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream.bufferedReader().use { it.readText() }
            if (code !in 200..299) error("Overpass error $code: ${body.take(200)}")
            return body
        } finally {
            conn.disconnect()
        }
    }

    private fun geomToPoints(geometry: JSONArray): List<GeoPoint> {
        val points = ArrayList<GeoPoint>(geometry.length())
        for (j in 0 until geometry.length()) {
            val node = geometry.optJSONObject(j) ?: continue
            points.add(GeoPoint(node.getDouble("lat"), node.getDouble("lon")))
        }
        return points
    }

    private fun parse(json: String): List<WaterFeature> {
        val elements = JSONObject(json).optJSONArray("elements") ?: return emptyList()
        val features = ArrayList<WaterFeature>(elements.length())

        for (i in 0 until elements.length()) {
            val el = elements.getJSONObject(i)
            val tags = el.optJSONObject("tags") ?: continue
            // Named-only — we filter in the query too, but be defensive.
            val name = tags.optString("name").ifBlank { null } ?: continue
            val isArea = tags.optString("natural") == "water" || tags.optString("landuse") == "reservoir"

            if (el.optString("type") == "relation") {
                // Multipolygon: draw each OUTER ring as its own feature (skip inner = islands/holes).
                val members = el.optJSONArray("members") ?: continue
                for (m in 0 until members.length()) {
                    val mem = members.getJSONObject(m)
                    if (mem.optString("type") != "way") continue
                    if (isArea && mem.optString("role") == "inner") continue
                    val geom = mem.optJSONArray("geometry") ?: continue
                    val pts = geomToPoints(geom)
                    if (pts.size >= 2) features.add(WaterFeature(name, pts, isArea))
                    if (features.size >= 500) return features
                }
            } else {
                val geom = el.optJSONArray("geometry") ?: continue
                val pts = geomToPoints(geom)
                if (pts.size >= 2) features.add(WaterFeature(name, pts, isArea))
            }
            // Keep the overlay responsive on dense areas.
            if (features.size >= 500) break
        }
        return features
    }
}
