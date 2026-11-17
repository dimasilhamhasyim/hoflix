package com.nuvio.app.features.profiles

/**
 * Avatar bawaan untuk profil baru dan untuk profil lama yang belum pernah
 * memilih avatar: empat preset gambar milik aplikasi dipakai bergiliran
 * menurut nomor profil, jadi empat profil tidak tampil identik semua.
 *
 * [preferredId] adalah preset yang ditandai server sebagai bawaan; dia dipakai
 * sebagai titik jangkar penggiliran. Kalau katalog kosong (bridge belum
 * terhubung), hasilnya null dan pemanggil membiarkan monogram.
 */
internal fun defaultProfileAvatarId(
    catalog: List<AvatarCatalogItem>,
    preferredId: String?,
    profileIndex: Int,
): String? {
    if (catalog.isEmpty()) return null
    val presets = catalog.filter { it.category == "preset" }.ifEmpty { catalog }
    if (presets.isEmpty()) return null
    val size = presets.size
    val anchor = preferredId
        ?.let { id -> presets.indexOfFirst { item -> item.id == id } }
        ?.takeIf { index -> index >= 0 }
        ?: 0
    val offset = (((profileIndex - 1) % size) + size) % size
    return presets[(anchor + offset) % size].id
}
