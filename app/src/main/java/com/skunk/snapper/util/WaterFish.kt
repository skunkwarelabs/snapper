package com.skunk.snapper.util

import android.content.Context
import org.json.JSONObject
import java.util.Locale

/**
 * Loads the bundled multi-state fish-stocking dataset (assets/fish_by_water.json) and
 * answers "which fish have been stocked in this body of water?" by name, scoped to a state.
 *
 * Data is pulled per state from each state's fish & wildlife agency (see tools/fetch_stocking.py).
 * Coverage and species depth vary — some states are trout-only, some aren't covered at all. It's
 * "what's been stocked here," not an exhaustive census of what lives there.
 *
 * Lookups are scoped by state when we know it (from the map's location) so a common name like
 * "Mud Lake" in one state doesn't pull another state's species; without a state we fall back to
 * searching every state.
 */
object WaterFish {

    // State code -> (normalized water name -> sorted species). Includes a "core" alias key per water.
    @Volatile private var data: Map<String, Map<String, List<String>>>? = null

    private val suffixes = setOf("lake", "reservoir", "pond", "river", "creek", "slough", "lagoon")

    private fun normalize(s: String) =
        s.lowercase(Locale.US).replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()

    private fun core(n: String): String {
        val parts = n.split(" ").toMutableList()
        if (parts.size > 1 && parts.first() in suffixes) parts.removeAt(0)
        if (parts.size > 1 && parts.last() in suffixes) parts.removeAt(parts.size - 1)
        return parts.joinToString(" ")
    }

    fun ensureLoaded(context: Context) {
        if (data != null) return
        synchronized(this) {
            if (data != null) return
            runCatching {
                val text = context.assets.open("fish_by_water.json").bufferedReader().use { it.readText() }
                val statesObj = JSONObject(text).getJSONObject("states")
                val out = HashMap<String, Map<String, List<String>>>(statesObj.length() * 2)
                statesObj.keys().forEach { code ->
                    val waters = statesObj.getJSONObject(code)
                    val index = HashMap<String, List<String>>(waters.length() * 2)
                    waters.keys().forEach { name ->
                        val arr = waters.getJSONArray(name)
                        val species = (0 until arr.length()).map { arr.getString(it) }
                        val norm = normalize(name)
                        index[norm] = species
                        index.putIfAbsent(core(norm), species)
                    }
                    out[code.uppercase(Locale.US)] = index
                }
                data = out
            }.onFailure { data = emptyMap() }
        }
    }

    /**
     * Stocked species for a water by name. [state] may be a 2-letter code, a full state name, or an
     * "area" string ending in the state ("Will County, Illinois"); null searches every state.
     */
    fun speciesFor(context: Context, name: String?, state: String?): List<String>? {
        if (name.isNullOrBlank()) return null
        ensureLoaded(context)
        val d = data ?: return null
        val norm = normalize(name)
        val coreN = core(norm)
        stateCode(state)?.let { code ->
            d[code]?.let { idx -> return idx[norm] ?: idx[coreN] }
        }
        // Unknown (or uncovered) state: best-effort across all states.
        for ((_, idx) in d) (idx[norm] ?: idx[coreN])?.let { return it }
        return null
    }

    /** Resolve a 2-letter code, full name, or "County, State" string to a 2-letter state code. */
    fun stateCode(state: String?): String? {
        if (state.isNullOrBlank()) return null
        val raw = state.trim()
        for (c in listOf(raw, raw.substringAfterLast(",").trim())) {
            if (c.length == 2 && c.uppercase(Locale.US) in CODES) return c.uppercase(Locale.US)
            NAME_TO_CODE[c.lowercase(Locale.US)]?.let { return it }
        }
        return null
    }

    private val NAME_TO_CODE: Map<String, String> = mapOf(
        "alabama" to "AL", "alaska" to "AK", "arizona" to "AZ", "arkansas" to "AR",
        "california" to "CA", "colorado" to "CO", "connecticut" to "CT", "delaware" to "DE",
        "florida" to "FL", "georgia" to "GA", "hawaii" to "HI", "idaho" to "ID",
        "illinois" to "IL", "indiana" to "IN", "iowa" to "IA", "kansas" to "KS",
        "kentucky" to "KY", "louisiana" to "LA", "maine" to "ME", "maryland" to "MD",
        "massachusetts" to "MA", "michigan" to "MI", "minnesota" to "MN", "mississippi" to "MS",
        "missouri" to "MO", "montana" to "MT", "nebraska" to "NE", "nevada" to "NV",
        "new hampshire" to "NH", "new jersey" to "NJ", "new mexico" to "NM", "new york" to "NY",
        "north carolina" to "NC", "north dakota" to "ND", "ohio" to "OH", "oklahoma" to "OK",
        "oregon" to "OR", "pennsylvania" to "PA", "rhode island" to "RI", "south carolina" to "SC",
        "south dakota" to "SD", "tennessee" to "TN", "texas" to "TX", "utah" to "UT",
        "vermont" to "VT", "virginia" to "VA", "washington" to "WA", "west virginia" to "WV",
        "wisconsin" to "WI", "wyoming" to "WY"
    )
    private val CODES = NAME_TO_CODE.values.toSet()
}
