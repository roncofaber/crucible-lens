package crucible.lens.ui.settings

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun OrcidLoginScreen(
    loginUrl: String,
    onBack: () -> Unit,
    onKeyFound: (String) -> Unit
) {
    val isLoading = remember { mutableStateOf(true) }
    // evaluateJavascript returns the JS string JSON-encoded: outer quotes + \" escapes.
    // We strip those before matching so the regex sees plain {"crucible_apikey":"..."}.
    val keyRegex = remember { Regex(""""crucible_apikey"\s*:\s*"([a-f0-9]+)"""") }

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
            if (isLoading.value) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            AndroidView(
                factory = { context ->
                    WebView(context).apply {
                        // Clear any session from a previous login so each attempt starts fresh,
                        // avoiding 500s caused by stale JWT cookies conflicting with ORCID OAuth.
                        CookieManager.getInstance().removeAllCookies(null)
                        clearCache(true)

                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true

                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view: WebView,
                                request: WebResourceRequest
                            ): Boolean = false

                            override fun onPageStarted(
                                view: WebView,
                                url: String?,
                                favicon: Bitmap?
                            ) {
                                isLoading.value = true
                            }

                            override fun onPageFinished(view: WebView, url: String?) {
                                isLoading.value = false
                                // Evaluate body text on every page; the regex only matches
                                // when we're on the user_apikey response page
                                view.evaluateJavascript(
                                    "(function(){ return document.body.innerText; })()"
                                ) { result ->
                                    // Strip outer JSON string encoding produced by evaluateJavascript:
                                    // e.g. "{\"crucible_apikey\":\"abc\"}" → {"crucible_apikey":"abc"}
                                    val body = result
                                        ?.removeSurrounding("\"")
                                        ?.replace("\\\"", "\"")
                                        ?: return@evaluateJavascript
                                    val key = keyRegex.find(body)?.groupValues?.getOrNull(1)
                                    if (!key.isNullOrBlank()) onKeyFound(key)
                                }
                            }
                        }

                        loadUrl(loginUrl)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
