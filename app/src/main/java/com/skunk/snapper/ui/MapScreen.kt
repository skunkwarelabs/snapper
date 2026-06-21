package com.skunk.snapper.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LocationCity
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.SatelliteAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Water
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.skunk.snapper.ai.AreaFish
import com.skunk.snapper.data.Catch
import com.skunk.snapper.data.Spot
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
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.MapTileIndex
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
fun MapScreen(vm: CatchViewModel, onOpenCatch: (Long) -> Unit, onOpenFish: (AreaFish) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val catches by vm.catches.collectAsState()
    val spots by vm.spots.collectAsState()
    val focusSpot by vm.focusSpot.collectAsState()
    // A point long-pressed on the map, pending a "save this spot?" name dialog.
    var pendingDrop by remember { mutableStateOf<GeoPoint?>(null) }
    // Map style: dark street basemap (default) or satellite imagery.
    var mapMode by rememberSaveable { mutableStateOf(MapMode.DARK) }

    // When set, show a bottom sheet of stocked species: (waterName, species, stateCode/name).
    val stocked = remember { mutableStateOf<Triple<String, List<String>, String?>?>(null) }
    // The state the map is currently centred over (reverse-geocoded), to scope species lookups.
    val currentState = remember { mutableStateOf<String?>(null) }
    val cardImageLoader = remember {
        coil.ImageLoader.Builder(context).networkObserverEnabled(false).build()
    }

    // Google-Maps-style search: type a lake/river/place, pick a result, fly the map there.
    var search by remember { mutableStateOf("") }
    var places by remember { mutableStateOf<List<Place>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var suppressSearch by remember { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(search) {
        if (suppressSearch) { suppressSearch = false; return@LaunchedEffect }
        val q = search.trim()
        if (q.length < 3) { places = emptyList(); searching = false; return@LaunchedEffect }
        kotlinx.coroutines.delay(350)  // debounce keystrokes
        searching = true
        places = geocodePlaces(context, q)
        searching = false
    }

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
            // Default style is the dark basemap; the mode effect below keeps it in sync.
            setTileSource(CartoDarkTiles)
            setMultiTouchControls(true)
            // Pinch-to-zoom is enough; hide the redundant +/- buttons.
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            controller.setZoom(5.0)
            controller.setCenter(GeoPoint(39.5, -98.35))
            applyMapBackground(dark = true)
        }
    }

    // Shared dark bubble, attached only to water that actually has a name. Just labels the
    // water; tap it to dismiss. (Regulations live on their own tab.)
    val waterInfoWindow = remember {
        WaterInfoWindow(
            mapView,
            stateProvider = { currentState.value },
            onOpenStocked = { name, species, state -> stocked.value = Triple(name, species, state) }
        )
    }

    val spotInfoWindow = remember { SpotInfoWindow(mapView) }

    // Tapping anywhere on the map (not on a feature) dismisses any open bubble; a long-press
    // on empty map offers to save that point as a spot.
    val tapToClose = remember {
        MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                InfoWindow.closeAllInfoWindowsOn(mapView)
                return false  // don't consume — let feature taps still open their bubble
            }
            override fun longPressHelper(p: GeoPoint?): Boolean {
                p?.let { pendingDrop = it }
                return true
            }
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

    // Apply the chosen map style: dark CARTO basemap or Esri satellite imagery.
    LaunchedEffect(mapMode) {
        when (mapMode) {
            MapMode.DARK -> {
                mapView.setTileSource(CartoDarkTiles)
                mapView.applyMapBackground(dark = true, lift = true)
            }
            MapMode.SATELLITE -> {
                mapView.setTileSource(EsriSatelliteTiles)
                // Don't lift shadows on photographic imagery — show it as-is.
                mapView.applyMapBackground(dark = true, lift = false)
            }
        }
        mapView.invalidate()
    }

    // Always render water in a bright blue that pops on the dark basemap
    // (independent of the app's theme color).
    val waterFill = androidx.compose.ui.graphics.Color(0x6629B6F6).toArgb()
    val waterStroke = androidx.compose.ui.graphics.Color(0xFF4FC3F7).toArgb()

    fun drawWater(features: List<WaterFeature>) {
        mapView.overlays.removeAll { it is Polygon || it is Polyline || it is WaterLabel }
        // Insert water at the bottom of the overlay stack (index 0) so catch
        // markers and the location dot always render on top of it.
        features.forEach { f ->
            if (f.isArea) {
                mapView.overlays.add(0, Polygon(mapView).apply {
                    points = f.points
                    fillPaint.color = waterFill
                    outlinePaint.color = waterStroke
                    outlinePaint.strokeWidth = 4f
                    // Named water shows the dark bubble; unnamed shows nothing.
                    infoWindow = if (f.name != null) waterInfoWindow else null
                    f.name?.let { title = it }
                })
            } else {
                mapView.overlays.add(0, Polyline(mapView).apply {
                    setPoints(f.points)
                    outlinePaint.color = waterStroke
                    outlinePaint.strokeWidth = 9f
                    infoWindow = if (f.name != null) waterInfoWindow else null
                    f.name?.let { title = it }
                })
            }
        }
        // Persistent name labels for the bodies of water, one per name, so anglers
        // can read what's what without tapping each shape. Rendered above the fill.
        addWaterLabels(mapView, features)
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
            // Note which state this viewport is over, so stocked-species lookups scope correctly.
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching {
                    @Suppress("DEPRECATION")
                    android.location.Geocoder(context, java.util.Locale.US)
                        .getFromLocation((box.latNorth + box.latSouth) / 2, (box.lonEast + box.lonWest) / 2, 1)
                        ?.firstOrNull()?.adminArea
                }.getOrNull()?.let { currentState.value = it }
            }
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

    // Fly to a search result, then scan its water.
    fun goToPlace(place: Place) {
        suppressSearch = true   // don't re-search when we echo the name into the box
        search = place.label
        places = emptyList()
        keyboard?.hide()
        place.state?.let { currentState.value = it }  // scope stocked-species lookups right away
        mapView.controller.setZoom(13.0)
        mapView.controller.animateTo(GeoPoint(place.lat, place.lng))
        mapView.postDelayed({ findWaterHere(announce = false) }, 700)
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
        // Remove only catch pins — leave water name labels and spot stars (also Markers) in place.
        mapView.overlays.removeAll { it is Marker && it !is WaterLabel && it !is SpotMarker }
        catches.forEach { c -> markerFor(mapView, c, onOpenCatch)?.let { mapView.overlays.add(it) } }
        mapView.invalidate()
    }

    // Plot saved spots as ⭐ stars; redraw when the list changes. Tapping one opens its
    // name bubble.
    LaunchedEffect(spots) {
        mapView.overlays.removeAll { it is SpotMarker }
        spots.forEach { s -> mapView.overlays.add(spotMarkerFor(mapView, s, spotInfoWindow)) }
        mapView.invalidate()
    }

    // When the Favorites tab asks to show a spot, fly there and pop its bubble.
    LaunchedEffect(focusSpot) {
        focusSpot?.let { s ->
            mapView.controller.setZoom(15.0)
            mapView.controller.animateTo(GeoPoint(s.lat, s.lng))
            mapView.postDelayed({
                mapView.overlays.filterIsInstance<SpotMarker>()
                    .firstOrNull { it.spotId == s.id }
                    ?.let { it.showInfoWindow() }
            }, 700)
            vm.consumeFocusSpot()
        }
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
                // Map-style switcher (dark basemap ↔ satellite), Google-Maps style.
                Box {
                    var layersMenu by remember { mutableStateOf(false) }
                    SmallFloatingActionButton(onClick = { layersMenu = true }) {
                        Icon(Icons.Default.Layers, contentDescription = "Map style")
                    }
                    DropdownMenu(expanded = layersMenu, onDismissRequest = { layersMenu = false }) {
                        MapMode.entries.forEach { m ->
                            DropdownMenuItem(
                                text = { Text(m.label) },
                                leadingIcon = { Icon(m.icon, contentDescription = null) },
                                trailingIcon = {
                                    if (m == mapMode) Icon(Icons.Default.Check, contentDescription = null)
                                },
                                onClick = { mapMode = m; layersMenu = false }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
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

            // Floating search bar (+ live results) pinned to the top of the map.
            Surface(
                shape = RoundedCornerShape(28.dp),
                shadowElevation = 6.dp,
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Spacer(Modifier.width(14.dp))
                        Icon(
                            Icons.Default.Search, contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextField(
                            value = search,
                            onValueChange = { search = it },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            placeholder = { Text("Search a lake, river, or place") },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() })
                        )
                        if (searching) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        if (search.isNotEmpty()) {
                            IconButton(onClick = { search = ""; places = emptyList() }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear")
                            }
                        }
                    }
                    places.forEach { place ->
                        val muted = MaterialTheme.colorScheme.onSurfaceVariant
                        val waterTint = Color(0xFF4FC3F7)
                        val (icon, tint) = when (place.kind) {
                            PlaceKind.LAKE -> Icons.Default.Water to waterTint
                            PlaceKind.RIVER -> Icons.Default.Waves to waterTint
                            PlaceKind.STATE -> Icons.Default.Flag to muted
                            PlaceKind.COUNTY -> Icons.Default.Map to muted
                            PlaceKind.CITY -> Icons.Default.LocationCity to muted
                            PlaceKind.OTHER -> Icons.Default.Place to muted
                        }
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { goToPlace(place) }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(icon, contentDescription = null, tint = tint)
                            Spacer(Modifier.width(12.dp))
                            Text(place.label, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }

    // Stocked-species sheet: opened from the water bubble's "Stocked here ›" link.
    stocked.value?.let { (waterName, species, state) ->
        var cards by remember(waterName) { mutableStateOf<List<AreaFish>>(emptyList()) }
        LaunchedEffect(waterName) { cards = vm.stockedFishCards(species, state) }
        ModalBottomSheet(onDismissRequest = { stocked.value = null }) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp)
            ) {
                Text(waterName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    "Stocked species · state stocking records",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                cards.forEach { fish ->
                    AreaFishCard(fish, cardImageLoader, onClick = {
                        stocked.value = null
                        onOpenFish(fish)
                    })
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
    }

    // Long-pressed a point on the map → offer to save it as a spot (optional name).
    pendingDrop?.let { point ->
        var name by remember(point) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { pendingDrop = null },
            title = { Text("Save this spot") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text("Name (optional)") },
                    placeholder = { Text("e.g. The honey hole") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.saveSpot(point.latitude, point.longitude, name)
                    pendingDrop = null
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { pendingDrop = null }) { Text("Cancel") } }
        )
    }

}

/** What a search hit represents, so we can show a fitting icon. */
private enum class PlaceKind { LAKE, RIVER, STATE, COUNTY, CITY, OTHER }

/** A search hit: a labelled point to fly the map to. */
private data class Place(
    val label: String, val lat: Double, val lng: Double,
    val state: String?, val kind: PlaceKind
)

/** Forward-geocode a free-text query (lake / river / place) into a few candidate points. */
private suspend fun geocodePlaces(context: Context, query: String): List<Place> =
    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        runCatching {
            @Suppress("DEPRECATION")
            android.location.Geocoder(context, java.util.Locale.US)
                .getFromLocationName(query, 6)
                ?.mapNotNull { a ->
                    if (!a.hasLatitude() || !a.hasLongitude()) return@mapNotNull null
                    val name = a.featureName?.takeIf { it.isNotBlank() && !it.all(Char::isDigit) }
                    val label = listOfNotNull(name, a.locality ?: a.subAdminArea, a.adminArea)
                        .distinct().joinToString(", ").ifBlank { query }
                    Place(label, a.latitude, a.longitude, a.adminArea, classify(a, name, label))
                } ?: emptyList()
        }.getOrDefault(emptyList())
    }

/** Infer a result's kind from its name keywords and which admin fields are populated. */
private fun classify(a: android.location.Address, name: String?, label: String): PlaceKind {
    val text = "${name ?: ""} $label".lowercase(java.util.Locale.US)
    return when {
        Regex("\\b(lake|reservoir|pond|lagoon|slough|impoundment|flowage)\\b").containsMatchIn(text) -> PlaceKind.LAKE
        Regex("\\b(river|creek|stream|branch|run|fork|brook|bayou|canal|ditch)\\b").containsMatchIn(text) -> PlaceKind.RIVER
        a.locality.isNullOrBlank() && a.subAdminArea.isNullOrBlank() && !a.adminArea.isNullOrBlank() &&
            (name == null || name.equals(a.adminArea, true)) -> PlaceKind.STATE
        text.contains("county") || text.contains("parish") ||
            (!a.subAdminArea.isNullOrBlank() && a.locality.isNullOrBlank()) -> PlaceKind.COUNTY
        !a.locality.isNullOrBlank() -> PlaceKind.CITY
        else -> PlaceKind.OTHER
    }
}

/** A non-interactive name label for a body of water (subclass so catch-pin
 *  redraws can tell it apart from real catch markers). */
private class WaterLabel(map: MapView) : Marker(map)

/**
 * Drop one readable name label per body of water at its centre, so anglers can
 * read the map without tapping each shape. De-duped by name (a lake split into
 * several rings gets a single label) and capped to keep dense areas legible.
 */
private fun addWaterLabels(mapView: MapView, features: List<WaterFeature>) {
    val seen = HashSet<String>()
    var added = 0
    for (f in features) {
        val name = f.name ?: continue
        if (f.points.isEmpty()) continue
        if (!seen.add(name)) continue
        if (added >= 60) break
        added++
        val center = GeoPoint(
            f.points.sumOf { it.latitude } / f.points.size,
            f.points.sumOf { it.longitude } / f.points.size
        )
        mapView.overlays.add(WaterLabel(mapView).apply {
            position = center
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            icon = android.graphics.drawable.BitmapDrawable(mapView.resources, waterLabelBitmap(name))
            setInfoWindow(null)
            // Don't steal taps — let the underlying water shape open its bubble.
            setOnMarkerClickListener { _, _ -> false }
        })
    }
}

/** Render a water name as a bright label with a dark halo so it reads on any basemap. */
private fun waterLabelBitmap(text: String): android.graphics.Bitmap {
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 34f
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
    }
    val pad = 8f
    val fm = paint.fontMetrics
    val width = (paint.measureText(text) + pad * 2).toInt().coerceAtLeast(1)
    val height = (fm.descent - fm.ascent + pad * 2).toInt().coerceAtLeast(1)
    val bmp = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bmp)
    val baseline = -fm.ascent + pad
    // Dark outline first for contrast against bright water and pale basemaps.
    paint.style = android.graphics.Paint.Style.STROKE
    paint.strokeWidth = 6f
    paint.color = 0xDD06121A.toInt()
    canvas.drawText(text, pad, baseline, paint)
    // Bright fill on top.
    paint.style = android.graphics.Paint.Style.FILL
    paint.color = 0xFFE3F5FF.toInt()
    canvas.drawText(text, pad, baseline, paint)
    return bmp
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
private fun MapView.applyMapBackground(dark: Boolean, lift: Boolean = dark) {
    val bg = if (dark) 0xFF0E1013.toInt() else 0xFFF2F2F2.toInt()
    setBackgroundColor(bg)
    overlayManager.tilesOverlay.setLoadingBackgroundColor(bg)
    overlayManager.tilesOverlay.setLoadingLineColor(bg)
    // Lift the near-black CARTO dark basemap so the *unscanned* water and land read more
    // clearly (it's otherwise an almost-flat black). Off for satellite/light basemaps.
    overlayManager.tilesOverlay.setColorFilter(if (lift) DarkTileLift else null)
}

/**
 * A gentle "lift the shadows" filter for the dark basemap: pushes the darkest tones (water,
 * which CARTO renders near-black) up toward a cool slate so they're visible, with a slight
 * blue bias so water still reads as water. Already-light pixels barely change.
 */
private val DarkTileLift: android.graphics.ColorFilter = android.graphics.ColorMatrixColorFilter(
    android.graphics.ColorMatrix(
        floatArrayOf(
            1f, 0f, 0f, 0f, 20f,
            0f, 1f, 0f, 0f, 24f,
            0f, 0f, 1f, 0f, 34f,
            0f, 0f, 0f, 1f, 0f
        )
    )
)

/** Selectable basemap style, Google-Maps-style (street/dark vs satellite). */
private enum class MapMode(val label: String, val icon: ImageVector) {
    DARK("Dark", Icons.Default.DarkMode),
    SATELLITE("Satellite", Icons.Default.SatelliteAlt)
}

/**
 * Free, no-key satellite imagery from Esri's World Imagery service. Esri serves tiles
 * as z/y/x (not osmdroid's default z/x/y), so the URL builder is overridden.
 */
private val EsriSatelliteTiles = object : OnlineTileSourceBase(
    "EsriWorldImagery", 0, 19, 256, "",
    arrayOf("https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/"),
    "© Esri, Maxar, Earthstar Geographics"
) {
    override fun getTileURLString(pMapTileIndex: Long): String =
        baseUrl +
            MapTileIndex.getZoom(pMapTileIndex) + "/" +
            MapTileIndex.getY(pMapTileIndex) + "/" +
            MapTileIndex.getX(pMapTileIndex)
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

/** A saved-spot star marker (subclass so catch/water redraws can tell it apart). */
private class SpotMarker(map: MapView, val spotId: Long) : Marker(map)

private fun spotMarkerFor(mapView: MapView, spot: Spot, window: SpotInfoWindow): SpotMarker =
    SpotMarker(mapView, spot.id).apply {
        position = GeoPoint(spot.lat, spot.lng)
        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
        icon = android.graphics.drawable.BitmapDrawable(mapView.resources, starPin)
        title = spot.displayName
        infoWindow = window
        // Open this spot's name bubble on tap (and consume the tap).
        setOnMarkerClickListener { m, _ -> m.showInfoWindow(); true }
    }

/** A gold five-point star with a dark outline, used to plot saved spots. Drawn once. */
private val starPin: android.graphics.Bitmap by lazy {
    val s = 72
    val bmp = android.graphics.Bitmap.createBitmap(s, s, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bmp)
    val cx = s / 2f; val cy = s / 2f
    val outer = s * 0.46f; val inner = outer * 0.42f
    val path = android.graphics.Path()
    for (i in 0 until 10) {
        val r = if (i % 2 == 0) outer else inner
        // Start at the top point (−90°) and step every 36°.
        val a = Math.toRadians((i * 36 - 90).toDouble())
        val x = cx + r * Math.cos(a).toFloat()
        val y = cy + r * Math.sin(a).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    paint.style = android.graphics.Paint.Style.FILL
    paint.color = 0xFFFFC107.toInt()  // amber/gold
    canvas.drawPath(path, paint)
    paint.style = android.graphics.Paint.Style.STROKE
    paint.strokeWidth = s * 0.06f
    paint.color = 0xDD06121A.toInt()  // dark outline so it reads on bright water
    canvas.drawPath(path, paint)
    bmp
}

private fun markerFor(mapView: MapView, catch: Catch, onOpenCatch: (Long) -> Unit): Marker? {
    val lat = catch.lat ?: return null
    val lng = catch.lng ?: return null
    return Marker(mapView).apply {
        position = GeoPoint(lat, lng)
        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        icon = android.graphics.drawable.BitmapDrawable(mapView.resources, fishPin)
        title = catch.species
        subDescription = formatDate(catch.caughtAt)
        // Tapping the flag opens the catch instead of the default info bubble.
        setOnMarkerClickListener { _, _ -> onOpenCatch(catch.id); true }
    }
}

/** Green map-pin with a white fish silhouette, used to plot saved catches. Drawn once. */
private val fishPin: android.graphics.Bitmap by lazy {
    val w = 88; val h = 112
    val bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bmp)
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    val cx = w / 2f
    val r = w * 0.42f
    val cy = r + 4f
    val green = 0xFF2E9E4F.toInt()

    // Pin body: one smooth solid-green teardrop (tip up each side, arc over the top) —
    // no circle+triangle seams that would leave transparent notches showing the map.
    paint.color = green
    canvas.drawPath(
        android.graphics.Path().apply {
            moveTo(cx, h - 1f)                                       // tip (anchor point)
            quadTo(cx - r * 1.05f, cy + r * 0.55f, cx - r, cy)       // up the left side
            arcTo(android.graphics.RectF(cx - r, cy - r, cx + r, cy + r), 180f, 180f, false)  // over the top
            quadTo(cx + r * 1.05f, cy + r * 0.55f, cx, h - 1f)       // down the right side
            close()
        },
        paint
    )

    // White fish silhouette inside the head, pointing right.
    paint.color = android.graphics.Color.WHITE
    val bodyW = r * 1.15f
    val bodyH = r * 0.72f
    val left = cx - bodyW * 0.40f
    val body = android.graphics.RectF(left, cy - bodyH / 2f, left + bodyW, cy + bodyH / 2f)
    canvas.drawOval(body, paint)
    canvas.drawPath(
        android.graphics.Path().apply {  // tail on the left
            moveTo(body.left + 2f, cy)
            lineTo(body.left - r * 0.34f, cy - r * 0.30f)
            lineTo(body.left - r * 0.34f, cy + r * 0.30f)
            close()
        },
        paint
    )
    // Eye — punched back to green so it stays "pin-green + fish-white".
    paint.color = green
    canvas.drawCircle(body.right - bodyW * 0.24f, cy - bodyH * 0.14f, r * 0.085f, paint)
    bmp
}
