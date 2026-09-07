package com.example.arivai.ui.onboarding

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.util.Locale

@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel,
    onNavigateToLibrary: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val ggufPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.loadLocalGgufFile(context, it) }
    }

    LaunchedEffect(uiState.isCompleted) {
        if (uiState.isCompleted) {
            onNavigateToLibrary()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Welcome to ariv.ai",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "An on-device RAG-powered Socratic AI Tutor. Runs local Qwen3 GGUF weights for 100% offline privacy.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        if (uiState.hasExistingModel) {
            Text(
                text = "🟢 GGUF Model File Detected on Device",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = { viewModel.continueToLibrary() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("🚀 Continue to Textbook Library & Chat")
            }

            Spacer(modifier = Modifier.height(12.dp))
        }

        if (uiState.isDownloading) {
            LinearProgressIndicator(
                progress = { uiState.progressPercent / 100f },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "${uiState.progressPercent}% (${String.format(Locale.getDefault(), "%.1f", uiState.downloadedMb)} MB / ${String.format(Locale.getDefault(), "%.1f", uiState.totalMb)} MB)",
                style = MaterialTheme.typography.bodySmall
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(onClick = { viewModel.cancelDownload() }) {
                Text("Cancel Download")
            }
        } else {
            if (uiState.errorMessage != null) {
                Text(
                    text = uiState.errorMessage ?: "",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            OutlinedButton(
                onClick = { ggufPickerLauncher.launch("*/*") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("📁 Select Local .gguf File from Phone")
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = { viewModel.startDownload() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("📥 Download Model Weights (~2.5GB)")
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        TextButton(onClick = { viewModel.continueToLibrary() }) {
            Text("⚡ Run Instant Socratic Demo Mode")
        }
    }
}
