package com.princeyadav.grayout.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.princeyadav.grayout.ui.theme.GrayoutTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GrayoutToggleSemanticsTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun exposesSwitchRoleAndToggleState() {
        var checked by mutableStateOf(false)

        composeRule.setContent {
            GrayoutTheme {
                GrayoutToggle(
                    checked = checked,
                    onCheckedChange = { checked = it },
                    contentDescription = "Grayscale",
                )
            }
        }

        composeRule.onNode(hasContentDescription("Grayscale") and hasSwitchRole())
            .assert(hasToggleableState(ToggleableState.Off))
            .assert(hasStateDescription("Off"))
            .performClick()

        composeRule.onNode(hasContentDescription("Grayscale") and hasSwitchRole())
            .assert(hasToggleableState(ToggleableState.On))
            .assert(hasStateDescription("On"))
    }

    @Test
    fun keepsMinimumInteractiveTarget() {
        lateinit var density: Density

        composeRule.setContent {
            density = LocalDensity.current
            GrayoutTheme {
                GrayoutToggle(
                    checked = false,
                    onCheckedChange = {},
                    contentDescription = "Grayscale",
                )
            }
        }

        val node = composeRule.onNode(hasContentDescription("Grayscale")).fetchSemanticsNode()
        val minTargetPx = with(density) { 48.dp.roundToPx() }

        assertTrue(node.size.width >= minTargetPx)
        assertTrue(node.size.height >= minTargetPx)
    }

    private fun hasSwitchRole(): SemanticsMatcher =
        SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch)

    private fun hasToggleableState(state: ToggleableState): SemanticsMatcher =
        SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, state)

    private fun hasStateDescription(description: String): SemanticsMatcher =
        SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, description)
}
