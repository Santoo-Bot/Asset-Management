package id.perusahaan.asetga.data.file

import android.content.Context
import android.content.Intent
import android.os.Build
import id.perusahaan.asetga.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.LocalDateTime

/**
 * Menyimpan rincian error bila aplikasi tertutup tiba-tiba (tanpa mengirim apa pun ke internet).
 * Saat aplikasi dibuka lagi, pengguna bisa membagikan laporan ini untuk perbaikan.
 */
object CrashReporter {
    private fun file(context: Context) = File(context.filesDir, "crash/last-crash.txt")

    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
                val f = file(appContext).apply { parentFile?.mkdirs() }
                f.writeText(
                    buildString {
                        appendLine("Laporan error Aset GA")
                        appendLine("Waktu: ${LocalDateTime.now()}")
                        appendLine("Versi aplikasi: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                        appendLine("Perangkat: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                        appendLine("Thread: ${thread.name}")
                        appendLine()
                        append(trace.take(60_000))
                    },
                )
            }
            previous?.uncaughtException(thread, error)
        }
    }

    fun pendingReport(context: Context): String? = file(context).takeIf { it.exists() }?.readText()

    fun clear(context: Context) {
        file(context).delete()
    }

    fun share(context: Context, report: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Laporan error Aset GA")
            putExtra(Intent.EXTRA_TEXT, report)
        }
        context.startActivity(Intent.createChooser(intent, "Bagikan laporan error").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
