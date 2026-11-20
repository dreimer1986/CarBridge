package org.carbridge.ui

import org.carbridge.ShizukuMode
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import org.carbridge.R
import org.carbridge.core.CarOptions

@Composable
internal fun About() {
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text(stringResource(R.string.how_it_works), style = MaterialTheme.typography.headlineSmall)
        AboutItem(stringResource(R.string.about_copy_title), stringResource(R.string.about_copy_body))
        AboutItem(stringResource(R.string.about_limits_title), stringResource(R.string.about_limits_body))
        AboutItem(stringResource(R.string.about_key_title), stringResource(R.string.about_key_body))
        AboutItem(stringResource(R.string.about_fair_title), stringResource(R.string.about_fair_body))
        AboutItem(stringResource(R.string.about_free_title), stringResource(R.string.about_free_body))
    }
}

@Composable
internal fun Settings(
    autoUpdate: Boolean,
    onAutoUpdate: (Boolean) -> Unit,
    defaults: CarOptions,
    onDefaults: (CarOptions) -> Unit,
    backedUp: Boolean,
    notInBackup: Int,
    onBackUp: () -> Unit,
    onRestore: () -> Unit,
    onCheck: () -> Unit,
    shizuku: ShizukuMode.State,
    useShizuku: Boolean,
    onUseShizuku: (Boolean) -> Unit,
    onAllowShizuku: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Text(stringResource(R.string.settings), style = MaterialTheme.typography.headlineSmall)
        SettingSwitch(stringResource(R.string.auto_update_title), stringResource(R.string.auto_update_body), autoUpdate, onAutoUpdate)
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            AboutItem(stringResource(R.string.defaults_title), stringResource(R.string.defaults_body))
            SettingSwitch(stringResource(R.string.choice_fill_title), stringResource(R.string.defaults_fill_body), defaults.fillScreen) {
                onDefaults(defaults.copy(fillScreen = it))
            }
            SettingSwitch(stringResource(R.string.choice_player_title), stringResource(R.string.defaults_player_body), defaults.keepMediaServices) {
                onDefaults(defaults.copy(keepMediaServices = it))
            }
            SettingSwitch(stringResource(R.string.menu_hide_on_phone), stringResource(R.string.defaults_hide_body), defaults.hideOnPhone) {
                onDefaults(defaults.copy(hideOnPhone = it))
            }
        }
        Column {
            AboutItem(
                stringResource(R.string.key_title),
                listOfNotNull(
                    stringResource(if (backedUp) R.string.key_backed_up else R.string.key_not_backed_up),
                    if (backedUp && notInBackup > 0) pluralStringResource(R.plurals.key_copies_missing, notInBackup, notInBackup) else null,
                    stringResource(R.string.key_body),
                ).joinToString(" "),
            )
            FlowRow(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = onBackUp) { Text(stringResource(if (backedUp) R.string.back_up_again else R.string.back_up_key)) }
                OutlinedButton(onClick = onRestore) { Text(stringResource(R.string.restore_backup)) }
                OutlinedButton(onClick = onCheck) { Text(stringResource(R.string.check_backup)) }
            }
        }
        ShizukuSetting(shizuku, useShizuku, onUseShizuku, onAllowShizuku)
    }
}

@Composable
private fun SettingSwitch(title: String, body: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) { AboutItem(title, body) }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun ShizukuSetting(state: ShizukuMode.State, on: Boolean, onSwitch: (Boolean) -> Unit, onAllow: () -> Unit) {
    val context = LocalContext.current
    val uri = LocalUriHandler.current
    val title = stringResource(R.string.shizuku_title)
    val what = stringResource(R.string.shizuku_what)
    when (state) {
        ShizukuMode.State.Ready -> SettingSwitch(title, what, on, onSwitch)
        else -> Column {
            AboutItem(
                title,
                what + " " + when (state) {
                    ShizukuMode.State.NotInstalled -> stringResource(R.string.shizuku_not_installed)
                    ShizukuMode.State.NotRunning ->
                        stringResource(R.string.shizuku_not_running) +
                            if (on) " " + stringResource(R.string.shizuku_until_then) else ""
                    else -> stringResource(R.string.shizuku_needs_permission)
                },
            )
            val (label, action) = when (state) {
                ShizukuMode.State.NotInstalled -> stringResource(R.string.get_shizuku) to { uri.openUri("https://shizuku.rikka.app/") }
                ShizukuMode.State.NotRunning -> stringResource(R.string.open_shizuku) to {
                    context.packageManager.getLaunchIntentForPackage(ShizukuMode.PACKAGE)?.let(context::startActivity) ?: Unit
                }
                else -> stringResource(R.string.allow_carbridge) to onAllow
            }
            OutlinedButton(onClick = action, modifier = Modifier.padding(top = 12.dp)) { Text(label) }
        }
    }
}

@Composable
private fun AboutItem(title: String, body: String) {
    Column {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(
            body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
