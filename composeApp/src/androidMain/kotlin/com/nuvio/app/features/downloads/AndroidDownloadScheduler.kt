package com.nuvio.app.features.downloads

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.net.Network
import android.os.Build
import android.os.PersistableBundle
import android.os.SystemClock
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient

internal class AndroidDownloadScheduler(val context: Context) {
    val store = AndroidDownloadStore(File(context.filesDir, "download-transfers"))
    val directory = File(context.filesDir, "downloads")
    private val locks = ConcurrentHashMap<String, Mutex>()
    private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun enqueue(item: DownloadItem): AndroidDownloadTransfer {
        val previous = store.get(item.fileName)
        val transfer = store.begin(item)
        val existing = previous?.generation == transfer.generation
        if (existing && Build.VERSION.SDK_INT >= 34) return transfer
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                check(jobScheduler().schedule(buildJob(transfer)) == JobScheduler.RESULT_SUCCESS) {
                    "Android could not start the download. Open the app and try again."
                }
            } else {
                val work = OneTimeWorkRequestBuilder<DownloadsTransferWorker>()
                    .setInputData(workDataOf(FILE_NAME to item.fileName, GENERATION to transfer.generation))
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                    .build()
                val operation = WorkManager.getInstance(context).enqueueUniqueWork(
                    workName(transfer), if (existing) ExistingWorkPolicy.KEEP else ExistingWorkPolicy.REPLACE, work,
                )
                operation.result.addListener({
                    try {
                        operation.result.get()
                    } catch (error: Exception) {
                        fail(transfer, error)
                        notifyCurrent(item.fileName)
                    }
                }, ContextCompat.getMainExecutor(context))
            }
        } catch (error: Exception) {
            fail(transfer, error)
        }
        return store.get(item.fileName) ?: transfer
    }

    @RequiresApi(34)
    internal fun buildJob(transfer: AndroidDownloadTransfer): JobInfo =
        JobInfo.Builder(transfer.jobId, ComponentName(context, DownloadsTransferJobService::class.java))
            .setUserInitiated(true)
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setPersisted(true)
            .setEstimatedNetworkBytes(transfer.item.totalBytes ?: JobInfo.NETWORK_BYTES_UNKNOWN.toLong(), 0L)
            .setBackoffCriteria(30_000L, JobInfo.BACKOFF_POLICY_EXPONENTIAL)
            .setExtras(PersistableBundle().apply {
                putString(FILE_NAME, transfer.item.fileName)
                putString(GENERATION, transfer.generation)
            })
            .build()

    @RequiresApi(34)
    private fun jobScheduler(): JobScheduler =
        context.getSystemService(JobScheduler::class.java).forNamespace(JOB_NAMESPACE)

    fun restore(item: DownloadItem): DownloadItem {
        var transfer = store.get(item.fileName)?.takeIf { it.item.id == item.id }
            ?: return if (item.status == DownloadStatus.Downloading) {
                item.copy(status = DownloadStatus.Paused, errorMessage = null)
            } else item
        if (transfer.item.status == DownloadStatus.Downloading && Build.VERSION.SDK_INT >= 34 &&
            jobScheduler().getPendingJob(transfer.jobId) == null) {
            pause(item.fileName)
            transfer = store.get(item.fileName) ?: transfer
        }
        return transfer.item
    }

    fun pause(fileName: String) {
        val transfer = store.get(fileName) ?: return
        if (transfer.item.status != DownloadStatus.Downloading) return
        val paused = store.update(fileName, transfer.generation) {
            it.copy(item = it.item.copy(
                status = DownloadStatus.Paused,
                bytesPerSecond = 0L,
                etaSeconds = null,
                errorMessage = null,
            ))
        }
        cancelScheduled(transfer)
        paused?.let { DownloadsLiveStatusPlatform.notifyTransfer(it.item) }
    }

    fun remove(fileName: String) {
        val transfer = store.get(fileName)
        store.remove(fileName)
        transfer?.let(::cancelScheduled)
        transfer?.let { DownloadsLiveStatusPlatform.removeNotification(it.item.id) }
        cleanupScope.launch {
            lock(fileName).withLock {
                if (store.get(fileName) == null) {
                    File(directory, "$fileName.part").delete()
                    File(directory, "$fileName.dash").deleteRecursively()
                    DownloadSubtitleStorage(File(directory, fileName).toURI().toString()).remove()
                }
            }
        }
    }

    private fun cancelScheduled(transfer: AndroidDownloadTransfer) {
        if (Build.VERSION.SDK_INT >= 34) jobScheduler().cancel(transfer.jobId)
        else WorkManager.getInstance(context).cancelUniqueWork(workName(transfer))
    }

    suspend fun execute(
        transfer: AndroidDownloadTransfer,
        network: Network? = null,
        onProgress: (AndroidDownloadTransfer) -> Unit,
    ): Boolean = lock(transfer.item.fileName).withLock {
        val fileName = transfer.item.fileName
        if (!isActive(transfer)) return@withLock false
        val destination = File(directory, fileName)
        val client = if (network != null) {
            downloadHttpClient.newBuilder()
                .socketFactory(network.socketFactory)
                .dns { network.getAllByName(it).toList() }
                .connectionPool(ConnectionPool())
                .build()
        } else downloadHttpClient
        try {
            if (transfer.item.dashPlan) {
                // The job runs on the main dispatcher; every network read, the
                // muxer and the copy into the public folder are disk/IO work.
                return@withLock withContext(Dispatchers.IO) {
                    executeDashPlan(transfer, client, onProgress)
                }
            }
            DownloadSubtitles.prepare(transfer.item, destination.toURI().toString())
            currentCoroutineContext().ensureActive()
            if (!isActive(transfer)) return@withLock false
            var lastProgressAt = 0L
            val rate = DownloadRateEstimator()
            // A partial file already on disk is progress this attempt starts
            // with, so the bar opens where the last attempt stopped.
            val seedBytes = if (destination.isFile) destination.length() else 0L
            if (seedBytes > 0L) {
                lastProgressAt = SystemClock.elapsedRealtime()
                rate.sample(lastProgressAt, seedBytes, transfer.item.totalBytes)
                updateActive(transfer) {
                    it.copy(item = it.item.copy(downloadedBytes = seedBytes))
                }?.let(onProgress)
            }
            val partial = if (destination.isFile) destination else transferAndroidDownload(
                item = transfer.item,
                directory = directory,
                validator = transfer.validator,
                client = client,
                onHeaders = { total, validator ->
                    updateActive(transfer) { it.copy(validator = validator, item = it.item.copy(totalBytes = total)) }
                },
                onProgress = { bytes, total ->
                    val now = SystemClock.elapsedRealtime()
                    if (now - lastProgressAt >= REPORT_INTERVAL_MS || bytes == total) {
                        lastProgressAt = now
                        val sample = rate.sample(now, bytes, total)
                        updateActive(transfer) {
                            it.copy(item = it.item.copy(
                                downloadedBytes = bytes,
                                totalBytes = total,
                                bytesPerSecond = sample.bytesPerSecond,
                                etaSeconds = sample.etaSeconds,
                            ))
                        }?.let(onProgress)
                    }
                },
            )
            currentCoroutineContext().ensureActive()
            if (partial != destination && !partial.renameTo(destination)) {
                throw IOException("Could not finalize the downloaded file")
            }
            val bytes = destination.length()
            // Copying a finished episode into Download/HOFLIX is a file copy,
            // not something to run on the caller's dispatcher.
            val localUri = withContext(Dispatchers.IO) {
                publishToPublicFolder(destination, transfer.item)
            }
            updateActive(transfer) { current ->
                current.copy(item = current.item.copy(
                    status = DownloadStatus.Completed,
                    localFileUri = localUri,
                    downloadedBytes = bytes,
                    totalBytes = bytes,
                    bytesPerSecond = 0L,
                    etaSeconds = null,
                    merging = false,
                    errorMessage = null,
                ))
            }
            false
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            currentCoroutineContext().ensureActive()
            if (!isActive(transfer)) return@withLock false
            val retry = shouldRetryAndroidDownload(error, transfer.retryCount)
            if (retry) updateActive(transfer) { it.copy(retryCount = it.retryCount + 1) }
            else fail(transfer, error)
            retry
        } finally {
            if (network != null) withContext(NonCancellable + Dispatchers.IO) { client.connectionPool.evictAll() }
        }
    }

    /**
     * MovieBox-style download: pull the DASH segments straight from the CDN,
     * join video and audio on the phone, then hand the MP4 to the public
     * Download/HOFLIX folder. Nothing but the segment plan passes through the
     * bridge, so one episode costs one download instead of two.
     */
    private suspend fun executeDashPlan(
        transfer: AndroidDownloadTransfer,
        client: OkHttpClient,
        onProgress: (AndroidDownloadTransfer) -> Unit,
    ): Boolean {
        val fileName = transfer.item.fileName
        val workDir = File(directory, "$fileName.dash").apply { mkdirs() }
        var lastReportAt = 0L
        var estimateBytes = 0L
        // Absolute byte accounting: seeds from what is already on disk, then
        // counts segments as the network delivers them. Never additive across
        // attempts, which is what used to make a resumed transfer jump ahead of
        // the bytes it had really pulled.
        var ledger = DashByteLedger()
        val rate = DownloadRateEstimator()
        // Segment fetchers report from several threads at once, so the counter,
        // the estimator and the state write share one lock.
        val progressLock = Any()

        fun report(merging: Boolean = false, force: Boolean = false) {
            synchronized(progressLock) {
                val now = SystemClock.elapsedRealtime()
                val bytes = ledger.total()
                // An estimate below the bytes already here would peg the bar at
                // 100% while the transfer is still running.
                val total = maxOf(estimateBytes, bytes)
                val sample = rate.sample(now, bytes, total.takeIf { it > 0L })
                if (!force && !merging && now - lastReportAt < REPORT_INTERVAL_MS) return
                lastReportAt = now
                updateActive(transfer) { current ->
                    current.copy(
                        item = current.item.copy(
                            downloadedBytes = bytes,
                            totalBytes = total.takeIf { it > 0L } ?: current.item.totalBytes,
                            bytesPerSecond = sample.bytesPerSecond,
                            etaSeconds = sample.etaSeconds,
                            merging = merging,
                        ),
                    )
                }?.let(onProgress)
            }
        }

        fun applyPlan(plan: DashDownloadPlan) {
            synchronized(progressLock) {
                estimateBytes = plan.totalBytes
                ledger = DashByteLedger(dashSeededBytes(workDir, plan))
                rate.reset()
            }
        }

        var plan = loadCachedDashPlan(workDir, transfer.item.sourceUrl)
            ?: fetchDashPlan(client, transfer.item.sourceUrl).also {
                saveCachedDashPlan(workDir, transfer.item.sourceUrl, it)
            }
        applyPlan(plan)
        // Reported before the first request so the card opens at the resume
        // point instead of sitting at "preparing" and then leaping forward.
        report(force = true)

        fun onChanged() = report()

        val videoFile: File
        val audioFile: File?
        try {
            videoFile = downloadDashTrack(
                client, plan, plan.video, workDir, ledger = ledger, onChanged = ::onChanged,
            )
            audioFile = plan.audio?.takeIf { it.segments.isNotEmpty() }?.let { track ->
                downloadDashTrack(
                    client, plan, track, workDir, ledger = ledger, onChanged = ::onChanged,
                )
            }
        } catch (expired: DashHttpException) {
            if (expired.statusCode != 401 && expired.statusCode != 403) throw expired
            // The CDN signature lapsed mid-download: ask for a fresh one and
            // keep the segments that already landed. The counters restart from
            // the checkpoints, so nothing is counted twice.
            plan = fetchDashPlan(client, transfer.item.sourceUrl, refresh = true).also {
                saveCachedDashPlan(workDir, transfer.item.sourceUrl, it)
            }
            applyPlan(plan)
            report(force = true)
            val retryVideo = downloadDashTrack(
                client, plan, plan.video, workDir, ledger = ledger, onChanged = ::onChanged,
            )
            val retryAudio = plan.audio?.takeIf { it.segments.isNotEmpty() }?.let { track ->
                downloadDashTrack(
                    client, plan, track, workDir, ledger = ledger, onChanged = ::onChanged,
                )
            }
            return finishDashPlan(plan, transfer, workDir, retryVideo, retryAudio, onProgress)
        }
        currentCoroutineContext().ensureActive()
        return finishDashPlan(plan, transfer, workDir, videoFile, audioFile, onProgress)
    }

    private suspend fun finishDashPlan(
        plan: DashDownloadPlan,
        transfer: AndroidDownloadTransfer,
        workDir: File,
        videoFile: File,
        audioFile: File?,
        onProgress: (AndroidDownloadTransfer) -> Unit,
    ): Boolean {
        val fileName = transfer.item.fileName
        updateActive(transfer) {
            it.copy(item = it.item.copy(merging = true, bytesPerSecond = 0L, etaSeconds = null))
        }?.let(onProgress)
        val muxed = File(directory, "$fileName.muxing.mp4")
        // A transport stream that the platform muxer refuses is still playable,
        // so it is kept as-is instead of losing the whole download.
        val transport = File(directory, "$fileName.muxing.ts")
        var completed = false
        try {
            val finished: File = if (plan.kind == "hls") {
                val remuxed = runCatching { muxTransportStream(videoFile, muxed) }.isSuccess
                if (remuxed) {
                    videoFile.delete()
                    muxed
                } else {
                    if (transport.exists()) transport.delete()
                    if (!videoFile.renameTo(transport)) {
                        videoFile.copyTo(transport, overwrite = true)
                        videoFile.delete()
                    }
                    transport
                }
            } else {
                muxDashTracks(videoFile, audioFile, muxed)
                // The joined file is what gets kept; the two raw tracks are only
                // input, and dropping them now halves the peak disk usage.
                videoFile.delete()
                audioFile?.delete()
                muxed
            }
            val bytes = finished.length()
            val localUri = publishToPublicFolder(finished, transfer.item)
            // Captions are stored next to the finished file, the way the
            // progressive path already did, so an offline episode keeps its
            // subtitle menu.
            runCatching { DownloadSubtitles.prepare(transfer.item, localUri) }
            updateActive(transfer) { current ->
                current.copy(
                    item = current.item.copy(
                        status = DownloadStatus.Completed,
                        localFileUri = localUri,
                        downloadedBytes = bytes,
                        totalBytes = bytes,
                        bytesPerSecond = 0L,
                        etaSeconds = null,
                        merging = false,
                        errorMessage = null,
                    ),
                )
            }?.let(onProgress)
            completed = true
        } finally {
            muxed.delete()
            transport.delete()
            // A failed join keeps the raw track and its checkpoint, so the retry
            // goes straight to muxing instead of pulling every segment again.
            if (completed) workDir.deleteRecursively()
        }
        return false
    }

    private fun publishToPublicFolder(file: File, item: DownloadItem): String {
        val uri = PublicDownloads.publish(context, file, downloadDisplayName(item, file.name))
        val keepsSource = uri.path == file.absolutePath
        if (!keepsSource && file.isFile) file.delete()
        return uri.toString()
    }

    fun isActive(transfer: AndroidDownloadTransfer): Boolean = store.get(transfer.item.fileName)?.let {
        it.generation == transfer.generation && it.item.status == DownloadStatus.Downloading
    } == true

    fun fail(transfer: AndroidDownloadTransfer, error: Exception) {
        // The card shows this text verbatim, so a failing segment names itself
        // instead of collapsing into a generic "download failed".
        val reason = error.message?.takeIf { it.isNotBlank() } ?: "Unduhan gagal"
        updateActive(transfer) {
            it.copy(item = it.item.copy(
                status = DownloadStatus.Failed,
                bytesPerSecond = 0L,
                etaSeconds = null,
                errorMessage = "Gagal: $reason",
            ))
        }
    }

    fun notifyCurrent(fileName: String) {
        store.get(fileName)?.let { DownloadsLiveStatusPlatform.notifyTransfer(it.item) }
    }

    private fun updateActive(
        transfer: AndroidDownloadTransfer,
        update: (AndroidDownloadTransfer) -> AndroidDownloadTransfer,
    ): AndroidDownloadTransfer? = store.update(transfer.item.fileName, transfer.generation) {
        if (it.item.status == DownloadStatus.Downloading) {
            update(it).let { changed -> changed.copy(item = changed.item.copy(updatedAtEpochMs = System.currentTimeMillis())) }
        } else it
    }

    private fun lock(fileName: String): Mutex = locks.getOrPut(fileName) { Mutex() }
    private fun workName(transfer: AndroidDownloadTransfer) = "nuvio-download-${transfer.jobId}"

    companion object {
        const val FILE_NAME = "download_file_name"
        const val GENERATION = "download_generation"
        const val JOB_NAMESPACE = "nuvio-downloads"
        /** How often the byte counter is flushed to the card and notification. */
        const val REPORT_INTERVAL_MS = 700L
    }
}

internal fun shouldRetryAndroidDownload(error: Exception, retries: Int): Boolean =
    retries < 4 && when (error) {
        is DownloadHttpException -> error.statusCode == 408 || error.statusCode == 429 || error.statusCode in 500..599
        is IOException -> true
        else -> false
    }

/** `HOFLIX - Dark - S01E01 - 1080p.mp4`, the name the file manager shows. */
internal fun downloadDisplayName(item: DownloadItem, fallback: String): String {
    val quality = item.streamTitle.substringBefore(' ').trim()
    val base = buildString {
        append("HOFLIX - ")
        append(item.title.trim().ifBlank { "Unduhan" })
        if (item.seasonNumber != null && item.episodeNumber != null) {
            append(" - S")
            append(item.seasonNumber.toString().padStart(2, '0'))
            append('E')
            append(item.episodeNumber.toString().padStart(2, '0'))
        }
        if (quality.isNotEmpty()) {
            append(" - ")
            append(quality)
        }
    }
    val safe = base.replace(Regex("[/\\\\:*?\"<>|]"), "_").trim().take(120)
    val extension = fallback.substringAfterLast('.', "mp4").lowercase()
    return "$safe.$extension"
}
