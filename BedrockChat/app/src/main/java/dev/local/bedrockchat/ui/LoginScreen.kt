package dev.local.bedrockchat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.local.bedrockchat.data.DEFAULT_MODEL_ID
import dev.local.bedrockchat.data.MODELS
import dev.local.bedrockchat.data.REGIONS
import dev.local.bedrockchat.data.modelById
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(onSignIn: suspend (apiKey: String, region: String, modelId: String) -> String?) {
    val colors = AppTheme.colors
    val scope = rememberCoroutineScope()
    var apiKey by rememberSaveable { mutableStateOf("") }
    var region by rememberSaveable { mutableStateOf("us-east-1") }
    var modelId by rememberSaveable { mutableStateOf(DEFAULT_MODEL_ID) }
    var showKey by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Box(Modifier.fillMaxSize().background(colors.background).systemBarsPadding().imePadding()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(72.dp))
            Spark(Modifier.size(56.dp))
            Spacer(Modifier.height(20.dp))
            Text(
                "Claude, through\nAmazon Bedrock",
                style = androidx.compose.material3.MaterialTheme.typography.displaySmall,
                color = colors.text,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Sign in with an Amazon Bedrock API key to chat with Claude.",
                style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(36.dp))

            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it; error = null },
                label = { Text("Bedrock API key") },
                singleLine = true,
                visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = { showKey = !showKey }) {
                        Icon(if (showKey) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                            if (showKey) "Hide key" else "Show key")
                    }
                },
                shape = RoundedCornerShape(14.dp),
                colors = fieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(14.dp))
            Dropdown("AWS region", region, REGIONS, { it }) { region = it }
            Spacer(Modifier.height(14.dp))
            Dropdown("Default model", modelId, MODELS.map { it.id }, { modelById(it).name }) { modelId = it }

            error?.let {
                Spacer(Modifier.height(14.dp))
                Text(it, color = androidx.compose.material3.MaterialTheme.colorScheme.error,
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
            }

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = {
                    busy = true
                    error = null
                    scope.launch {
                        error = onSignIn(apiKey, region, modelId)
                        busy = false
                    }
                },
                enabled = apiKey.isNotBlank() && !busy,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.primaryButton, contentColor = colors.onPrimaryButton),
                modifier = Modifier.fillMaxWidth().height(54.dp),
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(22.dp), color = colors.onPrimaryButton, strokeWidth = 2.dp)
                else Text("Continue with Bedrock API key", style = androidx.compose.material3.MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.height(20.dp))
            Text(
                "Create a key in the Amazon Bedrock console under API keys. The key is checked with one short " +
                    "request, then stored encrypted on this device only.",
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = AppTheme.colors.text,
    unfocusedBorderColor = AppTheme.colors.border,
    focusedContainerColor = AppTheme.colors.surface,
    unfocusedContainerColor = AppTheme.colors.surface,
    focusedLabelColor = AppTheme.colors.text,
    cursorColor = AppTheme.colors.text,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Dropdown(
    label: String,
    value: String,
    options: List<String>,
    display: (String) -> String,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = display(value),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            shape = RoundedCornerShape(14.dp),
            colors = fieldColors(),
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(text = { Text(display(option)) }, onClick = { onSelect(option); expanded = false })
            }
        }
    }
}

