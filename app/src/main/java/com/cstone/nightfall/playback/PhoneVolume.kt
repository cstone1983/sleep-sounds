package com.cstone.nightfall.playback

import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlin.math.roundToInt

/**
 * Sets the phone's media volume for a session and puts it back afterwards.
 * If someone changes it while sound plays, that level is theirs: check() hands it back to be saved,
 * and it isn't put back at the end.
 */
class PhoneVolume(private val audio: AudioManager) {
  private var before = -1
  private var applied = -1
  private var pending = -1
  private var pendingSince = 0L
  private var routeChangedAt = -SETTLE_MS
  private var routes: AudioDeviceCallback? = null
  private var outputs = emptySet<Int>()

  private val max get() = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
  private val current get() = audio.getStreamVolume(AudioManager.STREAM_MUSIC)

  /** percent is of the phone's top media volume. It never goes fully silent. */
  fun set(percent: Int) {
    if (audio.isVolumeFixed || max <= 0) return
    val target = (max * percent.coerceIn(1, 100) / 100f).roundToInt().coerceIn(1, max)
    try {
      if (before < 0) before = current
      audio.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
      applied = current
      Log.i("Nightfall", "phone volume $before -> $applied of $max")
    } catch (e: SecurityException) {
      Log.w("Nightfall", "couldn't set phone volume: $e")
      return
    }
    if (routes == null) {
      // Android reports every device already connected when this is registered, so only a different set counts.
      outputs = outputIds()
      routes = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>) = devicesChanged()
        override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>) = devicesChanged()
      }.also { audio.registerAudioDeviceCallback(it, Handler(Looper.getMainLooper())) }
    }
  }

  private fun outputIds() = audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).map { it.id }.toSet()

  private fun devicesChanged() {
    val now = outputIds()
    if (now == outputs) return
    outputs = now
    routeChanged()
  }

  /**
   * Headphones or a speaker coming and going switch Android to that device's own volume.
   * That isn't a choice anyone made, so it's never saved, and the old level isn't put back on the new device.
   */
  fun routeChanged(now: Long = System.currentTimeMillis()) {
    if (applied < 0) return
    routeChangedAt = now
    before = -1
  }

  /**
   * Call this every second or so. Returns a new level, as a percent, once someone has changed the
   * volume and left it alone for a few seconds. Muting isn't saved: that's a pause, not a level.
   */
  fun check(now: Long = System.currentTimeMillis()): Int? {
    if (applied < 0) return null
    val level = current
    when {
      level == applied -> pending = -1
      now - routeChangedAt < SETTLE_MS -> {
        applied = level
        pending = -1
      }
      level != pending -> {
        pending = level
        pendingSince = now
      }
      now - pendingSince >= SETTLE_MS -> {
        applied = level
        pending = -1
        before = -1
        if (level > 0) {
          Log.i("Nightfall", "phone volume changed to $level of $max, saving it")
          return (level * 100f / max).roundToInt().coerceIn(MIN_PERCENT, 100)
        }
      }
    }
    return null
  }

  fun restore() {
    routes?.let(audio::unregisterAudioDeviceCallback)
    routes = null
    if (before >= 0) {
      try {
        if (current == applied) audio.setStreamVolume(AudioManager.STREAM_MUSIC, before, 0)
      } catch (e: SecurityException) {
        Log.w("Nightfall", "couldn't restore phone volume: $e")
      }
    }
    before = -1
    applied = -1
    pending = -1
  }

  companion object {
    const val SETTLE_MS = 3_000L
    const val MIN_PERCENT = 5
  }
}
