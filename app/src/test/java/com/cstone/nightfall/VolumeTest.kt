package com.cstone.nightfall

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cstone.nightfall.playback.PhoneVolume
import com.cstone.nightfall.playback.PlaybackService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VolumeTest {
  private val audio = ApplicationProvider.getApplicationContext<Context>().getSystemService(AudioManager::class.java)
  private val max get() = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
  private val now get() = audio.getStreamVolume(AudioManager.STREAM_MUSIC)

  @Test
  fun setsAShareOfFullVolumeAndPutsItBack() {
    audio.setStreamVolume(AudioManager.STREAM_MUSIC, max, 0)
    val volume = PhoneVolume(audio)
    volume.set(40)
    assertEquals(Math.round(max * 0.4f), now)
    volume.restore()
    assertEquals(max, now)
  }

  @Test
  fun aMutedPhoneIsTurnedUp() {
    audio.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
    PhoneVolume(audio).set(1)
    assertEquals(1, now)
  }

  @Test
  fun leavesALevelSomeoneChose() {
    audio.setStreamVolume(AudioManager.STREAM_MUSIC, max, 0)
    val volume = PhoneVolume(audio)
    volume.set(40)
    audio.setStreamVolume(AudioManager.STREAM_MUSIC, 2, 0)
    volume.restore()
    assertEquals(2, now)
  }

  @Test
  fun readsNumbersSentAsText() {
    val intent = Intent().putExtra("secs", "1800").putExtra("gain", 30).putExtra("volume", " 25 ").putExtra("mix", "rain")
    assertEquals(1800, PlaybackService.intExtra(intent, "secs"))
    assertEquals(30, PlaybackService.intExtra(intent, "gain"))
    assertEquals(25, PlaybackService.intExtra(intent, "volume"))
    assertNull(PlaybackService.intExtra(intent, "mix"))
    assertNull(PlaybackService.intExtra(intent, "missing"))
    assertNull(PlaybackService.intExtra(null, "secs"))
  }
}
