package org.carbridge

import android.os.ParcelFileDescriptor
import kotlin.system.exitProcess

/** Shizuku starts this in its own process with shell rights; it only runs `pm` for ShizukuMode. */
class ShellService : IShellService.Stub() {
    override fun destroy() {
        exitProcess(0)
    }

    override fun run(command: Array<String>, input: ParcelFileDescriptor?): String {
        val process = ProcessBuilder(*command).redirectErrorStream(true).start()
        process.outputStream.use { stdin ->
            input?.let { ParcelFileDescriptor.AutoCloseInputStream(it).use { apk -> apk.copyTo(stdin) } }
        }
        val output = process.inputStream.bufferedReader().readText()
        return "${process.waitFor()}\n$output"
    }
}
