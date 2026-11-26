package id.perusahaan.asetga.data.seed

/**
 * Pengisi data contoh. Implementasi nyata hanya ada di source set `debug`;
 * build release memakai versi no-op sehingga data contoh tidak pernah ikut ke produksi.
 */
interface SampleDataSeeder {
    suspend fun seedIfNeeded()
}
