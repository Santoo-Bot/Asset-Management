package id.perusahaan.asetga.data.local

import androidx.room.TypeConverter
import id.perusahaan.asetga.domain.model.AssetStatus
import java.time.LocalDate

/** LocalDate disimpan sebagai epochDay (INTEGER) agar bisa diurutkan & dibandingkan di SQL. */
class Converters {
    @TypeConverter
    fun fromEpochDay(value: Long?): LocalDate? = value?.let(LocalDate::ofEpochDay)

    @TypeConverter
    fun toEpochDay(date: LocalDate?): Long? = date?.toEpochDay()

    /** Status aset disimpan sebagai id master Status (teks). */
    @TypeConverter
    fun fromStatus(status: AssetStatus?): String? = status?.id

    @TypeConverter
    fun toStatus(value: String?): AssetStatus? = value?.let(::AssetStatus)
}
