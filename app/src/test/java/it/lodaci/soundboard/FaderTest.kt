package it.lodaci.soundboard

import it.lodaci.soundboard.playback.Fader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FaderTest {
    @Test
    fun endpoints() {
        assertEquals(0f, Fader.value(0f, 1f, 0f), 1e-6f)
        assertEquals(1f, Fader.value(0f, 1f, 1f), 1e-6f)
        assertEquals(0.8f, Fader.value(0.8f, 0f, 0f), 1e-6f)
        assertEquals(0f, Fader.value(0.8f, 0f, 1f), 1e-6f)
    }

    @Test
    fun clampsTime() {
        assertEquals(1f, Fader.value(0f, 1f, 5f), 1e-6f)
        assertEquals(0f, Fader.value(0f, 1f, -1f), 1e-6f)
    }

    @Test
    fun monotonic() {
        var prevIn = -1f
        var prevOut = 2f
        for (i in 0..100) {
            val t = i / 100f
            val fin = Fader.value(0f, 1f, t)
            val fout = Fader.value(1f, 0f, t)
            assertTrue(fin >= prevIn)
            assertTrue(fout <= prevOut)
            prevIn = fin
            prevOut = fout
        }
    }

    @Test
    fun equalPowerAtMidpoint() {
        val fin = Fader.value(0f, 1f, 0.5f)
        val fout = Fader.value(1f, 0f, 0.5f)
        assertEquals(1f, fin * fin + fout * fout, 1e-4f)
    }

    @Test
    fun scaledDuration() {
        assertEquals(2000L, Fader.scaledDuration(2000, 1f, 0f))
        assertEquals(1000L, Fader.scaledDuration(2000, 0.5f, 0f))
        assertEquals(0L, Fader.scaledDuration(2000, 1f, 1f))
    }
}
