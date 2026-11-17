package com.nuvio.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.nuvio.app.features.catalog.CatalogScreen
import com.nuvio.app.features.catalog.CatalogTarget
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.library.toLibraryItem
import com.nuvio.app.features.servers.ServerCatalog
import com.nuvio.app.navigation.CatalogRoute
import com.nuvio.app.navigation.DetailRoute
import com.nuvio.app.navigation.IdlixCollectionRoute
import com.nuvio.app.navigation.IdlixNetworkRoute
import com.nuvio.app.navigation.NuvioNavigator

internal data class CatalogLaunch(
    val title: String,
    val subtitle: String,
    val target: CatalogTarget,
)

internal object CatalogLaunchStore {
    private var nextLaunchId = 1L
    private val launches = mutableMapOf<Long, CatalogLaunch>()

    fun put(launch: CatalogLaunch): Long {
        val launchId = nextLaunchId++
        launches[launchId] = launch
        return launchId
    }

    fun get(launchId: Long): CatalogLaunch? = launches[launchId]

    fun remove(launchId: Long) {
        launches.remove(launchId)
    }
}

internal fun NuvioNavigator.openPreview(meta: MetaPreview) {
    // Kartu Koleksi membawa id `collection:<slug>`: yang dibuka adalah layar
    // koleksi, bukan halaman detail film.
    if (meta.id.startsWith("collection:")) {
        val slug = meta.id.removePrefix("collection:").trim()
        if (slug.isNotEmpty()) {
            navigate(IdlixCollectionRoute(slug = slug, title = meta.name))
            return
        }
    }
    // Kartu network membawa id `network:<slug>`: yang dibuka adalah grid
    // jaringan itu, bukan halaman detail film.
    if (meta.id.startsWith("network:")) {
        val slug = meta.id.removePrefix("network:").trim()
        if (slug.isNotEmpty()) {
            navigate(IdlixNetworkRoute(slug = slug, title = meta.name))
            return
        }
    }
    val collection = ServerCatalog.collectionTarget(meta)
    if (collection == null) {
        navigate(DetailRoute(type = meta.type, id = meta.id, title = meta.name))
        return
    }
    val subtitle = ServerCatalog.sourceLabel(collection)
    val launchId = CatalogLaunchStore.put(CatalogLaunch(title = meta.name, subtitle = subtitle, target = collection))
    navigate(CatalogRoute(launchId = launchId, title = meta.name, subtitle = subtitle))
}

@Composable
internal fun CatalogDestination(
    route: CatalogRoute,
    navController: NuvioNavigator,
    onPosterLongClick: (PosterActionTarget) -> Unit,
) {
    val onBack = rememberGuardedPopBackStack(navController, route)
    val launch = remember(route.launchId) { CatalogLaunchStore.get(route.launchId) }
    if (launch == null) {
        LaunchedEffect(route.launchId) { onBack() }
        return
    }

    val target = launch.target
    CatalogScreen(
        title = launch.title,
        subtitle = launch.subtitle,
        target = target,
        onBack = onBack,
        onPosterClick = navController::openPreview,
        onPosterLongClick = { meta: MetaPreview ->
            onPosterLongClick(
                if (target is CatalogTarget.Library) {
                    PosterActionTarget(
                        preview = meta,
                        libraryItem = meta.toLibraryItem(savedAtEpochMs = 0L),
                        libraryListKey = target.sectionType,
                    )
                } else {
                    PosterActionTarget(preview = meta)
                },
            )
        },
        modifier = Modifier.fillMaxSize(),
    )
}
