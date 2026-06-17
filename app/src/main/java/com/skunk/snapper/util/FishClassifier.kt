package com.skunk.snapper.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * On-device fish-species classifier backed by a bundled TensorFlow Lite model
 * (assets/fish_model.tflite + assets/fish_labels.txt). Replaces the Gemini photo ID.
 *
 * The model is produced by tools/train_fish_model.py. Until it's bundled, [isAvailable] is
 * false and the app falls back to manual species entry. Model contract: a square float32
 * RGB input with pixel values 0–255, output a softmax over the label file's line order.
 * The input side length is read from the model itself ([inputSize]) — no constant to keep in
 * sync, so any backbone/resolution from the trainer drops in without a code change.
 */
object FishClassifier {

    private const val MODEL = "fish_model.tflite"
    private const val LABELS = "fish_labels.txt"
    private const val DEFAULT_SIZE = 300

    data class Prediction(val label: String, val confidence: Float)

    @Volatile private var interpreter: Interpreter? = null
    @Volatile private var labels: List<String> = emptyList()
    @Volatile private var inputSize = DEFAULT_SIZE
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
            // Input tensor shape is [1, H, W, 3]; trust the model for the side length.
            itp.getInputTensor(0).shape().getOrNull(1)?.takeIf { it > 0 }?.let { inputSize = it }
            val lbls = context.assets.open(LABELS).bufferedReader().use { r ->
                r.readLines().map { it.trim() }.filter { it.isNotEmpty() }
            }
            interpreter = itp
            labels = lbls
        }.onFailure { interpreter = null }  // no model bundled yet → manual fallback
    }

    /**
     * Top predictions (highest confidence first), or empty if the model isn't available.
     *
     * Uses test-time augmentation: the softmax is averaged over a few in-distribution views
     * of the photo (the full frame, its mirror, and a center crop). The mirror matches the
     * RandomFlip the model trained with, and the center crop sits inside its RandomZoom range
     * while zeroing in on the (usually centered) fish — together they steady the prediction on
     * noisy real-world phone shots for a small, cheap accuracy bump.
     */
    fun classify(context: Context, bitmap: Bitmap): List<Prediction> {
        ensureLoaded(context)
        val itp = interpreter ?: return emptyList()
        val n = labels.size
        val summed = FloatArray(n)
        var views = 0
        for (view in ttaViews(bitmap)) {
            val output = Array(1) { FloatArray(n) }
            itp.run(preprocess(view), output)
            for (i in 0 until n) summed[i] += output[0][i]
            views++
        }
        if (views == 0) return emptyList()
        return labels.indices
            .map { Prediction(labels[it], summed[it] / views) }
            .sortedByDescending { it.confidence }
    }

    /** The full frame, a horizontal mirror, and an 80% center crop (all later squished to inputSize). */
    private fun ttaViews(src: Bitmap): List<Bitmap> {
        val views = ArrayList<Bitmap>(3)
        views.add(src)
        runCatching {
            val m = Matrix().apply { preScale(-1f, 1f) }
            views.add(Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true))
        }
        runCatching {
            val cw = (src.width * 0.8f).toInt().coerceAtLeast(1)
            val ch = (src.height * 0.8f).toInt().coerceAtLeast(1)
            views.add(Bitmap.createBitmap(src, (src.width - cw) / 2, (src.height - ch) / 2, cw, ch))
        }
        return views
    }

    private fun preprocess(bitmap: Bitmap): ByteBuffer {
        val size = inputSize
        val scaled = Bitmap.createScaledBitmap(bitmap, size, size, true)
        val buf = ByteBuffer.allocateDirect(1 * size * size * 3 * 4).order(ByteOrder.nativeOrder())
        val px = IntArray(size * size)
        scaled.getPixels(px, 0, size, 0, 0, size, size)
        for (p in px) {
            buf.putFloat(((p shr 16) and 0xFF).toFloat())  // R, 0–255
            buf.putFloat(((p shr 8) and 0xFF).toFloat())   // G
            buf.putFloat((p and 0xFF).toFloat())           // B
        }
        buf.rewind()
        return buf
    }
}
