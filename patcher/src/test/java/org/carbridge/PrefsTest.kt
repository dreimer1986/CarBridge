package org.carbridge

import org.junit.Assert.assertEquals
import org.junit.Test

class PrefsTest {
    private val copies = mapOf("org.videolan.vlc" to 30_701L)

    @Test
    fun keepsBookkeepingForCopiesThatStillNeedIt() {
        val kept = listOf(Prefs.notified("org.videolan.vlc"), Prefs.autoTried("org.videolan.vlc"), Prefs.outdatedSince("org.videolan.vlc", 30_800))
        assertEquals(emptyList<String>(), kept.filter { Prefs.isStale(it, copies) })
    }

    @Test
    fun dropsBookkeepingForRemovedCopiesAndPastVersions() {
        val stale = listOf(
            Prefs.notified("org.mozilla.firefox"),
            Prefs.autoTried("org.mozilla.firefox"),
            Prefs.outdatedSince("org.mozilla.firefox", 1),
            Prefs.outdatedSince("org.videolan.vlc", 30_701),
            Prefs.outdatedSince("org.videolan.vlc", 30_600),
        )
        assertEquals(stale, stale.filter { Prefs.isStale(it, copies) })
    }

    @Test
    fun neverDropsChoicesOrSettings() {
        val keys = listOf(Prefs.keepMedia("gone.app"), Prefs.hideOnPhone("gone.app"), Prefs.autoUpdateOff("gone.app"), Prefs.AUTO_UPDATE)
        assertEquals(emptyList<String>(), keys.filter { Prefs.isStale(it, copies) })
    }
}
