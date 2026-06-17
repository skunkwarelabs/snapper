package com.skunk.snapper.util

import android.content.Context
import java.util.Locale

/**
 * Maps a species name to a bundled photo in assets/fish/<slug>.webp (high-res, ~1600px).
 * Lets the Regs cards and detail show a picture instantly with no network call. Returns null
 * for species without a bundled image so callers can fall back to a fetched thumbnail.
 */
object FishPhotos {

    private const val DIR = "fish"
    @Volatile private var available: Set<String>? = null

    private fun ensure(context: Context) {
        if (available != null) return
        synchronized(this) {
            if (available == null) {
                available = runCatching { context.assets.list(DIR)?.toSet() }.getOrNull() ?: emptySet()
            }
        }
    }

    private fun slug(name: String) =
        name.trim().lowercase(Locale.US).replace(Regex("[^a-z0-9]+"), "_").trim('_')

    /** A `file:///android_asset/...` URI for the species' bundled photo, or null if not bundled. */
    fun assetUri(context: Context, speciesName: String): String? {
        ensure(context)
        val file = "${slug(speciesName)}.webp"
        return if (available?.contains(file) == true) "file:///android_asset/$DIR/$file" else null
    }
}
