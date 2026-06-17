package com.skunk.snapper.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** A single logged catch. */
@Entity(tableName = "catches")
data class Catch(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val species: String,
    /** Model's confidence: "high" / "medium" / "low" (or blank if logged manually). */
    val confidence: String = "",
    /** One-line identifying detail or fun fact from the model. */
    val details: String = "",
    /** Estimated length in inches, number only e.g. "13" (model guess or hand-entered; blank if unknown). */
    val lengthEstimate: String = "",
    /** Estimated whole pounds, number only e.g. "2" (model guess or hand-entered; blank if unknown). */
    val weightLb: String = "",
    /** Estimated remaining ounces 0–15, number only e.g. "5" (model guess or hand-entered; blank if unknown). */
    val weightOz: String = "",
    /** The angler's own notes. */
    val notes: String = "",
    /** Absolute path to the photo in the app's internal storage. */
    val photoPath: String,
    /** When the catch was logged, epoch millis. */
    val caughtAt: Long,
    /** Where it was caught, if location was available. */
    val lat: Double? = null,
    val lng: Double? = null
)
