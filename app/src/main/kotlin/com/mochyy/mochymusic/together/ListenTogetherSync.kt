// ListenTogetherSync.kt
//
// Ties the room to the player. The host publishes what is playing and where every couple of
// seconds; guests follow: they open the same song, seek to where the host is (allowing for the
// time the message took) and match play/pause. Guests' own playback is replaced while they're in
// a room, and only YouTube-backed songs can be shared, because guests have to open them by id.

package com.mochyy.mochymusic.together

import com.mochyy.mochymusic.models.MediaMetadata
import com.mochyy.mochymusic.playback.PlayerConnection
import com.mochyy.mochymusic.playback.queues.YouTubeQueue
import com.music.innertube.models.WatchEndpoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.math.abs

object ListenTogetherSync {
    private const val PublishEveryMs = 2_000L

    /** A guest is only corrected when they're this far from the host, so small network jitter doesn't stutter playback. */
    private const val DriftToleranceMs = 2_500L

    /** Clocks on two phones can disagree; never trust a sentAt that claims more than this much travel time. */
    private const val MaxTravelMs = 5_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var player: PlayerConnection? = null
    private var watching = false
    private var hostJob: Job? = null
    private var guestJob: Job? = null
    private var requestedTrackId: String? = null

    /** Called from the Home screen with the app's player; safe to call on every recomposition. */
    fun attach(playerConnection: PlayerConnection) {
        player = playerConnection
        if (watching) return
        watching = true
        scope.launch {
            ListenTogetherClient.room.collect { room ->
                hostJob?.cancel()
                guestJob?.cancel()
                hostJob = null
                guestJob = null
                requestedTrackId = null
                when (room?.role) {
                    TogetherRole.HOST -> {
                        hostJob = scope.launch { hostLoop() }
                    }
                    TogetherRole.GUEST -> {
                        guestJob = scope.launch {
                            ListenTogetherClient.remoteState.collect { applyRemote(it) }
                        }
                    }
                    null -> Unit
                }
            }
        }
    }

    private suspend fun hostLoop() = coroutineScope {
        launch {
            // a guest just joined and wants where we are now
            ListenTogetherClient.syncRequests.collect { snapshot()?.let(ListenTogetherClient::publish) }
        }
        while (isActive) {
            snapshot()?.let(ListenTogetherClient::publish)
            delay(PublishEveryMs)
        }
    }

    private fun snapshot(): TogetherState? = runCatching {
        val p = player ?: return null
        val metadata = p.mediaMetadata.value
        TogetherState(
            track = metadata?.let {
                TogetherTrack(
                    id = it.id,
                    title = it.title,
                    artist = it.artists.joinToString(", ") { artist -> artist.name },
                    thumbUrl = it.thumbnailUrl,
                    durationSec = it.duration,
                )
            },
            positionMs = p.player.currentPosition.coerceAtLeast(0L),
            playing = p.isEffectivelyPlaying.value,
            sentAt = System.currentTimeMillis(),
        )
    }.onFailure { Timber.w(it, "Listen together: couldn't read the player") }.getOrNull()

    private fun applyRemote(state: TogetherState) {
        val p = player ?: return
        val track = state.track ?: return
        runCatching {
            if (track.id != p.mediaMetadata.value?.id) {
                // still loading the song we asked for: don't ask again on every snapshot
                if (track.id == requestedTrackId) return@runCatching
                requestedTrackId = track.id
                val metadata = MediaMetadata(
                    id = track.id,
                    title = track.title,
                    artists = listOf(MediaMetadata.Artist(id = null, name = track.artist)),
                    duration = track.durationSec,
                    thumbnailUrl = track.thumbUrl,
                )
                p.playQueue(YouTubeQueue(WatchEndpoint(videoId = track.id), metadata))
                return@runCatching
            }

            val travel = (System.currentTimeMillis() - state.sentAt).coerceIn(0L, MaxTravelMs)
            val expected = if (state.playing) state.positionMs + travel else state.positionMs
            if (abs(p.player.currentPosition - expected) > DriftToleranceMs) p.seekTo(expected)

            val playingHere = p.isEffectivelyPlaying.value
            when {
                state.playing && !playingHere -> p.play()
                !state.playing && playingHere -> p.pause()
            }
        }.onFailure { Timber.w(it, "Listen together: couldn't follow the host") }
    }
}
