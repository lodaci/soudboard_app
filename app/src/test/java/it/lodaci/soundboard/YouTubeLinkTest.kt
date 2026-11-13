package it.lodaci.soundboard

import it.lodaci.soundboard.remote.AudioPicker
import it.lodaci.soundboard.remote.YouTube
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class YouTubeLinkTest {
    @Test
    fun recognizesYouTube() {
        assertTrue(YouTube.isYouTube("https://youtube.com/watch?v=hAkZgArOF8Q"))
        assertTrue(YouTube.isYouTube("https://youtu.be/hAkZgArOF8Q"))
        assertTrue(YouTube.isYouTube("https://m.youtube.com/watch?v=abc"))
        assertTrue(YouTube.isYouTube("https://music.youtube.com/watch?v=abc"))
        assertFalse(YouTube.isYouTube("https://example.com/a.mp3"))
        assertFalse(YouTube.isYouTube("not a url"))
    }

    @Test
    fun playlistOnly() {
        assertTrue(YouTube.isPlaylistOnly("https://youtube.com/playlist?list=PLMTUjc123"))
        assertTrue(YouTube.isPlaylistOnly("https://www.youtube.com/playlist?list=PLx&si=abc"))
        assertFalse(YouTube.isPlaylistOnly("https://youtube.com/watch?v=abc&list=PLx"))
        assertFalse(YouTube.isPlaylistOnly("https://youtu.be/abc"))
    }

    @Test
    fun googleVideo() {
        assertTrue(YouTube.isGoogleVideo("https://rr3---sn-abc.googlevideo.com/videoplayback?x=1"))
        assertFalse(YouTube.isGoogleVideo("https://www.youtube.com/watch?v=1"))
    }

    @Test
    fun picksOriginalM4aHighestBitrate() {
        fun c(url: String, m4a: Boolean, br: Int, orig: Boolean = true, prog: Boolean = true) =
            AudioPicker.Candidate(url, m4a, br, orig, prog)
        val list = listOf(
            c("opus160", false, 160),
            c("m4a48", true, 48),
            c("m4a128", true, 128),
            c("dubbed-m4a256", true, 256, orig = false),
            c("dash-m4a512", true, 512, prog = false),
        )
        assertEquals("m4a128", AudioPicker.pick(list)?.url)
        assertEquals("opus160", AudioPicker.pick(listOf(c("opus160", false, 160), c("x", true, 300, prog = false)))?.url)
        assertNull(AudioPicker.pick(emptyList()))
    }
}
