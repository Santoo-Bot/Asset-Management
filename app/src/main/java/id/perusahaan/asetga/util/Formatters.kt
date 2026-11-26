package id.perusahaan.asetga.util

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

/** Format angka & tanggal gaya Indonesia: Rp 1.250.000, 26 Nov 2026. */
object Fmt {
    val LOCALE_ID: Locale = Locale.forLanguageTag("id-ID")

    private val symbols = DecimalFormatSymbols(LOCALE_ID).apply {
        groupingSeparator = '.'
        decimalSeparator = ','
    }
    private val dateFmt = DateTimeFormatter.ofPattern("dd MMM yyyy", LOCALE_ID)
    private val numericDateFmt = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    private val dateTimeFmt = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm", LOCALE_ID)

    private fun intFormat() = DecimalFormat("#,##0", symbols)
    private fun qtyFormat() = DecimalFormat("#,##0.##", symbols)
    private fun percentFormat() = DecimalFormat("#,##0.##", symbols)

    fun rupiah(value: Long): String = (if (value < 0) "-Rp " else "Rp ") + intFormat().format(abs(value))

    fun number(value: Long): String = intFormat().format(value)

    fun qty(value: Double): String = qtyFormat().format(value)

    fun percent(value: Double): String = percentFormat().format(value) + "%"

    fun date(value: LocalDate?): String = value?.format(dateFmt) ?: "-"

    /** dd/MM/yyyy, format yang dipakai template import Excel. */
    fun dateNumeric(value: LocalDate): String = value.format(numericDateFmt)

    fun dateTime(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        Instant.ofEpochMilli(epochMillis).atZone(zone).format(dateTimeFmt)

    /** Ubah teks masukan pengguna ("1.250.000", "Rp 1.250.000") menjadi Long; null bila tidak valid. */
    fun parseRupiah(text: String): Long? {
        val digits = text.filter { it.isDigit() }
        if (digits.isEmpty() || digits.length > 15) return null
        return digits.toLong()
    }

    /** Terima "12,5" maupun "12.5". */
    fun parseDecimal(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()

    /** Umur berjalan, mis. "2 th 3 bln". */
    fun age(years: Int, months: Int): String = when {
        years == 0 && months == 0 -> "< 1 bln"
        years == 0 -> "$months bln"
        months == 0 -> "$years th"
        else -> "$years th $months bln"
    }
}
