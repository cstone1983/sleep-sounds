package com.cstone.nightfall

import com.cstone.nightfall.audio.MixCore
import com.cstone.nightfall.audio.SAMPLE_RATE
import com.cstone.nightfall.audio.SOUNDS
import com.cstone.nightfall.audio.rms
import com.cstone.nightfall.data.Layer
import com.cstone.nightfall.data.Mix
import com.cstone.nightfall.data.Store
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
class SoundTest {
  @Test
  fun everySoundIsAudibleAndStaysInRange() {
    assertEquals(18, SOUNDS.size)
    for (sound in SOUNDS) {
      val gen = sound.make()
      val samples = FloatArray(SAMPLE_RATE * 60) { gen.next() }
      assertTrue("${sound.id} has invalid samples", samples.all { it.isFinite() })
      assertTrue("${sound.id} is silent, rms=${rms(samples)}", rms(samples) > 0.002f)
      val peak = samples.maxOf { abs(it) }
      assertTrue("${sound.id} peaks at $peak", peak < 3f)
    }
  }

  @Test
  fun mixStaysBelowFullScaleWithEverythingOn() {
    val core = MixCore()
    core.setLayers(SOUNDS.map { Layer(it.id, 100) })
    val block = FloatArray(4_800)
    repeat(200) {
      core.render(block)
      assertTrue(block.all { it.isFinite() && abs(it) <= 1f })
    }
  }

  @Test
  fun removedLayersFadeAway() {
    val core = MixCore()
    core.setLayers(listOf(Layer("brown", 100)))
    val block = FloatArray(4_800)
    repeat(20) { core.render(block) }
    assertTrue(rms(block) > 0.01f)
    core.setLayers(emptyList())
    repeat(20) { core.render(block) }
    assertTrue("still audible: ${rms(block)}", rms(block) < 0.001f)
  }

  @Test
  fun mixesSurviveSaving() {
    val mixes = listOf(Mix("a", "Test", listOf(Layer("fan", 40, drift = true, soften = true))))
    assertEquals(mixes, Store.decode(Store.encode(mixes)))
  }
}
