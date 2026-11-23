package app.kpakam.shared.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.kpakam.R
import app.kpakam.core.ledger.InferenceSource
import app.kpakam.core.routing.QueueReason
import app.kpakam.core.routing.ReasonCode

/** "On device" or "Cloud" with the reason label (F6). */
@Composable
fun InferenceBadge(source: InferenceSource, reason: ReasonCode?, modifier: Modifier = Modifier) {
    val (icon, label) =
        when (source) {
            InferenceSource.ON_DEVICE -> Icons.Filled.PhoneAndroid to R.string.badge_device
            InferenceSource.IN_CLOUD -> Icons.Filled.Cloud to R.string.badge_cloud
            InferenceSource.MANUAL -> Icons.Filled.Edit to R.string.badge_manual
        }
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StatusChip(icon, stringResource(label))
        if (reason != null) {
            Text(stringResource(reasonLabel(reason)), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** "Waiting" with the queue reason. */
@Composable
fun QueueBadge(queueReason: QueueReason, modifier: Modifier = Modifier) {
    StatusChip(Icons.Filled.HourglassTop, stringResource(queueLabel(queueReason)), modifier)
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
