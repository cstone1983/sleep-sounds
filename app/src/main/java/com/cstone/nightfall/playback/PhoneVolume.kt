package com.cstone.nightfall.playback

import android.media.AudioManager
import android.util.Log
import kotlin.math.roundToInt

/**
 * Sets the phone's media volume for a session and puts it back afterwards.
 * It isn't put back if someone changed it in the meantime, because then the new level is theirs.
 */
class PhoneVolume(private val audio: AudioManager) {
  private var before = -1
  private var applied = -1

  /** percent is of the phone's top media volume. It never goes fully silent. */
  fun set(percent: Int) {
    if (audio.isVolumeFixed) return
    val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    if (max <= 0) return
    val target = (max * percent.coerceIn(1, 100) / 100f).roundToInt().coerceIn(1, max)
    try {
      if (before < 0) before = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
      audio.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
      applied = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
      Log.i("Nightfall", "phone volume $before -> $applied of $max")
    } catch (e: SecurityException) {
      Log.w("Nightfall", "couldn't set phone volume: $e")
    }
  }

  fun restore() {
    if (before < 0) return
    try {
      if (audio.getStreamVolume(AudioManager.STREAM_MUSIC) == applied) {
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, before, 0)
      }
    } catch (e: SecurityException) {
      Log.w("Nightfall", "couldn't restore phone volume: $e")
    }
    before = -1
    applied = -1
  }
}
