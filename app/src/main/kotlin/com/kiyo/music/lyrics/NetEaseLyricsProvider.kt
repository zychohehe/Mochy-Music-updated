// NetEaseLyricsProvider.kt

package com.kiyo.music.lyrics

import android.content.Context
import com.music.directlyrics.NetEase
import com.kiyo.music.constants.EnableNetEaseKey
import com.kiyo.music.utils.dataStore
import com.kiyo.music.utils.get

object NetEaseLyricsProvider : LyricsProvider {
    override val name = "NetEase"
    override fun isEnabled(context: Context): Boolean = context.dataStore[EnableNetEaseKey] ?: true

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
    ): Result<String> = NetEase.getLyrics(title, artist, duration)
}
