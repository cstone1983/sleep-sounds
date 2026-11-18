package com.cstone.nightfall

import com.cstone.nightfall.audio.MixCore
import com.cstone.nightfall.audio.Rng
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
import java.io.File
import kotlin.math.abs
import kotlin.math.sqrt

@RunWith(RobolectricTestRunner::class)
class SoundTest {
  @Test
  fun everySoundIsAudibleBalancedAndStereo() {
    assertEquals(29, SOUNDS.size)
    assertEquals(SOUNDS.size, SOUNDS.map { it.id }.toSet().size)
    val report = StringBuilder()
    Rng.fixedSeed = 20_261_118
    val problems = mutableListOf<String>()
    for (sound in SOUNDS) {
      val source = sound.make()
      val frames = SAMPLE_RATE * 90
      val left = FloatArray(frames)
      val right = FloatArray(frames)
      val block = 4_800
      val a = FloatArray(block)
      val b = FloatArray(block)
      for (start in 0 until frames step block) {
        source.render(a, b, block)
        a.copyInto(left, start)
        b.copyInto(right, start)
      }
      assertTrue("${sound.id} has invalid samples", left.all { it.isFinite() } && right.all { it.isFinite() })
      val level = (rms(left) + rms(right)) / 2
      val peak = maxOf(left.maxOf { abs(it) }, right.maxOf { abs(it) })
      var both = 0.0
      for (i in 0 until frames) both += left[i] * right[i]
      val alike = both / (frames * rms(left).toDouble() * rms(right) + 1e-12)
      val mean = left.average()
      // The loudest second, so sounds made of rare events are judged by the event and not the gaps.
      var loudest = 0f
      for (start in 0 until frames - SAMPLE_RATE step SAMPLE_RATE) {
        var sum = 0.0
        for (i in start until start + SAMPLE_RATE) sum += left[i] * left[i] + right[i] * right[i]
        loudest = maxOf(loudest, sqrt(sum / (SAMPLE_RATE * 2)).toFloat())
      }
      report.append("%-11s rms %.3f loudest %.3f peak %.2f alike %.2f dc %.4f\n".format(sound.id, level, loudest, peak, alike, mean))
      if (level <= 0.004f) problems += "${sound.id} is silent, rms=$level"
      if (loudest <= 0.05f) problems += "${sound.id} is too quiet at its loudest: $loudest"
      if (loudest >= 0.3f) problems += "${sound.id} is too loud at its loudest: $loudest"
      if (peak >= 1.3f) problems += "${sound.id} peaks at $peak"
      // A heartbeat belongs in the middle.
      if (alike >= 0.97 && sound.id != "heartbeat") problems += "${sound.id} is the same in both ears: $alike"
      if (abs(mean) >= 0.01) problems += "${sound.id} sits off centre: $mean"
    }
    Rng.fixedSeed = 0
    File("build/sound-levels.txt").apply { parentFile?.mkdirs() }.writeText(report.toString())
    assertTrue(problems.joinToString("; "), problems.isEmpty())
  }

  @Test
  fun mixStaysBelowFullScaleWithEverythingOn() {
    val core = MixCore()
    core.setLayers(SOUNDS.map { Layer(it.id, 100) })
    val block = FloatArray(9_600)
    repeat(200) {
      core.render(block)
      assertTrue(block.all { it.isFinite() && abs(it) <= 1f })
    }
  }

  @Test
  fun aTypicalMixRendersFarFasterThanItPlays() {
    val core = MixCore()
    core.setLayers(listOf("heavy_rain", "thunder", "ocean", "crickets", "bowls", "trees").map { Layer(it, 60) })
    val block = FloatArray(9_600)
    repeat(20) { core.render(block) }
    val began = System.nanoTime()
    repeat(300) { core.render(block) }
    val seconds = (System.nanoTime() - began) / 1e9
    File("build/sound-speed.txt").writeText("30 s of a six sound mix took %.2f s\n".format(seconds))
    assertTrue("30 seconds of sound took $seconds s to make", seconds < 6.0)
  }

  @Test
  fun removedLayersFadeAway() {
    val core = MixCore()
    core.setLayers(listOf(Layer("brown", 100)))
    val block = FloatArray(9_600)
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
