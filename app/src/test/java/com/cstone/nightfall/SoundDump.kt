package com.cstone.nightfall

import com.cstone.nightfall.audio.Rng
import com.cstone.nightfall.audio.SAMPLE_RATE
import com.cstone.nightfall.audio.SOUNDS
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

private const val SECONDS = 60
private const val WINDOW = 8_192
private val BANDS = listOf(20 to 60, 60 to 125, 125 to 250, 250 to 500, 500 to 1_000, 1_000 to 2_000, 2_000 to 4_000, 4_000 to 8_000, 8_000 to 16_000)

/**
 * Nobody can listen during a build, so this measures every sound instead, and writes
 * build/sound-analysis.txt. With NIGHTFALL_DUMP=wav it also writes each sound to build/sound-wav.
 * Skipped unless NIGHTFALL_DUMP is set.
 *
 * The STATIC column is the one to watch. A sound scores high when it is bright, steady, smooth and
 * has no pitch all at once, which is what a detuned radio is. Real rain, surf and wind each break
 * at least one of those.
 */
class SoundDump {
  @Test
  fun measureEverySound() {
    val mode = System.getenv("NIGHTFALL_DUMP")
    assumeTrue(mode != null)
    val report = StringBuilder()
    report.append("energy in each band, dB of the total%n".format())
    report.append("%-11s %s | high%%  tilt  move  spiky  pitch  STATIC%n".format("sound", BANDS.joinToString(" ") { "%5s".format(if (it.first < 1_000) "${it.first}" else "${it.first / 1_000}k") }))
    Rng.fixedSeed = 20_261_118
    for (sound in SOUNDS) {
      val source = sound.make()
      val frames = SAMPLE_RATE * SECONDS
      val left = FloatArray(frames)
      val right = FloatArray(frames)
      val a = FloatArray(4_800)
      val b = FloatArray(4_800)
      for (start in 0 until frames step 4_800) {
        source.render(a, b, 4_800)
        a.copyInto(left, start)
        b.copyInto(right, start)
      }
      if (mode == "wav") writeWav(sound.id, left, right)
      val mono = DoubleArray(frames) { (left[it] + right[it]) * 0.5 }
      report.append(measure(sound.id, mono))
    }
    Rng.fixedSeed = 0
    File("build/sound-analysis.txt").apply { parentFile?.mkdirs() }.writeText(report.toString())
  }

  private fun measure(name: String, mono: DoubleArray): String {
    val power = DoubleArray(WINDOW / 2)
    val hann = DoubleArray(WINDOW) { 0.5 - 0.5 * cos(2 * PI * it / WINDOW) }
    var windows = 0
    val re = DoubleArray(WINDOW)
    val im = DoubleArray(WINDOW)
    for (start in 0 until mono.size - WINDOW step WINDOW / 2) {
      for (i in 0 until WINDOW) {
        re[i] = mono[start + i] * hann[i]
        im[i] = 0.0
      }
      fft(re, im)
      for (i in power.indices) power[i] += re[i] * re[i] + im[i] * im[i]
      windows++
    }
    fun bin(hertz: Int) = (hertz.toLong() * WINDOW / SAMPLE_RATE).toInt()
    fun sum(low: Int, high: Int): Double {
      var total = 0.0
      for (i in bin(low) until bin(high)) total += power[i]
      return total
    }
    val total = sum(20, 16_000) + 1e-30
    val share = BANDS.map { sum(it.first, it.second) / total }
    val db = share.map { 10 * log10(it + 1e-9) }
    val high = (share[7] + share[8]) * 100
    // Slope across the four octaves from 500 Hz to 8 kHz, in dB per octave.
    val tilt = (-1.5 * db[4] - 0.5 * db[5] + 0.5 * db[6] + 1.5 * db[7]) / 5
    // How much the loudness moves: the spread of 100 ms loudness readings, in dB.
    val hop = SAMPLE_RATE / 10
    val levels = (0 until mono.size - hop step hop).map { start ->
      var energy = 0.0
      for (i in start until start + hop) energy += mono[i] * mono[i]
      20 * log10(sqrt(energy / hop) + 1e-6)
    }
    val mean = levels.average()
    val move = sqrt(levels.sumOf { (it - mean) * (it - mean) } / levels.size)
    var square = 0.0
    var fourth = 0.0
    for (value in mono) {
      square += value * value
      fourth += value * value * value * value
    }
    val spiky = fourth / mono.size / ((square / mono.size) * (square / mono.size) + 1e-30)
    // Pitch: 0 for pure noise, towards 1 as energy gathers at particular frequencies.
    var logs = 0.0
    var plain = 0.0
    var count = 0
    for (i in bin(200) until bin(8_000)) {
      logs += ln(power[i] + 1e-30)
      plain += power[i]
      count++
    }
    val pitch = 1 - exp(logs / count) / (plain / count + 1e-30)
    val static = (min(high, 40.0) / 40) * max(0.0, 1 - move / 4) * max(0.0, 1 - (spiky - 3) / 4) * (1 - pitch)
    return "%-11s %s | %5.0f %5.1f %5.1f %6.1f %6.2f  %.2f%s%n".format(
      name, db.joinToString(" ") { "%5.0f".format(it) }, high, tilt, move, spiky, pitch, static, if (static > 0.2) "  <<<" else "",
    )
  }

  private fun fft(re: DoubleArray, im: DoubleArray) {
    val n = re.size
    var j = 0
    for (i in 1 until n) {
      var bit = n shr 1
      while (j and bit != 0) {
        j = j xor bit
        bit = bit shr 1
      }
      j = j xor bit
      if (i < j) {
        val r = re[i]; re[i] = re[j]; re[j] = r
        val m = im[i]; im[i] = im[j]; im[j] = m
      }
    }
    var length = 2
    while (length <= n) {
      val angle = -2 * PI / length
      val stepRe = cos(angle)
      val stepIm = sin(angle)
      for (start in 0 until n step length) {
        var wRe = 1.0
        var wIm = 0.0
        for (k in 0 until length / 2) {
          val p = start + k
          val q = p + length / 2
          val tRe = re[q] * wRe - im[q] * wIm
          val tIm = re[q] * wIm + im[q] * wRe
          re[q] = re[p] - tRe
          im[q] = im[p] - tIm
          re[p] += tRe
          im[p] += tIm
          val next = wRe * stepRe - wIm * stepIm
          wIm = wRe * stepIm + wIm * stepRe
          wRe = next
        }
      }
      length = length shl 1
    }
  }

  private fun writeWav(name: String, left: FloatArray, right: FloatArray) {
    val bytes = ByteBuffer.allocate(44 + left.size * 4).order(ByteOrder.LITTLE_ENDIAN)
    bytes.put("RIFF".toByteArray()).putInt(bytes.capacity() - 8).put("WAVEfmt ".toByteArray())
    bytes.putInt(16).putShort(1).putShort(2).putInt(SAMPLE_RATE).putInt(SAMPLE_RATE * 4).putShort(4).putShort(16)
    bytes.put("data".toByteArray()).putInt(bytes.capacity() - 44)
    for (i in left.indices) {
      bytes.putShort((left[i].coerceIn(-1f, 1f) * 32_767).toInt().toShort())
      bytes.putShort((right[i].coerceIn(-1f, 1f) * 32_767).toInt().toShort())
    }
    File("build/sound-wav").apply { mkdirs() }.resolve("$name.wav").writeBytes(bytes.array())
  }
}
