package com.nuvio.app.features.streams

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

object StreamParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(
        payload: String,
        addonName: String,
        addonId: String,
        addonLogo: String? = null,
    ): List<StreamItem> {
        val root = json.parseToJsonElement(payload).jsonObject
        val streamsArray = root["streams"] as? JsonArray ?: return emptyList()
        return streamsArray.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val url = obj.string("url")
            val infoHash = obj.string("infoHash")
            val externalUrl = obj.string("externalUrl")
            val ytId = obj.string("ytId")?.trim()?.takeIf { it.isNotEmpty() }
            val clientResolve = obj.objectValue("clientResolve")?.toClientResolve()
            val hfxResolve = obj.objectValue("hfxResolve")?.toHfxResolve()

            // Must have at least one source or external target.
            if (url == null && infoHash == null && externalUrl == null && ytId == null &&
                clientResolve == null && hfxResolve == null
            ) {
                return@mapNotNull null
            }

            val hintsObj = obj["behaviorHints"] as? JsonObject
            val proxyHeaders = hintsObj
                ?.objectValue("proxyHeaders")
                ?.toProxyHeaders()
            StreamItem(
                name = obj.string("name"),
                title = obj.string("title"),
                description = obj.string("description") ?: obj.string("title"),
                url = url,
                infoHash = infoHash,
                fileIdx = obj.int("fileIdx"),
                externalUrl = externalUrl,
                ytId = ytId,
                sources = obj.stringList("sources"),
                addonName = addonName,
                addonId = addonId,
                addonLogo = addonLogo,
                streamType = normalizeStreamType(obj.string("type")),
                clientResolve = clientResolve,
                hfxResolve = hfxResolve,
                behaviorHints = StreamBehaviorHints(
                    bingeGroup = hintsObj?.string("bingeGroup"),
                    notWebReady = (hintsObj?.boolean("notWebReady") ?: false) || proxyHeaders != null,
                    videoHash = hintsObj?.string("videoHash"),
                    videoSize = hintsObj?.long("videoSize"),
                    filename = hintsObj?.string("filename"),
                    proxyHeaders = proxyHeaders,
                    audioId = hintsObj?.string("audioId"),
                    audioLabel = hintsObj?.string("audioLabel"),
                    audioLang = hintsObj?.string("audioLang"),
                    audioOriginal = hintsObj?.boolean("audioOriginal") ?: false,
                    qualityHeight = hintsObj?.int("qualityHeight"),
                    adaptive = hintsObj?.boolean("adaptive") ?: false,
                    sizeBytes = hintsObj?.long("sizeBytes") ?: hintsObj?.long("videoSize"),
                    downloadVariants = hintsObj?.downloadVariantList("downloadVariants").orEmpty(),
                    downloadTemplate = hintsObj?.string("downloadTemplate")
                        ?.takeIf { it.isNotBlank() },
                    downloadSizesTemplate = hintsObj?.string("downloadSizesTemplate")
                        ?.takeIf { it.isNotBlank() },
                    downloadPlanTemplate = hintsObj?.string("downloadPlanTemplate")
                        ?.takeIf { it.isNotBlank() },
                ),
                externalSubtitles = obj.subtitleList("subtitles"),
            )
        }
    }

    private fun JsonObject.string(name: String): String? =
        this[name]?.jsonPrimitive?.contentOrNull

    private fun JsonObject.int(name: String): Int? =
        this[name]?.jsonPrimitive?.let { primitive ->
            primitive.intOrNull ?: primitive.contentOrNull?.toIntOrNull()
        }

    private fun JsonObject.long(name: String): Long? =
        this[name]?.jsonPrimitive?.let { primitive ->
            primitive.longOrNull ?: primitive.contentOrNull?.toLongOrNull()
        }

    private fun JsonObject.boolean(name: String): Boolean? =
        this[name]?.jsonPrimitive?.booleanOrNull

    private fun JsonObject.objectValue(name: String): JsonObject? =
        this[name] as? JsonObject

    private fun JsonObject.stringList(name: String): List<String> =
        (this[name] as? JsonArray)
            ?.mapNotNull { it.jsonPrimitive.contentOrNull?.takeIf(String::isNotBlank) }
            .orEmpty()

    /**
     * Downloadable resolutions the addon advertises for this stream. Each entry
     * carries the byte size so the download sheet shows real numbers, and a URL
     * for the episode that was resolved.
     */
    private fun JsonObject.downloadVariantList(name: String): List<StreamDownloadVariant> =
        (this[name] as? JsonArray)
            ?.mapNotNull { element ->
                val obj = element as? JsonObject ?: return@mapNotNull null
                val height = obj.int("height") ?: 0
                if (height <= 0) return@mapNotNull null
                StreamDownloadVariant(
                    height = height,
                    sizeBytes = obj.long("sizeBytes"),
                    url = obj.string("url")?.takeIf { it.isNotBlank() },
                    plan = obj.string("plan")?.takeIf { it.isNotBlank() },
                )
            }
            .orEmpty()

    /**
     * Subtitles an addon ships on the stream itself (MovieBox-style per-video
     * captions). Stremio's spec allows `subtitles` next to `url`, and those
     * tracks belong to the playable item, so they seed the player's CC list
     * without a second subtitle request.
     */
    private fun JsonObject.subtitleList(name: String): List<StreamSubtitle> =
        (this[name] as? JsonArray)
            ?.mapNotNull { element ->
                val obj = element as? JsonObject ?: return@mapNotNull null
                val url = obj.string("url")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val language = obj.string("lang")
                    ?: obj.string("language")
                    ?: obj.string("languageCode")
                    ?: obj.string("locale")
                    ?: "unknown"
                StreamSubtitle(
                    url = url,
                    language = language,
                    name = obj.string("name") ?: obj.string("label"),
                )
            }
            .orEmpty()

    private fun JsonObject.intList(name: String): List<Int> =
        (this[name] as? JsonArray)
            ?.mapNotNull { it.jsonPrimitive.intOrNull }
            .orEmpty()

    private fun JsonObject.stringMap(): Map<String, String> =
        entries.mapNotNull { (key, value) ->
            (value as? JsonPrimitive)?.contentOrNull
                ?.takeIf { it.isNotBlank() }
                ?.let { key to it }
        }.toMap()

    private fun JsonObject.toProxyHeaders(): StreamProxyHeaders? {
        val requestHeaders = objectValue("request")?.stringMap().orEmpty().takeIf { it.isNotEmpty() }
        val responseHeaders = objectValue("response")?.stringMap().orEmpty().takeIf { it.isNotEmpty() }
        if (requestHeaders == null && responseHeaders == null) {
            return null
        }
        return StreamProxyHeaders(
            request = requestHeaders,
            response = responseHeaders,
        )
    }

    private fun JsonObject.toHfxResolve(): HfxResolveSpec =
        HfxResolveSpec(
            provider = string("provider").orEmpty(),
            kind = string("kind") ?: "movie",
            targetId = string("targetId") ?: string("uuid").orEmpty(),
            season = int("season") ?: 0,
            episode = int("episode") ?: 0,
            playInfoUrl = string("playInfoUrl").orEmpty(),
            apiBase = string("apiBase").orEmpty(),
            webBase = string("webBase").orEmpty(),
            userAgent = string("userAgent").orEmpty(),
            playlistBase = string("playlistBase").orEmpty(),
            playToken = string("playToken").orEmpty(),
        )

    private fun JsonObject.toClientResolve(): StreamClientResolve =
        StreamClientResolve(
            type = string("type"),
            infoHash = string("infoHash"),
            fileIdx = int("fileIdx"),
            magnetUri = string("magnetUri"),
            sources = stringList("sources"),
            torrentName = string("torrentName"),
            filename = string("filename"),
            mediaType = string("mediaType"),
            mediaId = string("mediaId"),
            mediaOnlyId = string("mediaOnlyId"),
            title = string("title"),
            season = int("season"),
            episode = int("episode"),
            service = string("service"),
            serviceIndex = int("serviceIndex"),
            serviceExtension = string("serviceExtension"),
            isCached = boolean("isCached"),
            stream = objectValue("stream")?.toClientResolveStream(),
        )

    private fun JsonObject.toClientResolveStream(): StreamClientResolveStream =
        StreamClientResolveStream(
            raw = objectValue("raw")?.toClientResolveRaw(),
        )

    private fun JsonObject.toClientResolveRaw(): StreamClientResolveRaw =
        StreamClientResolveRaw(
            torrentName = string("torrentName"),
            filename = string("filename"),
            size = long("size"),
            folderSize = long("folderSize"),
            tracker = string("tracker"),
            indexer = string("indexer"),
            network = string("network"),
            parsed = objectValue("parsed")?.toClientResolveParsed(),
        )

    private fun JsonObject.toClientResolveParsed(): StreamClientResolveParsed =
        StreamClientResolveParsed(
            rawTitle = string("raw_title"),
            parsedTitle = string("parsed_title"),
            year = int("year"),
            resolution = string("resolution"),
            seasons = intList("seasons"),
            episodes = intList("episodes"),
            quality = string("quality"),
            hdr = stringList("hdr"),
            codec = string("codec"),
            audio = stringList("audio"),
            channels = stringList("channels"),
            languages = stringList("languages"),
            group = string("group"),
            network = string("network"),
            edition = string("edition"),
            duration = long("duration"),
            bitDepth = string("bit_depth"),
            extended = boolean("extended"),
            theatrical = boolean("theatrical"),
            remastered = boolean("remastered"),
            unrated = boolean("unrated"),
        )
}
