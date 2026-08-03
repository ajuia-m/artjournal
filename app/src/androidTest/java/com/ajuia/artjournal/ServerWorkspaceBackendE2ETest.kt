package com.ajuia.artjournal

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ServerWorkspaceBackendE2ETest {
    @get:Rule val composeRule = createEmptyComposeRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        assumeTrue(
            "Real backend E2E is opt-in; pass -PARTJOURNAL_E2E_ENABLED=true.",
            BuildConfig.ARTJOURNAL_E2E_ENABLED
        )
        clearClientState()
    }

    @After
    fun tearDown() {
        clearClientState()
    }

    @Test
    fun loginSchoolSelectionAndEncryptedSessionRestoreUseRealBackend() {
        var scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            composeRule.onNodeWithTag("workspace-server").performClick()
            waitForText("Сервер школы")

            login(password = "invalid-password")
            waitForText("Неверный логин или пароль.")
            composeRule.onNodeWithText("Войти заново").performClick()

            login(password = PASSWORD)
            waitForText("Выберите школу")
            composeRule.onNodeWithText("Art Journal E2E School").performClick()
            waitForText("Сервер подключён")
            composeRule.onNodeWithText("Преподаватель").assertIsDisplayed()
            composeRule.onNodeWithText("Валюта: RUB").assertIsDisplayed()
            assertRefreshTokenIsEncryptedAtRest()

            scenario.close()
            scenario = ActivityScenario.launch(MainActivity::class.java)
            waitForText("Сервер подключён")
            composeRule.onNodeWithText("Art Journal E2E School").assertIsDisplayed()

            composeRule.onNodeWithText("Выйти").performClick()
            waitForText("Сервер школы")
            assertFalse(
                context.getSharedPreferences("server_session_secure", Context.MODE_PRIVATE)
                    .contains("refresh_token")
            )
        } finally {
            scenario.close()
        }
    }

    private fun login(password: String) {
        composeRule.onNodeWithTag("server-username").performTextInput(USERNAME)
        composeRule.onNodeWithTag("server-password").performTextInput(password)
        composeRule.onNodeWithTag("server-login").performClick()
    }

    private fun waitForText(text: String) {
        composeRule.waitUntil(timeoutMillis = 30_000) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(text).assertIsDisplayed()
    }

    private fun clearClientState() {
        listOf("workspace_mode", "server_session_secure", "server_workspace").forEach { name ->
            context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
        }
    }

    private fun assertRefreshTokenIsEncryptedAtRest() {
        val preferences = context.getSharedPreferences("server_session_secure", Context.MODE_PRIVATE)
        val encryptedToken = preferences.getString("refresh_token", null)
        assertNotNull(encryptedToken)
        assertTrue(preferences.contains("refresh_token_iv"))
        assertFalse("JWT must not be stored as plaintext.", encryptedToken!!.startsWith("eyJ"))
    }

    private companion object {
        const val USERNAME = "android-e2e-teacher"
        const val PASSWORD = "AndroidE2E-password-2026"
    }
}
