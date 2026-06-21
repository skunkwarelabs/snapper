package com.skunk.snapper.ui

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.OverlayWithIW
import org.osmdroid.views.overlay.infowindow.InfoWindow

/**
 * A compact dark bubble for a saved spot — just shows the spot's name (user-set, else the
 * auto-derived one held in the marker's title). Tap it to dismiss. Mirrors [WaterInfoWindow].
 */
class SpotInfoWindow(mapView: MapView) : InfoWindow(buildView(mapView), mapView) {

    override fun onOpen(item: Any?) {
        val label = (item as? OverlayWithIW)?.title?.takeIf { it.isNotBlank() } ?: "Saved spot"
        mView.findViewById<TextView>(android.R.id.text1)?.text = label
        mView.setOnClickListener { close() }
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
                    setColor(0xF01C1C1E.toInt())
                }
                elevation = dp(6).toFloat()
                addView(TextView(ctx).apply {
                    id = android.R.id.text1
                    setTextColor(Color.WHITE)
                    textSize = 13f
                    setTypeface(typeface, Typeface.BOLD)
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                })
            }
        }
    }
}
