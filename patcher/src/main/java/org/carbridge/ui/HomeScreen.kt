package org.carbridge.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.carbridge.CarCopy
import org.carbridge.HomeModel
import org.carbridge.R
import org.carbridge.SourceApp
import org.carbridge.UserMessage

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
