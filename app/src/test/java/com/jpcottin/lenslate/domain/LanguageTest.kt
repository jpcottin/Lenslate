package com.jpcottin.lenslate.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LanguageTest {
    @Test
    fun fromCode_isCaseInsensitive_andTrims() {
        assertEquals(Language.FRENCH, Language.fromCode("fr"))
        assertEquals(Language.JAPANESE, Language.fromCode(" JA "))
        assertNull(Language.fromCode("xx"))
        assertNull(Language.fromCode(null))
    }

    @Test
    fun tags_andLabels() {
        assertEquals("fr-FR", Language.FRENCH.tag)
        assertEquals("fr", Language.FRENCH.locale.language)
        assertEquals("DE", Language.GERMAN.shortLabel)
        assertEquals(5, Language.entries.size)
    }
}
