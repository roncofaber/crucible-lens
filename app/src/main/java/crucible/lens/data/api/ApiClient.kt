package crucible.lens.data.api

import crucible.lens.BuildConfig
import crucible.lens.data.preferences.PreferencesManager
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

object ApiClient {
    private var apiKey: String = ""
    private var baseUrl: String = PreferencesManager.DEFAULT_API_BASE_URL
    private var _service: CrucibleApiService? = null

    fun setApiKey(key: String) {
        apiKey = key
        _service = null // Force recreation with new API key
    }

    fun setBaseUrl(url: String) {
        baseUrl = url.trimEnd('/') + "/"
        _service = null // Force recreation with new URL
    }

    fun getBaseUrl() = baseUrl

    fun getApiKey() = apiKey

    val httpClient: HttpClient
        get() = HttpClient {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                })
            }
            install(Logging) {
                level = if (BuildConfig.DEBUG) LogLevel.BASIC else LogLevel.NONE
            }
        }

    val service: CrucibleApiService
        get() {
            if (_service == null) {
                _service = CrucibleApiService(httpClient, baseUrl, apiKey)
            }
            return requireNotNull(_service)
        }
}
