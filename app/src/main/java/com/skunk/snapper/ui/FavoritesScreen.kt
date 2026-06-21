package com.skunk.snapper.ui

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.skunk.snapper.data.Spot
import com.skunk.snapper.util.LocationProvider
import kotlinx.coroutines.launch

/**
 * Saved fishing spots. Tap a spot to see it on the map; the ＋ button saves your current
 * location as a new spot (optionally named). Spots also appear as ⭐ pins on the Map, and
 * can be dropped there by long-pressing the map.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(vm: CatchViewModel, onOpenSpot: (Spot) -> Unit) {
    val spots by vm.spots.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    // Non-null when the add/rename dialog is open. A new spot carries its coords; a rename
    // carries the existing spot.
    var addAt by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var renaming by remember { mutableStateOf<Spot?>(null) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) { Snackbar(it) } },
        topBar = { TopAppBar(title = { Text("Favorites") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                val fix = LocationProvider.lastKnown(context)
                if (fix == null) {
                    scope.launch { snackbar.showSnackbar("Location unknown — enable location to save your spot.") }
                } else {
                    addAt = fix
                }
            }) {
                Icon(Icons.Default.Add, contentDescription = "Save current location")
            }
        }
    ) { padding ->
        if (spots.isEmpty()) {
            EmptyFavorites(Modifier.padding(padding))
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 88.dp,
                    start = 16.dp, end = 16.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(spots, key = { it.id }) { spot ->
                    SpotCard(
                        spot = spot,
                        onClick = { onOpenSpot(spot) },
                        onRename = { renaming = spot },
                        onDelete = { vm.deleteSpot(spot) }
                    )
                }
            }
        }
    }

    // Save-new dialog: optional name for the just-dropped/current-location spot.
    addAt?.let { (lat, lng) ->
        SpotNameDialog(
            title = "Save this spot",
            initial = "",
            onConfirm = { name -> vm.saveSpot(lat, lng, name); addAt = null },
            onDismiss = { addAt = null }
        )
    }
    // Rename dialog.
    renaming?.let { spot ->
        SpotNameDialog(
            title = "Rename spot",
            initial = spot.name,
            onConfirm = { name -> vm.renameSpot(spot, name); renaming = null },
            onDismiss = { renaming = null }
        )
    }
}

@Composable
private fun SpotCard(
    spot: Spot,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Star,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(spot.displayName, style = MaterialTheme.typography.titleMedium)
                Text(
                    "%.4f, %.4f".format(spot.lat, spot.lng),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            var menu by remember { mutableStateOf(false) }
            IconButton(onClick = { menu = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = "Spot options")
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text("Rename") },
                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                    onClick = { menu = false; onRename() }
                )
                DropdownMenuItem(
                    text = { Text("Delete") },
                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                    onClick = { menu = false; onDelete() }
                )
            }
        }
    }
}

@Composable
private fun SpotNameDialog(
    title: String,
    initial: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text("Name (optional)") },
                placeholder = { Text("e.g. The honey hole") }
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(name) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun EmptyFavorites(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.Star,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(12.dp))
            Text("No saved spots yet", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "Tap ＋ to save where you are, or long-press the map to drop a spot.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
        }
    }
}
