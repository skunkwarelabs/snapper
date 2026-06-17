package com.skunk.snapper.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.skunk.snapper.util.LocationProvider
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView

/**
 * Lets the angler set where a catch happened by panning the map under a fixed
 * center pin. Confirming writes the map center back into the add-catch draft.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationPickerScreen(vm: CatchViewModel, onDone: () -> Unit) {
    val context = LocalContext.current
    val state by vm.add.collectAsState()
    val dark = isSystemInDarkTheme()

    LaunchedEffect(Unit) {
        Configuration.getInstance().apply {
            userAgentValue = context.packageName
            osmdroidBasePath = context.cacheDir
            osmdroidTileCache = context.cacheDir.resolve("osm-tiles")
        }
    }

    // Start centered on the draft's location, else the current fix, else the US.
    val mapView = remember {
        val draft = state.lat?.let { lat -> state.lng?.let { lng -> GeoPoint(lat, lng) } }
        val fix = draft ?: LocationProvider.lastKnown(context)?.let { GeoPoint(it.first, it.second) }
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            controller.setZoom(if (fix != null) 15.0 else 5.0)
            controller.setCenter(fix ?: GeoPoint(39.5, -98.35))
        }
    }

    LaunchedEffect(dark) {
        mapView.setTileSource(if (dark) PickerDarkTiles else TileSourceFactory.MAPNIK)
        mapView.invalidate()
    }

    DisposableEffect(Unit) {
        mapView.onResume()
        onDispose {
            mapView.onPause()
            mapView.onDetach()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Catch location") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    val center = mapView.mapCenter
                    vm.updateLocation(center.latitude, center.longitude)
                    onDone()
                },
                icon = { Icon(Icons.Default.Check, contentDescription = null) },
                text = { Text("Use this spot") }
            )
        }
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center
        ) {
            AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
            // Fixed pin marking the map center; nudged up so its tip sits on center.
            Icon(
                Icons.Default.Place,
                contentDescription = "Selected location",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(48.dp)
                    .offset(y = (-24).dp)
            )
        }
    }
}

/** Free, no-key dark basemap from CARTO ("dark matter"). */
private val PickerDarkTiles = XYTileSource(
    "CartoDarkMatter", 0, 20, 256, ".png",
    arrayOf(
        "https://a.basemaps.cartocdn.com/dark_all/",
        "https://b.basemaps.cartocdn.com/dark_all/",
        "https://c.basemaps.cartocdn.com/dark_all/",
        "https://d.basemaps.cartocdn.com/dark_all/"
    ),
    "© OpenStreetMap contributors © CARTO"
)
