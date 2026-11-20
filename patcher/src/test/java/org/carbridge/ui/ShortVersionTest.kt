package org.carbridge.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ShortVersionTest {
    @Test
    fun dropsBuildMetadata() {
        assertEquals("6.34.0", shortVersion("6.34.0+18552420-google"))
        assertEquals("16.26.0", shortVersion("16.26.0 (1626000)"))
    }

    @Test
    fun keepsPlainAndPreReleaseNames() {
        assertEquals("160.0.1", shortVersion("160.0.1"))
        assertEquals("2.0-beta1", shortVersion("2.0-beta1"))
    }

    @Test
    fun keepsNameThatIsOnlyMetadata() {
        assertEquals("+build", shortVersion("+build"))
    }
}
