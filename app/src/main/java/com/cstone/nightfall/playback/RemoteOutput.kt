package com.cstone.nightfall.playback

import android.util.Log
import com.cstone.nightfall.audio.MixCore
import com.cstone.nightfall.audio.StreamServer
import com.cstone.nightfall.connect.Upnp
import com.cstone.nightfall.data.Speaker
import java.io.IOException
import java.util.concurrent.Executors

/** Plays a mix on a Wi-Fi speaker. The phone makes the sound, and the speaker fetches it as a stream. */
class RemoteOutput(val speaker: Speaker, private val onLost: (RemoteOutput) -> Unit) {
  val core = MixCore()
  private val calls = Executors.newSingleThreadExecutor()
  private val server = StreamServer(core) { calls.execute(::checkStillPlaying) }
  @Volatile private var paused = false
  @Volatile private var released = false

  /** Blocks, and throws if the speaker can't be reached or refuses. */
  @Throws(IOException::class)
  fun open() {
    server.start()
    Upnp.play(speaker.control, server.url(Upnp.localAddress(speaker.control)))
  }

  /** Blocks until the speaker says it's playing, or gives up after a few seconds. */
  fun confirm(): Boolean {
    repeat(10) {
      val state = runCatching { Upnp.state(speaker.control) }.getOrDefault("")
      if (state == "PLAYING") return true
      Thread.sleep(500)
    }
    return false
  }

  fun pause() {
    paused = true
    send { Upnp.pause(speaker.control) }
  }

  fun resume() {
    paused = false
    send { Upnp.resume(speaker.control) }
  }

  fun release() {
    released = true
    server.close()
    send { Upnp.stop(speaker.control) }
    calls.shutdown()
  }

  private fun send(call: () -> Unit) {
    calls.execute {
      try {
        call()
      } catch (e: IOException) {
        Log.w("Nightfall", "speaker call failed: $e")
      }
    }
  }

  // A dropped connection is either a hiccup, which the speaker recovers from, or someone stopping it there.
  private fun checkStillPlaying() {
    if (paused || released) return
    Thread.sleep(3_000)
    if (paused || released) return
    if (!confirm()) onLost(this)
  }
}
