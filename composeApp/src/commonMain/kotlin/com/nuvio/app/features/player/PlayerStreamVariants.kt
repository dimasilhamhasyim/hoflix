package com.nuvio.app.features.player

import com.nuvio.app.features.streams.StreamItem
import kotlin.math.abs

/**
 * MovieBox ships every dub as its own playable subject and every quality as its
 * own resource, so the player gets a flat stream list. These helpers turn that
 * list back into the two menus a viewer expects: audio language and quality.
 */
data class PlayerAudioVariant(
    val id: String,
    val label: String,
    val lang: String?,
    val original: Boolean,
)

data class PlayerQualityChoice(
    val height: Int,
    val label: String,
    val stream: StreamItem,
    val automatic: Boolean,
)

object PlayerStreamVariants {
    fun hasVariants(streams: List<StreamItem>): Boolean =
        streams.any { it.behaviorHints.audioId != null }

    fun audioVariants(streams: List<StreamItem>): List<PlayerAudioVariant> {
        val variants = mutableListOf<PlayerAudioVariant>()
        val seen = mutableSetOf<String>()
        for (stream in streams) {
            val hints = stream.behaviorHints
            val id = hints.audioId ?: continue
            if (!seen.add(id)) continue
            variants += PlayerAudioVariant(
                id = id,
                label = hints.audioLabel?.takeIf { it.isNotBlank() }
                    ?: if (hints.audioOriginal) "Original Audio" else "Audio",
                lang = hints.audioLang?.takeIf { it.isNotBlank() },
                original = hints.audioOriginal,
            )
        }
        // Original audio first, every dub after it in catalog order.
        return variants.sortedBy { if (it.original) 0 else 1 }
    }

    fun qualityChoices(streams: List<StreamItem>, audioId: String?): List<PlayerQualityChoice> {
        val pool = streams.filter {
            it.playableDirectUrl != null && it.behaviorHints.audioId == audioId
        }
        if (pool.isEmpty()) return emptyList()
        // Real resolutions only. The adaptive manifest is the best stream for
        // its own height, so it is shown under that height instead of an
        // "automatic" row the viewer has to interpret.
        val heights = pool.mapNotNull { it.behaviorHints.qualityHeight }
            .filter { it > 0 }
            .distinct()
            .sortedDescending()
        val choices = heights.mapNotNull { height ->
            val best = pool.filter { it.behaviorHints.qualityHeight == height }
                .maxByOrNull { if (it.behaviorHints.adaptive) 1 else 0 }
                ?: return@mapNotNull null
            PlayerQualityChoice(
                height = height,
                label = "${height}p",
                stream = best,
                automatic = false,
            )
        }.toMutableList()
        if (choices.isEmpty()) {
            val only = pool.firstOrNull { it.behaviorHints.adaptive } ?: pool.first()
            choices += PlayerQualityChoice(
                height = 0,
                label = only.title?.substringBefore(" •")?.trim().orEmpty().ifBlank { "Sumber" },
                stream = only,
                automatic = false,
            )
        }
        return choices
    }

    /**
     * The stream to play for an audio variant at a wanted height. Height 0 (or
     * `automatic`) keeps the adaptive manifest; a height the variant does not
     * offer falls back to its closest resource so switching audio never drops
     * playback.
     */
    fun streamFor(streams: List<StreamItem>, audioId: String?, height: Int): StreamItem? {
        val pool = streams.filter {
            it.playableDirectUrl != null && it.behaviorHints.audioId == audioId
        }
        if (pool.isEmpty()) return null
        if (height <= 0) {
            return pool.firstOrNull { it.behaviorHints.adaptive } ?: pool.first()
        }
        return pool.filter { it.behaviorHints.qualityHeight == height }
            .maxByOrNull { if (it.behaviorHints.adaptive) 1 else 0 }
            ?: pool.minByOrNull { abs((it.behaviorHints.qualityHeight ?: 0) - height) }
    }

    fun activeAudioId(stream: StreamItem?): String? = stream?.behaviorHints?.audioId

    fun activeIsAutomatic(stream: StreamItem?): Boolean =
        stream?.behaviorHints?.adaptive == true
}
