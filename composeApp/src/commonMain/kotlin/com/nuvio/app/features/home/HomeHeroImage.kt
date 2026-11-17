package com.nuvio.app.features.home

/**
 * Aturan gambar hero billboard.
 *
 * Hero mengisi lebar layar (1080 px fisik) dan masih di-zoom, jadi butuh
 * gambar >= 1280 px. `w780`, `w500`, dan poster `w342` selalu terlihat burem
 * kalau dipasang di sini. Gambar yang ukurannya tidak bisa dibaca dari URL
 * (mis. still MovieBox mentah) juga ditolak: lebih baik tidak muncul di hero
 * daripada muncul pecah.
 */
private const val HERO_MIN_WIDTH = 1280

private val HERO_SIZE_TOKEN = Regex("/(w\\d+|original)(?=/|$)", RegexOption.IGNORE_CASE)

/**
 * Gambar lanskap HD untuk hero: banner lebih dulu, lalu landscape poster.
 *
 * Poster potret sengaja tidak dipakai — judulnya menempel di gambar dan
 * resolusinya (`w342`) selalu burem di billboard. Kalau banner ada tapi
 * resolusinya kecil, landscape yang besar tetap dipakai.
 */
fun MetaPreview.heroImageUrl(): String? =
    sequenceOf(banner, landscapePoster)
        .filterNotNull()
        .firstOrNull { candidate -> candidate.isNotBlank() && isHdHeroImage(candidate) }

/** True kalau URL gambar menunjuk berkas >= 1280 px (atau `original`). */
fun isHdHeroImage(url: String): Boolean {
    val path = url.substringBefore('?').substringBefore('#')
    val token = HERO_SIZE_TOKEN.find(path)?.groupValues?.get(1)?.lowercase() ?: return false
    if (token == "original") return true
    val width = token.removePrefix("w").toIntOrNull() ?: return false
    return width >= HERO_MIN_WIDTH
}

/** True kalau judul ini punya gambar hero yang tidak burem. */
fun MetaPreview.hasHdHeroImage(): Boolean = heroImageUrl() != null
