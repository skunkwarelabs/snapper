package com.skunk.snapper.util

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * On-device fish-species classifier backed by a bundled TensorFlow Lite model
 * (assets/fish_model.tflite + assets/fish_labels.txt). Replaces the Gemini photo ID.
 *
 * The model is produced by tools/train_fish_model.py. Until it's bundled, [isAvailable] is
 * false and the app falls back to manual species entry. Model contract: input 224×224×3
 * float32 with pixel values 0–255, output a softmax over the label file's line order.
 */
object FishClassifier {

    private const val MODEL = "fish_model.tflite"
    private const val LABELS = "fish_labels.txt"
    private const val SIZE = 224

    data class Prediction(val label: String, val confidence: Float)

    @Volatile private var interpreter: Interpreter? = null
    @Volatile private var labels: List<String> = emptyList()
    @Volatile private var triedLoad = false

    /** True once the bundled model has loaded. Lazy + cached. */
    fun isAvailable(context: Context): Boolean {
        ensureLoaded(context)
        return interpreter != null && labels.isNotEmpty()
    }

    @Synchronized
    private fun ensureLoaded(context: Context) {
        if (triedLoad) return
        triedLoad = true
        runCatching {
            val bytes = context.assets.open(MODEL).use { it.readBytes() }
            val buf = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder())
            buf.put(bytes); buf.rewind()
            val itp = Interpreter(buf)
            val lbls = context.assets.open(LABELS).bufferedReader().use { r ->
                r.readLines().map { it.trim() }.filter { it.isNotEmpty() }
            }
            interpreter = itp
            labels = lbls
        }.onFailure { interpreter = null }  // no model bundled yet → manual fallback
    }

    /** Top predictions (highest confidence first), or empty if the model isn't available. */
    fun classify(context: Context, bitmap: Bitmap): List<Prediction> {
        ensureLoaded(context)
        val itp = interpreter ?: return emptyList()
        val output = Array(1) { FloatArray(labels.size) }
        itp.run(preprocess(bitmap), output)
        return labels.indices
            .map { Prediction(labels[it], output[0][it]) }
            .sortedByDescending { it.confidence }
    }

    private fun preprocess(bitmap: Bitmap): ByteBuffer {
        val scaled = Bitmap.createScaledBitmap(bitmap, SIZE, SIZE, true)
        val buf = ByteBuffer.allocateDirect(1 * SIZE * SIZE * 3 * 4).order(ByteOrder.nativeOrder())
        val px = IntArray(SIZE * SIZE)
        scaled.getPixels(px, 0, SIZE, 0, 0, SIZE, SIZE)
        for (p in px) {
            buf.putFloat(((p shr 16) and 0xFF).toFloat())  // R, 0–255
            buf.putFloat(((p shr 8) and 0xFF).toFloat())   // G
            buf.putFloat((p and 0xFF).toFloat())           // B
        }
        buf.rewind()
        return buf
    }
}
