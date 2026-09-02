package com.cabin.app.util

import com.cabin.app.BuildConfig
import java.text.NumberFormat
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Currency
import java.util.Locale

object Format {

    private val locale: Locale = Locale.Builder().setLanguage("en").setRegion("PH").build()

    private val currency: NumberFormat = NumberFormat.getCurrencyInstance(locale).apply {
        maximumFractionDigits = 0
        this.currency = Currency.getInstance("PHP")
    }

    /** "₱8,500,000" for sale, "₱22,000/mo" for rent. */
    fun price(price: Long, listingType: String): String {
        val base = currency.format(price)
        return if (listingType.equals("rent", ignoreCase = true)) "$base/mo" else base
    }

    /** A compact form for map pins and dense rows: "₱8.5M", "₱22K". */
    fun compactPrice(price: Long): String = when {
        price >= 1_000_000 -> "₱%.1fM".format(price / 1_000_000.0).replace(".0M", "M")
        price >= 1_000 -> "₱%.1fK".format(price / 1_000.0).replace(".0K", "K")
        else -> "₱$price"
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

    /** Turns a verification flag code ("thin_description") into readable text. */
    fun flagLabel(code: String): String = capitalize(code.replace('_', ' '))

    // --- dates ---

    /** Parses the RFC3339 timestamps the API returns. */
    fun instant(raw: String?): Instant? {
        if (raw.isNullOrBlank()) return null
        return runCatching { Instant.parse(raw) }.getOrNull()
    }

    private val dateTimeFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("d MMM 'at' h:mm a", locale).withZone(ZoneId.systemDefault())

    /** "3 Sep at 2:00 PM" */
    fun dateTime(raw: String?): String =
        instant(raw)?.let { dateTimeFormatter.format(it) } ?: "—"

    /** "2h ago", "3d ago" — for chat lists and listing freshness. */
    fun relative(raw: String?): String {
        val then = instant(raw) ?: return ""
        val d = Duration.between(then, Instant.now())
        return when {
            d.toMinutes() < 1 -> "just now"
            d.toMinutes() < 60 -> "${d.toMinutes()}m ago"
            d.toHours() < 24 -> "${d.toHours()}h ago"
            d.toDays() < 30 -> "${d.toDays()}d ago"
            else -> "${d.toDays() / 30}mo ago"
        }
    }

    /** True when the owner hasn't confirmed availability within 30 days. */
    fun isStale(lastConfirmedAt: String?, createdAt: String?): Boolean {
        val reference = instant(lastConfirmedAt) ?: instant(createdAt) ?: return false
        return Duration.between(reference, Instant.now()).toDays() > 30
    }

    /** Resolves a possibly-relative image URL (e.g. "/uploads/x.jpg") to an absolute URL. */
    fun imageUrl(url: String): String {
        if (url.startsWith("http://", true) || url.startsWith("https://", true)) return url
        val base = BuildConfig.API_BASE_URL.trimEnd('/')
        return base + url
    }
}
