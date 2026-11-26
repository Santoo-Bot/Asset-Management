package id.perusahaan.asetga.data.file

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import id.perusahaan.asetga.domain.excel.OpnameImportTemplate
import id.perusahaan.asetga.domain.excel.Sheet
import id.perusahaan.asetga.domain.excel.XlsxReader
import id.perusahaan.asetga.domain.excel.XlsxWriter
import id.perusahaan.asetga.domain.excel.num
import id.perusahaan.asetga.domain.excel.text
import id.perusahaan.asetga.domain.model.AuditResult
import id.perusahaan.asetga.domain.model.OpnameItem
import id.perusahaan.asetga.domain.model.OpnameSession
import id.perusahaan.asetga.util.Fmt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OpnameFileService @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    suspend fun template(): File = withContext(Dispatchers.IO) {
        val file = File(ShareHelper.sharedDir(context), "template-aset-opname.xlsx")
        file.outputStream().use {
            XlsxWriter.write(
                listOf(
                    Sheet("Aset Opname", OpnameImportTemplate.headers, listOf(listOf(text("IT-2024-001")), listOf(text("QR-STIKER-0007"))), listOf(28)),
                    Sheet(
                        "Petunjuk", listOf("Petunjuk"),
                        listOf(
                            "Isi satu kode per baris: kode aset ATAU kode QR (keduanya dikenali).",
                            "File export aset juga bisa langsung diunggah; kolom Kode Aset dipakai, kolom lain diabaikan.",
                            "Aset yang sudah ada di sesi tidak digandakan.",
                            "Aset berstatus Write Off atau kode yang tidak dikenal ditampilkan sebagai baris bermasalah.",
                            "Hapus baris contoh sebelum mengunggah.",
                        ).map { listOf(text(it)) },
                        listOf(100),
                    ),
                ),
                it,
            )
        }
        file
    }

    suspend fun readSheet(uri: Uri): List<List<String>> = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(uri)?.use { XlsxReader.readFirstSheet(it) } ?: throw IllegalArgumentException("File tidak dapat dibuka")
    }

    /** Laporan hasil opname: ringkasan + seluruh hasil + daftar tindak lanjut (tidak ditemukan, rusak, di luar daftar). */
    suspend fun report(s: OpnameSession, items: List<OpnameItem>): File = withContext(Dispatchers.IO) {
        val safe = s.name.replace(Regex("[^A-Za-z0-9]+"), "-").trim('-').ifEmpty { "opname" }
        val file = File(ShareHelper.sharedDir(context), "laporan-opname-$safe-${s.date}.xlsx")
        val header = listOf(
            "No", "Kode Aset", "Kode QR", "Nama Aset", "Kategori", "Lokasi Tercatat", "Pengguna / Detail Lokasi",
            "Hasil", "Kondisi Fisik", "Kondisi di Sistem", "Catatan", "Waktu Cek",
        )
        val widths = listOf(6, 18, 18, 30, 16, 20, 24, 16, 14, 16, 30, 18)
        fun rows(list: List<OpnameItem>) = list.mapIndexed { i, it ->
            listOf(
                num(i + 1), text(if (it.assetId != null) it.code else ""), text(it.qrCode ?: if (it.assetId == null) it.code else ""),
                text(it.name ?: "Kode tidak dikenal"), text(it.categoryName), text(it.locationName), text(it.userLocation),
                text(resultLabel(it.result, s.isFinished)), text(it.condition?.label), text(it.systemCondition?.label), text(it.note),
                text(it.checkedAt?.let(Fmt::dateTime)),
            )
        }
        val notFound = items.filter { it.result == AuditResult.TIDAK_DITEMUKAN }
        val damaged = items.filter { it.result == AuditResult.DITEMUKAN && it.condition == id.perusahaan.asetga.domain.model.AssetCondition.RUSAK }
        val outside = items.filter { it.result == AuditResult.TAK_TERDAFTAR }
        file.outputStream().use { out ->
            XlsxWriter.write(
                listOf(
                    Sheet(
                        "Ringkasan", listOf("Keterangan", "Nilai"),
                        listOf(
                            "Nama sesi" to s.name,
                            "Tanggal opname" to Fmt.date(s.date),
                            "Dibuat oleh" to (s.startedBy ?: "-"),
                            "Status" to if (s.isFinished) "Selesai ${Fmt.dateTime(s.finishedAt!!)}" else "Berjalan (laporan sementara)",
                            "Jumlah aset diperiksa" to s.total.toString(),
                            "Ditemukan" to "${s.found} (${s.foundPercent}%)",
                            "  · kondisi Baik" to (s.found - s.damaged).toString(),
                            "  · kondisi Rusak" to s.damaged.toString(),
                            (if (s.isFinished) "Tidak ditemukan" else "Belum dicek") to s.notFound.toString(),
                            "Di luar daftar (hasil scan)" to s.outside.toString(),
                            "Catatan" to (s.note ?: "-"),
                            "Dibuat" to Fmt.dateTime(System.currentTimeMillis()),
                        ).map { (k, v) -> listOf(text(k), text(v)) },
                        listOf(32, 48),
                    ),
                    Sheet("Hasil Opname", header, rows(items), widths),
                    Sheet(if (s.isFinished) "Tidak Ditemukan" else "Belum Dicek", header, rows(notFound), widths),
                    Sheet("Kondisi Rusak", header, rows(damaged), widths),
                    Sheet("Di Luar Daftar", header, rows(outside), widths),
                ),
                out,
            )
        }
        file
    }

    companion object {
        fun resultLabel(r: AuditResult, finished: Boolean): String = when (r) {
            AuditResult.DITEMUKAN -> "Ditemukan"
            AuditResult.TIDAK_DITEMUKAN -> if (finished) "Tidak ditemukan" else "Belum dicek"
            AuditResult.TAK_TERDAFTAR -> "Di luar daftar"
        }
    }
}
