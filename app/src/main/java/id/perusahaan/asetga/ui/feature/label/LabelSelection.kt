package id.perusahaan.asetga.ui.feature.label

import javax.inject.Inject
import javax.inject.Singleton

/** Daftar aset yang akan dibuatkan label QR (bisa ribuan, jadi tidak dikirim lewat argumen navigasi). */
@Singleton
class LabelSelection @Inject constructor() {
    @Volatile var assetIds: List<String> = emptyList()
}
