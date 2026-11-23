package app.kpakam.features.quickentry

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import app.kpakam.R

/** The big mic button (F8). Asks for the microphone permission the first time. */
@Composable
fun VoiceButton(phase: EntryPhase, onTap: () -> Unit, onDenied: () -> Unit) {
    val context = LocalContext.current
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) onTap() else onDenied()
        }
    val active = phase == EntryPhase.Listening || phase == EntryPhase.Recording
    val enabled = phase == EntryPhase.Idle || active
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        LargeFloatingActionButton(
            onClick = {
                when {
                    !enabled -> Unit
                    hasMicPermission(context) -> onTap()
                    else -> launcher.launch(Manifest.permission.RECORD_AUDIO)
                }
            },
            modifier = Modifier.size(96.dp),
        ) {
            Icon(
                imageVector = if (active) Icons.Filled.Stop else Icons.Filled.Mic,
                contentDescription = stringResource(if (active) R.string.entry_mic_stop else R.string.entry_mic),
                modifier = Modifier.size(48.dp),
            )
        }
        Text(stringResource(phaseLabel(phase)))
    }
}

private fun hasMicPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

private fun phaseLabel(phase: EntryPhase): Int =
    when (phase) {
        EntryPhase.Listening -> R.string.entry_listening
        EntryPhase.Recording -> R.string.entry_recording
        else -> R.string.entry_mic
    }
