package id.perusahaan.asetga.domain.security

object CredentialRules {
    const val FIELD_USERNAME = "username"
    const val FIELD_FULL_NAME = "fullName"
    const val FIELD_PASSWORD = "password"
    const val FIELD_CONFIRM = "confirm"

    private val usernameRegex = Regex("^[a-z0-9._]{3,32}$")

    fun validateUsername(username: String): String? = when {
        username.isBlank() -> "Username wajib diisi"
        !usernameRegex.matches(username) -> "3–32 karakter: huruf kecil, angka, titik, atau garis bawah"
        else -> null
    }

    fun validatePassword(password: String): String? = when {
        password.length < 8 -> "Kata sandi minimal 8 karakter"
        password.none { it.isDigit() } || password.none { it.isLetter() } -> "Kata sandi harus berisi huruf dan angka"
        else -> null
    }

    fun validatePin(pin: String): String? = when {
        !pin.all { it.isDigit() } -> "PIN hanya boleh angka"
        pin.length !in 4..8 -> "PIN 4–8 digit"
        pin.toSet().size == 1 -> "PIN terlalu mudah ditebak"
        else -> null
    }
}
