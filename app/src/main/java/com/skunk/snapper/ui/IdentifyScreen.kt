package com.skunk.snapper.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.skunk.snapper.ai.FishGuess
import com.skunk.snapper.util.ImageStorage
import java.io.File

/** A quick "what fish is this?" tab — a live camera + shutter; IDs the shot, saves nothing. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IdentifyScreen(vm: CatchViewModel, onLogCatch: () -> Unit, onViewRegs: (String) -> Unit) {
    val state by vm.idState.collectAsState()
    val context = LocalContext.current

    var hasCamera by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val askCamera = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCamera = granted }

    Scaffold { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color.Black)
        ) {
            when {
                !hasCamera -> PermissionPrompt { askCamera.launch(Manifest.permission.CAMERA) }
                state.photoUri != null -> CapturedResult(
                    state,
                    onRetake = vm::clearIdentify,
                    onLog = { vm.prefillCatchFromIdentify(); onLogCatch() },
                    onViewRegs = onViewRegs,
                    onChoose = vm::chooseIdSpecies
                )
                else -> CameraCapture(onCaptured = { vm.identifyPhoto(it) })
            }
        }
    }
}

@Composable
private fun PermissionPrompt(onGrant: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "Camera access is needed to identify fish.",
                color = Color.White,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(24.dp)
            )
            Button(onClick = onGrant) { Text("Grant camera access") }
        }
    }
}

@Composable
private fun CameraCapture(onCaptured: (Uri) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val controller = remember {
        LifecycleCameraController(context).apply { bindToLifecycle(lifecycleOwner) }
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = { PreviewView(it).apply { this.controller = controller } },
            modifier = Modifier.fillMaxSize()
        )

        // Snapchat-style shutter: a white ring with a filled inner circle.
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
                .size(76.dp)
                .border(4.dp, Color.White, CircleShape)
                .padding(6.dp)
                .clip(CircleShape)
                .background(Color.White)
                .clickable {
                    val file = File(context.cacheDir, "identify_${System.currentTimeMillis()}.jpg")
                    controller.takePicture(
                        ImageCapture.OutputFileOptions.Builder(file).build(),
                        ContextCompat.getMainExecutor(context),
                        object : ImageCapture.OnImageSavedCallback {
                            override fun onImageSaved(results: ImageCapture.OutputFileResults) {
                                onCaptured(Uri.fromFile(file))
                            }
                            override fun onError(exc: ImageCaptureException) {}
                        }
                    )
                }
        )
    }
}

@Composable
private fun CapturedResult(
    state: IdUiState,
    onRetake: () -> Unit,
    onLog: () -> Unit,
    onViewRegs: (String) -> Unit,
    onChoose: (String) -> Unit
) {
    Box(Modifier.fillMaxSize()) {
        AsyncImage(
            model = state.photoUri,
            contentDescription = "Captured photo",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Close / retake — hidden on the "no fish detected" message (its "Try again" covers it).
        if (state.message == null) {
            FilledIconButton(
                onClick = onRetake,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Retake")
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            when {
                state.identifying -> Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Text("Identifying…", modifier = Modifier.padding(top = 8.dp))
                    }
                }

                state.result != null -> ResultCard(state.result, onViewRegs, onLog, onChoose)

                state.message != null -> Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(20.dp)
                ) {
                    Column {
                        Text(state.message, style = MaterialTheme.typography.titleMedium)
                        Button(onClick = onRetake, modifier = Modifier.padding(top = 12.dp)) {
                            Text("Try again")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultCard(
    guess: FishGuess,
    onViewRegs: (String) -> Unit,
    onLog: () -> Unit,
    onChoose: (String) -> Unit
) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(20.dp)
    ) {
        Column {
            Text(
                guess.species.ifBlank { "Unknown" },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            if (guess.confidence.isNotBlank()) {
                Text(
                    "${guess.confidence} confidence",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            val size = listOf(
                guess.lengthEstimate.takeIf { it.isNotBlank() }?.let { "$it in" },
                weightText(guess).takeIf { it.isNotBlank() }
            ).filterNotNull().joinToString(" · ")
            if (size.isNotBlank()) {
                Text(size, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp))
            }
            if (guess.details.isNotBlank()) {
                Text(guess.details, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
            }
            if (guess.alternatives.isNotEmpty()) {
                Row(
                    modifier = Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    guess.alternatives.forEach { alt ->
                        AssistChip(onClick = { onChoose(alt) }, label = { Text(alt) })
                    }
                }
            }
            Row(
                modifier = Modifier.padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(onClick = onLog, modifier = Modifier.weight(1f)) {
                    Text("Log this catch")
                }
                OutlinedButton(
                    onClick = { onViewRegs(guess.species) },
                    enabled = guess.species.isNotBlank() && !guess.species.equals("Unknown", true),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("View regs")
                }
            }
        }
    }
}

private fun weightText(guess: FishGuess): String = listOf(
    guess.weightLb.takeIf { it.isNotBlank() }?.let { "$it lb" },
    guess.weightOz.takeIf { it.isNotBlank() }?.let { "$it oz" }
).filterNotNull().joinToString(" ")
