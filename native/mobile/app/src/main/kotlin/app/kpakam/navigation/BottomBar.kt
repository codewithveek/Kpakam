package app.kpakam.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.kpakam.R
import app.kpakam.shared.ui.Kpakam
import app.kpakam.shared.ui.KpakamType

/** Height of the docked bar, above the system navigation inset. */
val BottomBarHeight = 64.dp

/** The four tabs; Speak sits between History and Debts. */
enum class Tab(val route: String, val label: Int, val icon: ImageVector) {
    HOME("home", R.string.nav_home, Icons.Outlined.Home),
    HISTORY("history", R.string.nav_history, Icons.AutoMirrored.Outlined.FormatListBulleted),
    DEBTS("debts", R.string.nav_debts, Icons.Outlined.People),
    SETTINGS("settings", R.string.nav_settings, Icons.Outlined.Settings),
}

/**
 * A docked, full-width bottom bar (web bottom-nav.tsx): five equal slots with short labels, so nothing
 * ever squeezes. The middle Speak slot shows its mic in the one black pill and opens the voice screen.
 */
@Composable
fun BottomBar(current: Tab?, onSelect: (Tab) -> Unit, onSpeak: () -> Unit, modifier: Modifier = Modifier) {
    val colors = Kpakam.colors
    Column(modifier.fillMaxWidth().background(colors.surface)) {
        HorizontalDivider(color = colors.border, thickness = 1.dp)
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .height(BottomBarHeight)
                    .padding(horizontal = 8.dp)
                    .selectableGroup(),
            horizontalArrangement = Arrangement.Center,
        ) {
            Row(Modifier.widthIn(max = 672.dp)) {
                TabSlot(Tab.HOME, current == Tab.HOME, onSelect, Modifier.weight(1f))
                TabSlot(Tab.HISTORY, current == Tab.HISTORY, onSelect, Modifier.weight(1f))
                SpeakSlot(onSpeak, Modifier.weight(1f))
                TabSlot(Tab.DEBTS, current == Tab.DEBTS, onSelect, Modifier.weight(1f))
                TabSlot(Tab.SETTINGS, current == Tab.SETTINGS, onSelect, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun TabSlot(tab: Tab, selected: Boolean, onSelect: (Tab) -> Unit, modifier: Modifier) {
    val colors = Kpakam.colors
    val tint = if (selected) colors.text else colors.muted
    SlotLayout(
        label = stringResource(tab.label),
        bold = selected,
        color = tint,
        modifier = modifier.selectable(selected = selected, role = Role.Tab, onClick = { onSelect(tab) }),
    ) {
        Icon(tab.icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun SpeakSlot(onSpeak: () -> Unit, modifier: Modifier) {
    val colors = Kpakam.colors
    SlotLayout(
        label = stringResource(R.string.nav_speak),
        bold = true,
        color = colors.text,
        modifier = modifier.clickable(role = Role.Button, onClick = onSpeak),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(width = 48.dp, height = 32.dp).clip(CircleShape).background(colors.ink),
        ) {
            Icon(Icons.Outlined.Mic, contentDescription = null, tint = colors.onInk, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun SlotLayout(
    label: String,
    bold: Boolean,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier,
    icon: @Composable () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
        modifier = modifier.height(BottomBarHeight),
    ) {
        Box(Modifier.height(32.dp), contentAlignment = Alignment.Center) { icon() }
        Text(
            label,
            style = KpakamType.label,
            fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Medium,
            color = color,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Visible,
        )
    }
}
