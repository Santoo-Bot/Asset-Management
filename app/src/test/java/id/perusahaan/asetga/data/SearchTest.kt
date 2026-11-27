package id.perusahaan.asetga.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import id.perusahaan.asetga.data.local.AppDatabase
import id.perusahaan.asetga.data.local.entity.AssetEntity
import id.perusahaan.asetga.data.local.entity.LocationEntity
import id.perusahaan.asetga.domain.model.AssetCondition
import id.perusahaan.asetga.domain.model.AssetStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/** Pencarian memperlakukan teks apa adanya: "%" dan "_" bukan karakter pengganti. */
@RunWith(AndroidJUnit4::class)
class SearchTest {
    private lateinit var db: AppDatabase

    @Before fun setUp() = runTest {
        db = inMemoryDb()
        db.locationDao().insert(LocationEntity("L", "Kantor", null, true, 1, 1))
        val cat = db.categoryDao().observeAll(false).first().first().id
        fun asset(id: String, name: String) = AssetEntity(
            id = id, code = "IT-$id", name = name, categoryId = cat, acquisitionDate = LocalDate.of(2024, 1, 1), acquisitionCost = 1,
            locationId = "L", condition = AssetCondition.BAIK, status = AssetStatus.DIGUNAKAN, createdAt = 1, updatedAt = 1,
        )
        db.assetDao().insertAll(listOf(asset("1", "Diskon 100% Mouse"), asset("2", "Kabel A_B"), asset("3", "Kabel AXB"), asset("4", "Laptop")))
    }

    @After fun tearDown() = db.close()

    private suspend fun count(query: String) = db.assetDao().count(query, null, null, null, null, null, null, false).first()

    @Test fun `persen dan garis bawah dicari apa adanya`() = runTest {
        assertEquals(1, count("%"))
        assertEquals(1, count("100%"))
        assertEquals(1, count("_"))
        assertEquals(1, count("a_b"))
    }

    @Test fun `pencarian biasa tetap tidak peka huruf besar-kecil dan memakai potongan teks`() = runTest {
        assertEquals(1, count("LAPTOP"))
        assertEquals(2, count("kabel"))
        assertEquals(4, count(""))
        assertEquals(0, count("tidak-ada"))
    }
}
