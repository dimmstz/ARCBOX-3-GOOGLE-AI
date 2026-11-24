package com.example

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.autofill.AutofillTree
import androidx.compose.ui.autofill.AutofillType
import androidx.compose.ui.platform.LocalAutofillTree
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.ui.components.CloudProvider
import com.example.ui.components.OAuthCloudConnectModal
import com.example.ui.theme.ArcboxTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OAuthCloudConnectModalTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @OptIn(ExperimentalComposeUiApi::class)
    @Test
    fun `displays renamed user password label, removes clutter, and supports autofill nodes`() {
        val customAutofillTree = AutofillTree()

        composeTestRule.setContent {
            ArcboxTheme {
                CompositionLocalProvider(LocalAutofillTree provides customAutofillTree) {
                    OAuthCloudConnectModal(
                        provider = CloudProvider.MEGA,
                        onAuthorize = { _, _, _ -> },
                        onDismiss = {}
                    )
                }
            }
        }

        // Renamed password label and clean card assertions
        composeTestRule.onNodeWithText("Senha do usuário").assertIsDisplayed()
        composeTestRule.onNodeWithText("Senha da conta ou Senha de App").assertDoesNotExist()
        composeTestRule.onNodeWithText("Usar a Senha Salva do Google").assertDoesNotExist()
        composeTestRule.onNodeWithText("Criar Pasta Segura Offline (MEGA)").assertDoesNotExist()

        // Verify AutofillNodes registered for Email/Username and Password
        val emailNode = customAutofillTree.children.values.firstOrNull {
            it.autofillTypes.contains(AutofillType.EmailAddress) &&
                it.autofillTypes.contains(AutofillType.Username)
        }
        val passwordNode = customAutofillTree.children.values.firstOrNull {
            it.autofillTypes.contains(AutofillType.Password)
        }
        assertNotNull("Email/Username AutofillNode should be registered", emailNode)
        assertNotNull("Password AutofillNode should be registered", passwordNode)

        // Simulate Android Autofill filling both nodes
        composeTestRule.runOnUiThread {
            customAutofillTree.performAutofill(emailNode!!.id, "usuario@mega.nz")
            customAutofillTree.performAutofill(passwordNode!!.id, "MinhaSenha123")
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("cloud_email_input").assertTextContains("usuario@mega.nz")

        // Switch to Chave / Token tab: credential autofill nodes should be unregistered cleanly
        composeTestRule.onNodeWithTag("cloud_tab_token").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("cloud_token_input").assertIsDisplayed()
        assertEquals(0, customAutofillTree.children.size)

        // Switch back to E-mail e Senha tab: autofill nodes registered again
        composeTestRule.onNodeWithTag("cloud_tab_credentials").performClick()
        composeTestRule.waitForIdle()
        assertTrue(customAutofillTree.children.size >= 2)
    }
}
