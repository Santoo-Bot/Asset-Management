package id.perusahaan.asetga.data.file

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import id.perusahaan.asetga.data.local.dao.AssetDao
import id.perusahaan.asetga.data.local.dao.AssetDetailRow
import id.perusahaan.asetga.data.local.dao.CategoryDao
import id.perusahaan.asetga.data.local.dao.DepartmentDao
import id.perusahaan.asetga.data.local.dao.DivisionDao
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
import id.perusahaan.asetga.domain.excel.ListValidation
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
    private val divisionDao: DivisionDao,
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

    /** Dropdown Kondisi & Status, dipakai di template dan export aset. */
    private fun assetValidations() = listOf(
        ListValidation(ImportColumn.CONDITION.ordinal, AssetCondition.entries.map { it.label }),
        ListValidation(ImportColumn.STATUS.ordinal, AssetStatus.entries.map { it.label }),
    )

    /** Nilai kolom template untuk satu aset (export bisa diimport kembali). */
    private fun templateCells(r: AssetDetailRow): Map<ImportColumn, Cell> {
        val a = r.asset
        return mapOf(
            ImportColumn.CODE to text(a.code),
            ImportColumn.QR to text(a.qrCode ?: a.code),
            ImportColumn.NAME to text(a.name),
            ImportColumn.CATEGORY to text(r.categoryName),
            ImportColumn.SERIAL to text(a.serialNumber),
            ImportColumn.ACQ_DATE to text(Fmt.dateNumeric(a.acquisitionDate)),
            ImportColumn.COST to num(a.acquisitionCost),
            ImportColumn.LOCATION to text(r.locationName),
            ImportColumn.DIVISION to text(r.divisionName),
            ImportColumn.DEPARTMENT to text(r.departmentName),
            ImportColumn.CONDITION to text(a.condition.label),
            ImportColumn.STATUS to text(a.status.label),
            ImportColumn.WARRANTY to text(a.warrantyUntil?.let(Fmt::dateNumeric)),
            ImportColumn.NOTES to text(a.notes),
            ImportColumn.USER_LOCATION to text(a.userLocationDetail),
            ImportColumn.PR to text(a.prNumber),
            ImportColumn.PO to text(a.poNumber),
            ImportColumn.GR to text(a.grNumber),
            ImportColumn.REQUESTER to text(a.requester),
            ImportColumn.MEMO_NO to text(a.memoNumber),
            ImportColumn.MEMO_DATE to text(a.memoDate?.let(Fmt::dateNumeric)),
            ImportColumn.FOLLOW_UP to text(a.followUp),
            ImportColumn.HANDOVER to text(a.handoverDoc),
            ImportColumn.ACCESSORIES to text(a.accessories),
        )
    }

    private suspend fun assetSheet(): Sheet {
        val today = LocalDate.now(clock)
        val defaultResidual = settings.current().defaultResidualPercent
        val rows = assetDao.exportRows().map { r ->
            val a = r.asset
            val asOf = if (a.status == AssetStatus.WRITE_OFF) a.disposalDate ?: today else today
            val residual = r.categoryResidualPercent ?: defaultResidual
            val dep = Depreciation.calculate(a.acquisitionCost, a.acquisitionDate, r.usefulLifeYears, residual, asOf)
            val age = AssetAgeCalc.of(a.acquisitionDate, asOf)
            val cells = templateCells(r)
            ImportColumn.entries.map { cells[it] ?: text("") } + listOf(
                text(WarrantyCalc.status(a.warrantyUntil, today).label), text(Fmt.age(age.years, age.months)), num(r.usefulLifeYears),
                num(residual), num(dep.annualDepreciation), num(dep.accumulated), num(dep.bookValue),
                text(a.brandModel), text(a.picName),
            )
        }
        return Sheet(
            "Aset",
            ImportColumn.entries.map { it.templateHeader } + listOf(
                "Status Garansi", "Umur Berjalan", "Umur Ekonomis (th)", "Residu (%)",
                "Penyusutan/Tahun", "Akumulasi Penyusutan", "Nilai Buku", "Merk/Tipe (lama)", "PIC (lama)",
            ),
            rows,
            validations = assetValidations(),
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
        val divs = divisionDao.observeAll(false).first()
        val divName = divs.associate { it.id to it.name }
        val sample: Map<ImportColumn, Cell> = mapOf(
            ImportColumn.CODE to text("${cats.firstOrNull()?.codePrefix ?: "IT"}-${LocalDate.now(clock).year}-001"),
            ImportColumn.QR to text("${cats.firstOrNull()?.codePrefix ?: "IT"}-${LocalDate.now(clock).year}-001"),
            ImportColumn.NAME to text("Laptop Dell Latitude 5440"),
            ImportColumn.CATEGORY to text(cats.firstOrNull()?.name ?: "Elektronik & IT"),
            ImportColumn.SERIAL to text("SN123456"),
            ImportColumn.ACQ_DATE to text("15/01/2024"),
            ImportColumn.COST to num(15_000_000),
            ImportColumn.LOCATION to text(locs.firstOrNull()?.name ?: "Kantor Pusat"),
            ImportColumn.DEPARTMENT to text(deps.firstOrNull()?.name.orEmpty()),
            ImportColumn.CONDITION to text(AssetCondition.BAIK.label),
            ImportColumn.STATUS to text(AssetStatus.DIGUNAKAN.label),
            ImportColumn.WARRANTY to text("15/01/2027"),
            ImportColumn.NOTES to text("Contoh baris, hapus sebelum import. Kode QR boleh sama dengan Kode Aset."),
            ImportColumn.USER_LOCATION to text("Budi · Ruang 301"),
            ImportColumn.DIVISION to text(divs.firstOrNull()?.name.orEmpty()),
            ImportColumn.PR to text("PR-2024-0012"),
            ImportColumn.PO to text("PO-2024-0034"),
            ImportColumn.GR to text("GR-2024-0056"),
            ImportColumn.REQUESTER to text("Citra"),
            ImportColumn.MEMO_NO to text("MEMO/GA/2024/007"),
            ImportColumn.MEMO_DATE to text("10/01/2024"),
            ImportColumn.FOLLOW_UP to text("Diserahkan ke pengguna"),
            ImportColumn.HANDOVER to text("BAST/GA/2024/015"),
            ImportColumn.ACCESSORIES to text("Charger, Tas laptop"),
        )
        val example = ImportColumn.entries.map { sample[it] ?: text("") }
        val maxLen = maxOf(cats.size, locs.size, deps.size, divs.size, AssetCondition.entries.size, AssetStatus.entries.size)
        val guide = (0 until maxLen).map { i ->
            listOf(
                text(cats.getOrNull(i)?.let { "${it.name} (${it.codePrefix})" }), text(locs.getOrNull(i)?.name), text(divs.getOrNull(i)?.name),
                text(deps.getOrNull(i)?.let { d -> d.name + (d.divisionId?.let(divName::get)?.let { " — Divisi $it" } ?: "") }),
                text(AssetCondition.entries.getOrNull(i)?.label), text(AssetStatus.entries.getOrNull(i)?.label),
            )
        }
        val file = File(ShareHelper.sharedDir(context), "template-import-aset.xlsx")
        file.outputStream().use {
            XlsxWriter.write(
                listOf(
                    Sheet(
                        "Import Aset", ImportColumn.entries.map { c -> c.templateHeader }, listOf(example),
                        ImportColumn.entries.map { c -> if (c == ImportColumn.STATUS) 34 else 22 },
                        validations = assetValidations(),
                    ),
                    Sheet("Petunjuk", listOf("Kategori", "Lokasi", "Divisi", "Departemen (Divisi induk)", "Kondisi", "Status"), guide, listOf(32, 28, 24, 40, 16, 18)),
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
            divisions = divisionDao.observeAll(false).first().associate { it.name.lowercase() to it.id },
            departmentDivision = departmentDao.observeAll(false).first().mapNotNull { d -> d.divisionId?.let { d.id to it } }.toMap(),
            existingCodes = (assetDao.allCodes() + assetDao.allQrCodes()).map { it.uppercase() }.toSet(),
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
