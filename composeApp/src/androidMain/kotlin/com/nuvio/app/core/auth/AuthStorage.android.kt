package com.nuvio.app.core.auth

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Token identitas disegel dengan kunci AES-GCM yang tinggal di Android Keystore.
 *
 * Sebelumnya token disimpan apa adanya di `SharedPreferences`. Satu dump
 * storage, satu backup, atau satu perangkat root berarti sesi penuh bisa
 * dipakai orang lain — token itu memberi akses ke akun Supabase, cookie IDLIX,
 * dan status keanggotaan sekaligus.
 *
 * Nilai lama yang masih plaintext tetap terbaca (lalu ditulis ulang dalam
 * bentuk tersegel), jadi pembaruan aplikasi tidak memaksa login ulang.
 */
private object IdentityTokenCipher {
    private const val PREFIX = "v2:"
    private const val KEY_ALIAS = "hoflix_identity_token_v1"
    private const val KEYSTORE = "AndroidKeyStore"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128

    private fun secretKey(): SecretKey? = runCatching {
        val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (store.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
            ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).apply {
                init(
                    KeyGenParameterSpec.Builder(
                        KEY_ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                    )
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setKeySize(256)
                        .build(),
                )
            }.generateKey()
    }.getOrNull()

    fun seal(plain: String): String? = runCatching {
        val key = secretKey() ?: return null
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val sealed = cipher.iv + cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        PREFIX + Base64.encodeToString(sealed, Base64.NO_WRAP)
    }.getOrNull()

    fun open(stored: String): String? = runCatching {
        if (!stored.startsWith(PREFIX)) return stored
        val key = secretKey() ?: return null
        val raw = Base64.decode(stored.removePrefix(PREFIX), Base64.NO_WRAP)
        if (raw.size <= IV_BYTES) return null
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            key,
            GCMParameterSpec(TAG_BITS, raw, 0, IV_BYTES),
        )
        String(cipher.doFinal(raw, IV_BYTES, raw.size - IV_BYTES), Charsets.UTF_8)
    }.getOrNull()

    fun isSealed(value: String): Boolean = value.startsWith(PREFIX)
}

actual object AuthStorage {
    private const val PREFS_NAME = "nuvio_auth"
    private const val KEY_ANONYMOUS_USER_ID = "anonymous_user_id"
    private const val KEY_IDENTITY_TOKEN = "identity_token"

    private var preferences: SharedPreferences? = null

    fun initialize(context: Context) {
        preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    actual fun loadAnonymousUserId(): String? =
        preferences?.getString(KEY_ANONYMOUS_USER_ID, null)

    actual fun saveAnonymousUserId(userId: String) {
        preferences?.edit()?.putString(KEY_ANONYMOUS_USER_ID, userId)?.apply()
    }

    actual fun clearAnonymousUserId() {
        preferences?.edit()?.remove(KEY_ANONYMOUS_USER_ID)?.apply()
    }

    actual fun loadFlag(key: String): Boolean =
        preferences?.getBoolean(key, false) == true

    actual fun saveFlag(key: String, value: Boolean) {
        preferences?.edit()?.putBoolean(key, value)?.apply()
    }

    actual fun loadText(key: String): String? =
        preferences?.getString(key, null)

    actual fun saveText(key: String, value: String) {
        preferences?.edit()?.putString(key, value)?.apply()
    }

    actual fun clearText(key: String) {
        preferences?.edit()?.remove(key)?.apply()
    }

    actual fun loadIdentityToken(): String? {
        val stored = preferences?.getString(KEY_IDENTITY_TOKEN, null) ?: return null
        val plain = IdentityTokenCipher.open(stored)
        if (plain == null) {
            // Tidak bisa dibuka (kunci Keystore hilang/reset): jangan simpan
            // sisa yang tak terpakai, minta pengguna masuk ulang.
            clearIdentityToken()
            return null
        }
        if (!IdentityTokenCipher.isSealed(stored)) {
            // Migrasi diam-diam dari bentuk plaintext lama.
            saveIdentityToken(plain)
        }
        return plain
    }

    actual fun saveIdentityToken(token: String) {
        val sealed = IdentityTokenCipher.seal(token) ?: token
        preferences?.edit()?.putString(KEY_IDENTITY_TOKEN, sealed)?.apply()
    }

    actual fun clearIdentityToken() {
        preferences?.edit()?.remove(KEY_IDENTITY_TOKEN)?.apply()
    }
}
