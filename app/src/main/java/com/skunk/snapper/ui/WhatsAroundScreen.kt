package com.skunk.snapper.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.skunk.snapper.ai.AreaFish
import com.skunk.snapper.util.LocationProvider

/** Shows fish native/common to the angler's area: photo, size ranges, and season. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhatsAroundScreen(vm: CatchViewModel, onOpenFish: (AreaFish) -> Unit, onOpenCredits: () -> Unit) {
    val state by vm.whatsAround.collectAsState()
    val context = LocalContext.current

    // Coil's connectivity observer can wrongly report "offline" on some devices and
    // silently stall network image loads — disable it so it always hits the network.
    val imageLoader = remember {
        coil.ImageLoader.Builder(context).networkObserverEnabled(false).build()
    }
    var query by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()

    val askLocation = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) vm.loadWhatsAround(force = true) }

    LaunchedEffect(Unit) {
        if (LocationProvider.hasPermission(context)) vm.loadWhatsAround()
        else askLocation.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Regulations")
                        state.area?.let {
                            Text(it, style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            )
        }
    ) { padding ->
        when {
            state.fish.isNotEmpty() -> {
                val filtered = state.fish.filter {
                    it.name.contains(query, ignoreCase = true) ||
                        it.scientificName.contains(query, ignoreCase = true)
                }
                // Start scrolled past the search bar (item 0) so it's hidden until you
                // pull/scroll down to reveal it — iOS-style.
                LaunchedEffect(state.fish.isNotEmpty()) {
                    if (query.isBlank()) listState.scrollToItem(1)
                }
                // iOS-style "catch": when a scroll settles with the search bar (item 0)
                // only partially showing, snap it fully open or fully tucked away.
                LaunchedEffect(listState.isScrollInProgress) {
                    if (!listState.isScrollInProgress) {
                        val first = listState.layoutInfo.visibleItemsInfo.firstOrNull()
                        if (first != null && first.index == 0 && first.offset < 0 && first.size > 0) {
                            if (-first.offset > first.size / 2) listState.animateScrollToItem(1)
                            else listState.animateScrollToItem(0)
                        }
                    }
                }
                PullToRefreshBox(
                    isRefreshing = state.loading,
                    onRefresh = { vm.loadWhatsAround(force = true) },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            val cardColor = CardDefaults.cardColors().containerColor
                            OutlinedTextField(
                                value = query,
                                onValueChange = { query = it },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                placeholder = { Text("Search fish") },
                                singleLine = true,
                                shape = RoundedCornerShape(percent = 50),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = cardColor,
                                    unfocusedContainerColor = cardColor,
                                    focusedBorderColor = Color.Transparent,
                                    unfocusedBorderColor = Color.Transparent
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp)
                            )
                        }
                        items(filtered, key = { it.name }) { fish ->
                            AreaFishCard(fish, imageLoader, onClick = { onOpenFish(fish) })
                        }
                        if (filtered.isEmpty()) {
                            item {
                                Text(
                                    "No fish match \"$query\".",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                        item {
                            Text(
                                "Statewide general limits — many waters have their own exceptions. " +
                                    "Always confirm with your state DNR before keeping fish.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                        item {
                            Text(
                                "Photo credits",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .padding(top = 4.dp)
                                    .clickable { onOpenCredits() }
                            )
                        }
                    }
                }
            }

            state.loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(8.dp))
                    Text("Loading your state's regulations…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            else -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        state.message ?: "Nothing to show yet.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp)
                    )
                    Button(onClick = { vm.loadWhatsAround(force = true) }) { Text("Try again") }
                }
            }
        }
    }
}

/** Reusable regulation fish card (Regs tab list + map water-bubble sheet). */
@Composable
fun AreaFishCard(fish: AreaFish, imageLoader: coil.ImageLoader, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (fish.imageUrl != null) {
                // Wikimedia returns 403 without a descriptive User-Agent, so set one.
                val request = ImageRequest.Builder(LocalContext.current)
                    .data(fish.imageUrl)
                    .setHeader("User-Agent", "Snapper/1.0 (Android fishing app)")
                    .crossfade(true)
                    .build()
                AsyncImage(
                    model = request,
                    imageLoader = imageLoader,
                    contentDescription = fish.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(84.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
            } else {
                Box(
                    Modifier
                        .size(84.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) { Text("🐟", style = MaterialTheme.typography.headlineMedium) }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(fish.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (fish.scientificName.isNotBlank()) {
                    Text(
                        fish.scientificName,
                        style = MaterialTheme.typography.bodySmall,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                val size = listOf(fish.lengthRange, fish.weightRange).filter { it.isNotBlank() }.joinToString(" · ")
                if (size.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(size, style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(6.dp))
                HarvestBadge(fish.inSeason)
            }
        }
    }
}

/**
 * Keep-status badge derived from the regulations: green "In season" when the fish is harvestable,
 * grey "Catch & release" when the regs protect it (bag 0 / no-harvest / protected).
 */
@Composable
fun HarvestBadge(inSeason: Boolean) {
    val container = if (inSeason) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant
    val onContainer = if (inSeason) MaterialTheme.colorScheme.onPrimaryContainer
                      else MaterialTheme.colorScheme.onSurfaceVariant
    Text(
        text = if (inSeason) "● In season" else "● Catch & release",
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = onContainer,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(container)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    )
}
