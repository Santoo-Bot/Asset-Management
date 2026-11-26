package id.perusahaan.asetga.data.repository

import id.perusahaan.asetga.domain.model.AppResult
import id.perusahaan.asetga.domain.model.Role
import id.perusahaan.asetga.domain.session.SessionManager

internal const val MSG_NO_ACCESS = "Anda tidak memiliki akses untuk tindakan ini"

/** Pemeriksaan peran di lapisan data, sebagai pengaman kedua setelah UI menyembunyikan tombol. */
internal fun SessionManager.denyUnless(check: (Role) -> Boolean): AppResult.Failure? {
    val role = currentUser?.role
    return if (role != null && check(role)) null else AppResult.Failure(MSG_NO_ACCESS)
}
