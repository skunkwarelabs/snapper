package com.skunk.snapper.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import android.Manifest
import coil.compose.AsyncImage
import com.skunk.snapper.util.ImageStorage
import com.skunk.snapper.util.LocationProvider
import java.io.File
import java.util.Calendar
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddCatchScreen(vm: CatchViewModel, onDone: () -> Unit, onPickLocation: () -> Unit) {
    val state by vm.add.collectAsState()
    val context = LocalContext.current

    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var pendingDateMillis by remember { mutableStateOf<Long?>(null) }

    // Pop back to the list as soon as the save completes.
    LaunchedEffect(state.saved) {
        if (state.saved) onDone()
    }

    // Ask for location once so the saved catch can be GPS-stamped.
    val locationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) vm.useCurrentLocation() }
    LaunchedEffect(Unit) {
        if (!LocationProvider.hasPermission(context)) {
            locationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    // rememberSaveable: the system often kills our process while the camera app is
    // foreground, so the pending Uri must survive process death or the captured photo
    // never makes it back. (Uri is Parcelable, so the default saver handles it.)
    var pendingCameraUri by rememberSaveable { mutableStateOf<Uri?>(null) }

    val takePicture = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = pendingCameraUri
        // Trust `success`, but some OEM camera apps (Samsung) report false even after a
        // good capture — so fall back to checking whether the file actually got written.
        if (uri != null && (success || ImageStorage.hasContent(context, uri))) {
            vm.onPhotoChosen(uri)
        }
    }

    val pickPhoto = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let { vm.onPhotoChosen(it) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New catch") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Photo preview / placeholder
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(4f / 3f)
                    .clip(RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                val path = state.photoPath
                if (path != null) {
                    AsyncImage(
                        model = File(path),
                        contentDescription = "Catch photo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(
                        "Take or choose a photo of your catch",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (state.identifying) {
                    Box(
                        Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(8.dp))
                            Text("Identifying…")
                        }
                    }
                }
            }

            // Photo source buttons
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = {
                        val uri = ImageStorage.newCameraUri(context)
                        pendingCameraUri = uri
                        takePicture.launch(uri)
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.PhotoCamera, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Camera")
                }
                OutlinedButton(
                    onClick = {
                        pickPhoto.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Gallery")
                }
            }

            state.message?.let { msg ->
                Text(
                    text = msg,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            // Identified details (read-only hint)
            if (state.details.isNotBlank()) {
                Text(
                    text = state.details,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            OutlinedTextField(
                value = state.species,
                onValueChange = vm::updateSpecies,
                label = { Text("Species") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = state.lengthEstimate,
                    onValueChange = vm::updateLength,
                    label = { Text("Length (in)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1.2f)
                )
                OutlinedTextField(
                    value = state.weightLb,
                    onValueChange = vm::updateWeightLb,
                    label = { Text("lb") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = state.weightOz,
                    onValueChange = vm::updateWeightOz,
                    label = { Text("oz") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
            }

            // When + where the catch happened (default: now / current location).
            OutlinedButton(
                onClick = { showDatePicker = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Schedule, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    if (state.caughtAt > 0L) formatDate(state.caughtAt) else "Set date & time",
                    modifier = Modifier.weight(1f)
                )
            }
            OutlinedButton(
                onClick = onPickLocation,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Place, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    if (state.lat != null && state.lng != null)
                        "%.4f, %.4f".format(state.lat, state.lng)
                    else "Set location",
                    modifier = Modifier.weight(1f)
                )
            }

            OutlinedTextField(
                value = state.notes,
                onValueChange = vm::updateNotes,
                label = { Text("Notes (bait, spot…)") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
            )

            Button(
                onClick = vm::save,
                enabled = state.photoPath != null && !state.identifying,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save catch")
            }
        }
    }

    // Date picker → then time picker → combine into the draft's caughtAt.
    if (showDatePicker) {
        val dpState = rememberDatePickerState(
            initialSelectedDateMillis = state.caughtAt.takeIf { it > 0L }
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pendingDateMillis = dpState.selectedDateMillis
                    showDatePicker = false
                    showTimePicker = true
                }) { Text("Next") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = dpState)
        }
    }

    if (showTimePicker) {
        val base = remember {
            Calendar.getInstance().apply {
                timeInMillis = state.caughtAt.takeIf { it > 0L } ?: System.currentTimeMillis()
            }
        }
        val tpState = rememberTimePickerState(
            initialHour = base.get(Calendar.HOUR_OF_DAY),
            initialMinute = base.get(Calendar.MINUTE),
            is24Hour = false
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    vm.updateTime(
                        combineDateTime(
                            pendingDateMillis ?: state.caughtAt,
                            tpState.hour,
                            tpState.minute
                        )
                    )
                    showTimePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("Cancel") }
            },
            text = { TimePicker(state = tpState) }
        )
    }
}

/**
 * DatePicker hands back UTC-midnight millis; pull the calendar date out in UTC and
 * recombine with the chosen wall-clock time in the device's local zone.
 */
private fun combineDateTime(dateMillis: Long, hour: Int, minute: Int): Long {
    val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = dateMillis }
    return Calendar.getInstance().apply {
        set(Calendar.YEAR, utc.get(Calendar.YEAR))
        set(Calendar.MONTH, utc.get(Calendar.MONTH))
        set(Calendar.DAY_OF_MONTH, utc.get(Calendar.DAY_OF_MONTH))
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}
