package org.carbridge

import android.app.Application
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.carbridge.core.CarOptions
import org.carbridge.patch.ApkImport
import org.carbridge.patch.BackupFailure
import org.carbridge.patch.ImportFailure
import org.carbridge.patch.KeyBackup
import org.carbridge.patch.KeystoreSigner
import org.carbridge.patch.PatchPipeline

private const val TAG = "CarBridge"

/** A car copy being made; [step] indexes [JobStep]. */
data class Job(val label: String, val icon: ImageBitmap, val packageName: String, val update: Boolean, val step: Int) {
    /** [waiting] is how many more copies an Update all run will build after this one. */
    fun headline(waiting: Int) = when {
        waiting > 0 -> "Updating $label, $waiting more after"
        update -> "Updating $label"
        else -> "Adding $label to your car"
    }

    // Updates of copies CarBridge installed often skip the prompt
    val stepText get() = if (update && step == JobStep.Confirm.ordinal) "Installing the update" else JobStep.entries[step].text
}

enum class JobStep(val text: String) {
    Read("Reading the app"),
    Patch("Making the car copy"),
    Sign("Signing it on this phone"),
    Confirm("Confirm the install in Android's prompt"),
}

data class Setup(val canInstall: Boolean, val developerMode: Boolean, val unknownSources: Boolean) {
    val complete get() = canInstall && developerMode && unknownSources
}

/** An add waiting for the user's layout and audio choices. */
data class AddChoice(val app: SourceApp, val portraitLocked: Boolean, val hasMediaService: Boolean)

data class UserMessage(val text: String, val details: String? = null)

/** A key backup made but not yet saved; [code] is shown once and never stored. */
class NewBackup(val file: ByteArray, val code: String)

/** A backup file the user picked, waiting for its recovery code. */
class RestoreRequest(val file: ByteArray, val error: String? = null, val working: Boolean = false)

class HomeModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("setup", 0)

    var catalog by mutableStateOf<Catalog?>(null)
        private set
    var job by mutableStateOf<Job?>(null)
        private set
    var setup by mutableStateOf(readSetup())
        private set
    var showAutoApps by mutableStateOf(prefs.getBoolean("show_auto_apps", false))
        private set
    var autoUpdate by mutableStateOf(AutoUpdate.isEnabled(app))
        private set

    /** The label of a copy the background job is rebuilding, or null. */
    var backgroundBuild by mutableStateOf<String?>(null)
        private set

    private val _messages = Channel<UserMessage>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()
    private val log = mutableListOf<String>()
    private val updateQueue = ArrayDeque<SourceApp>()

    /** Copies still waiting in an Update all run, including the one being built. */
    var queued by mutableStateOf(0)
        private set

    /** Fires once, after the first copy installs, so the permission ask has an obvious reason. */
    private val _notificationPrompt = Channel<Unit>(Channel.CONFLATED)
    val notificationPrompt = _notificationPrompt.receiveAsFlow()

    init {
        viewModelScope.launch {
            CopyBuilder.building.collect { pkg ->
                // The screen's own builds already show their progress
                val background = pkg?.takeIf { job?.packageName != it }
                val finished = backgroundBuild != null && background == null
                backgroundBuild = background?.let { withContext(Dispatchers.IO) { AutoUpdate.labelOf(app, it) } }
                if (finished) refresh()
            }
        }
        viewModelScope.launch {
            // queued stays above 0 between copies of an Update all run, so the service keeps running
            snapshotFlow { job to queued }.collect { (current, waiting) ->
                when {
                    current != null -> PatchService.show(app, current.headline(maxOf(waiting - 1, 0)), current.stepText)
                    waiting == 0 -> PatchService.stop(app)
                }
            }
        }
        viewModelScope.launch {
            Installer.events.collect { result ->
                val current = job
                if (current != null && (result.packageName ?: current.packageName) == current.packageName) {
                    job = null
                    if (result.success && !prefs.getBoolean("asked_notifications", false)) {
                        prefs.edit().putBoolean("asked_notifications", true).apply()
                        _notificationPrompt.send(Unit)
                    }
                    _messages.send(
                        when {
                            result.success -> UserMessage(
                                "${current.label} is on your car. Reconnect your phone to Android Auto to see it.",
                            )
                            result.cancelled -> UserMessage("Install cancelled.")
                            else -> UserMessage("Couldn't add ${current.label}.", details(result.message))
                        },
                    )
                }
                refresh()
                if (job == null) nextQueued(continueRun = result.success)
            }
        }
    }

    /** Rebuilds every outdated copy in turn; Android may ask to confirm each install. */
    fun updateAll(copies: List<CarCopy>) {
        if (job != null) return
        updateQueue.clear()
        updateQueue.addAll(copies.filter { it.outdated }.mapNotNull { it.original })
        nextQueued(continueRun = true)
    }

    private fun nextQueued(continueRun: Boolean) {
        // A cancelled or failed install stops the run, so the user isn't trapped in prompts
        if (!continueRun) updateQueue.clear()
        val next = updateQueue.removeFirstOrNull()
        queued = if (next == null) 0 else updateQueue.size + 1
        next?.let { add(it, update = true) }
    }

    var pendingAdd by mutableStateOf<AddChoice?>(null)
        private set

    /** Asks for choices first when they matter, so the copy isn't patched twice. */
    fun requestAdd(app: SourceApp) {
        if (job != null) return
        viewModelScope.launch {
            val (portrait, media) = withContext(Dispatchers.IO) {
                AppCatalog.traits(getApplication(), app.info.packageName)
            }
            if (portrait || media) pendingAdd = AddChoice(app, portrait, media) else add(app, update = false)
        }
    }

    fun confirmAdd(choice: AddChoice, options: CarOptions) {
        val pkg = choice.app.info.packageName
        prefs.edit()
            .putBoolean("keep_portrait:$pkg", !options.fillScreen)
            .putBoolean("keep_media:$pkg", options.keepMediaServices)
            .apply()
        pendingAdd = null
        add(choice.app, update = false)
    }

    fun cancelAdd() {
        pendingAdd = null
    }

    /** Set from the update notification; null package means every outdated copy. */
    private var pendingUpdate: String? = null
    private var pendingUpdateAll = false

    fun updateFromNotification(originalPackage: String?) {
        if (originalPackage == null) pendingUpdateAll = true else pendingUpdate = originalPackage
        refresh()
    }

    private fun runPendingUpdate(catalog: Catalog) {
        if (job != null) return
        val all = pendingUpdateAll
        val pkg = pendingUpdate
        pendingUpdateAll = false
        pendingUpdate = null
        when {
            all -> updateAll(catalog.copies)
            pkg != null -> catalog.copies.firstOrNull { it.original?.info?.packageName == pkg }?.original
                ?.let { add(it, update = true) }
        }
    }

    /** Fixed sample data for store screenshots; never set in normal use. */
    private var sample: Catalog? = null

    fun showSample(catalog: Catalog) {
        sample = catalog
        refresh()
    }

    fun refresh() {
        sample?.let { setup = Setup(canInstall = true, developerMode = true, unknownSources = true); catalog = it; return }
        setup = readSetup()
        viewModelScope.launch {
            val (loaded, backed) = withContext(Dispatchers.IO) {
                AppCatalog.load(getApplication(), ::carOptions) to KeystoreSigner(getApplication()).hasBackup()
            }
            catalog = loaded
            backedUp = backed
            runPendingUpdate(loaded)
        }
    }

    /** Starts true so the backup card doesn't flash before the first load. */
    var backedUp by mutableStateOf(true)
        private set
    var restoreDeclined by mutableStateOf(prefs.getBoolean("restore_declined", false))
        private set
    var newBackup by mutableStateOf<NewBackup?>(null)
        private set
    var restoreRequest by mutableStateOf<RestoreRequest?>(null)
        private set

    val backupFileName get() = "carbridge-key-${LocalDate.now()}.${KeyBackup.FILE_EXTENSION}"

    fun startBackup() {
        viewModelScope.launch {
            withContext(Dispatchers.Default) { runCatching { KeystoreSigner(getApplication()).createBackup() } }
                .onSuccess { (file, code) -> newBackup = NewBackup(file, code) }
                .onFailure { e ->
                    Log.e(TAG, "backup failed", e)
                    _messages.send(UserMessage("Couldn't make a key backup.", "${e.javaClass.simpleName}: ${e.message}"))
                }
        }
    }

    fun cancelBackup() {
        newBackup = null
    }

    /** Writes the backup to the file the user picked; nothing is marked saved if that fails. */
    fun saveBackup(uri: Uri) {
        val backup = newBackup ?: return
        newBackup = null
        val context = getApplication<Application>()
        viewModelScope.launch {
            val saved = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(backup.file) }
                    KeystoreSigner(context).markBackedUp()
                }
            }
            backedUp = saved.isSuccess
            _messages.send(
                if (saved.isSuccess) UserMessage("Key backed up. Keep the file and the code somewhere other than this phone.")
                else UserMessage("Couldn't save the backup.", saved.exceptionOrNull()?.message),
            )
        }
    }

    fun openRestore(uri: Uri) {
        val context = getApplication<Application>()
        viewModelScope.launch {
            val file = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openInputStream(uri)!!.use { it.readBytes() } }
            }
            file.onSuccess { restoreRequest = RestoreRequest(it) }
                .onFailure { _messages.send(UserMessage("Couldn't open that file.", it.message)) }
        }
    }

    fun restore(code: String) {
        val request = restoreRequest ?: return
        restoreRequest = RestoreRequest(request.file, working = true)
        val context = getApplication<Application>()
        viewModelScope.launch {
            val lostBefore = catalog?.copies?.count { it.needsRestore } ?: 0
            val result = withContext(Dispatchers.Default) {
                runCatching { KeystoreSigner(context).restore(request.file, code) }
            }
            result.exceptionOrNull()?.let { e ->
                Log.w(TAG, "restore failed", e)
                restoreRequest = RestoreRequest(request.file, error = (e as? BackupFailure)?.message ?: "This backup couldn't be restored.")
                return@launch
            }
            restoreRequest = null
            val loaded = withContext(Dispatchers.IO) { AppCatalog.load(context, ::carOptions) }
            catalog = loaded
            backedUp = true
            val fixed = lostBefore - loaded.copies.count { it.needsRestore }
            _messages.send(
                UserMessage(
                    when (fixed) {
                        0 -> "Key restored. It doesn't match any copy on this phone, but it will sign new ones."
                        1 -> "Key restored. 1 copy can update again."
                        else -> "Key restored. $fixed copies can update again."
                    },
                ),
            )
        }
    }

    fun cancelRestore() {
        restoreRequest = null
    }

    /** The user has no backup, so stop offering a restore for copies signed with a lost key. */
    fun declineRestore() {
        prefs.edit().putBoolean("restore_declined", true).apply()
        restoreDeclined = true
    }

    fun switchAutoUpdate(on: Boolean) {
        AutoUpdate.setEnabled(getApplication(), on)
        autoUpdate = on
        // Hands copies that are already outdated to the job right away
        if (on) viewModelScope.launch(Dispatchers.IO) { UpdateCheck.run(getApplication()) }
    }

    fun setAutoUpdateAllowed(copy: CarCopy, allowed: Boolean) {
        val original = copy.original ?: return
        AutoUpdate.setAllowed(getApplication(), original.info.packageName, allowed)
        refresh()
    }

    fun toggleAutoApps() {
        showAutoApps = !showAutoApps
        prefs.edit().putBoolean("show_auto_apps", showAutoApps).apply()
    }

    private fun carOptions(originalPackage: String) = CopyBuilder.options(getApplication(), originalPackage)

    /** Saves the layout choice and rebuilds the copy with it. */
    fun setFillScreen(copy: CarCopy, fill: Boolean) {
        val original = copy.original ?: return
        prefs.edit().putBoolean("keep_portrait:${original.info.packageName}", !fill).apply()
        add(original, update = true)
    }

    /** Saves the audio-player choice and rebuilds the copy with it. */
    fun setKeepMedia(copy: CarCopy, keep: Boolean) {
        val original = copy.original ?: return
        prefs.edit().putBoolean("keep_media:${original.info.packageName}", keep).apply()
        add(original, update = true)
    }

    fun markDeveloperMode() = mark("developer_mode")
    fun markUnknownSources() = mark("unknown_sources")

    private fun mark(key: String) {
        prefs.edit().putBoolean(key, true).apply()
        setup = readSetup()
    }

    private fun readSetup() = Setup(
        canInstall = getApplication<Application>().packageManager.canRequestPackageInstalls(),
        developerMode = prefs.getBoolean("developer_mode", false),
        unknownSources = prefs.getBoolean("unknown_sources", false),
    )

    fun add(source: SourceApp, update: Boolean) {
        build(source.label, source.icon, source.info, update)
    }

    /** The package a file must belong to: set by Update from a file, cleared by Add from a file. */
    private var expectedFilePackage: String? = null

    fun expectFilesFor(copy: CarCopy?) {
        expectedFilePackage = copy?.packageName?.let { PatchPipeline.originalPackageName(it) }
    }

    /** Makes a copy from APK files the user picked, for apps that aren't installed on the phone. */
    fun addFromFiles(uris: List<Uri>) {
        if (uris.isEmpty() || job != null || backgroundBuild != null) return
        val context = getApplication<Application>()
        val expected = expectedFilePackage
        viewModelScope.launch {
            val read = withContext(Dispatchers.IO) {
                runCatching {
                    val dir = ApkImport.workDir(context.cacheDir).apply { deleteRecursively(); mkdirs() }
                    val files = uris.mapIndexed { i, uri ->
                        File(dir, "picked-$i").also { file ->
                            context.contentResolver.openInputStream(uri)!!.use { input -> file.outputStream().use(input::copyTo) }
                        }
                    }
                    val import = ApkImport.read(files, dir)
                    if (expected != null && import.packageName != expected) {
                        throw ImportFailure("That file is a different app (${import.packageName}). Pick the one this copy was made from.")
                    }
                    val pm = context.packageManager
                    val info = import.applicationInfo(pm)
                    Triple(info, pm.getApplicationLabel(info).toString(), AppCatalog.icon(pm.getApplicationIcon(info)))
                }
            }
            read.onFailure { e ->
                Log.w(TAG, "import failed", e)
                withContext(Dispatchers.IO) { ApkImport.workDir(context.cacheDir).deleteRecursively() }
                _messages.send(UserMessage("Couldn't use that file.", details((e as? ImportFailure)?.message ?: CopyBuilder.describe(e))))
            }
            val (info, label, icon) = read.getOrNull() ?: return@launch
            val installed = runCatching { context.packageManager.getPackageInfo(info.packageName, 0) }.isSuccess
            if (!installed) prefs.edit().putBoolean("from_file:${info.packageName}", true).apply()
            val carPackage = PatchPipeline.carPackageName(info.packageName)
            val update = runCatching { context.packageManager.getPackageInfo(carPackage, 0) }.isSuccess
            build(label, icon, info, update) { ApkImport.workDir(context.cacheDir).deleteRecursively() }
        }
    }

    private fun build(label: String, icon: ImageBitmap, info: ApplicationInfo, update: Boolean, cleanUp: () -> Unit = {}) {
        if (job != null || backgroundBuild != null) return
        val context = getApplication<Application>()
        val carPackage = PatchPipeline.carPackageName(info.packageName)
        log.clear()
        job = Job(label, icon, carPackage, update, JobStep.Read.ordinal)
        viewModelScope.launch {
            val started = try {
                withContext(Dispatchers.IO) {
                    CopyBuilder.build(context, info, ::onLog) {
                        viewModelScope.launch { job = job?.copy(step = JobStep.Confirm.ordinal) }
                    }
                }
            } catch (e: Throwable) {
                // Throwable: apksig/ARSCLib can throw Errors (e.g. missing JDK classes on Android)
                Log.e(TAG, "patch failed", e)
                job = null
                nextQueued(continueRun = false)
                _messages.send(UserMessage("Couldn't add $label.", details(CopyBuilder.describe(e))))
                return@launch
            } finally {
                withContext(Dispatchers.IO) { cleanUp() }
            }
            if (!started) {
                job = null
                nextQueued(continueRun = false)
                _messages.send(UserMessage("CarBridge is updating another copy in the background. Try again in a minute."))
            }
        }
    }

    private fun onLog(line: String) {
        Log.i(TAG, line)
        synchronized(log) { log.add(line) }
        val step = when {
            line.startsWith("Signing") -> JobStep.Sign
            line.startsWith("Car-enabled") || line.startsWith("Renamed") -> JobStep.Patch
            else -> null
        } ?: return
        viewModelScope.launch { job = job?.copy(step = maxOf(job?.step ?: 0, step.ordinal)) }
    }

    override fun onCleared() {
        // The patch runs in viewModelScope, which is cancelled with the model
        PatchService.stop(getApplication())
    }

    private fun details(error: String?) = synchronized(log) { (log + listOfNotNull(error)).joinToString("\n") }
}
