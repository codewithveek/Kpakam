package app.kpakam.features.settings

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.kpakam.R
import app.kpakam.core.routing.VoiceLanguage
import app.kpakam.shared.ui.Kpakam
import app.kpakam.shared.ui.KpakamType
import app.kpakam.shared.ui.RoundIconButton
import app.kpakam.shared.ui.RoundTone
import kotlinx.coroutines.launch

/** A switch row. The thumb shows a tick when on, so state never relies on colour alone. */
@Composable
fun ToggleRow(label: String, help: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    val stateText = stringResource(if (checked) R.string.state_on else R.string.state_off)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp)
                .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
                .semantics { stateDescription = stateText }
                .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = KpakamType.rowTitle)
            help?.let {
                Text(
                    it,
                    style = KpakamType.small,
                    color = Kpakam.colors.muted,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        KSwitch(checked)
    }
}

@Composable
private fun KSwitch(checked: Boolean) {
    val colors = Kpakam.colors
    val offset by animateDpAsState(if (checked) 24.dp else 4.dp, label = "thumb")
    Box(
        contentAlignment = Alignment.CenterStart,
        modifier =
            Modifier
                .size(
                    width = 52.dp,
                    height = 32.dp,
                ).clip(CircleShape)
                .background(if (checked) colors.ink else colors.surface3),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .offset(x = offset)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(colors.surface),
        ) {
            if (checked) {
                Icon(
                    Icons.Outlined.Check,
                    contentDescription = null,
                    tint = colors.text,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

/** "Language you speak" as a bottom sheet: one large row per choice; the chosen one is black with a tick. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageSheet(selected: VoiceLanguage, onSelect: (VoiceLanguage) -> Unit, onClose: () -> Unit) {
    val sheet = rememberBottomSheetState(SheetValue.Hidden, setOf(SheetValue.Hidden, SheetValue.Expanded))
    val scope = rememberCoroutineScope()
    val close: () -> Unit = { scope.launch { sheet.hide() }.invokeOnCompletion { onClose() } }
    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = sheet,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        containerColor = Kpakam.colors.surface,
        contentColor = Kpakam.colors.text,
        scrimColor = Kpakam.colors.scrim,
        dragHandle = { SheetHandle() },
    ) {
        Column(Modifier.padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 24.dp)) {
            SheetTitle(stringResource(R.string.settings_voice), close)
            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                VoiceLanguage.entries.forEach { language ->
                    LanguageOption(language, language == selected) {
                        onSelect(language)
                        close()
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetHandle() {
    Box(
        Modifier
            .padding(vertical = 12.dp)
            .size(width = 40.dp, height = 6.dp)
            .clip(CircleShape)
            .background(Kpakam.colors.surface3),
    )
}

@Composable
private fun SheetTitle(title: String, onClose: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        Spacer(Modifier.width(48.dp))
        Text(
            title,
            style = KpakamType.listAmount,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        RoundIconButton(
            Icons.Outlined.Close,
            stringResource(R.string.voice_close),
            onClose,
            tone = RoundTone.Soft,
            size = 44.dp,
        )
    }
}

@Composable
private fun LanguageOption(language: VoiceLanguage, active: Boolean, onClick: () -> Unit) {
    val colors = Kpakam.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(if (active) colors.ink else colors.surface2)
                .selectable(selected = active, role = Role.RadioButton, onClick = onClick)
                .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(voiceLabel(language)),
                style = KpakamType.listAmount,
                color = if (active) colors.onInk else colors.text,
            )
            Text(
                stringResource(voiceHelp(language)),
                style = KpakamType.small,
                color = if (active) colors.onInkMuted else colors.muted,
            )
        }
        if (active) {
            Icon(
                Icons.Outlined.Check,
                contentDescription = null,
                tint = colors.onInk,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** "Delete my account and records": the trader types DELETE to confirm. */
@Composable
fun DeleteAccountDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    var typed by rememberSaveable { mutableStateOf("") }
    val word = stringResource(R.string.settings_delete_word)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Kpakam.colors.surface,
        title = { Text(stringResource(R.string.settings_delete_account), style = KpakamType.listAmount) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.settings_delete_confirm), style = KpakamType.body)
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it.take(word.length + 2) },
                    singleLine = true,
                )
                Spacer(Modifier.height(4.dp))
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = typed.trim() == word) {
                Text(
                    stringResource(R.string.settings_delete_button),
                    color =
                        if (typed.trim() ==
                            word
                        ) {
                            Kpakam.colors.danger
                        } else {
                            Kpakam.colors.muted
                        },
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.edit_cancel), color = Kpakam.colors.text) }
        },
    )
}
