package com.nuvio.app.features.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nuvio.app.core.ui.nuvio
import com.nuvio.app.features.party.PartyChat
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

private data class PartyChatToast(
    val key: String,
    val name: String,
    val body: String,
)

/**
 * Chat bubble shown over the player when a party member writes.
 *
 * Design rules that keep it out of the way: top-right corner, at most two at a
 * time, five seconds each, no sound and no vibration. It never pauses playback
 * and never covers the subtitle area or the bottom controls. Three messages in
 * ten seconds is enough; the rest only bump the unread badge.
 */
@Composable
internal fun PartyChatToasts(
    enabled: Boolean,
    onOpenChat: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val live by rememberUpdatedState(enabled)
    var toasts by remember { mutableStateOf<List<PartyChatToast>>(emptyList()) }
    val recent = remember { mutableListOf<TimeMark>() }
    LaunchedEffect(Unit) {
        PartyChat.incoming.collect { message ->
            if (!live) return@collect
            recent.removeAll { it.elapsedNow() > QUIET_WINDOW }
            if (recent.size >= QUIET_LIMIT) return@collect
            recent += TimeSource.Monotonic.markNow()
            val toast = PartyChatToast(
                key = message.key,
                name = message.name.ifBlank { "HOFLIX" },
                body = message.body,
            )
            toasts = (listOf(toast) + toasts).take(MAX_TOASTS)
        }
    }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.End,
    ) {
        toasts.forEach { toast ->
            key(toast.key) {
                PartyChatToastCard(
                    toast = toast,
                    onOpenChat = onOpenChat,
                    onExpired = { toasts = toasts.filterNot { it.key == toast.key } },
                )
            }
        }
    }
}

@Composable
private fun PartyChatToastCard(
    toast: PartyChatToast,
    onOpenChat: () -> Unit,
    onExpired: () -> Unit,
) {
    val tokens = MaterialTheme.nuvio
    val shape = RoundedCornerShape(14.dp)
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        visible = true
        delay(TOAST_DURATION)
        visible = false
        delay(180)
        onExpired()
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(160)),
        exit = fadeOut(tween(180)),
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .clip(shape)
                .background(tokens.colors.surfaceElevated.copy(alpha = 0.94f))
                .border(1.dp, tokens.colors.textPrimary.copy(alpha = 0.10f), shape)
                .clickable(onClick = onOpenChat)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = toast.name,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = tokens.colors.accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = toast.body,
                style = MaterialTheme.typography.bodySmall,
                color = tokens.colors.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private const val TOAST_DURATION = 5000L
private const val MAX_TOASTS = 2
private const val QUIET_LIMIT = 3
private val QUIET_WINDOW = 10.seconds
