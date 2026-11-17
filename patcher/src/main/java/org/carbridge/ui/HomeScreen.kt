package org.carbridge.ui

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
import org.carbridge.Job
import org.carbridge.JobStep
import org.carbridge.RestoreRequest
import org.carbridge.Setup
import org.carbridge.SourceApp
import org.carbridge.UserMessage
import org.carbridge.Version
import org.carbridge.core.CarOptions
import org.carbridge.patch.KeyBackup

class HomeActions(
    val allowInstalls: () -> Unit,
    val openAndroidAuto: () -> Unit,
    val markDeveloperMode: () -> Unit,
    val markUnknownSources: () -> Unit,
    val add: (SourceApp) -> Unit,
    val update: (CarCopy) -> Unit,
    val updateAll: (List<CarCopy>) -> Unit,
    val openMorphe: () -> Unit,
    val setFillScreen: (CarCopy, Boolean) -> Unit,
    val setKeepMedia: (CarCopy, Boolean) -> Unit,
    val remove: (CarCopy) -> Unit,
    val open: (CarCopy) -> Unit,
    /** Opens Android's file picker to save the key backup under the given name. */
    val saveBackup: (String) -> Unit,
    val pickBackup: () -> Unit,
)

private val SignShape = RoundedCornerShape(20.dp)
private val SignBorderShape = RoundedCornerShape(15.dp)
private val Gutter = 16.dp

/** Below this many apps the list is short enough to scroll by hand. */
private const val INDEX_MIN_APPS = 15
private val APP_LIST_KEYS = setOf("add-title", "search", "auto-toggle", "add-empty", "footer")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(model: HomeModel, actions: HomeActions) {
    val snackbar = remember { SnackbarHostState() }
    var details by remember { mutableStateOf<UserMessage?>(null) }
    var showAbout by rememberSaveable { mutableStateOf(false) }
    var showSetup by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var cautionFor by remember { mutableStateOf<SourceApp?>(null) }
    var restoreFirstFor by remember { mutableStateOf<SourceApp?>(null) }
    var backupLater by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(model) {
        model.messages.collect { message ->
            val result = snackbar.showSnackbar(
                message.text,
                actionLabel = if (message.details != null) "Details" else null,
                withDismissAction = message.details == null,
                duration = SnackbarDuration.Long,
            )
            if (result == SnackbarResult.ActionPerformed) details = message
        }
    }

    val catalog = model.catalog
    val job = model.job
    val setup = model.setup
    val busy = job != null
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
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            AnimatedVisibility(job != null, enter = slideInVertically { it }, exit = slideOutVertically { it }) {
                job?.let { JobBar(it, waiting = (model.queued - 1).coerceAtLeast(0)) }
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
                item("header") { SignHeader(onAbout = { showAbout = true }) }

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
                            title = if (lost == 1) "1 copy needs your key backup" else "$lost copies need your key backup",
                            body = "CarBridge was reinstalled or its data cleared, so it can't update copies made before. " +
                                "If you saved a key backup, restore it.",
                            action = "Restore backup",
                            onAction = actions.pickBackup,
                            other = "I don't have one",
                            onOther = model::declineRestore,
                        )
                    }
                } else if (catalog != null && catalog.copies.any { !it.signedElsewhere } && !model.backedUp && !backupLater) {
                    item("backup") {
                        KeyCard(
                            title = "Back up your CarBridge key",
                            body = "If CarBridge is reinstalled or its data cleared, your copies can only update again " +
                                "with this backup.",
                            action = "Back up",
                            onAction = model::startBackup,
                            other = "Later",
                            onOther = { backupLater = true },
                        )
                    }
                }

                item("car-title") { SectionTitle("On your car") }
                when {
                    catalog == null -> item("loading") { Loading() }
                    catalog.copies.isEmpty() -> item("car-empty") {
                        Hint("Nothing yet. Add an app below, then reconnect your phone to Android Auto.")
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
                        )
                    }
                    }
                }

                item("add-title") { SectionTitle("Add an app") }
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
                            Hint(if (search.isEmpty()) "Every app you can add is already on your car." else "No apps match “$search”.")
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
            title = { Text("Restore your key first?") },
            text = {
                Text(
                    "Restore your key backup before adding apps, so new copies use the same key as the ones " +
                        "already on your car and one backup covers them all.",
                )
            },
            confirmButton = { TextButton(onClick = { restoreFirstFor = null; actions.pickBackup() }) { Text("Restore backup") } },
            dismissButton = { TextButton(onClick = { restoreFirstFor = null; addApp(app) }) { Text("Add without it") } },
        )
    }
    model.newBackup?.let { backup ->
        BackupCodeDialog(backup.code, onSave = { actions.saveBackup(model.backupFileName) }, onDismiss = model::cancelBackup)
    }
    model.restoreRequest?.let { request ->
        RestoreDialog(request, onRestore = model::restore, onDismiss = model::cancelRestore)
    }
    if (showAbout) {
        ModalBottomSheet(onDismissRequest = { showAbout = false }) {
            About(
                onBackUp = { showAbout = false; model.startBackup() },
                onRestore = { showAbout = false; actions.pickBackup() },
            )
        }
    }
    details?.let { message ->
        AlertDialog(
            onDismissRequest = { details = null },
            confirmButton = { TextButton(onClick = { details = null }) { Text("Close") } },
            title = { Text(message.text) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        "This app may not support being copied. These details help when reporting a problem:",
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
private fun SignHeader(onAbout: () -> Unit) {
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
                "CarBridge",
                style = MaterialTheme.typography.displaySmall,
                color = signs.onGuide,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            IconButton(onClick = onAbout) {
                Icon(Icons.Rounded.Info, contentDescription = "About CarBridge", tint = signs.onGuide)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "Put the apps on your phone on your car's screen.",
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
            "Let CarBridge install apps",
            "Android asks once. CarBridge only installs the car copies you choose.",
            setup.canInstall,
        ) { Button(onClick = actions.allowInstalls, colors = cautionButton()) { Text("Allow") } },
        SetupStep(
            "Turn on developer mode in Android Auto",
            "Open Android Auto settings and tap Version 10 times until it says developer mode is on.",
            setup.developerMode,
        ) {
            Button(onClick = actions.openAndroidAuto, colors = cautionButton()) { Text("Open Android Auto") }
            TextButton(onClick = actions.markDeveloperMode, colors = ButtonDefaults.textButtonColors(contentColor = signs.onCaution)) {
                Text("It's on")
            }
        },
        SetupStep(
            "Allow unknown sources",
            "In Android Auto settings, open the menu at the top right, choose Developer settings and turn on Unknown sources.",
            setup.unknownSources,
        ) {
            Button(onClick = actions.openAndroidAuto, colors = cautionButton()) { Text("Open Android Auto") }
            TextButton(onClick = actions.markUnknownSources, colors = ButtonDefaults.textButtonColors(contentColor = signs.onCaution)) {
                Text("It's on")
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
                    if (current == -1) "You're set up" else "Set up once",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                if (onHide != null) {
                    IconButton(onClick = onHide) { Icon(Icons.Rounded.Close, contentDescription = "Hide setup") }
                }
            }
            Text(
                "Android Auto only lists apps from outside the Play Store after these three steps.",
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
                Icon(Icons.Rounded.Check, contentDescription = "Done", tint = signs.caution, modifier = Modifier.size(20.dp))
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
        Text("Android Auto is set up", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        TextButton(onClick = onShow) { Text("Setup steps") }
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
) {
    val signs = LocalSigns.current
    var menu by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 4.dp)) {
        if (copy.outdated && copy.updatable) UpdateTab()
        GuidePanel(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Opening on the phone is how users sign in to a copy, which starts with no data
                Row(
                    Modifier.weight(1f).clickable(enabled = !busy, onClickLabel = "Open ${copy.label}", onClick = onOpen),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppIcon(copy.icon, 40)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(copy.label, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            when {
                                copy.original == null -> "Original removed"
                                copy.madeByOtherCarBridge -> "Made by another CarBridge app"
                                copy.signedElsewhere -> "To update, restore your key or add it again"
                                copy.outdated -> "${versionText(copy.version!!, copy.originalVersion!!)} → " +
                                    versionText(copy.originalVersion, copy.version)
                                else -> copy.version?.name ?: "In Android Auto"
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
                    ) { Text("Update") }
                }
                Box {
                    IconButton(onClick = { menu = true }, enabled = !busy) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = "More for ${copy.label}", tint = signs.onGuide)
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("Open on phone") },
                            leadingIcon = { Icon(Icons.Rounded.Phone, contentDescription = null) },
                            onClick = { menu = false; onOpen() },
                        )
                        if (copy.updatable) {
                            DropdownMenuItem(
                                text = { Text("Update copy") },
                                leadingIcon = { Icon(Icons.Rounded.Refresh, contentDescription = null) },
                                onClick = { menu = false; onUpdate() },
                            )
                        }
                        if (copy.updatable && copy.portraitLocked) {
                            DropdownMenuItem(
                                text = { Text(if (copy.options.fillScreen) "Keep portrait layout" else "Fill the car screen") },
                                leadingIcon = { Icon(LandscapeGlyph, contentDescription = null) },
                                onClick = { menu = false; onFillScreen(!copy.options.fillScreen) },
                            )
                        }
                        if (copy.updatable && copy.hasMediaService) {
                            val keep = copy.options.keepMediaServices
                            DropdownMenuItem(
                                // Kept media services let Android Auto show its audio player for the copy
                                text = { Text(if (keep) "Always show the full app" else "Use Android Auto's audio player") },
                                leadingIcon = { Icon(Icons.Rounded.PlayArrow, contentDescription = null) },
                                onClick = { menu = false; onKeepMedia(!keep) },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Remove from car") },
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
            "UPDATE READY",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
        )
    }
}

/** Version name, or the build number when both sides share a name (e.g. a same-name rebuild). */
private fun versionText(version: Version, other: Version): String =
    version.name?.takeIf { it != other.name } ?: "build ${version.code}"

@Composable
private fun AppIcon(icon: ImageBitmap, sizeDp: Int) {
    Image(icon, contentDescription = null, modifier = Modifier.size(sizeDp.dp))
}

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit) {
    TextField(
        value = query,
        onValueChange = onChange,
        placeholder = { Text("Search your apps") },
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
        trailingIcon = if (query.isNotEmpty()) {
            { IconButton(onClick = { onChange("") }) { Icon(Icons.Rounded.Close, contentDescription = "Clear search") } }
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
    val apps = if (count == 1) "1 app" else "$count apps"
    Row(
        Modifier.fillMaxWidth().padding(start = Gutter, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (showing) "Showing $apps that already work in Android Auto." else "$apps already work in Android Auto.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onToggle) { Text(if (showing) "Hide them" else "Show them") }
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
                        Caution.LicenseCheck -> "Copy protected"
                        Caution.Morphe -> "Patched with Morphe"
                        null -> null
                    },
                    "Has Android Auto".takeIf { app.hasAndroidAuto },
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
            Text("Add")
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
            "$count copies are out of date",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        FilledTonalButton(onClick = onClick, enabled = enabled) { Text("Update all") }
    }
}

@Composable
private fun AddChoiceDialog(choice: AddChoice, onConfirm: (CarOptions) -> Unit, onDismiss: () -> Unit) {
    var fill by rememberSaveable { mutableStateOf(true) }
    var player by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add ${choice.app.label}") },
        text = {
            Column {
                Text(
                    "You can change these later from the copy's ⋮ menu.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (choice.portraitLocked) {
                    ChoiceRow(
                        "Fill the car screen",
                        "Some screens only work upright. Turn off to keep them narrow but unchanged.",
                        fill,
                    ) { fill = it }
                }
                if (choice.hasMediaService) {
                    ChoiceRow(
                        "Use Android Auto's audio player",
                        "Android Auto shows its own player for this app instead of the full app.",
                        player,
                    ) { player = it }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(CarOptions(keepMediaServices = player, fillScreen = fill)) }) {
                Text("Add to car")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
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
            title = { Text("Add ${app.label} in Morphe instead") },
            text = {
                Text(
                    "${app.label} was patched with Morphe. Add the CarBridge Patches source in Morphe and " +
                        "repatch there: you keep one app with your settings, and it avoids a branding error.",
                )
            },
            confirmButton = { TextButton(onClick = onOpenMorphe) { Text("Open in Morphe") } },
            dismissButton = { TextButton(onClick = onAddAnyway) { Text("Make a copy anyway") } },
        )
        Caution.LicenseCheck -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("${app.label} probably won't open") },
            text = {
                Text(
                    "${app.label} uses Google Play's copy protection, which usually closes copies as soon " +
                        "as they start. You can still try.",
                )
            },
            confirmButton = { TextButton(onClick = onAddAnyway) { Text("Add anyway") } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        )
        null -> Unit
    }
}

@Composable
private fun Footer(onAbout: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(start = Gutter, end = Gutter, top = 32.dp)) {
        Text(
            "Car copies are for passengers, or for when you're parked.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(onClick = onAbout, modifier = Modifier.padding(top = 12.dp)) { Text("How CarBridge works") }
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
                        job.headline(waiting),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        job.stepText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            LaneProgress(job.step, JobStep.entries.size, Modifier.padding(top = 14.dp))
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
private fun About(onBackUp: () -> Unit, onRestore: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text("How CarBridge works", style = MaterialTheme.typography.headlineSmall)
        AboutItem(
            "A separate copy",
            "CarBridge makes a copy of an app that Android Auto will show on the car screen. " +
                "Your original app and its data stay as they are; the copy has its own data and sign-in.",
        )
        AboutItem(
            "Some apps won't open",
            "Apps that check their own signature, a license or Play Integrity may refuse to run as a copy.",
        )
        Column {
            AboutItem(
                "Back up your key",
                "Copies are signed with a key stored on this phone. If you uninstall CarBridge or clear its data, " +
                    "your copies can only update again with a key backup. Keep the file and its code off this phone.",
            )
            FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = onBackUp) { Text("Back up key") }
                OutlinedButton(onClick = onRestore) { Text("Restore backup") }
            }
        }
        AboutItem(
            "Use it safely and fairly",
            "Use car copies only while parked or for passengers. Only copy apps whose license allows changes, " +
                "and don't share the copies.",
        )
        AboutItem(
            "Free software",
            "GPL-3.0. No accounts, ads or tracking. Everything happens on this phone.",
        )
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
        title = { Text("Save your recovery code") },
        text = {
            Column {
                Text(
                    "You need this code and the backup file to restore your key. CarBridge shows it only once.",
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
                TextButton(onClick = { copySensitive(context, code) }) { Text("Copy code") }
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp)
                        .toggleable(value = saved, role = Role.Checkbox, onValueChange = { saved = it }),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = saved, onCheckedChange = null)
                    Spacer(Modifier.width(8.dp))
                    Text("I've written down or saved the code", style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = { TextButton(onClick = onSave, enabled = saved) { Text("Save backup file") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Marked sensitive, so Android hides the code in its clipboard preview. */
private fun copySensitive(context: Context, text: String) {
    val clip = ClipData.newPlainText("CarBridge recovery code", text).apply {
        description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
    }
    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
}

@Composable
private fun RestoreDialog(request: RestoreRequest, onRestore: (String) -> Unit, onDismiss: () -> Unit) {
    var code by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Restore your key") },
        text = {
            Column {
                Text("Enter the recovery code you saved with this backup.", style = MaterialTheme.typography.bodyMedium)
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
                TextButton(onClick = { onRestore(code) }, enabled = KeyBackup.isWellFormed(code)) { Text("Restore") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !request.working) { Text("Cancel") } },
    )
}
