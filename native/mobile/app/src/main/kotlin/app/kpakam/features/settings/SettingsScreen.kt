package app.kpakam.features.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kpakam.R
import app.kpakam.core.routing.VoiceLanguage
import app.kpakam.shared.ui.MinTouchTarget
import app.kpakam.shared.ui.SectionHeader
import app.kpakam.shared.ui.modelStateLabel

/** Settings, the trader's numbers, and "Download my records". */
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionHeader(stringResource(R.string.settings_title))
        SwitchRow(R.string.settings_data_saver, R.string.settings_data_saver_help, state.settings.dataSaver) {
            viewModel.setDataSaver(it)
        }
        VoiceSection(state.settings.voiceLanguage, viewModel::setVoiceLanguage)
        SwitchRow(R.string.settings_analytics, null, state.settings.analyticsOptIn) { viewModel.setAnalytics(it) }
        StatsSection(state)
        AccountSection(state.exportMessage, viewModel::export)
    }
}

@Composable
private fun VoiceSection(selected: VoiceLanguage, onSelect: (VoiceLanguage) -> Unit) {
    Text(stringResource(R.string.settings_voice), style = MaterialTheme.typography.titleLarge)
    VoiceLanguage.entries.forEach { language ->
        RadioRow(language, selected == language) { onSelect(language) }
    }
    Text(stringResource(voiceHelp(selected)), style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun StatsSection(state: SettingsUiState) {
    SectionHeader(stringResource(R.string.settings_stats))
    Text(stringResource(R.string.settings_on_device_share, state.stats.onDeviceShare))
    Text(stringResource(R.string.settings_cloud_calls, state.stats.inCloud))
    Text(stringResource(R.string.settings_phone_reading, stringResource(modelStateLabel(state.model))))
    val network =
        when {
            !state.network.online -> R.string.settings_network_offline
            state.network.metered -> R.string.settings_network_metered
            else -> R.string.settings_network_unmetered
        }
    Text(stringResource(network))
}

@Composable
private fun AccountSection(message: ExportMessage?, onExport: (String) -> Unit) {
    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            uri?.let { onExport(it.toString()) }
        }
    SectionHeader(stringResource(R.string.settings_account))
    OutlinedButton(onClick = { picker.launch(EXPORT_FILE_NAME) }, modifier = Modifier.heightIn(min = MinTouchTarget)) {
        Text(stringResource(R.string.settings_export))
    }
    when (message) {
        ExportMessage.Done -> Text(stringResource(R.string.settings_export_done))
        ExportMessage.Failed -> Text(stringResource(R.string.error_generic))
        null -> Unit
    }
}

@Composable
private fun SwitchRow(title: Int, help: Int?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = MinTouchTarget)
                .toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(title), style = MaterialTheme.typography.bodyLarge)
            help?.let { Text(stringResource(it), style = MaterialTheme.typography.bodyMedium) }
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun RadioRow(language: VoiceLanguage, selected: Boolean, onSelect: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = MinTouchTarget)
                .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect),
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(stringResource(voiceLabel(language)), modifier = Modifier.padding(start = 8.dp))
    }
}

private const val EXPORT_FILE_NAME = "kpakam-records.json"

private fun voiceLabel(language: VoiceLanguage): Int =
    when (language) {
        VoiceLanguage.AUTO -> R.string.settings_voice_auto
        VoiceLanguage.EN -> R.string.settings_voice_en
        VoiceLanguage.PCM_YO -> R.string.settings_voice_pcm_yo
    }

private fun voiceHelp(language: VoiceLanguage): Int =
    when (language) {
        VoiceLanguage.AUTO -> R.string.settings_voice_help_auto
        VoiceLanguage.EN -> R.string.settings_voice_help_en
        VoiceLanguage.PCM_YO -> R.string.settings_voice_help_pcm_yo
    }
