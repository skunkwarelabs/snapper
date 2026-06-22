package com.skunk.snapper.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoLibrary
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
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.skunk.snapper.data.Spot
import com.skunk.snapper.util.LocationProvider
import kotlinx.coroutines.launch
import java.io.File

/**
 * Saved fishing spots. Tap a spot to see it on the map; the ＋ button saves your current
 * location as a new spot (name + optional photo). Spots also appear as ⭐ pins on the Map.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(vm: CatchViewModel, onOpenSpot: (Spot) -> Unit) {
    val spots by vm.spots.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    // Editor state: adding (coords, no spot) or editing (an existing spot). Null = closed.
    var addAt by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    var editing by remember { mutableStateOf<Spot?>(null) }

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
                        onEdit = { editing = spot },
                        onDelete = { vm.deleteSpot(spot) }
                    )
                }
            }
        }
    }

    // Save a new spot at the current location.
    addAt?.let { (lat, lng) ->
        SpotEditorDialog(
            title = "Save this spot",
            initialName = "",
            existingPhotoPath = null,
            onConfirm = { name, photoUri -> vm.saveSpot(lat, lng, name, photoUri); addAt = null },
            onDismiss = { addAt = null }
        )
    }
    // Edit an existing spot (name + photo).
    editing?.let { spot ->
        SpotEditorDialog(
            title = "Edit spot",
            initialName = spot.name,
            existingPhotoPath = spot.photoPath,
            onConfirm = { name, photoUri -> vm.updateSpot(spot, name, photoUri); editing = null },
            onDismiss = { editing = null }
        )
    }
}

@Composable
private fun SpotCard(
    spot: Spot,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Photo if the angler added one, otherwise a star tile.
            if (spot.photoPath != null) {
                AsyncImage(
                    model = File(spot.photoPath),
                    contentDescription = spot.displayName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(56.dp).clip(RoundedCornerShape(12.dp))
                )
            } else {
                Box(
                    Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Star,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
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
                    text = { Text("Edit") },
                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                    onClick = { menu = false; onEdit() }
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

/** Add/edit a spot: a photo (gallery) and an optional name. */
@Composable
private fun SpotEditorDialog(
    title: String,
    initialName: String,
    existingPhotoPath: String?,
    onConfirm: (name: String, photoUri: Uri?) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var photoUri by remember { mutableStateOf<Uri?>(null) }
    val pickPhoto = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) photoUri = uri }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                // Photo preview: newly picked, else the existing one, else a placeholder tile.
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            pickPhoto.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    val model: Any? = photoUri ?: existingPhotoPath?.let { File(it) }
                    if (model != null) {
                        AsyncImage(
                            model = model,
                            contentDescription = "Spot photo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                            Text("Add a photo", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        pickPhoto.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (model_has(photoUri, existingPhotoPath)) "Change photo" else "Add photo")
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text("Name (optional)") },
                    placeholder = { Text("e.g. The honey hole") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(name, photoUri) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private fun model_has(photoUri: Uri?, existingPhotoPath: String?) =
    photoUri != null || existingPhotoPath != null

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
