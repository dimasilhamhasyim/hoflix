package com.nuvio.app.core.auth

internal expect object AuthStorage {
    fun loadAnonymousUserId(): String?
    fun saveAnonymousUserId(userId: String)
    fun clearAnonymousUserId()
    fun loadFlag(key: String): Boolean
    fun saveFlag(key: String, value: Boolean)

    /** Teks bebas untuk cache kecil (mis. status keanggotaan). */
    fun loadText(key: String): String?
    fun saveText(key: String, value: String)
    fun clearText(key: String)

    /** Token sesi IDLIX yang dipegang bridge; dipakai untuk resolve stream 1080. */
    fun loadIdentityToken(): String?
    fun saveIdentityToken(token: String)
    fun clearIdentityToken()
}
