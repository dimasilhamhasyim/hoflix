package com.nuvio.app.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import com.nuvio.app.features.profiles.AvatarCropRect

/**
 * Men-dekode byte gambar (PNG/JPEG) agar bisa ditampilkan langsung tanpa
 * bergantung pada loader jaringan.
 */
expect fun decodeImageBitmap(bytes: ByteArray): ImageBitmap?

/** Hasil pemilih gambar, dibedakan supaya UI bisa membedakan batal dan gagal. */
sealed interface ProfileImagePickResult {
    data class Picked(val bytes: ByteArray) : ProfileImagePickResult
    data object Cancelled : ProfileImagePickResult
    data object Failed : ProfileImagePickResult
}

/**
 * Pemilih gambar dari galeri. Mengembalikan byte gambar sumber (sudah
 * diperkecil agar hemat memori) untuk diatur potongannya sebelum diunggah.
 */
@Composable
expect fun rememberProfileImagePicker(onPicked: (ProfileImagePickResult) -> Unit): () -> Unit

/**
 * Memotong persegi [crop] dari gambar sumber lalu menuliskannya sebagai JPEG
 * persegi 512 px yang siap diunggah. Null kalau gambar tidak bisa dibaca.
 */
internal expect fun cropAvatarImage(source: ByteArray, crop: AvatarCropRect): ByteArray?
