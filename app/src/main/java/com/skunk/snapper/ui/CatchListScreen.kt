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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.Phishing
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.skunk.snapper.data.Catch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatchListScreen(
    vm: CatchViewModel,
    onAddCatch: () -> Unit,
    onOpenCatch: (Long) -> Unit,
    onOpenStats: () -> Unit
) {
    val catches by vm.catches.collectAsState()
    var sortField by remember { mutableStateOf(SortField.NEWEST) }
    // Direction for Length/Weight: true = high→low (longest/heaviest first).
    var descending by remember { mutableStateOf(true) }
    val sorted = remember(catches, sortField, descending) { sortCatches(catches, sortField, descending) }

    // Medals only when ranking biggest-first (longest or heaviest), never short/light/etc.
    val medals = remember(sorted, sortField, descending) {
        if (descending && (sortField == SortField.LENGTH || sortField == SortField.WEIGHT)) {
            val measured: (Catch) -> Boolean =
                if (sortField == SortField.LENGTH) { c -> lengthOf(c) != null }
                else { c -> ouncesOf(c) != null }
            sorted.filter(measured).take(3)
                .mapIndexed { i, c -> c.id to listOf("🥇", "🥈", "🥉")[i] }
                .toMap()
        } else emptyMap()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Catches") },
                actions = {
                    IconButton(onClick = onOpenStats) {
                        Icon(Icons.Default.Leaderboard, contentDescription = "Stats")
                    }
                    var menuOpen by remember { mutableStateOf(false) }
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.Sort, contentDescription = "Sort catches")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        SortField.entries.forEach { field ->
                            val active = field == sortField
                            val toggleable = field == SortField.LENGTH || field == SortField.WEIGHT
                            DropdownMenuItem(
                                text = { Text(field.label) },
                                onClick = {
                                    if (toggleable) {
                                        // Re-tapping the active field flips direction; keep
                                        // the menu open so the arrow visibly updates.
                                        if (active) descending = !descending
                                        else { sortField = field; descending = true }
                                    } else {
                                        sortField = field
                                        menuOpen = false
                                    }
                                },
                                trailingIcon = {
                                    when {
                                        toggleable -> Icon(
                                            if (active && !descending) Icons.Default.ArrowUpward
                                            else Icons.Default.ArrowDownward,
                                            contentDescription = if (descending) "high to low" else "low to high",
                                            tint = if (active) MaterialTheme.colorScheme.primary
                                                   else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        active -> Icon(Icons.Default.Check, contentDescription = null)
                                    }
                                }
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddCatch) {
                Icon(Icons.Default.Add, contentDescription = "Log a catch")
            }
        }
    ) { padding ->
        if (catches.isEmpty()) {
            EmptyState(Modifier.padding(padding))
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 88.dp,
                    start = 16.dp,
                    end = 16.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(sorted, key = { it.id }) { catch ->
                    CatchCard(
                        catch = catch,
                        medal = medals[catch.id],
                        onClick = { onOpenCatch(catch.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun CatchCard(catch: Catch, medal: String?, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = File(catch.photoPath),
                contentDescription = catch.species,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(12.dp))
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = catch.species,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = formatDate(catch.caughtAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (catch.confidence.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "${catch.confidence} confidence",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            if (medal != null) {
                Spacer(Modifier.width(8.dp))
                Text(medal, style = MaterialTheme.typography.headlineMedium)
            }
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.Phishing,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(12.dp))
            Text("No catches yet", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "Tap + to snap your first fish.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private enum class SortField(val label: String) {
    NEWEST("Newest"),
    SPECIES("Species (A–Z)"),
    LENGTH("Length"),
    WEIGHT("Weight")
}

private fun lengthOf(c: Catch): Double? = c.lengthEstimate.trim().toDoubleOrNull()

private fun ouncesOf(c: Catch): Int? {
    val lb = c.weightLb.trim().toIntOrNull()
    val oz = c.weightOz.trim().toIntOrNull()
    if (lb == null && oz == null) return null
    return (lb ?: 0) * 16 + (oz ?: 0)
}

// Catches without the relevant measurement always sort to the bottom (either direction).
private fun sortCatches(catches: List<Catch>, field: SortField, descending: Boolean): List<Catch> =
    when (field) {
        SortField.NEWEST -> catches.sortedByDescending { it.caughtAt }
        SortField.SPECIES -> catches.sortedBy { it.species.lowercase(Locale.getDefault()) }
        SortField.LENGTH ->
            if (descending) catches.sortedByDescending { lengthOf(it) ?: Double.NEGATIVE_INFINITY }
            else catches.sortedBy { lengthOf(it) ?: Double.POSITIVE_INFINITY }
        SortField.WEIGHT ->
            if (descending) catches.sortedByDescending { ouncesOf(it) ?: Int.MIN_VALUE }
            else catches.sortedBy { ouncesOf(it) ?: Int.MAX_VALUE }
    }

private val dateFormat = SimpleDateFormat("MMM d, yyyy · h:mm a", Locale.getDefault())
fun formatDate(millis: Long): String = dateFormat.format(Date(millis))
