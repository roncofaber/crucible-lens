package crucible.lens.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.multiplatform.webview.web.LoadingState
import com.multiplatform.webview.web.WebView
import com.multiplatform.webview.web.rememberWebViewNavigator
import com.multiplatform.webview.web.rememberWebViewState
import crucible.lens.ui.common.AppScaffold
import kotlinx.coroutines.delay

private val KEY_REGEX = Regex(""""crucible_apikey"\s*:\s*"([^"]+)"""")

private fun extractKey(rawJsResult: String?): String? {
    if (rawJsResult.isNullOrBlank() || rawJsResult == "null") return null
    val body = rawJsResult
        .removeSurrounding("\"")
        .replace("\\n", "\n")
        .replace("\\\"", "\"")
        .replace("\\\\", "\\")
    return KEY_REGEX.find(body)?.groupValues?.getOrNull(1)?.ifBlank { null }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrcidLoginScreen(
    loginUrl: String,
    onBack: () -> Unit,
    onKeyFound: (String) -> Unit
) {
    val state = rememberWebViewState(loginUrl)
    val navigator = rememberWebViewNavigator()
    var keyFound by remember { mutableStateOf(false) }

    state.webSettings.isJavaScriptEnabled = true
    state.webSettings.androidWebSettings.domStorageEnabled = true

    // Re-run whenever the page finishes loading. Polls up to 10 times with
    // 500 ms gaps so slow DOM population or redirect chains don't cause misses.
    // The LaunchedEffect is cancelled automatically when loadingState changes
    // (new navigation starts), so we never act on a stale page.
    LaunchedEffect(state.loadingState) {
        if (state.loadingState !is LoadingState.Finished || keyFound) return@LaunchedEffect
        repeat(10) { attempt ->
            if (keyFound) return@repeat
            if (attempt > 0) delay(500)
            navigator.evaluateJavaScript("(function(){ return document.body.innerText; })()") { result ->
                if (!keyFound) {
                    val key = extractKey(result)
                    if (!key.isNullOrBlank()) {
                        keyFound = true
                        onKeyFound(key)
                    }
                }
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
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (state.loadingState is LoadingState.Loading) {
                LinearProgressIndicator(
                    progress = { (state.loadingState as LoadingState.Loading).progress },
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
