package com.esum.learnfarmore.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.esum.learnfarmore.AppContainer
import com.esum.learnfarmore.data.ProviderKind

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(container: AppContainer, onBack: () -> Unit) {
    val viewModel = viewModel { SettingsViewModel(container) }
    val state by viewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.savedCount) {
        if (state.savedCount > 0) snackbar.showSnackbar("Saved")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Model provider", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ProviderKind.entries.forEach { provider ->
                    FilterChip(
                        selected = state.provider == provider,
                        onClick = { viewModel.setProvider(provider) },
                        label = { Text(providerLabel(provider)) },
                    )
                }
            }
            OutlinedTextField(
                value = state.model,
                onValueChange = viewModel::setModel,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Model") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Uri),
            )
            OutlinedTextField(
                value = state.baseUrl,
                onValueChange = viewModel::setBaseUrl,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Endpoint (optional)") },
                supportingText = { Text(endpointHint(state.provider)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Uri),
            )
            OutlinedTextField(
                value = state.apiKey,
                onValueChange = viewModel::setApiKey,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("API key") },
                placeholder = { Text(if (state.hasStoredKey) "Saved. Enter a new key to replace it." else "Paste your key") },
                supportingText = { Text("Stored encrypted on this device and sent only to your provider.") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Password),
            )
            if (state.hasStoredKey) {
                TextButton(onClick = viewModel::removeKey) { Text("Remove saved key") }
            }
            Button(onClick = viewModel::save, modifier = Modifier.fillMaxWidth()) { Text("Save") }
        }
    }
}

private fun providerLabel(provider: ProviderKind) = when (provider) {
    ProviderKind.ANTHROPIC -> "Anthropic"
    ProviderKind.OPENAI_COMPATIBLE -> "OpenAI-compatible"
}

private fun endpointHint(provider: ProviderKind) = when (provider) {
    ProviderKind.ANTHROPIC -> "Leave empty for api.anthropic.com."
    ProviderKind.OPENAI_COMPATIBLE ->
        "Leave empty for api.openai.com, or enter any compatible HTTPS base URL such as https://openrouter.ai/api/v1."
}
