package com.kiyo.music.di

import com.kiyo.music.backup.CloudBackupManager
import com.kiyo.music.backup.CloudSignIn
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** for the cloud login callback and the scheduled backup worker, which hilt doesn't build */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface CloudBackupEntryPoint {
    fun cloudSignIn(): CloudSignIn

    fun cloudBackupManager(): CloudBackupManager
}
