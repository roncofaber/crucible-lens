package crucible.lens.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.multiplatform.webview.web.LoadingState
import com.multiplatform.webview.web.WebView
import com.multiplatform.webview.web.rememberWebViewNavigator
import com.multiplatform.webview.web.rememberWebViewState
import crucible.lens.ui.common.AppScaffold

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

    // JavaScript must be enabled to evaluate body text and extract the API key
    state.webSettings.isJavaScriptEnabled = true
    // ORCID uses DOM storage for session state
    state.webSettings.androidWebSettings.domStorageEnabled = true

    // Evaluate body text whenever a page finishes loading
    val loadingState = state.loadingState
    LaunchedEffect(loadingState) {
        if (loadingState is LoadingState.Finished) {
            navigator.evaluateJavaScript("(function(){ return document.body.innerText; })()") { result ->
                val body = result
                    .removeSurrounding("\"")
                    .replace("\\\"", "\"")
                val key = KEY_REGEX.find(body)?.groupValues?.getOrNull(1)
                if (!key.isNullOrBlank()) onKeyFound(key)
            }
        }
    }

    AppScaffold(
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
        Column(modifier = Modifier.fillMaxSize().padding(padding).background(Color.White)) {
            if (loadingState is LoadingState.Loading) {
                LinearProgressIndicator(
                    progress = { loadingState.progress },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            WebView(
                state = state,
                navigator = navigator,
                modifier = Modifier.weight(1f).fillMaxWidth()
            )
        }
    }
}
