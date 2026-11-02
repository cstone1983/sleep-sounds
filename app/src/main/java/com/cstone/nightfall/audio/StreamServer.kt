package com.cstone.nightfall.audio

import android.util.Log
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

private const val BLOCK = 4_800
private const val LEAD_MS = 3_000L
private const val STALLED_MS = 1_000L
// Sonos won't play a stream with no length, so it's given a length no night reaches: 12 hours.
private const val LENGTH = 0xFFFFFFF0L

internal fun wavHeader(size: Long, rate: Int): ByteArray {
  val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
  header.put("RIFF".toByteArray()).putInt((size - 8).toInt()).put("WAVEfmt ".toByteArray())
  header.putInt(16).putShort(1).putShort(1).putInt(rate).putInt(rate * 2).putShort(2).putShort(16)
  header.put("data".toByteArray()).putInt((size - 44).toInt())
  return header.array()
}

/** Serves a [MixCore] as an endless WAV, ahead of real time by only a few seconds so changes reach the speaker. */
class StreamServer(private val core: MixCore, private val onDrop: () -> Unit) {
  private val server = ServerSocket(0)
  private val path = "/nightfall-${UUID.randomUUID()}.wav"
  @Volatile private var closed = false
  @Volatile private var current: Socket? = null

  fun url(host: String) = "http://$host:${server.localPort}$path"

  fun start() {
    Thread({
      while (!closed) {
        val client = try {
          server.accept()
        } catch (e: IOException) {
          return@Thread
        }
        current?.runCatching { close() }
        current = client
        Thread({ serve(client) }, "nightfall-stream").start()
      }
    }, "nightfall-accept").start()
  }

  fun close() {
    closed = true
    server.runCatching { close() }
    current?.runCatching { close() }
  }

  private fun serve(client: Socket) {
    var streaming = false
    try {
      client.use {
        client.soTimeout = 5_000
        client.tcpNoDelay = true
        val request = readRequest(client.getInputStream())
        val out = client.getOutputStream()
        if (!request.startsWith("GET $path ") && !request.startsWith("HEAD $path ")) {
          out.write("HTTP/1.1 404 Not Found\r\nConnection: close\r\n\r\n".toByteArray())
          return
        }
        out.write("HTTP/1.1 200 OK\r\nContent-Type: audio/wav\r\nContent-Length: $LENGTH\r\nConnection: close\r\n\r\n".toByteArray())
        if (request.startsWith("HEAD")) return
        out.write(wavHeader(LENGTH, SAMPLE_RATE))
        streaming = true
        pump(out)
      }
    } catch (e: IOException) {
      Log.i("Nightfall", "stream ended: $e")
    }
    if (streaming && !closed && client === current) onDrop()
  }

  private fun readRequest(input: InputStream): String {
    val text = StringBuilder()
    while (!text.endsWith("\r\n\r\n") && text.length < 8_192) {
      val next = input.read()
      if (next < 0) break
      text.append(next.toChar())
    }
    return text.toString()
  }

  private fun pump(out: OutputStream) {
    val samples = FloatArray(BLOCK)
    val bytes = ByteBuffer.allocate(BLOCK * 2).order(ByteOrder.LITTLE_ENDIAN)
    var start = System.currentTimeMillis()
    var sentMs = 0L
    while (!closed) {
      val ahead = sentMs - (System.currentTimeMillis() - start)
      if (ahead > LEAD_MS) {
        Thread.sleep(50)
        continue
      }
      // After a pause the speaker holds audio already, so carry on without a burst.
      if (ahead < -STALLED_MS) start = System.currentTimeMillis() - sentMs + LEAD_MS
      synchronized(core) { core.render(samples) }
      bytes.clear()
      for (sample in samples) bytes.putShort((sample.coerceIn(-1f, 1f) * 32_767).toInt().toShort())
      out.write(bytes.array())
      sentMs += BLOCK * 1_000L / SAMPLE_RATE
    }
  }
}
