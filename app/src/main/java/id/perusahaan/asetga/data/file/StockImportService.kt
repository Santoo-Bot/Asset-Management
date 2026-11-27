package id.perusahaan.asetga.data.file

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import id.perusahaan.asetga.data.local.dao.LocationDao
import id.perusahaan.asetga.data.local.dao.StockDao
import id.perusahaan.asetga.data.local.dao.UnitDao
import id.perusahaan.asetga.domain.excel.Sheet
import id.perusahaan.asetga.domain.excel.StockImportTemplate
import id.perusahaan.asetga.domain.excel.XlsxReader
import id.perusahaan.asetga.domain.excel.XlsxWriter
import id.perusahaan.asetga.domain.excel.num
import id.perusahaan.asetga.domain.excel.text
import id.perusahaan.asetga.domain.model.AppResult
import id.perusahaan.asetga.domain.model.AuditAction
import id.perusahaan.asetga.domain.model.AuditEntityType
import id.perusahaan.asetga.domain.repository.AuditLogRepository
import id.perusahaan.asetga.domain.repository.StockRepository
import id.perusahaan.asetga.domain.session.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class StockImportResult(val imported: Int, val failed: List<StockImportTemplate.Row>)

/** Import barang habis pakai dari Excel: hanya barang baru; kode atau barcode yang sudah ada ditolak. */
@Singleton
class StockImportService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val stockDao: StockDao,
    private val unitDao: UnitDao,
    private val locationDao: LocationDao,
    private val stock: StockRepository,
    private val audit: AuditLogRepository,
    private val session: SessionManager,
) {
    suspend fun template(): File = withContext(Dispatchers.IO) {
        val units = unitDao.observeAll(false).first()
        val locs = locationDao.observeAll(false).first()
        val example = listOf(
            text("ATK-001"), text("8991234567890"), text("Kertas A4 80 gram"), text(units.firstOrNull()?.name ?: "Rim"),
            text(locs.firstOrNull()?.name.orEmpty()), num(20), num(5), text("Contoh baris, hapus sebelum import"),
        )
        val guide = (0 until maxOf(units.size, locs.size)).map { i -> listOf(text(units.getOrNull(i)?.name), text(locs.getOrNull(i)?.name)) }
        val file = File(ShareHelper.sharedDir(context), "template-import-barang.xlsx")
        file.outputStream().use {
            XlsxWriter.write(
                listOf(
                    Sheet("Import Barang", StockImportTemplate.headers, listOf(example), listOf(18, 22, 32, 14, 24, 12, 12, 34)),
                    Sheet("Petunjuk", listOf("Satuan", "Lokasi Simpan"), guide, listOf(24, 30)),
                ),
                it,
            )
        }
        file
    }

    suspend fun preview(uri: Uri): StockImportTemplate.Preview = withContext(Dispatchers.IO) {
        val sheet = runCatching {
            context.contentResolver.openInputStream(uri)?.use { XlsxReader.readFirstSheet(it) }
        }.getOrElse { return@withContext StockImportTemplate.Preview(emptyList(), "File tidak dapat dibaca: ${it.message ?: "format bukan .xlsx"}") }
            ?: return@withContext StockImportTemplate.Preview(emptyList(), "File tidak dapat dibuka")
        if (sheet.size > MAX_IMPORT_ROWS + 1) return@withContext StockImportTemplate.Preview(emptyList(), "Maksimal $MAX_IMPORT_ROWS baris per import")
        val items = stockDao.activeItems()
        StockImportTemplate.parse(
            sheet,
            StockImportTemplate.Lookups(
                units = unitDao.observeAll(false).first().associate { it.name.lowercase() to it.id },
                locations = locationDao.observeAll(false).first().associate { it.name.lowercase() to it.id },
                existingCodes = items.map { it.code.uppercase() }.toSet(),
                existingBarcodes = items.mapNotNull { it.barcode?.uppercase() }.toSet(),
            ),
        )
    }

    suspend fun commit(preview: StockImportTemplate.Preview, onProgress: (Int, Int) -> Unit = { _, _ -> }): AppResult<StockImportResult> {
        if (session.currentUser?.role?.canEditData != true) return AppResult.Failure("Anda tidak memiliki akses untuk import")
        val valid = preview.valid
        val failed = mutableListOf<StockImportTemplate.Row>()
        var imported = 0
        valid.forEachIndexed { i, row ->
            when (val r = stock.saveItem(row.input!!)) {
                is AppResult.Success -> imported++
                is AppResult.Failure -> failed += row.copy(input = null, errors = listOf(r.message) + r.fieldErrors.values)
            }
            onProgress(i + 1, valid.size)
        }
        val failedCount = failed.size + preview.invalid.size
        audit.log(AuditEntityType.ITEM, "import", AuditAction.IMPORT, "Import barang dari Excel: $imported berhasil, $failedCount gagal")
        return AppResult.Success(StockImportResult(imported, preview.invalid + failed))
    }

    companion object {
        const val MAX_IMPORT_ROWS = 10_000
    }
}
