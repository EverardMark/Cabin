package com.cabin.app.data.remote

import com.cabin.app.data.SessionStore
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalSerializationApi::class)
val CabinJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    coerceInputValues = true
    namingStrategy = JsonNamingStrategy.SnakeCase
}

object Network {
    fun createApi(baseUrl: String, session: SessionStore): CabinApi {
        val authInterceptor = Interceptor { chain ->
            val original = chain.request()
            val token = session.currentToken
            val request = if (token != null) {
                original.newBuilder().header("Authorization", "Bearer $token").build()
            } else {
                original
            }
            chain.proceed(request)
        }

        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

        val client = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(CabinJson.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(CabinApi::class.java)
    }
}
