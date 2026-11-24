package app.kpakam.features.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kpakam.R
import app.kpakam.core.routing.VoiceLanguage
import app.kpakam.shared.ui.IconTile
import app.kpakam.shared.ui.Kpakam
import app.kpakam.shared.ui.KpakamType
import app.kpakam.shared.ui.ListPanel
import app.kpakam.shared.ui.PageGutter
import app.kpakam.shared.ui.PageHeader
import app.kpakam.shared.ui.SectionHeader
import app.kpakam.shared.ui.Spinner
import app.kpakam.shared.ui.rowClick

/** Settings: voice language (F8), data control (F7), privacy, and the account. */
@Composable
fun SettingsScreen(contentPadding: PaddingValues, viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var languageOpen by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            uri?.let { viewModel.export(it.toString()) }
        }
    SettingsList(
        state = state,
        viewModel = viewModel,
        actions =
            SettingsActions(
                { languageOpen = true },
                { picker.launch(EXPORT_FILE_NAME) },
                { confirmDelete = true },
            ),
        contentPadding = contentPadding,
    )
    if (languageOpen) {
        LanguageSheet(state.settings.voiceLanguage, viewModel::setVoiceLanguage, onClose = { languageOpen = false })
    }
    if (confirmDelete) {
        DeleteAccountDialog(
            onConfirm = {
                confirmDelete = false
                viewModel.deleteAccount()
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

/** What the rows that open something do. */
private class SettingsActions(val openLanguage: () -> Unit, val export: () -> Unit, val delete: () -> Unit)

@Composable
private fun SettingsList(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
    actions: SettingsActions,
    contentPadding: PaddingValues,
) {
    val settings = state.settings
    val section = Modifier.padding(horizontal = PageGutter)
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { PageHeader(stringResource(R.string.settings_title), section.padding(bottom = 8.dp)) }
        panel(R.string.settings_section_voice) { LanguageRow(settings.voiceLanguage, actions.openLanguage) }
        panel(R.string.settings_section_data) {
            ToggleRow(
                stringResource(R.string.settings_data_saver),
                stringResource(R.string.settings_data_saver_help),
                settings.dataSaver,
                viewModel::setDataSaver,
            )
        }
        panel(R.string.settings_section_privacy) {
            ToggleRow(
                stringResource(R.string.settings_analytics),
                null,
                settings.analyticsOptIn,
                viewModel::setAnalytics,
            )
        }
        item { SectionHeader(stringResource(R.string.settings_account), section) }
        item { AccountPanel(state.deleting, actions.export, actions.delete, section) }
    }
}

/** A section heading and a one-row white panel. */
private fun LazyListScope.panel(title: Int, row: @Composable () -> Unit) {
    val section = Modifier.padding(horizontal = PageGutter)
    item { SectionHeader(stringResource(title), section) }
    item { ListPanel(section, listOf(row)) }
}

@Composable
private fun LanguageRow(language: VoiceLanguage, onClick: () -> Unit) {
    val colors = Kpakam.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(
                    min = 72.dp,
                ).rowClick(onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        IconTile(Icons.Outlined.Mic)
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.settings_voice), style = KpakamType.rowTitle)
            Text(
                stringResource(voiceHelp(language)),
                style = KpakamType.small,
                color = colors.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            stringResource(voiceLabel(language)),
            style = KpakamType.chip,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            modifier =
                Modifier
                    .clip(
                        CircleShape,
                    ).background(colors.surface2)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
        )
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = colors.muted, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun AccountPanel(deleting: Boolean, onExport: () -> Unit, onDelete: () -> Unit, modifier: Modifier) {
    ListPanel(
        modifier,
        listOf(
            { ActionRow(Icons.Outlined.FileDownload, stringResource(R.string.settings_export), onExport) },
            {
                ActionRow(
                    Icons.Outlined.DeleteOutline,
                    stringResource(R.string.settings_delete_account),
                    onDelete,
                    danger = true,
                    busy = deleting,
                )
            },
        ),
    )
}

@Composable
private fun ActionRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    danger: Boolean = false,
    busy: Boolean = false,
) {
    val colors = Kpakam.colors
    val tint = if (danger) colors.danger else colors.text
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 68.dp)
                .rowClick { if (!busy) onClick() }
                .padding(horizontal = 16.dp),
    ) {
        IconTile(icon, background = if (danger) colors.dangerSoft else colors.surface2, tint = tint)
        Text(label, style = KpakamType.rowTitle, color = tint, modifier = Modifier.weight(1f))
        when {
            busy -> Spinner()
            !danger ->
                Icon(
                    Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    null,
                    tint = colors.muted,
                    modifier = Modifier.size(20.dp),
                )
        }
    }
}

private const val EXPORT_FILE_NAME = "kpakam-records.json"

/** The short name of a voice language. */
fun voiceLabel(language: VoiceLanguage): Int =
    when (language) {
        VoiceLanguage.AUTO -> R.string.settings_voice_auto
        VoiceLanguage.EN -> R.string.settings_voice_en
        VoiceLanguage.PCM_YO -> R.string.settings_voice_pcm_yo
    }

/** What a voice language choice means, in plain words. */
fun voiceHelp(language: VoiceLanguage): Int =
    when (language) {
        VoiceLanguage.AUTO -> R.string.settings_voice_help_auto
        VoiceLanguage.EN -> R.string.settings_voice_help_en
        VoiceLanguage.PCM_YO -> R.string.settings_voice_help_pcm_yo
    }
