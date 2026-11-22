// ListenTogetherClient.kt
//
// The socket side of "listen together": creating or joining a room, and sending/receiving
// playback snapshots through the relay server in /server/listen-together. It knows nothing about
// the player; ListenTogetherSync does the playing.

package com.mochyy.mochymusic.together

import com.mochyy.mochymusic.R
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import timber.log.Timber
import java.util.concurrent.TimeUnit

/** Where the relay server lives. Deploy server/listen-together and put its wss:// address here. */
object ListenTogetherConfig {
    const val SERVER_URL = "wss://listen.mochy.app/ws"
}

enum class TogetherRole { HOST, GUEST }

enum class TogetherLink { IDLE, CONNECTING, CONNECTED }

data class TogetherRoom(val code: String, val role: TogetherRole, val members: Int)

/** The song being played for everyone in the room, and where it is. [sentAt] is the sender's clock in ms. */
data class TogetherTrack(
    val id: String,
    val title: String,
    val artist: String,
    val thumbUrl: String?,
    val durationSec: Int,
)

data class TogetherState(
    val track: TogetherTrack?,
    val positionMs: Long,
    val playing: Boolean,
    val sentAt: Long,
)

object ListenTogetherClient {
    private val http = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .build()

    private var socket: WebSocket? = null

    private val _link = MutableStateFlow(TogetherLink.IDLE)
    val link: StateFlow<TogetherLink> = _link.asStateFlow()

    private val _room = MutableStateFlow<TogetherRoom?>(null)
    val room: StateFlow<TogetherRoom?> = _room.asStateFlow()

    /** Snapshots arriving from the host, for guests. */
    private val _remoteState = MutableSharedFlow<TogetherState>(extraBufferCapacity = 16)
    val remoteState: SharedFlow<TogetherState> = _remoteState.asSharedFlow()

    /** A guest asked for the current state (just joined); the host answers with a fresh snapshot. */
    private val _syncRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
    val syncRequests: SharedFlow<Unit> = _syncRequests.asSharedFlow()

    /** Short user-facing reasons, as string resource ids. */
    private val _errors = MutableSharedFlow<Int>(extraBufferCapacity = 8)
    val errors: SharedFlow<Int> = _errors.asSharedFlow()

    fun create() = open(JSONObject().put("type", "create"))

    fun join(code: String) = open(JSONObject().put("type", "join").put("code", code))

    fun leave() {
        runCatching { socket?.send(JSONObject().put("type", "leave").toString()) }
        close()
    }

    /** Host only: publish what is playing now. Guests and rooms they are not hosting ignore it. */
    fun publish(state: TogetherState) {
        if (_room.value?.role != TogetherRole.HOST) return
        val track = state.track
        val json = JSONObject()
            .put("type", "state")
            .put("positionMs", state.positionMs)
            .put("playing", state.playing)
            .put("sentAt", state.sentAt)
            .put(
                "track",
                track?.let {
                    JSONObject()
                        .put("id", it.id)
                        .put("title", it.title)
                        .put("artist", it.artist)
                        .put("thumbUrl", it.thumbUrl ?: JSONObject.NULL)
                        .put("durationSec", it.durationSec)
                } ?: JSONObject.NULL,
            )
        runCatching { socket?.send(json.toString()) }
    }

    /** Guest: ask the host for a snapshot right away instead of waiting for its next tick. */
    fun requestState() {
        runCatching { socket?.send(JSONObject().put("type", "request_state").toString()) }
    }

    private fun open(hello: JSONObject) {
        close()
        _link.value = TogetherLink.CONNECTING
        val created = http.newWebSocket(
            Request.Builder().url(ListenTogetherConfig.SERVER_URL).build(),
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    webSocket.send(hello.toString())
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    if (socket === webSocket) handle(webSocket, text)
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    Timber.w(t, "Listen together: connection failed")
                    if (socket !== webSocket) return
                    socket = null
                    _room.value = null
                    _link.value = TogetherLink.IDLE
                    _errors.tryEmit(R.string.together_error_offline)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    if (socket !== webSocket) return
                    socket = null
                    val wasInRoom = _room.value != null
                    _room.value = null
                    _link.value = TogetherLink.IDLE
                    if (wasInRoom) _errors.tryEmit(R.string.together_closed)
                }
            },
        )
        socket = created
    }

    private fun handle(webSocket: WebSocket, text: String) {
        val msg = runCatching { JSONObject(text) }.getOrNull() ?: return
        when (msg.optString("type")) {
            "room" -> {
                val role = if (msg.optString("role") == "host") TogetherRole.HOST else TogetherRole.GUEST
                _room.value = TogetherRoom(
                    code = msg.optString("code"),
                    role = role,
                    members = msg.optInt("members", 1),
                )
                _link.value = TogetherLink.CONNECTED
                if (role == TogetherRole.GUEST) requestState()
            }

            "members" -> _room.value = _room.value?.copy(members = msg.optInt("count", 1))

            "state" -> parseState(msg)?.let { _remoteState.tryEmit(it) }

            "request_state" -> _syncRequests.tryEmit(Unit)

            "closed" -> {
                _room.value = null
                _errors.tryEmit(R.string.together_closed)
                close()
            }

            "error" -> {
                val res = when (msg.optString("reason")) {
                    "not_found" -> R.string.together_error_not_found
                    else -> R.string.together_error_offline
                }
                _errors.tryEmit(res)
                if (_room.value == null) {
                    // nothing joined, so there is no reason to stay connected
                    socket?.close(1000, null)
                    socket = null
                    _link.value = TogetherLink.IDLE
                }
            }
        }
    }

    private fun parseState(msg: JSONObject): TogetherState? {
        val trackJson = msg.optJSONObject("track")
        val track = trackJson?.let {
            TogetherTrack(
                id = it.optString("id").takeIf(String::isNotBlank) ?: return null,
                title = it.optString("title"),
                artist = it.optString("artist"),
                thumbUrl = it.optString("thumbUrl").takeIf { url -> url.isNotBlank() && url != "null" },
                durationSec = it.optInt("durationSec", 0),
            )
        }
        return TogetherState(
            track = track,
            positionMs = msg.optLong("positionMs", 0L).coerceAtLeast(0L),
            playing = msg.optBoolean("playing", false),
            sentAt = msg.optLong("sentAt", System.currentTimeMillis()),
        )
    }

    private fun close() {
        val current = socket
        socket = null
        runCatching { current?.close(1000, "bye") }
        _room.value = null
        _link.value = TogetherLink.IDLE
    }
}
