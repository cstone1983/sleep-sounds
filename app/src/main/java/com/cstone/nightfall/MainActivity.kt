package com.cstone.nightfall

import android.Manifest
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.cstone.nightfall.playback.Player
import com.cstone.nightfall.ui.NightfallApp
import com.cstone.nightfall.ui.askToRemoveBatteryLimits

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge(
      statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
      navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
    )
    setContent { NightfallApp() }
    requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
    handleDeepLink(intent)
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    handleDeepLink(intent)
  }

  // Runs after the notification prompt, so the two system dialogs never stack.
  override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    askToRemoveBatteryLimits(this)
  }

  /** nightfall://start is the one-tap route. It works because this screen is visible. */
  private fun handleDeepLink(intent: Intent?) {
    if (intent?.data?.host == "start" && !Player.state.value.active) Player.start(this)
  }
}
