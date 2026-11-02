package com.cstone.nightfall.playback

import android.content.Context
import android.content.Intent
import android.media.AudioDeviceInfo
import com.cstone.nightfall.data.Layer
import com.cstone.nightfall.data.Speaker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class PlayState(
  val active: Boolean = false,
  val paused: Boolean = false,
  val title: String = "",
  val layers: List<Layer> = emptyList(),
  val hasTimer: Boolean = false,
  val leftMs: Long = 0,
  val confirmed: Boolean = false,
  val guideTitle: String = "",
  val voiceLeftMs: Long = 0,
  val voiceTotalMs: Long = 0,
  val soundLevel: Int = 45,
  val voiceLevel: Int = 80,
  val speaker: String = "",
)

/** The UI's view of playback. The service publishes here, and the UI sends commands through it. */
object Player {
  private val _state = MutableStateFlow(PlayState())
  val state: StateFlow<PlayState> = _state
  internal var service: PlaybackService? = null

  internal fun publish(value: PlayState) {
    _state.value = value
  }

  fun start(context: Context, seconds: Int? = null) {
    val intent = Intent(context, PlaybackService::class.java)
    seconds?.let { intent.putExtra(PlaybackService.EXTRA_SECONDS, it) }
    context.startForegroundService(intent)
  }

  /** Plays a guided session, over whatever's already playing or as a new session. */
  fun startGuided(context: Context, id: String) {
    val running = service
    if (running != null) {
      running.startGuided(id)
      return
    }
    context.startForegroundService(Intent(context, PlaybackService::class.java).putExtra(PlaybackService.EXTRA_SESSION, id))
  }

  fun setSoundLevel(level: Int) { service?.setSoundLevel(level) }
  fun setVoiceLevel(level: Int) { service?.setVoiceLevel(level) }
  fun stop() { service?.stopFromUser() }
  fun togglePause() { service?.togglePause() }
  fun addTime() { service?.addTime() }
  fun updateLayers(layers: List<Layer>) { service?.updateLayers(layers) }
  fun setOutput(device: AudioDeviceInfo?) { service?.setOutput(device) }
  fun setSpeaker(speaker: Speaker?) { service?.setSpeaker(speaker) }
}
