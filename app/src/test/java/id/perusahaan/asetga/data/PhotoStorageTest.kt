package id.perusahaan.asetga.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import id.perusahaan.asetga.data.file.PhotoStorage
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class PhotoStorageTest {
    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private val storage = PhotoStorage(ctx)
    private val photoDir get() = File(ctx.filesDir, PhotoStorage.DIR)
    private val outside get() = File(ctx.filesDir, "bukan-foto.db")

    @Before fun setUp() {
        photoDir.mkdirs()
        File(photoDir, "a.jpg").writeText("foto")
        outside.writeText("penting")
    }

    @After fun tearDown() {
        photoDir.deleteRecursively()
        outside.delete()
    }

    @Test fun `path foto yang sah tetap bisa dibaca dan dihapus`() {
        assertNotNull(storage.resolve("photos/a.jpg"))
        storage.delete("photos/a.jpg")
        assertFalse(File(photoDir, "a.jpg").exists())
    }

    @Test fun `path keluar dari folder foto ditolak`() {
        assertNull(storage.resolve("../${outside.name}"))
        assertNull(storage.resolve("photos/../${outside.name}"))
        assertNull(storage.resolve("photos"))
        storage.delete("../${outside.name}")
        storage.delete("photos/../${outside.name}")
        storage.delete("photos")
        assertTrue(outside.exists())
        assertEquals("penting", outside.readText())
        assertTrue(photoDir.exists())
    }
}
