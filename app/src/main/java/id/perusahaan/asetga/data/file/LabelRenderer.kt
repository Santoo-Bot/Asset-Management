package id.perusahaan.asetga.data.file

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.io.File

data class LabelData(val code: String, val name: String, val location: String)

/**
 * Menggambar label QR aset. Satu lembar A4 berisi grid [COLUMNS] × [ROWS] label (24 label),
 * QR digambar sebagai vektor sehingga tajam saat dicetak. Isi QR = kode aset.
 */
object LabelRenderer {
    const val PAGE_W = 595 // A4 dalam point (1/72 inci)
    const val PAGE_H = 842
    const val COLUMNS = 3
    const val ROWS = 8
    const val PER_PAGE = COLUMNS * ROWS
    private const val MARGIN = 18f
    private val CELL_W = (PAGE_W - 2 * MARGIN) / COLUMNS
    private val CELL_H = (PAGE_H - 2 * MARGIN) / ROWS

    fun qrMatrix(content: String): BitMatrix = QRCodeWriter().encode(
        content, BarcodeFormat.QR_CODE, 0, 0,
        mapOf(EncodeHintType.MARGIN to 0, EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.CHARACTER_SET to "UTF-8"),
    )

    private fun drawQr(canvas: Canvas, content: String, left: Float, top: Float, size: Float) {
        val m = qrMatrix(content)
        val module = size / m.width
        val paint = Paint().apply { color = Color.BLACK; isAntiAlias = false; style = Paint.Style.FILL }
        for (y in 0 until m.height) {
            var x = 0
            while (x < m.width) {
                if (m[x, y]) {
                    val start = x
                    while (x < m.width && m[x, y]) x++
                    canvas.drawRect(left + start * module, top + y * module, left + x * module, top + (y + 1) * module, paint)
                } else {
                    x++
                }
            }
        }
    }

    /** Gambar satu label di area (left, top, CELL_W, CELL_H). */
    private fun drawLabel(canvas: Canvas, label: LabelData, company: String, left: Float, top: Float) {
        val pad = 8f
        val border = Paint().apply { color = Color.rgb(200, 200, 200); style = Paint.Style.STROKE; strokeWidth = 0.5f }
        canvas.drawRect(left, top, left + CELL_W, top + CELL_H, border)

        val qrSize = CELL_H - 2 * pad
        drawQr(canvas, label.code, left + pad, top + pad, qrSize)

        val textLeft = left + pad + qrSize + 8f
        val textWidth = (left + CELL_W - pad - textLeft).toInt().coerceAtLeast(20)
        var y = top + pad

        if (company.isNotBlank()) {
            val p = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 6.5f; color = Color.rgb(0, 96, 175) }
            val text = TextUtils.ellipsize("Milik $company", p, textWidth.toFloat(), TextUtils.TruncateAt.END).toString()
            canvas.drawText(text, textLeft, y + 7f, p)
            y += 11f
        }
        val codePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10f; typeface = Typeface.DEFAULT_BOLD; color = Color.BLACK }
        canvas.drawText(TextUtils.ellipsize(label.code, codePaint, textWidth.toFloat(), TextUtils.TruncateAt.END).toString(), textLeft, y + 10f, codePaint)
        y += 15f

        val namePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 8f; color = Color.rgb(30, 30, 30) }
        val layout = StaticLayout.Builder.obtain(label.name, 0, label.name.length, namePaint, textWidth)
            .setMaxLines(3).setEllipsize(TextUtils.TruncateAt.END).setAlignment(Layout.Alignment.ALIGN_NORMAL).build()
        canvas.save()
        canvas.translate(textLeft, y)
        layout.draw(canvas)
        canvas.restore()

        val locPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 6.5f; color = Color.rgb(90, 90, 90) }
        canvas.drawText(
            TextUtils.ellipsize(label.location, locPaint, textWidth.toFloat(), TextUtils.TruncateAt.END).toString(),
            textLeft, top + CELL_H - pad, locPaint,
        )
    }

    private fun drawPage(canvas: Canvas, labels: List<LabelData>, company: String) {
        canvas.drawColor(Color.WHITE)
        labels.forEachIndexed { i, label ->
            val col = i % COLUMNS
            val row = i / COLUMNS
            drawLabel(canvas, label, company, MARGIN + col * CELL_W, MARGIN + row * CELL_H)
        }
    }

    fun pageCount(total: Int): Int = (total + PER_PAGE - 1) / PER_PAGE

    fun writePdf(labels: List<LabelData>, company: String, out: File) {
        val doc = PdfDocument()
        try {
            labels.chunked(PER_PAGE).forEachIndexed { index, chunk ->
                val page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, index + 1).create())
                drawPage(page.canvas, chunk, company)
                doc.finishPage(page)
            }
            out.outputStream().use { doc.writeTo(it) }
        } finally {
            doc.close()
        }
    }

    /** Render satu halaman ke bitmap (untuk pratinjau / PNG). [scale] 2 ≈ 144 dpi. */
    fun renderPage(labels: List<LabelData>, company: String, page: Int, scale: Float): Bitmap {
        val chunk = labels.chunked(PER_PAGE).getOrElse(page) { emptyList() }
        val bmp = Bitmap.createBitmap((PAGE_W * scale).toInt(), (PAGE_H * scale).toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.scale(scale, scale)
        drawPage(canvas, chunk, company)
        return bmp
    }

    /** Render satu label saja (PNG untuk ditempel/cetak printer label). */
    fun renderSingle(label: LabelData, company: String, scale: Float = 4f): Bitmap {
        val bmp = Bitmap.createBitmap((CELL_W * scale).toInt(), (CELL_H * scale).toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.scale(scale, scale)
        canvas.drawColor(Color.WHITE)
        drawLabel(canvas, label, company, 0f, 0f)
        return bmp
    }
}
