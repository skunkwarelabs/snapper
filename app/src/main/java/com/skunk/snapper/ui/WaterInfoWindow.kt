package com.skunk.snapper.ui

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.skunk.snapper.util.WaterFish
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.OverlayWithIW
import org.osmdroid.views.overlay.infowindow.InfoWindow

/**
 * A compact, dark, rounded info bubble for named water features — replaces
 * osmdroid's default light "bonuspack" bubble. Built in code (no layout XML).
 */
class WaterInfoWindow(
    mapView: MapView,
    /** Supplies the state (code/name) the map is currently over, to scope the species lookup. */
    private val stateProvider: (() -> String?)? = null,
    /** Invoked when the angler taps the "Stocked here ›" line; opens the species card list. */
    private val onOpenStocked: ((name: String, species: List<String>, state: String?) -> Unit)? = null
) : InfoWindow(buildView(mapView), mapView) {

    override fun onOpen(item: Any?) {
        val label = (item as? OverlayWithIW)?.title?.takeIf { it.isNotBlank() } ?: "Water"
        mView.findViewById<TextView>(android.R.id.text1)?.text = label

        // Show stocked species for this water (state stocking data), if we have any.
        val state = stateProvider?.invoke()
        val species = WaterFish.speciesFor(mView.context, label.takeIf { it != "Water" }, state)
        mView.findViewById<TextView>(android.R.id.text2)?.apply {
            if (species.isNullOrEmpty()) {
                visibility = View.GONE
                setOnClickListener(null)
            } else {
                visibility = View.VISIBLE
                // Trailing arrow signals it's tappable; tapping opens the species card list.
                text = "Stocked here: " + species.joinToString(", ") + "  ›"
                setOnClickListener { onOpenStocked?.invoke(label, species, state) }
            }
        }
        mView.setOnClickListener { close() }  // tap the bubble (off the link) to dismiss it
    }

    override fun onClose() { /* nothing to clean up */ }

    companion object {
        private fun buildView(mapView: MapView): View {
            val ctx = mapView.context
            val density = ctx.resources.displayMetrics.density
            fun dp(v: Int) = (v * density).toInt()
            return LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14), dp(9), dp(14), dp(9))
                background = GradientDrawable().apply {
                    cornerRadius = dp(16).toFloat()
                    setColor(0xF01C1C1E.toInt())  // near-opaque dark, slight translucency
                }
                elevation = dp(6).toFloat()
                // Bubbles can get wide on waters with long species lists; cap them.
                addView(TextView(ctx).apply {
                    id = android.R.id.text1
                    setTextColor(Color.WHITE)
                    textSize = 13f
                    setTypeface(typeface, Typeface.BOLD)
                    // Wrap the name tightly — a vertical LinearLayout otherwise defaults its
                    // children to MATCH_PARENT, leaving a wide empty bubble when there's no
                    // stocked-species line (the bubble keeps a previous wide measurement).
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                })
                addView(TextView(ctx).apply {
                    id = android.R.id.text2
                    setTextColor(0xFFB6E3F4.toInt())
                    textSize = 11f
                    visibility = View.GONE
                    maxWidth = dp(240)
                    val p = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = dp(3) }
                    layoutParams = p
                })
            }
        }
    }
}
