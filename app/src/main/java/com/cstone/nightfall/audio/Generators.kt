package com.cstone.nightfall.audio

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.pow

/** Fills stereo blocks. Every generated sound is made live, so it never loops. */
fun interface Source {
  fun render(left: FloatArray, right: FloatArray, count: Int)
}

/** A source worked out one stereo sample at a time. Set [l] and [r] in [frame]. */
abstract class Stereo : Source {
  protected var l = 0f
  protected var r = 0f
  protected abstract fun frame()
  final override fun render(left: FloatArray, right: FloatArray, count: Int) {
    for (i in 0 until count) {
      frame()
      left[i] = l
      right[i] = r
    }
  }
}

enum class Colour { WHITE, PINK, BROWN }

/** Steady noise. Part of it is shared between the speakers and part isn't, so it's wide without a hole in the middle. */
class Noise(private val colour: Colour, private val gain: Float) : Stereo() {
  private val rng = Rng()
  private val pink = Array(3) { Pink(rng) }
  private val brown = Array(3) { Brown(rng) }

  private fun one(i: Int) = when (colour) {
    Colour.WHITE -> rng.bipolar()
    Colour.PINK -> pink[i].next()
    Colour.BROWN -> brown[i].next()
  }

  override fun frame() {
    val middle = one(0) * 0.55f
    l = (middle + one(1) * 0.83f) * gain
    r = (middle + one(2) * 0.83f) * gain
  }
}

/**
 * Rain is the patter of countless small drops plus the nearer ones you can pick out.
 * [density] is how many of the small ones land each second: fewer sounds sparser and more "pattery".
 * The surface it lands on decides the rest: [bedLow] to [bedHigh] is the band of the hiss,
 * and the drops are noise bursts ([dropDull]) with an optional ring ([toneLow] to [toneHigh]).
 */
class Rain(
  private val bed: Float,
  bedLow: Float,
  bedHigh: Float,
  private val roar: Float,
  private val dropsPerSecond: Float,
  private val dropGain: Float,
  private val dropSeconds: Float,
  private val dropDull: Float,
  private val toneLow: Float = 0f,
  private val toneHigh: Float = 0f,
  density: Float = 3_000f,
) : Stereo() {
  private val rng = Rng()
  // The hiss is thousands of separate tiny impacts a second, not smooth noise: smooth noise is static.
  private val chance = density / SAMPLE_RATE
  private val grain = kotlin.math.sqrt(0.95f / chance)
  private val wash = Array(2) { Pink(rng) }

  private fun impacts(side: Int): Float {
    val far = wash[side].next() * 0.7f
    if (rng.unit() >= chance) return far
    val size = rng.unit()
    return far + rng.bipolar() * (0.45f + size * size * size) * grain
  }
  private val lowCut = Array(2) { Biquad().highPass(bedLow) }
  private val highCut = Array(2) { Biquad().lowPass(bedHigh) }
  private val rumble = Array(2) { Brown(rng) }
  private val rumbleCut = Array(2) { LowPass(320f) }
  private val swell = Wander(rng, 9f)
  private val lean = Wander(rng, 5f)
  private val drops = Pings(rng, 14)

  override fun frame() {
    if (rng.chance(dropsPerSecond)) {
      val size = rng.unit()
      val pan = rng.bipolar() * 0.9f
      drops.take().fire(dropGain * (0.15f + size * size * 0.85f), dropSeconds * (0.5f + rng.unit()), pan = pan, dull = dropDull)
      if (toneHigh > 0f && rng.unit() < 0.6f) {
        drops.take().fire(dropGain * 0.5f * (0.2f + size), dropSeconds * 0.8f, rng.between(toneLow, toneHigh), pan = pan)
      }
    }
    drops.step()
    val level = bed * (0.75f + swell.next() * 0.5f)
    val tilt = (lean.next() - 0.5f) * 0.25f
    val left = highCut[0].step(lowCut[0].step(impacts(0))) + rumbleCut[0].step(rumble[0].next()) * roar
    val right = highCut[1].step(lowCut[1].step(impacts(1))) + rumbleCut[1].step(rumble[1].next()) * roar
    l = left * level * (1f - tilt) + drops.left
    r = right * level * (1f + tilt) + drops.right
  }
}

/** A crack if it's close, then a long roll that swells and fades as it echoes off the sky. */
class Thunder : Stereo() {
  private val rng = Rng()
  private val rumble = Array(2) { Brown(rng) }
  private val rumbleCut = Array(2) { Biquad().lowPass(120f) }
  private val crackCut = Biquad().lowPass(2_000f)
  private val echo = Reverb(3.2f, 0.6f)
  private var wait = (SAMPLE_RATE * rng.between(3f, 8f)).toInt()
  private var rolling = 0
  private var nextRoll = 0
  private var strength = 0f
  private var target = 0f
  private var envelope = 0f
  private var crack = 0f
  private var pan = 0f

  override fun frame() {
    if (wait-- <= 0) {
      val near = rng.unit()
      strength = 0.45f + near * 0.55f
      crack = near * near * 0.9f
      pan = rng.bipolar() * 0.6f
      crackCut.lowPass(500f + near * 3_500f)
      for (cut in rumbleCut) cut.lowPass(80f + near * 140f)
      rolling = (SAMPLE_RATE * rng.between(4f, 10f)).toInt()
      wait = rolling + (SAMPLE_RATE * rng.between(14f, 45f)).toInt()
      nextRoll = 0
    }
    if (rolling > 0) {
      rolling--
      if (nextRoll-- <= 0) {
        target = strength * rng.between(0.35f, 1f)
        strength *= 0.82f
        nextRoll = (SAMPLE_RATE * rng.between(0.3f, 1.3f)).toInt()
      }
    } else {
      target = 0f
    }
    envelope += (target - envelope) * if (target > envelope) 0.0006f else 0.00003f
    val snap = crackCut.step(rng.bipolar()) * crack
    crack *= 0.99988f
    echo.step(snap * panLeft(pan), snap * panRight(pan))
    l = rumbleCut[0].step(rumble[0].next()) * envelope * 5f + snap * panLeft(pan) * 0.5f + echo.left * 0.8f
    r = rumbleCut[1].step(rumble[1].next()) * envelope * 5f + snap * panRight(pan) * 0.5f + echo.right * 0.8f
  }
}

/** One breaker: it gathers, crashes, then drags back over the sand with a fizz. */
private class Wave(private val rng: Rng, private val pan: Float) {
  private val body = Brown(rng)
  private val bodyCut = Biquad().lowPass(420f)
  private val wash = Pink(rng)
  private val washCut = Biquad().highPass(700f)
  private val fizzCut = Biquad().highPass(5_500f)
  private var position = rng.unit()
  private var step = 0f
  private var size = 1f
  var left = 0f
  var right = 0f

  init { again() }

  private fun again() {
    step = 1f / (SAMPLE_RATE * rng.between(8f, 14f))
    size = rng.between(0.55f, 1f)
  }

  fun step() {
    position += step
    if (position >= 1f) {
      position -= 1f
      again()
    }
    val t = position
    val swell: Float
    val bright: Float
    val fizz: Float
    if (t < 0.38f) {
      val x = t / 0.38f
      swell = x * x * x
      bright = x * x * 0.5f
      fizz = 0f
    } else {
      val x = (t - 0.38f) / 0.62f
      swell = exp(-x * 5f)
      bright = 0.5f + 0.5f * exp(-x * 9f)
      fizz = x.coerceAtMost(0.08f) / 0.08f * exp(-x * 3.2f)
    }
    val value = (bodyCut.step(body.next()) * swell * 1.5f +
      washCut.step(wash.next()) * swell * bright * 1.6f +
      fizzCut.step(rng.bipolar()) * fizz * 0.22f) * size
    // The crash sweeps a little along the beach.
    val place = pan + (t - 0.5f) * 0.5f
    left = value * panLeft(place.coerceIn(-1f, 1f))
    right = value * panRight(place.coerceIn(-1f, 1f))
  }
}

class Ocean : Stereo() {
  private val rng = Rng()
  private val waves = arrayOf(Wave(rng, -0.45f), Wave(rng, 0.45f), Wave(rng, 0f))
  private val far = Array(2) { Brown(rng) }
  private val farCut = Array(2) { LowPass(240f) }

  override fun frame() {
    var left = farCut[0].step(far[0].next()) * 0.3f
    var right = farCut[1].step(far[1].next()) * 0.3f
    for (wave in waves) {
      wave.step()
      left += wave.left
      right += wave.right
    }
    l = left * 0.75f
    r = right * 0.75f
  }
}

/**
 * Running water is a rush plus thousands of bubbles, and each bubble is a tiny rising note.
 * More and higher bubbles make a brook. No bubbles and a wide rush make a waterfall.
 */
class Water(
  private val rush: Float,
  rushLow: Float,
  rushHigh: Float,
  private val rumble: Float,
  private val bubblesPerSecond: Float,
  private val bubbleLow: Float,
  private val bubbleHigh: Float,
  private val bubbleGain: Float,
) : Stereo() {
  private val rng = Rng()
  private val noise = Array(2) { Pink(rng) }
  private val lowCut = Array(2) { Biquad().highPass(rushLow) }
  private val highCut = Array(2) { Biquad().lowPass(rushHigh) }
  private val deep = Array(2) { Brown(rng) }
  private val deepCut = Array(2) { LowPass(150f) }
  private val move = Array(2) { Wander(rng, 2.5f) }
  private val bubbles = Pings(rng, 20)

  override fun frame() {
    if (bubblesPerSecond > 0f && rng.chance(bubblesPerSecond)) {
      val small = rng.unit()
      val hertz = bubbleLow + small * small * (bubbleHigh - bubbleLow)
      bubbles.take().fire(
        bubbleGain * rng.between(0.2f, 1f) * (1.2f - small),
        0.07f - small * 0.05f,
        hertz,
        1f + rng.between(0.00003f, 0.00014f),
        rng.bipolar() * 0.85f,
        soft = 0.002f,
      )
    }
    bubbles.step()
    l = highCut[0].step(lowCut[0].step(noise[0].next())) * rush * (0.7f + move[0].next() * 0.6f) +
      deepCut[0].step(deep[0].next()) * rumble + bubbles.left
    r = highCut[1].step(lowCut[1].step(noise[1].next())) * rush * (0.7f + move[1].next() * 0.6f) +
      deepCut[1].step(deep[1].next()) * rumble + bubbles.right
  }
}

/** Single drops into still water, with the room they fall in. A long [room] makes it a cave. */
class Drips(private val perSecond: Float, room: Float, private val wet: Float, private val low: Float, private val high: Float) : Stereo() {
  private val rng = Rng()
  private val drops = Pings(rng, 6)
  private val echo = Reverb(room, 0.3f)

  override fun frame() {
    if (rng.chance(perSecond)) {
      val pan = rng.bipolar() * 0.7f
      val hertz = rng.between(low, high)
      drops.take().fire(rng.between(0.25f, 0.5f), rng.between(0.07f, 0.14f), hertz, 1.00005f, pan, soft = 0.001f)
      drops.take().fire(0.08f, 0.004f, pan = pan, dull = 0.4f)
      if (rng.unit() < 0.3f) drops.take().fire(0.3f, 0.12f, hertz * 0.32f, 0.99998f, pan, soft = 0.004f)
    }
    drops.step()
    echo.step(drops.left, drops.right)
    l = drops.left + echo.left * wet
    r = drops.right + echo.right * wet
  }
}

/** Wind is noise squeezed through a moving gap: the pitch rises as it gusts. [leaves] adds the rustle of trees. */
class Wind(private val leaves: Float) : Stereo() {
  private val rng = Rng()
  private val gust = Wander(rng, 8f)
  private val side = Array(2) { Wander(rng, 4f) }
  private val air = Array(2) { Pink(rng) }
  private val howl = Array(2) { Biquad() }
  private val deep = Array(2) { Brown(rng) }
  private val deepCut = Array(2) { LowPass(140f) }
  private val leafCut = Array(2) { Biquad().highPass(2_600f) }
  private val flutter = Array(2) { LowPass(18f) }
  private val strength = FloatArray(2)
  private var tick = 0

  override fun frame() {
    val shared = gust.next()
    if (tick-- <= 0) {
      tick = 96
      for (i in 0..1) {
        strength[i] = (shared * 0.75f + side[i].next() * 0.25f).coerceIn(0f, 1f)
        howl[i].bandPass(160f + strength[i] * strength[i] * 620f, 1.1f + strength[i] * 1.4f)
      }
    } else {
      side[0].next()
      side[1].next()
    }
    for (i in 0..1) {
      val s = strength[i]
      var out = howl[i].step(air[i].next()) * (0.5f + s * 2.6f) + deepCut[i].step(deep[i].next()) * s * 0.5f
      if (leaves > 0f) {
        val grain = flutter[i].step(abs(rng.bipolar())) * 2f
        out = out * 0.45f + leafCut[i].step(rng.bipolar()) * grain * (0.12f + s * s) * leaves
      }
      if (i == 0) l = out else r = out
    }
  }
}

class Fire : Stereo() {
  private val rng = Rng()
  private val roar = Array(2) { Brown(rng) }
  private val roarCut = Array(2) { Biquad().lowPass(300f) }
  private val hissCut = Array(2) { Biquad().highPass(4_000f) }
  private val flicker = Array(2) { Wander(rng, 0.8f) }
  private val sparks = Pings(rng, 12)

  override fun frame() {
    if (rng.chance(11f)) {
      val size = rng.unit().pow(5)
      sparks.take().fire(0.05f + size * 0.75f, 0.002f + size * 0.012f, pan = rng.bipolar() * 0.75f, dull = rng.between(0.1f, 0.6f))
    }
    if (rng.chance(0.45f)) {
      val pan = rng.bipolar() * 0.5f
      sparks.take().fire(0.55f, 0.05f, pan = pan, dull = 0.93f)
      sparks.take().fire(0.3f, 0.004f, pan = pan)
    }
    sparks.step()
    l = roarCut[0].step(roar[0].next()) * (0.55f + flicker[0].next() * 0.7f) + hissCut[0].step(rng.bipolar()) * 0.012f + sparks.left
    r = roarCut[1].step(roar[1].next()) * (0.55f + flicker[1].next() * 0.7f) + hissCut[1].step(rng.bipolar()) * 0.012f + sparks.right
  }
}

/**
 * A voiced call: a note that can slide in pitch, pulse like a croak and carry overtones.
 * Birds, owls and frogs are all this with different numbers.
 */
private class Call(private val rng: Rng) {
  private var phase = 0f
  private var from = 0f
  private var to = 0f
  private var position = 0
  private var length = 0
  private var level = 0f
  private var pulse = 0f
  private var pulsePhase = 0f
  private var second = 0f
  private var third = 0f
  private var breath = 0f
  private var vibrato = 0f
  private var vibratoPhase = 0f
  private val breathCut = LowPass(900f)
  var left = 0f
    private set
  var right = 0f
    private set
  val busy get() = position < length

  fun start(
    from: Float, to: Float, seconds: Float, level: Float, pan: Float,
    pulse: Float = 0f, second: Float = 0f, third: Float = 0f, breath: Float = 0f, vibrato: Float = 0f,
  ) {
    this.from = from
    this.to = to
    length = (seconds * SAMPLE_RATE).toInt()
    position = 0
    this.level = level
    this.pulse = pulse / SAMPLE_RATE
    pulsePhase = 0f
    this.second = second
    this.third = third
    this.breath = breath
    this.vibrato = vibrato
    left = panLeft(pan)
    right = panRight(pan)
  }

  fun next(): Float {
    if (position >= length) return 0f
    val t = position.toFloat() / length
    position++
    var hertz = from + (to - from) * t
    if (vibrato > 0f) {
      vibratoPhase += 28f / SAMPLE_RATE
      if (vibratoPhase >= 1f) vibratoPhase -= 1f
      hertz *= 1f + sine(vibratoPhase) * vibrato
    }
    phase += hertz / SAMPLE_RATE
    if (phase >= 1f) phase -= 1f
    // Rounded at both ends, so no note starts or stops with a click.
    val edge = (t * 6f).coerceAtMost(1f) * ((1f - t) * 5f).coerceAtMost(1f)
    var shape = edge * edge * (3f - 2f * edge)
    if (pulse > 0f) {
      pulsePhase += pulse
      val beat = 0.5f - 0.5f * sine(pulsePhase + 0.25f)
      shape *= beat * beat
    }
    var out = sine(phase)
    if (second > 0f) out += sine(phase * 2f) * second
    if (third > 0f) out += sine(phase * 3f) * third
    if (breath > 0f) out += breathCut.step(rng.bipolar()) * breath
    return out * shape * level
  }
}

/** One cricket: a few quick pulses, a pause, and again, sometimes resting. */
private class Cricket(private val rng: Rng) {
  private val step = rng.between(4_100f, 5_300f) / SAMPLE_RATE
  private val level = rng.between(0.045f, 0.1f)
  private val pulses = if (rng.unit() < 0.5f) 3 else 4
  private val pulseLength = (SAMPLE_RATE * rng.between(0.014f, 0.02f)).toInt()
  private val period = (SAMPLE_RATE * rng.between(0.42f, 1.1f)).toInt()
  private val pan = rng.bipolar() * 0.9f
  val left = panLeft(pan)
  val right = panRight(pan)
  private var phase = 0f
  private var position = (rng.unit() * period).toInt()
  private var rest = 0

  fun next(): Float {
    if (rest > 0) {
      rest--
      return 0f
    }
    if (++position >= period) {
      position = (rng.bipolar() * SAMPLE_RATE * 0.02f).toInt()
      if (rng.unit() < 0.04f) rest = (SAMPLE_RATE * rng.between(2f, 9f)).toInt()
    }
    val slot = pulseLength * 2
    if (position < 0 || position >= slot * pulses) return 0f
    val within = position % slot
    if (within >= pulseLength) return 0f
    phase += step
    if (phase >= 1f) phase -= 1f
    val shape = sine(within.toFloat() / pulseLength * 0.5f)
    return sine(phase) * shape * shape * level
  }
}

class Crickets : Stereo() {
  private val rng = Rng()
  private val near = Array(7) { Cricket(rng) }
  private val swell = Wander(rng, 6f)
  private var carrier = 0f
  private var trill = 0f

  override fun frame() {
    var left = 0f
    var right = 0f
    for (cricket in near) {
      val value = cricket.next()
      left += value * cricket.left
      right += value * cricket.right
    }
    // The far-off field: one unbroken shimmer, slightly different in each ear.
    carrier += 3_350f / SAMPLE_RATE
    if (carrier >= 1f) carrier -= 1f
    trill += 43f / SAMPLE_RATE
    if (trill >= 1f) trill -= 1f
    val level = 0.006f + swell.next() * 0.01f
    val beatLeft = 0.5f + 0.5f * sine(trill)
    val beatRight = 0.5f + 0.5f * sine(trill + 0.31f)
    l = left + sine(carrier) * beatLeft * beatLeft * level
    r = right + sine(carrier + 0.17f) * beatRight * beatRight * level
  }
}

/** Shared by the animal sounds: a few callers, a little night air and the space around them. */
abstract class Callers(count: Int, room: Float, private val wet: Float, private val air: Float) : Stereo() {
  protected val rng = Rng()
  private val calls = Array(count) { Call(rng) }
  private val echo = Reverb(room, 0.45f)
  private val hush = Array(2) { Pink(rng) }
  private val hushCut = Array(2) { LowPass(700f) }

  /** Called every sample. Start calls from here with [say]. */
  protected abstract fun think()

  protected fun free(index: Int) = !calls[index].busy

  protected fun say(
    index: Int, from: Float, to: Float, seconds: Float, level: Float, pan: Float,
    pulse: Float = 0f, second: Float = 0f, third: Float = 0f, breath: Float = 0f, vibrato: Float = 0f,
  ) = calls[index].start(from, to, seconds, level, pan, pulse, second, third, breath, vibrato)

  override fun frame() {
    think()
    var left = 0f
    var right = 0f
    for (call in calls) {
      if (!call.busy) continue
      val value = call.next()
      left += value * call.left
      right += value * call.right
    }
    echo.step(left, right)
    l = left + echo.left * wet + hushCut[0].step(hush[0].next()) * air
    r = right + echo.right * wet + hushCut[1].step(hush[1].next()) * air
  }
}

/** A night bird now and then: a short phrase of sliding whistles from somewhere in the trees. */
class NightBirds : Callers(2, 1.8f, 0.4f, 0.03f) {
  private var wait = (SAMPLE_RATE * rng.between(2f, 5f)).toInt()
  private var notes = 0
  private var pan = 0f
  private var pitch = 0f

  override fun think() {
    if (wait-- > 0) return
    if (notes == 0) {
      notes = 2 + (rng.unit() * 4).toInt()
      pan = rng.bipolar() * 0.8f
      pitch = rng.between(1_500f, 2_600f)
    }
    notes--
    val index = notes % 2
    val start = pitch * rng.between(0.9f, 1.15f)
    say(index, start, start * rng.between(0.8f, 1.3f), rng.between(0.12f, 0.3f), rng.between(0.08f, 0.14f), pan, second = 0.08f, vibrato = 0.012f)
    wait = if (notes > 0) (SAMPLE_RATE * rng.between(0.2f, 0.45f)).toInt() else (SAMPLE_RATE * rng.between(6f, 22f)).toInt()
  }
}

/** Two owls calling across a wood: a low, breathy "hoo, hoo-hoo, hooo", and sometimes an answer. */
class Owls : Callers(2, 3f, 0.7f, 0.035f) {
  private val rhythm = floatArrayOf(0.3f, 0.16f, 0.5f, 0.42f, 0.5f)
  private val gaps = floatArrayOf(0.2f, 0.12f, 0.42f, 0.36f, 0f)
  private var wait = (SAMPLE_RATE * rng.between(2f, 6f)).toInt()
  private var note = -1
  private var owl = 0
  private var answer = false

  override fun think() {
    if (wait-- > 0) return
    if (note < 0) {
      note = 0
      if (!answer) owl = if (rng.unit() < 0.6f) 0 else 1
    }
    val pitch = if (owl == 0) 335f else 410f
    val pan = if (owl == 0) -0.5f else 0.55f
    val level = if (owl == 0) 0.2f else 0.11f
    say(owl, pitch * 1.03f, pitch * 0.96f, rhythm[note], level, pan, second = 0.16f, third = 0.05f, breath = 0.06f)
    wait = (SAMPLE_RATE * (rhythm[note] + gaps[note])).toInt()
    if (++note == rhythm.size) {
      note = -1
      answer = !answer && rng.unit() < 0.45f
      if (answer) {
        owl = 1 - owl
        wait += (SAMPLE_RATE * rng.between(1.5f, 4f)).toInt()
      } else {
        wait += (SAMPLE_RATE * rng.between(9f, 30f)).toInt()
      }
    }
  }
}

/** A pond at night: peepers keeping time, a few croakers, and one long trill. */
class Frogs : Callers(8, 1.2f, 0.25f, 0.025f) {
  private val waits = IntArray(8) { (SAMPLE_RATE * rng.between(0.2f, 4f)).toInt() }
  private val pans = FloatArray(8) { rng.bipolar() * 0.9f }
  private val tune = FloatArray(8) { rng.between(0.9f, 1.1f) }
  private val pace = FloatArray(8) { rng.between(0.8f, 1.3f) }
  private val near = FloatArray(8) { rng.between(0.4f, 1f) }

  override fun think() {
    for (i in waits.indices) {
      if (waits[i]-- > 0) continue
      val resting = rng.unit() < 0.05f
      when {
        i < 5 -> {
          say(i, 2_750f * tune[i], 3_150f * tune[i], 0.1f, 0.07f * near[i], pans[i], second = 0.05f)
          waits[i] = (SAMPLE_RATE * (if (resting) rng.between(3f, 9f) else 0.85f * pace[i] + rng.unit() * 0.08f)).toInt()
        }
        i < 7 -> {
          say(i, 480f * tune[i], 440f * tune[i], rng.between(0.3f, 0.55f), 0.16f * near[i], pans[i], pulse = 46f, second = 0.5f, third = 0.25f)
          waits[i] = (SAMPLE_RATE * (if (resting) rng.between(6f, 15f) else rng.between(1.4f, 4.5f))).toInt()
        }
        else -> {
          say(i, 1_250f * tune[i], 1_280f * tune[i], rng.between(1.2f, 2.2f), 0.06f, pans[i], pulse = 23f, second = 0.6f)
          waits[i] = (SAMPLE_RATE * rng.between(5f, 14f)).toInt()
        }
      }
    }
  }
}

class Fan : Stereo() {
  private val rng = Rng()
  private val air = Array(2) { Pink(rng) }
  private val body = Array(2) { Biquad().lowPass(850f, 0.6f) }
  private val breeze = Array(2) { Biquad().highPass(1_800f) }
  private var motor = 0f
  private var blade = 0f

  override fun frame() {
    motor += 100f / SAMPLE_RATE
    if (motor >= 1f) motor -= 1f
    blade += 6.4f / SAMPLE_RATE
    if (blade >= 1f) blade -= 1f
    val wobble = 1f + sine(blade) * 0.07f
    val tone = sine(motor) * 0.02f + sine(motor * 2f) * 0.008f + sine(motor * 3f) * 0.004f
    for (i in 0..1) {
      val noise = air[i].next()
      val out = (body[i].step(noise) * 1.7f + breeze[i].step(noise) * 0.12f) * (if (i == 0) wobble else 2f - wobble) + tone
      if (i == 0) l = out else r = out
    }
  }
}

/** Inside a carriage: a steady rumble and the wheels crossing each rail joint in pairs. */
class Train : Stereo() {
  private val rng = Rng()
  private val rumble = Array(2) { Brown(rng) }
  private val rumbleCut = Array(2) { Biquad().lowPass(190f) }
  private val rattle = Array(2) { Biquad().bandPass(900f, 0.8f) }
  private val sway = Wander(rng, 5f)
  private val wheels = Pings(rng, 6)
  private var wait = 0
  private var beat = 0

  override fun frame() {
    if (wait-- <= 0) {
      beat = (beat + 1) % 4
      wait = (SAMPLE_RATE * if (beat % 2 == 0) 0.6f else 0.17f).toInt()
      val pan = if (beat < 2) -0.35f else 0.35f
      wheels.take().fire(0.3f, 0.07f, 150f, 0.99992f, pan, soft = 0.002f)
      wheels.take().fire(0.22f, 0.035f, pan = pan, dull = 0.9f)
    }
    wheels.step()
    val level = 0.85f + sway.next() * 0.3f
    l = (rumbleCut[0].step(rumble[0].next()) * 1.3f + rattle[0].step(rng.bipolar()) * 0.03f) * level + wheels.left
    r = (rumbleCut[1].step(rumble[1].next()) * 1.3f + rattle[1].step(rng.bipolar()) * 0.03f) * level + wheels.right
  }
}

/** Mains hum, as from a fridge or a transformer, beating very slowly between the ears. */
class Hum : Stereo() {
  private val phases = FloatArray(4)
  private val steps = floatArrayOf(60f, 60.06f, 120.11f, 119.93f).map { it / SAMPLE_RATE }

  override fun frame() {
    for (i in phases.indices) {
      phases[i] += steps[i]
      if (phases[i] >= 1f) phases[i] -= 1f
    }
    l = sine(phases[0]) * 0.3f + sine(phases[2]) * 0.12f + sine(phases[0] * 3f) * 0.02f
    r = sine(phases[1]) * 0.3f + sine(phases[3]) * 0.12f + sine(phases[1] * 3f) * 0.02f
  }
}

/**
 * Singing bowls, struck softly and left to ring. Each overtone is two notes a hair apart,
 * one in each ear, which makes the slow swirl a real bowl has.
 */
class Bowls : Stereo() {
  private val rng = Rng()
  private val notes = floatArrayOf(174.6f, 220f, 261.6f, 329.6f)
  private val ratios = floatArrayOf(1f, 2.76f, 5.2f, 8.3f)
  private val levels = floatArrayOf(0.2f, 0.1f, 0.04f, 0.015f)
  private val rings = floatArrayOf(17f, 10f, 5f, 2.5f)
  private val partials = Array(notes.size * ratios.size * 2) { Partial() }
  private val mallet = Ping(rng)
  private val echo = Reverb(3.2f, 0.4f)
  private var wait = SAMPLE_RATE / 2

  override fun frame() {
    if (wait-- <= 0) {
      val bowl = (rng.unit() * notes.size).toInt().coerceAtMost(notes.size - 1)
      val hard = rng.between(0.6f, 1f)
      for (i in ratios.indices) {
        val hertz = notes[bowl] * ratios[i]
        val apart = rng.between(0.0012f, 0.0035f)
        partials[(bowl * ratios.size + i) * 2].strike(hertz * (1f - apart), levels[i] * hard, rings[i])
        partials[(bowl * ratios.size + i) * 2 + 1].strike(hertz * (1f + apart), levels[i] * hard, rings[i])
      }
      mallet.fire(0.05f * hard, 0.05f, dull = 0.96f)
      wait = (SAMPLE_RATE * rng.between(8f, 19f)).toInt()
    }
    var left = 0f
    var right = 0f
    for (i in partials.indices step 2) {
      val a = partials[i].next()
      val b = partials[i + 1].next()
      left += a * 0.85f + b * 0.35f
      right += a * 0.35f + b * 0.85f
    }
    val thump = mallet.next()
    left += thump
    right += thump
    echo.step(left, right)
    l = left + echo.left * 0.35f
    r = right + echo.right * 0.35f
  }
}

/** Metal tubes on a porch: they ring in little flurries when the breeze picks up. */
class Chimes : Stereo() {
  private val rng = Rng()
  private val tubes = floatArrayOf(1_046.5f, 1_174.7f, 1_318.5f, 1_568f, 1_760f, 2_093f)
  private val ratios = floatArrayOf(1f, 2.76f, 5.4f)
  private val levels = floatArrayOf(0.11f, 0.045f, 0.018f)
  private val rings = floatArrayOf(3.6f, 1.6f, 0.6f)
  private val partials = Array(tubes.size * ratios.size) { Partial() }
  private val breeze = Wander(rng, 5f)
  private val echo = Reverb(2.2f, 0.3f)
  private var last = -1

  override fun frame() {
    val wind = breeze.next()
    if (rng.chance(0.15f + wind * wind * wind * 7f)) {
      var tube = (rng.unit() * tubes.size).toInt().coerceAtMost(tubes.size - 1)
      if (tube == last) tube = (tube + 1) % tubes.size
      last = tube
      val hard = rng.between(0.25f, 1f)
      for (i in ratios.indices) partials[tube * ratios.size + i].strike(tubes[tube] * ratios[i], levels[i] * hard, rings[i])
    }
    var left = 0f
    var right = 0f
    for (tube in tubes.indices) {
      var value = 0f
      for (i in ratios.indices) value += partials[tube * ratios.size + i].next()
      val pan = -0.7f + 1.4f * tube / (tubes.size - 1)
      left += value * panLeft(pan)
      right += value * panRight(pan)
    }
    echo.step(left, right)
    l = left + echo.left * 0.3f
    r = right + echo.right * 0.3f
  }
}

/** A soft chord that never resolves: each note swells and fades on its own slow clock. */
class Drone : Stereo() {
  private val rng = Rng()
  private val notes = floatArrayOf(65.41f, 98f, 130.81f, 196f, 261.63f, 293.66f, 392f)
  private val levels = floatArrayOf(0.17f, 0.11f, 0.1f, 0.06f, 0.035f, 0.025f, 0.012f)
  private val leftPhase = FloatArray(notes.size) { rng.unit() }
  private val rightPhase = FloatArray(notes.size) { rng.unit() }
  private val swellPhase = FloatArray(notes.size) { rng.unit() }
  private val swellStep = FloatArray(notes.size) { 1f / (SAMPLE_RATE * rng.between(13f, 41f)) }
  private val air = Array(2) { Pink(rng) }
  private val airCut = Array(2) { LowPass(500f) }

  override fun frame() {
    var left = airCut[0].step(air[0].next()) * 0.02f
    var right = airCut[1].step(air[1].next()) * 0.02f
    for (i in notes.indices) {
      leftPhase[i] += (notes[i] - 0.11f) / SAMPLE_RATE
      rightPhase[i] += (notes[i] + 0.11f) / SAMPLE_RATE
      swellPhase[i] += swellStep[i]
      if (leftPhase[i] >= 1f) leftPhase[i] -= 1f
      if (rightPhase[i] >= 1f) rightPhase[i] -= 1f
      if (swellPhase[i] >= 1f) swellPhase[i] -= 1f
      val level = levels[i] * (0.3f + 0.7f * (0.5f + 0.5f * sine(swellPhase[i])))
      left += sine(leftPhase[i]) * level
      right += sine(rightPhase[i]) * level
    }
    l = left
    r = right
  }
}

/** A resting heartbeat, heard as if with an ear to a chest: lub, dub, and a pause. */
class Heartbeat : Stereo() {
  private val rng = Rng()
  private val beats = Pings(rng, 6)
  private val pace = Wander(rng, 20f)
  private val soft = Array(2) { Biquad().lowPass(260f) }
  private var wait = SAMPLE_RATE / 2
  private var second = -1

  private fun thump(hertz: Float, seconds: Float, level: Float) {
    beats.take().fire(level, seconds, hertz, 0.99991f, soft = 0.006f)
    beats.take().fire(level * 0.3f, seconds * 0.8f, hertz * 2f, 0.99991f, soft = 0.006f)
    beats.take().fire(level * 0.5f, seconds * 0.6f, dull = 0.975f, soft = 0.004f)
  }

  override fun frame() {
    if (wait-- <= 0) {
      thump(62f, 0.13f, 0.6f)
      second = (SAMPLE_RATE * 0.3f).toInt()
      wait = (SAMPLE_RATE * 60f / (52f + pace.next() * 8f)).toInt()
    } else {
      pace.next()
    }
    if (second >= 0 && second-- == 0) thump(80f, 0.09f, 0.4f)
    beats.step()
    l = soft[0].step(beats.left) * 1.4f
    r = soft[1].step(beats.right) * 1.4f
  }
}
