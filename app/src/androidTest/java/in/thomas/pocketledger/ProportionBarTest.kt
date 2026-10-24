package `in`.thomas.pocketledger

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ProportionBarTest {
    @get:Rule val ui = createComposeRule()

    @Test fun dataBarsHaveProportionalFillAndNoTrackGapOrZeroValueDot() {
        val progress = mutableFloatStateOf(0f)
        ui.setContent { ProportionBar(progress.floatValue, Color.Red, Color.Blue, Modifier.size(200.dp, 4.dp).testTag("bar")) }
        for (fraction in listOf(0f, .01f, .25f, .6f, 1f)) {
            ui.runOnIdle { progress.floatValue = fraction }
            val pixels = ui.onNodeWithTag("bar").captureToImage().toPixelMap()
            val center = pixels.height / 2
            val filled = (0 until pixels.width).count { pixels[it, center] == Color.Red }
            assertEquals("Filled length for $fraction", pixels.width * fraction, filled.toFloat(), 2f)
            if (fraction < 1f) {
                val trackStart = (pixels.width * fraction).toInt() + 2
                assertTrue((trackStart until pixels.width - 2).all { pixels[it, center] == Color.Blue })
            }
        }
    }
}
