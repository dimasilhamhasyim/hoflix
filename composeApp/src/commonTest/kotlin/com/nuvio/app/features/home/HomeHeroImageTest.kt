package com.nuvio.app.features.home

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HomeHeroImageTest {

    private fun item(
        id: String,
        banner: String? = null,
        landscape: String? = null,
        poster: String? = null,
    ) = MetaPreview(
        id = id,
        type = "movie",
        name = id,
        poster = poster,
        banner = banner,
        landscapePoster = landscape,
    )

    @Test
    fun `1280 and original pass the hd gate`() {
        assertTrue(isHdHeroImage("https://image.tmdb.org/t/p/w1280/abc.jpg"))
        assertTrue(isHdHeroImage("https://hoflix-api.siho.my.id/img/t/w1280/abc.jpg"))
        assertTrue(isHdHeroImage("https://image.tmdb.org/t/p/original/abc.jpg"))
        assertTrue(isHdHeroImage("https://image.tmdb.org/t/p/w1920/abc.jpg"))
    }

    @Test
    fun `small and unknown sizes never reach the hero`() {
        assertFalse(isHdHeroImage("https://image.tmdb.org/t/p/w780/abc.jpg"))
        assertFalse(isHdHeroImage("https://hoflix-api.siho.my.id/img/t/w342/abc.jpg"))
        assertFalse(isHdHeroImage("https://hoflix-api.siho.my.id/img/t/w500/abc.jpg"))
        // Ukuran tidak terbaca dari URL (still MovieBox mentah) ikut ditolak.
        assertFalse(isHdHeroImage("https://cdn.example.com/still/abc.jpg"))
    }

    @Test
    fun `poster only titles are not hero candidates`() {
        val posterOnly = item("a", poster = "https://image.tmdb.org/t/p/w342/a.jpg")
        assertFalse(posterOnly.hasHdHeroImage())

        val withBanner = item("b", banner = "https://image.tmdb.org/t/p/w1280/b.jpg")
        assertTrue(withBanner.hasHdHeroImage())
    }

    @Test
    fun `landscape is used when banner is blank`() {
        val onlyLandscape = item(
            "c",
            banner = "",
            landscape = "https://image.tmdb.org/t/p/w1280/c.jpg",
        )
        assertEquals("https://image.tmdb.org/t/p/w1280/c.jpg", onlyLandscape.heroImageUrl())
    }

    @Test
    fun `small banner falls through to a large landscape`() {
        val mixed = item(
            "d",
            banner = "https://image.tmdb.org/t/p/w780/d.jpg",
            landscape = "https://image.tmdb.org/t/p/w1280/d-wide.jpg",
        )
        assertEquals("https://image.tmdb.org/t/p/w1280/d-wide.jpg", mixed.heroImageUrl())
    }

    @Test
    fun `wide but small landscape is still rejected`() {
        val small = item(
            "e",
            landscape = "https://image.tmdb.org/t/p/w500/e.jpg",
            poster = "https://image.tmdb.org/t/p/w342/e.jpg",
        )
        assertFalse(small.hasHdHeroImage())
        assertEquals(null, small.heroImageUrl())
    }

    @Test
    fun `one seed keeps the slide order stable inside a session`() {
        val items = (1..12).map { index ->
            item("id$index", banner = "https://image.tmdb.org/t/p/w1280/$index.jpg")
        }
        val filtered = items.filter { it.hasHdHeroImage() }

        val first = filtered.shuffled(Random(4242)).take(8).map { it.id }
        val second = filtered.shuffled(Random(4242)).take(8).map { it.id }

        assertEquals(first, second)
    }
}
