package org.carbridge

import android.app.Application
import android.content.Context
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.storage.StorageManager
import android.util.Log
import androidx.annotation.StringRes
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
import org.carbridge.patch.BackedUpCopy
import rikka.shizuku.Shizuku
import org.carbridge.patch.BackupFailure
import org.carbridge.patch.ImportFailure
import org.carbridge.patch.KeyBackup
import org.carbridge.patch.KeystoreSigner
import org.carbridge.patch.PatchPipeline

private const val TAG = "CarBridge"

// Original packages a restored backup listed, until their copies are made or the offer dismissed
private const val RESTORE_PENDING = "restore_pending"

// Original packages the latest saved or checked backup lists
private const val BACKUP_COPIES = "backup_copies"

/** A car copy being made; [step] indexes [JobStep]. */
data class Job(
    val label: String,
    val icon: ImageBitmap,
    val packageName: String,
    val update: Boolean,
    val step: Int,
    /** Installing through Shizuku, so Android won't show a prompt. */
    val silent: Boolean = false,
) {
    /** [waiting] is how many more copies an Update all run will build after this one. */
    fun headline(context: Context, waiting: Int): String = when {
        waiting > 0 -> context.resources.getQuantityString(R.plurals.job_updating_more, waiting, label, waiting)
        update -> context.getString(R.string.job_updating, label)
        else -> context.getString(R.string.job_adding, label)
    }

    // Updates of copies CarBridge installed often skip the prompt
    fun stepText(context: Context): String = context.getString(
        when {
            step != JobStep.Confirm.ordinal -> JobStep.entries[step].text
            update -> R.string.step_installing_update
            silent -> R.string.step_installing
            else -> JobStep.Confirm.text
        },
    )
}

enum class JobStep(@StringRes val text: Int) {
    Read(R.string.step_read),
    Patch(R.string.step_patch),
    Sign(R.string.step_sign),
    Confirm(R.string.step_confirm),
}

data class Setup(val canInstall: Boolean, val developerMode: Boolean, val unknownSources: Boolean) {
    val complete get() = canInstall && developerMode && unknownSources
}

/** An add waiting for the user's layout and audio choices, which start from [defaults]. */
data class AddChoice(val app: SourceApp, val portraitLocked: Boolean, val hasMediaService: Boolean, val defaults: CarOptions)

data class UserMessage(val text: String, val details: String? = null)

/** A key backup made but not yet saved; [code] is shown once and never stored. */
class NewBackup(val file: ByteArray, val code: String, val copies: List<BackedUpCopy>)

/** A backup file the user picked, waiting for its recovery code; [checkOnly] tests it without restoring. */
class RestoreRequest(val file: ByteArray, val checkOnly: Boolean = false, val error: String? = null, val working: Boolean = false)

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
    var shizuku by mutableStateOf(ShizukuMode.state(app))
        private set
    var useShizuku by mutableStateOf(ShizukuMode.isEnabled(app))
        private set
    private val shizukuChanged = Shizuku.OnBinderReceivedListener { shizuku = ShizukuMode.state(app) }
    private val shizukuGone = Shizuku.OnBinderDeadListener { shizuku = ShizukuMode.state(app) }
    private val shizukuAnswered = Shizuku.OnRequestPermissionResultListener { _, _ ->
        shizuku = ShizukuMode.state(app)
        if (shizuku == ShizukuMode.State.Ready) switchShizuku(true)
    }

    fun switchShizuku(on: Boolean) {
        ShizukuMode.setEnabled(getApplication(), on)
        useShizuku = on
    }

    /** Asks Shizuku for access; the answer turns Shizuku installs on. */
    fun allowShizuku() = ShizukuMode.requestPermission()

    /** The label of a copy the background job is rebuilding, or null. */
    var backgroundBuild by mutableStateOf<String?>(null)
        private set

    private val _messages = Channel<UserMessage>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()
    private val log = mutableListOf<String>()
    /** Apps waiting in an Update all or Add them back run, and whether each is an update. */
    private val updateQueue = ArrayDeque<Pair<SourceApp, Boolean>>()

    /** Copies still waiting in a run, including the one being built. */
    var queued by mutableStateOf(0)
        private set

    /** Fires once, after the first copy installs, so the permission ask has an obvious reason. */
    private val _notificationPrompt = Channel<Unit>(Channel.CONFLATED)
    val notificationPrompt = _notificationPrompt.receiveAsFlow()

    init {
        Shizuku.addBinderReceivedListenerSticky(shizukuChanged)
        Shizuku.addBinderDeadListener(shizukuGone)
        Shizuku.addRequestPermissionResultListener(shizukuAnswered)
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
                    current != null -> PatchService.show(app, current.headline(app, maxOf(waiting - 1, 0)), current.stepText(app))
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
                            result.success -> UserMessage(text(R.string.msg_added, current.label))
                            result.cancelled -> UserMessage(text(R.string.msg_install_cancelled))
                            else -> UserMessage(text(R.string.msg_add_failed, current.label), details(result.message))
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
        updateQueue.addAll(copies.filter { it.outdated }.mapNotNull { it.original }.map { it to true })
        nextQueued(continueRun = true)
    }

    /** Makes the copies a restored backup listed, one after another, with their saved choices. */
    fun addRestorable() {
        if (job != null) return
        updateQueue.clear()
        updateQueue.addAll(restorable.map { it to false })
        nextQueued(continueRun = true)
    }

    /** Apps a restored backup listed that are installed here but have no copy yet. */
    var restorable by mutableStateOf<List<SourceApp>>(emptyList())
        private set

    fun dismissRestorable() {
        prefs.edit().remove(RESTORE_PENDING).apply()
        restorable = emptyList()
    }

    private fun nextQueued(continueRun: Boolean) {
        // A cancelled or failed install stops the run, so the user isn't trapped in prompts
        if (!continueRun) updateQueue.clear()
        val next = updateQueue.removeFirstOrNull()
        queued = if (next == null) 0 else updateQueue.size + 1
        next?.let { (app, update) -> add(app, update) }
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
            if (portrait || media) pendingAdd = AddChoice(app, portrait, media, defaults) else add(app, update = false)
        }
    }

    fun confirmAdd(choice: AddChoice, options: CarOptions) {
        CopyBuilder.save(getApplication(), choice.app.info.packageName, options)
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
        shizuku = ShizukuMode.state(getApplication())
        viewModelScope.launch {
            val (loaded, backed) = withContext(Dispatchers.IO) {
                AppCatalog.load(getApplication(), ::carOptions) to KeystoreSigner(getApplication()).hasBackup()
            }
            catalog = loaded
            backedUp = backed
            updateRestorable(loaded)
            runPendingUpdate(loaded)
        }
    }

    private fun updateRestorable(catalog: Catalog) {
        val pending = prefs.getStringSet(RESTORE_PENDING, null).orEmpty()
        // Made since, so a copy removed later isn't offered again
        val made = catalog.copies.map { PatchPipeline.originalPackageName(it.packageName) }.toSet()
        if (pending.any { it in made }) prefs.edit().putStringSet(RESTORE_PENDING, pending - made).apply()
        restorable = catalog.available.filter { it.info.packageName in pending && it.info.packageName !in made }
        val inBackup = prefs.getStringSet(BACKUP_COPIES, null)
        notInBackup = if (inBackup == null) ownCopies(catalog).size else ownCopies(catalog).count { it.originalPackage !in inBackup }
    }

    /** Copies this CarBridge made, as a backup lists them. */
    private fun ownCopies(catalog: Catalog): List<BackedUpCopy> = catalog.copies
        .filter { !it.madeByOtherCarBridge }
        .map { BackedUpCopy(PatchPipeline.originalPackageName(it.packageName), it.options, it.autoUpdateAllowed) }

    /** Copies a new phone couldn't add back from the latest backup, which predates them. */
    var notInBackup by mutableStateOf(0)
        private set

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
            val copies = catalog?.let(::ownCopies).orEmpty()
            withContext(Dispatchers.Default) { runCatching { KeystoreSigner(getApplication()).createBackup(copies) } }
                .onSuccess { (file, code) -> newBackup = NewBackup(file, code, copies) }
                .onFailure { e ->
                    Log.e(TAG, "backup failed", e)
                    _messages.send(UserMessage(text(R.string.msg_backup_failed), "${e.javaClass.simpleName}: ${e.message}"))
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
            if (saved.isSuccess) rememberBackedUpCopies(backup.copies)
            _messages.send(
                if (saved.isSuccess) UserMessage(text(R.string.msg_backed_up))
                else UserMessage(text(R.string.msg_backup_save_failed), saved.exceptionOrNull()?.message),
            )
        }
    }

    /** Whether the next picked backup file is only checked; set just before the picker opens. */
    private var checkNextBackup = false

    fun expectBackup(checkOnly: Boolean) {
        checkNextBackup = checkOnly
    }

    fun openRestore(uri: Uri) {
        val context = getApplication<Application>()
        val checkOnly = checkNextBackup
        viewModelScope.launch {
            val file = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openInputStream(uri)!!.use { it.readBytes() } }
            }
            file.onSuccess { restoreRequest = RestoreRequest(it, checkOnly) }
                .onFailure { _messages.send(UserMessage(text(R.string.msg_open_failed), it.message)) }
        }
    }

    fun restore(code: String) {
        val request = restoreRequest ?: return
        if (request.checkOnly) return check(request, code)
        restoreRequest = RestoreRequest(request.file, working = true)
        val context = getApplication<Application>()
        viewModelScope.launch {
            val lostBefore = catalog?.copies?.count { it.needsRestore } ?: 0
            val result = withContext(Dispatchers.Default) {
                runCatching { KeystoreSigner(context).restore(request.file, code) }
            }
            result.exceptionOrNull()?.let { e ->
                Log.w(TAG, "restore failed", e)
                restoreRequest = RestoreRequest(request.file, error = text((e as? BackupFailure)?.reason?.text ?: R.string.backup_restore_failed))
                return@launch
            }
            restoreRequest = null
            val backup = result.getOrThrow()
            withContext(Dispatchers.IO) { applyBackedUpCopies(backup.copies) }
            val loaded = withContext(Dispatchers.IO) { AppCatalog.load(context, ::carOptions) }
            catalog = loaded
            backedUp = true
            updateRestorable(loaded)
            val fixed = lostBefore - loaded.copies.count { it.needsRestore }
            // Restoring this phone's own backup fixes nothing but still covers its copies
            val covered = loaded.copies.count { !it.signedElsewhere }
            _messages.send(
                UserMessage(
                    when {
                        fixed > 0 -> getApplication<Application>().resources.getQuantityString(R.plurals.msg_restored_fixed, fixed, fixed)
                        restorable.isNotEmpty() -> text(R.string.msg_restored_readd)
                        covered > 0 -> text(R.string.msg_restored_covers)
                        else -> text(R.string.msg_restored_new)
                    },
                ),
            )
        }
    }

    private fun check(request: RestoreRequest, code: String) {
        restoreRequest = RestoreRequest(request.file, checkOnly = true, working = true)
        val context = getApplication<Application>()
        viewModelScope.launch {
            val result = withContext(Dispatchers.Default) { runCatching { KeystoreSigner(context).check(request.file, code) } }
            result.exceptionOrNull()?.let { e ->
                Log.w(TAG, "backup check failed", e)
                val reason = (e as? BackupFailure)?.reason?.text ?: R.string.backup_restore_failed
                restoreRequest = RestoreRequest(request.file, checkOnly = true, error = text(reason))
                return@launch
            }
            restoreRequest = null
            val backup = result.getOrThrow()
            val works = backup != null
            if (backup != null) {
                backedUp = true
                rememberBackedUpCopies(backup.copies)
                catalog?.let(::updateRestorable)
            }
            _messages.send(UserMessage(text(if (works) R.string.msg_check_ok else R.string.msg_check_other)))
        }
    }

    private fun rememberBackedUpCopies(copies: List<BackedUpCopy>) {
        prefs.edit().putStringSet(BACKUP_COPIES, copies.map { it.originalPackage }.toSet()).apply()
        catalog?.let(::updateRestorable)
    }

    /**
     * Keeps the choices a restored backup saved for each copy, unless this phone already has its
     * own, and lists the copies to offer making again.
     */
    private fun applyBackedUpCopies(copies: List<BackedUpCopy>) {
        val context = getApplication<Application>()
        for (copy in copies) {
            val pkg = copy.originalPackage
            if (!prefs.contains("keep_portrait:$pkg")) CopyBuilder.save(context, pkg, copy.options)
            if (!copy.autoUpdate) AutoUpdate.setAllowed(context, pkg, false)
        }
        val pending = prefs.getStringSet(RESTORE_PENDING, null).orEmpty() + copies.map { it.originalPackage }
        prefs.edit().putStringSet(RESTORE_PENDING, pending).putStringSet(BACKUP_COPIES, copies.map { it.originalPackage }.toSet()).apply()
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

    /** What new copies start with; existing copies keep their own choices. */
    var defaults by mutableStateOf(CopyBuilder.defaults(app))
        private set

    fun changeDefaults(options: CarOptions) {
        CopyBuilder.setDefaults(getApplication(), options)
        defaults = options
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

    /** Saves whether the copy shows in the phone's app list and rebuilds it with that. */
    fun setHideOnPhone(copy: CarCopy, hide: Boolean) {
        val original = copy.original ?: return
        prefs.edit().putBoolean("hide_on_phone:${original.info.packageName}", hide).apply()
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
        if (uris.isEmpty()) return
        if (job != null || backgroundBuild != null) {
            viewModelScope.launch { _messages.send(UserMessage(text(R.string.msg_busy_file))) }
            return
        }
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
                    // Includes cache Android would clear to make room, like PatchPipeline's check
                    val storage = context.getSystemService(StorageManager::class.java)
                    val import = ApkImport.read(files, dir, storage.getAllocatableBytes(storage.getUuidForPath(dir)))
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
                _messages.send(UserMessage(text(R.string.msg_file_failed), details((e as? ImportFailure)?.message ?: CopyBuilder.describe(e))))
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
                _messages.send(UserMessage(text(R.string.msg_add_failed, label), details(CopyBuilder.describe(e))))
                return@launch
            } finally {
                withContext(Dispatchers.IO) { cleanUp() }
            }
            if (!started) {
                job = null
                nextQueued(continueRun = false)
                _messages.send(UserMessage(text(R.string.msg_busy_background)))
            }
        }
    }

    private fun onLog(line: String) {
        Log.i(TAG, line)
        synchronized(log) { log.add(line) }
        if (line == ShizukuMode.INSTALL_LOG) {
            viewModelScope.launch { job = job?.copy(silent = true) }
            return
        }
        val step = when {
            line.startsWith("Signing") -> JobStep.Sign
            line.startsWith("Car-enabled") || line.startsWith("Renamed") -> JobStep.Patch
            else -> null
        } ?: return
        viewModelScope.launch { job = job?.copy(step = maxOf(job?.step ?: 0, step.ordinal)) }
    }

    override fun onCleared() {
        Shizuku.removeBinderReceivedListener(shizukuChanged)
        Shizuku.removeBinderDeadListener(shizukuGone)
        Shizuku.removeRequestPermissionResultListener(shizukuAnswered)
        // The patch runs in viewModelScope, which is cancelled with the model
        PatchService.stop(getApplication())
    }

    private fun text(@StringRes id: Int, vararg args: Any): String = getApplication<Application>().getString(id, *args)

    private fun details(error: String?) = synchronized(log) { (log + listOfNotNull(error)).joinToString("\n") }
}
