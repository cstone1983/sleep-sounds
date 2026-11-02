package com.cstone.nightfall.connect

import android.util.Log
import com.cstone.nightfall.data.Speaker
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.xml.parsers.DocumentBuilderFactory

private const val SSDP = "239.255.255.250"
private const val MDNS = "224.0.0.251"
private const val SONOS_PORT = 1400
private const val SEARCH_MS = 5_000L
private const val ASK_EVERY_MS = 1_500L

/**
 * Finds speakers that play a URL they're given. SSDP covers Sonos, DLNA speakers and many TVs.
 * mDNS covers Sonos on another subnet, where a router passes it along but SSDP doesn't cross.
 */
object SpeakerSearch {
  /** Blocks for the length of the search, so call this off the main thread. */
  fun search(timeoutMs: Long = SEARCH_MS, stop: () -> Boolean = { false }, found: (Speaker) -> Unit) {
    val seen = mutableSetOf<String>()
    try {
      DatagramSocket().use { socket ->
        socket.soTimeout = 300
        val buffer = ByteArray(9_000)
        val end = System.currentTimeMillis() + timeoutMs
        var nextAsk = 0L
        while (System.currentTimeMillis() < end && !stop()) {
          if (System.currentTimeMillis() >= nextAsk) {
            ask(socket)
            nextAsk = System.currentTimeMillis() + ASK_EVERY_MS
          }
          val packet = DatagramPacket(buffer, buffer.size)
          try {
            socket.receive(packet)
          } catch (e: SocketTimeoutException) {
            continue
          }
          for (location in locations(packet)) {
            if (seen.add(location)) Thread({ describe(location)?.let(found) }, "nightfall-describe").start()
          }
        }
      }
    } catch (e: IOException) {
      Log.w("Nightfall", "speaker search failed: $e")
    }
  }

  /** Finds one speaker by its id or room name, or null if it doesn't answer. */
  fun find(wanted: String, timeoutMs: Long = SEARCH_MS): Speaker? {
    var match: Speaker? = null
    val done = CountDownLatch(1)
    search(timeoutMs, stop = { done.count == 0L }) {
      if (it.id == wanted || it.name.equals(wanted, ignoreCase = true)) {
        match = it
        done.countDown()
      }
    }
    done.await(500, TimeUnit.MILLISECONDS)
    return match
  }

  private fun ask(socket: DatagramSocket) {
    val ssdp = "M-SEARCH * HTTP/1.1\r\nHOST: $SSDP:1900\r\nMAN: \"ssdp:discover\"\r\nMX: 2\r\n" +
      "ST: urn:schemas-upnp-org:device:MediaRenderer:1\r\n\r\n"
    socket.send(DatagramPacket(ssdp.toByteArray(), ssdp.length, InetAddress.getByName(SSDP), 1900))
    val mdns = mdnsQuery("_sonos._tcp.local")
    socket.send(DatagramPacket(mdns, mdns.size, InetAddress.getByName(MDNS), 5353))
  }

  private fun locations(packet: DatagramPacket): List<String> {
    if (packet.port == 5353) {
      return runCatching { mdnsAddresses(packet.data, packet.length) }.getOrDefault(emptyList())
        .map { "http://$it:$SONOS_PORT/xml/device_description.xml" }
    }
    val reply = String(packet.data, 0, packet.length)
    return reply.lineSequence()
      .filter { it.startsWith("location:", ignoreCase = true) }
      .map { it.substringAfter(':').trim() }
      .toList()
  }

  private fun describe(location: String): Speaker? = try {
    val connection = URL(location).openConnection() as HttpURLConnection
    connection.connectTimeout = 3_000
    connection.readTimeout = 3_000
    connection.inputStream.use { parseDescription(it.readBytes(), location) }.also { connection.disconnect() }
  } catch (e: Exception) {
    Log.i("Nightfall", "no speaker at $location: $e")
    null
  }
}

internal fun mdnsQuery(name: String): ByteArray {
  val labels = name.split('.').flatMap { listOf(it.length.toByte()) + it.toByteArray().toList() }
  // One question, type PTR, with the top bit asking for a direct reply.
  return byteArrayOf(0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0) + labels + byteArrayOf(0, 0, 12, 0x80.toByte(), 1)
}

/** The IPv4 addresses in an mDNS reply. */
internal fun mdnsAddresses(data: ByteArray, length: Int): List<String> {
  fun u16(at: Int) = (data[at].toInt() and 0xFF shl 8) or (data[at + 1].toInt() and 0xFF)
  var at = 12
  fun skipName() {
    while (true) {
      val size = data[at].toInt() and 0xFF
      if (size == 0) {
        at += 1
        return
      }
      if (size and 0xC0 == 0xC0) {
        at += 2
        return
      }
      at += size + 1
    }
  }
  repeat(u16(4)) {
    skipName()
    at += 4
  }
  val found = mutableListOf<String>()
  repeat(u16(6) + u16(8) + u16(10)) {
    if (at >= length) return found
    skipName()
    val type = u16(at)
    val size = u16(at + 8)
    at += 10
    if (type == 1 && size == 4) found += (0..3).joinToString(".") { (data[at + it].toInt() and 0xFF).toString() }
    at += size
  }
  return found
}

/** Reads a UPnP device description. Anything without AVTransport can't be told what to play. */
internal fun parseDescription(xml: ByteArray, location: String): Speaker? {
  val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(ByteArrayInputStream(xml))
  val root = document.getElementsByTagName("device").item(0) as? Element ?: return null
  val services = document.getElementsByTagName("service")
  val transport = (0 until services.length)
    .mapNotNull { services.item(it) as? Element }
    .firstOrNull { it.text("serviceType").contains(":AVTransport:") } ?: return null
  val base = document.documentElement.text("URLBase").ifEmpty { location }
  return Speaker(
    id = root.text("UDN"),
    name = root.text("roomName").ifEmpty { root.text("friendlyName") },
    model = root.text("modelName"),
    control = URL(URL(base), transport.text("controlURL")).toString(),
  )
}

private fun Element.text(tag: String): String = getElementsByTagName(tag).item(0)?.textContent.orEmpty().trim()

/** The few UPnP calls playback needs. Each one blocks, so call them off the main thread. */
object Upnp {
  private const val SERVICE = "urn:schemas-upnp-org:service:AVTransport:1"

  fun play(control: String, url: String) {
    call(control, "SetAVTransportURI", "<CurrentURI>${escape(url)}</CurrentURI><CurrentURIMetaData>${escape(metadata(url))}</CurrentURIMetaData>")
    resume(control)
  }

  fun resume(control: String) { call(control, "Play", "<Speed>1</Speed>") }
  fun pause(control: String) { call(control, "Pause") }
  fun stop(control: String) { call(control, "Stop") }

  /** PLAYING, TRANSITIONING, PAUSED_PLAYBACK or STOPPED. */
  fun state(control: String): String =
    call(control, "GetTransportInfo").substringAfter("<CurrentTransportState>", "").substringBefore('<')

  /** This phone's address on the route to the speaker. */
  fun localAddress(control: String): String = DatagramSocket().use {
    it.connect(InetSocketAddress(URL(control).host, 9))
    it.localAddress.hostAddress.orEmpty()
  }

  internal fun metadata(url: String) =
    """<DIDL-Lite xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/">""" +
      """<item id="0" parentID="0" restricted="1"><dc:title>Nightfall</dc:title><upnp:class>object.item.audioItem.musicTrack</upnp:class>""" +
      """<res protocolInfo="http-get:*:audio/wav:*">${escape(url)}</res></item></DIDL-Lite>"""

  private fun escape(text: String) =
    text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

  private fun call(control: String, action: String, args: String = ""): String {
    val body = """<?xml version="1.0"?><s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">""" +
      """<s:Body><u:$action xmlns:u="$SERVICE"><InstanceID>0</InstanceID>$args</u:$action></s:Body></s:Envelope>"""
    val connection = URL(control).openConnection() as HttpURLConnection
    try {
      connection.requestMethod = "POST"
      connection.connectTimeout = 4_000
      connection.readTimeout = 4_000
      connection.doOutput = true
      connection.setRequestProperty("Content-Type", "text/xml; charset=\"utf-8\"")
      connection.setRequestProperty("SOAPACTION", "\"$SERVICE#$action\"")
      connection.outputStream.use { it.write(body.toByteArray()) }
      if (connection.responseCode !in 200..299) throw IOException("$action refused: ${connection.responseCode}")
      return connection.inputStream.use { it.readBytes().decodeToString() }
    } finally {
      connection.disconnect()
    }
  }
}
