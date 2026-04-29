package crucible.lens.data.api

import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

object ApiClient {
    private var apiKey: String = ""
    private var baseUrl: String = "https://crucible.lbl.gov/api/v2/"
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
                level = LogLevel.NONE
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
