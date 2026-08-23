package io.github.jpcottin.lenslate.ui.phone

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.jpcottin.lenslate.data.settings.Settings
import io.github.jpcottin.lenslate.domain.Language
import io.github.jpcottin.lenslate.domain.LiveTranslationState
import io.github.jpcottin.lenslate.domain.Utterance
import io.github.jpcottin.lenslate.ui.phone.home.HomeScreen
import io.github.jpcottin.lenslate.ui.theme.LenslateTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class HomeScreenTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private fun setContent(
        live: LiveTranslationState,
        connected: Boolean = true,
        onToggle: () -> Unit = {},
        onSwap: () -> Unit = {},
        onLaunch: () -> Unit = {},
    ) = composeTestRule.setContent {
        LenslateTheme {
            HomeScreen(
                live = live,
                settings = Settings(from = Language.FRENCH, to = Language.ENGLISH),
                glassesConnected = connected,
                micPermissionDenied = false,
                launchError = null,
                onToggleListening = onToggle,
                onSetLanguages = { _, _ -> },
                onSwapLanguages = onSwap,
                onClearTranscript = {},
                onOpenSettings = {},
                onLaunchOnGlasses = onLaunch,
                isWideWindow = false,
            )
        }
    }

    @Test
    fun transcript_showsSourceAndTranslation() {
        setContent(
            LiveTranslationState(
                isListening = true,
                utterances = listOf(Utterance(1, "Bonjour", "Hello")),
                partialSource = "Comment ça va",
            )
        )
        composeTestRule.onNodeWithText("Bonjour").assertIsDisplayed()
        composeTestRule.onNodeWithText("Hello").assertIsDisplayed()
        composeTestRule.onNodeWithText("Comment ça va").assertIsDisplayed()
        // The extended FAB merges its children, so look the label up in the unmerged tree.
        composeTestRule.onNodeWithText("Stop", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun listenButton_togglesListening() {
        var toggles = 0
        setContent(LiveTranslationState(), onToggle = { toggles++ })
        composeTestRule.onNodeWithText("Listen", useUnmergedTree = true).performClick()
        assertEquals(1, toggles)
    }

    @Test
    fun launchOnGlasses_disabledWhenDisconnected_enabledWhenConnected() {
        setContent(LiveTranslationState(), connected = false)
        composeTestRule.onNodeWithText("No AI Glasses connected").assertIsDisplayed()
        composeTestRule.onNodeWithText("Launch on glasses").assertIsNotEnabled()
    }

    @Test
    fun launchOnGlasses_invokesCallback() {
        var launched = false
        setContent(LiveTranslationState(), connected = true, onLaunch = { launched = true })
        composeTestRule.onNodeWithText("AI Glasses connected").assertIsDisplayed()
        composeTestRule.onNodeWithText("Launch on glasses").assertIsEnabled().performClick()
        assertTrue(launched)
    }

    @Test
    fun swapButton_invokesCallback() {
        var swapped = false
        setContent(LiveTranslationState(), onSwap = { swapped = true })
        composeTestRule.onNodeWithContentDescription("Swap languages").performClick()
        assertTrue(swapped)
    }

    @Test
    fun preparingAndError_areShown() {
        setContent(LiveTranslationState(isListening = true, isPreparing = true, error = "Network error"))
        composeTestRule.onNodeWithText("Downloading FR → EN models…").assertIsDisplayed()
        composeTestRule.onNodeWithText("Network error").assertIsDisplayed()
        composeTestRule.onNodeWithText("Listening…").assertIsDisplayed()
    }

    @Test
    fun emptyTranscript_showsHint_andClearIsDisabled() {
        setContent(LiveTranslationState())
        composeTestRule.onNodeWithText("Tap Listen and start talking. Translations appear here and on your glasses.").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Clear transcript").assertIsNotEnabled()
    }

    @Test
    fun pendingTranslation_showsEllipsis_andTranslationErrorIsShown() {
        setContent(
            LiveTranslationState(
                utterances = listOf(Utterance(1, "Salut", null), Utterance(2, "Bof", null, error = "boom")),
            )
        )
        composeTestRule.onNodeWithText("…").assertIsDisplayed()
        composeTestRule.onNodeWithText("boom").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Clear transcript").assertIsEnabled()
    }
}
