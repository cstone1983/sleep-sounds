package com.cstone.nightfall

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.cstone.nightfall.data.Layer
import com.cstone.nightfall.playback.PlayState
import com.cstone.nightfall.ui.NightfallApp
import com.cstone.nightfall.ui.Route
import com.cstone.nightfall.ui.Tab
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

private const val OUT = "build/screens"
private val PLAYING = PlayState(
  active = true,
  title = "Rain on the tent",
  layers = listOf(Layer("heavy_rain", 70), Layer("thunder", 30), Layer("brown", 45)),
  hasTimer = true,
  leftMs = 38 * 60_000L,
  confirmed = true,
)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w390dp-h844dp-xhdpi")
class ScreensTest {
  @get:Rule val compose = createComposeRule()

  private fun shot(name: String, route: Route, state: PlayState? = null) {
    compose.setContent { NightfallApp(start = route, previewState = state) }
    compose.waitForIdle()
    compose.onRoot().captureRoboImage("$OUT/$name.png")
  }

  @Test fun tonight() = shot("tonight", Route.Main(Tab.TONIGHT))
  @Test fun tonightPlaying() = shot("tonight-playing", Route.Main(Tab.TONIGHT), PLAYING)
  @Test fun mixes() = shot("mixes", Route.Main(Tab.MIXES))
  @Test fun meditate() = shot("meditate", Route.Main(Tab.MEDITATE))
  @Test fun routine() = shot("routine", Route.Main(Tab.ROUTINE))
  @Test fun timer() = shot("timer", Route.Timer)
  @Test fun output() = shot("output", Route.Output)
  @Test fun nowPlaying() = shot("now-playing", Route.NowPlaying, PLAYING)
  @Test fun nightDim() = shot("night-dim", Route.NightDim, PLAYING)
  @Test fun breathe() = shot("breathe", Route.Breathe)
  @Test fun settings() = shot("settings", Route.Main(Tab.SETTINGS))

  @Test
  fun mixerOpensAndTilesToggle() {
    compose.setContent { NightfallApp() }
    compose.onNodeWithText("Change the mix").performClick()
    compose.waitForIdle()
    compose.onNodeWithText("In this mix · 3").assertIsDisplayed()
    compose.onRoot().captureRoboImage("$OUT/mixer.png")
    compose.onNodeWithText("Light rain").performClick()
    compose.waitForIdle()
    compose.onNodeWithText("In this mix · 4").assertIsDisplayed()
    compose.onNodeWithText("Noise").performClick()
    compose.waitForIdle()
    compose.onNodeWithText("White noise").assertIsDisplayed()
    compose.onNodeWithText("Save").performClick()
    compose.waitForIdle()
    compose.onNodeWithText("Change the mix").assertIsDisplayed()
  }

  @Test
  fun timerChoiceShowsOnTonight() {
    compose.setContent { NightfallApp() }
    compose.onNodeWithText("45 min").performClick()
    compose.waitForIdle()
    compose.onNodeWithText("1 hr").performClick()
    compose.waitForIdle()
    compose.onNodeWithText("Done").performClick()
    compose.waitForIdle()
    compose.onNodeWithText("1 hr").assertIsDisplayed()
  }

  @Test
  fun tabsNavigate() {
    compose.setContent { NightfallApp() }
    compose.onNodeWithText("Routine").performClick()
    compose.waitForIdle()
    compose.onNodeWithText("Ready check").assertIsDisplayed()
    compose.onNodeWithText("Settings").performClick()
    compose.waitForIdle()
    compose.onNodeWithText("Test n8n").assertExists()
  }

  @Test
  fun infoPopupsExplainEachConnection() {
    compose.setContent { NightfallApp(start = Route.Main(Tab.SETTINGS)) }
    compose.onNodeWithContentDescription("How to connect Home Assistant").performClick()
    compose.waitForIdle()
    compose.onNodeWithText("Connect Home Assistant").assertIsDisplayed()
    compose.onNode(isDialog()).captureRoboImage("$OUT/help-ha.png")
    compose.onNodeWithText("Close").performClick()
    compose.waitForIdle()
    compose.onNodeWithContentDescription("How to connect n8n").performClick()
    compose.waitForIdle()
    compose.onNodeWithText("Connect n8n").assertIsDisplayed()
    compose.onNode(isDialog()).captureRoboImage("$OUT/help-n8n.png")
  }
}
