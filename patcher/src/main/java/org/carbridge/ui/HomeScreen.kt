package org.carbridge.ui

import org.carbridge.ShizukuMode
import androidx.compose.ui.platform.LocalUriHandler
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.PersistableBundle
import android.text.format.Formatter
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.carbridge.AddChoice
import org.carbridge.CarCopy
import org.carbridge.Caution
import org.carbridge.HomeModel
import org.carbridge.R
import org.carbridge.Job
import org.carbridge.JobStep
import org.carbridge.RestoreRequest
import org.carbridge.Setup
import org.carbridge.SourceApp
import org.carbridge.UserMessage
import org.carbridge.Version
import org.carbridge.core.CarOptions
import org.carbridge.patch.KeyBackup

/** Home screen callbacks; each defaults to a no-op, so screenshots need none. */
class HomeActions(
    val allowInstalls: () -> Unit = {},
    val openAndroidAuto: () -> Unit = {},
    val markDeveloperMode: () -> Unit = {},
    val markUnknownSources: () -> Unit = {},
    val add: (SourceApp) -> Unit = {},
    val update: (CarCopy) -> Unit = {},
    val updateAll: (List<CarCopy>) -> Unit = {},
    val openMorphe: () -> Unit = {},
    val setFillScreen: (CarCopy, Boolean) -> Unit = { _, _ -> },
    val setKeepMedia: (CarCopy, Boolean) -> Unit = { _, _ -> },
    val setHideOnPhone: (CarCopy, Boolean) -> Unit = { _, _ -> },
    val remove: (CarCopy) -> Unit = {},
    val open: (CarCopy) -> Unit = {},
    /** Opens Android's file picker to save the key backup under the given name. */
    val saveBackup: (String) -> Unit = {},
    val pickBackup: () -> Unit = {},
    /** Opens the file picker for a backup to test with its code, without restoring it. */
    val checkBackup: () -> Unit = {},
    /** Opens the file picker for APKs; with a copy, the files must be a newer version of its app. */
    val pickApks: (CarCopy?) -> Unit = {},
)

private val SignShape = RoundedCornerShape(20.dp)
private val SignBorderShape = RoundedCornerShape(15.dp)
private val Gutter = 16.dp

/** Below this many apps the list is short enough to scroll by hand. */
private const val INDEX_MIN_APPS = 15
private val APP_LIST_KEYS = setOf("add-title", "add-file", "search", "auto-toggle", "add-empty", "footer")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(model: HomeModel, actions: HomeActions) {
    val snackbar = remember { SnackbarHostState() }
    var details by remember { mutableStateOf<UserMessage?>(null) }
    var showAbout by rememberSaveable { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showSetup by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var cautionFor by remember { mutableStateOf<SourceApp?>(null) }
    var restoreFirstFor by remember { mutableStateOf<SourceApp?>(null) }
    var backupLater by rememberSaveable { mutableStateOf(false) }

    val detailsLabel = stringResource(R.string.details)
    LaunchedEffect(model) {
        model.messages.collect { message ->
            val result = snackbar.showSnackbar(
                message.text,
                actionLabel = if (message.details != null) detailsLabel else null,
                withDismissAction = message.details == null,
                duration = SnackbarDuration.Long,
            )
            if (result == SnackbarResult.ActionPerformed) details = message
        }
    }

    val catalog = model.catalog
    val job = model.job
    val setup = model.setup
    val busy = job != null || model.backgroundBuild != null
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Apps shown under "Add an app", computed here so the letter index can see them
    val search = query.trim()
    val shown = catalog?.available.orEmpty().filter {
        // A search is explicit, so it also finds apps that already have Android Auto
        if (search.isNotEmpty()) it.label.contains(search, ignoreCase = true)
        else model.showAutoApps || !it.hasAndroidAuto
    }
    val indexed = shown.size >= INDEX_MIN_APPS
    val labels = remember(shown) { shown.map { it.label } }
    // Copies signed with a key this phone lost; a key backup can make them updatable again
    val lost = catalog?.copies?.count { it.needsRestore } ?: 0
    val offerRestore = lost > 0 && !model.restoreDeclined
    val addApp = { app: SourceApp -> if (app.caution != null) cautionFor = app else actions.add(app) }

    Scaffold(
        // Edge to edge: lift messages above the navigation bar unless the progress bar already does
        snackbarHost = { SnackbarHost(snackbar, if (busy) Modifier else Modifier.navigationBarsPadding()) },
        bottomBar = {
            AnimatedVisibility(busy, enter = slideInVertically { it }, exit = slideOutVertically { it }) {
                when {
                    job != null -> JobBar(job, waiting = (model.queued - 1).coerceAtLeast(0))
                    model.backgroundBuild != null -> BackgroundBar(model.backgroundBuild!!)
                }
            }
        },
        contentWindowInsets = WindowInsets(0),
    ) { inner ->
        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                Modifier.fillMaxSize(),
                state = listState,
                contentPadding = PaddingValues(
                    bottom = inner.calculateBottomPadding() + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp,
                ),
            ) {
                item("header") { SignHeader(onSettings = { showSettings = true }, onAbout = { showAbout = true }) }

                item("setup") {
                    if (!setup.complete || showSetup) {
                        SetupCard(setup, actions, onHide = if (setup.complete) ({ showSetup = false }) else null)
                    } else {
                        SetupDone(onShow = { showSetup = true })
                    }
                }

                if (offerRestore) {
                    item("restore") {
                        KeyCard(
                            title = pluralStringResource(R.plurals.restore_card_title, lost, lost),
                            body = stringResource(R.string.restore_card_body),
                            action = stringResource(R.string.restore_backup),
                            onAction = actions.pickBackup,
                            other = stringResource(R.string.restore_none),
                            onOther = model::declineRestore,
                        )
                    }
                } else if (model.restorable.isNotEmpty()) {
                    item("re-add") {
                        val apps = model.restorable
                        KeyCard(
                            title = pluralStringResource(R.plurals.readd_title, apps.size, apps.size),
                            body = stringResource(R.string.readd_body, apps.joinToString { it.label }),
                            action = stringResource(R.string.readd_action),
                            onAction = model::addRestorable,
                            other = stringResource(R.string.not_now),
                            onOther = model::dismissRestorable,
                        )
                    }
                } else if (catalog != null && catalog.copies.any { !it.signedElsewhere } && !model.backedUp && !backupLater) {
                    item("backup") {
                        KeyCard(
                            title = stringResource(R.string.backup_card_title),
                            body = stringResource(R.string.backup_card_body),
                            action = stringResource(R.string.back_up),
                            onAction = model::startBackup,
                            other = stringResource(R.string.later),
                            onOther = { backupLater = true },
                        )
                    }
                }

                item("car-title") { SectionTitle(stringResource(R.string.on_your_car)) }
                when {
                    catalog == null -> item("loading") { Loading() }
                    catalog.copies.isEmpty() -> item("car-empty") {
                        Column {
                            Hint(stringResource(R.string.car_empty))
                            // A new phone starts here; its old copies come back through the key backup
                            TextButton(onClick = actions.pickBackup, modifier = Modifier.padding(start = 4.dp)) {
                                Text(stringResource(R.string.restore_from_other_phone))
                            }
                        }
                    }
                    else -> {
                        val outdated = catalog.copies.filter { it.outdated && it.updatable }
                        if (outdated.size > 1) {
                            item("update-all") { UpdateAllRow(outdated.size, enabled = !busy) { actions.updateAll(outdated) } }
                        }
                        items(catalog.copies, key = { "car:" + it.packageName }) { copy ->
                        CarSign(
                            copy,
                            busy,
                            onUpdate = { actions.update(copy) },
                            onRemove = { actions.remove(copy) },
                            onOpen = { actions.open(copy) },
                            onFillScreen = { fill -> actions.setFillScreen(copy, fill) },
                            onKeepMedia = { keep -> actions.setKeepMedia(copy, keep) },
                            onUpdateFromFile = { actions.pickApks(copy) },
                            onHideOnPhone = { hide -> actions.setHideOnPhone(copy, hide) },
                            onAutoUpdate = if (model.autoUpdate) ({ allowed -> model.setAutoUpdateAllowed(copy, allowed) }) else null,
                        )
                    }
                    }
                }

                item("add-title") { SectionTitle(stringResource(R.string.add_an_app)) }
                item("add-file") {
                    OutlinedButton(
                        onClick = { actions.pickApks(null) },
                        enabled = !busy,
                        modifier = Modifier.padding(start = Gutter, end = Gutter, bottom = 12.dp),
                    ) { Text(stringResource(R.string.add_from_file)) }
                }
                if (catalog != null) {
                    if (catalog.available.size > 6) {
                        item("search") { SearchField(query) { query = it } }
                    }
                    val autoCount = catalog.available.count { it.hasAndroidAuto }
                    if (search.isEmpty() && autoCount > 0) {
                        item("auto-toggle") { AutoAppsToggle(autoCount, model.showAutoApps, model::toggleAutoApps) }
                    }
                    if (shown.isEmpty()) {
                        item("add-empty") {
                            Hint(if (search.isEmpty()) stringResource(R.string.add_empty_all) else stringResource(R.string.add_empty_search, search))
                        }
                    }
                    items(shown, key = { "src:" + it.info.packageName }) { app ->
                        SourceRow(
                            app,
                            enabled = !busy,
                            endPadding = if (indexed) Gutter + IndexWidth else Gutter,
                            onAdd = { if (offerRestore) restoreFirstFor = app else addApp(app) },
                        )
                    }
                }

                item("footer") { Footer(onAbout = { showAbout = true }) }
            }

            if (indexed) {
                // App rows come after every other item but the footer
                val firstSource by remember { derivedStateOf { listState.layoutInfo.totalItemsCount - 1 - labels.size } }
                // Only once "Add an app" reaches the top, so the strip never covers the car copies
                val inAppList by remember {
                    derivedStateOf {
                        val key = listState.layoutInfo.visibleItemsInfo.firstOrNull()?.key as? String
                        key != null && (key in APP_LIST_KEYS || key.startsWith("src:"))
                    }
                }
                val statusBarPx = WindowInsets.statusBars.getTop(LocalDensity.current)
                val topApp by remember(statusBarPx) {
                    derivedStateOf {
                        // A row half under the status bar doesn't count as the top one
                        listState.layoutInfo.visibleItemsInfo
                            .firstOrNull { (it.key as? String)?.startsWith("src:") == true && it.offset + it.size / 2 > statusBarPx }
                            ?.let { labels.getOrNull(it.index - firstSource) }
                    }
                }
                AnimatedVisibility(
                    inAppList,
                    Modifier.align(Alignment.CenterEnd).fillMaxHeight()
                        .windowInsetsPadding(WindowInsets.statusBars.union(WindowInsets.navigationBars))
                        .padding(top = 8.dp, bottom = inner.calculateBottomPadding() + 8.dp),
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    AlphabetIndex(
                        present = remember(labels) { labels.map(Letters::of).toSet() },
                        current = topApp?.let(Letters::of),
                        onLetter = { letter ->
                            Letters.target(labels, letter)?.let { i -> scope.launch { listState.scrollToItem(firstSource + i) } }
                        },
                        modifier = Modifier.padding(end = 4.dp),
                    )
                }
            }

            // Keeps light status bar icons readable once the header scrolls away
            Box(
                Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars)
                    .background(LocalSigns.current.guide),
            )
        }
    }

    model.pendingAdd?.let { choice ->
        AddChoiceDialog(choice, onConfirm = { model.confirmAdd(choice, it) }, onDismiss = model::cancelAdd)
    }
    cautionFor?.let { app ->
        CautionDialog(
            app,
            onDismiss = { cautionFor = null },
            onAddAnyway = { cautionFor = null; actions.add(app) },
            onOpenMorphe = { cautionFor = null; actions.openMorphe() },
        )
    }
    restoreFirstFor?.let { app ->
        AlertDialog(
            onDismissRequest = { restoreFirstFor = null },
            title = { Text(stringResource(R.string.restore_first_title)) },
            text = { Text(stringResource(R.string.restore_first_body)) },
            confirmButton = {
                TextButton(onClick = { restoreFirstFor = null; actions.pickBackup() }) { Text(stringResource(R.string.restore_backup)) }
            },
            dismissButton = { TextButton(onClick = { restoreFirstFor = null; addApp(app) }) { Text(stringResource(R.string.add_without_key)) } },
        )
    }
    model.newBackup?.let { backup ->
        BackupCodeDialog(backup.code, onSave = { actions.saveBackup(model.backupFileName) }, onDismiss = model::cancelBackup)
    }
    model.restoreRequest?.let { request ->
        RestoreDialog(request, onRestore = model::restore, onDismiss = model::cancelRestore)
    }
    if (showAbout) {
        ModalBottomSheet(onDismissRequest = { showAbout = false }) { About() }
    }
    if (showSettings) {
        // Short enough to show whole, so the key buttons never start behind the navigation bar
        ModalBottomSheet(
            onDismissRequest = { showSettings = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Settings(
                autoUpdate = model.autoUpdate,
                onAutoUpdate = model::switchAutoUpdate,
                defaults = model.defaults,
                onDefaults = model::changeDefaults,
                backedUp = model.backedUp,
                notInBackup = model.notInBackup,
                onBackUp = { showSettings = false; model.startBackup() },
                onRestore = { showSettings = false; actions.pickBackup() },
                onCheck = { showSettings = false; actions.checkBackup() },
                shizuku = model.shizuku,
                useShizuku = model.useShizuku,
                onUseShizuku = model::switchShizuku,
                onAllowShizuku = model::allowShizuku,
            )
        }
    }
    details?.let { message ->
        AlertDialog(
            onDismissRequest = { details = null },
            confirmButton = { TextButton(onClick = { details = null }) { Text(stringResource(R.string.close)) } },
            title = { Text(message.text) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        stringResource(R.string.details_intro),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(message.details.orEmpty(), style = MaterialTheme.typography.bodySmall)
                }
            },
        )
    }
}

/** A guide-sign blank: green field, inset white border. */
@Composable
private fun GuidePanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val signs = LocalSigns.current
    Surface(modifier, shape = SignShape, color = signs.guide, contentColor = signs.onGuide, shadowElevation = 2.dp) {
        Column(
            Modifier.padding(5.dp).border(2.dp, signs.onGuide, SignBorderShape)
                .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            content = content,
        )
    }
}

@Composable
private fun SignHeader(onSettings: () -> Unit, onAbout: () -> Unit) {
    val signs = LocalSigns.current
    Column(
        Modifier.fillMaxWidth().background(signs.guide)
            .padding(WindowInsets.statusBars.asPaddingValues())
            .padding(start = Gutter, end = 4.dp, top = 12.dp, bottom = 28.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(BridgeMark, contentDescription = null, tint = signs.onGuide, modifier = Modifier.size(40.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.displaySmall,
                color = signs.onGuide,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            IconButton(onClick = onSettings) {
                Icon(Icons.Rounded.Settings, contentDescription = stringResource(R.string.settings), tint = signs.onGuide)
            }
            IconButton(onClick = onAbout) {
                Icon(Icons.Rounded.Info, contentDescription = stringResource(R.string.about_cd), tint = signs.onGuide)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.tagline),
            style = MaterialTheme.typography.titleMedium,
            color = signs.onGuideMuted,
            modifier = Modifier.padding(end = Gutter),
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.headlineSmall,
        modifier = Modifier.padding(start = Gutter, end = Gutter, top = 32.dp, bottom = 12.dp).semantics { heading() },
    )
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = Gutter, vertical = 4.dp),
    )
}

@Composable
private fun Loading() {
    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun SetupCard(setup: Setup, actions: HomeActions, onHide: (() -> Unit)?) {
    val signs = LocalSigns.current
    val steps = listOf(
        SetupStep(
            stringResource(R.string.setup_install_title),
            stringResource(R.string.setup_install_body),
            setup.canInstall,
        ) { Button(onClick = actions.allowInstalls, colors = cautionButton()) { Text(stringResource(R.string.allow)) } },
        SetupStep(
            stringResource(R.string.setup_dev_title),
            stringResource(R.string.setup_dev_body),
            setup.developerMode,
        ) {
            Button(onClick = actions.openAndroidAuto, colors = cautionButton()) { Text(stringResource(R.string.open_android_auto)) }
            TextButton(onClick = actions.markDeveloperMode, colors = ButtonDefaults.textButtonColors(contentColor = signs.onCaution)) {
                Text(stringResource(R.string.its_on))
            }
        },
        SetupStep(
            stringResource(R.string.setup_unknown_title),
            stringResource(R.string.setup_unknown_body),
            setup.unknownSources,
        ) {
            Button(onClick = actions.openAndroidAuto, colors = cautionButton()) { Text(stringResource(R.string.open_android_auto)) }
            TextButton(onClick = actions.markUnknownSources, colors = ButtonDefaults.textButtonColors(contentColor = signs.onCaution)) {
                Text(stringResource(R.string.its_on))
            }
        },
    )
    val current = steps.indexOfFirst { !it.done }

    Surface(
        Modifier.fillMaxWidth().padding(start = Gutter, end = Gutter, top = 20.dp),
        shape = SignShape,
        color = signs.caution,
        contentColor = signs.onCaution,
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(if (current == -1) R.string.setup_done_title else R.string.setup_title),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                if (onHide != null) {
                    IconButton(onClick = onHide) { Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.hide_setup)) }
                }
            }
            Text(
                stringResource(R.string.setup_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = signs.onCautionMuted,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
            )
            steps.forEachIndexed { i, step ->
                StepRow(number = i + 1, step = step, active = i == current)
            }
        }
    }
}

private class SetupStep(
    val title: String,
    val body: String,
    val done: Boolean,
    val actions: @Composable () -> Unit,
)

@Composable
private fun cautionButton() = LocalSigns.current.let {
    ButtonDefaults.buttonColors(containerColor = it.onCaution, contentColor = it.caution)
}

@Composable
private fun StepRow(number: Int, step: SetupStep, active: Boolean) {
    val signs = LocalSigns.current
    Row(Modifier.fillMaxWidth().padding(top = 16.dp)) {
        Box(
            Modifier.size(32.dp).clip(CircleShape)
                .then(if (step.done) Modifier.background(signs.onCaution) else Modifier.border(2.dp, signs.onCaution, CircleShape)),
            contentAlignment = Alignment.Center,
        ) {
            if (step.done) {
                Icon(Icons.Rounded.Check, contentDescription = stringResource(R.string.step_done), tint = signs.caution, modifier = Modifier.size(20.dp))
            } else {
                Text("$number", style = MaterialTheme.typography.titleMedium)
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f).alpha(if (step.done || active) 1f else 0.72f)) {
            Text(step.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp))
            if (active) {
                Text(step.body, style = MaterialTheme.typography.bodyMedium, color = signs.onCautionMuted, modifier = Modifier.padding(top = 2.dp))
                FlowRow(
                    Modifier.padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) { step.actions() }
            }
        }
    }
}

@Composable
private fun SetupDone(onShow: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = Gutter, end = 4.dp, top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.setup_complete), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        TextButton(onClick = onShow) { Text(stringResource(R.string.setup_steps)) }
    }
}

@Composable
private fun CarSign(
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
                                else -> copy.version?.name ?: stringResource(R.string.copy_in_android_auto)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = signs.onGuideMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
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
                        if (copy.fromFile && !copy.signedElsewhere) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_update_from_file)) },
                                leadingIcon = { Icon(Icons.Rounded.Refresh, contentDescription = null) },
                                onClick = { menu = false; onUpdateFromFile() },
                            )
                        }
                        if (copy.updatable) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.menu_update_copy)) },
                                leadingIcon = { Icon(Icons.Rounded.Refresh, contentDescription = null) },
                                onClick = { menu = false; onUpdate() },
                            )
                        }
                        if (copy.updatable && copy.portraitLocked) {
                            DropdownMenuItem(
                                text = { Text(stringResource(if (copy.options.fillScreen) R.string.menu_keep_portrait else R.string.menu_fill_screen)) },
                                leadingIcon = { Icon(LandscapeGlyph, contentDescription = null) },
                                onClick = { menu = false; onFillScreen(!copy.options.fillScreen) },
                            )
                        }
                        if (copy.updatable && copy.hasMediaService) {
                            val keep = copy.options.keepMediaServices
                            DropdownMenuItem(
                                // Kept media services let Android Auto show its audio player for the copy
                                text = { Text(stringResource(if (keep) R.string.menu_full_app else R.string.menu_audio_player)) },
                                leadingIcon = { Icon(Icons.Rounded.PlayArrow, contentDescription = null) },
                                onClick = { menu = false; onKeepMedia(!keep) },
                            )
                        }
                        if (copy.updatable) {
                            val hidden = copy.options.hideOnPhone
                            DropdownMenuItem(
                                text = { Text(stringResource(if (hidden) R.string.menu_show_on_phone else R.string.menu_hide_on_phone)) },
                                leadingIcon = { Icon(Icons.Rounded.Phone, contentDescription = null) },
                                onClick = { menu = false; onHideOnPhone(!hidden) },
                            )
                        }
                        if (copy.updatable && onAutoUpdate != null) {
                            val allowed = copy.autoUpdateAllowed
                            DropdownMenuItem(
                                text = { Text(stringResource(if (allowed) R.string.menu_no_auto_update else R.string.menu_auto_update)) },
                                leadingIcon = { Icon(Icons.Rounded.Refresh, contentDescription = null) },
                                onClick = { menu = false; onAutoUpdate(!allowed) },
                            )
                        }
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
    version.name?.takeIf { it != other.name } ?: stringResource(R.string.version_build, version.code)

@Composable
private fun AppIcon(icon: ImageBitmap, sizeDp: Int) {
    Image(icon, contentDescription = null, modifier = Modifier.size(sizeDp.dp))
}

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit) {
    TextField(
        value = query,
        onValueChange = onChange,
        placeholder = { Text(stringResource(R.string.search_apps)) },
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
        trailingIcon = if (query.isNotEmpty()) {
            { IconButton(onClick = { onChange("") }) { Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.clear_search)) } }
        } else null,
        singleLine = true,
        shape = CircleShape,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
            unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        ),
        modifier = Modifier.fillMaxWidth().padding(start = Gutter, end = Gutter, bottom = 8.dp),
    )
}

@Composable
private fun AutoAppsToggle(count: Int, showing: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = Gutter, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            pluralStringResource(if (showing) R.plurals.auto_apps_showing else R.plurals.auto_apps_hidden, count, count),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onToggle) { Text(stringResource(if (showing) R.string.hide_them else R.string.show_them)) }
    }
}

@Composable
private fun SourceRow(app: SourceApp, enabled: Boolean, endPadding: Dp, onAdd: () -> Unit) {
    val context = LocalContext.current
    Row(
        Modifier.fillMaxWidth().padding(start = Gutter, end = endPadding, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(app.icon, 44)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(app.label, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(
                    when (app.caution) {
                        Caution.LicenseCheck -> stringResource(R.string.copy_protected)
                        Caution.Morphe -> stringResource(R.string.patched_with_morphe)
                        null -> null
                    },
                    stringResource(R.string.has_android_auto).takeIf { app.hasAndroidAuto },
                    Formatter.formatShortFileSize(context, app.sizeBytes),
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(8.dp))
        FilledTonalButton(onClick = onAdd, enabled = enabled, contentPadding = ButtonDefaults.ButtonWithIconContentPadding) {
            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.add))
        }
    }
}

@Composable
private fun UpdateAllRow(count: Int, enabled: Boolean, onClick: () -> Unit) {
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

@Composable
private fun AddChoiceDialog(choice: AddChoice, onConfirm: (CarOptions) -> Unit, onDismiss: () -> Unit) {
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
private fun CautionDialog(app: SourceApp, onDismiss: () -> Unit, onAddAnyway: () -> Unit, onOpenMorphe: () -> Unit) {
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
private fun Footer(onAbout: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(start = Gutter, end = Gutter, top = 32.dp)) {
        Text(
            stringResource(R.string.footer_safety),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(onClick = onAbout, modifier = Modifier.padding(top = 12.dp)) { Text(stringResource(R.string.how_it_works)) }
    }
}

@Composable
private fun JobBar(job: Job, waiting: Int) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        shadowElevation = 8.dp,
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(job.icon, 40)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        job.headline(LocalContext.current, waiting),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        job.stepText(LocalContext.current),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            LaneProgress(job.step, JobStep.entries.size, Modifier.padding(top = 14.dp))
        }
    }
}

@Composable
private fun BackgroundBar(label: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        shadowElevation = 8.dp,
    ) {
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
            Spacer(Modifier.width(14.dp))
            Text(
                stringResource(R.string.background_update, label),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Progress drawn as lane markings: finished dashes solid, the current one pulsing. */
@Composable
private fun LaneProgress(current: Int, total: Int, modifier: Modifier = Modifier) {
    val pulse by rememberInfiniteTransition(label = "lane").animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "pulse",
    )
    val on = MaterialTheme.colorScheme.primary
    val off = MaterialTheme.colorScheme.outlineVariant
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(total) { i ->
            Box(
                Modifier.weight(1f).height(6.dp).clip(CircleShape).background(
                    when {
                        i < current -> on
                        i == current -> on.copy(alpha = pulse)
                        else -> off
                    },
                ),
            )
        }
    }
}

@Composable
private fun About() {
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
private fun Settings(
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

@Composable
private fun KeyCard(title: String, body: String, action: String, onAction: () -> Unit, other: String, onOther: () -> Unit) {
    val signs = LocalSigns.current
    Surface(
        Modifier.fillMaxWidth().padding(start = Gutter, end = Gutter, top = 20.dp),
        shape = SignShape,
        color = signs.caution,
        contentColor = signs.onCaution,
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = signs.onCautionMuted,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Button(onClick = onAction, colors = cautionButton()) { Text(action) }
                TextButton(onClick = onOther, colors = ButtonDefaults.textButtonColors(contentColor = signs.onCaution)) {
                    Text(other)
                }
            }
        }
    }
}

@Composable
private fun BackupCodeDialog(code: String, onSave: () -> Unit, onDismiss: () -> Unit) {
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
private fun RestoreDialog(request: RestoreRequest, onRestore: (String) -> Unit, onDismiss: () -> Unit) {
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
