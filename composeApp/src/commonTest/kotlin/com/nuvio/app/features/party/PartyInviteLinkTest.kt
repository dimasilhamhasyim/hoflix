package com.nuvio.app.features.party

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PartyInviteLinkTest {

    @Test
    fun `invite link points at the hoflix invite page`() {
        assertEquals(
            "https://hoflix-api.siho.my.id/party/K7M2QX",
            partyInviteLink("K7M2QX"),
        )
    }

    @Test
    fun `invite link never points at a third-party domain`() {
        val link = partyInviteLink("AB2CD3")
        assertTrue(link.startsWith("https://hoflix-api.siho.my.id/party/"))
        assertFalse(link.contains("nuvio", ignoreCase = true))
    }
}
