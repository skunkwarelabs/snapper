package com.skunk.snapper.util

import android.content.Context
import android.location.Geocoder
import java.util.Locale

/** Reverse-geocoding helpers (uses the platform Geocoder — no API key). */
object PlaceLookup {

    /**
     * Best-effort US state (adminArea) for a coordinate, or null.
     * Blocking / does I/O — call off the main thread.
     */
    @Suppress("DEPRECATION")
    fun stateFor(context: Context, lat: Double, lng: Double): String? = try {
        Geocoder(context, Locale.getDefault())
            .getFromLocation(lat, lng, 1)
            ?.firstOrNull()
            ?.adminArea
            ?.takeIf { it.isNotBlank() }
    } catch (e: Exception) {
        null
    }

    /**
     * A "general area" label for a coordinate — "County, State" when available,
     * else just the state. Null if it can't be resolved. Blocking — call off main.
     */
    @Suppress("DEPRECATION")
    fun areaFor(context: Context, lat: Double, lng: Double): String? = try {
        val addr = Geocoder(context, Locale.getDefault()).getFromLocation(lat, lng, 1)?.firstOrNull()
        val state = addr?.adminArea?.takeIf { it.isNotBlank() }
        val county = addr?.subAdminArea?.takeIf { it.isNotBlank() }
        when {
            county != null && state != null -> "$county, $state"
            else -> state
        }
    } catch (e: Exception) {
        null
    }
}
