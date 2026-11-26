package id.perusahaan.asetga.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import id.perusahaan.asetga.data.local.AppDatabase
import id.perusahaan.asetga.data.local.DefaultData

fun inMemoryDb(): AppDatabase =
    Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java)
        .addCallback(DefaultData.callback)
        .allowMainThreadQueries()
        .build()
