package crucible.lens.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrcidLoginScreen(
    loginUrl: String,
    onBack: () -> Unit,
    onKeyFound: (String) -> Unit
) {
    // TODO: Implement WebView for ORCID login on iOS. For now, show placeholder.
    // The Android implementation uses AndroidView with WebView to handle ORCID OAuth flow.
    // iOS will need a different approach (WKWebView via Kotlin Native interop).

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sign in with ORCID") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Card(
                modifier = Modifier.padding(32.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        "ORCID Login",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        "Web-based ORCID authentication is not yet available on this platform. Please use the API key option in settings.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(onClick = onBack) {
                        Text("Go Back")
                    }
                }
            }
        }
    }
}
