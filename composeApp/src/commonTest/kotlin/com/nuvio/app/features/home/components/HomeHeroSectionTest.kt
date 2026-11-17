package com.nuvio.app.features.home.components

import com.nuvio.app.features.watchprogress.ContinueWatchingSectionStyle
import kotlin.test.Test
import kotlin.test.assertEquals

class HomeHeroSectionTest {

    @Test
    fun `first and last indicators select adjacent pages across the loop`() {
        assertEquals(79, heroPageForItem(currentPage = 80, itemIndex = 7, itemCount = 8))
        assertEquals(88, heroPageForItem(currentPage = 87, itemIndex = 0, itemCount = 8))
    }

    @Test
    fun `indicators keep the current page when selecting the active title`() {
        assertEquals(83, heroPageForItem(currentPage = 83, itemIndex = 3, itemCount = 8))
    }

    @Test
    fun `indicators select the closest occurrence of a title`() {
        assertEquals(85, heroPageForItem(currentPage = 83, itemIndex = 5, itemCount = 8))
        assertEquals(81, heroPageForItem(currentPage = 83, itemIndex = 1, itemCount = 8))
    }

    @Test
    fun `mobile hero height stays compact without continue watching`() {
        val layout = homeHeroLayout(
            maxWidthDp = 390f,
            viewportHeightDp = 844f,
        )

        assertEquals(false, layout.isTablet)
        assertEquals(569.4f, layout.heroHeight.value, 0.001f)
    }

    @Test
    fun `mobile hero height hits the chosen 574dp at a 393dp wide viewport`() {
        val layout = homeHeroLayout(
            maxWidthDp = 393f,
            viewportHeightDp = 800f,
        )

        assertEquals(573.78f, layout.heroHeight.value, 0.001f)
    }

    @Test
    fun `tablet hero height remains width driven even with viewport height`() {
        val layout = homeHeroLayout(
            maxWidthDp = 840f,
            viewportHeightDp = 1200f,
        )

        assertEquals(true, layout.isTablet)
        assertEquals(386.4f, layout.heroHeight.value, 0.001f)
    }

    @Test
    fun `mobile hero height ignores the continue watching reserve`() {
        val continueWatchingLayout = rememberContinueWatchingLayout(maxWidthDp = 390f)
        val reserveHeight = continueWatchingHeroViewportReserveHeight(
            style = ContinueWatchingSectionStyle.Card,
            layout = continueWatchingLayout,
            basePosterWidthDp = 110,
        )
        val withoutRail = homeHeroLayout(
            maxWidthDp = 390f,
            viewportHeightDp = 844f,
        )
        val withRail = homeHeroLayout(
            maxWidthDp = 390f,
            viewportHeightDp = 844f,
            mobileBelowSectionHeightHintDp = reserveHeight.value,
        )

        assertEquals(569.4f, withRail.heroHeight.value, 0.001f)
        assertEquals(withoutRail.heroHeight.value, withRail.heroHeight.value, 0.001f)
    }

    @Test
    fun `mobile hero height stays put for any reserve height on short viewports`() {
        val heights = listOf(null, 120f, 300f, 460f).map { hint ->
            homeHeroLayout(
                maxWidthDp = 390f,
                viewportHeightDp = 568f,
                mobileBelowSectionHeightHintDp = hint,
            ).heroHeight.value
        }

        assertEquals(listOf(465.76f, 465.76f, 465.76f, 465.76f), heights)
    }
}
