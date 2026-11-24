package app.kpakam.features.quickentry

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.kpakam.R
import app.kpakam.core.jobs.AiJobState
import app.kpakam.core.routing.AiTask
import app.kpakam.core.routing.QueueReason
import app.kpakam.inference.queue.AiJob
import app.kpakam.shared.ui.KCard
import app.kpakam.shared.ui.Kpakam
import app.kpakam.shared.ui.KpakamType
import app.kpakam.shared.ui.PillButton
import app.kpakam.shared.ui.PillStyle
import app.kpakam.shared.ui.QueueBadge
import app.kpakam.shared.ui.Spinner

/** One waiting note or voice clip, or drafts ready to check (F2, F7), as a white card. */
@Composable
fun JobCard(job: AiJob, viewModel: QuickEntryViewModel, modifier: Modifier = Modifier) {
    val kind = stringResource(if (job.task == AiTask.PARSE_AUDIO) R.string.jobs_voice else R.string.jobs_text)
    KCard(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            JobStatus(job)
            Text(kind, style = KpakamType.body, color = Kpakam.colors.muted)
            JobButtons(job, viewModel)
        }
    }
}

@Composable
private fun JobStatus(job: AiJob) {
    val colors = Kpakam.colors
    when (job.state) {
        AiJobState.SUCCEEDED ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = colors.income)
                Text(stringResource(R.string.drafts_ready), style = KpakamType.rowTitle)
            }
        AiJobState.FAILED ->
            Text(
                stringResource(R.string.jobs_failed),
                style = KpakamType.rowTitle,
                color = colors.danger,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        AiJobState.RUNNING ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Spinner()
                Text(
                    stringResource(R.string.entry_working),
                    style = KpakamType.body,
                    fontWeight = FontWeight.Medium,
                    color = colors.muted,
                )
            }
        else -> job.queueReason?.let { QueueBadge(it) }
    }
}

@Composable
private fun JobButtons(job: AiJob, viewModel: QuickEntryViewModel) {
    val context = LocalContext.current
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (job.state != AiJobState.SUCCEEDED) {
            PillButton(stringResource(R.string.jobs_cancel), { viewModel.cancelJob(job.id) }, style = PillStyle.Soft)
        }
        when {
            job.state == AiJobState.SUCCEEDED ->
                PillButton(
                    stringResource(R.string.jobs_review),
                    { viewModel.review(job) },
                )
            job.state == AiJobState.FAILED ->
                PillButton(
                    stringResource(R.string.jobs_retry),
                    { viewModel.retryJob(job.id) },
                    style = PillStyle.Secondary,
                )
            job.queueReason == QueueReason.DATA_SAVER && job.state == AiJobState.QUEUED -> {
                val size = Formatter.formatShortFileSize(context, job.sizeBytes)
                PillButton(
                    stringResource(R.string.jobs_send_now_size, size),
                    { viewModel.sendNow(job.id) },
                    icon = Icons.AutoMirrored.Outlined.Send,
                )
            }
        }
    }
}
