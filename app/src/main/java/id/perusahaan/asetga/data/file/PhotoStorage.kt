package id.perusahaan.asetga.data.file

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

/**
 * Menyimpan foto aset/servis di penyimpanan internal aplikasi (filesDir/photos), sudah dikompres:
 * sisi terpanjang maks. [MAX_SIDE] px, JPEG kualitas [QUALITY], orientasi EXIF diterapkan.
 * Path yang disimpan di database relatif terhadap filesDir agar ikut terbawa saat backup/restore.
 */
@Singleton
class PhotoStorage @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val photoDir: File get() = File(context.filesDir, DIR).apply { mkdirs() }

    fun resolve(relativePath: String?): File? = relativePath?.let { File(context.filesDir, it) }?.takeIf { it.exists() }

    /** Uri sementara untuk kamera (ActivityResultContracts.TakePicture). */
    fun newCameraUri(): Uri {
        val dir = File(context.cacheDir, "camera").apply { mkdirs() }
        val file = File(dir, "capture-${UUID.randomUUID()}.jpg")
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    /** Kompres gambar dari [uri] (kamera/galeri) lalu simpan. Mengembalikan path relatif, atau null bila gagal. */
    suspend fun importCompressed(uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null

            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
            val decoded = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: return@runCatching null

            val rotation = context.contentResolver.openInputStream(uri)?.use { stream ->
                when (ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f

            val scale = MAX_SIDE.toFloat() / max(decoded.width, decoded.height)
            val matrix = Matrix().apply {
                if (scale < 1f) postScale(scale, scale)
                if (rotation != 0f) postRotate(rotation)
            }
            val output = if (matrix.isIdentity) decoded else Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)

            val name = "${UUID.randomUUID()}.jpg"
            File(photoDir, name).outputStream().use { output.compress(Bitmap.CompressFormat.JPEG, QUALITY, it) }
            if (output !== decoded) output.recycle()
            decoded.recycle()
            "$DIR/$name"
        }.getOrNull()
    }

    fun delete(relativePath: String?) {
        relativePath?.let { File(context.filesDir, it).delete() }
    }

    /** Hapus file foto yang tidak lagi dirujuk data mana pun. Mengembalikan jumlah file yang dihapus. */
    suspend fun deleteUnreferenced(referenced: Collection<String>): Int = withContext(Dispatchers.IO) {
        val keep = referenced.toHashSet()
        val dir = File(context.filesDir, DIR)
        dir.listFiles().orEmpty().count { file -> file.isFile && "$DIR/${file.name}" !in keep && file.delete() }
    }

    companion object {
        const val DIR = "photos"
        const val MAX_SIDE = 1280
        const val QUALITY = 80
    }
}
