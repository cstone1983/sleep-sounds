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
  fun aLevelChosenWhilePlayingIsKept() {
    audio.setStreamVolume(AudioManager.STREAM_MUSIC, max, 0)
    val volume = PhoneVolume(audio)
    volume.set(40)
    assertNull(volume.check(0))
    audio.setStreamVolume(AudioManager.STREAM_MUSIC, Math.round(max * 0.6f), 0)
    assertNull("saved before it settled", volume.check(1_000))
    assertNull(volume.check(2_000))
    assertEquals(60, volume.check(4_000))
    assertNull("saved twice", volume.check(5_000))
    volume.restore()
    assertEquals(Math.round(max * 0.6f), now)
  }

  @Test
  fun aLevelPassedThroughOnTheWayIsNotKept() {
    audio.setStreamVolume(AudioManager.STREAM_MUSIC, max, 0)
    val volume = PhoneVolume(audio)
    volume.set(40)
    audio.setStreamVolume(AudioManager.STREAM_MUSIC, Math.round(max * 0.6f), 0)
    assertNull(volume.check(1_000))
    audio.setStreamVolume(AudioManager.STREAM_MUSIC, Math.round(max * 0.2f), 0)
    assertNull(volume.check(3_500))
    assertNull(volume.check(5_000))
    assertEquals(20, volume.check(6_500))
  }

  @Test
  fun aNewDevicesVolumeIsNotKept() {
    audio.setStreamVolume(AudioManager.STREAM_MUSIC, max, 0)
    val volume = PhoneVolume(audio)
    volume.set(40)
    volume.routeChanged(10_000)
    audio.setStreamVolume(AudioManager.STREAM_MUSIC, 2, 0)
    assertNull(volume.check(11_000))
    assertNull(volume.check(20_000))
    volume.restore()
    assertEquals("the speaker's level was replaced with the phone's", 2, now)
  }

  @Test
  fun mutingIsNotKept() {
    audio.setStreamVolume(AudioManager.STREAM_MUSIC, max, 0)
    val volume = PhoneVolume(audio)
    volume.set(40)
    audio.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
    assertNull(volume.check(1_000))
    assertNull(volume.check(5_000))
    volume.restore()
    assertEquals(0, now)
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
