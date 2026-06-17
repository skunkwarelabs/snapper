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
    val weightOz: String,
    /** Other close matches (next most likely species), so the UI can offer one-tap corrections. */
    val alternatives: List<String> = emptyList()
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
    // off-distribution / non-fish photos. Kept fairly low because real phone shots are
    // off-distribution vs the iNaturalist training set — a wrong-but-confident guess is
    // recoverable (the user can tap an alternative), a false "no fish" is just annoying.
    private const val MIN_CONFIDENCE = 0.35f

    // A runner-up must clear this to be offered as a tappable alternative.
    private const val MIN_ALT_CONFIDENCE = 0.08f

    fun isAvailable(context: Context): Boolean = FishClassifier.isAvailable(context)

    suspend fun identify(context: Context, bitmap: Bitmap): Result<FishGuess> =
        withContext(Dispatchers.IO) {
            runCatching {
                val preds = FishClassifier.classify(context, bitmap)
                val best = preds.firstOrNull()
                    ?: error("Identifier model not available")
                if (best.confidence < MIN_CONFIDENCE) return@runCatching noFish()

                val alts = preds.drop(1)
                    .filter { it.confidence >= MIN_ALT_CONFIDENCE }
                    .take(2)
                    .map { it.label }
                buildGuess(context, best.label, confidenceLabel(best.confidence), alts)
            }
        }

    /**
     * Build a guess for an explicitly chosen species (e.g. the user tapped one of the
     * [FishGuess.alternatives]). Confidence is blank since this is a human pick, not the model's.
     */
    fun guessFor(context: Context, species: String, alternatives: List<String> = emptyList()): FishGuess =
        buildGuess(context, species, "", alternatives)

    private fun buildGuess(
        context: Context,
        species: String,
        confidence: String,
        alternatives: List<String>
    ): FishGuess {
        val facts = FishFacts.lookup(context, species)
        val length = facts?.length?.let { midpoint(it)?.toString() } ?: ""
        val (lb, oz) = facts?.weight?.let { weightFromRange(it) } ?: ("" to "")
        return FishGuess(
            isFish = true,
            species = species,
            confidence = confidence,
            details = if (alternatives.isEmpty()) "Best match from on-device recognition."
            else "On-device match — could also be ${alternatives.joinToString(" or ")}.",
            lengthEstimate = length,
            weightLb = lb,
            weightOz = oz,
            alternatives = alternatives
        )
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
