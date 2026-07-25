package com.cabin.app.util

import com.cabin.app.data.remote.CabinJson
import kotlinx.serialization.decodeFromString
import retrofit2.HttpException
import java.io.IOException

/** Converts an exception into a short, user-friendly message. */
fun Throwable.userMessage(): String = when (this) {
    is HttpException -> {
        val body = runCatching { response()?.errorBody()?.string() }.getOrNull()
        val serverMessage = body?.let {
            runCatching {
                CabinJson.decodeFromString<Map<String, String>>(it)["error"]
            }.getOrNull()
        }
        serverMessage ?: "Request failed (${code()})"
    }

    is IOException -> "Can't reach the server. Make sure the API is running."
    else -> message ?: "Something went wrong."
}
