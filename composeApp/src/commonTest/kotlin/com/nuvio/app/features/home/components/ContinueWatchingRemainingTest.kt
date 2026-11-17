package com.nuvio.app.features.home.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ContinueWatchingRemainingTest {
    private val minuteMs = 60_000L

    @Test
    fun roundsUpSoHalfMinuteDoesNotShowFloor() {
        // 41 mnt 30 dtk sisa -> tampil 42 mnt, bukan 41.
        val remaining = continueWatchingRemaining(
            durationMs = 100 * minuteMs,
            positionMs = 100 * minuteMs - (41 * minuteMs + 30_000L),
        )

        assertEquals(42L, remaining.totalMinutes)
        assertEquals(0L, remaining.hours)
        assertEquals(42L, remaining.minutes)
        assertFalse(remaining.underOneMinute)
    }

    @Test
    fun splitsHoursAndMinutes() {
        val remaining = continueWatchingRemaining(
            durationMs = 2 * 60 * minuteMs + 5 * minuteMs,
            positionMs = 40 * minuteMs,
        )

        assertEquals(1L, remaining.hours)
        assertEquals(25L, remaining.minutes)
        assertEquals(85L, remaining.totalMinutes)
    }

    @Test
    fun exactHourHasZeroMinutes() {
        val remaining = continueWatchingRemaining(
            durationMs = 3 * 60 * minuteMs,
            positionMs = 60 * minuteMs,
        )

        assertEquals(2L, remaining.hours)
        assertEquals(0L, remaining.minutes)
    }

    @Test
    fun underOneMinuteIsFlagged() {
        val remaining = continueWatchingRemaining(
            durationMs = 50 * minuteMs,
            positionMs = 50 * minuteMs - 20_000L,
        )

        assertTrue(remaining.underOneMinute)
        // totalMinutes terbulatkan ke atas jadi 1, tapi badge memakai flag underOneMinute.
        assertEquals(1L, remaining.totalMinutes)
    }

    @Test
    fun bufferlessPositionYieldsZeroRemainingNotNegative() {
        // Posisi melewati durasi (durationMs di data bisa lebih kecil dari posisi).
        val remaining = continueWatchingRemaining(
            durationMs = 10 * minuteMs,
            positionMs = 25 * minuteMs,
        )

        assertTrue(remaining.underOneMinute)
        assertEquals(0L, remaining.totalMinutes)
    }

    @Test
    fun oneMinuteExactlyIsNotUnderOneMinute() {
        val remaining = continueWatchingRemaining(
            durationMs = 10 * minuteMs,
            positionMs = 9 * minuteMs,
        )

        assertFalse(remaining.underOneMinute)
        assertEquals(1L, remaining.totalMinutes)
    }

    @Test
    fun zeroRemainingIsUnderOneMinute() {
        val remaining = continueWatchingRemaining(
            durationMs = 10 * minuteMs,
            positionMs = 10 * minuteMs,
        )

        assertTrue(remaining.underOneMinute)
        assertEquals(0L, remaining.totalMinutes)
    }
}
