// ListenTogetherUi.kt
//
// The headphones button beside the profile picture in Home's top-right corner, and the sheet it
// opens to start or join a room.

package com.mochyy.mochymusic.together

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mochyy.mochymusic.LocalPlayerConnection
import com.mochyy.mochymusic.R

/** Round button for the Home header, same size as the profile picture next to it. */
@Composable
fun ListenTogetherHeaderButton(modifier: Modifier = Modifier) {
    val playerConnection = LocalPlayerConnection.current
    LaunchedEffect(playerConnection) { playerConnection?.let { ListenTogetherSync.attach(it) } }

    val room by ListenTogetherClient.room.collectAsState()
    var open by rememberSaveable { mutableStateOf(false) }
    val inRoom = room != null

    Box(
        modifier = modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(if (inRoom) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.14f))
            .clickable { open = true },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.Headphones,
            contentDescription = stringResource(R.string.together_button_cd),
            tint = if (inRoom) MaterialTheme.colorScheme.onPrimary else Color.White,
            modifier = Modifier.size(20.dp),
        )
    }

    if (open) ListenTogetherSheet(onDismiss = { open = false })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListenTogetherSheet(onDismiss: () -> Unit) {
    val room by ListenTogetherClient.room.collectAsState()
    val link by ListenTogetherClient.link.collectAsState()
    var code by rememberSaveable { mutableStateOf("") }
    var message by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(Unit) {
        ListenTogetherClient.errors.collect { message = it }
    }

    val sheetState = rememberModalBottomSheetState()
    val busy = link == TogetherLink.CONNECTING

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = stringResource(R.string.together_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )

            val current = room
            if (current == null) {
                Text(
                    text = stringResource(R.string.together_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = {
                        message = null
                        ListenTogetherClient.create()
                    },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.together_create))
                }
                OutlinedTextField(
                    value = code,
                    onValueChange = { typed -> code = typed.uppercase().filter { it.isLetterOrDigit() }.take(6) },
                    label = { Text(stringResource(R.string.together_code_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedButton(
                    onClick = {
                        message = null
                        ListenTogetherClient.join(code)
                    },
                    enabled = !busy && code.length == 6,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.together_join))
                }
                if (busy) {
                    Text(
                        text = stringResource(R.string.together_connecting),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                val isHost = current.role == TogetherRole.HOST
                Text(
                    text = stringResource(R.string.together_code_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = current.code,
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 4.sp,
                )
                Text(
                    text = stringResource(
                        R.string.together_role_members,
                        stringResource(if (isHost) R.string.together_role_host else R.string.together_role_guest),
                        current.members,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = stringResource(if (isHost) R.string.together_host_hint else R.string.together_guest_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = { ListenTogetherClient.leave() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.together_leave))
                }
            }

            message?.let {
                Text(
                    text = stringResource(it),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
