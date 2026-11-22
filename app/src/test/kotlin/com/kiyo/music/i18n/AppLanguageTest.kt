package com.kiyo.music.i18n

import com.kiyo.music.constants.SYSTEM_DEFAULT
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class AppLanguageTest {
    @Test fun `unset language opens in Indonesian whatever the device says`() {
        val indonesian = Locale.forLanguageTag(DefaultUiLanguageTag)
        assertEquals(indonesian, resolveUiLocale(SYSTEM_DEFAULT, Locale.JAPANESE))
        assertEquals(indonesian, resolveUiLocale(null, Locale.ENGLISH))
    }

    @Test fun `explicit override wins over device`() {
        assertEquals(Locale.ENGLISH, resolveUiLocale("en", Locale.forLanguageTag("id-ID")))
        assertEquals(Locale.forLanguageTag("id"), resolveUiLocale("id", Locale.JAPANESE))
        assertEquals(Locale.forLanguageTag("zh-TW"), resolveUiLocale("zh-TW", Locale.ENGLISH))
    }

    @Test fun `empty or malformed selection falls back to the Indonesian default`() {
        val indonesian = Locale.forLanguageTag(DefaultUiLanguageTag)
        assertEquals(indonesian, resolveUiLocale("", Locale.JAPANESE))
        assertEquals(indonesian, resolveUiLocale("!!!", Locale.JAPANESE))
        assertEquals(indonesian, resolveUiLocale(null, Locale.ROOT))
    }

    @Test fun `missing resources fall back to English before other locales`() {
        assertEquals(listOf(Locale.forLanguageTag("id"), Locale.ENGLISH), uiLocaleFallbacks(Locale.forLanguageTag("id")))
        assertEquals(listOf(Locale.forLanguageTag("fr-CA"), Locale.ENGLISH), uiLocaleFallbacks(Locale.forLanguageTag("fr-CA")))
        assertEquals(listOf(Locale.ENGLISH), uiLocaleFallbacks(Locale.ENGLISH))
    }
}
