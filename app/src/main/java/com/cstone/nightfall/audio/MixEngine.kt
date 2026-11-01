package com.cstone.nightfall.audio

import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioTrack
import com.cstone.nightfall.data.Layer
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.tanh

private const val BLOCK = 4_800

/** Everything the audio thread needs for one sound in the mix. Safe to change while playing. */
private class Voice(val id: String, val gen: Gen) {
  @Volatile var target = 0f
  @Volatile var drift = false
  @Volatile var soften = false
  @Volatile var leaving = false
  var level = 0f
  var phase = Math.random().toFloat()
  val softener = LowPass(0.09f)
}

/** Renders the mix into one block. Separate from the AudioTrack so tests can run it without a device. */
class MixCore {
  private val voices = CopyOnWriteArrayList<Voice>()
  @Volatile var master = 1f
  private var masterNow = 0f

  fun setLayers(layers: List<Layer>) {
    val wanted = layers.associateBy { it.soundId }
    for (voice in voices) {
      val layer = wanted[voice.id]
      voice.leaving = layer == null
      if (layer != null) applyTo(voice, layer)
    }
    for (layer in layers) {
      if (voices.any { it.id == layer.soundId }) continue
      val sound = soundById(layer.soundId) ?: continue
      voices.add(Voice(sound.id, sound.make()).also { applyTo(it, layer) })
    }
  }

  private fun applyTo(voice: Voice, layer: Layer) {
    voice.target = layer.volume / 100f
    voice.drift = layer.drift
    voice.soften = layer.soften
  }

  fun render(out: FloatArray) {
    out.fill(0f)
    for (voice in voices) {
      val goal = if (voice.leaving) 0f else voice.target
      // Drift swells a sound gently over about 40 seconds.
      voice.phase += out.size / (SAMPLE_RATE * 40f)
      if (voice.phase >= 1f) voice.phase -= 1f
      val swing = if (voice.drift) 0.65f + 0.35f * sin(voice.phase * 2.0 * PI).toFloat() else 1f
      val soften = voice.soften
      for (i in out.indices) {
        voice.level += (goal - voice.level) * 0.0004f
        var sample = voice.gen.next()
        if (soften) sample = voice.softener.step(sample) * 1.5f
        out[i] += sample * voice.level * swing
      }
      if (voice.leaving && voice.level < 0.001f) voices.remove(voice)
    }
    for (i in out.indices) {
      masterNow += (master - masterNow) * 0.0002f
      out[i] = tanh(out[i] * masterNow)
    }
  }
}

/** Plays a [MixCore] through one AudioTrack on its own thread. */
class MixEngine(attributes: AudioAttributes) {
  val core = MixCore()
  val track: AudioTrack
  @Volatile private var running = false
  @Volatile var paused = false
  @Volatile var lastWrite = 0
    private set
  private var thread: Thread? = null
  /** Called from the audio thread if a write fails. */
  var onError: ((Int) -> Unit)? = null

  init {
    val format = AudioFormat.Builder()
      .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
      .setSampleRate(SAMPLE_RATE)
      .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
      .build()
    val minimum = AudioTrack.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT)
    track = AudioTrack.Builder()
      .setAudioAttributes(attributes)
      .setAudioFormat(format)
      .setBufferSizeInBytes(maxOf(minimum * 4, BLOCK * 4 * 2))
      .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_POWER_SAVING)
      .setTransferMode(AudioTrack.MODE_STREAM)
      .build()
  }

  fun start() {
    running = true
    track.play()
    thread = Thread({
      val buffer = FloatArray(BLOCK)
      while (running) {
        if (paused) {
          Thread.sleep(50)
          continue
        }
        core.render(buffer)
        val written = track.write(buffer, 0, buffer.size, AudioTrack.WRITE_BLOCKING)
        lastWrite = written
        if (written < 0) {
          onError?.invoke(written)
          return@Thread
        }
      }
    }, "nightfall-mix").apply {
      priority = Thread.MAX_PRIORITY
      start()
    }
  }

  fun pause() {
    paused = true
    track.pause()
  }

  fun resume() {
    track.play()
    paused = false
  }

  fun setOutput(device: AudioDeviceInfo?) {
    track.preferredDevice = device
  }

  fun release() {
    running = false
    thread?.join(500)
    track.stop()
    track.release()
  }
}
