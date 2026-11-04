package com.xrotune.app.data.api

import com.xrotune.app.data.model.PlaybackUnavailableException
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * These run against responses captured from the live InnerTube API, so they
 * fail when the real renderer shape drifts rather than only when the code
 * changes.
 */
class ResponseParserTest {

    private fun fixture(name: String): JSONObject {
        val text = checkNotNull(javaClass.classLoader?.getResourceAsStream(name)) {
            "Missing fixture $name"
        }.bufferedReader().use { it.readText() }
        return JSONObject(text)
    }

    @Test
    fun `parses search results from a real response`() {
        val tracks = ResponseParser.parseTracks(fixture("search_response.json"))

        assertFalse("expected search hits", tracks.isEmpty())
        assertTrue("expected several hits, got ${tracks.size}", tracks.size >= 5)

        val first = tracks.first()
        assertEquals("videoId should be 11 chars, got ${first.videoId}", 11, first.videoId.length)
        assertFalse(first.title.isBlank())
        assertNotNull("subtitle should carry artist/type info", first.subtitle)

        assertTrue(tracks.all { it.videoId.length == 11 })
        assertEquals("videoIds must be de-duplicated", tracks.size, tracks.map { it.videoId }.toSet().size)
    }

    @Test
    fun `parses home shelves and drops non playable rows`() {
        val sections = ResponseParser.parseSections(fixture("home_response.json"))

        assertFalse("expected at least one home shelf", sections.isEmpty())
        assertTrue(sections.all { it.title.isNotBlank() })

        val items = sections.flatMap { it.items }
        assertFalse("expected playable items", items.isEmpty())
        // Rows carrying only a browseEndpoint must be filtered out.
        assertTrue(
            "non-playable rows leaked in: ${items.filter { it.videoId.length != 11 }.map { it.videoId }}",
            items.all { it.videoId.length == 11 },
        )
    }

    @Test
    fun `reports playback refusal instead of throwing something opaque`() {
        val response = JSONObject(
            """
            {"playabilityStatus":{"status":"LOGIN_REQUIRED",
             "reason":{"runs":[{"text":"Sign in to confirm you're not a bot"}]}}}
            """.trimIndent(),
        )

        val error = runCatching { ResponseParser.parsePlayable(response, "abc") }
            .exceptionOrNull()

        assertTrue("expected PlaybackUnavailableException, got $error",
            error is PlaybackUnavailableException)
        assertEquals("LOGIN_REQUIRED", (error as PlaybackUnavailableException).status)
        assertTrue(error.reason!!.contains("not a bot"))
    }

    @Test
    fun `picks the highest bitrate audio stream`() {
        val response = JSONObject(
            """
            {"playabilityStatus":{"status":"OK"},
             "videoDetails":{"title":"Song","author":"Artist"},
             "streamingData":{"adaptiveFormats":[
               {"itag":251,"mimeType":"audio/webm; codecs=\"opus\"","bitrate":130000,"url":"https://a/lo"},
               {"itag":140,"mimeType":"audio/mp4; codecs=\"mp4a.40.2\"","bitrate":256000,"url":"https://a/hi"},
               {"itag":137,"mimeType":"video/mp4","bitrate":4000000,"url":"https://a/v"}
             ]}}
            """.trimIndent(),
        )

        val media = ResponseParser.parsePlayable(response, "abc")

        assertEquals(140, media.itag)
        assertEquals(256000, media.bitrate)
        assertEquals("https://a/hi", media.url)
        assertEquals("Song", media.title)
    }
}
