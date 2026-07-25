package com.cabin.app.util

import android.content.Context
import android.net.Uri
import java.io.File

/** Copies a picked content:// image into the cache dir so it can be uploaded as multipart. */
fun copyUriToCache(context: Context, uri: Uri): File? = runCatching {
    val resolver = context.contentResolver
    val ext = when (resolver.getType(uri)?.lowercase()) {
        "image/png" -> "png"
        "image/webp" -> "webp"
        "image/gif" -> "gif"
        else -> "jpg"
    }
    val file = File.createTempFile("cabin_upload_", ".$ext", context.cacheDir)
    resolver.openInputStream(uri)?.use { input ->
        file.outputStream().use { output -> input.copyTo(output) }
    } ?: return null
    file
}.getOrNull()
