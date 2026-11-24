package app.kpakam.shared.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.kpakam.R
import app.kpakam.core.ledger.InferenceSource
import app.kpakam.core.routing.QueueReason
import app.kpakam.core.routing.ReasonCode

/** "On your phone" or "Online" with the reason label (F6). Icon and text, never colour alone. */
@Composable
fun InferenceBadge(source: InferenceSource, reason: ReasonCode?, modifier: Modifier = Modifier) {
    val (icon, label) =
        when (source) {
            InferenceSource.ON_DEVICE -> Icons.Outlined.PhoneAndroid to R.string.badge_device
            InferenceSource.IN_CLOUD -> Icons.Outlined.Cloud to R.string.badge_cloud
            InferenceSource.MANUAL -> Icons.Outlined.Edit to R.string.badge_manual
        }
    BadgePill(icon, stringResource(label), reason?.let { stringResource(reasonLabel(it)) }, modifier)
}

/** "Waiting" with the queue reason. */
@Composable
fun QueueBadge(queueReason: QueueReason, modifier: Modifier = Modifier) {
    BadgePill(
        Icons.Outlined.Schedule,
        stringResource(R.string.badge_waiting),
        stringResource(queueLabel(queueReason)),
        modifier,
        background = Kpakam.colors.accentSoft,
        content = Kpakam.colors.accent,
    )
}

@Composable
private fun BadgePill(
    icon: ImageVector,
    label: String,
    why: String?,
    modifier: Modifier = Modifier,
    background: Color = Kpakam.colors.surface2,
    content: Color = Kpakam.colors.text,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier =
            modifier
                .clip(CircleShape)
                .background(background)
                .heightIn(min = 32.dp)
                .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(16.dp))
        Text(label, style = KpakamType.small, fontWeight = FontWeight.SemiBold, color = content)
        why?.let {
            Text(
                "· $it",
                style = KpakamType.small,
                fontWeight = FontWeight.Medium,
                color = content.copy(alpha = 0.9f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** The English label for a reason code (routing.md). */
@StringRes
fun reasonLabel(reason: ReasonCode): Int =
    when (reason) {
        ReasonCode.IMAGE_INPUT -> R.string.reason_image_input
        ReasonCode.LONG_CONTEXT -> R.string.reason_long_context
        ReasonCode.DEVICE_UNAVAILABLE -> R.string.reason_device_unavailable
        ReasonCode.LANGUAGE -> R.string.reason_language
        ReasonCode.TOO_LONG -> R.string.reason_too_long
        ReasonCode.LOW_BATTERY -> R.string.reason_low_battery
        ReasonCode.DEFAULT_DEVICE -> R.string.reason_default_device
        ReasonCode.VALIDATION_ESCALATION -> R.string.reason_validation_escalation
        ReasonCode.ROUTER_FALLBACK -> R.string.reason_router_fallback
        ReasonCode.SPEECH_DEVICE -> R.string.reason_speech_device
        ReasonCode.SPEECH_CLOUD -> R.string.reason_speech_cloud
        ReasonCode.SPEECH_MIXED -> R.string.reason_speech_mixed
    }

/** The English label for a queue reason (routing.md). */
@StringRes
fun queueLabel(reason: QueueReason): Int =
    when (reason) {
        QueueReason.OFFLINE -> R.string.queue_offline
        QueueReason.DATA_SAVER -> R.string.queue_data_saver
        QueueReason.CLOUD_DISABLED -> R.string.queue_cloud_disabled
        QueueReason.RATE_LIMITED -> R.string.queue_rate_limited
    }
