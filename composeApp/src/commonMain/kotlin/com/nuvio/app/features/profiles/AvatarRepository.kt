package com.nuvio.app.features.profiles

import co.touchlab.kermit.Logger
import com.nuvio.app.core.network.ServerConfigurationRepository
import com.nuvio.app.features.addons.AddonRepository
import com.nuvio.app.features.addons.addonTransportBaseUrl
import com.nuvio.app.features.addons.enabledAddons
import com.nuvio.app.features.addons.fetchAddonResponseText
import com.nuvio.app.core.network.SupabaseProvider
import com.nuvio.app.features.membership.CosmeticEntitlement
import com.nuvio.app.features.membership.MemberAccessRepository
import com.nuvio.app.features.membership.MemberAssetStorage
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeMark
import kotlin.time.TimeSource

private const val MemberAvatarBucket = "membership-profile-avatars"
private val AvatarCatalogRefreshInterval = 15.minutes

@Serializable
private data class StoredAvatarCatalogPayload(
    val items: List<AvatarCatalogItem> = emptyList(),
    val memberItems: List<MemberAvatarCatalogItem> = emptyList(),
    @SerialName("default_avatar_id") val defaultAvatarId: String? = null,
)

@Serializable
private data class MemberAvatarCatalogItem(
    val id: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("storage_path") val storagePath: String,
    val category: String,
    @SerialName("sort_order") val sortOrder: Int = 0,
    @SerialName("bg_color") val bgColor: String? = null,
    @SerialName("asset_version") val assetVersion: Int,
)

internal fun availableAvatarCatalog(
    standardCatalog: List<AvatarCatalogItem>,
    memberCatalog: List<AvatarCatalogItem>,
    hasMemberAccess: Boolean,
): List<AvatarCatalogItem> = standardCatalog + if (hasMemberAccess) memberCatalog else emptyList()

object AvatarRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val log = Logger.withTag("AvatarRepository")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val _avatars = MutableStateFlow<List<AvatarCatalogItem>>(emptyList())
    val avatars: StateFlow<List<AvatarCatalogItem>> = _avatars.asStateFlow()

    private var standardCatalog = emptyList<AvatarCatalogItem>()
    private var memberCatalog = emptyList<AvatarCatalogItem>()
    private var memberCatalogMetadata = emptyList<MemberAvatarCatalogItem>()
    private var defaultCatalogAvatarId: String? = null
    private var standardLoaded = false
    private var cacheHydrated = false
    private var accessObserverStarted = false
    private var standardFetchInFlight = false
    private var memberFetchInFlight = false
    private var hasMemberAccess = false
    private var lastStandardRefresh: TimeMark? = null
    private var lastMemberRefresh: TimeMark? = null

    suspend fun shareableImageUrl(profile: NuvioProfile): String? {
        val storage = "${ServerConfigurationRepository.active.value.backendUrl}/storage/v1/object/"
        normalizedAvatarUrl(profile.avatarUrl)?.let { url -> return url.takeIf { it.startsWith(storage) } }
        val avatar = profile.avatarId?.let { id -> avatars.value.firstOrNull { it.id == id } } ?: return null
        if (!avatar.memberOnly) return avatar.storagePath.takeIf { it.isNotBlank() }?.let(::avatarStorageUrl)
        return try {
            SupabaseProvider.client.storage[MemberAvatarBucket].createSignedUrl(avatar.storagePath, 1.days)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            log.w(error) { "Unable to share supporter avatar ${avatar.id}" }
            null
        }
    }

    suspend fun fetchAvatars() {
        hydrateFromCacheIfNeeded()
        ensureMemberAccessObserver()
        if (standardLoaded && standardCatalog.isNotEmpty() && !isRefreshDue(lastStandardRefresh)) {
            publishCatalog()
            return
        }
        fetchStandardCatalog()
    }

    suspend fun refreshAvatars(force: Boolean = false) {
        hydrateFromCacheIfNeeded()
        ensureMemberAccessObserver()
        if (force || isRefreshDue(lastStandardRefresh)) {
            fetchStandardCatalog()
        }
        if (hasMemberAccess && (force || isRefreshDue(lastMemberRefresh))) {
            fetchMemberCatalog()
        }
    }

    private fun isRefreshDue(lastRefresh: TimeMark?): Boolean =
        lastRefresh == null || lastRefresh.elapsedNow() >= AvatarCatalogRefreshInterval

    private fun hydrateFromCacheIfNeeded() {
        if (cacheHydrated) return
        cacheHydrated = true

        val payload = AvatarStorage.loadPayload().orEmpty().trim()
        if (payload.isEmpty()) return

        val stored = runCatching {
            json.decodeFromString<StoredAvatarCatalogPayload>(payload)
        }.getOrNull() ?: return

        defaultCatalogAvatarId = stored.defaultAvatarId
        standardCatalog = stored.items
            .filter { it.isActive }
            .sortedWith(compareBy({ it.category }, { it.sortOrder }))
        memberCatalogMetadata = stored.memberItems
        memberCatalog = memberCatalogMetadata
            .mapNotNull(::loadCachedMemberAvatar)
            .sortedWith(compareBy({ it.category }, { it.sortOrder }))
        standardLoaded = standardCatalog.isNotEmpty()
        publishCatalog()
    }

    private fun ensureMemberAccessObserver() {
        if (accessObserverStarted) return
        accessObserverStarted = true
        MemberAccessRepository.ensureStarted()
        hasMemberAccess = MemberAccessRepository.access.value.entitlements
            .includes(CosmeticEntitlement.PROFILE_AVATARS)
        publishCatalog()
        if (hasMemberAccess) scope.launch { fetchMemberCatalog() }
        scope.launch {
            MemberAccessRepository.access.collectLatest { access ->
                val nextAccess = access.entitlements.includes(CosmeticEntitlement.PROFILE_AVATARS)
                if (nextAccess == hasMemberAccess) return@collectLatest
                hasMemberAccess = nextAccess
                if (nextAccess) {
                    fetchMemberCatalog()
                } else {
                    publishCatalog()
                }
            }
        }
    }

    private suspend fun fetchStandardCatalog() {
        if (standardFetchInFlight) return
        standardFetchInFlight = true
        try {
            val result = SupabaseProvider.client.postgrest.rpc("get_avatar_catalog")
            val items = result.decodeList<AvatarCatalogItem>()
            standardCatalog = items.filter { it.isActive }.sortedWith(
                compareBy({ it.category }, { it.sortOrder }),
            )
            defaultCatalogAvatarId = items.firstOrNull { it.isDefault }?.id ?: defaultCatalogAvatarId
            standardLoaded = standardCatalog.isNotEmpty()
            lastStandardRefresh = TimeSource.Monotonic.markNow()
            if (standardLoaded) {
                publishCatalog()
                saveCachedCatalog()
            } else {
                // This build has no avatar bucket; fall back to the bundled set.
                loadBundledAvatars()
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            log.e(error) { "Failed to fetch avatar catalog" }
            loadBundledAvatars()
        } finally {
            standardFetchInFlight = false
        }
    }

    /**
     * Preset bawaan yang dibundel aplikasi. Dipakai kalau bridge tidak bisa
     * dihubungi, supaya pemilih avatar tidak pernah kosong dan profil baru tetap
     * mendapat avatar default.
     */
    private val builtInPresets = listOf(
        Triple("hoflix-sage", "Zaitun", "#668B41"),
        Triple("hoflix-teal", "Teal", "#1A7371"),
        Triple("hoflix-red", "Merah", "#FF000C"),
        Triple("hoflix-amber", "Amber", "#FFAD01"),
    )

    private fun addonBaseUrl(): String? =
        AddonRepository.uiState.value.addons.enabledAddons()
            .firstOrNull { it.manifest?.id == "org.hoflix.moviebox" }
            ?.manifestUrl
            ?.let(::addonTransportBaseUrl)

    /** Isi katalog dari preset bawaan; tidak menimpa katalog yang sudah ada. */
    private fun applyBuiltInPresets() {
        if (standardCatalog.isNotEmpty()) return
        val base = addonBaseUrl() ?: return
        standardCatalog = builtInPresets.mapIndexed { index, (slug, label, color) ->
            AvatarCatalogItem(
                id = slug,
                displayName = label,
                storagePath = "$base/avatars/$slug.png",
                category = "preset",
                sortOrder = index,
                bgColor = color,
                isDefault = slug == "hoflix-sage",
            )
        }
        defaultCatalogAvatarId = "hoflix-sage"
        standardLoaded = true
        publishCatalog()
    }

    /**
     * Katalog upstream tinggal di bucket Supabase yang tidak dimiliki build ini.
     * Bridge HOFLIX menyajikan set avatarnya sendiri, jadi pemilih avatar tidak
     * pernah kosong dan mengganti avatar tetap jalan tanpa Supabase.
     */
    private suspend fun loadBundledAvatars() {
        val addon = AddonRepository.uiState.value.addons.enabledAddons()
            .firstOrNull { it.manifest?.id == "org.hoflix.moviebox" }
        val base = addon?.manifestUrl?.let(::addonTransportBaseUrl)
        if (base == null) {
            applyBuiltInPresets()
            return
        }
        val url = "$base/avatars.json"
        val payload = try {
            fetchAddonResponseText(url)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            log.w(error) { "Unable to load bundled avatars" }
            applyBuiltInPresets()
            return
        }
        val stored = runCatching {
            json.decodeFromString<StoredAvatarCatalogPayload>(payload)
        }.getOrNull()
        val items = stored?.items.orEmpty()
        // Payload kosong berarti bridge belum punya katalog: pertahankan yang ada.
        if (items.isEmpty()) return
        defaultCatalogAvatarId = stored?.defaultAvatarId
            ?: items.firstOrNull { it.isDefault }?.id
        standardCatalog = items
            .filter { it.isActive }
            .sortedWith(compareBy({ it.category }, { it.sortOrder }))
        standardLoaded = standardCatalog.isNotEmpty()
        if (standardLoaded) {
            lastStandardRefresh = TimeSource.Monotonic.markNow()
        }
        publishCatalog()
        saveCachedCatalog()
    }

    private suspend fun fetchMemberCatalog() {
        if (memberFetchInFlight) return
        memberFetchInFlight = true
        try {
            val remote = SupabaseProvider.client.postgrest
                .rpc("get_member_profile_avatar_catalog")
                .decodeList<MemberAvatarCatalogItem>()
            lastMemberRefresh = TimeSource.Monotonic.markNow()
            memberCatalogMetadata = remote
            saveCachedCatalog()
            memberCatalog = coroutineScope {
                remote.map { item ->
                    async { loadMemberAvatar(item) }
                }.awaitAll().filterNotNull()
            }.sortedWith(compareBy({ it.category }, { it.sortOrder }))
            publishCatalog()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            log.w(error) { "Unable to load supporter avatar catalog" }
        } finally {
            memberFetchInFlight = false
        }
    }

    private suspend fun loadMemberAvatar(item: MemberAvatarCatalogItem): AvatarCatalogItem? {
        return try {
            val cacheKey = memberAvatarCacheKey(item)
            val localImageUrl = MemberAssetStorage.loadProfileAvatar(cacheKey)
                ?: SupabaseProvider.client.storage[MemberAvatarBucket]
                    .downloadAuthenticated(item.storagePath)
                    .let { bytes -> MemberAssetStorage.saveProfileAvatar(cacheKey, bytes) }
                ?: return null
            item.toAvatarCatalogItem(localImageUrl)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            log.w(error) { "Unable to load supporter avatar ${item.id}" }
            null
        }
    }

    private fun loadCachedMemberAvatar(item: MemberAvatarCatalogItem): AvatarCatalogItem? {
        val localImageUrl = MemberAssetStorage.loadProfileAvatar(memberAvatarCacheKey(item)) ?: return null
        return item.toAvatarCatalogItem(localImageUrl)
    }

    private fun memberAvatarCacheKey(item: MemberAvatarCatalogItem): String {
        val extension = item.storagePath.substringAfterLast('.', "img")
            .takeIf { it.length in 2..5 && it.all(Char::isLetterOrDigit) }
            ?: "img"
        return "${item.id}-v${item.assetVersion}.$extension"
    }

    private fun MemberAvatarCatalogItem.toAvatarCatalogItem(localImageUrl: String): AvatarCatalogItem =
        AvatarCatalogItem(
            id = id,
            displayName = displayName,
            storagePath = storagePath,
            category = category,
            sortOrder = sortOrder,
            bgColor = bgColor,
            localImageUrl = localImageUrl,
            memberOnly = true,
        )

    /**
     * Preset yang ditandai bridge sebagai avatar bawaan. Dipakai pemilih profil
     * untuk mengisi avatar profil baru dan profil lama yang belum pernah memilih.
     */
    fun preferredDefaultAvatarId(): String? =
        defaultCatalogAvatarId ?: standardCatalog.firstOrNull { it.isDefault }?.id

    private fun saveCachedCatalog() {
        AvatarStorage.savePayload(
            json.encodeToString(
                StoredAvatarCatalogPayload(
                    items = standardCatalog,
                    memberItems = memberCatalogMetadata,
                    defaultAvatarId = defaultCatalogAvatarId,
                ),
            ),
        )
    }

    private fun publishCatalog() {
        _avatars.value = availableAvatarCatalog(
            standardCatalog = standardCatalog,
            memberCatalog = memberCatalog,
            hasMemberAccess = hasMemberAccess,
        )
    }
}
