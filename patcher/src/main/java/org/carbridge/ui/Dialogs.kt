package org.carbridge.ui

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.PersistableBundle
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.carbridge.AddChoice
import org.carbridge.Caution
import org.carbridge.R
import org.carbridge.RestoreRequest
import org.carbridge.SourceApp
import org.carbridge.core.CarOptions
import org.carbridge.patch.KeyBackup

@Composable
internal fun AddChoiceDialog(choice: AddChoice, onConfirm: (CarOptions) -> Unit, onDismiss: () -> Unit) {
    var fill by rememberSaveable { mutableStateOf(choice.defaults.fillScreen) }
    var player by rememberSaveable { mutableStateOf(choice.defaults.keepMediaServices) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_title, choice.app.label)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.add_choice_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (choice.portraitLocked) {
                    ChoiceRow(
                        stringResource(R.string.choice_fill_title),
                        stringResource(R.string.choice_fill_body),
                        fill,
                    ) { fill = it }
                }
                if (choice.hasMediaService) {
                    ChoiceRow(
                        stringResource(R.string.choice_player_title),
                        stringResource(R.string.choice_player_body),
                        player,
                    ) { player = it }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(choice.defaults.copy(keepMediaServices = player, fillScreen = fill)) }) {
                Text(stringResource(R.string.add_to_car))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun ChoiceRow(title: String, body: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 16.dp).toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
internal fun CautionDialog(app: SourceApp, onDismiss: () -> Unit, onAddAnyway: () -> Unit, onOpenMorphe: () -> Unit) {
    when (app.caution) {
        Caution.Morphe -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.morphe_title, app.label)) },
            text = { Text(stringResource(R.string.morphe_body, app.label)) },
            confirmButton = { TextButton(onClick = onOpenMorphe) { Text(stringResource(R.string.open_in_morphe)) } },
            dismissButton = { TextButton(onClick = onAddAnyway) { Text(stringResource(R.string.copy_anyway)) } },
        )
        Caution.LicenseCheck -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.license_title, app.label)) },
            text = { Text(stringResource(R.string.license_body, app.label)) },
            confirmButton = { TextButton(onClick = onAddAnyway) { Text(stringResource(R.string.add_anyway)) } },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        )
        null -> Unit
    }
}

@Composable
internal fun BackupCodeDialog(code: String, onSave: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var saved by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(R.string.code_title)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.code_body),
                    style = MaterialTheme.typography.bodyMedium,
                )
                SelectionContainer {
                    Text(
                        // Three groups per line, so a group never breaks across lines
                        code.split("-").chunked(3).joinToString("\n") { it.joinToString("-") },
                        style = MaterialTheme.typography.titleLarge,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                }
                TextButton(onClick = { copySensitive(context, code) }) { Text(stringResource(R.string.copy_code)) }
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp)
                        .toggleable(value = saved, role = Role.Checkbox, onValueChange = { saved = it }),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = saved, onCheckedChange = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.code_saved), style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = { TextButton(onClick = onSave, enabled = saved) { Text(stringResource(R.string.save_backup_file)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Marked sensitive, so Android hides the code in its clipboard preview. */
private fun copySensitive(context: Context, text: String) {
    val clip = ClipData.newPlainText(context.getString(R.string.code_clip_label), text).apply {
        description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
    }
    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
}

@Composable
internal fun RestoreDialog(request: RestoreRequest, onRestore: (String) -> Unit, onDismiss: () -> Unit) {
    var code by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (request.checkOnly) R.string.check_title else R.string.restore_title)) },
        text = {
            Column {
                Text(stringResource(R.string.restore_prompt), style = MaterialTheme.typography.bodyMedium)
                TextField(
                    value = code,
                    onValueChange = { code = it },
                    placeholder = { Text("XXXX-XXXX-XXXX-XXXX-XXXX-XXXX") },
                    singleLine = true,
                    isError = request.error != null,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        autoCorrectEnabled = false,
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    ),
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                )
                request.error?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            if (request.working) {
                CircularProgressIndicator(Modifier.size(24.dp))
            } else {
                TextButton(onClick = { onRestore(code) }, enabled = KeyBackup.isWellFormed(code)) {
                    Text(stringResource(if (request.checkOnly) R.string.check else R.string.restore))
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !request.working) { Text(stringResource(R.string.cancel)) } },
    )
}
