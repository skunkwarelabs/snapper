package com.skunk.snapper.util

import android.content.Context
import org.json.JSONObject
import java.util.Locale

/** One species' statewide bag/size limit, from the bundled regulations dataset. */
data class SpeciesReg(
    val name: String,
    val dailyBag: String,
    val minLength: String,
    val notes: String
)

/** All regulations for one state. */
data class StateRegs(
    val state: String,
    val stateCode: String,
    val source: String,
    val lastVerified: String,
    val species: List<SpeciesReg>
)

/**
 * Loads the bundled statewide fishing-regulations dataset (assets/regulations.json) once
 * and answers "what's the bag limit / min size for this species in this state?".
 *
 * The dataset holds STATEWIDE general/default freshwater limits compiled from official
 * state DNR sources — many waters have their own exceptions, so treat it as guidance.
 * Species lookups are fuzzy (normalized names + common aliases) because the species names
 * coming from the "What's around" tab (Gemini) won't match the dataset's exactly.
 */
object FishRegs {

    @Volatile private var states: Map<String, StateRegs>? = null
    @Volatile var disclaimer: String = "Always confirm with the official state DNR regulations before keeping fish."
        private set

    /** Common-name aliases → canonical dataset name. All keys are normalized (lowercase). */
    private val aliases: Map<String, String> = buildMap {
        put("largemouth", "largemouth bass")
        put("largemouth black bass", "largemouth bass")
        put("florida bass", "largemouth bass")
        put("smallmouth", "smallmouth bass")
        put("spotted bass", "spotted bass")
        put("kentucky bass", "spotted bass")
        put("muskie", "muskellunge")
        put("musky", "muskellunge")
        put("tiger muskie", "muskellunge")
        put("tiger musky", "muskellunge")
        put("northern", "northern pike")
        put("pike", "northern pike")
        put("pickerel", "chain pickerel")
        put("crappie", "crappie")
        put("black crappie", "black crappie")
        put("white crappie", "white crappie")
        put("speckled perch", "black crappie")
        put("sunfish", "bluegill")
        put("bream", "bluegill")
        put("redear", "redear sunfish")
        put("shellcracker", "redear sunfish")
        put("perch", "yellow perch")
        put("channel cat", "channel catfish")
        put("flathead", "flathead catfish")
        put("blue cat", "blue catfish")
        put("rainbow", "rainbow trout")
        put("steelhead", "rainbow trout")
        put("brown", "brown trout")
        put("brookie", "brook trout")
        put("brook", "brook trout")
        put("laker", "lake trout")
        put("togue", "lake trout")
        put("striper", "striped bass")
        put("rockfish", "striped bass")
        put("wiper", "hybrid striped bass")
        put("hybrid striped bass", "hybrid striped bass")
        put("sunshine bass", "hybrid striped bass")
        put("king salmon", "chinook salmon")
        put("chinook", "chinook salmon")
        put("silver salmon", "coho salmon")
        put("coho", "coho salmon")
        put("sturgeon", "lake sturgeon")
        put("hornpout", "bullhead")
    }

    private fun normalize(s: String) =
        s.trim().lowercase(Locale.US).replace(Regex("[^a-z ]"), "").replace(Regex("\\s+"), " ").trim()

    /** Collapse any "None statewide"/"None (varies)"/blank min size to a plain "None". */
    private fun normalizeMin(v: String): String {
        val t = v.trim()
        return if (t.isBlank() || t.startsWith("None", ignoreCase = true)) "None" else t
    }

    /** Load the dataset from assets if not already loaded. Safe to call repeatedly. */
    fun ensureLoaded(context: Context) {
        if (states != null) return
        synchronized(this) {
            if (states != null) return
            runCatching {
                val text = context.assets.open("regulations.json").bufferedReader().use { it.readText() }
                val root = JSONObject(text)
                root.optString("disclaimer").takeIf { it.isNotBlank() }?.let { disclaimer = it }
                val statesObj = root.getJSONObject("states")
                val map = LinkedHashMap<String, StateRegs>()
                statesObj.keys().forEach { stateName ->
                    val o = statesObj.getJSONObject(stateName)
                    val arr = o.getJSONArray("species")
                    val species = (0 until arr.length()).map { i ->
                        val sp = arr.getJSONObject(i)
                        SpeciesReg(
                            name = sp.optString("name"),
                            dailyBag = sp.optString("dailyBag").ifBlank { "—" },
                            minLength = normalizeMin(sp.optString("minLength")),
                            notes = sp.optString("notes")
                        )
                    }
                    map[normalize(stateName)] = StateRegs(
                        state = stateName,
                        stateCode = o.optString("stateCode"),
                        source = o.optString("source"),
                        lastVerified = o.optString("lastVerified"),
                        species = species
                    )
                }
                states = map
            }.onFailure { states = emptyMap() }
        }
    }

    /**
     * All regulations for a state. [state] may be a full name ("Illinois"), a two-letter
     * code ("IL"), or an "area" label whose last component is the state ("Will County, Illinois").
     */
    fun regsFor(context: Context, state: String): StateRegs? {
        ensureLoaded(context)
        val map = states ?: return null
        val raw = state.trim()
        // Try the whole string, then the last comma-separated piece (area "County, State").
        val candidates = listOf(raw, raw.substringAfterLast(",").trim())
        for (c in candidates) {
            val key = normalize(c)
            map[key]?.let { return it }
            // Two-letter postal code.
            if (c.length == 2) map.values.firstOrNull { it.stateCode.equals(c, true) }?.let { return it }
        }
        return null
    }

    /**
     * The bag/size limit for one species in a state, or null if the state isn't covered or
     * the species isn't listed there. [speciesName] is matched fuzzily.
     */
    fun lookup(context: Context, state: String, speciesName: String): SpeciesReg? {
        val regs = regsFor(context, state) ?: return null
        val target = canonicalSpecies(speciesName)
        // Exact normalized match first, then alias-resolved match, then a contains fallback.
        regs.species.firstOrNull { normalize(it.name) == target }?.let { return it }
        regs.species.firstOrNull { canonicalSpecies(it.name) == target }?.let { return it }
        return regs.species.firstOrNull {
            val n = normalize(it.name)
            n.contains(target) || target.contains(n)
        }
    }

    /**
     * Best-effort "can you keep one right now?" from the bag limit + notes. The dataset has no
     * calendar seasons, so this only distinguishes harvestable from catch-and-release/protected.
     * Seasonal closures (notes with a month, a date, or "spawn") still count as harvestable since
     * the species is keepable most of the year.
     */
    fun isHarvestable(reg: SpeciesReg): Boolean {
        val bag = reg.dailyBag.trim().lowercase(Locale.US)
        if (bag == "0" || bag.startsWith("catch")) return false
        val n = reg.notes.lowercase(Locale.US)
        val seasonal = n.contains("spawn") || n.contains(Regex("\\d")) ||
            n.contains(Regex("\\b(jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)"))
        val protectedKw = listOf(
            "no harvest", "protected", "endangered", "possession prohibited",
            "illegal to possess", "release immediately", "released immediately",
            "must be released", "season closed", "catch and release only", "c&r only"
        )
        if (!seasonal && protectedKw.any { n.contains(it) }) return false
        return true
    }

    /** Resolve a common name to its canonical (normalized) dataset name via the alias table. */
    private fun canonicalSpecies(name: String): String {
        val n = normalize(name)
        aliases[n]?.let { return normalize(it) }
        // Try aliases as substrings ("largemouth bass (florida strain)" → "largemouth").
        for ((alias, canon) in aliases) {
            if (n.contains(alias)) return normalize(canon)
        }
        return n
    }
}
