package com.nuvio.app.features.party

import co.touchlab.kermit.Logger
import com.nuvio.app.core.sync.SyncClientIdentity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlin.random.Random

enum class PartyChatStatus {
    Sent,
    Sending,
    Failed,
}

/** Why a message never left the composer. */
enum class PartyChatIssue {
    /** The realtime room is not ready yet (still connecting or already closed). */
    NotReady,

    /** The server rejected the message. */
    Failed,
}

data class PartyChatNotice(
    val issue: PartyChatIssue,
    /** Bumped per notice so the UI can re-show the same issue twice in a row. */
    val seq: Long,
)

data class PartyChatMessage(
    val key: String,
    val id: Long?,
    val memberId: String,
    val name: String,
    val avatarUrl: String?,
    val avatarColor: String?,
    val body: String,
    val sentAt: Long,
    val mine: Boolean,
    val clientId: String? = null,
    val status: PartyChatStatus = PartyChatStatus.Sent,
)

internal data class PartyChatRoom(
    val partyId: String,
    val memberId: String,
    val members: List<PartyMember>,
)

internal data class PartyChatMerge(
    val messages: List<PartyChatMessage>,
    val added: List<PartyChatMessage>,
)

@OptIn(ExperimentalCoroutinesApi::class)
object PartyChat {
    private val log = Logger.withTag("PartyChat")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))
    private val syncLock = Mutex()

    private val _messages = MutableStateFlow<List<PartyChatMessage>>(emptyList())
    val messages: StateFlow<List<PartyChatMessage>> = _messages.asStateFlow()

    private val _unread = MutableStateFlow(0)
    val unread: StateFlow<Int> = _unread.asStateFlow()

    /** Messages from other members, used by the in-player chat bubble. */
    private val _incoming = MutableSharedFlow<PartyChatMessage>(extraBufferCapacity = 16)
    val incoming: SharedFlow<PartyChatMessage> = _incoming.asSharedFlow()

    /** Set when the composer tried to send but the message never left. */
    private val _notice = MutableStateFlow<PartyChatNotice?>(null)
    val notice: StateFlow<PartyChatNotice?> = _notice.asStateFlow()
    private var noticeSeq = 0L

    val draft = MutableStateFlow("")

    private var room: PartyChatRoom? = null
    private val ownMemberIds = mutableSetOf<String>()
    private var loaded = false
    private var open = false

    /**
     * Every stored message is mirrored to the other members over the party
     * channel. Without this the sender only saw its own bubble and everyone
     * else waited for a reconnect before the history re-synced.
     */
    private var broadcaster: (suspend (JsonObject) -> Unit)? = null

    internal fun setBroadcaster(handler: (suspend (JsonObject) -> Unit)?) {
        broadcaster = handler
    }

    fun setOpen(value: Boolean) {
        scope.launch {
            open = value
            if (value) _unread.value = 0
        }
    }

    fun clearNotice(seq: Long) {
        if (_notice.value?.seq == seq) _notice.value = null
    }

    fun send(text: String) {
        val body = text.trim().take(MAX_LENGTH)
        if (body.isEmpty()) return
        scope.launch {
            val current = room
            if (current == null) {
                restoreDraft(body)
                raiseNotice(PartyChatIssue.NotReady)
                return@launch
            }
            val self = current.members.firstOrNull { it.id == current.memberId }
            val clientId = newClientId()
            val message = PartyChatMessage(
                key = clientId,
                id = null,
                memberId = current.memberId,
                name = self?.displayName.orEmpty(),
                avatarUrl = self?.avatarUrl,
                avatarColor = self?.avatarColor,
                body = body,
                sentAt = PartyRepository.nowMs(),
                mine = true,
                clientId = clientId,
                status = PartyChatStatus.Sending,
            )
            _messages.update { it + message }
            deliver(current.partyId, message)
        }
    }

    fun retry(key: String) {
        scope.launch {
            val target = room?.partyId ?: return@launch
            val message = _messages.value.firstOrNull { it.key == key && it.status == PartyChatStatus.Failed } ?: return@launch
            setStatus(key, PartyChatStatus.Sending)
            deliver(target, message)
        }
    }

    internal fun start(next: PartyChatRoom) {
        scope.launch {
            if (room?.partyId != next.partyId) reset()
            room = next
            ownMemberIds += next.memberId
            sync()
        }
    }

    internal fun stop() {
        scope.launch { reset() }
    }

    internal fun refresh() {
        scope.launch {
            sync()
            val target = room?.partyId ?: return@launch
            _messages.value.filter { it.status == PartyChatStatus.Failed }.forEach { message ->
                setStatus(message.key, PartyChatStatus.Sending)
                deliver(target, message)
            }
        }
    }

    /**
     * History-only refresh used by the safety poll: it never re-sends a failed
     * message, because a poll every few seconds must not turn into a retry loop.
     */
    internal fun poll() {
        scope.launch { sync() }
    }

    internal fun receive(payload: JsonObject) {
        scope.launch {
            if (room == null) return@launch
            val message = runCatching { PartyApi.json.decodeFromJsonElement(PartyChatPayload.serializer(), payload) }
                .getOrNull() ?: return@launch
            apply(listOf(message), replaceOlder = false, notify = true)
        }
    }

    private suspend fun sync() {
        syncLock.withLock {
            val target = room?.partyId ?: return
            val after = if (loaded) _messages.value.lastOrNull { it.id != null }?.id else null
            val history = try {
                PartyApi.listMessages(target, SyncClientIdentity.currentClientId(), after)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                log.w(error) { "Chat history failed" }
                return
            }
            if (target != room?.partyId || history.error != null) return
            val firstLoad = !loaded
            loaded = true
            apply(
                payloads = history.messages,
                replaceOlder = after != null && history.messages.size >= HISTORY_LIMIT,
                notify = !firstLoad,
            )
        }
    }

    private suspend fun deliver(target: String, message: PartyChatMessage) {
        val result = try {
            PartyApi.sendMessage(target, SyncClientIdentity.currentClientId(), message.body, message.key)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            log.w(error) { "Chat message failed" }
            null
        }
        if (target != room?.partyId) return
        val sent = result?.message
        if (sent == null) {
            setStatus(message.key, PartyChatStatus.Failed)
            restoreDraft(message.body)
            raiseNotice(PartyChatIssue.Failed)
            return
        }
        apply(listOf(sent), replaceOlder = false, notify = false)
        mirror(sent)
    }

    /** Mirrors a stored message to the rest of the party so chat arrives live. */
    private suspend fun mirror(payload: PartyChatPayload) {
        val handler = broadcaster ?: return
        val element = runCatching {
            PartyApi.json.encodeToJsonElement(PartyChatPayload.serializer(), payload).jsonObject
        }.getOrNull() ?: return
        runCatching { handler(element) }
    }

    private fun restoreDraft(body: String) {
        if (draft.value.isBlank()) draft.value = body
    }

    private fun raiseNotice(issue: PartyChatIssue) {
        noticeSeq += 1
        _notice.value = PartyChatNotice(issue = issue, seq = noticeSeq)
    }

    private fun apply(payloads: List<PartyChatPayload>, replaceOlder: Boolean, notify: Boolean) {
        val result = mergePartyChat(
            current = _messages.value,
            incoming = payloads.map { it.toMessage() },
            replaceOlder = replaceOlder,
            limit = MESSAGE_LIMIT,
        )
        _messages.value = result.messages
        val fromOthers = result.added.filterNot { it.mine }
        if (fromOthers.isEmpty()) return
        if (!open) fromOthers.forEach { _incoming.tryEmit(it) }
        if (!notify || open) return
        _unread.update { it + fromOthers.size }
    }

    private fun setStatus(key: String, status: PartyChatStatus) {
        _messages.update { list -> list.map { if (it.key == key && it.id == null) it.copy(status = status) else it } }
    }

    private fun reset() {
        room = null
        ownMemberIds.clear()
        loaded = false
        draft.value = ""
        _messages.value = emptyList()
        _unread.value = 0
        _notice.value = null
    }

    private fun PartyChatPayload.toMessage() = PartyChatMessage(
        key = "id:$id",
        id = id,
        memberId = memberId,
        name = name,
        avatarUrl = avatarUrl,
        avatarColor = avatarColor,
        body = body,
        sentAt = sentAt,
        // Never trust the wire flag: a broadcast is encoded by the sender, so
        // `mine` there describes the sender, not the receiver.
        mine = memberId in ownMemberIds,
        clientId = clientId,
    )

    private fun newClientId(): String =
        buildString { repeat(CLIENT_ID_LENGTH) { append(CLIENT_ID_ALPHABET[Random.nextInt(CLIENT_ID_ALPHABET.length)]) } }

    const val MAX_LENGTH = 500
    private const val HISTORY_LIMIT = 100
    private const val MESSAGE_LIMIT = 200
    private const val CLIENT_ID_LENGTH = 20
    private const val CLIENT_ID_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
}

internal fun mergePartyChat(
    current: List<PartyChatMessage>,
    incoming: List<PartyChatMessage>,
    replaceOlder: Boolean,
    limit: Int,
): PartyChatMerge {
    val oldestIncoming = incoming.mapNotNull { it.id }.minOrNull()
    val base = if (replaceOlder && oldestIncoming != null) {
        current.filter { it.id == null || it.id >= oldestIncoming }
    } else {
        current
    }
    val confirmed = base.filter { it.id != null }.associateByTo(mutableMapOf()) { it.id }
    val pending = base.filter { it.id == null }.toMutableList()
    val added = mutableListOf<PartyChatMessage>()
    incoming.sortedBy { it.id }.forEach { message ->
        val id = message.id ?: return@forEach
        if (id in confirmed) return@forEach
        val local = message.clientId
            ?.takeIf { message.mine }
            ?.let { clientId -> pending.firstOrNull { it.key == clientId } }
        if (local != null) {
            pending.remove(local)
            confirmed[id] = message.copy(key = local.key)
        } else {
            confirmed[id] = message
            added += message
        }
    }
    val merged = confirmed.values.sortedBy { it.id } + pending
    return PartyChatMerge(merged.takeLast(limit), added)
}
