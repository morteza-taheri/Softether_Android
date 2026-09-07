package vn.unlimit.vpngate.utils

import android.content.Context
import java.util.Date

/**
 * Locale-aware date formatting. When the app language is Persian, dates are
 * rendered in the Solar Hijri (Shamsi) calendar via android.icu (available
 * from API 24); otherwise the Gregorian calendar is used.
 */
object CalendarFormatter {

    /**
     * Format [date] (e.g. a server-list last-update timestamp) for the
     * current app locale:
     * - fa → Persian calendar + Persian digits, long style
     * - everything else → Gregorian, medium style
     */
    fun formatDate(context: Context, date: Date): String {
        return try {
            val locales = androidx.appcompat.app.AppCompatDelegate.getApplicationLocales()
            val isPersian = locales.toLanguageTags().startsWith("fa") ||
                    context.resources.configuration.locales[0]?.language == "fa"
            if (isPersian && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                val uLocale = android.icu.util.ULocale("fa_IR@calendar=persian")
                val cal = android.icu.util.Calendar.getInstance(uLocale)
                cal.time = date
                // Example: ۱۶ شهریور ۱۴۰۵
                val df = android.icu.text.DateFormat.getDateInstance(
                    android.icu.text.DateFormat.LONG,
                    uLocale,
                )
                df.format(cal)
            } else {
                java.text.DateFormat.getDateInstance(
                    java.text.DateFormat.MEDIUM,
                    context.resources.configuration.locales[0],
                ).format(date)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(date)
        }
    }

    /**
     * Format [date] including the time-of-day (used for the "last updated"
     * chip on the server list): Persian calendar for fa, else Gregorian.
     */
    fun formatDateTime(context: Context, date: Date): String {
        return try {
            val locales = androidx.appcompat.app.AppCompatDelegate.getApplicationLocales()
            val isPersian = locales.toLanguageTags().startsWith("fa") ||
                    context.resources.configuration.locales[0]?.language == "fa"
            if (isPersian && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                val uLocale = android.icu.util.ULocale("fa_IR@calendar=persian")
                val cal = android.icu.util.Calendar.getInstance(uLocale)
                cal.time = date
                // Example: ۱۶ شهریور ۱۴۰۵، ۱۴:۳۰
                val df = android.icu.text.DateFormat.getDateTimeInstance(
                    android.icu.text.DateFormat.LONG,
                    android.icu.text.DateFormat.SHORT,
                    uLocale,
                )
                df.format(cal)
            } else {
                java.text.DateFormat.getDateTimeInstance(
                    java.text.DateFormat.MEDIUM,
                    java.text.DateFormat.SHORT,
                    context.resources.configuration.locales[0],
                ).format(date)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            java.text.DateFormat.getDateTimeInstance(
                java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT,
            ).format(date)
        }
    }
}
