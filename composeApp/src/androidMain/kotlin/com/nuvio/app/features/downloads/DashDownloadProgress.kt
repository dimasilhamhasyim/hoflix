package com.nuvio.app.features.downloads

import java.io.File

/**
 * One DASH track's resume point plus the segments an earlier attempt already
 * fetched into the work directory and that this attempt will reuse untouched.
 */
internal data class DashTrackResumeState(
    val progress: DashTrackProgress,
    val reusedSegments: Map<Int, File>,
)

internal fun dashSegmentFile(workDir: File, trackId: String, index: Int): File =
    File(workDir, "seg_${trackId}_$index.bin")

/**
 * A segment is written here first and renamed when the body is complete, so a
 * connection that dies mid-segment can never be mistaken for a finished one.
 */
internal fun dashPartFile(workDir: File, trackId: String, index: Int): File =
    File(workDir, "seg_${trackId}_$index.bin.part")

internal fun readDashTrackState(workDir: File, track: DashPlanTrack): DashTrackResumeState {
    val names = dashTrackNames(track)
    val output = File(workDir, "track_${track.id}.mp4")
    val progress = resolveDashTrackResume(
        saved = readDashTrackProgress(File(workDir, "track_${track.id}.progress.json")),
        fingerprint = dashTrackFingerprint(track),
        nameCount = names.size,
        fileLength = output.takeIf(File::isFile)?.length() ?: 0L,
    )
    // Only a checkpoint that still matches this segment list may hand back
    // fetched segments. A different plan makes every leftover untrustworthy.
    if (progress.nextIndex <= 0) return DashTrackResumeState(progress, emptyMap())
    val reused = buildMap {
        for (index in progress.nextIndex until names.size) {
            val file = dashSegmentFile(workDir, track.id, index)
            if (file.isFile && file.length() > 0L) put(index, file)
        }
    }
    return DashTrackResumeState(progress, reused)
}

/**
 * Bytes this plan already holds on disk, read before the first segment is
 * requested. Reporting this up front is what stops the card from sitting at
 * "preparing" and then jumping forward the moment the first segment lands.
 */
internal fun dashSeededBytes(workDir: File, plan: DashDownloadPlan): Long {
    val tracks = listOfNotNull(plan.video, plan.audio?.takeIf { it.segments.isNotEmpty() })
    return tracks.sumOf { track ->
        val state = readDashTrackState(workDir, track)
        state.progress.bytes + state.reusedSegments.values.sumOf { it.length() }
    }
}

/**
 * Absolute byte count for one DASH transfer: checkpointed bytes, finished
 * segments, and the bytes of the segments currently arriving.
 *
 * Every value is *set*, never added, so a refreshed plan or a restarted track
 * cannot report the same byte twice — which is what used to make a resumed
 * download jump ahead of what it had really pulled.
 */
internal class DashByteLedger(private val seedBytes: Long = 0L) {
    private val lock = Any()
    private var finished = 0L
    private val parts = HashMap<String, Long>()

    fun total(): Long = synchronized(lock) { seedBytes + finished + parts.values.sum() }

    fun partBytes(trackId: String, index: Int, bytes: Long) = synchronized(lock) {
        parts[key(trackId, index)] = bytes.coerceAtLeast(0L)
    }

    fun finishPart(trackId: String, index: Int, bytes: Long) = synchronized(lock) {
        parts.remove(key(trackId, index))
        finished += bytes.coerceAtLeast(0L)
    }

    fun dropPart(trackId: String, index: Int) = synchronized(lock) {
        parts.remove(key(trackId, index))
    }

    private fun key(trackId: String, index: Int) = "$trackId:$index"
}

internal data class DownloadRate(val bytesPerSecond: Long, val etaSeconds: Long?)

/**
 * Transfer speed and remaining time from the byte counter.
 *
 * The speed is measured over a short window and decays to zero when nothing
 * arrives, so a paused radio does not keep advertising a rate it no longer
 * has. ETA only exists while bytes are actually moving.
 */
internal class DownloadRateEstimator(
    private val windowMillis: Long = 2_000L,
    private val stallMillis: Long = 2_500L,
) {
    private var windowStartAt = Long.MIN_VALUE
    private var windowStartBytes = 0L
    private var lastGrowthAt = Long.MIN_VALUE
    private var lastBytes = 0L
    private var speed = 0L

    fun reset() {
        windowStartAt = Long.MIN_VALUE
        windowStartBytes = 0L
        lastGrowthAt = Long.MIN_VALUE
        lastBytes = 0L
        speed = 0L
    }

    fun sample(nowMillis: Long, bytes: Long, totalBytes: Long?): DownloadRate {
        val safeBytes = bytes.coerceAtLeast(0L)
        if (windowStartAt == Long.MIN_VALUE || safeBytes < lastBytes) {
            windowStartAt = nowMillis
            windowStartBytes = safeBytes
            lastBytes = safeBytes
            lastGrowthAt = nowMillis
            speed = 0L
            return DownloadRate(0L, null)
        }
        val grew = safeBytes > lastBytes
        if (grew) {
            lastBytes = safeBytes
            lastGrowthAt = nowMillis
        }
        val span = nowMillis - windowStartAt
        if (span >= windowMillis) {
            // Bytes over the window, not an average of averages: the card has
            // to match what the connection is doing right now.
            speed = ((safeBytes - windowStartBytes) * 1_000L / span).coerceAtLeast(0L)
            windowStartAt = nowMillis
            windowStartBytes = safeBytes
        }
        if (!grew && lastGrowthAt != Long.MIN_VALUE && nowMillis - lastGrowthAt > stallMillis) {
            speed = 0L
        }
        val eta = totalBytes
            ?.takeIf { it > safeBytes && speed > 0L }
            ?.let { (it - safeBytes + speed - 1) / speed }
        return DownloadRate(speed, eta)
    }
}
