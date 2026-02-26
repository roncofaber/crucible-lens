package gov.lbl.crucible.scanner.data.api

import com.squareup.moshi.Moshi
import gov.lbl.crucible.scanner.BuildConfig
import gov.lbl.crucible.scanner.data.preferences.PreferencesManager
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {
    private var apiKey: String? = null
    private var baseUrl: String = PreferencesManager.DEFAULT_API_BASE_URL
    private var _service: CrucibleApiService? = null

    fun setApiKey(key: String) {
        apiKey = key
    }

    fun setBaseUrl(url: String) {
        if (url != baseUrl) {
            baseUrl = url
            _service = null // Force recreation with new URL
        }
    }

    private val moshi = Moshi.Builder()
        .build()

    private val authInterceptor = Interceptor { chain ->
        val request = chain.request()
        val newRequest = if (apiKey != null) {
            request.newBuilder()
                .addHeader("Authorization", "Bearer $apiKey")
                .build()
        } else {
            request
        }
        chain.proceed(newRequest)
    }

    val service: CrucibleApiService
        get() {
            if (_service == null) {
                val clientBuilder = OkHttpClient.Builder()
                    .addInterceptor(authInterceptor)
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .writeTimeout(30, TimeUnit.SECONDS)

                if (BuildConfig.DEBUG) {
                    clientBuilder.addInterceptor(
                        HttpLoggingInterceptor().apply {
                            level = HttpLoggingInterceptor.Level.BASIC
                        }
                    )
                }

                val retrofit = Retrofit.Builder()
                    .baseUrl(baseUrl)
                    .client(clientBuilder.build())
                    .addConverterFactory(MoshiConverterFactory.create(moshi))
                    .build()

                _service = retrofit.create(CrucibleApiService::class.java)
            }
            return requireNotNull(_service)
        }
}
