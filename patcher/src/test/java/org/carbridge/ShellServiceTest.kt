package org.carbridge

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShellServiceTest {
    @Test
    fun allowsWhatShizukuModeRuns() {
        assertTrue(ShellService.allowed(listOf("pm", "install-create", "-r", "-i", "org.carbridge", "--user", "current")))
        assertTrue(ShellService.allowed(listOf("pm", "install-write", "-S", "10", "123", "base.apk", "-")))
        assertTrue(ShellService.allowed(listOf("pm", "install-commit", "123")))
        assertTrue(ShellService.allowed(listOf("pm", "install-abandon", "123")))
        assertTrue(ShellService.allowed(listOf("pm", "grant", "org.videolan.vlc.car", "android.permission.CAMERA")))
    }

    @Test
    fun refusesEverythingElse() {
        assertFalse(ShellService.allowed(listOf("sh", "-c", "pm install-commit 1")))
        assertFalse(ShellService.allowed(listOf("pm", "uninstall", "com.example")))
        assertFalse(ShellService.allowed(listOf("pm", "grant", "com.example", "android.permission.CAMERA")))
        assertFalse(ShellService.allowed(listOf("pm", "grant", "a.car", "android.permission.CAMERA", "extra")))
        assertFalse(ShellService.allowed(listOf("pm")))
        assertFalse(ShellService.allowed(emptyList()))
    }
}
