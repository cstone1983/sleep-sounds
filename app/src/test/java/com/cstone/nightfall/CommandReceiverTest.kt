package com.cstone.nightfall

import android.app.Application
import android.content.Intent
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cstone.nightfall.connect.Connections
import com.cstone.nightfall.playback.CommandReceiver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
class CommandReceiverTest {
  private val app = ApplicationProvider.getApplicationContext<Application>()

  private fun sendStart(key: String) {
    app.sendBroadcast(Intent("${app.packageName}.START").setClass(app, CommandReceiver::class.java).putExtra("key", key))
    shadowOf(Looper.getMainLooper()).idle()
  }

  @Test
  fun startRunsWhenRemoteControlIsOn() {
    sendStart(Connections.automationKey(app))
    assertNotNull(shadowOf(app).nextStartedService)
  }

  @Test
  fun startDeadEndsWhenRemoteControlIsOff() {
    Connections.setRemoteAllowed(app, false)
    sendStart(Connections.automationKey(app))
    assertNull(shadowOf(app).nextStartedService)
    assertEquals("Ignored, remote control off", Connections.lastStart(app))
  }

  @Test
  fun wrongKeyIsRejectedBeforeRemoteControlIsChecked() {
    Connections.setRemoteAllowed(app, false)
    sendStart("wrong")
    assertNull(shadowOf(app).nextStartedService)
    assertEquals("", Connections.lastStart(app))
  }
}
