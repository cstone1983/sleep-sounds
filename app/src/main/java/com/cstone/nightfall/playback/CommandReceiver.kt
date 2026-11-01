package com.cstone.nightfall.playback

import android.app.ForegroundServiceStartNotAllowedException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.cstone.nightfall.connect.Connections
import com.cstone.nightfall.connect.Reporter

/** The entry point for automations. See docs/AUTOMATION.md for the commands. */
class CommandReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    val command = intent.action.orEmpty().substringAfterLast('.')
    if (intent.getStringExtra("key") != Connections.automationKey(context)) {
      Log.w("Nightfall", "rejected $command: wrong or missing key")
      return
    }
    val service = Intent(context, PlaybackService::class.java).putExtras(intent).apply { removeExtra("key") }
    when (command) {
      "STOP" -> Player.stop()
      "TOGGLE" -> Player.togglePause()
      "ADD15" -> Player.addTime()
      "START" -> start(context, service.putExtra(PlaybackService.EXTRA_AUTOMATION, true))
    }
  }

  private fun start(context: Context, service: Intent) {
    try {
      context.startForegroundService(service)
    } catch (e: ForegroundServiceStartNotAllowedException) {
      Log.w("Nightfall", "background start denied: ${e.message}")
      Connections.recordStart(context, "Blocked by battery limits")
      val pending = goAsync()
      Reporter.send(context, "start_failed", mapOf("ok" to false, "reason" to "background_start_denied")) { pending.finish() }
    }
  }
}
