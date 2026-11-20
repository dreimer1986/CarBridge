package org.carbridge

import android.os.ParcelFileDescriptor
import kotlin.system.exitProcess

/** Shizuku starts this in its own process with shell rights; it only runs `pm` for ShizukuMode. */
class ShellService : IShellService.Stub() {
    override fun destroy() {
        exitProcess(0)
    }

    override fun run(command: Array<String>, input: ParcelFileDescriptor?): String {
        // Shell rights reach far beyond installing; anything else that asks gets nothing
        if (!allowed(command.toList())) {
            input?.close()
            return "-1\nNot allowed: ${command.firstOrNull().orEmpty()} ${command.getOrNull(1).orEmpty()}"
        }
        val process = ProcessBuilder(*command).redirectErrorStream(true).start()
        process.outputStream.use { stdin ->
            input?.let { ParcelFileDescriptor.AutoCloseInputStream(it).use { apk -> apk.copyTo(stdin) } }
        }
        val output = process.inputStream.bufferedReader().readText()
        return "${process.waitFor()}\n$output"
    }

    companion object {
        private val INSTALL_STEPS = setOf("install-create", "install-write", "install-commit", "install-abandon")

        /** The `pm` calls ShizukuMode makes: install sessions, and permission grants to car copies. */
        fun allowed(command: List<String>): Boolean {
            if (command.size < 2 || command[0] != "pm") return false
            return when (command[1]) {
                in INSTALL_STEPS -> true
                "grant" -> command.size == 4 && command[2].endsWith(".car")
                else -> false
            }
        }
    }
}
