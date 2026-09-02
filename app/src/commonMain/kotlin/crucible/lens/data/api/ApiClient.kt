package crucible.lens.data.api

import crucible.lens.data.preferences.AppPreferences
import crucible.lens.platform.isDebugBuild
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

class ApiClient private constructor(engine: HttpClientEngine?) {
    constructor() : this(null)

    companion object {
        internal fun withEngine(engine: HttpClientEngine): ApiClient = ApiClient(engine)
    }

    private var apiKey: String = ""
    private var baseUrl: String = AppPreferences.DEFAULT_API_BASE_URL
    private var _service: CrucibleApiService? = null

    fun setApiKey(key: String) {
        if (apiKey == key) return
        apiKey = key
        _service = null // Force recreation with new API key
    }

    fun setBaseUrl(url: String) {
        val normalized = url.trim().trimEnd('/') + "/"
        if (baseUrl == normalized) return
        baseUrl = normalized
        _service = null // Force recreation with new URL
    }

    fun getBaseUrl() = baseUrl

    fun getApiKey() = apiKey

    private val httpClient: HttpClient = if (engine == null) HttpClient {
        configureApiClient()
    } else HttpClient(engine) {
        configureApiClient()
    }

    internal val gcsClient: HttpClient = if (engine != null) httpClient else HttpClient {
        install(HttpTimeout) {
            requestTimeoutMillis = 10 * 60_000
            connectTimeoutMillis = 30_000
            socketTimeoutMillis = 10 * 60_000
        }
    }

    private fun HttpClientConfig<*>.configureApiClient() {
        // Ktor defaults expectSuccess to false, meaning a non-2xx response (e.g. a 409 from
        // POST /resources/{id}/metadata when metadata already exists) is returned as a normal
        // response rather than thrown — and .body<Unit>() unconditionally succeeds regardless of
        // status, so any ApiResult<Unit> call would silently report success on a failed request.
        // Setting this true makes safeCall's existing ResponseException handler actually catch it.
        expectSuccess = true
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
        install(Logging) {
            level = if (isDebugBuild) LogLevel.INFO else LogLevel.NONE
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 30_000
            connectTimeoutMillis = 10_000
            socketTimeoutMillis  = 30_000
        }
    }

    val service: CrucibleApiService
        get() {
            if (_service == null) {
                _service = CrucibleApiService(httpClient, gcsClient, baseUrl, apiKey)
            }
            return requireNotNull(_service)
        }
}
