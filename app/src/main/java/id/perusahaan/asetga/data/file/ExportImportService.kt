package id.perusahaan.asetga.data.file

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import id.perusahaan.asetga.data.local.dao.AssetDao
import id.perusahaan.asetga.data.local.dao.CategoryDao
import id.perusahaan.asetga.data.local.dao.DepartmentDao
import id.perusahaan.asetga.data.local.dao.LocationDao
import id.perusahaan.asetga.data.local.dao.ServiceDao
import id.perusahaan.asetga.data.local.dao.StockDao
import id.perusahaan.asetga.domain.calc.AssetAgeCalc
import id.perusahaan.asetga.domain.calc.Depreciation
import id.perusahaan.asetga.domain.calc.ServiceDueCalc
import id.perusahaan.asetga.domain.calc.WarrantyCalc
import id.perusahaan.asetga.domain.excel.AssetImportParser
import id.perusahaan.asetga.domain.excel.Cell
import id.perusahaan.asetga.domain.excel.CsvWriter
import id.perusahaan.asetga.domain.excel.ImportColumn
import id.perusahaan.asetga.domain.excel.ImportLookups
import id.perusahaan.asetga.domain.excel.ImportPreview
import id.perusahaan.asetga.domain.excel.ImportRow
import id.perusahaan.asetga.domain.excel.Sheet
import id.perusahaan.asetga.domain.excel.XlsxReader
import id.perusahaan.asetga.domain.excel.XlsxWriter
import id.perusahaan.asetga.domain.excel.num
import id.perusahaan.asetga.domain.excel.text
import id.perusahaan.asetga.domain.model.AppResult
import id.perusahaan.asetga.domain.model.AssetCondition
import id.perusahaan.asetga.domain.model.AssetStatus
import id.perusahaan.asetga.domain.model.AuditAction
import id.perusahaan.asetga.domain.model.AuditEntityType
import id.perusahaan.asetga.domain.model.StockTxnType
import id.perusahaan.asetga.domain.repository.AssetRepository
import id.perusahaan.asetga.domain.repository.AuditLogRepository
import id.perusahaan.asetga.domain.repository.SettingsRepository
import id.perusahaan.asetga.domain.session.SessionManager
import id.perusahaan.asetga.util.Fmt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Clock
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

enum class ExportKind(val label: String, val fileStem: String) {
    ASSETS("Register aset", "aset"),
    SERVICES("Riwayat servis", "servis"),
    STOCK("Stok & transaksi", "stok"),
}

enum class ExportFormat(val label: String, val extension: String, val mime: String) {
    XLSX("Excel (.xlsx)", "xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
    CSV("CSV (.csv)", "csv", "text/csv"),
}

data class ImportResult(val imported: Int, val failed: List<ImportRow>)

@Singleton
class ExportImportService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val assetDao: AssetDao,
    private val serviceDao: ServiceDao,
    private val stockDao: StockDao,
    private val categoryDao: CategoryDao,
    private val locationDao: LocationDao,
    private val departmentDao: DepartmentDao,
    private val assets: AssetRepository,
    private val settings: SettingsRepository,
    private val audit: AuditLogRepository,
    private val session: SessionManager,
    private val clock: Clock,
) {
    private val stamp get() = LocalDate.now(clock).format(DateTimeFormatter.BASIC_ISO_DATE)

    /** Membuat file di folder cache yang bisa dibagikan; dikembalikan untuk share sheet. */
    suspend fun export(kind: ExportKind, format: ExportFormat): File = withContext(Dispatchers.IO) {
        val sheets = when (kind) {
            ExportKind.ASSETS -> listOf(assetSheet())
            ExportKind.SERVICES -> listOf(serviceSheet())
            ExportKind.STOCK -> stockSheets()
        }
        val file = File(ShareHelper.sharedDir(context), "${kind.fileStem}-$stamp.${format.extension}")
        file.outputStream().use { out ->
            when (format) {
                ExportFormat.XLSX -> XlsxWriter.write(sheets, out)
                // CSV hanya satu lembar: lembar pertama.
                ExportFormat.CSV -> sheets.first().let { s -> CsvWriter.write(s.header, s.rows.map { r -> r.map(::cellText) }, out) }
            }
        }
        audit.log(AuditEntityType.SYSTEM, kind.name, AuditAction.UPDATE, "Export ${kind.label} (${format.extension.uppercase()})")
        file
    }

    private fun cellText(c: Cell): String = when (c) {
        is Cell.Text -> c.value
        is Cell.Number -> if (c.value == Math.rint(c.value)) c.value.toLong().toString() else c.value.toString().replace('.', ',')
    }

    private suspend fun assetSheet(): Sheet {
        val today = LocalDate.now(clock)
        val defaultResidual = settings.current().defaultResidualPercent
        val rows = assetDao.exportRows().map { r ->
            val a = r.asset
            val asOf = if (a.status == AssetStatus.DIHAPUS_DIJUAL) a.disposalDate ?: today else today
            val residual = r.categoryResidualPercent ?: defaultResidual
            val dep = Depreciation.calculate(a.acquisitionCost, a.acquisitionDate, r.usefulLifeYears, residual, asOf)
            val age = AssetAgeCalc.of(a.acquisitionDate, asOf)
            listOf(
                text(a.code), text(a.name), text(r.categoryName), text(a.brandModel), text(a.serialNumber),
                text(Fmt.date(a.acquisitionDate)), num(a.acquisitionCost), text(r.locationName), text(r.departmentName), text(a.picName),
                text(a.condition.label), text(a.status.label), text(a.warrantyUntil?.let(Fmt::date)),
                text(WarrantyCalc.status(a.warrantyUntil, today).label), text(Fmt.age(age.years, age.months)), num(r.usefulLifeYears),
                num(residual), num(dep.annualDepreciation), num(dep.accumulated), num(dep.bookValue), text(a.notes),
            )
        }
        return Sheet(
            "Aset",
            listOf(
                "Kode Aset", "Nama Aset", "Kategori", "Merk/Tipe", "No. Seri", "Tgl Perolehan", "Harga Perolehan", "Lokasi", "Departemen", "PIC",
                "Kondisi", "Status", "Garansi s/d", "Status Garansi", "Umur Berjalan", "Umur Ekonomis (th)", "Residu (%)",
                "Penyusutan/Tahun", "Akumulasi Penyusutan", "Nilai Buku", "Catatan",
            ),
            rows,
        )
    }

    private suspend fun serviceSheet(): Sheet {
        val today = LocalDate.now(clock)
        val rows = serviceDao.allRows().map { s ->
            listOf(
                text(Fmt.date(s.serviceDate)), text(s.assetCode), text(s.assetName), text(s.type.label), text(s.vendorName),
                num(s.cost), text(s.description), text(s.nextServiceDate?.let(Fmt::date)),
                text(s.nextServiceDate?.let { ServiceDueCalc.status(it, today).label }),
            )
        }
        return Sheet(
            "Servis",
            listOf("Tanggal", "Kode Aset", "Nama Aset", "Jenis", "Vendor", "Biaya", "Deskripsi", "Servis Berikutnya", "Status Jadwal"),
            rows,
        )
    }

    private suspend fun stockSheets(): List<Sheet> {
        val items = stockDao.activeItems()
        val byId = items.associateBy { it.id }
        val stock = Sheet(
            "Stok",
            listOf("Kode", "Barcode", "Nama Barang", "Satuan", "Lokasi Simpan", "Stok Awal", "Min. Stok", "Stok Akhir", "Status"),
            items.map {
                listOf(
                    text(it.code), text(it.barcode), text(it.name), text(it.unitName), text(it.storageLocationName),
                    num(it.initialStock), num(it.minStock), num(it.currentStock), text(if (it.currentStock <= it.minStock) "Reorder" else "Aman"),
                )
            },
        )
        val txns = Sheet(
            "Transaksi",
            listOf("Tanggal", "Kode Barang", "Nama Barang", "Jenis", "Jumlah", "Referensi", "Catatan", "Dicatat oleh"),
            stockDao.allTxns().map { t ->
                val item = byId[t.itemId]
                listOf(
                    text(Fmt.date(t.txnDate)), text(item?.code), text(item?.name), text(t.type.label),
                    num(if (t.type == StockTxnType.IN) t.qty else -t.qty), text(t.reference), text(t.note), text(t.createdBy),
                )
            },
        )
        return listOf(stock, txns)
    }

    /** Template import berisi contoh baris dan lembar petunjuk daftar nilai yang valid. */
    suspend fun importTemplate(): File = withContext(Dispatchers.IO) {
        val cats = categoryDao.observeAll(false).first()
        val locs = locationDao.observeAll(false).first()
        val deps = departmentDao.observeAll(false).first()
        val example = listOf(
            text(""), text("Laptop Dell Latitude 5440"), text(cats.firstOrNull()?.name ?: "Elektronik & IT"), text("Dell Latitude 5440"),
            text("SN123456"), text("15/01/2024"), num(15_000_000), text(locs.firstOrNull()?.name ?: "Kantor Pusat"),
            text(deps.firstOrNull()?.name.orEmpty()), text("Budi"), text("Baik"), text("Aktif"), text("15/01/2027"), text("Kosongkan Kode Aset untuk kode otomatis"),
        )
        val maxLen = maxOf(cats.size, locs.size, deps.size, AssetCondition.entries.size, AssetStatus.entries.size)
        val guide = (0 until maxLen).map { i ->
            listOf(
                text(cats.getOrNull(i)?.let { "${it.name} (${it.codePrefix})" }), text(locs.getOrNull(i)?.name), text(deps.getOrNull(i)?.name),
                text(AssetCondition.entries.getOrNull(i)?.label), text(AssetStatus.entries.getOrNull(i)?.label),
            )
        }
        val file = File(ShareHelper.sharedDir(context), "template-import-aset.xlsx")
        file.outputStream().use {
            XlsxWriter.write(
                listOf(
                    Sheet("Import Aset", ImportColumn.entries.map { c -> c.templateHeader }, listOf(example), ImportColumn.entries.map { 22 }),
                    Sheet("Petunjuk", listOf("Kategori", "Lokasi", "Departemen", "Kondisi", "Status"), guide, listOf(32, 28, 24, 16, 18)),
                ),
                it,
            )
        }
        file
    }

    suspend fun preview(uri: Uri): ImportPreview = withContext(Dispatchers.IO) {
        val sheet = runCatching {
            context.contentResolver.openInputStream(uri)?.use { XlsxReader.readFirstSheet(it) }
        }.getOrElse { return@withContext ImportPreview(emptyList(), "File tidak dapat dibaca: ${it.message ?: "format bukan .xlsx"}") }
            ?: return@withContext ImportPreview(emptyList(), "File tidak dapat dibuka")
        if (sheet.size > MAX_IMPORT_ROWS + 1) return@withContext ImportPreview(emptyList(), "Maksimal $MAX_IMPORT_ROWS baris per import")

        val cats = categoryDao.observeAll(false).first()
        val lookups = ImportLookups(
            categories = cats.associate { it.name.lowercase() to it.id } + cats.associate { it.codePrefix.lowercase() to it.id },
            locations = locationDao.observeAll(false).first().associate { it.name.lowercase() to it.id },
            departments = departmentDao.observeAll(false).first().associate { it.name.lowercase() to it.id },
            existingCodes = assetDao.allCodes().map { it.uppercase() }.toSet(),
        )
        AssetImportParser.parse(sheet, lookups, LocalDate.now(clock))
    }

    /** Simpan baris yang valid; baris yang gagal saat disimpan ikut dilaporkan. */
    suspend fun commit(preview: ImportPreview, onProgress: (Int, Int) -> Unit = { _, _ -> }): AppResult<ImportResult> {
        if (session.currentUser?.role?.canEditData != true) return AppResult.Failure("Anda tidak memiliki akses untuk import")
        val valid = preview.valid
        val failed = mutableListOf<ImportRow>()
        var imported = 0
        valid.forEachIndexed { i, row ->
            when (val r = assets.save(row.input!!)) {
                is AppResult.Success -> imported++
                is AppResult.Failure -> failed += row.copy(errors = listOf(r.message) + r.fieldErrors.values)
            }
            onProgress(i + 1, valid.size)
        }
        audit.log(AuditEntityType.ASSET, "import", AuditAction.IMPORT, "Import aset dari Excel: $imported berhasil, ${failed.size + preview.invalid.size} gagal")
        return AppResult.Success(ImportResult(imported, preview.invalid + failed))
    }

    companion object {
        const val MAX_IMPORT_ROWS = 10_000
    }
}
