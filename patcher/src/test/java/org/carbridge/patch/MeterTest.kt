package org.carbridge.patch

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class MeterTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun apk(name: String, bytes: Int) = tmp.newFile(name).apply { writeBytes(ByteArray(bytes)) }

    @Test
    fun climbsToOneInOrder() {
        val base = apk("base.apk", 300)
        val split = apk("split.apk", 100)
        val seen = mutableListOf<Float>()
        val meter = Meter(listOf(base, split)) { seen += it }

        val baseWrite = meter.writing(base)
        listOf(0f, 0.5f, 1f).forEach(baseWrite)
        val splitWrite = meter.writing(split)
        listOf(0f, 1f).forEach(splitWrite)
        meter.signed(base)
        meter.signed(split)

        assertEquals(1f, seen.last(), 0.0001f)
        assertTrue("never goes back: $seen", seen.zipWithNext().all { (a, b) -> b >= a })
        // Base (3/4 of the bytes) fully written: 3/4 of the 70% rewrite share
        assertTrue(seen.any { kotlin.math.abs(it - 0.525f) < 0.001f })
    }

    @Test
    fun skipsSubPercentSteps() {
        val base = apk("base.apk", 100)
        val seen = mutableListOf<Float>()
        val write = Meter(listOf(base)) { seen += it }.writing(base)
        repeat(1000) { write(it / 1000f) }
        assertTrue("too many updates: ${seen.size}", seen.size <= 45)
    }
}
