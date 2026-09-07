package com.example.arivai.ui.chat

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.arivai.data.model.EngineMode
import com.example.arivai.ui.components.CameraCaptureButton
import com.example.arivai.ui.components.ChatBubble
import com.example.arivai.ui.components.MicButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val context = LocalContext.current

    // Camera image capture
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        bitmap?.let { viewModel.processImageForInput(it) }
    }

    // GGUF model file picker launcher
    val ggufPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.loadCustomGgufFile(context, it) }
    }

    // Audio permission
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.startVoiceInput()
        }
    }

    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(text = "ariv.ai Socratic Tutor")
                        val modeText = when (uiState.engineMode) {
                            EngineMode.NATIVE_GGUF -> "🟢 Native Qwen3 GGUF Active"
                            EngineMode.DEMO_FALLBACK -> "🟡 Socratic Rule-Based Mode Active (Tap ℹ️ for details)"
                            EngineMode.NOT_LOADED -> "🔴 Model Not Loaded (Tap ℹ️ for details)"
                        }
                        Text(
                            text = modeText,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.toggleModelStatusDialog(true) }) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Model Info",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Engine Status Banner
            val bannerBg = when (uiState.engineMode) {
                EngineMode.NATIVE_GGUF -> Color(0xFFE8F5E9)
                else -> Color(0xFFFFF3E0)
            }
            val bannerText = when (uiState.engineMode) {
                EngineMode.NATIVE_GGUF -> "On-Device GGUF Inference Active. Native token generation on device."
                else -> "Running in Socratic Demo Mode. Tap here to select a local .gguf file or view hardware status."
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(bannerBg)
                    .clickable { viewModel.toggleModelStatusDialog(true) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = bannerText,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
            }

            // Chat Message List
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                items(uiState.messages) { message ->
                    ChatBubble(message = message)
                }
            }

            if (uiState.errorMessage != null) {
                Text(
                    text = uiState.errorMessage ?: "",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            // Bottom Input Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CameraCaptureButton(onClick = { cameraLauncher.launch(null) })

                MicButton(
                    isListening = uiState.isListening,
                    onClick = {
                        audioPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                    }
                )

                OutlinedTextField(
                    value = uiState.inputText,
                    onValueChange = { viewModel.onInputTextChange(it) },
                    placeholder = { Text("Ask a question or topic...") },
                    modifier = Modifier.weight(1f),
                    maxLines = 3
                )

                Spacer(modifier = Modifier.width(4.dp))

                IconButton(
                    onClick = { viewModel.sendMessage() },
                    enabled = uiState.inputText.isNotBlank() && !uiState.isGenerating
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }

    // Model Status & Hardware Diagnostic Dialog
    if (uiState.showModelStatusDialog) {
        LaunchedEffect(Unit) {
            viewModel.loadHardwareReport(context)
        }

        AlertDialog(
            onDismissRequest = { viewModel.toggleModelStatusDialog(false) },
            title = { Text("Device Hardware & Engine Status") },
            text = {
                Column {
                    Text(
                        text = "Current Mode: ${uiState.engineMode}",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.padding(top = 8.dp))

                    uiState.hardwareReport?.let { hw ->
                        Text(
                            text = "📱 Hardware Specs:\n• RAM: ${hw.availRamMb} MB free / ${hw.totalRamMb} MB total\n• CPU: ${hw.cpuCores} Cores (${hw.supportedAbis.firstOrNull() ?: "arm64-v8a"})\n• Verdict: ${if (hw.canRun4BModel) "🟢 Sufficient memory for 4B model" else "🟡 Low RAM — recommend 1.5B model"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.padding(top = 8.dp))
                    }

                    Text(
                        text = "Engine Diagnostics:\n${uiState.engineStatusDetail}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.toggleModelStatusDialog(false)
                    ggufPickerLauncher.launch("*/*")
                }) {
                    Text("📁 Select Local .gguf File")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.toggleModelStatusDialog(false) }) {
                    Text("Close")
                }
            }
        )
    }
}
