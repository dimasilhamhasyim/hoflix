package com.nuvio.app.features.downloads

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * How far one track's assembly got. `bytes` is the trusted length of the track
 * file: on resume everything past it is truncated before appending, so a crash
 * between "bytes written" and "progress saved" cannot leave a duplicated
 * fragment in the joined file.
 */
@Serializable
internal data class DashTrackProgress(
    val fingerprint: String = "",
    val nextIndex: Int = 0,
    val bytes: Long = 0L,
)

/** Identifies the segment list, so a changed plan restarts instead of mixing. */
internal fun dashTrackFingerprint(track: DashPlanTrack): String {
    // HLS segment names carry a signed query that changes every time the plan is
    // refreshed. Comparing the path only keeps a resumed transfer resumable
    // instead of throwing away work when the same CDN token is rotated.
    fun path(name: String): String = name.substringBefore('?')
    return "${track.id}|${track.init}|${track.segments.size}|" +
        "${path(track.segments.firstOrNull() ?: "")}|${path(track.segments.lastOrNull() ?: "")}"
}

internal fun dashTrackNames(track: DashPlanTrack): List<String> = buildList {
    if (track.init.isNotBlank()) add(track.init)
    addAll(track.segments)
}

/**
 * Pure resume decision. Returns the point the assembly must continue from.
 *
 * A saved checkpoint is only honoured when it belongs to the same segment list
 * and the file on disk still holds at least the bytes it claims. A shorter file
 * means the tail was lost, and there is no way to know where the last complete
 * fragment ended, so that track starts over instead of producing a broken file.
 */
internal fun resolveDashTrackResume(
    saved: DashTrackProgress?,
    fingerprint: String,
    nameCount: Int,
    fileLength: Long,
): DashTrackProgress {
    if (saved == null || saved.fingerprint != fingerprint) return DashTrackProgress(fingerprint, 0, 0L)
    if (saved.bytes <= 0L || saved.nextIndex <= 0) return DashTrackProgress(fingerprint, 0, 0L)
    if (saved.nextIndex > nameCount) return DashTrackProgress(fingerprint, 0, 0L)
    if (fileLength < saved.bytes) return DashTrackProgress(fingerprint, 0, 0L)
    return saved
}

internal fun readDashTrackProgress(file: File): DashTrackProgress? = runCatching {
    dashPlanJson.decodeFromString(DashTrackProgress.serializer(), file.readText())
}.getOrNull()

internal fun writeDashTrackProgress(file: File, progress: DashTrackProgress) {
    runCatching {
        val temporary = File(file.parentFile, "${file.name}.tmp")
        temporary.writeText(dashPlanJson.encodeToString(progress))
        if (!temporary.renameTo(file)) {
            file.delete()
            temporary.renameTo(file)
        }
    }
}

/**
 * Pulls one DASH track (its init segment plus every media segment) straight
 * from the CDN.
 *
 * Segments are fetched a few at a time and appended to the track file strictly
 * in order, so the result is a single fragmented MP4 the muxer can read. Every
 * appended segment is checkpointed on disk, and a segment is only deleted once
 * that checkpoint is written. A dropped connection therefore continues from the
 * last finished segment instead of restarting the episode.
 *
 * Progress comes from [ledger], which counts bytes as the network delivers them
 * — including the segments still arriving — instead of only when a whole
 * segment has been joined. That is what keeps the indicator moving at the same
 * rate the connection is working at.
 */
internal suspend fun downloadDashTrack(
    client: OkHttpClient,
    plan: DashDownloadPlan,
    track: DashPlanTrack,
    workDir: File,
    concurrency: Int = 3,
    ledger: DashByteLedger,
    onChanged: () -> Unit,
): File = coroutineScope {
    val names = dashTrackNames(track)
    if (names.isEmpty()) throw IOException("DASH track has no segments")
    val output = File(workDir, "track_${track.id}.mp4")
    val checkpoint = File(workDir, "track_${track.id}.progress.json")
    val fingerprint = dashTrackFingerprint(track)
    val state = readDashTrackState(workDir, track)
    val resumed = state.progress
    if (resumed.nextIndex > 0) {
        runCatching { RandomAccessFile(output, "rw").use { it.setLength(resumed.bytes) } }
    } else {
        output.delete()
        // Segments left by a different plan cannot be checked against this one,
        // so they are dropped rather than silently appended.
        workDir.listFiles { file -> file.name.startsWith("seg_${track.id}_") }?.forEach(File::delete)
    }
    writeDashTrackProgress(checkpoint, resumed)

    val files = arrayOfNulls<File>(names.size)
    // Segments an earlier attempt finished are part of the seeded byte count.
    state.reusedSegments.forEach { (index, file) -> files[index] = file }
    val next = AtomicInteger(resumed.nextIndex)
    val permits = Semaphore(concurrency * 2)
    // Only a segment this attempt actually fetches holds a permit, so the
    // semaphore is never over-released by a reused segment.
    val holdsPermit = java.util.Collections.synchronizedSet(HashSet<Int>())
    val fetchers = List(concurrency.coerceAtLeast(1)) {
        launch(Dispatchers.IO) {
            while (true) {
                val index = next.getAndIncrement()
                if (index >= names.size) break
                // A segment an earlier attempt finished is already accounted for.
                if (files[index] != null) continue
                permits.acquire()
                holdsPermit.add(index)
                var delivered = false
                var part: File? = null
                try {
                    var attempt = 0
                    while (true) {
                        ensureActive()
                        try {
                            val target = dashPartFile(workDir, track.id, index)
                            part = target
                            // A body interrupted at byte N must never be treated
                            // as a finished segment, so it is thrown away and its
                            // reported bytes are withdrawn before the retry.
                            target.delete()
                            ledger.dropPart(track.id, index)
                            onChanged()
                            fetchDashSegment(
                                client,
                                plan.urlOf(names[index]),
                                plan.cookie,
                                target,
                                userAgent = plan.userAgent,
                            ) { written ->
                                ledger.partBytes(track.id, index, written)
                                onChanged()
                            }
                            val finished = dashSegmentFile(workDir, track.id, index)
                            if (!target.renameTo(finished)) throw IOException("segment rename failed")
                            ledger.finishPart(track.id, index, finished.length())
                            files[index] = finished
                            delivered = true
                            onChanged()
                            break
                        } catch (error: DashHttpException) {
                            // A CDN edge occasionally refuses a valid HLS token
                            // for a moment; that is a retry, not a dead plan.
                            val retryableHlsRefusal =
                                plan.kind == "hls" && error.statusCode == 403
                            if (!retryableHlsRefusal && (error.statusCode == 401 || error.statusCode == 403)) throw error
                            // A track can end a few segments before the
                            // template says; treat a tail 404 as the end
                            // instead of failing the episode.
                            if (error.statusCode == 404 && index >= names.size - 4) {
                                val empty = dashSegmentFile(workDir, track.id, index)
                                empty.writeBytes(ByteArray(0))
                                ledger.dropPart(track.id, index)
                                files[index] = empty
                                delivered = true
                                onChanged()
                                break
                            }
                            if (!retryableHlsRefusal && (error.statusCode in 400..499 || attempt >= 3)) throw error
                            if (retryableHlsRefusal && attempt >= 5) throw error
                        } catch (error: IOException) {
                            if (attempt >= 4) throw error
                        }
                        attempt += 1
                        delay(300L * attempt)
                    }
                } finally {
                    if (!delivered) {
                        part?.let { ledger.dropPart(track.id, index) }
                        if (holdsPermit.remove(index)) permits.release()
                    }
                }
            }
        }
    }
    var written = resumed.bytes
    try {
        FileOutputStream(output, resumed.nextIndex > 0).use { sink ->
            for (index in resumed.nextIndex until names.size) {
                while (files[index] == null) {
                    ensureActive()
                    delay(20L)
                }
                val segment = files[index] ?: throw IOException("segment missing")
                val length = segment.length()
                segment.inputStream().use { input -> input.copyTo(sink, 256 * 1024) }
                sink.flush()
                written += length
                // Checkpoint before deleting: a restart then never re-appends a
                // segment it already copied, and never loses one it has not.
                writeDashTrackProgress(checkpoint, DashTrackProgress(fingerprint, index + 1, written))
                segment.delete()
                files[index] = null
                if (holdsPermit.remove(index)) permits.release()
            }
            sink.fd.sync()
        }
        // The checkpoint stays: a mux failure later can retry without pulling
        // the whole track again, since it still points past the last segment.
        // Stray segments from a longer previous attempt are dead weight.
        workDir.listFiles { file -> file.name.startsWith("seg_${track.id}_") }
            ?.forEach(File::delete)
    } finally {
        fetchers.forEach { it.cancel() }
    }
    output
}

/**
 * Streams one segment to [destination], reporting every chunk it has written so
 * the transfer's byte counter follows the network instead of stepping once per
 * finished segment.
 */
internal suspend fun fetchDashSegment(
    client: OkHttpClient,
    url: String,
    cookie: String,
    destination: File,
    userAgent: String = "",
    onBytes: (Long) -> Unit = {},
) = withContext(Dispatchers.IO) {
    val request = Request.Builder()
        .url(url)
        .apply { if (cookie.isNotBlank()) header("Cookie", cookie) }
        // Some CDNs pin the signed token to the client that requested it, and
        // refuse any other User-Agent even when the token is still valid.
        .header("User-Agent", userAgent.ifBlank { "okhttp/4.12.0" })
        .build()
    client.newCall(request).execute().use { response ->
        if (!response.isSuccessful) {
            throw DashHttpException(response.code, url.substringAfterLast('/'))
        }
        val body = response.body ?: throw IOException("empty segment")
        var written = 0L
        destination.outputStream().buffered(256 * 1024).use { sink ->
            body.byteStream().use { input ->
                val buffer = ByteArray(256 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    sink.write(buffer, 0, read)
                    written += read
                    onBytes(written)
                }
            }
        }
    }
}
