// ProfileStoreTest.kt

package com.kiyo.music.ui.screens.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProfileStoreTest {
    @Test fun `a raw path gets a file scheme`() {
        assertEquals("file:///data/user/0/app/files/profile_pic_1.jpg", ProfileStore.imageModel("/data/user/0/app/files/profile_pic_1.jpg"))
    }

    @Test fun `a file uri is left alone`() {
        assertEquals("file:///data/user/0/app/files/p.jpg", ProfileStore.imageModel("file:///data/user/0/app/files/p.jpg"))
    }

    @Test fun `a content uri is left alone`() {
        assertEquals("content://media/picker/0/1", ProfileStore.imageModel("content://media/picker/0/1"))
    }

    @Test fun `empty, blank and null give no model`() {
        assertNull(ProfileStore.imageModel(""))
        assertNull(ProfileStore.imageModel("   "))
        assertNull(ProfileStore.imageModel(null))
    }

    @Test fun `surrounding spaces are trimmed first`() {
        assertEquals("file:///files/p.jpg", ProfileStore.imageModel("  /files/p.jpg \n"))
        assertEquals("content://x/1", ProfileStore.imageModel(" content://x/1 "))
    }
}
