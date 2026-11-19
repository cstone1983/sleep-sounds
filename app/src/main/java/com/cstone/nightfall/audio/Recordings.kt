package com.cstone.nightfall.audio

import android.content.Context
import android.content.res.AssetManager
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import java.lang.ref.SoftReference
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap

/**
 * Real field recordings, shipped as one-minute stereo loops in assets/sounds.
 * See recordings/SOURCES.md for where each came from and recordings/prepare.py for how they're cut.
 */
object Recordings {
  private const val PREFS = "nightfall_store"
  private const val KEY = "recordings"
  @Volatile private var assets: AssetManager? = null
  @Volatile var enabled = true
    private set
  private val loaded = ConcurrentHashMap<String, SoftReference<ShortArray>>()

  /** Call before making any sound. Until then, and in tests, every sound is the generated one. */
  fun attach(context: Context) {
    assets = context.applicationContext.assets
    enabled = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, true)
  }

  /** The saved choice, for screens that only show it. */
  fun wanted(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, true)

  fun setEnabled(context: Context, on: Boolean) {
    enabled = on
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, on).apply()
  }

  val available get() = assets != null && enabled

  /** Blocks while it decodes. Returns interleaved 16-bit stereo, or null if the recording can't be read. */
  fun load(name: String): ShortArray? {
    loaded[name]?.get()?.let { return it }
    val from = assets ?: return null
    return try {
      decode(from, "sounds/$name.ogg").also { loaded[name] = SoftReference(it) }
    } catch (e: Exception) {
      Log.w("Nightfall", "couldn't read the $name recording: $e")
      null
    }
  }

  private fun decode(assets: AssetManager, path: String): ShortArray {
    val extractor = MediaExtractor()
    assets.openFd(path).use { extractor.setDataSource(it.fileDescriptor, it.startOffset, it.length) }
    extractor.selectTrack(0)
    val format = extractor.getTrackFormat(0)
    require(format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) == 2 && format.getInteger(MediaFormat.KEY_SAMPLE_RATE) == SAMPLE_RATE) { "$path must be 48 kHz stereo" }
    val expected = (format.getLong(MediaFormat.KEY_DURATION) / 1_000_000.0 * SAMPLE_RATE * 2).toInt() + SAMPLE_RATE
    var samples = ShortArray(expected)
    var count = 0
    val codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
    try {
      codec.configure(format, null, null, 0)
      codec.start()
      val info = MediaCodec.BufferInfo()
      var feeding = true
      while (true) {
        if (feeding) {
          val index = codec.dequeueInputBuffer(10_000)
          if (index >= 0) {
            val size = extractor.readSampleData(codec.getInputBuffer(index)!!, 0)
            if (size < 0) {
              codec.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
              feeding = false
            } else {
              codec.queueInputBuffer(index, 0, size, extractor.sampleTime, 0)
              extractor.advance()
            }
          }
        }
        val index = codec.dequeueOutputBuffer(info, 10_000)
        if (index >= 0) {
          val shorts = codec.getOutputBuffer(index)!!.order(ByteOrder.nativeOrder()).asShortBuffer()
          val more = info.size / 2
          if (count + more > samples.size) samples = samples.copyOf(maxOf(samples.size * 2, count + more))
          shorts.get(samples, count, more)
          count += more
          codec.releaseOutputBuffer(index, false)
          if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
        }
      }
    } finally {
      codec.release()
      extractor.release()
    }
    return samples.copyOf(count - count % 2)
  }
}

/**
 * Plays a recording on a loop. It's silent for the moment it takes to decode, then fades in.
 * Where there's no recording to play (recordings switched off, a damaged file, a test), it's the
 * generated version of the same sound instead.
 */
class Recorded(private val name: String, private val gain: Float = 1f, generated: () -> Source) : Source {
  private val fallback: Source? = if (Recordings.available) null else generated()
  @Volatile private var samples: ShortArray? = null
  @Volatile private var failed: Source? = null
  private var position = 0
  private var fade = 0f

  init {
    if (fallback == null) {
      Thread({
        val data = Recordings.load(name)
        if (data == null || data.size < SAMPLE_RATE) failed = generated() else samples = data
      }, "nightfall-recording").start()
    }
  }

  override fun render(left: FloatArray, right: FloatArray, count: Int) {
    (fallback ?: failed)?.let { return it.render(left, right, count) }
    val data = samples
    if (data == null) {
      left.fill(0f, 0, count)
      right.fill(0f, 0, count)
      return
    }
    val scale = gain / 32_768f
    for (i in 0 until count) {
      if (fade < 1f) fade = (fade + 0.00008f).coerceAtMost(1f)
      left[i] = data[position] * scale * fade
      right[i] = data[position + 1] * scale * fade
      position += 2
      if (position >= data.size) position = 0
    }
  }
}
