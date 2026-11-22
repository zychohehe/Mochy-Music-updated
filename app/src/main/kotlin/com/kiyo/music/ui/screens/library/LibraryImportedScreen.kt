// LibraryImportedScreen.kt
//
// "Imported from Spotify": every song one service brought in, in the same A-Z list as Songs, with the
// service's own icon where the Library home has the avatar.

package com.kiyo.music.ui.screens.library

import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.kiyo.music.R
import com.kiyo.music.ui.screens.search.AvatarSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.kiyo.music.LocalPlayerConnection
import com.kiyo.music.extensions.toMediaItem
import com.kiyo.music.playback.queues.ListQueue
import com.kiyo.music.ui.component.LocalMenuState
import com.kiyo.music.ui.component.SongSwipeBox
import com.kiyo.music.ui.component.librarySwipeAction
import com.kiyo.music.ui.component.queueSwipeAction
import com.kiyo.music.ui.component.removeImportSwipeAction
import com.kiyo.music.ui.menu.SongMenu
import com.kiyo.music.viewmodels.LibraryImportedViewModel

@Composable
fun LibraryImportedScreen(
    navController: NavController,
    modifier: Modifier = Modifier,
    pureBlack: Boolean = false,
) {
    val viewModel: LibraryImportedViewModel = hiltViewModel()
    val songs by viewModel.songs.collectAsState()
    val playerConnection = LocalPlayerConnection.current ?: return
    val menuState = LocalMenuState.current
    val source = viewModel.source

    val label = source?.label ?: stringResource(R.string.lib_imported_unknown_source)
    val queueTitle = stringResource(R.string.lib_imported_queue_title, label)
    val sourceIcon: (@Composable () -> Unit)? = if (source != null) {
        { ImportSourceIcon(source, size = AvatarSize) }
    } else {
        null
    }

    LibraryEntityListScreen(
        title = source?.label ?: stringResource(R.string.library_tab_imported),
        subtitle = pluralStringResource(R.plurals.lib_imported_songs_subtitle, songs.size, songs.size, label),
        searchPlaceholder = stringResource(R.string.lib_imported_search, label),
        items = songs,
        idOf = { it.id },
        nameOf = { it.song.title },
        subtitleOf = { it.artists.joinToString { a -> a.name }.ifBlank { null } },
        thumbnailOf = { it.song.thumbnailUrl },
        playingIdOf = { it.id },
        onClick = { song ->
            // The order on screen, so the queue starts where the user tapped and runs down the list.
            val ordered = sortForLibraryIndex(songs) { it.song.title }
            playerConnection.playQueue(
                ListQueue(
                    title = queueTitle,
                    items = ordered.map { it.toMediaItem() },
                    startIndex = ordered.indexOf(song).coerceAtLeast(0),
                ),
            )
        },
        onLongClick = { song ->
            menuState.show {
                SongMenu(
                    originalSong = song,
                    navController = navController,
                    onDismiss = menuState::dismiss,
                )
            }
        },
        modifier = modifier,
        pureBlack = pureBlack,
        rowWrapper = { song, row ->
            SongSwipeBox(
                slab = false,
                library = { if (source != null) removeImportSwipeAction(song.song, source) else librarySwipeAction(song.song) },
                queue = { queueSwipeAction { song.toMediaItem() } },
            ) {
                row()
            }
        },
        trailing = sourceIcon,
    )
}
