package com.nuvio.app.features.profiles

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ProfileAvatarDefaultsTest {

    private fun preset(id: String) = AvatarCatalogItem(
        id = id,
        displayName = id,
        category = "preset",
    )

    private val smiles = listOf(
        preset("hoflix-sage"),
        preset("hoflix-teal"),
        preset("hoflix-red"),
        preset("hoflix-amber"),
    )

    @Test
    fun `empty catalog has no default`() {
        assertNull(defaultProfileAvatarId(emptyList(), "hoflix-sage", 1))
    }

    @Test
    fun `new profile gets the preset the server marks as default`() {
        assertEquals("hoflix-sage", defaultProfileAvatarId(smiles, "hoflix-sage", 1))
    }

    @Test
    fun `second profile rotates to the next preset`() {
        assertEquals("hoflix-teal", defaultProfileAvatarId(smiles, "hoflix-sage", 2))
        assertEquals("hoflix-red", defaultProfileAvatarId(smiles, "hoflix-sage", 3))
        assertEquals("hoflix-amber", defaultProfileAvatarId(smiles, "hoflix-sage", 4))
    }

    @Test
    fun `rotation wraps around after the fourth profile`() {
        assertEquals("hoflix-sage", defaultProfileAvatarId(smiles, "hoflix-sage", 5))
        assertEquals("hoflix-teal", defaultProfileAvatarId(smiles, "hoflix-sage", 6))
    }

    @Test
    fun `unknown anchor falls back to the first preset`() {
        assertEquals("hoflix-sage", defaultProfileAvatarId(smiles, "tidak-ada", 1))
        assertEquals("hoflix-teal", defaultProfileAvatarId(smiles, null, 2))
    }

    @Test
    fun `non preset entries never become the default when presets exist`() {
        val mixed = listOf(
            AvatarCatalogItem(id = "member-dragon", category = "character"),
            preset("hoflix-sage"),
            preset("hoflix-teal"),
        )
        assertEquals("hoflix-sage", defaultProfileAvatarId(mixed, null, 1))
        assertEquals("hoflix-teal", defaultProfileAvatarId(mixed, null, 2))
    }

    @Test
    fun `profiles outside the normal range still resolve`() {
        assertEquals("hoflix-amber", defaultProfileAvatarId(smiles, "hoflix-sage", 0))
        assertEquals("hoflix-red", defaultProfileAvatarId(smiles, "hoflix-sage", -1))
    }
}
