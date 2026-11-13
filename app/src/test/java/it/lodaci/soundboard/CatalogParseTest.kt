package it.lodaci.soundboard

import it.lodaci.soundboard.data.TrackKind
import it.lodaci.soundboard.remote.catalog.ArchiveSource
import it.lodaci.soundboard.remote.catalog.FreesoundSource
import it.lodaci.soundboard.remote.catalog.OpenverseSource
import it.lodaci.soundboard.remote.catalog.licenseLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogParseTest {
    @Test
    fun licenses() {
        assertEquals("CC0", licenseLabel("cc0", "1.0"))
        assertEquals("CC BY 4.0", licenseLabel("by", "4.0"))
        assertEquals("CC BY-NC 3.0", licenseLabel("http://creativecommons.org/licenses/by-nc/3.0/"))
        assertEquals("CC0", licenseLabel("https://creativecommons.org/publicdomain/zero/1.0/"))
        assertEquals("Sampling+", licenseLabel("sampling+"))
        assertNull(licenseLabel(null))
    }

    @Test
    fun openverse() {
        val json = """
            {"result_count": 45, "page_count": 3, "page_size": 20, "page": 1, "results": [
              {"id": "a1", "title": "Sword clash", "creator": "foley_guy", "license": "by", "license_version": "4.0",
               "license_url": "https://creativecommons.org/licenses/by/4.0/", "foreign_landing_url": "https://freesound.org/s/1/",
               "url": "https://cdn.example.org/sword.mp3", "filetype": "mp3", "filesize": 51200, "duration": 1500,
               "source": "freesound", "category": "sound_effect"},
              {"id": "a2", "title": "Tavern theme", "creator": null, "license": "cc0", "license_version": "1.0",
               "url": "https://cdn.example.org/tavern.ogg", "filetype": null, "duration": null, "provider": "jamendo", "category": "music"},
              {"id": "a3", "title": "no file", "url": null}
            ]}
        """.trimIndent()
        val page = OpenverseSource.parse(json, 1)
        assertEquals(2, page.results.size)
        assertTrue(page.hasMore)
        assertEquals(45, page.total)
        val a = page.results[0]
        assertEquals("Sword clash", a.title)
        assertEquals("foley_guy", a.author)
        assertEquals("CC BY 4.0", a.license)
        assertEquals(1500, a.durationMs)
        assertEquals("https://cdn.example.org/sword.mp3", a.downloadUrl)
        assertEquals(TrackKind.SFX, a.suggestedKind)
        val b = page.results[1]
        assertNull(b.author)
        assertEquals("CC0", b.license)
        assertEquals(TrackKind.MUSIC, b.suggestedKind)
        assertEquals("jamendo", b.origin)
        assertFalse(OpenverseSource.parse(json, 3).hasMore)
    }

    @Test
    fun freesound() {
        val json = """
            {"count": 2, "next": null, "previous": null, "results": [
              {"id": 123, "name": "dragon_roar.wav", "username": "beast", "license": "http://creativecommons.org/licenses/by/3.0/",
               "duration": 3.25, "type": "wav", "filesize": 900000, "url": "https://freesound.org/people/beast/sounds/123/",
               "previews": {"preview-hq-mp3": "https://cdn.freesound.org/previews/0/123-hq.mp3", "preview-lq-mp3": "https://cdn.freesound.org/previews/0/123-lq.mp3"}},
              {"id": 124, "name": "forest ambience long", "username": "x", "license": "http://creativecommons.org/publicdomain/zero/1.0/",
               "duration": 120.0, "previews": {"preview-lq-mp3": "https://cdn.freesound.org/previews/0/124-lq.mp3"}}
            ]}
        """.trimIndent()
        val page = FreesoundSource.parse(json)
        assertEquals(2, page.results.size)
        assertFalse(page.hasMore)
        val a = page.results[0]
        assertEquals("dragon_roar", a.title)
        assertEquals("CC BY 3.0", a.license)
        assertEquals(3250, a.durationMs)
        assertEquals("https://cdn.freesound.org/previews/0/123-hq.mp3", a.downloadUrl)
        assertEquals("https://freesound.org/people/beast/sounds/123/", a.landingUrl)
        assertEquals("https://cdn.freesound.org/previews/0/124-lq.mp3", page.results[1].downloadUrl)
        assertEquals(TrackKind.MUSIC, page.results[1].suggestedKind)
    }

    @Test
    fun archive() {
        // stessa forma restituita dal server SoundVault (server/soundvault/indexer.py: search)
        val json = """
            {"results":[{"id":1,"title":"footstep grass 000","collection":"Kenney RPG","category":"Audio",
              "duration_ms":1000,"size":16044,"format":"wav"}],"page":1,"page_size":30,"total":31}
        """.trimIndent()
        val page = ArchiveSource.parse(json, "https://suoni.example.it")
        assertTrue(page.hasMore)
        val r = page.results.single()
        assertEquals("https://suoni.example.it/api/v1/files/1?compact=true", r.downloadUrl)
        assertEquals("Kenney RPG › Audio", r.origin)
        assertEquals("wav", r.format)
        assertEquals(16044, r.sizeBytes)
    }
}
