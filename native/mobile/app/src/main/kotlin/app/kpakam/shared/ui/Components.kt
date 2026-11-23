package app.kpakam.shared.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.kpakam.R
import app.kpakam.core.money.Kobo
import app.kpakam.shared.money.NairaFormat

/** Minimum touch target from the accessibility rules. */
val MinTouchTarget = 48.dp

/** A screen or section heading, marked as a heading for screen readers. */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        modifier =
            modifier
                .padding(vertical = 8.dp)
                .semantics { heading() },
    )
}

/** A money amount in large type. */
@Composable
fun AmountText(kobo: Kobo, modifier: Modifier = Modifier, large: Boolean = false) {
    Text(
        text = NairaFormat.format(kobo),
        style = if (large) MaterialTheme.typography.displaySmall else MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        modifier = modifier,
    )
}

/** A small status chip: always an icon plus text, never colour alone. */
@Composable
fun StatusChip(icon: ImageVector, text: String, modifier: Modifier = Modifier) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier =
                Modifier
                    .heightIn(min = 32.dp)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(text = text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** Shows whether an entry has reached Firestore yet. */
@Composable
fun SyncMark(pending: Boolean, modifier: Modifier = Modifier) {
    if (pending) {
        StatusChip(Icons.Filled.CloudUpload, stringResource(R.string.sync_pending), modifier)
    } else {
        StatusChip(Icons.Filled.CloudDone, stringResource(R.string.sync_done), modifier)
    }
}

/** A banner shown while the phone is offline. */
@Composable
fun OfflineBanner(modifier: Modifier = Modifier) {
    StatusChip(Icons.Filled.CloudOff, stringResource(R.string.status_offline), modifier)
}
