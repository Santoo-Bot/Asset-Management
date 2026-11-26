package id.perusahaan.asetga.domain.model

/** Hasil operasi tulis dengan pesan kesalahan yang siap ditampilkan ke pengguna. */
sealed interface AppResult<out T> {
    data class Success<T>(val value: T) : AppResult<T>
    data class Failure(val message: String, val fieldErrors: Map<String, String> = emptyMap()) : AppResult<Nothing>
}

inline fun <T> AppResult<T>.onSuccess(block: (T) -> Unit): AppResult<T> {
    if (this is AppResult.Success) block(value)
    return this
}
