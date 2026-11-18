package com.cstone.nightfall.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

const val SAMPLE_RATE = 48_000

/** Fast random numbers: the audio thread needs millions of these a minute. */
class Rng(seed: Long = nextSeed()) {
  companion object {
    /** Tests set this so every run makes the same sound. Left at 0, every sound is different each time. */
    @Volatile var fixedSeed = 0L
    private var handedOut = 0L

    @Synchronized private fun nextSeed(): Long =
      if (fixedSeed == 0L) System.nanoTime() xor (Math.random() * Long.MAX_VALUE).toLong()
      else fixedSeed + (++handedOut) * 0x9E3779B97F4A7C15uL.toLong()
  }

  private var state = (seed xor (seed ushr 32)).toInt().let { if (it == 0) 0x2F6E2B1 else it }

  fun unit(): Float {
    state = state xor (state shl 13)
    state = state xor (state ushr 17)
    state = state xor (state shl 5)
    return (state ushr 9) * (1f / 8_388_608f)
  }

  fun bipolar() = unit() * 2f - 1f

  fun between(low: Float, high: Float) = low + unit() * (high - low)

  /** True on average [perSecond] times a second. */
  fun chance(perSecond: Float) = unit() < perSecond / SAMPLE_RATE
}

private const val TABLE = 4_096
private val SINE = FloatArray(TABLE + 2) { sin(it * 2.0 * PI / TABLE).toFloat() }

/** A clean sine for a phase of 0 to 1, read from a table with interpolation. */
fun sine(phase: Float): Float {
  val position = (phase - phase.toInt()) * TABLE
  val index = position.toInt()
  val rest = position - index
  return SINE[index] + (SINE[index + 1] - SINE[index]) * rest
}

/** Left and right gains for a position from -1 (left) to 1 (right), at equal loudness. */
fun panLeft(pan: Float) = cos((pan + 1f) * 0.25f * PI.toFloat())
fun panRight(pan: Float) = sin((pan + 1f) * 0.25f * PI.toFloat())

class LowPass(hertz: Float) {
  private val coefficient = (1.0 - Math.exp(-2.0 * PI * hertz / SAMPLE_RATE)).toFloat()
  private var state = 0f
  fun step(input: Float): Float {
    state += coefficient * (input - state)
    return state
  }
}

class HighPass(hertz: Float) {
  private val low = LowPass(hertz)
  fun step(input: Float) = input - low.step(input)
}

/** A two-pole filter with a real slope and resonance, for shaping noise into something recognisable. */
class Biquad {
  private var b0 = 1f
  private var b1 = 0f
  private var b2 = 0f
  private var a1 = 0f
  private var a2 = 0f
  private var x1 = 0f
  private var x2 = 0f
  private var y1 = 0f
  private var y2 = 0f

  fun lowPass(hertz: Float, q: Float = 0.707f) = apply { set(hertz, q, 0) }
  fun highPass(hertz: Float, q: Float = 0.707f) = apply { set(hertz, q, 1) }
  fun bandPass(hertz: Float, q: Float = 1f) = apply { set(hertz, q, 2) }

  private fun set(hertz: Float, q: Float, kind: Int) {
    val w = 2.0 * PI * hertz.coerceIn(10f, SAMPLE_RATE * 0.45f) / SAMPLE_RATE
    val alpha = sin(w) / (2.0 * q)
    val c = cos(w)
    val a0 = 1.0 + alpha
    when (kind) {
      0 -> { b0 = ((1 - c) / 2 / a0).toFloat(); b1 = ((1 - c) / a0).toFloat(); b2 = b0 }
      1 -> { b0 = ((1 + c) / 2 / a0).toFloat(); b1 = (-(1 + c) / a0).toFloat(); b2 = b0 }
      else -> { b0 = (alpha / a0).toFloat(); b1 = 0f; b2 = -b0 }
    }
    a1 = (-2 * c / a0).toFloat()
    a2 = ((1 - alpha) / a0).toFloat()
  }

  fun step(input: Float): Float {
    val out = b0 * input + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
    x2 = x1
    x1 = input
    y2 = y1
    y1 = if (out > -1e-20f && out < 1e-20f) 0f else out
    return y1
  }
}

/** Slow random movement between 0 and 1, so nothing sits perfectly still. */
class Wander(private val rng: Rng, seconds: Float) {
  private val hold = (SAMPLE_RATE * seconds * 0.5f).toInt().coerceAtLeast(1)
  private val rate = 3f / (SAMPLE_RATE * seconds)
  private var value = rng.unit()
  private var target = rng.unit()
  private var counter = 0
  fun next(): Float {
    if (counter-- <= 0) {
      counter = hold
      target = rng.unit()
    }
    value += (target - value) * rate
    return value
  }
}

/** Paul Kellet's refined pink filter: within a fraction of a decibel of true pink across the hearing range. */
class Pink(private val rng: Rng) {
  private var b0 = 0f
  private var b1 = 0f
  private var b2 = 0f
  private var b3 = 0f
  private var b4 = 0f
  private var b5 = 0f
  private var b6 = 0f
  fun next(): Float {
    val white = rng.bipolar()
    b0 = 0.99886f * b0 + white * 0.0555179f
    b1 = 0.99332f * b1 + white * 0.0750759f
    b2 = 0.96900f * b2 + white * 0.1538520f
    b3 = 0.86650f * b3 + white * 0.3104856f
    b4 = 0.55000f * b4 + white * 0.5329522f
    b5 = -0.7616f * b5 - white * 0.0168980f
    val out = b0 + b1 + b2 + b3 + b4 + b5 + b6 + white * 0.5362f
    b6 = white * 0.115926f
    return out * 0.11f
  }
}

class Brown(private val rng: Rng) {
  private var last = 0f
  fun next(): Float {
    last = (last + 0.02f * rng.bipolar()) / 1.02f
    return last * 3.5f
  }
}

/**
 * A short decaying tone or noise burst placed somewhere between the speakers.
 * Used for drops, bubbles, crackles and taps. Read [left] and [right] after [next].
 */
class Ping(private val rng: Rng) {
  private var level = 0f
  private var decay = 0f
  private var attack = 1f
  private var rise = 0f
  private var phase = 0f
  private var step = 0f
  private var bend = 1f
  private var tone = false
  private var colour = 0f
  private var smooth = 0f
  var left = 0.7f
    private set
  var right = 0.7f
    private set
  val idle get() = level < 0.0004f

  /**
   * hertz = 0 makes a noise burst, and [dull] from 0 to 1 takes the edge off it.
   * [bend] above 1 slides a tone upwards, as a bubble does. [soft] is a fade-in in seconds.
   */
  fun fire(amplitude: Float, seconds: Float, hertz: Float = 0f, bend: Float = 1f, pan: Float = 0f, dull: Float = 0f, soft: Float = 0f) {
    level = amplitude
    decay = 1f - 6.9f / (seconds * SAMPLE_RATE)
    step = hertz / SAMPLE_RATE
    this.bend = bend
    tone = hertz > 0f
    phase = 0f
    colour = 1f - dull.coerceIn(0f, 0.98f)
    attack = if (soft > 0f) 0f else 1f
    rise = if (soft > 0f) 1f / (soft * SAMPLE_RATE) else 0f
    left = panLeft(pan)
    right = panRight(pan)
  }

  fun next(): Float {
    if (level < 0.0004f) return 0f
    val body = if (tone) {
      phase += step
      step *= bend
      if (phase >= 1f) phase -= 1f
      sine(phase)
    } else {
      smooth += colour * (rng.bipolar() - smooth)
      smooth
    }
    if (attack < 1f) attack = (attack + rise).coerceAtMost(1f)
    val out = body * level * attack
    level *= decay
    return out
  }
}

/** A bank of pings, handing out whichever is quietest. */
class Pings(rng: Rng, count: Int) {
  private val all = Array(count) { Ping(rng) }
  private var cursor = 0
  var left = 0f
    private set
  var right = 0f
    private set

  fun take(): Ping {
    repeat(all.size) {
      cursor = (cursor + 1) % all.size
      if (all[cursor].idle) return all[cursor]
    }
    cursor = (cursor + 1) % all.size
    return all[cursor]
  }

  /** Advances every ping by one sample and sums them into [left] and [right]. */
  fun step() {
    var l = 0f
    var r = 0f
    for (ping in all) {
      if (ping.idle) continue
      val value = ping.next()
      l += value * ping.left
      r += value * ping.right
    }
    left = l
    right = r
  }
}

/** A sine partial that rings and dies away, for bowls and chimes. Never clicks: it starts at zero. */
class Partial {
  private var phase = 0f
  private var step = 0f
  private var level = 0f
  private var decay = 0f
  private var attack = 0f
  val idle get() = level < 0.0002f

  fun strike(hertz: Float, amplitude: Float, seconds: Float) {
    step = hertz / SAMPLE_RATE
    level = amplitude
    decay = 1f - 6.9f / (seconds * SAMPLE_RATE)
    attack = 0f
  }

  fun next(): Float {
    if (level < 0.0002f) return 0f
    phase += step
    if (phase >= 1f) phase -= 1f
    if (attack < 1f) attack = (attack + 0.004f).coerceAtMost(1f)
    val out = sine(phase) * level * attack
    level *= decay
    return out
  }
}

private class Delay(length: Int) {
  private val buffer = FloatArray(length)
  private var index = 0
  fun read() = buffer[index]
  fun write(value: Float) {
    buffer[index] = value
    if (++index == buffer.size) index = 0
  }
}

/**
 * A small stereo room. Four feedback delays per side with slightly different lengths, so the
 * echo spreads between the speakers. [seconds] is roughly how long it rings.
 */
class Reverb(seconds: Float, private val damping: Float = 0.35f) {
  private val lengths = intArrayOf(1_687, 1_931, 2_203, 2_473)
  private val leftLines = Array(4) { Delay(lengths[it]) }
  private val rightLines = Array(4) { Delay(lengths[it] + 37 + it * 11) }
  private val leftTone = FloatArray(4)
  private val rightTone = FloatArray(4)
  private val feedback = FloatArray(4) { Math.pow(0.001, lengths[it] / (seconds * SAMPLE_RATE).toDouble()).toFloat() }
  private val leftSpread = arrayOf(Delay(347), Delay(113))
  private val rightSpread = arrayOf(Delay(359), Delay(127))
  var left = 0f
    private set
  var right = 0f
    private set

  /** Feed one sample in. The echo alone, without the original, is left in [left] and [right]. */
  fun step(inLeft: Float, inRight: Float) {
    left = side(inLeft + inRight * 0.3f, leftLines, leftTone, leftSpread)
    right = side(inRight + inLeft * 0.3f, rightLines, rightTone, rightSpread)
  }

  private fun side(input: Float, lines: Array<Delay>, tone: FloatArray, spread: Array<Delay>): Float {
    var sum = 0f
    for (i in 0 until 4) {
      val out = lines[i].read()
      tone[i] += (1f - damping) * (out - tone[i])
      if (tone[i] > -1e-20f && tone[i] < 1e-20f) tone[i] = 0f
      lines[i].write(input + tone[i] * feedback[i])
      sum += out
    }
    var out = sum * 0.25f
    for (stage in spread) {
      val delayed = stage.read()
      val mixed = out + delayed * 0.5f
      stage.write(mixed)
      out = delayed - mixed * 0.5f
    }
    return out
  }
}

/** Root mean square of a block, used by the tests to prove a sound isn't silent. */
fun rms(samples: FloatArray): Float = sqrt(samples.sumOf { (it * it).toDouble() } / samples.size).toFloat()
