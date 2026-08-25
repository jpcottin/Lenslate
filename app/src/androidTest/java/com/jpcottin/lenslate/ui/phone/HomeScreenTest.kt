package com.jpcottin.lenslate.ui.phone

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.jpcottin.lenslate.data.settings.Settings
import com.jpcottin.lenslate.domain.Language
import com.jpcottin.lenslate.domain.LiveTranslationState
import com.jpcottin.lenslate.domain.Utterance
import com.jpcottin.lenslate.domain.UtteranceKind
import com.jpcottin.lenslate.ui.phone.home.HomeScreen
import com.jpcottin.lenslate.ui.theme.LenslateTheme
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
        onRead: () -> Unit = {},
        onShare: () -> Unit = {},
        onCopy: (Utterance) -> Unit = {},
    ) = composeTestRule.setContent {
        LenslateTheme {
            HomeScreen(
                live = live,
                settings = Settings(from = Language.FRENCH, to = Language.ENGLISH),
                glassesConnected = connected,
                micPermissionDenied = false,
                launchError = null,
                onToggleListening = onToggle,
                onRead = onRead,
                onSetLanguages = { _, _ -> },
                onSwapLanguages = onSwap,
                onClearTranscript = {},
                onShareTranscript = onShare,
                onCopyUtterance = onCopy,
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

    @Test
    fun shareButton_isDisabledWhenTranscriptIsEmpty() {
        setContent(LiveTranslationState())
        composeTestRule.onNodeWithContentDescription("Share transcript").assertIsNotEnabled()
    }

    @Test
    fun shareButton_invokesCallbackWhenTranscriptHasContent() {
        var shares = 0
        setContent(
            LiveTranslationState(utterances = listOf(Utterance(1, "Bonjour", "Hello"))),
            onShare = { shares++ },
        )
        composeTestRule.onNodeWithContentDescription("Share transcript").assertIsEnabled().performClick()
        assertEquals(1, shares)
    }

    @Test
    fun copyButton_invokesCallbackWithItsUtterance_andPartialRowHasNone() {
        val copied = mutableListOf<Utterance>()
        setContent(
            LiveTranslationState(
                utterances = listOf(Utterance(1, "Bonjour", "Hello"), Utterance(2, "Merci", "Thanks")),
                partialSource = "Comment ça va",
            ),
            onCopy = { copied += it },
        )
        // One copy button per finished row, none on the partial row.
        composeTestRule.onAllNodesWithContentDescription("Copy").assertCountEquals(2)
        composeTestRule.onAllNodesWithContentDescription("Copy")[1].performClick()
        assertEquals(listOf("Merci"), copied.map { it.source })
    }

    @Test
    fun readButton_invokesCallback_andReadUtteranceShowsCameraIcon() {
        var reads = 0
        setContent(
            LiveTranslationState(utterances = listOf(Utterance(1, "SORTIE", "EXIT", kind = UtteranceKind.READ))),
            onRead = { reads++ },
        )
        // Both the FAB and the transcript's camera icon are described as "Read"; click the button.
        composeTestRule.onNode(hasContentDescription("Read") and hasClickAction()).performClick()
        assertEquals(1, reads)
        composeTestRule.onNodeWithText("EXIT").assertIsDisplayed()
        composeTestRule.onAllNodesWithContentDescription("Read").assertCountEquals(2)
    }
}
