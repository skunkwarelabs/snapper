package com.skunk.snapper.util

import android.content.Context
import org.json.JSONObject
import java.util.Locale

/** Static field-guide facts for one species (generic, not location-specific). */
data class FishFact(
    val scientific: String,
    val length: String,
    val weight: String,
    val about: String,
    val identification: String,
    val habitat: String,
    val diet: String,
    val bestTime: String
)

/**
 * Bundled per-species facts (assets/fish_facts.json) — typical size/weight plus a short field
 * guide. Lets the Regs cards and detail screen show everything instantly, with no network call.
 * Species names match the regulations dataset; lookups are normalized + alias-aware.
 */
object FishFacts {

    @Volatile private var facts: Map<String, FishFact>? = null

    /** Common-name aliases → canonical key (normalized lowercase). */
    private val aliases: Map<String, String> = mapOf(
        "largemouth" to "largemouth bass",
        "smallmouth" to "smallmouth bass",
        "muskie" to "muskellunge",
        "musky" to "muskellunge",
        "pike" to "northern pike",
        "pickerel" to "chain pickerel",
        "speckled perch" to "black crappie",
        "sunfish" to "bluegill",
        "bream" to "bluegill",
        "redear" to "redear sunfish",
        "shellcracker" to "redear sunfish",
        "perch" to "yellow perch",
        "channel cat" to "channel catfish",
        "flathead" to "flathead catfish",
        "blue cat" to "blue catfish",
        "steelhead" to "rainbow trout",
        "togue" to "lake trout",
        "striper" to "striped bass",
        "wiper" to "hybrid striped bass",
        "king salmon" to "chinook salmon",
        "chinook" to "chinook salmon",
        "silver salmon" to "coho salmon",
        "coho" to "coho salmon",
        "sturgeon" to "lake sturgeon",
        "hornpout" to "bullhead",
        "tucunare" to "peacock bass"
    )

    private fun normalize(s: String) =
        s.trim().lowercase(Locale.US).replace(Regex("[^a-z ]"), "").replace(Regex("\\s+"), " ").trim()

    fun ensureLoaded(context: Context) {
        if (facts != null) return
        synchronized(this) {
            if (facts != null) return
            runCatching {
                val text = context.assets.open("fish_facts.json").bufferedReader().use { it.readText() }
                val obj = JSONObject(text).getJSONObject("facts")
                val map = HashMap<String, FishFact>()
                obj.keys().forEach { name ->
                    val o = obj.getJSONObject(name)
                    map[normalize(name)] = FishFact(
                        scientific = o.optString("scientific"),
                        length = o.optString("length"),
                        weight = o.optString("weight"),
                        about = o.optString("about"),
                        identification = o.optString("identification"),
                        habitat = o.optString("habitat"),
                        diet = o.optString("diet"),
                        bestTime = o.optString("bestTime")
                    )
                }
                facts = map
            }.onFailure { facts = emptyMap() }
        }
    }

    /** Facts for a species name (fuzzy: normalized name, then alias, then contains), or null. */
    fun lookup(context: Context, speciesName: String): FishFact? {
        ensureLoaded(context)
        val map = facts ?: return null
        val n = normalize(speciesName)
        map[n]?.let { return it }
        aliases[n]?.let { map[normalize(it)]?.let { f -> return f } }
        for ((alias, canon) in aliases) {
            if (n.contains(alias)) map[normalize(canon)]?.let { return it }
        }
        return map.entries.firstOrNull { (k, _) -> n.contains(k) || k.contains(n) }?.value
    }
}
