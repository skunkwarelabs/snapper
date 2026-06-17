package com.skunk.snapper.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Phishing
import androidx.compose.material.icons.filled.Scale
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.skunk.snapper.data.Catch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    vm: CatchViewModel,
    onOpenCatches: () -> Unit,
    onOpenCatch: (Long) -> Unit
) {
    val catches by vm.catches.collectAsState()
    val stats = remember(catches) { computeStats(catches) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Stats") }) }
    ) { padding ->
        if (catches.isEmpty()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Log some catches and your stats will show up here.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Totals
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SummaryCard("Catches", stats.total.toString(), Modifier.weight(1f), onClick = onOpenCatches)
                    SummaryCard("Species", stats.speciesCount.toString(), Modifier.weight(1f))
                }
            }

            // High scores
            item {
                Text(
                    "High scores",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            item {
                if (stats.longest != null) {
                    RecordCard(
                        icon = Icons.Default.Straighten,
                        label = "Longest",
                        value = "${trimNum(stats.longest.second)} in",
                        subtitle = stats.longest.first.species,
                        onClick = { onOpenCatch(stats.longest.first.id) }
                    )
                } else {
                    HintCard("No lengths recorded yet.")
                }
            }
            item {
                if (stats.heaviest != null) {
                    RecordCard(
                        icon = Icons.Default.Scale,
                        label = "Heaviest",
                        value = formatOunces(stats.heaviest.second),
                        subtitle = stats.heaviest.first.species,
                        onClick = { onOpenCatch(stats.heaviest.first.id) }
                    )
                } else {
                    HintCard("No weights recorded yet.")
                }
            }

            // Species breakdown
            item {
                Text(
                    "By species",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            items(stats.bySpecies) { sc ->
                SpeciesRow(sc.species, sc.count, stats.bySpecies.first().count)
            }
        }
    }
}

@Composable
private fun SummaryCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val body = @Composable {
        Column(Modifier.padding(16.dp)) {
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    if (onClick != null) {
        Card(onClick = onClick, modifier = modifier) { body() }
    } else {
        Card(modifier = modifier) { body() }
    }
}

@Composable
private fun RecordCard(
    icon: ImageVector,
    label: String,
    value: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(subtitle, style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun HintCard(text: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(16.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SpeciesRow(species: String, count: Int, maxCount: Int) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Phishing,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(species, style = MaterialTheme.typography.bodyLarge)
            }
            Text("×$count", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(4.dp))
        // Proportional bar.
        Box(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                Modifier
                    .fillMaxWidth(if (maxCount > 0) count.toFloat() / maxCount else 0f)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
    }
}

// --- stats model + helpers ---

private data class SpeciesCount(val species: String, val count: Int)

private data class Stats(
    val total: Int,
    val speciesCount: Int,
    val bySpecies: List<SpeciesCount>,
    /** Catch + length in inches. */
    val longest: Pair<Catch, Double>?,
    /** Catch + total ounces. */
    val heaviest: Pair<Catch, Int>?
)

private fun Catch.lengthInches(): Double? = lengthEstimate.trim().toDoubleOrNull()

private fun Catch.totalOunces(): Int? {
    val lb = weightLb.trim().toIntOrNull()
    val oz = weightOz.trim().toIntOrNull()
    if (lb == null && oz == null) return null
    return (lb ?: 0) * 16 + (oz ?: 0)
}

private fun computeStats(catches: List<Catch>): Stats {
    val bySpecies = catches
        .groupingBy { it.species.ifBlank { "Unknown" } }
        .eachCount()
        .entries
        .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
        .map { SpeciesCount(it.key, it.value) }

    val longest = catches.mapNotNull { c -> c.lengthInches()?.let { c to it } }.maxByOrNull { it.second }
    val heaviest = catches.mapNotNull { c -> c.totalOunces()?.let { c to it } }.maxByOrNull { it.second }

    return Stats(catches.size, bySpecies.size, bySpecies, longest, heaviest)
}

private fun formatOunces(total: Int): String {
    val lb = total / 16
    val oz = total % 16
    return when {
        lb > 0 && oz > 0 -> "$lb lb $oz oz"
        lb > 0 -> "$lb lb"
        else -> "$oz oz"
    }
}

/** Drop a trailing ".0" so "13.0" shows as "13" but "13.5" stays. */
private fun trimNum(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()
