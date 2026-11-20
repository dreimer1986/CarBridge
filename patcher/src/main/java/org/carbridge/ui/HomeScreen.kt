package org.carbridge.ui

import android.app.Activity
import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.animation.core.animate
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.WindowInsetsSides
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
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

private val APP_LIST_KEYS = setOf("add-title", "add-file", "search", "auto-toggle", "add-empty", "footer", "search-room")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(model: HomeModel, actions: HomeActions) {
    val snackbar = remember { SnackbarHostState() }
    var details by remember { mutableStateOf<UserMessage?>(null) }
    var showAbout by rememberSaveable { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showSetup by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var searchFocused by remember { mutableStateOf(false) }
    var cautionFor by remember { mutableStateOf<SourceApp?>(null) }
    // One app from its row, or several from a selection
    var restoreFirstFor by remember { mutableStateOf<List<SourceApp>?>(null) }
    var batchCaution by remember { mutableStateOf<List<SourceApp>?>(null) }
    var selecting by rememberSaveable { mutableStateOf(false) }
    var selected by rememberSaveable { mutableStateOf(emptyList<String>()) }
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
    val stopSelecting = { selecting = false; selected = emptyList() }
    val addBatch = { apps: List<SourceApp> ->
        if (apps.any { it.caution != null }) batchCaution = apps else model.addSeveral(apps)
    }
    val addApps = { apps: List<SourceApp> ->
        when {
            apps.isEmpty() -> Unit
            offerRestore -> restoreFirstFor = apps
            apps.size == 1 -> addApp(apps.single())
            else -> addBatch(apps)
        }
    }
    BackHandler(enabled = selecting) { stopSelecting() }
    // Hiding the keyboard leaves the search focused, so the next tap wouldn't scroll it up again
    val imeVisible = WindowInsets.isImeVisible
    val focusManager = LocalFocusManager.current
    LaunchedEffect(imeVisible) { if (!imeVisible) focusManager.clearFocus() }

    Scaffold(
        // Edge to edge: lift messages above the navigation bar unless the progress bar already does
        snackbarHost = { SnackbarHost(snackbar, if (busy || selecting) Modifier else Modifier.navigationBarsPadding()) },
        bottomBar = {
            Box {
                AnimatedVisibility(busy, enter = slideInVertically { it }, exit = slideOutVertically { it }) {
                    when {
                        job != null -> JobBar(job, waiting = (model.queued - 1).coerceAtLeast(0))
                        model.backgroundBuild != null -> BackgroundBar(model.backgroundBuild!!)
                    }
                }
                AnimatedVisibility(selecting && !busy, enter = slideInVertically { it }, exit = slideOutVertically { it }) {
                    SelectionBar(
                        count = selected.size,
                        onCancel = stopSelecting,
                        onAdd = {
                            // In list order, which is the order they're built in
                            val apps = catalog?.available.orEmpty().filter { it.info.packageName in selected }
                            stopSelecting()
                            addApps(apps)
                        },
                    )
                }
            }
        },
        contentWindowInsets = WindowInsets(0),
    ) { inner ->
        // In landscape the navigation bar and camera cutout sit at the sides, so keep rows clear of them
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))) {
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
                if (model.reconnectNeeded) {
                    item("reconnect") { ReconnectCallout(onDismiss = model::dismissReconnect) }
                }
                val copyBytes = catalog?.copies.orEmpty().sumOf { it.sizeBytes }
                if (copyBytes > 0) {
                    item("car-storage") {
                        val count = catalog!!.copies.size
                        Text(
                            pluralStringResource(R.plurals.copies_storage, count, count, Formatter.formatShortFileSize(LocalContext.current, copyBytes)),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = Gutter, end = Gutter, bottom = 8.dp),
                        )
                    }
                }
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
                    Row(
                        Modifier.fillMaxWidth().padding(start = Gutter - 12.dp, end = Gutter - 12.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Secondary to the list below: most people add apps that are already installed
                        TextButton(onClick = { actions.pickApks(null) }, enabled = !busy) {
                            Icon(FileGlyph, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.add_from_file))
                        }
                        Spacer(Modifier.weight(1f))
                        if (!selecting && (catalog?.available?.size ?: 0) > 1) {
                            TextButton(onClick = { selecting = true }, enabled = !busy) {
                                Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.select_several))
                            }
                        }
                    }
                }
                if (catalog != null) {
                    if (catalog.available.size > 6) {
                        item("search") {
                            val topInset = WindowInsets.statusBars.getTop(LocalDensity.current)
                            SearchField(query, onChange = { query = it }) { focused ->
                                searchFocused = focused
                                if (focused) scope.launch {
                                    // Up to just below the status bar, so the results get the screen
                                    val target = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == "search" } ?: return@launch
                                    // Above the keyboard's own bring-into-view scroll, which would cancel this one
                                    listState.scroll(MutatePriority.UserInput) {
                                        var done = 0f
                                        animate(0f, (target.offset - topInset).toFloat()) { value, _ -> done += scrollBy(value - done) }
                                    }
                                }
                            }
                        }
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
                        val pkg = app.info.packageName
                        SourceRow(
                            app,
                            enabled = !busy,
                            endPadding = if (indexed) Gutter + IndexWidth else Gutter,
                            selecting = selecting,
                            selected = pkg in selected,
                            onAdd = { addApps(listOf(app)) },
                            onToggle = { selected = if (pkg in selected) selected - pkg else selected + pkg },
                            onStartSelecting = { selecting = true; selected = listOf(pkg) },
                        )
                    }
                }

                if (searchFocused || search.isNotEmpty()) {
                    // A short result list can't scroll that far, so room below keeps the search at the top
                    item("search-room") {
                        Spacer(Modifier.height(with(LocalDensity.current) { listState.layoutInfo.viewportSize.height.toDp() }))
                    }
                } else {
                    item("footer") { Footer(onAbout = { showAbout = true }) }
                }
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

            // Far enough down that getting back to the car copies takes more than a flick
            val farDown by remember { derivedStateOf { listState.firstVisibleItemIndex > 8 } }
            AnimatedVisibility(
                farDown && !searchFocused,
                // Centred, as the right side is the + buttons' and the letter strip's
                Modifier.align(Alignment.BottomCenter).padding(
                    bottom = inner.calculateBottomPadding() + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 16.dp,
                ),
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut(),
            ) {
                Surface(
                    onClick = { scope.launch { listState.animateScrollToItem(0) } },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shadowElevation = 3.dp,
                ) {
                    Row(Modifier.padding(start = 12.dp, end = 16.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.back_to_top), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }

            // Green behind the header, then the page color, so no green strip floats over the list
            val headerGone by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }
            val scrim by animateColorAsState(
                if (headerGone) MaterialTheme.colorScheme.background else LocalSigns.current.guide,
                label = "statusScrim",
            )
            StatusBarIcons(dark = headerGone && !isSystemInDarkTheme())
            Box(Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars).background(scrim))
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
    batchCaution?.let { apps ->
        BatchCautionDialog(
            risky = apps.filter { it.caution != null },
            onAddAll = { batchCaution = null; model.addSeveral(apps) },
            onLeaveOut = { batchCaution = null; model.addSeveral(apps.filter { it.caution == null }) },
            onDismiss = { batchCaution = null },
        )
    }
    restoreFirstFor?.let { apps ->
        AlertDialog(
            onDismissRequest = { restoreFirstFor = null },
            title = { Text(stringResource(R.string.restore_first_title)) },
            text = { Text(stringResource(R.string.restore_first_body)) },
            confirmButton = {
                TextButton(onClick = { restoreFirstFor = null; actions.pickBackup() }) { Text(stringResource(R.string.restore_backup)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    restoreFirstFor = null
                    if (apps.size == 1) addApp(apps.single()) else addBatch(apps)
                }) { Text(stringResource(R.string.add_without_key)) }
            },
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

/** Dark status bar icons over a light page; light ones over the green header or a dark page. */
@Composable
private fun StatusBarIcons(dark: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return
    LaunchedEffect(dark) {
        val window = (view.context as? Activity)?.window ?: return@LaunchedEffect
        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = dark
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
private fun ReconnectCallout(onDismiss: () -> Unit) {
    Surface(
        Modifier.fillMaxWidth().padding(start = Gutter, end = Gutter, bottom = 12.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Column(Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 4.dp)) {
            Text(stringResource(R.string.reconnect_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.reconnect_body), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text(stringResource(R.string.got_it)) }
        }
    }
}
