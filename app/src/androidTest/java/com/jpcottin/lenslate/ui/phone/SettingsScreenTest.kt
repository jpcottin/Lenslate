package com.jpcottin.lenslate.ui.phone

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.jpcottin.lenslate.data.settings.Settings
import com.jpcottin.lenslate.data.translate.ModelStatus
import com.jpcottin.lenslate.domain.EngineKind
import com.jpcottin.lenslate.domain.Language
import com.jpcottin.lenslate.ui.phone.settings.SettingsScreen
import com.jpcottin.lenslate.ui.theme.LenslateTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SettingsScreenTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private fun setContent(
        settings: Settings,
        models: Map<Language, ModelStatus> = emptyMap(),
        showBack: Boolean = true,
        onBack: () -> Unit = {},
        onEngine: (EngineKind) -> Unit = {},
        onSpeak: (Boolean) -> Unit = {},
        onDownload: (Language) -> Unit = {},
        onDelete: (Language) -> Unit = {},
    ) = composeTestRule.setContent {
        LenslateTheme {
            SettingsScreen(
                settings = settings,
                models = models,
                showBack = showBack,
                onBack = onBack,
                onEngineChange = onEngine,
                onGeminiApiKeyChange = {},
                onGeminiModelChange = {},
                onSpeakTranslationsChange = onSpeak,
                onShowSourceOnGlassesChange = {},
                onDownloadModel = onDownload,
                onDeleteModel = onDelete,
            )
        }
    }

    @Test
    fun geminiFields_onlyShownForGeminiEngine() {
        setContent(Settings(engine = EngineKind.ON_DEVICE))
        composeTestRule.onNodeWithText("Gemini API key").assertDoesNotExist()
    }

    @Test
    fun selectingGemini_invokesCallback_andShowsMissingKeyHint() {
        var chosen: EngineKind? = null
        setContent(Settings(engine = EngineKind.GEMINI), onEngine = { chosen = it })
        composeTestRule.onNodeWithText("Gemini API key").assertIsDisplayed()
        composeTestRule.onNodeWithText("Add a Gemini API key to use the Gemini engine.").assertIsDisplayed()
        composeTestRule.onNodeWithText("On-device (ML Kit)").performClick()
        assertEquals(EngineKind.ON_DEVICE, chosen)
    }

    @Test
    fun speakSwitch_invokesCallback() {
        var value: Boolean? = null
        setContent(Settings(speakTranslations = false), onSpeak = { value = it })
        composeTestRule.onNodeWithText("Speak translations aloud").performScrollTo()
        // The first switch on the screen is "Speak translations aloud".
        composeTestRule.onAllNodes(isToggleable())[0].performClick()
        assertEquals(true, value)
    }

    @Test
    fun modelRows_reflectStatus_andButtonsInvokeCallbacks() {
        var downloaded: Language? = null
        var deleted: Language? = null
        setContent(
            Settings(),
            models = mapOf(
                Language.FRENCH to ModelStatus.Downloaded,
                Language.GERMAN to ModelStatus.Failed("No network"),
                Language.JAPANESE to ModelStatus.Downloading,
            ),
            onDownload = { downloaded = it },
            onDelete = { deleted = it },
        )
        composeTestRule.onNodeWithText("No network").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Downloading…").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Delete").performScrollTo().performClick()
        assertEquals(Language.FRENCH, deleted)
        composeTestRule.onAllNodesWithText("Download")[0].performScrollTo().performClick()
        assertEquals(Language.ENGLISH, downloaded)
    }

    @Test
    fun backArrow_isHiddenWhenSideBySideWithHome() {
        setContent(Settings(), showBack = false)
        composeTestRule.onNodeWithContentDescription("Back").assertDoesNotExist()
    }

    @Test
    fun backArrow_isShownAndInvokesOnBack() {
        var backs = 0
        setContent(Settings(), showBack = true, onBack = { backs++ })
        composeTestRule.onNodeWithContentDescription("Back").performClick()
        assertEquals(1, backs)
    }
}
