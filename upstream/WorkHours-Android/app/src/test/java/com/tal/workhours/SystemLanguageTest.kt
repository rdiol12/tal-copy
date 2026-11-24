package com.tal.workhours

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class SystemLanguageTest {
    @Test fun supportedRegionalLanguagesUseTheirTranslation() {
        for (tag in listOf("he-IL", "en-US", "es-MX", "ru-RU", "ar-EG", "hi-IN", "fr-CA", "pt-BR", "de-AT")) {
            assertEquals(tag.substringBefore('-'), supportedSystemLanguage(Locale.forLanguageTag(tag)))
        }
    }
    @Test fun unsupportedLanguagesUseEnglish() {
        for (tag in listOf("it-IT", "ja-JP", "ko-KR", "tr-TR", "zh-Hant-TW")) {
            assertEquals("en", supportedSystemLanguage(Locale.forLanguageTag(tag)))
        }
    }
    @Test fun simplifiedChineseUsesBundledScript() {
        for (tag in listOf("zh-CN", "zh-SG", "zh-Hans-CN")) {
            assertEquals("zh-Hans", supportedSystemLanguage(Locale.forLanguageTag(tag)))
        }
    }
}
