// QQMusicLyricsProvider.kt

package com.kiyo.music.lyrics

import android.content.Context
import com.music.directlyrics.QQMusic
import com.kiyo.music.constants.EnableQQMusicKey
import com.kiyo.music.utils.dataStore
import com.kiyo.music.utils.get

object QQMusicLyricsProvider : LyricsProvider {
    override val name = "QQMusic"
    override fun isEnabled(context: Context): Boolean = context.dataStore[EnableQQMusicKey] ?: true

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
    ): Result<String> = QQMusic.getLyrics(title, artist, duration)
}
