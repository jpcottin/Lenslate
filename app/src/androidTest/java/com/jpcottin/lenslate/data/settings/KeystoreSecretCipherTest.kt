package com.jpcottin.lenslate.data.settings

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyStore

/** Exercises the real Android Keystore, which the JVM unit tests cannot. */
@RunWith(AndroidJUnit4::class)
class KeystoreSecretCipherTest {
    private val alias = "lenslate_settings_test"
    private val cipher = KeystoreSecretCipher(alias)

    @After
    fun tearDown() = deleteKey()

    @Test
    fun roundTrip() {
        val stored = cipher.encrypt("AIza-secret-key")
        assertFalse("AIza-secret-key" in stored)
        assertEquals("AIza-secret-key", cipher.decrypt(stored))
        // A fresh IV every time: the same secret never encrypts to the same text.
        assertNotEquals(stored, cipher.encrypt("AIza-secret-key"))
    }

    @Test
    fun garbage_decryptsToNull() {
        assertNull(cipher.decrypt("not base64 !"))
        assertNull(cipher.decrypt(""))
        assertNull(cipher.decrypt("AAAA"))
    }

    @Test
    fun lostKeystoreKey_decryptsToNull() {
        val stored = cipher.encrypt("AIza-secret-key")
        // What a restore on another device looks like: the settings file without its key.
        deleteKey()
        assertNull(cipher.decrypt(stored))
    }

    private fun deleteKey() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(alias)
}
