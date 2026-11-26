package id.perusahaan.asetga.data.local

import androidx.room.TypeConverter
import java.time.LocalDate

/** LocalDate disimpan sebagai epochDay (INTEGER) agar bisa diurutkan & dibandingkan di SQL. */
class Converters {
    @TypeConverter
    fun fromEpochDay(value: Long?): LocalDate? = value?.let(LocalDate::ofEpochDay)

    @TypeConverter
    fun toEpochDay(date: LocalDate?): Long? = date?.toEpochDay()
}
