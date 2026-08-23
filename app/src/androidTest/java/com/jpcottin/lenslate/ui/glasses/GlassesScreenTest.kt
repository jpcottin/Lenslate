package com.jpcottin.lenslate.ui.glasses

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.xr.glimmer.GlimmerTheme
import com.jpcottin.lenslate.domain.Language
import com.jpcottin.lenslate.domain.LiveTranslationState
import com.jpcottin.lenslate.domain.LiveTranslator
import com.jpcottin.lenslate.domain.UtteranceKind
import com.jpcottin.lenslate.domain.Utterance
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** The Glimmer screen is plain Compose, so it can be exercised on any device, not only glasses. */
class GlassesScreenTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private fun setContent(
        live: LiveTranslationState,
        showSource: Boolean = true,
        onToggle: () -> Unit = {},
        onRead: () -> Unit = {},
    ) =
        composeTestRule.setContent {
            GlimmerTheme {
                GlassesScreen(
                    live = live,
                    showSource = showSource,
                    isVisualUiSupported = true,
                    permissionDenied = false,
                    onToggleListening = onToggle,
                    onRead = onRead,
                    onRetryPermission = {},
                    onExit = {},
                )
            }
        }

    @Test
    fun showsLanguagePair_translation_andSource() {
        setContent(
            LiveTranslationState(
                isListening = true,
                from = Language.SPANISH,
                to = Language.ENGLISH,
                utterances = listOf(Utterance(1, "Buenos días", "Good morning")),
            )
        )
        composeTestRule.onNodeWithText("ES → EN").assertIsDisplayed()
        composeTestRule.onNodeWithText("Listening").assertIsDisplayed()
        composeTestRule.onNodeWithText("Good morning").assertIsDisplayed()
        composeTestRule.onNodeWithText("Buenos días").assertIsDisplayed()
    }

    @Test
    fun hidesSource_whenDisabled() {
        setContent(
            LiveTranslationState(isListening = true, utterances = listOf(Utterance(1, "Bonjour", "Hello"))),
            showSource = false,
        )
        composeTestRule.onNodeWithText("Hello").assertIsDisplayed()
        composeTestRule.onNodeWithText("Bonjour").assertDoesNotExist()
    }

    @Test
    fun tappingTheCard_togglesListening() {
        var toggles = 0
        setContent(LiveTranslationState(isListening = false), onToggle = { toggles++ })
        composeTestRule.onNodeWithText("Paused").assertIsDisplayed()
        composeTestRule.onNodeWithText("Tap the touchpad to listen").assertIsDisplayed().performClick()
        assertEquals(1, toggles)
    }

    @Test
    fun readButton_readsText_andIsDisabledWhileReading() {
        var reads = 0
        setContent(LiveTranslationState(isListening = true), onRead = { reads++ })
        composeTestRule.onNodeWithContentDescription("Read text with the camera").performClick()
        assertEquals(1, reads)
    }

    @Test
    fun readingState_andReadUtterance_areShown() {
        setContent(
            LiveTranslationState(
                isReading = true,
                utterances = listOf(Utterance(1, "SORTIE DE SECOURS", "EMERGENCY EXIT", kind = UtteranceKind.READ)),
            )
        )
        composeTestRule.onNodeWithText("Reading…").assertIsDisplayed()
        composeTestRule.onNodeWithText("EMERGENCY EXIT").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Read text with the camera").assertIsNotEnabled()
    }

    @Test
    fun noTextFound_isExplained() {
        setContent(LiveTranslationState(isListening = false, error = LiveTranslator.NO_TEXT_FOUND))
        composeTestRule.onNodeWithText("Error: No text found. Try getting closer or improving the light.").assertIsDisplayed()
    }
}
