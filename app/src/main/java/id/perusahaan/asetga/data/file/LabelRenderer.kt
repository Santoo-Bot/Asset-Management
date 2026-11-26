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
 * Label aset ukuran cetak 50 × 20 mm: simbol QR, teks kode QR, dan kategori.
 * PDF berisi satu label per halaman berukuran 50 × 20 mm; QR digambar sebagai vektor sehingga tajam saat dicetak.
 */
object LabelRenderer {
    private const val MM = 72f / 25.4f
    const val LABEL_W_MM = 50
    const val LABEL_H_MM = 20
    private val CELL_W = LABEL_W_MM * MM
    private val CELL_H = LABEL_H_MM * MM
    /** Ukuran halaman PDF dalam point (bilangan bulat), konten diskalakan pas ke halaman. */
    private val PDF_W = Math.round(CELL_W)
    private val PDF_H = Math.round(CELL_H)

    /** Ukuran media untuk layanan cetak Android, dalam 1/1000 inci. */
    val MEDIA_W_MILS = Math.round(LABEL_W_MM * 1000 / 25.4f)
    val MEDIA_H_MILS = Math.round(LABEL_H_MM * 1000 / 25.4f)

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
    private fun drawLabel(canvas: Canvas, label: LabelData, left: Float, top: Float, border: Boolean = false) {
        val pad = 1.5f * MM
        if (border) {
            val p = Paint().apply { color = Color.rgb(210, 210, 210); style = Paint.Style.STROKE; strokeWidth = 0.4f }
            canvas.drawRect(left, top, left + CELL_W, top + CELL_H, p)
        }
        val qrSize = CELL_H - 2 * pad
        drawQr(canvas, label.qr, left + pad, top + pad, qrSize)

        val textLeft = left + pad + qrSize + 1.5f * MM
        val textWidth = (left + CELL_W - pad - textLeft).toInt().coerceAtLeast(20)

        // Kode QR: tebal, selalu 1 baris; ukuran huruf dikecilkan sampai muat lebar label.
        val codePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; color = Color.BLACK; textSize = 8f }
        while (codePaint.textSize > 4f && codePaint.measureText(label.qr) > textWidth) codePaint.textSize -= 0.25f
        val codeText = TextUtils.ellipsize(label.qr, codePaint, textWidth.toFloat(), TextUtils.TruncateAt.END).toString()
        val codeMetrics = codePaint.fontMetrics
        val codeH = codeMetrics.descent - codeMetrics.ascent

        val catPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 6.5f; color = Color.rgb(60, 60, 60) }
        val cat = layout(label.category, catPaint, textWidth, 2)

        val gap = 1.5f
        val blockH = codeH + gap + cat.height
        var y = top + (CELL_H - blockH) / 2
        canvas.drawText(codeText, textLeft, y - codeMetrics.ascent, codePaint)
        y += codeH + gap
        canvas.save(); canvas.translate(textLeft, y); cat.draw(canvas); canvas.restore()
    }

    /** Satu label per halaman, jadi jumlah halaman = jumlah label. */
    fun pageCount(total: Int): Int = total

    /** PDF dengan ukuran halaman 50 × 20 mm, satu label per halaman (untuk printer label). */
    fun writePdf(labels: List<LabelData>, out: File) {
        val doc = PdfDocument()
        try {
            labels.forEachIndexed { index, label ->
                val page = doc.startPage(PdfDocument.PageInfo.Builder(PDF_W, PDF_H, index + 1).create())
                val canvas = page.canvas
                canvas.scale(PDF_W / CELL_W, PDF_H / CELL_H)
                canvas.drawColor(Color.WHITE)
                drawLabel(canvas, label, 0f, 0f, border = false)
                doc.finishPage(page)
            }
            out.outputStream().use { doc.writeTo(it) }
        } finally {
            doc.close()
        }
    }

    /** Pratinjau label ke-[page]. */
    fun renderPage(labels: List<LabelData>, page: Int, scale: Float): Bitmap =
        renderSingle(labels.getOrElse(page) { labels.first() }, scale)

    /** Render satu label 50 × 20 mm (PNG untuk printer label). [scale] 4 ≈ 288 dpi. */
    fun renderSingle(label: LabelData, scale: Float = 4f): Bitmap {
        val bmp = Bitmap.createBitmap((CELL_W * scale).toInt(), (CELL_H * scale).toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.scale(scale, scale)
        canvas.drawColor(Color.WHITE)
        drawLabel(canvas, label, 0f, 0f, border = false)
        return bmp
    }
}
