package com.skunk.snapper.ui

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.TextView
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.OverlayWithIW
import org.osmdroid.views.overlay.infowindow.InfoWindow

/**
 * A compact, dark, rounded info bubble for named water features — replaces
 * osmdroid's default light "bonuspack" bubble. Built in code (no layout XML).
 */
class WaterInfoWindow(
    mapView: MapView
) : InfoWindow(buildView(mapView), mapView) {

    override fun onOpen(item: Any?) {
        val label = (item as? OverlayWithIW)?.title?.takeIf { it.isNotBlank() } ?: "Water"
        mView.findViewById<TextView>(android.R.id.text1)?.text = label
        mView.setOnClickListener { close() }  // tap the bubble to dismiss it
    }

    override fun onClose() { /* nothing to clean up */ }

    companion object {
        private fun buildView(mapView: MapView): View {
            val ctx = mapView.context
            val density = ctx.resources.displayMetrics.density
            fun dp(v: Int) = (v * density).toInt()
            return TextView(ctx).apply {
                id = android.R.id.text1
                setTextColor(Color.WHITE)
                textSize = 13f
                setPadding(dp(14), dp(9), dp(14), dp(9))
                background = GradientDrawable().apply {
                    cornerRadius = dp(16).toFloat()
                    setColor(0xF01C1C1E.toInt())  // near-opaque dark, slight translucency
                }
                elevation = dp(6).toFloat()
            }
        }
    }
}
