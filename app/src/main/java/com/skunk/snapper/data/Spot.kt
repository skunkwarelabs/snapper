package com.skunk.snapper.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A saved fishing spot: a specific GPS point the angler wants to remember.
 * [name] is what the user typed; if blank, [autoName] (derived from the nearest
 * water/place when the spot was saved) is shown instead.
 */
@Entity(tableName = "spots")
data class Spot(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** User-set name; blank means fall back to [autoName]. */
    val name: String = "",
    /** Name auto-derived from the nearest named water/place at save time. */
    val autoName: String = "",
    val lat: Double,
    val lng: Double,
    /** When the spot was saved, epoch millis. */
    val createdAt: Long,
    /** Absolute path to a photo of the spot in internal storage, if the angler added one. */
    val photoPath: String? = null
) {
    /** What to show on the map and in the list: the user's name, else the derived one. */
    val displayName: String
        get() = name.ifBlank { autoName }.ifBlank { "Saved spot" }
}
