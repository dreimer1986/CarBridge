package org.carbridge

import org.carbridge.core.CarOptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CompatReportTest {
    private val facts = CompatReport.Facts(
        app = "VLC",
        packageName = "org.videolan.vlc",
        version = "3.7.1",
        splits = 2,
        options = CarOptions(keepMediaServices = true, fillScreen = false),
        carBridge = "0.5.0",
        androidAuto = "17.9",
        phone = "samsung SM-S928B",
        android = "17",
    )

    @Test
    fun titleNamesAppAndVersion() {
        assertEquals("Compatibility: VLC 3.7.1", CompatReport.title(facts))
    }

    @Test
    fun bodyListsEveryFactAsTableRows() {
        val body = CompatReport.body(facts)
        listOf(
            "| App | VLC (`org.videolan.vlc`) 3.7.1 |",
            "| Split APKs | 2 |",
            "| Layout | Keep portrait |",
            "| Audio | Android Auto's audio player |",
            "| Android Auto | 17.9 |",
            "| Phone | samsung SM-S928B, Android 17 |",
        ).forEach { assertTrue("missing $it in:\n$body", body.lines().contains(it)) }
    }
}
