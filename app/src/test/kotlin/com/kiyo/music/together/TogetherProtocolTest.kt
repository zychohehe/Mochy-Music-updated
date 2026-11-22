// TogetherProtocolTest.kt

package com.kiyo.music.together

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TogetherProtocolTest {
    @Test fun `parseState reads a full snapshot`() {
        val state = parseState(
            JSONObject(
                """{"type":"state","positionMs":12345,"playing":true,"sentAt":1000,
                   "track":{"id":"abc","title":"Song","artist":"Band","thumbUrl":"https://i.example/x.jpg","durationSec":212}}""",
            ),
        )!!
        assertEquals("abc", state.track!!.id)
        assertEquals("Song", state.track!!.title)
        assertEquals("https://i.example/x.jpg", state.track!!.thumbUrl)
        assertEquals(212, state.track!!.durationSec)
        assertEquals(12345L, state.positionMs)
        assertTrue(state.playing)
        assertEquals(1000L, state.sentAt)
    }

    @Test fun `parseState accepts a null track`() {
        val state = parseState(JSONObject("""{"type":"state","track":null,"positionMs":5,"playing":false,"sentAt":1}"""))!!
        assertNull(state.track)
        assertFalse(state.playing)
    }

    @Test fun `parseState rejects a track without an id`() {
        assertNull(parseState(JSONObject("""{"type":"state","track":{"id":"","title":"x"},"sentAt":1}""")))
        assertNull(parseState(JSONObject("""{"type":"state","track":{"title":"x"},"sentAt":1}""")))
    }

    @Test fun `parseState clamps negatives and treats a null thumbnail as none`() {
        val state = parseState(
            JSONObject("""{"type":"state","positionMs":-50,"sentAt":1,"track":{"id":"a","thumbUrl":null,"durationSec":-3}}"""),
        )!!
        assertEquals(0L, state.positionMs)
        assertNull(state.track!!.thumbUrl)
        assertEquals(0, state.track!!.durationSec)
    }

    @Test fun `parseMembers reads names, hosts and https avatars only`() {
        val members = parseMembers(
            JSONObject(
                """{"type":"members","count":3,"members":[
                    {"name":"Kiyo","host":true,"avatarUrl":"https://a.example/m.jpg"},
                    {"name":"Teman","host":false,"avatarUrl":"file:///sdcard/x.jpg"},
                    {"name":"","host":false}]}""",
            ),
        )
        assertEquals(3, members.size)
        assertEquals(TogetherMember("Kiyo", "https://a.example/m.jpg", true), members[0])
        assertNull(members[1].avatarUrl)
        assertEquals("", members[2].name)
    }

    @Test fun `parseMembers can read the room message's list and survives a missing one`() {
        val room = JSONObject("""{"type":"room","members":1,"memberList":[{"name":"A","host":true}]}""")
        assertEquals(listOf(TogetherMember("A", null, true)), parseMembers(room, "memberList"))
        assertTrue(parseMembers(JSONObject("""{"type":"members","count":1}""")).isEmpty())
    }

    @Test fun `local songs are not shareable`() {
        assertTrue(isShareableSong("dQw4w9WgXcQ", null))
        assertFalse(isShareableSong("LOCAL_123", null))
        assertFalse(isShareableSong("dQw4w9WgXcQ", "content://media/external/audio/1"))
        assertFalse(isShareableSong("", null))
    }

    @Test fun `shareableTrack sends no track for a local song`() {
        assertNull(shareableTrack("LOCAL_9", "t", "a", "file:///sdcard/art.jpg", 100, "file:///sdcard/a.mp3"))
    }

    @Test fun `shareableTrack drops a thumbnail that is not https`() {
        val track = shareableTrack("abc", "t", "a", "content://media/art/1", 100, null)!!
        assertNull(track.thumbUrl)
        assertEquals("https://i.example/x.jpg", shareableTrack("abc", "t", "a", "https://i.example/x.jpg", 100, null)!!.thumbUrl)
    }

    @Test fun `trackToJson never contains a local path`() {
        val json = trackToJson(shareableTrack("abc", "t", "a", "content://x", 10, null)!!).toString()
        assertFalse(json.contains("content://"))
        assertFalse(json.contains("file://"))
    }

    @Test fun `room codes are cleaned to six upper case characters`() {
        assertEquals("ABC123", cleanRoomCode(" abc-123 xyz"))
        assertEquals("", cleanRoomCode("--"))
    }

    @Test fun `listen links give their code`() {
        assertEquals("BCD234", parseListenLink("kiyo", "listen", listOf("BCD234")))
        assertEquals("BCD234", parseListenLink("KIYO", "Listen", listOf("bcd234")))
        assertEquals("kiyo://listen/BCD234", listenLink("BCD234"))
    }

    @Test fun `other or malformed links give nothing`() {
        assertNull(parseListenLink("https", "listen", listOf("BCD234")))
        assertNull(parseListenLink("kiyo", "playlist", listOf("BCD234")))
        assertNull(parseListenLink("kiyo", "listen", emptyList()))
        assertNull(parseListenLink("kiyo", "listen", listOf("BCD")))
        assertNull(parseListenLink("kiyo", "listen", listOf("BCD234EXTRA")))
        assertNull(parseListenLink("kiyo", "listen", listOf("BCD-23")))
    }
}
