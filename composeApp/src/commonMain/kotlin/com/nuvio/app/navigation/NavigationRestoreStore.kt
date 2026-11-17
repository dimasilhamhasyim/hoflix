package com.nuvio.app.navigation

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Where the user was when the app last had the foreground.
 *
 * Android is free to kill the process while the app sits in the background, and
 * Funtouch/iManager does it aggressively. Everything that describes "where am I"
 * lives in the Compose composition ([androidx.navigation3.runtime.NavBackStack],
 * `selectedTab`), so a killed process used to come back on the home tab. This is
 * the durable record that gets the user back.
 */
@Serializable
internal data class NavigationRestoreSnapshot(
    val tab: String = "Home",
    /** Only the top-most restorable route. The tab root underneath is implicit. */
    val route: RestoreRoute? = null,
    /** Set while the player was on screen; cleared as soon as it is left. */
    val player: RestorePlayerContext? = null,
)

/**
 * Route descriptor limited to destinations whose whole state is derived from
 * constructor arguments. Routes carrying a `launchId` (stream picker, catalog
 * grid, collection editor) point into memory-only stores and are therefore not
 * restorable — the user lands on the owning tab instead of a blank screen.
 */
@Serializable
internal data class RestoreRoute(
    val kind: String,
    val a: String? = null,
    val b: String? = null,
    val c: String? = null,
    val n1: Int? = null,
    val n2: Int? = null,
)

/** Everything needed to rebuild a playback launch after the in-memory store is gone. */
@Serializable
internal data class RestorePlayerContext(
    val type: String,
    val videoId: String,
    val parentMetaId: String,
    val parentMetaType: String,
    val title: String,
    val logo: String? = null,
    val poster: String? = null,
    val background: String? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val episodeTitle: String? = null,
    val episodeThumbnail: String? = null,
    val pauseDescription: String? = null,
)

internal object NavigationRestoreStore {
    private const val KIND_DETAIL = "detail"
    private const val KIND_PERSON = "person"
    private const val KIND_ENTITY = "entity"
    private const val KIND_SETTINGS_PAGE = "settings"
    private const val KIND_DOWNLOADS = "downloads"
    private const val KIND_COLLECTIONS = "collections"
    private const val KIND_IDLIX_COLLECTIONS = "idlix-collections"
    private const val KIND_IDLIX_COLLECTION = "idlix-collection"
    private const val KIND_IDLIX_NETWORK = "idlix-network"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /**
     * The player is restored at most once per process: a configuration change
     * recreates the composition but must not yank the user back into playback.
     */
    private var playerRestoreConsumed = false

    fun load(profileId: Int): NavigationRestoreSnapshot? {
        val payload = NavigationRestoreStorage.load(profileId) ?: return null
        return runCatching { json.decodeFromString<NavigationRestoreSnapshot>(payload) }.getOrNull()
    }

    fun save(profileId: Int, snapshot: NavigationRestoreSnapshot) {
        val payload = runCatching { json.encodeToString(snapshot) }.getOrNull() ?: return
        NavigationRestoreStorage.save(profileId, payload)
    }

    fun clear(profileId: Int) {
        NavigationRestoreStorage.clear(profileId)
        playerRestoreConsumed = false
    }

    /**
     * Hands the pending playback destination to the caller exactly once and
     * drops it from storage, so a failed restore cannot loop forever across
     * cold starts. A successful restore re-writes it as soon as the player
     * screen is actually on top.
     */
    fun takePlayerForRestore(profileId: Int): RestorePlayerContext? {
        if (playerRestoreConsumed) return null
        val snapshot = load(profileId) ?: return null
        val player = snapshot.player ?: return null
        playerRestoreConsumed = true
        save(profileId, snapshot.copy(player = null))
        return player
    }

    /** Test seam: lets a unit test exercise the one-shot handoff more than once. */
    fun resetRestoreConsumptionForTesting() {
        playerRestoreConsumed = false
    }

    fun restoreRouteOf(route: AppRoute): RestoreRoute? = when (route) {
        is DetailRoute -> RestoreRoute(
            kind = KIND_DETAIL,
            a = route.type,
            b = route.id,
            c = route.title,
        )

        is PersonDetailRoute -> RestoreRoute(
            kind = KIND_PERSON,
            a = route.personName,
            b = route.personPhoto,
            c = route.castAvatarTransitionKey,
            n1 = route.personId,
            n2 = if (route.preferCrew) 1 else 0,
        )

        is EntityBrowseRoute -> RestoreRoute(
            kind = KIND_ENTITY,
            a = route.entityKind,
            b = route.entityName,
            c = route.sourceType,
            n1 = route.entityId,
        )

        is SettingsPageRoute -> RestoreRoute(
            kind = KIND_SETTINGS_PAGE,
            a = route.pageName,
            b = route.title,
        )

        is DownloadsRoute -> RestoreRoute(kind = KIND_DOWNLOADS)

        is CollectionsRoute -> RestoreRoute(kind = KIND_COLLECTIONS)

        is IdlixCollectionsRoute -> RestoreRoute(kind = KIND_IDLIX_COLLECTIONS)

        is IdlixCollectionRoute -> RestoreRoute(
            kind = KIND_IDLIX_COLLECTION,
            a = route.slug,
            b = route.title,
        )

        is IdlixNetworkRoute -> RestoreRoute(
            kind = KIND_IDLIX_NETWORK,
            a = route.slug,
            b = route.title,
        )

        else -> null
    }

    fun toRoute(restore: RestoreRoute): AppRoute? = when (restore.kind) {
        KIND_DETAIL -> restore.a?.let { type ->
            restore.b?.let { id -> DetailRoute(type = type, id = id, title = restore.c) }
        }

        KIND_PERSON -> restore.n1?.let { personId ->
            PersonDetailRoute(
                personId = personId,
                personName = restore.a.orEmpty(),
                personPhoto = restore.b,
                castAvatarTransitionKey = restore.c,
                preferCrew = restore.n2 == 1,
            )
        }

        KIND_ENTITY -> {
            val kind = restore.a
            val id = restore.n1
            if (kind != null && id != null) {
                EntityBrowseRoute(
                    entityKind = kind,
                    entityId = id,
                    entityName = restore.b.orEmpty(),
                    sourceType = restore.c ?: "tv",
                )
            } else {
                null
            }
        }

        KIND_SETTINGS_PAGE -> restore.a?.let { pageName ->
            SettingsPageRoute(pageName = pageName, title = restore.b.orEmpty())
        }

        KIND_DOWNLOADS -> DownloadsRoute()
        KIND_COLLECTIONS -> CollectionsRoute()
        KIND_IDLIX_COLLECTIONS -> IdlixCollectionsRoute
        KIND_IDLIX_COLLECTION -> restore.a?.let { slug ->
            IdlixCollectionRoute(slug = slug, title = restore.b.orEmpty())
        }

        KIND_IDLIX_NETWORK -> restore.a?.let { slug ->
            IdlixNetworkRoute(slug = slug, title = restore.b.orEmpty())
        }

        else -> null
    }
}
