package com.cabin.app.util

import com.cabin.app.BuildConfig
import java.text.NumberFormat
import java.util.Locale

object Format {

    private val currency: NumberFormat = NumberFormat.getCurrencyInstance(Locale.US).apply {
        maximumFractionDigits = 0
    }

    /** "$525,000" for sale, "$2,800/mo" for rent. */
    fun price(price: Long, listingType: String): String {
        val base = currency.format(price)
        return if (listingType.equals("rent", ignoreCase = true)) "$base/mo" else base
    }

    fun capitalize(text: String): String =
        text.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }

    /** "3 bd" / "Studio" summary for bedrooms. */
    fun beds(bedrooms: Int): String = if (bedrooms <= 0) "Studio" else "$bedrooms bd"

    fun baths(bathrooms: Double): String {
        val n = if (bathrooms % 1.0 == 0.0) bathrooms.toInt().toString() else bathrooms.toString()
        return "$n ba"
    }

    fun area(sqft: Int): String = if (sqft <= 0) "—" else "${"%,d".format(sqft)} sqft"

    /** Resolves a possibly-relative image URL (e.g. "/uploads/x.jpg") to an absolute URL. */
    fun imageUrl(url: String): String {
        if (url.startsWith("http://", true) || url.startsWith("https://", true)) return url
        val base = BuildConfig.API_BASE_URL.trimEnd('/')
        return base + url
    }
}
