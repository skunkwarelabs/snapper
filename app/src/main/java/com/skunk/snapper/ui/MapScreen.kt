package com.skunk.snapper.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Water
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.skunk.snapper.data.Catch
import com.skunk.snapper.util.LocationProvider
import com.skunk.snapper.water.WaterFeature
import com.skunk.snapper.water.WaterFinder
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.events.DelayedMapListener
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.infowindow.InfoWindow
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(vm: CatchViewModel, onOpenCatch: (Long) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val dark = isSystemInDarkTheme()
    val catches by vm.catches.collectAsState()

    var loading by remember { mutableStateOf(false) }
    // Bounding box of the last successful scan — used to skip redundant auto-scans.
    var lastScan by remember { mutableStateOf<org.osmdroid.util.BoundingBox?>(null) }

    LaunchedEffect(Unit) {
        Configuration.getInstance().apply {
            userAgentValue = context.packageName
            osmdroidBasePath = context.cacheDir
            osmdroidTileCache = context.cacheDir.resolve("osm-tiles")
        }
        WaterFinder.init(context)
    }

    val mapView = remember {
        MapView(context).apply {
            // Start on the basemap that matches the theme so dark mode never flashes white.
            setTileSource(if (dark) CartoDarkTiles else TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            // Pinch-to-zoom is enough; hide the redundant +/- buttons.
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            controller.setZoom(5.0)
            controller.setCenter(GeoPoint(39.5, -98.35))
            applyMapBackground(dark)
        }
    }

    // Shared dark bubble, attached only to water that actually has a name. Just labels the
    // water; tap it to dismiss. (Regulations live on their own tab.)
    val waterInfoWindow = remember { WaterInfoWindow(mapView) }

    // Tapping anywhere on the map (not on a feature) dismisses any open water bubble.
    val tapToClose = remember {
        MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                InfoWindow.closeAllInfoWindowsOn(mapView)
                return false  // don't consume — let feature taps still open their bubble
            }
            override fun longPressHelper(p: GeoPoint?): Boolean = false
        })
    }

    val locationOverlay = remember {
        MyLocationNewOverlay(GpsMyLocationProvider(context), mapView).apply {
            enableMyLocation()
            val dot = blueLocationDot()
            setDirectionArrow(dot, dot) // replace the default "person" with a blue dot
            setPersonAnchor(0.5f, 0.5f)
            setDirectionAnchor(0.5f, 0.5f)
        }
    }

    // Use a real dark basemap (CARTO dark matter) in dark mode, MAPNIK otherwise.
    LaunchedEffect(dark) {
        mapView.setTileSource(if (dark) CartoDarkTiles else TileSourceFactory.MAPNIK)
        mapView.applyMapBackground(dark)
        mapView.invalidate()
    }

    // Always render water in blue (independent of the app's theme color).
    val waterFill = androidx.compose.ui.graphics.Color(0x552196F3).toArgb()
    val waterStroke = androidx.compose.ui.graphics.Color(0xFF1976D2).toArgb()

    fun drawWater(features: List<WaterFeature>) {
        mapView.overlays.removeAll { it is Polygon || it is Polyline }
        // Insert water at the bottom of the overlay stack (index 0) so catch
        // markers and the location dot always render on top of it.
        features.forEach { f ->
            if (f.isArea) {
                mapView.overlays.add(0, Polygon(mapView).apply {
                    points = f.points
                    fillPaint.color = waterFill
                    outlinePaint.color = waterStroke
                    outlinePaint.strokeWidth = 3f
                    // Named water shows the dark bubble; unnamed shows nothing.
                    infoWindow = if (f.name != null) waterInfoWindow else null
                    f.name?.let { title = it }
                })
            } else {
                mapView.overlays.add(0, Polyline(mapView).apply {
                    setPoints(f.points)
                    outlinePaint.color = waterStroke
                    outlinePaint.strokeWidth = 8f
                    infoWindow = if (f.name != null) waterInfoWindow else null
                    f.name?.let { title = it }
                })
            }
        }
        mapView.invalidate()
    }

    fun findWaterHere(announce: Boolean) {
        if (loading) return
        // Don't auto-scan the wide default view: the bbox is too large for Overpass
        // to return usefully, and scanning it would poison the de-dup below.
        if (!announce && mapView.zoomLevelDouble < 10.0) return
        val box = mapView.boundingBox ?: return
        // Skip auto-scans whose viewport is already fully covered by the last scan
        // (panning/zooming within an area we've already fetched). This is what was
        // hammering Overpass into 429s — but only skip when truly redundant.
        val prev = lastScan
        if (!announce && prev != null &&
            prev.latNorth >= box.latNorth && prev.latSouth <= box.latSouth &&
            prev.lonEast >= box.lonEast && prev.lonWest <= box.lonWest
        ) return
        loading = true
        scope.launch {
            WaterFinder.fishableWater(
                north = box.latNorth, south = box.latSouth,
                east = box.lonEast, west = box.lonWest
            ).onSuccess { features ->
                lastScan = box
                drawWater(features)
                if (announce) snackbar.showSnackbar(
                    if (features.isEmpty()) "No mapped water in view — try zooming out a bit."
                    else "Highlighted ${features.size} water feature(s)."
                )
            }.onFailure {
                if (announce) snackbar.showSnackbar("Couldn't load water: ${it.message}")
            }
            loading = false
        }
    }

    fun centerOnMe() {
        locationOverlay.enableMyLocation()
        // Center instantly on the last known fix...
        LocationProvider.lastKnown(context)?.let { (lat, lng) ->
            mapView.post {
                mapView.controller.setZoom(14.0)
                mapView.controller.animateTo(GeoPoint(lat, lng))
            }
            // ...and scan once we've settled there, rather than waiting for the
            // pan-debounce (the wide first-layout view no longer auto-scans).
            mapView.postDelayed({ findWaterHere(announce = false) }, 800)
        }
        // ...then refine once a live GPS fix arrives.
        locationOverlay.runOnFirstFix {
            locationOverlay.myLocation?.let { loc ->
                mapView.post {
                    mapView.controller.animateTo(loc)
                    mapView.controller.setZoom(14.0)
                }
            }
        }
    }

    val locationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) centerOnMe() }

    // Set up overlays, auto-center, auto-scan on entering the screen.
    LaunchedEffect(Unit) {
        mapView.overlays.add(locationOverlay)
        mapView.overlays.add(tapToClose)  // topmost: closes bubbles on empty-map taps
        val hasLocation = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (hasLocation) centerOnMe() else locationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)

        // Scan automatically as soon as the map has a real viewport, and again
        // (debounced) whenever the user pans or zooms to a new area.
        mapView.addOnFirstLayoutListener(
            object : MapView.OnFirstLayoutListener {
                override fun onFirstLayout(v: android.view.View, left: Int, top: Int, right: Int, bottom: Int) {
                    findWaterHere(announce = false)
                }
            }
        )
    }

    // Re-scan after the map settles following a pan/zoom.
    DisposableEffect(Unit) {
        val listener = DelayedMapListener(object : MapListener {
            override fun onScroll(event: ScrollEvent?): Boolean { findWaterHere(announce = false); return false }
            override fun onZoom(event: ZoomEvent?): Boolean { findWaterHere(announce = false); return false }
        }, 2500L)
        mapView.addMapListener(listener)
        onDispose { mapView.removeMapListener(listener) }
    }

    // Plot saved catches as pins; redraw when the list changes.
    LaunchedEffect(catches) {
        mapView.overlays.removeAll { it is Marker }
        catches.forEach { c -> markerFor(mapView, c, onOpenCatch)?.let { mapView.overlays.add(it) } }
        mapView.invalidate()
    }

    // osmdroid lifecycle.
    DisposableEffect(Unit) {
        mapView.onResume()
        onDispose {
            mapView.onPause()
            mapView.onDetach()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) { Snackbar(it) } },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End) {
                SmallFloatingActionButton(onClick = {
                    val granted = ContextCompat.checkSelfPermission(
                        context, Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
                    if (granted) centerOnMe()
                    else locationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                }) {
                    Icon(Icons.Default.MyLocation, contentDescription = "Center on me")
                }
                Spacer(Modifier.height(12.dp))
                ExtendedFloatingActionButton(
                    onClick = { findWaterHere(announce = true) },
                    icon = { Icon(Icons.Default.Water, contentDescription = null) },
                    text = { Text(if (loading) "Scanning…" else "Find water here") }
                )
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
        }
    }

}

/** A Google-Maps-style blue location dot with a white ring. */
private fun blueLocationDot(): android.graphics.Bitmap {
    val size = 56
    val bmp = android.graphics.Bitmap.createBitmap(
        size, size, android.graphics.Bitmap.Config.ARGB_8888
    )
    val canvas = android.graphics.Canvas(bmp)
    val c = size / 2f
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    // soft halo
    paint.color = 0x331976D2
    canvas.drawCircle(c, c, c, paint)
    // white ring
    paint.color = android.graphics.Color.WHITE
    canvas.drawCircle(c, c, size * 0.30f, paint)
    // blue center
    paint.color = 0xFF1976D2.toInt()
    canvas.drawCircle(c, c, size * 0.22f, paint)
    return bmp
}

/**
 * Paint the map view and the tile-loading placeholders to match the theme, so the map never
 * flashes white before tiles paint. CARTO dark-matter's own background is near-black (#0E1013).
 */
private fun MapView.applyMapBackground(dark: Boolean) {
    val bg = if (dark) 0xFF0E1013.toInt() else 0xFFF2F2F2.toInt()
    setBackgroundColor(bg)
    overlayManager.tilesOverlay.setLoadingBackgroundColor(bg)
    overlayManager.tilesOverlay.setLoadingLineColor(bg)
}

/** Free, no-key dark basemap from CARTO ("dark matter"). */
private val CartoDarkTiles = XYTileSource(
    "CartoDarkMatter", 0, 20, 256, ".png",
    arrayOf(
        "https://a.basemaps.cartocdn.com/dark_all/",
        "https://b.basemaps.cartocdn.com/dark_all/",
        "https://c.basemaps.cartocdn.com/dark_all/",
        "https://d.basemaps.cartocdn.com/dark_all/"
    ),
    "© OpenStreetMap contributors © CARTO"
)

private fun markerFor(mapView: MapView, catch: Catch, onOpenCatch: (Long) -> Unit): Marker? {
    val lat = catch.lat ?: return null
    val lng = catch.lng ?: return null
    return Marker(mapView).apply {
        position = GeoPoint(lat, lng)
        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        title = catch.species
        subDescription = formatDate(catch.caughtAt)
        // Tapping the flag opens the catch instead of the default info bubble.
        setOnMarkerClickListener { _, _ -> onOpenCatch(catch.id); true }
    }
}
