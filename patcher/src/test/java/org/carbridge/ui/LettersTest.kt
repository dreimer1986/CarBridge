package org.carbridge.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LettersTest {
    @Test
    fun filesLabelsUnderTheirFirstLetter() {
        assertEquals('V', Letters.of("vlc"))
        assertEquals('E', Letters.of("Écran"))
        assertEquals('F', Letters.of("  Firefox"))
        assertEquals(Letters.OTHER, Letters.of("2 Player Games"))
        assertEquals(Letters.OTHER, Letters.of("Телеграм"))
        assertEquals(Letters.OTHER, Letters.of(""))
    }

    @Test
    fun targetIsFirstLabelOfTheLetterOrTheNextOneThatHasLabels() {
        val labels = listOf("2 Player Games", "Firefox", "GitHub", "Gmail", "VLC")
        assertEquals(0, Letters.target(labels, '#'))
        assertEquals(2, Letters.target(labels, 'G'))
        assertEquals(1, Letters.target(labels, 'A'))
        assertEquals(4, Letters.target(labels, 'T'))
        assertEquals(4, Letters.target(labels, 'Z'))
        assertNull(Letters.target(emptyList(), 'A'))
    }
}
