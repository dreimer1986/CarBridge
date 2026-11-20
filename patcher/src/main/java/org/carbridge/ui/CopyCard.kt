package org.carbridge.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.carbridge.CarCopy
import org.carbridge.R
import org.carbridge.Version

@Composable
internal fun CarSign(
    copy: CarCopy,
    busy: Boolean,
    onUpdate: () -> Unit,
    onRemove: () -> Unit,
    onOpen: () -> Unit,
    onFillScreen: (Boolean) -> Unit,
    onKeepMedia: (Boolean) -> Unit,
    onUpdateFromFile: () -> Unit,
    onHideOnPhone: (Boolean) -> Unit,
    /** Null while automatic updates are off for every copy. */
    onAutoUpdate: ((Boolean) -> Unit)?,
) {
    val signs = LocalSigns.current
    var menu by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 4.dp)) {
        if (copy.outdated && copy.updatable) UpdateTab()
        GuidePanel(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Opening on the phone is how users sign in to a copy, which starts with no data
                Row(
                    Modifier.weight(1f).clickable(enabled = !busy, onClickLabel = stringResource(R.string.open_copy, copy.label), onClick = onOpen),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppIcon(copy.icon, 40)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(copy.label, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            when {
                                copy.fromFile -> listOfNotNull(stringResource(R.string.copy_from_file), copy.version?.name).joinToString(" · ")
                                copy.original == null -> stringResource(R.string.copy_original_removed)
                                copy.madeByOtherCarBridge -> stringResource(R.string.copy_other_carbridge)
                                copy.signedElsewhere -> stringResource(R.string.copy_restore_key)
                                copy.carProblem != null -> stringResource(R.string.car_problem_subtitle, stringResource(copy.carProblem.text))
                                copy.outdated -> "${versionText(copy.version!!, copy.originalVersion!!)} → " +
                                    versionText(copy.originalVersion, copy.version)
                                else -> copy.version?.name?.let(::shortVersion) ?: stringResource(R.string.copy_in_android_auto)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = signs.onGuideMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val tags = choiceTags(copy, autoUpdates = onAutoUpdate != null)
                        if (tags.isNotEmpty()) {
                            Text(
                                tags.joinToString(" · "),
                                style = MaterialTheme.typography.labelMedium,
                                color = signs.onGuide,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                    }
                }
                if (copy.outdated && copy.updatable) {
                    Button(
                        onClick = onUpdate,
                        enabled = !busy,
                        colors = ButtonDefaults.buttonColors(containerColor = signs.caution, contentColor = signs.onCaution),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                    ) { Text(stringResource(R.string.update)) }
                }
                Box {
                    IconButton(onClick = { menu = true }, enabled = !busy) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.more_for, copy.label), tint = signs.onGuide)
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_open_on_phone)) },
                            leadingIcon = { Icon(Icons.Rounded.Phone, contentDescription = null) },
                            onClick = { menu = false; onOpen() },
                        )
                        if (copy.updatable) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_update_copy)) },
                                leadingIcon = { Icon(Icons.Rounded.Refresh, contentDescription = null) },
                                onClick = { menu = false; onUpdate() },
                            )
                        }
                        if (copy.fromFile && !copy.signedElsewhere) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_update_from_file)) },
                                leadingIcon = { Icon(FileGlyph, contentDescription = null) },
                                onClick = { menu = false; onUpdateFromFile() },
                            )
                        }
                        if (copy.updatable) {
                            HorizontalDivider()
                            if (copy.portraitLocked) {
                                ChoiceItem(R.string.menu_fill_screen, LandscapeGlyph, copy.options.fillScreen) {
                                    menu = false; onFillScreen(it)
                                }
                            }
                            if (copy.hasMediaService) {
                                // Kept media services let Android Auto show its audio player for the copy
                                ChoiceItem(R.string.menu_audio_player, Icons.Rounded.PlayArrow, copy.options.keepMediaServices) {
                                    menu = false; onKeepMedia(it)
                                }
                            }
                            ChoiceItem(R.string.menu_show_on_phone, AppGridGlyph, !copy.options.hideOnPhone) {
                                menu = false; onHideOnPhone(!it)
                            }
                            if (onAutoUpdate != null) {
                                ChoiceItem(R.string.menu_auto_update, ChargingGlyph, copy.autoUpdateAllowed) {
                                    menu = false; onAutoUpdate(it)
                                }
                            }
                        }
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_remove)) },
                            leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
                            onClick = { menu = false; onRemove() },
                        )
                    }
                }
            }
        }
    }
}

/** A menu choice that shows its current state with a check, and flips it when tapped. */
@Composable
private fun ChoiceItem(label: Int, icon: ImageVector, on: Boolean, onChange: (Boolean) -> Unit) {
    DropdownMenuItem(
        text = { Text(stringResource(label)) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        trailingIcon = if (on) ({ Icon(Icons.Rounded.Check, contentDescription = null) }) else null,
        onClick = { onChange(!on) },
        modifier = Modifier.semantics {
            role = Role.Checkbox
            toggleableState = ToggleableState(on)
        },
    )
}

/** Choices that differ from a plain copy, so the card says how this copy behaves. */
@Composable
private fun choiceTags(copy: CarCopy, autoUpdates: Boolean): List<String> = listOfNotNull(
    stringResource(R.string.tag_portrait).takeIf { copy.portraitLocked && !copy.options.fillScreen },
    stringResource(R.string.tag_audio_player).takeIf { copy.hasMediaService && copy.options.keepMediaServices },
    stringResource(R.string.tag_hidden).takeIf { copy.options.hideOnPhone },
    stringResource(R.string.tag_manual_updates).takeIf { autoUpdates && copy.updatable && !copy.autoUpdateAllowed },
)

/** Drops build metadata, e.g. "6.34.0+18552420-google" becomes "6.34.0". */
internal fun shortVersion(name: String): String =
    name.substringBefore('+').substringBefore(' ').ifEmpty { name }

/** A sign-yellow tab on top of the panel, like the exit tab on a guide sign. */
@Composable
private fun UpdateTab() {
    val signs = LocalSigns.current
    Surface(
        Modifier.padding(start = 20.dp),
        shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
        color = signs.caution,
        contentColor = signs.onCaution,
    ) {
        Text(
            stringResource(R.string.update_ready),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
        )
    }
}

/** Version name, or the build number when both sides share a name (e.g. a same-name rebuild). */
@Composable
private fun versionText(version: Version, other: Version): String =
    version.name?.let(::shortVersion)?.takeIf { it != other.name?.let(::shortVersion) }
        ?: stringResource(R.string.version_build, version.code)

@Composable
internal fun UpdateAllRow(count: Int, enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = Gutter, end = Gutter, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            pluralStringResource(R.plurals.copies_outdated, count, count),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        FilledTonalButton(onClick = onClick, enabled = enabled) { Text(stringResource(R.string.update_all)) }
    }
}
