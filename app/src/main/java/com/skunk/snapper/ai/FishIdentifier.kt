package com.skunk.snapper.ai

import android.content.Context
import android.graphics.Bitmap
import com.skunk.snapper.util.FishClassifier
import com.skunk.snapper.util.FishFacts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

data class FishGuess(
    /** False when the photo isn't confidently one of our species — UI shows "No fish detected". */
    val isFish: Boolean,
    val species: String,
    val confidence: String,
    val details: String,
    /** Rough length guess in inches, number only e.g. "13" (blank if unknown). */
    val lengthEstimate: String,
    /** Estimated whole pounds, number only e.g. "2" (blank if unknown). */
    val weightLb: String,
    /** Estimated remaining ounces 0–15, number only e.g. "5" (blank if unknown). */
    val weightOz: String
)

/**
 * Identifies a fish from a photo entirely ON-DEVICE via [FishClassifier] (a bundled TFLite
 * model) — no network, no Gemini. Size/weight come from the bundled [FishFacts] typical ranges
 * (a photo rarely has a scale reference). When no model is bundled, [isAvailable] is false and
 * callers fall back to manual species entry.
 */
object FishIdentifier {

    // Below this top-1 confidence we treat the photo as "not a recognizable fish".
    // The fine-tuned EfficientNetB0 (~96% val) is confident on real fish; this rejects
    // off-distribution / non-fish photos. Tune after field-testing real phone shots.
    private const val MIN_CONFIDENCE = 0.45f

    fun isAvailable(context: Context): Boolean = FishClassifier.isAvailable(context)

    suspend fun identify(context: Context, bitmap: Bitmap): Result<FishGuess> =
        withContext(Dispatchers.IO) {
            runCatching {
                val preds = FishClassifier.classify(context, bitmap)
                val best = preds.firstOrNull()
                    ?: error("Identifier model not available")
                if (best.confidence < MIN_CONFIDENCE) return@runCatching noFish()

                val facts = FishFacts.lookup(context, best.label)
                val length = facts?.length?.let { midpoint(it)?.toString() } ?: ""
                val (lb, oz) = facts?.weight?.let { weightFromRange(it) } ?: ("" to "")
                val runnerUp = preds.getOrNull(1)?.takeIf { it.confidence > 0.15f }
                FishGuess(
                    isFish = true,
                    species = best.label,
                    confidence = confidenceLabel(best.confidence),
                    details = runnerUp?.let { "Best match — could also be ${it.label}." }
                        ?: "Best match from on-device recognition.",
                    lengthEstimate = length,
                    weightLb = lb,
                    weightOz = oz
                )
            }
        }

    private fun confidenceLabel(p: Float) = when {
        p >= 0.75f -> "high"
        p >= 0.55f -> "medium"
        else -> "low"
    }

    /** Average of the numbers in a range like "12-18 in" → 15. */
    private fun midpoint(range: String): Int? {
        val nums = Regex("[0-9]+(?:\\.[0-9]+)?").findAll(range).map { it.value.toDouble() }.toList()
        return if (nums.isEmpty()) null else nums.average().roundToInt()
    }

    /** "1-5 lb" → ("3","0"); "0.25-1 lb" → ("0","10"). Splits the average into lb + oz. */
    private fun weightFromRange(range: String): Pair<String, String> {
        val nums = Regex("[0-9]+(?:\\.[0-9]+)?").findAll(range).map { it.value.toDouble() }.toList()
        if (nums.isEmpty()) return "" to ""
        val totalOz = (nums.average() * 16).roundToInt()
        return (totalOz / 16).toString() to (totalOz % 16).toString()
    }

    private fun noFish() = FishGuess(
        isFish = false, species = "", confidence = "",
        details = "", lengthEstimate = "", weightLb = "", weightOz = ""
    )
}
