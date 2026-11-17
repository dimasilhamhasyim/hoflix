package com.nuvio.app.features.details.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nuvio.app.core.format.formatReleaseDateForDisplay
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.hoflix_coming_soon
import nuvio.composeapp.generated.resources.hoflix_coming_soon_date
import nuvio.composeapp.generated.resources.hoflix_coming_soon_hint
import nuvio.composeapp.generated.resources.hoflix_remind_me
import nuvio.composeapp.generated.resources.hoflix_reminder_active
import org.jetbrains.compose.resources.stringResource

/**
 * Posted under the hero for a title MovieBox has not released yet: the label
 * says when it arrives, the pill schedules a notification for that day.
 *
 * The pill is an outlined 40dp capsule that lines up with the primary actions
 * above it; a plain text button used to float on the hero without a shape.
 */
@Composable
fun ComingSoonBanner(
    releaseDate: String?,
    isReminded: Boolean,
    onToggleReminder: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val formattedDate = releaseDate
        ?.takeIf { it.isNotBlank() }
        ?.let(::formatReleaseDateForDisplay)
    val label = if (formattedDate != null) {
        stringResource(Res.string.hoflix_coming_soon_date, formattedDate)
    } else {
        stringResource(Res.string.hoflix_coming_soon)
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(Res.string.hoflix_coming_soon_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            ReminderPill(isReminded = isReminded, onClick = onToggleReminder)
        }
    }
}

@Composable
private fun ReminderPill(
    isReminded: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    val container = if (isReminded) {
        MaterialTheme.colorScheme.primary
    } else {
        Color.Transparent
    }
    val content = if (isReminded) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Row(
        modifier = Modifier
            .height(40.dp)
            .clip(shape)
            .background(container, shape)
            .border(
                width = 1.dp,
                color = if (isReminded) {
                    Color.Transparent
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.24f)
                },
                shape = shape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = if (isReminded) {
                Icons.Default.NotificationsActive
            } else {
                Icons.Outlined.NotificationsNone
            },
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = stringResource(
                if (isReminded) Res.string.hoflix_reminder_active else Res.string.hoflix_remind_me,
            ),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = content,
            maxLines = 1,
        )
    }
}
