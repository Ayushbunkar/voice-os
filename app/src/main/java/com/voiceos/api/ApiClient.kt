package com.voiceos.api

import android.content.Context
import com.voiceos.memory.ContextManager
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * ApiClient — Singleton that establishes the Retrofit configuration.
 *
 * Uses the HTTP interceptor to inject the JWT from SharedPreferences 
 * automatically into every outbound request.
 */
object ApiClient {
    // Production default domain for deployed backend.
    // For Android emulator local testing, temporarily switch to: http://10.0.2.2:5000/
    private const val BASE_URL = "https://api.voiceos.app/"

    private var retrofit: Retrofit? = null
    lateinit var api: VoiceOSApi
        private set

    fun init(context: Context) {
        if (retrofit != null) return

        // 1. Auth Interceptor grabbing token directly from ContextManager (which wraps SharedPreferences)
        val authInterceptor = Interceptor { chain ->
            val token = ContextManager.getAuthToken()
            val requestBuilder = chain.request().newBuilder()
            
            if (token != null) {
                requestBuilder.addHeader("Authorization", "Bearer $token")
            }
            
            chain.proceed(requestBuilder.build())
        }

        // 2. Logging
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        // 3. OkHttpClient setup
        val client = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        // 4. Retrofit Build
        retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        api = retrofit!!.create(VoiceOSApi::class.java)
    }
}
