package com.skunk.snapper.ai

/** One fish row for a regulations list (Regs tab / map water-bubble sheet). */
data class AreaFish(
    val name: String,
    val scientificName: String,
    val lengthRange: String,
    val weightRange: String,
    val inSeason: Boolean,
    val seasonNote: String,
    /** Wikipedia thumbnail URL, filled in later (null until/if fetched). */
    val imageUrl: String? = null,
    /** Statewide daily bag limit from the bundled regs dataset (null if not covered). */
    val bagLimit: String? = null,
    /** Statewide minimum legal size from the bundled regs dataset (null if not covered). */
    val minSize: String? = null
)
