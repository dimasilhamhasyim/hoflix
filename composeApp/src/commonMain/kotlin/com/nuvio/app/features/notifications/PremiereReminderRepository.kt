package com.nuvio.app.features.notifications

import co.touchlab.kermit.Logger
import com.nuvio.app.core.deeplink.buildMetaDeepLinkUrl
import com.nuvio.app.core.ui.NuvioToastController
import com.nuvio.app.features.details.MetaDetails
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.hoflix_reminder_body
import nuvio.composeapp.generated.resources.hoflix_reminder_cancelled
import nuvio.composeapp.generated.resources.hoflix_reminder_scheduled
import org.jetbrains.compose.resources.getString

/**
 * "Ingatkan saya" on a title that has not released yet.
 *
 * The reminder is a single WorkManager job per title (09:00 on the release day)
 * and survives the app being closed. It is kept apart from the episode alerts so
 * toggling one never re-schedules the library's episode notifications.
 */
object PremiereReminderRepository {
    private const val requestPrefix = "premiere:"
    private const val separator = "\n"

    private val log = Logger.withTag("PremiereReminder")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _remindedIds = MutableStateFlow<Set<String>>(emptySet())
    val remindedIds: StateFlow<Set<String>> = _remindedIds.asStateFlow()

    private var loaded = false

    fun ensureLoaded() {
        if (loaded) return
        loaded = true
        _remindedIds.value = PremiereReminderStorage.loadPayload()
            .orEmpty()
            .split(separator)
            .map(String::trim)
            .filter(String::isNotBlank)
            .toSet()
    }

    fun isReminded(metaId: String): Boolean {
        ensureLoaded()
        val normalized = metaId.trim()
        return normalized.isNotBlank() && normalized in _remindedIds.value
    }

    fun toggle(meta: MetaDetails) {
        ensureLoaded()
        val metaId = meta.id.trim()
        val releaseDate = meta.released?.trim().orEmpty()
        if (metaId.isBlank() || releaseDate.isBlank()) return

        val alreadyReminded = metaId in _remindedIds.value
        val next = if (alreadyReminded) _remindedIds.value - metaId else _remindedIds.value + metaId
        _remindedIds.value = next
        PremiereReminderStorage.savePayload(next.joinToString(separator))

        scope.launch {
            val requestId = requestPrefix + metaId
            if (alreadyReminded) {
                runCatching { EpisodeReleaseNotificationPlatform.cancelPremiereReminder(requestId) }
                    .onFailure { error -> log.w { "cancel reminder failed: ${error.message}" } }
                NuvioToastController.show(getString(Res.string.hoflix_reminder_cancelled))
                return@launch
            }
            // Android 13+ needs the notification permission before the reminder
            // can land in the status bar; ask at the moment it becomes useful.
            runCatching { EpisodeReleaseNotificationPlatform.requestAuthorization() }
            runCatching {
                EpisodeReleaseNotificationPlatform.schedulePremiereReminder(
                    EpisodeReleaseNotificationRequest(
                        requestId = requestId,
                        notificationTitle = meta.name,
                        notificationBody = getString(Res.string.hoflix_reminder_body),
                        releaseDateIso = releaseDate,
                        deepLinkUrl = buildMetaDeepLinkUrl(meta.type, meta.id),
                        backdropUrl = meta.background ?: meta.poster,
                    ),
                )
            }.onFailure { error ->
                log.w { "schedule reminder failed: ${error.message}" }
            }
            NuvioToastController.show(getString(Res.string.hoflix_reminder_scheduled))
        }
    }
}
