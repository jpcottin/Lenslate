package io.github.jpcottin.lenslate.ui.glasses

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.xr.glimmer.GlimmerTheme
import io.github.jpcottin.lenslate.domain.Language
import io.github.jpcottin.lenslate.domain.LiveTranslationState
import io.github.jpcottin.lenslate.domain.Utterance
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** The Glimmer screen is plain Compose, so it can be exercised on any device, not only glasses. */
class GlassesScreenTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private fun setContent(live: LiveTranslationState, showSource: Boolean = true, onToggle: () -> Unit = {}) =
        composeTestRule.setContent {
            GlimmerTheme {
                GlassesScreen(
                    live = live,
                    showSource = showSource,
                    isVisualUiSupported = true,
                    permissionDenied = false,
                    onToggleListening = onToggle,
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
    fun tappingCard_togglesListening() {
        var toggles = 0
        setContent(LiveTranslationState(isListening = false), onToggle = { toggles++ })
        composeTestRule.onNodeWithText("Paused").assertIsDisplayed()
        composeTestRule.onNodeWithText("Tap to listen").performClick()
        assertEquals(1, toggles)
    }
}
