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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest

/** Detail page for a fish from the "What's around" list — bigger photo + field-guide info. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FishDetailScreen(vm: CatchViewModel, onBack: () -> Unit) {
    val fish by vm.selectedFish.collectAsState()
    val info by vm.fishInfo.collectAsState()
    val context = LocalContext.current
    val imageLoader = remember { ImageLoader.Builder(context).networkObserverEnabled(false).build() }

    var showFullImage by remember { mutableStateOf(false) }

    val current = fish
    if (current == null) {
        onBack()
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(current.name) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
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
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (current.imageUrl != null) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(current.imageUrl)
                        .setHeader("User-Agent", "Snapper/1.0 (Android fishing app)")
                        .crossfade(true)
                        .build(),
                    imageLoader = imageLoader,
                    contentDescription = current.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(4f / 3f)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { showFullImage = true }
                )
            }

            if (current.scientificName.isNotBlank()) {
                Text(
                    current.scientificName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            val size = listOf(current.lengthRange, current.weightRange).filter { it.isNotBlank() }.joinToString(" · ")
            if (size.isNotBlank()) {
                Text(size, style = MaterialTheme.typography.titleMedium)
            }

            HarvestBadge(current.inSeason)

            Spacer(Modifier.height(8.dp))

            when {
                info.loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.padding(end = 12.dp))
                    Text("Loading field guide…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                info.message != null -> Text(info.message!!, color = MaterialTheme.colorScheme.error)
                else -> {
                    // Quick-reference regs, big, above the description.
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        RegStat("Bag limit", info.bagLimit.ifBlank { "—" }, Modifier.weight(1f))
                        RegStat("Min size", info.minSize.ifBlank { "None" }, Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(8.dp))
                    InfoSections(info.body)
                }
            }
        }
    }

    if (showFullImage && current.imageUrl != null) {
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
                model = ImageRequest.Builder(context)
                    .data(current.imageUrl)
                    .setHeader("User-Agent", "Snapper/1.0 (Android fishing app)")
                    .crossfade(true)
                    .build(),
                imageLoader = imageLoader,
                contentDescription = current.name,
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

@Composable
private fun RegStat(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                label.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(Modifier.height(4.dp))
            Text(
                value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

/** Renders the Gemini "Label: text" lines with the labels bolded. */
@Composable
private fun InfoSections(text: String) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        text.lines().map { it.trim() }.filter { it.isNotBlank() }.forEach { line ->
            val label = line.substringBefore(":", "")
            val body = line.substringAfter(":", line)
            if (label.isNotBlank() && line.contains(":")) {
                Column {
                    Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(body.trim(), style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                Text(line, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
