package com.nuvio.app.features.downloads

import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer

/**
 * Joins the downloaded video and audio tracks into one MP4.
 *
 * DASH ships the two tracks separately; MovieBox joins them on the device with
 * its own SDK, and this does the same with the platform muxer — no ffmpeg
 * shipped in the APK. Both inputs are fragmented MP4, which MediaExtractor
 * reads since Android 7.
 */
internal fun muxDashTracks(videoFile: File, audioFile: File?, outputFile: File) {
    if (outputFile.exists()) outputFile.delete()
    val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    val extractors = mutableListOf<MediaExtractor>()
    var started = false
    try {
        val videoExtractor = MediaExtractor().apply { setDataSource(videoFile.absolutePath) }
        extractors += videoExtractor
        val videoTrack = firstTrackOfType(videoExtractor, "video/")
            ?: throw IOException("video track is missing from the downloaded file")
        val videoOut = muxer.addTrack(videoExtractor.getTrackFormat(videoTrack))
        var audioExtractor: MediaExtractor? = null
        var audioOut = -1
        if (audioFile != null && audioFile.isFile && audioFile.length() > 0L) {
            audioExtractor = MediaExtractor().apply { setDataSource(audioFile.absolutePath) }
            extractors += audioExtractor
            val audioTrack = firstTrackOfType(audioExtractor, "audio/")
            if (audioTrack >= 0) {
                audioOut = muxer.addTrack(audioExtractor.getTrackFormat(audioTrack))
            }
        }
        muxer.start()
        started = true
        copyTrack(videoExtractor, videoTrack, muxer, videoOut)
        if (audioExtractor != null && audioOut >= 0) {
            copyTrack(audioExtractor, firstTrackOfType(audioExtractor, "audio/"), muxer, audioOut)
        }
    } finally {
        if (started) runCatching { muxer.stop() }
        runCatching { muxer.release() }
        extractors.forEach { runCatching { it.release() } }
    }
}

private fun firstTrackOfType(extractor: MediaExtractor, prefix: String): Int {
    for (index in 0 until extractor.trackCount) {
        val mime = extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME) ?: continue
        if (mime.startsWith(prefix)) return index
    }
    return -1
}

private fun copyTrack(
    extractor: MediaExtractor,
    trackIndex: Int,
    muxer: MediaMuxer,
    outputTrack: Int,
) {
    extractor.selectTrack(trackIndex)
    val format = extractor.getTrackFormat(trackIndex)
    // The two-argument getInteger only exists from API 29; minSdk is 24.
    val declared = if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
        format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
    } else {
        0
    }
    val capacity = declared.coerceAtLeast(4 * 1024 * 1024)
    val buffer = ByteBuffer.allocateDirect(capacity)
    val sampleInfo = android.media.MediaCodec.BufferInfo()
    while (true) {
        sampleInfo.offset = 0
        sampleInfo.size = 0
        sampleInfo.flags = 0
        buffer.clear()
        val size = extractor.readSampleData(buffer, 0)
        if (size < 0) break
        sampleInfo.size = size
        sampleInfo.presentationTimeUs = extractor.sampleTime
        sampleInfo.flags = sampleFlags(extractor.sampleFlags)
        muxer.writeSampleData(outputTrack, buffer, sampleInfo)
        if (!extractor.advance()) break
    }
    extractor.unselectTrack(trackIndex)
}

private fun sampleFlags(flags: Int): Int {
    var result = 0
    if (flags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) {
        result = result or android.media.MediaCodec.BUFFER_FLAG_KEY_FRAME
    }
    return result
}

/**
 * Remuxes a concatenated MPEG-TS file into MP4.
 *
 * LokTV ships HLS: every segment already carries video and audio interleaved,
 * so there is nothing to pair up. One extractor per track reads the same source
 * file, and the muxer writes a normal MP4 the gallery and the offline player
 * both understand.
 */
internal fun muxTransportStream(inputFile: File, outputFile: File) {
    if (outputFile.exists()) outputFile.delete()
    val tracks = mutableListOf<Int>()
    val probe = MediaExtractor()
    try {
        probe.setDataSource(inputFile.absolutePath)
        for (index in 0 until probe.trackCount) {
            val mime = probe.getTrackFormat(index).getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("video/") || mime.startsWith("audio/")) tracks += index
        }
    } finally {
        runCatching { probe.release() }
    }
    if (tracks.isEmpty()) throw IOException("no playable track in the transport stream")
    val muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    val extractors = mutableListOf<MediaExtractor>()
    var started = false
    try {
        val outputs = mutableListOf<Triple<MediaExtractor, Int, Int>>()
        tracks.forEach { index ->
            val extractor = MediaExtractor().apply {
                setDataSource(inputFile.absolutePath)
                selectTrack(index)
            }
            extractors += extractor
            outputs += Triple(extractor, index, muxer.addTrack(extractor.getTrackFormat(index)))
        }
        muxer.start()
        started = true
        outputs.forEach { (extractor, index, outputTrack) ->
            copyTrack(extractor, index, muxer, outputTrack)
        }
    } finally {
        if (started) runCatching { muxer.stop() }
        runCatching { muxer.release() }
        extractors.forEach { runCatching { it.release() } }
    }
}
