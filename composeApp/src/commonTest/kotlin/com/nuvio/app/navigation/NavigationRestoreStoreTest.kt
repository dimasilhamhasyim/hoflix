package com.nuvio.app.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NavigationRestoreStoreTest {
    @Test
    fun detailRouteSurvivesRoundTrip() {
        val route = DetailRoute(type = "series", id = "idlix:dark-2017", title = "Dark")

        val restored = NavigationRestoreStore.toRoute(
            NavigationRestoreStore.restoreRouteOf(route)!!,
        )

        assertEquals(route, restored)
    }

    @Test
    fun personRouteSurvivesRoundTrip() {
        val route = PersonDetailRoute(
            personId = 1183992,
            personName = "Louis Hofmann",
            personPhoto = "https://example.test/photo.jpg",
            preferCrew = true,
        )

        val restored = NavigationRestoreStore.toRoute(
            NavigationRestoreStore.restoreRouteOf(route)!!,
        )

        assertEquals(route, restored)
    }

    @Test
    fun entityRouteSurvivesRoundTrip() {
        val route = EntityBrowseRoute(
            entityKind = "network",
            entityId = 213,
            entityName = "Netflix",
            sourceType = "movie",
        )

        val restored = NavigationRestoreStore.toRoute(
            NavigationRestoreStore.restoreRouteOf(route)!!,
        )

        assertEquals(route, restored)
    }

    @Test
    fun idlixCollectionRouteSurvivesRoundTrip() {
        val route = IdlixCollectionRoute(slug = "marvel-cinematic-universe", title = "MCU")

        val restored = NavigationRestoreStore.toRoute(
            NavigationRestoreStore.restoreRouteOf(route)!!,
        )

        assertEquals(route, restored)
    }

    @Test
    fun settingsPageSurvivesRoundTrip() {
        val route = SettingsPageRoute(pageName = "appearance", title = "Tampilan")

        val restored = NavigationRestoreStore.toRoute(
            NavigationRestoreStore.restoreRouteOf(route)!!,
        )

        assertEquals(route, restored)
    }

    @Test
    fun launchIdRoutesAreNotRestorable() {
        // These point into memory-only stores; restoring them would open a blank screen.
        assertNull(NavigationRestoreStore.restoreRouteOf(StreamRoute(launchId = 7)))
        assertNull(NavigationRestoreStore.restoreRouteOf(PlayerRoute(launchId = 7)))
        assertNull(NavigationRestoreStore.restoreRouteOf(CatalogRoute(launchId = 7)))
        assertNull(NavigationRestoreStore.restoreRouteOf(TabsRoute))
    }

    @Test
    fun malformedDescriptorIsRejected() {
        assertNull(NavigationRestoreStore.toRoute(RestoreRoute(kind = "detail", a = "movie")))
        assertNull(NavigationRestoreStore.toRoute(RestoreRoute(kind = "nonsense")))
    }
}
