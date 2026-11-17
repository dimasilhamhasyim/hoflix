package com.nuvio.app.features.downloads

import android.app.job.JobParameters
import android.app.job.JobService
import android.os.SystemClock
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch

@RequiresApi(34)
class DownloadsTransferJobService : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val runs = mutableMapOf<String, Job>()
    private val lastStartAt = mutableMapOf<String, Long>()

    override fun onStartJob(params: JobParameters): Boolean = startTransfer(params)

    override fun onNetworkChanged(params: JobParameters) {
        val generation = params.extras?.getString(AndroidDownloadScheduler.GENERATION) ?: return
        // A transfer that is still working owns its own segment retries. Calling
        // startTransfer here used to cancel it and begin again from zero, which
        // is what turned a flapping mobile connection into "3 MB, then back to
        // 0 B" every second.
        if (runs[generation]?.isActive == true) return
        // Android can deliver this callback in bursts while the radio settles.
        val now = SystemClock.elapsedRealtime()
        val previous = lastStartAt[generation]
        if (previous != null && now - previous < NETWORK_RESTART_GUARD_MS) return
        startTransfer(params)
    }

    private fun startTransfer(params: JobParameters): Boolean {
        val scheduler = DownloadsPlatformDownloader.scheduler(this)
        val fileName = params.extras.getString(AndroidDownloadScheduler.FILE_NAME) ?: return false
        val generation = params.extras.getString(AndroidDownloadScheduler.GENERATION) ?: return false
        val transfer = scheduler.store.get(fileName)?.takeIf { it.generation == generation } ?: return false
        if (!scheduler.isActive(transfer)) return false
        DownloadsLiveStatusPlatform.initialize(this)
        setNotification(params, DownloadsLiveStatusPlatform.notificationId(transfer.item.id),
            DownloadsLiveStatusPlatform.buildNotification(this, transfer.item), JOB_END_NOTIFICATION_POLICY_REMOVE)
        lastStartAt[generation] = SystemClock.elapsedRealtime()
        val run = scope.launch(start = CoroutineStart.LAZY) {
            try {
                val retry = scheduler.execute(transfer, params.network) { updated ->
                    updated.item.totalBytes?.let { updateEstimatedNetworkBytes(params, it, 0L) }
                    updateTransferredNetworkBytes(params, updated.item.downloadedBytes, 0L)
                    DownloadsLiveStatusPlatform.notifyTransfer(updated.item)
                }
                jobFinished(params, retry)
                scheduler.notifyCurrent(fileName)
            } finally {
                if (runs[generation] === currentCoroutineContext()[Job]) runs.remove(generation)
            }
        }
        runs.put(generation, run)?.cancel()
        run.start()
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        val scheduler = DownloadsPlatformDownloader.scheduler(this)
        val fileName = params.extras.getString(AndroidDownloadScheduler.FILE_NAME) ?: return false
        val generation = params.extras.getString(AndroidDownloadScheduler.GENERATION) ?: return false
        runs.remove(generation)?.cancel()
        lastStartAt.remove(generation)
        val transfer = scheduler.store.get(fileName)?.takeIf { it.generation == generation } ?: return false
        if (params.stopReason == JobParameters.STOP_REASON_USER) {
            scheduler.pause(fileName)
            return false
        }
        return scheduler.isActive(transfer)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        /** Ignore identical restarts that arrive closer together than this. */
        const val NETWORK_RESTART_GUARD_MS = 2_000L
    }
}
