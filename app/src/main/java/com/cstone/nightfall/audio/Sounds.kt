package com.cstone.nightfall.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

const val SAMPLE_RATE = 48_000

enum class Category(val label: String) { RAIN("Rain"), NATURE("Nature"), NOISE("Noise") }

class Sound(val id: String, val name: String, val category: Category, val icon: String, val make: () -> Gen)

/** One sample at a time, so every sound is generated live and never loops. */
fun interface Gen {
  fun next(): Float
}

private const val ICON_RAIN = "M7 15a4 4 0 0 1 .5-8A5 5 0 0 1 17 8a3.5 3.5 0 0 1 0 7H7ZM9 18v2M13 18v2M17 18v2"

val SOUNDS = listOf(
  Sound("heavy_rain", "Heavy rain", Category.RAIN, ICON_RAIN) { Rain(0.9f, 70f) },
  Sound("light_rain", "Light rain", Category.RAIN, "M7 15a4 4 0 0 1 .5-8A5 5 0 0 1 17 8a3.5 3.5 0 0 1 0 7H7ZM12 18v2") { Rain(0.4f, 18f) },
  Sound("thunder", "Thunder", Category.RAIN, "M13 3 6 14h5l-1 7 7-11h-5l1-7Z") { Thunder() },
  Sound("roof", "Rain on roof", Category.RAIN, "M3 13 12 6l9 7M6 11v8h12v-8") { Muffled(Rain(0.8f, 45f), 0.12f) },
  Sound("tent", "Rain on tent", Category.RAIN, "M12 4 3 20h18L12 4ZM12 4v16M9 20l3-6 3 6") { Rain(0.55f, 110f) },
  Sound("drip", "Drip", Category.RAIN, "M12 3s6 6.5 6 11a6 6 0 0 1-12 0c0-4.5 6-11 6-11Z") { Drip() },
  Sound("ocean", "Ocean", Category.NATURE, "M3 9c3-3 6-3 9 0s6 3 9 0M3 15c3-3 6-3 9 0s6 3 9 0") { Ocean() },
  Sound("creek", "Creek", Category.NATURE, "M3 7c3-2 6-2 9 0s6 2 9 0M3 12c3-2 6-2 9 0s6 2 9 0M3 17c3-2 6-2 9 0s6 2 9 0") { Creek() },
  Sound("wind", "Wind", Category.NATURE, "M3 9h11a3 3 0 1 0-3-3M3 15h15a3 3 0 1 1-3 3") { Wind() },
  Sound("fire", "Fire", Category.NATURE, "M12 3c1 4 6 6 6 11a6 6 0 0 1-12 0c0-2 1-4 3-5 0 2 1 3 2 3 1-3-1-6 1-9Z") { Fire() },
  Sound("crickets", "Crickets", Category.NATURE, "M12 6c3 0 4 3 4 6s-1 8-4 8-4-5-4-8 1-6 4-6ZM8 11l-4-2M16 11l4-2M8 15l-4 2M16 15l4 2M10 6 8 3M14 6l2-3") { Crickets() },
  Sound("birds", "Night birds", Category.NATURE, "M12 3 6 12h3l-4 6h14l-4-6h3L12 3ZM12 18v3") { NightBirds() },
  Sound("white", "White noise", Category.NOISE, "M3 12h3l2-7 3 14 3-11 2 7 2-3h3") { Scaled(White(), 0.35f) },
  Sound("pink", "Pink noise", Category.NOISE, "M3 12h4l2-4 3 8 3-6 2 2h4") { Scaled(Pink(), 0.6f) },
  Sound("brown", "Brown noise", Category.NOISE, "M3 13c2-3 4-3 6 0s4 3 6 0 4-3 6 0") { Scaled(Brown(), 0.9f) },
  Sound("fan", "Fan", Category.NOISE, "M12 10a2 2 0 1 0 0 4 2 2 0 0 0 0-4ZM12 10c-1-4 1-7 4-6 2 1 1 5-2 7M14 12c4-1 7 1 6 4-1 2-5 1-7-2M12 14c1 4-1 7-4 6-2-1-1-5 2-7M10 12c-4 1-7-1-6-4 1-2 5-1 7 2") { Fan() },
  Sound("train", "Train", Category.NOISE, "M7 3h10a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2ZM5 11h14M8 21l2-4M16 21l-2-4") { Train() },
  Sound("hum", "Low hum", Category.NOISE, "M3 12h3M9 8v8M13 5v14M17 8v8M21 12h-1") { Hum() },
)

fun soundById(id: String) = SOUNDS.firstOrNull { it.id == id }

/** Fast random numbers: the audio thread needs millions of these a minute. */
class Rng(seed: Long = System.nanoTime()) {
  private var state = seed.toInt().let { if (it == 0) 0x2F6E2B1 else it }

  fun unit(): Float {
    state = state xor (state shl 13)
    state = state xor (state ushr 17)
    state = state xor (state shl 5)
    return (state ushr 9) * (1f / 8_388_608f)
  }

  fun bipolar() = unit() * 2f - 1f

  /** True on average [perSecond] times a second. */
  fun chance(perSecond: Float) = unit() < perSecond / SAMPLE_RATE
}

class LowPass(private val coefficient: Float) {
  private var state = 0f
  fun step(input: Float): Float {
    state += coefficient * (input - state)
    return state
  }
}

class HighPass(coefficient: Float) {
  private val low = LowPass(coefficient)
  fun step(input: Float) = input - low.step(input)
}

/** A short decaying tone or noise hit, used for drops, crackles and clicks. */
private class Hit(private val rng: Rng) {
  private var level = 0f
  private var decay = 0f
  private var phase = 0f
  private var step = 0f
  private var drop = 1f
  private var tone = 0f

  fun fire(amplitude: Float, seconds: Float, hertz: Float = 0f, chirp: Float = 1f) {
    level = amplitude
    decay = 1f - 6f / (seconds * SAMPLE_RATE)
    step = hertz / SAMPLE_RATE
    drop = chirp
    tone = if (hertz > 0f) 1f else 0f
    phase = 0f
  }

  fun next(): Float {
    if (level < 0.0005f) return 0f
    val body = if (tone > 0f) {
      phase += step
      step *= drop
      if (phase >= 1f) phase -= 1f
      fastSine(phase)
    } else {
      rng.bipolar()
    }
    val out = body * level
    level *= decay
    return out
  }
}

/** Parabolic sine for a phase of 0 to 1. Plenty for low-level background sound. */
private fun fastSine(phase: Float): Float {
  val x = phase * 2f - 1f
  return 4f * x * (1f - abs(x))
}

/** Slow random movement between 0 and 1, so nothing sits perfectly still. */
private class Wander(private val rng: Rng, private val secondsPerChange: Float) {
  private var value = rng.unit()
  private var target = rng.unit()
  private var counter = 0
  fun next(): Float {
    if (counter-- <= 0) {
      counter = 4_800
      target = rng.unit()
    }
    value += (target - value) * (0.1f / secondsPerChange) / 10f
    return value
  }
}

class Scaled(private val source: Gen, private val gain: Float) : Gen {
  override fun next() = source.next() * gain
}

class Muffled(private val source: Gen, coefficient: Float) : Gen {
  private val filter = LowPass(coefficient)
  override fun next() = filter.step(source.next()) * 1.8f
}

class White(private val rng: Rng = Rng()) : Gen {
  override fun next() = rng.bipolar()
}

/** Paul Kellet's economy pink filter. */
class Pink(private val rng: Rng = Rng()) : Gen {
  private var b0 = 0f
  private var b1 = 0f
  private var b2 = 0f
  override fun next(): Float {
    val white = rng.bipolar()
    b0 = 0.99765f * b0 + white * 0.099046f
    b1 = 0.963f * b1 + white * 0.2965164f
    b2 = 0.57f * b2 + white * 1.0526913f
    return (b0 + b1 + b2 + white * 0.1848f) * 0.2f
  }
}

class Brown(private val rng: Rng = Rng()) : Gen {
  private var last = 0f
  override fun next(): Float {
    last = (last + 0.02f * rng.bipolar()) / 1.02f
    return last * 3.5f
  }
}

class Rain(private val body: Float, private val dropsPerSecond: Float) : Gen {
  private val rng = Rng()
  private val hiss = HighPass(0.06f)
  private val shape = LowPass(0.55f)
  private val swell = Wander(rng, 6f)
  private val hits = Array(8) { Hit(rng) }
  private var nextHit = 0

  override fun next(): Float {
    if (rng.chance(dropsPerSecond)) {
      hits[nextHit].fire(0.1f + rng.unit() * 0.28f, 0.008f + rng.unit() * 0.03f)
      nextHit = (nextHit + 1) % hits.size
    }
    var drops = 0f
    for (hit in hits) drops += hit.next()
    val bed = shape.step(hiss.step(rng.bipolar())) * body * (0.26f + swell.next() * 0.08f)
    return bed + drops * 0.45f
  }
}

class Thunder : Gen {
  private val rng = Rng()
  private val rumble = Brown(rng)
  private val filter = LowPass(0.012f)
  private var envelope = 0f
  private var target = 0f
  private var samplesLeft = SAMPLE_RATE * 6

  override fun next(): Float {
    if (samplesLeft-- <= 0) {
      if (target > 0f) {
        target = 0f
        samplesLeft = (SAMPLE_RATE * (18 + rng.unit() * 40)).toInt()
      } else {
        target = 0.6f + rng.unit() * 0.4f
        samplesLeft = (SAMPLE_RATE * (2 + rng.unit() * 5)).toInt()
      }
    }
    envelope += (target - envelope) * if (target > envelope) 0.00008f else 0.00002f
    return filter.step(rumble.next()) * envelope * 4f
  }
}

class Drip : Gen {
  private val rng = Rng()
  private val hits = Array(3) { Hit(rng) }
  private var nextHit = 0
  override fun next(): Float {
    if (rng.chance(1.4f)) {
      hits[nextHit].fire(0.3f + rng.unit() * 0.2f, 0.14f, 700f + rng.unit() * 900f, 1.00004f)
      nextHit = (nextHit + 1) % hits.size
    }
    return hits[0].next() + hits[1].next() + hits[2].next()
  }
}

class Ocean : Gen {
  private val rng = Rng()
  private val low = Brown(rng)
  private val high = Pink(rng)
  private val soften = LowPass(0.2f)
  private var phase = rng.unit()
  private val period = Wander(rng, 12f)

  override fun next(): Float {
    phase += (0.07f + period.next() * 0.06f) / SAMPLE_RATE
    if (phase >= 1f) phase -= 1f
    val wave = sin(phase * 2.0 * PI).toFloat() * 0.5f + 0.5f
    val swell = wave * wave
    return low.next() * (0.28f + swell * 0.42f) + soften.step(high.next()) * swell * 0.7f
  }
}

class Creek : Gen {
  private val rng = Rng()
  private val flow = HighPass(0.08f)
  private val shape = LowPass(0.3f)
  private val movement = Wander(rng, 1.5f)
  private val hits = Array(5) { Hit(rng) }
  private var nextHit = 0

  override fun next(): Float {
    if (rng.chance(14f)) {
      hits[nextHit].fire(0.05f + rng.unit() * 0.08f, 0.05f, 500f + rng.unit() * 1200f, 1.00012f)
      nextHit = (nextHit + 1) % hits.size
    }
    var bubbles = 0f
    for (hit in hits) bubbles += hit.next()
    return shape.step(flow.step(rng.bipolar())) * (0.25f + movement.next() * 0.3f) + bubbles
  }
}

class Wind : Gen {
  private val rng = Rng()
  private val noise = Pink(rng)
  private val gust = Wander(rng, 5f)
  private var low = 0f
  private var band = 0f

  override fun next(): Float {
    val strength = gust.next()
    val cutoff = 0.008f + strength * 0.03f
    val input = noise.next()
    low += cutoff * band
    band += cutoff * (input - low - 0.35f * band)
    return band * (0.5f + strength * 1.3f) * 0.9f
  }
}

class Fire : Gen {
  private val rng = Rng()
  private val roar = Brown(rng)
  private val filter = LowPass(0.05f)
  private val flicker = Wander(rng, 1f)
  private val hits = Array(6) { Hit(rng) }
  private var nextHit = 0

  override fun next(): Float {
    if (rng.chance(5f)) {
      val big = rng.unit() < 0.2f
      hits[nextHit].fire(if (big) 0.7f else 0.2f + rng.unit() * 0.2f, if (big) 0.03f else 0.008f)
      nextHit = (nextHit + 1) % hits.size
    }
    var crackle = 0f
    for (hit in hits) crackle += hit.next()
    return filter.step(roar.next()) * (0.7f + flicker.next() * 0.6f) + crackle * 0.5f
  }
}

class Crickets : Gen {
  private val rng = Rng()
  private var phase = 0f
  private var position = rng.unit() * SAMPLE_RATE
  private val pitch = 4_300f + rng.unit() * 400f

  override fun next(): Float {
    position += 1f
    if (position >= SAMPLE_RATE * 1.1f) position = 0f
    val chirp = if (position < SAMPLE_RATE * 0.42f) {
      val pulse = (position / (SAMPLE_RATE * 0.06f)) % 1f
      if (pulse < 0.55f) sin(pulse / 0.55f * PI).toFloat() else 0f
    } else {
      0f
    }
    phase += pitch / SAMPLE_RATE
    if (phase >= 1f) phase -= 1f
    return fastSine(phase) * chirp * 0.16f
  }
}

class NightBirds : Gen {
  private val rng = Rng()
  private val air = LowPass(0.04f)
  private val call = Hit(rng)
  private var samplesLeft = SAMPLE_RATE * 3
  private var notes = 0

  override fun next(): Float {
    if (samplesLeft-- <= 0) {
      if (notes > 0) {
        notes--
        call.fire(0.3f, 0.22f, 1_100f + rng.unit() * 400f, 0.99998f)
        samplesLeft = (SAMPLE_RATE * 0.28f).toInt()
      } else {
        notes = 1 + (rng.unit() * 3).toInt()
        samplesLeft = (SAMPLE_RATE * (6 + rng.unit() * 22)).toInt()
      }
    }
    return air.step(rng.bipolar()) * 0.1f + call.next()
  }
}

class Fan : Gen {
  private val rng = Rng()
  private val air = Pink(rng)
  private val filter = LowPass(0.1f)
  private var phase = 0f
  override fun next(): Float {
    phase += 120f / SAMPLE_RATE
    if (phase >= 1f) phase -= 1f
    return filter.step(air.next()) * 1.3f + fastSine(phase) * 0.018f
  }
}

class Train : Gen {
  private val rng = Rng()
  private val rumble = Brown(rng)
  private val filter = LowPass(0.04f)
  private val clack = Hit(rng)
  private var position = 0
  private var beat = 0

  override fun next(): Float {
    if (position-- <= 0) {
      beat = (beat + 1) % 4
      position = (SAMPLE_RATE * if (beat % 2 == 0) 0.62f else 0.16f).toInt()
      clack.fire(0.35f, 0.07f, 190f, 0.9999f)
    }
    return filter.step(rumble.next()) * 1.1f + clack.next()
  }
}

class Hum : Gen {
  private val rng = Rng()
  private val beat = Wander(rng, 8f)
  private var phaseA = 0f
  private var phaseB = 0f
  override fun next(): Float {
    phaseA += 60f / SAMPLE_RATE
    phaseB += (120f + beat.next() * 0.8f) / SAMPLE_RATE
    if (phaseA >= 1f) phaseA -= 1f
    if (phaseB >= 1f) phaseB -= 1f
    return fastSine(phaseA) * 0.34f + fastSine(phaseB) * 0.14f
  }
}

/** Root mean square of a block, used by the tests to prove a sound isn't silent. */
fun rms(samples: FloatArray): Float = sqrt(samples.sumOf { (it * it).toDouble() } / samples.size).toFloat()
