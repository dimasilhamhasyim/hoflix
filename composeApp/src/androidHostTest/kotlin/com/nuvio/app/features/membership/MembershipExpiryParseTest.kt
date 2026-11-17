package com.nuvio.app.features.membership

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Cache keanggotaan hanya dipakai saat server tidak bisa dihubungi. Tanggal
 * kedaluwarsa tetap dihormati supaya cache tidak memperpanjang langganan.
 */
class MembershipExpiryParseTest {
    @Test
    fun parsesUtcTimestamp() {
        assertEquals(1_765_756_800L, MembershipGateway.isoToEpochSeconds("2025-12-15T00:00:00Z"))
        assertEquals(0L, MembershipGateway.isoToEpochSeconds("1970-01-01T00:00:00Z"))
    }

    @Test
    fun acceptsOffsetSuffixByIgnoringIt() {
        assertEquals(
            MembershipGateway.isoToEpochSeconds("2026-11-15T14:18:08Z"),
            MembershipGateway.isoToEpochSeconds("2026-11-15T14:18:08+00:00"),
        )
    }

    @Test
    fun rejectsGarbage() {
        assertNull(MembershipGateway.isoToEpochSeconds(""))
        assertNull(MembershipGateway.isoToEpochSeconds("2026-13-15T14:18:08Z"))
        assertNull(MembershipGateway.isoToEpochSeconds("not-a-date-at-all"))
    }
}
