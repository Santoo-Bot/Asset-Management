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

/** [qr] = isi QR (kode QR khusus atau kode aset); dicetak sebagai simbol QR dan teks. */
data class LabelData(val code: String, val category: String, val qr: String = code)

/**
 * Label aset ukuran 50 × 20 mm: simbol QR, teks kode QR, dan kategori.
 * Lembar A4 berisi grid [COLUMNS] × [ROWS] label; QR digambar sebagai vektor sehingga tajam saat dicetak.
 * PNG satu label berukuran persis 50 × 20 mm (cocok untuk printer label).
 */
object LabelRenderer {
    const val PAGE_W = 595 // A4 dalam point (1/72 inci)
    const val PAGE_H = 842
    private const val MM = 72f / 25.4f
    const val LABEL_W_MM = 50
    const val LABEL_H_MM = 20
    private val CELL_W = LABEL_W_MM * MM
    private val CELL_H = LABEL_H_MM * MM
    const val COLUMNS = 4
    const val ROWS = 14
    const val PER_PAGE = COLUMNS * ROWS
    private val MARGIN_X = (PAGE_W - COLUMNS * CELL_W) / 2
    private val MARGIN_Y = (PAGE_H - ROWS * CELL_H) / 2

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

    private fun layout(text: String, paint: TextPaint, width: Int, maxLines: Int): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setMaxLines(maxLines).setEllipsize(TextUtils.TruncateAt.END).setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(false).build()

    /** Gambar satu label 50 × 20 mm di (left, top). */
    private fun drawLabel(canvas: Canvas, label: LabelData, left: Float, top: Float, border: Boolean = true) {
        val pad = 1.5f * MM
        if (border) {
            val p = Paint().apply { color = Color.rgb(210, 210, 210); style = Paint.Style.STROKE; strokeWidth = 0.4f }
            canvas.drawRect(left, top, left + CELL_W, top + CELL_H, p)
        }
        val qrSize = CELL_H - 2 * pad
        drawQr(canvas, label.qr, left + pad, top + pad, qrSize)

        val textLeft = left + pad + qrSize + 1.5f * MM
        val textWidth = (left + CELL_W - pad - textLeft).toInt().coerceAtLeast(20)

        // Kode QR: tebal, diperkecil bila terlalu panjang agar tetap terbaca utuh (maks. 2 baris).
        val codePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; color = Color.BLACK }
        var size = 9f
        codePaint.textSize = size
        while (size > 6f && codePaint.measureText(label.qr) > textWidth * 2) { size -= 0.5f; codePaint.textSize = size }
        val code = layout(label.qr, codePaint, textWidth, 2)
        val catPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 7f; color = Color.rgb(60, 60, 60) }
        val cat = layout(label.category, catPaint, textWidth, 2)

        val gap = 1.5f
        val blockH = code.height + gap + cat.height
        var y = top + (CELL_H - blockH) / 2
        canvas.save(); canvas.translate(textLeft, y); code.draw(canvas); canvas.restore()
        y += code.height + gap
        canvas.save(); canvas.translate(textLeft, y); cat.draw(canvas); canvas.restore()
    }

    private fun drawPage(canvas: Canvas, labels: List<LabelData>) {
        canvas.drawColor(Color.WHITE)
        labels.forEachIndexed { i, label ->
            val col = i % COLUMNS
            val row = i / COLUMNS
            drawLabel(canvas, label, MARGIN_X + col * CELL_W, MARGIN_Y + row * CELL_H)
        }
    }

    fun pageCount(total: Int): Int = (total + PER_PAGE - 1) / PER_PAGE

    fun writePdf(labels: List<LabelData>, out: File) {
        val doc = PdfDocument()
        try {
            labels.chunked(PER_PAGE).forEachIndexed { index, chunk ->
                val page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, index + 1).create())
                drawPage(page.canvas, chunk)
                doc.finishPage(page)
            }
            out.outputStream().use { doc.writeTo(it) }
        } finally {
            doc.close()
        }
    }

    /** Render satu halaman ke bitmap (untuk pratinjau / PNG). [scale] 2 ≈ 144 dpi. */
    fun renderPage(labels: List<LabelData>, page: Int, scale: Float): Bitmap {
        val chunk = labels.chunked(PER_PAGE).getOrElse(page) { emptyList() }
        val bmp = Bitmap.createBitmap((PAGE_W * scale).toInt(), (PAGE_H * scale).toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.scale(scale, scale)
        drawPage(canvas, chunk)
        return bmp
    }

    /** Render satu label saja, 50 × 20 mm (PNG untuk printer label). [scale] 4 ≈ 288 dpi. */
    fun renderSingle(label: LabelData, scale: Float = 4f): Bitmap {
        val bmp = Bitmap.createBitmap((CELL_W * scale).toInt(), (CELL_H * scale).toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.scale(scale, scale)
        canvas.drawColor(Color.WHITE)
        drawLabel(canvas, label, 0f, 0f, border = false)
        return bmp
    }
}
