package com.skunk.snapper.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatchDetailScreen(
    vm: CatchViewModel,
    catchId: Long,
    onBack: () -> Unit,
    onEdit: () -> Unit
) {
    val catches by vm.catches.collectAsState()
    val catch = catches.find { it.id == catchId }
    var confirmDelete by remember { mutableStateOf(false) }
    var showFullImage by remember { mutableStateOf(false) }

    // If the catch is gone (e.g. just deleted), bounce back.
    if (catch == null) {
        onBack()
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(catch.species) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { vm.startEditCatch(catch); onEdit() }) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit")
                    }
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AsyncImage(
                model = File(catch.photoPath),
                contentDescription = catch.species,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(4f / 3f)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { showFullImage = true }
            )

            Text(catch.species, style = MaterialTheme.typography.headlineSmall)
            Text(
                formatDate(catch.caughtAt),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (catch.confidence.isNotBlank()) {
                Text(
                    "Identified with ${catch.confidence} confidence",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            val weightText = listOf(
                catch.weightLb.takeIf { it.isNotBlank() }?.let { "$it lb" },
                catch.weightOz.takeIf { it.isNotBlank() }?.let { "$it oz" }
            ).filterNotNull().joinToString(" ")
            if (catch.lengthEstimate.isNotBlank() || weightText.isNotBlank()) {
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    if (catch.lengthEstimate.isNotBlank()) {
                        Column {
                            Text(
                                "Length",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text("${catch.lengthEstimate} in", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                    if (weightText.isNotBlank()) {
                        Column {
                            Text(
                                "Weight",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(weightText, style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }

            if (catch.details.isNotBlank()) {
                Text(catch.details, style = MaterialTheme.typography.bodyLarge)
            }

            if (catch.notes.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text("Notes", style = MaterialTheme.typography.titleMedium)
                Text(catch.notes, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this catch?") },
            text = { Text("This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.delete(catch)
                    onBack()
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            }
        )
    }

    if (showFullImage) {
        BackHandler { showFullImage = false }
        var scale by remember { mutableStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }
        val transform = rememberTransformableState { zoom, pan, _ ->
            scale = (scale * zoom).coerceIn(1f, 5f)
            offset = if (scale > 1f) offset + pan else Offset.Zero
        }
        // Full-screen overlay (sibling of the Scaffold) so the photo centers on the
        // whole screen — a Dialog window doesn't span under the system bars.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { showFullImage = false },
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = File(catch.photoPath),
                contentDescription = catch.species,
                contentScale = ContentScale.Fit,
                alignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .transformable(transform)
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y
                    )
            )
        }
    }
}
