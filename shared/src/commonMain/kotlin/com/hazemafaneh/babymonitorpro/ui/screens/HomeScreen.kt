package com.hazemafaneh.babymonitorpro.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hazemafaneh.babymonitorpro.core.Role
import com.hazemafaneh.babymonitorpro.ui.components.initialFocus
import com.hazemafaneh.babymonitorpro.ui.components.pressable
import com.hazemafaneh.babymonitorpro.ui.icons.BmpIcons
import com.hazemafaneh.babymonitorpro.ui.layout.gutter
import com.hazemafaneh.babymonitorpro.ui.layout.rememberWindowClass
import com.hazemafaneh.babymonitorpro.ui.layout.tvOverscan
import com.hazemafaneh.babymonitorpro.ui.theme.Space
import com.hazemafaneh.babymonitorpro.ui.theme.Touch

/** The two things the home screen holds. */
private enum class HomeTab(val label: String, val icon: ImageVector) {
    MONITOR("Monitor", BmpIcons.House),
    SETTINGS("Settings", BmpIcons.Sliders),
}

/**
 * Where the app opens: pick a role, or change how it behaves.
 *
 * Settings is a tab here rather than a destination behind the camera screen because that is
 * where it was unreachable from. A device used only for watching never opens the camera
 * screen, so until now it had no route to any setting at all — including the three that only
 * matter to a watching device. A tab is also the shape a television needs: two stops on a
 * horizontal D-pad path, both visible without pressing anything.
 *
 * The selected tab survives configuration changes but is not persisted. A parent who left the
 * app on Settings yesterday is opening it tonight to watch the baby.
 */
@Composable
fun HomeScreen(
    lastRole: Role?,
    night: Boolean,
    onNightChanged: (Boolean) -> Unit,
    onPick: (Role) -> Unit,
    onStop: () -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(HomeTab.MONITOR) }
    val window = rememberWindowClass()

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Row(
            Modifier
                .safeDrawingPadding()
                .padding(horizontal = window.gutter())
                .padding(top = tvOverscan() + Space.sm, bottom = Space.xs)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Space.xs),
        ) {
            for (entry in HomeTab.entries) {
                TabPill(
                    tab = entry,
                    selected = tab == entry,
                    onSelect = { tab = entry },
                    // The remote lands on the tab strip, not inside the content: it is the
                    // top of the screen's focus path, and every other target is one press
                    // down from it.
                    modifier = if (entry == HomeTab.MONITOR) Modifier.initialFocus() else Modifier,
                )
            }
        }

        Box(Modifier.fillMaxSize()) {
            when (tab) {
                HomeTab.MONITOR -> RolePickerScreen(lastRole = lastRole, onPick = onPick)
                HomeTab.SETTINGS -> SettingsScreen(
                    night = night,
                    onNightChanged = onNightChanged,
                    // A tab has nothing to go back to; the camera screen's copy still does.
                    onBack = null,
                    onStop = onStop,
                )
            }
        }
    }
}

/**
 * One tab.
 *
 * A pill rather than an underlined Material tab, for the same reason the rest of the app uses
 * pills: an underline is two pixels of state on a screen read from three metres away, and the
 * focus ring the remote draws needs a shape to follow.
 */
@Composable
private fun TabPill(
    tab: HomeTab,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier
            .widthIn(min = TAB_MIN_WIDTH)
            .heightIn(min = Touch.min)
            .pressable(
                onClick = onSelect,
                role = androidx.compose.ui.semantics.Role.Tab,
                focusShape = MaterialTheme.shapes.extraLarge,
            ),
        shape = MaterialTheme.shapes.extraLarge,
        color = if (selected) scheme.secondaryContainer else scheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            TAB_BORDER,
            if (selected) scheme.secondary else scheme.outlineVariant,
        ),
    ) {
        Row(
            Modifier.padding(horizontal = Space.md, vertical = TAB_V_PADDING),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = tab.icon,
                contentDescription = null,
                tint = if (selected) scheme.onSecondaryContainer else scheme.onSurfaceVariant,
                modifier = Modifier.size(TAB_ICON),
            )
            Spacer(Modifier.width(Space.xs))
            Text(
                text = tab.label,
                style = MaterialTheme.typography.labelLarge.copy(fontSize = TAB_TEXT),
                fontWeight = FontWeight.Bold,
                color = if (selected) scheme.onSecondaryContainer else scheme.onSurfaceVariant,
            )
        }
    }
}

private val TAB_MIN_WIDTH = 132.dp
private val TAB_BORDER = 1.5.dp
private val TAB_V_PADDING = 10.dp
private val TAB_ICON = 18.dp
private val TAB_TEXT = 14.sp
