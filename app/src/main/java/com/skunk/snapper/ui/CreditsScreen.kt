package com.skunk.snapper.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

private data class PhotoCredit(val species: String, val artist: String, val license: String)

/** Attribution for the bundled fish photos (Wikimedia Commons) — required for the CC BY-SA ones. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreditsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val credits by produceState(initialValue = emptyList<PhotoCredit>()) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val obj = JSONObject(
                    context.assets.open("fish_credits.json").bufferedReader().use { it.readText() }
                )
                obj.keys().asSequence().map { k ->
                    val o = obj.getJSONObject(k)
                    PhotoCredit(
                        species = k,
                        artist = o.optString("artist").replace("&amp;", "&").ifBlank { "Unknown author" },
                        license = o.optString("license").ifBlank { "See Wikimedia Commons" }
                    )
                }.sortedBy { it.species }.toList()
            }.getOrDefault(emptyList())
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Photo credits") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    "Fish photos are from Wikimedia Commons. Public-domain and Creative Commons " +
                        "works are used under their respective licenses; CC BY-SA images remain " +
                        "© their authors and are shared under the same license.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            items(credits, key = { it.species }) { c ->
                Column(Modifier.fillMaxWidth()) {
                    Text(c.species, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "${c.artist} · ${c.license}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    HorizontalDivider(Modifier.padding(top = 12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant)
                }
            }
        }
    }
}
