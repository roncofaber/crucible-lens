package crucible.lens.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.multiplatform.webview.web.LoadingState
import com.multiplatform.webview.web.WebView
import com.multiplatform.webview.web.rememberWebViewNavigator
import com.multiplatform.webview.web.rememberWebViewState

private val KEY_REGEX = Regex(""""crucible_apikey"\s*:\s*"([a-f0-9]+)"""")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrcidLoginScreen(
    loginUrl: String,
    onBack: () -> Unit,
    onKeyFound: (String) -> Unit
) {
    val state = rememberWebViewState(loginUrl)
    val navigator = rememberWebViewNavigator()

    // Evaluate body text whenever a page finishes loading
    val loadingState = state.loadingState
    LaunchedEffect(loadingState) {
        if (loadingState is LoadingState.Finished) {
            navigator.evaluateJavaScript("(function(){ return document.body.innerText; })()") { result ->
                val body = result
                    ?.removeSurrounding("\"")
                    ?.replace("\\\"", "\"")
                    ?: return@evaluateJavaScript
                val key = KEY_REGEX.find(body)?.groupValues?.getOrNull(1)
                if (!key.isNullOrBlank()) onKeyFound(key)
            }
        }
    }

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
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (loadingState is LoadingState.Loading) {
                LinearProgressIndicator(
                    progress = { (loadingState as? LoadingState.Loading)?.progress ?: 0f },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            WebView(
                state = state,
                navigator = navigator,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
