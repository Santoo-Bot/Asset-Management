package id.perusahaan.asetga.data

import android.database.sqlite.SQLiteConstraintException
import androidx.test.ext.junit.runners.AndroidJUnit4
import id.perusahaan.asetga.data.local.AppDatabase
import id.perusahaan.asetga.data.local.DefaultData
import id.perusahaan.asetga.data.local.entity.CategoryEntity
import id.perusahaan.asetga.data.local.entity.LocationEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MasterDaoTest {
    private lateinit var db: AppDatabase

    @Before fun setUp() { db = inMemoryDb() }
    @After fun tearDown() = db.close()

    @Test fun `kategori awal terisi dengan umur ekonomis yang benar`() = runTest {
        val cats = db.categoryDao().observeAll(includeInactive = true).first()
        assertEquals(DefaultData.categories.size, cats.size)
        val life = cats.associate { it.name to it.usefulLifeYears }
        assertEquals(4, life["Elektronik & IT"])
        assertEquals(8, life["Furnitur"])
        assertEquals(8, life["Kendaraan"])
        assertEquals(4, life["Peralatan Kantor"])
        assertEquals(8, life["Mesin & Peralatan"])
        assertEquals(20, life["Bangunan & Instalasi"])
        assertEquals(4, life["Lainnya"])
        assertTrue(cats.all { it.residualPercent == null && it.isActive })
    }

    @Test fun `satuan dan pengaturan default terisi`() = runTest {
        assertEquals(DefaultData.units.size, db.unitDao().observeAll(false).first().size)
        assertEquals("8", db.settingsDao().get("reminder_hour"))
        assertEquals("0", db.settingsDao().get("default_residual_percent"))
    }

    @Test fun `kode prefix kategori unik di tingkat database`() = runTest {
        val now = 1L
        try {
            db.categoryDao().insert(CategoryEntity("x", "Komputer", "IT", 4, null, true, now, now))
            fail("Seharusnya gagal karena prefix IT sudah ada")
        } catch (expected: SQLiteConstraintException) {
            // OK
        }
    }

    @Test fun `nonaktifkan menyembunyikan dari daftar aktif tanpa menghapus`() = runTest {
        val dao = db.locationDao()
        dao.insert(LocationEntity("l1", "Gudang A", null, true, 1, 1))
        dao.insert(LocationEntity("l2", "Gudang B", null, true, 1, 1))
        dao.setActive("l1", false, 2)

        assertEquals(listOf("Gudang B"), dao.observeAll(includeInactive = false).first().map { it.name })
        assertEquals(2, dao.observeAll(includeInactive = true).first().size)
        assertEquals(false, dao.getById("l1")?.isActive)
    }

    @Test fun `cek nama ganda tidak peka huruf besar kecil dan mengecualikan diri sendiri`() = runTest {
        val dao = db.locationDao()
        dao.insert(LocationEntity("l1", "Gudang A", null, true, 1, 1))
        assertEquals(1, dao.countByName("gudang a", excludeId = ""))
        assertEquals(0, dao.countByName("gudang a", excludeId = "l1"))
    }
}
