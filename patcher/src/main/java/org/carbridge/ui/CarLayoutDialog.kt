package org.carbridge.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import org.carbridge.core.SplitScreenOptions

/** Picks how a copy shows on the car screen; [current] and the result are null for full screen. */
@Composable
fun CarLayoutDialog(
    current: SplitScreenOptions?,
    onApply: (SplitScreenOptions?) -> Unit,
    onDismiss: () -> Unit,
) {
    var split by rememberSaveable { mutableStateOf(current != null) }
    var underBack by rememberSaveable { mutableStateOf(current?.fullHeight == true) }
    val chosen = if (split) SplitScreenOptions(fullHeight = underBack) else null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Car screen layout") },
        text = {
            Column(Modifier.selectableGroup()) {
                LayoutChoice(
                    selected = !split,
                    title = "Full screen",
                    detail = "The app fills the car screen.",
                    onSelect = { split = false },
                ) { CarPicture(split = false, underBack = false) }
                LayoutChoice(
                    selected = split,
                    title = "Next to the media cards",
                    detail = "Android Auto's media and notification cards stay visible. Needs Shizuku.",
                    onSelect = { split = true },
                ) { CarPicture(split = true, underBack = underBack) }
                if (split) {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 40.dp, top = 4.dp)
                            .toggleable(value = underBack, role = Role.Checkbox, onValueChange = { underBack = it }),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = underBack, onCheckedChange = null)
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("Let the app go under the Back button", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "More room, but Back covers the app's top-right corner.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        // Applying rebuilds and reinstalls the copy, so only offer it for a real change
        confirmButton = { TextButton(onClick = { onApply(chosen) }, enabled = chosen != current) { Text("Apply") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun LayoutChoice(
    selected: Boolean,
    title: String,
    detail: String,
    onSelect: () -> Unit,
    picture: @Composable () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(vertical = 8.dp),
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(16.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.size(8.dp))
            picture()
        }
    }
}

/** A sketch of the car screen: the app in the primary color, the media cards and Back in grey. */
@Composable
private fun CarPicture(split: Boolean, underBack: Boolean) {
    val colors = MaterialTheme.colorScheme
    val frame = RoundedCornerShape(6.dp)
    val part = RoundedCornerShape(3.dp)
    Row(
        Modifier.size(144.dp, 54.dp).clip(frame).border(1.dp, colors.outline, frame).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(Modifier.weight(1f).fillMaxHeight()) {
            Box(
                Modifier.fillMaxSize().padding(top = if (split && !underBack) 13.dp else 0.dp)
                    .clip(part).background(colors.primary),
            )
            if (split) {
                Box(
                    Modifier.align(Alignment.TopEnd).padding(top = 1.dp, end = 1.dp).size(24.dp, 10.dp)
                        .clip(CircleShape).background(colors.surface).border(1.dp, colors.outline, CircleShape),
                )
            }
        }
        if (split) {
            Column(Modifier.width(38.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.weight(1f).fillMaxWidth().clip(part).background(colors.outlineVariant))
                Box(Modifier.weight(1f).fillMaxWidth().clip(part).background(colors.outlineVariant))
            }
        }
    }
}
