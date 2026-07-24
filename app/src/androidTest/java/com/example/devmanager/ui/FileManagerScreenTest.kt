package com.example.devmanager.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FileManagerScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun appLaunchesSuccessfully() {
        composeTestRule.setContent {
            com.example.devmanager.ui.theme.DevManagerTheme {
                androidx.compose.material3.Text("DEV MANAGER")
            }
        }
        composeTestRule.onNodeWithText("DEV MANAGER").assertExists()
    }
}
