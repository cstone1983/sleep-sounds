package com.cstone.nightfall.playback

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import com.cstone.nightfall.audio.MixEngine
import com.cstone.nightfall.data.Layer
import com.cstone.nightfall.data.Store
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Plays one sound by itself in the mixer. It only runs on screen, so it needs no service. */
object Preview {
  private val _playing = MutableStateFlow<String?>(null)
  val playing: StateFlow<String?> = _playing
  private var engine: MixEngine? = null
  private var audio: AudioManager? = null
  private var focus: AudioFocusRequest? = null

  fun toggle(context: Context, layer: Layer) {
    val current = _playing.value
    stop()
    if (current != layer.soundId) play(context.applicationContext, layer)
  }

  private fun play(context: Context, layer: Layer) {
    val manager = context.getSystemService(AudioManager::class.java)
    val attributes = AudioAttributes.Builder()
      .setUsage(AudioAttributes.USAGE_MEDIA)
      .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
      .build()
    // Anything else taking the audio ends the preview, including a start from an automation.
    val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
      .setAudioAttributes(attributes)
      .setOnAudioFocusChangeListener { stop() }
      .build()
    if (manager.requestAudioFocus(request) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return
    audio = manager
    focus = request
    val tonight = Store.get(context).tonight.value
    engine = MixEngine(attributes).also {
      it.core.setLayers(listOf(layer))
      it.core.master = tonight.gain / 100f
      manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull { device -> device.id == tonight.outputId }?.let(it::setOutput)
      it.start()
    }
    _playing.value = layer.soundId
  }

  /** Follows changes to the mix, so the slider works while previewing. A removed sound stops. */
  fun sync(layers: List<Layer>) {
    val id = _playing.value ?: return
    val layer = layers.firstOrNull { it.soundId == id }
    if (layer == null) stop() else engine?.core?.setLayers(listOf(layer))
  }

  fun stop() {
    _playing.value = null
    engine?.release()
    engine = null
    focus?.let { audio?.abandonAudioFocusRequest(it) }
    focus = null
  }
}
