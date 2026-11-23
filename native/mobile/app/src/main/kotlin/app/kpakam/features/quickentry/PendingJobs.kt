package app.kpakam.features.quickentry

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.kpakam.R
import app.kpakam.core.jobs.AiJobState
import app.kpakam.core.routing.AiTask
import app.kpakam.core.routing.QueueReason
import app.kpakam.inference.queue.AiJob
import app.kpakam.shared.ui.MinTouchTarget
import app.kpakam.shared.ui.QueueBadge
import app.kpakam.shared.ui.SectionHeader
import app.kpakam.shared.ui.StatusChip

/** Notes and clips waiting in the queue, and results ready to check (F2, F7). */
@Composable
fun PendingJobs(jobs: List<AiJob>, viewModel: QuickEntryViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(stringResource(R.string.jobs_title))
        jobs.forEach { job -> JobCard(job, viewModel) }
    }
}

@Composable
private fun JobCard(job: AiJob, viewModel: QuickEntryViewModel) {
    val context = LocalContext.current
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(if (job.task == AiTask.PARSE_AUDIO) R.string.jobs_voice else R.string.jobs_text),
                style = MaterialTheme.typography.labelLarge,
            )
            when (job.state) {
                AiJobState.SUCCEEDED -> StatusChip(Icons.Filled.CheckCircle, stringResource(R.string.jobs_ready))
                AiJobState.FAILED -> StatusChip(Icons.Filled.ErrorOutline, stringResource(R.string.jobs_failed))
                else -> job.queueReason?.let { QueueBadge(it) }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when {
                    job.state == AiJobState.SUCCEEDED ->
                        JobButton(
                            stringResource(R.string.jobs_review),
                        ) { viewModel.review(job) }
                    job.state == AiJobState.FAILED ->
                        JobButton(
                            stringResource(R.string.jobs_retry),
                        ) { viewModel.retryJob(job.id) }
                    job.queueReason == QueueReason.DATA_SAVER -> {
                        val size = Formatter.formatShortFileSize(context, job.sizeBytes)
                        JobButton(stringResource(R.string.jobs_send_now_size, size)) { viewModel.sendNow(job.id) }
                    }
                }
                if (job.state != AiJobState.SUCCEEDED) {
                    TextButton(
                        onClick = { viewModel.cancelJob(job.id) },
                        modifier = Modifier.heightIn(min = MinTouchTarget),
                    ) {
                        Text(stringResource(R.string.jobs_cancel))
                    }
                }
            }
        }
    }
}

@Composable
private fun JobButton(text: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.heightIn(min = MinTouchTarget)) { Text(text) }
}
