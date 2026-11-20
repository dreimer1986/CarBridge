package org.carbridge

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.os.IBinder
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.carbridge.patch.PatchPipeline
import rikka.shizuku.Shizuku

/**
 * Optional installs through Shizuku: no prompt even for a first install, and the copy gets the
 * runtime permissions the original was granted. Falls back to the normal installer when off.
 */
object ShizukuMode {
    enum class State { NotInstalled, NotRunning, NeedsPermission, Ready }

    const val PACKAGE = "moe.shizuku.privileged.api"
    const val PERMISSION_REQUEST = 7
    const val INSTALL_LOG = "Installing through Shizuku"

    fun state(context: Context): State = when {
        Shizuku.pingBinder() -> if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) State.Ready else State.NeedsPermission
        // Sui (root) has no app, so only a missing app and no binder means it isn't there
        runCatching { context.packageManager.getPackageInfo(PACKAGE, 0) }.isFailure -> State.NotInstalled
        else -> State.NotRunning
    }

    fun isEnabled(context: Context) = prefs(context).getBoolean(Prefs.USE_SHIZUKU, false)

    fun setEnabled(context: Context, on: Boolean) = prefs(context).edit().putBoolean(Prefs.USE_SHIZUKU, on).apply()

    fun requestPermission() = Shizuku.requestPermission(PERMISSION_REQUEST)

    /**
     * Installs [apks] as [carPackage] through Shizuku and reports to [InstallStatusReceiver] like a
     * PackageInstaller session. Returns false, having done nothing, when Shizuku can't be used.
     */
    fun install(context: Context, apks: List<File>, carPackage: String, background: Boolean, log: (String) -> Unit): Boolean {
        if (!isEnabled(context) || state(context) != State.Ready) return false
        val shell = try {
            connect(context)
        } catch (e: IOException) {
            log("Shizuku: ${e.message} Installing the usual way.")
            return false
        }
        log(INSTALL_LOG)
        // -i keeps CarBridge as the installer, so later updates without Shizuku stay prompt-free
        val created = shell.exec("pm", "install-create", "-r", "-i", context.packageName, "--user", "current")
        val session = Regex("""\[(\d+)]""").find(created.output)?.groupValues?.get(1)
        if (created.code != 0 || session == null) {
            report(context, carPackage, background, PackageInstaller.STATUS_FAILURE, created.output)
            return true
        }
        for (apk in apks) {
            val pfd = ParcelFileDescriptor.open(apk, ParcelFileDescriptor.MODE_READ_ONLY)
            val written = shell.exec("pm", "install-write", "-S", apk.length().toString(), session, apk.name, "-", input = pfd)
            if (written.code != 0) {
                shell.exec("pm", "install-abandon", session)
                report(context, carPackage, background, PackageInstaller.STATUS_FAILURE, written.output)
                return true
            }
        }
        val committed = shell.exec("pm", "install-commit", session)
        if (committed.code != 0 || !committed.output.contains("Success")) {
            report(context, carPackage, background, statusOf(committed.output), committed.output)
            return true
        }
        val granted = grantLikeOriginal(context, shell, carPackage)
        if (granted > 0) log("Gave the copy $granted permission(s) the original has")
        report(context, carPackage, background, PackageInstaller.STATUS_SUCCESS, null)
        return true
    }

    private class Run(val code: Int, val output: String)

    private fun IShellService.exec(vararg command: String, input: ParcelFileDescriptor? = null): Run {
        val result = run(arrayOf(*command), input)
        return Run(result.substringBefore('\n').toIntOrNull() ?: -1, result.substringAfter('\n').trim())
    }

    /** Runtime permissions granted to the original that the copy also requests. */
    private fun grantLikeOriginal(context: Context, shell: IShellService, carPackage: String): Int {
        val pm = context.packageManager
        val original = runCatching {
            pm.getPackageInfo(PatchPipeline.originalPackageName(carPackage), PackageManager.GET_PERMISSIONS)
        }.getOrNull() ?: return 0
        val requested = runCatching {
            pm.getPackageInfo(carPackage, PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty().toSet()
        }.getOrDefault(emptySet())
        return grantedRuntime(pm, original).filter { it in requested }
            .count { shell.exec("pm", "grant", carPackage, it).code == 0 }
    }

    private fun grantedRuntime(pm: PackageManager, info: PackageInfo): List<String> {
        val perms = info.requestedPermissions.orEmpty()
        val flags = info.requestedPermissionsFlags ?: return emptyList()
        return perms.filterIndexed { i, perm ->
            flags[i] and PackageInfo.REQUESTED_PERMISSION_GRANTED != 0 &&
                runCatching { pm.getPermissionInfo(perm, 0).protection == PermissionInfo.PROTECTION_DANGEROUS }.getOrDefault(false)
        }
    }

    private fun statusOf(output: String) = when {
        "INSTALL_FAILED_UPDATE_INCOMPATIBLE" in output -> PackageInstaller.STATUS_FAILURE_CONFLICT
        "INSTALL_FAILED_INSUFFICIENT_STORAGE" in output -> PackageInstaller.STATUS_FAILURE_STORAGE
        "INSTALL_FAILED_VERSION_DOWNGRADE" in output -> PackageInstaller.STATUS_FAILURE_INCOMPATIBLE
        else -> PackageInstaller.STATUS_FAILURE
    }

    private fun report(context: Context, carPackage: String, background: Boolean, status: Int, message: String?) {
        context.sendBroadcast(
            Intent(context, InstallStatusReceiver::class.java)
                .putExtra(PackageInstaller.EXTRA_STATUS, status)
                .putExtra(PackageInstaller.EXTRA_PACKAGE_NAME, carPackage)
                .putExtra(PackageInstaller.EXTRA_STATUS_MESSAGE, message)
                .putExtra(Installer.EXTRA_CAR_PACKAGE, carPackage)
                .putExtra(Installer.EXTRA_BACKGROUND, background),
        )
    }

    @Volatile private var service: IShellService? = null

    /** Starts or reuses the shell-side helper; blocks, so call it off the main thread. */
    private fun connect(context: Context): IShellService {
        service?.takeIf { it.asBinder().pingBinder() }?.let { return it }
        val ready = CountDownLatch(1)
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                if (binder?.pingBinder() == true) service = IShellService.Stub.asInterface(binder)
                ready.countDown()
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                service = null
            }
        }
        val version = context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode.toInt()
        val args = Shizuku.UserServiceArgs(ComponentName(context.packageName, ShellService::class.java.name))
            .daemon(false)
            .processNameSuffix("shell")
            .debuggable(context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0)
            .version(version)
        Shizuku.bindUserService(args, connection)
        if (!ready.await(15, TimeUnit.SECONDS)) throw IOException("its helper didn't start.")
        return service ?: throw IOException("its helper didn't start.")
    }

    private fun prefs(context: Context) = Prefs.of(context)
}
