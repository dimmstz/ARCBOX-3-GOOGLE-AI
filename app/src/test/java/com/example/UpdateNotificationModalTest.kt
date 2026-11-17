package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.example.data.update.UpdateReleaseInfo
import com.example.data.update.UpdateStatus
import com.example.ui.components.UpdateNotificationModal
import com.example.ui.theme.ArcboxTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UpdateNotificationModalTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun sampleRelease(mandatory: Boolean = false) = UpdateReleaseInfo(
        tagName = "v9.9.9",
        releaseName = "ArcBox v9.9.9",
        targetVersionCode = 999,
        targetVersionName = "9.9.9",
        changelog = "Melhorias gerais",
        apkDownloadUrl = "https://example.com/app-release.apk",
        apkFileName = "app-release.apk",
        apkSizeBytes = 1024L * 1024L,
        assetApiUrl = null,
        expectedSha256 = null,
        publishedAt = "2026-11-17T00:00:00Z",
        isMandatory = mandatory
    )

    @Test
    fun `cancel and close buttons are always visible and cancel update request`() {
        var cancelClicks = 0
        composeTestRule.setContent {
            ArcboxTheme {
                UpdateNotificationModal(
                    updateStatus = UpdateStatus.UpdateAvailable(sampleRelease(mandatory = true)),
                    onDownloadAndInstall = {},
                    onInstallDownloadedApk = {},
                    onCancelAndDismiss = { cancelClicks++ }
                )
            }
        }

        composeTestRule.onNodeWithTag("update_modal_cancel_button").assertIsDisplayed().performClick()
        composeTestRule.onNodeWithTag("update_modal_close_button").assertIsDisplayed().performClick()
        assertEquals(2, cancelClicks)
    }

    @Test
    fun `cancel and close buttons during download invoke cancel callback`() {
        var cancelClicks = 0
        composeTestRule.setContent {
            ArcboxTheme {
                UpdateNotificationModal(
                    updateStatus = UpdateStatus.Downloading(
                        releaseInfo = sampleRelease(),
                        progressPercent = 42,
                        downloadedBytes = 420_000L,
                        totalBytes = 1_000_000L
                    ),
                    onDownloadAndInstall = {},
                    onInstallDownloadedApk = {},
                    onCancelAndDismiss = { cancelClicks++ }
                )
            }
        }

        composeTestRule.onNodeWithTag("update_modal_cancel_download_button").assertIsDisplayed().performClick()
        composeTestRule.onNodeWithTag("update_modal_close_button").assertIsDisplayed().performClick()
        assertEquals(2, cancelClicks)
    }
}
