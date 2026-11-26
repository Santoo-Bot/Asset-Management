package id.perusahaan.asetga.data.file

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import id.perusahaan.asetga.data.local.dao.InsuranceDao
import id.perusahaan.asetga.domain.excel.InsuranceImportTemplate
import id.perusahaan.asetga.domain.excel.Sheet
import id.perusahaan.asetga.domain.excel.XlsxReader
import id.perusahaan.asetga.domain.excel.XlsxWriter
import id.perusahaan.asetga.domain.excel.num
import id.perusahaan.asetga.domain.excel.text
import id.perusahaan.asetga.domain.model.InsurancePolicy
import id.perusahaan.asetga.util.Fmt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InsuranceFileService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: InsuranceDao,
) {
    /** Template upload: satu kolom kode (kode aset atau kode QR) + nilai pertanggungan opsional. */
    suspend fun template(): File = withContext(Dispatchers.IO) {
        val file = File(ShareHelper.sharedDir(context), "template-aset-asuransi.xlsx")
        file.outputStream().use {
            XlsxWriter.write(
                listOf(
                    Sheet(
                        "Aset Diasuransikan", InsuranceImportTemplate.headers,
                        listOf(listOf(text("IT-2024-001"), num(15_000_000)), listOf(text("QR-STIKER-0007"), text(""))),
                        listOf(28, 22),
                    ),
                    Sheet(
                        "Petunjuk", listOf("Petunjuk"),
                        listOf(
                            "Isi satu kode per baris: kode aset ATAU kode QR (keduanya dikenali).",
                            "Nilai Pertanggungan boleh dikosongkan; isi angka tanpa titik atau dengan format 15.000.000.",
                            "Aset yang sudah ada di polis tidak digandakan; nilai pertanggungannya diperbarui bila diisi.",
                            "Aset berstatus Write Off atau kode yang tidak dikenal akan ditampilkan sebagai baris bermasalah.",
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

    /** Export polis + daftar aset yang ditanggung. Lembar pertama memakai format template sehingga bisa diunggah ulang. */
    suspend fun exportPolicy(policy: InsurancePolicy): File = withContext(Dispatchers.IO) {
        val items = dao.items(policy.id)
        val safe = policy.policyNumber.replace(Regex("[^A-Za-z0-9]+"), "-")
        val file = File(ShareHelper.sharedDir(context), "polis-$safe.xlsx")
        file.outputStream().use {
            XlsxWriter.write(
                listOf(
                    Sheet(
                        "Aset Diasuransikan",
                        InsuranceImportTemplate.headers + listOf("Nama Aset", "Kode Aset", "Kode QR", "Kategori", "Lokasi", "Status", "Harga Perolehan"),
                        items.map { a ->
                            listOf(
                                text(a.code), a.sumInsured?.let { v -> num(v) } ?: text(""), text(a.name), text(a.code), text(a.qrCode ?: a.code),
                                text(a.categoryName), text(a.locationName), text(a.status.label), num(a.acquisitionCost),
                            )
                        },
                    ),
                    Sheet(
                        "Polis", listOf("Keterangan", "Nilai"),
                        listOf(
                            "Nomor polis" to policy.policyNumber,
                            "Tipe" to policy.typeName,
                            "Perusahaan" to policy.insurerName,
                            "Mulai" to Fmt.date(policy.startDate),
                            "Berakhir" to Fmt.date(policy.endDate),
                            "Status" to policy.status.label,
                            "Premi" to (policy.premium?.let(Fmt::rupiah) ?: "-"),
                            "Nilai pertanggungan polis" to (policy.sumInsured?.let(Fmt::rupiah) ?: "-"),
                            "Jumlah aset" to policy.assetCount.toString(),
                            "Total nilai pertanggungan aset" to Fmt.rupiah(policy.itemsSumInsured),
                            "Total harga perolehan aset" to Fmt.rupiah(policy.itemsAcquisitionCost),
                            "Catatan" to (policy.notes ?: "-"),
                        ).map { (k, v) -> listOf(text(k), text(v)) },
                        listOf(32, 40),
                    ),
                ),
                it,
            )
        }
        file
    }
}
