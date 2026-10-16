package com.fresko.app.ui.components

import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

private const val DAY = 24 * 3600 * 1000L

/**
 * « il y a 5 min », « hier »… Une date à minuit UTC pile est considérée comme
 * « au jour près » (cas de Demonic) : on n'affiche alors pas d'heure.
 */
fun relativeTime(millis: Long, now: Long = System.currentTimeMillis()): String {
    val dayOnly = millis % DAY == 0L
    val diff = (now - millis).coerceAtLeast(0)
    if (dayOnly) {
        val days = (now / DAY) - (millis / DAY)
        return when {
            days <= 0 -> "aujourd'hui"
            days == 1L -> "hier"
            days < 7 -> "il y a $days j"
            else -> DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.FRANCE).format(Date(millis))
        }
    }
    val minutes = TimeUnit.MILLISECONDS.toMinutes(diff)
    return when {
        minutes < 1 -> "à l'instant"
        minutes < 60 -> "il y a $minutes min"
        minutes < 24 * 60 -> "il y a ${minutes / 60} h"
        minutes < 48 * 60 -> "hier"
        minutes < 7 * 24 * 60 -> "il y a ${minutes / (24 * 60)} j"
        else -> DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.FRANCE).format(Date(millis))
    }
}

/** Libellé court d'un chapitre : « Ch. 281 ». */
fun shortChapter(number: Double?, fallback: String?): String = when {
    number == null -> fallback ?: "—"
    number % 1.0 == 0.0 -> "Ch. ${number.toLong()}"
    else -> "Ch. $number"
}
