package com.nuvio.app.features.notifications

internal expect object EpisodeReleaseNotificationPlatform {
    suspend fun notificationsAuthorized(): Boolean
    suspend fun requestAuthorization(): Boolean
    suspend fun scheduleEpisodeReleaseNotifications(requests: List<EpisodeReleaseNotificationRequest>)
    suspend fun clearScheduledEpisodeReleaseNotifications()
    suspend fun showTestNotification(request: EpisodeReleaseNotificationRequest)

    /** One-shot "this title is out" reminder, independent of the episode alerts. */
    suspend fun schedulePremiereReminder(request: EpisodeReleaseNotificationRequest)
    suspend fun cancelPremiereReminder(requestId: String)
}