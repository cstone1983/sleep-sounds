package com.cstone.nightfall

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cstone.nightfall.audio.MixCore
import com.cstone.nightfall.audio.SAMPLE_RATE
import com.cstone.nightfall.audio.StreamServer
import com.cstone.nightfall.audio.wavHeader
import com.cstone.nightfall.connect.Upnp
import com.cstone.nightfall.connect.mdnsAddresses
import com.cstone.nightfall.connect.mdnsQuery
import com.cstone.nightfall.connect.parseDescription
import com.cstone.nightfall.data.Layer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.net.Socket
import java.net.URL
import java.nio.ByteBuffer
import java.nio.ByteOrder

private const val SONOS = """<?xml version="1.0"?>
<root xmlns="urn:schemas-upnp-org:device-1-0"><device>
  <deviceType>urn:schemas-upnp-org:device:ZonePlayer:1</deviceType>
  <friendlyName>10.10.3.138 - Sonos Ray - RINCON_1</friendlyName>
  <modelName>Sonos Ray</modelName><roomName>Bedroom</roomName><UDN>uuid:RINCON_1</UDN>
  <deviceList><device>
    <deviceType>urn:schemas-upnp-org:device:MediaRenderer:1</deviceType>
    <friendlyName>Bedroom - Sonos Ray Media Renderer</friendlyName>
    <serviceList><service>
      <serviceType>urn:schemas-upnp-org:service:RenderingControl:1</serviceType>
      <controlURL>/MediaRenderer/RenderingControl/Control</controlURL>
    </service><service>
      <serviceType>urn:schemas-upnp-org:service:AVTransport:1</serviceType>
      <controlURL>/MediaRenderer/AVTransport/Control</controlURL>
    </service></serviceList>
  </device></deviceList>
</device></root>"""

private const val PRINTER = """<?xml version="1.0"?>
<root xmlns="urn:schemas-upnp-org:device-1-0"><device><friendlyName>Printer</friendlyName><UDN>uuid:2</UDN></device></root>"""

@RunWith(AndroidJUnit4::class)
class SpeakerTest {
  @Test
  fun readsASonosDescription() {
    val speaker = parseDescription(SONOS.toByteArray(), "http://10.10.3.138:1400/xml/device_description.xml")!!
    assertEquals("Bedroom", speaker.name)
    assertEquals("Sonos Ray", speaker.model)
    assertEquals("uuid:RINCON_1", speaker.id)
    assertEquals("http://10.10.3.138:1400/MediaRenderer/AVTransport/Control", speaker.control)
  }

  @Test
  fun skipsDevicesThatCantPlay() {
    assertNull(parseDescription(PRINTER.toByteArray(), "http://10.10.1.131/desc.xml"))
  }

  @Test
  fun readsAddressesFromAnMdnsReply() {
    // Header, then one PTR record with a two byte name pointer, then one A record.
    val reply = byteArrayOf(0, 0, 0x84.toByte(), 0, 0, 0, 0, 1, 0, 0, 0, 1) +
      byteArrayOf(0xC0.toByte(), 12, 0, 12, 0, 1, 0, 0, 0, 120, 0, 2, 0xC0.toByte(), 12) +
      byteArrayOf(0xC0.toByte(), 12, 0, 1, 0, 1, 0, 0, 0, 120, 0, 4, 10, 10, 3, 138.toByte())
    assertEquals(listOf("10.10.3.138"), mdnsAddresses(reply, reply.size))
  }

  @Test
  fun buildsAnMdnsQuestion() {
    val query = mdnsQuery("_sonos._tcp.local")
    assertEquals(1, query[5].toInt())
    assertEquals(6, query[12].toInt())
    assertTrue(String(query).contains("_sonos"))
  }

  @Test
  fun metadataEscapesTheStreamAddress() {
    assertTrue(Upnp.metadata("http://a/b?x=1&y=2").contains("x=1&amp;y=2"))
  }

  @Test
  fun wavHeaderDescribesStereo16Bit() {
    val header = ByteBuffer.wrap(wavHeader(0xFFFFFFF0L, SAMPLE_RATE)).order(ByteOrder.LITTLE_ENDIAN)
    assertEquals(44, header.capacity())
    assertEquals(2, header.getShort(22).toInt())
    assertEquals(SAMPLE_RATE, header.getInt(24))
    assertEquals(16, header.getShort(34).toInt())
    assertEquals(0xFFFFFFF0L - 44, header.getInt(40).toLong() and 0xFFFFFFFFL)
  }

  @Test
  fun streamsTheMixToWhoeverHasTheAddress() {
    val core = MixCore().apply {
      setLayers(listOf(Layer("brown", 80)))
      master = 1f
    }
    val server = StreamServer(core) {}
    server.start()
    val url = URL(server.url("127.0.0.1"))
    Socket("127.0.0.1", url.port).use { client ->
      client.getOutputStream().write("GET ${url.path} HTTP/1.1\r\nHost: x\r\n\r\n".toByteArray())
      val input = client.getInputStream()
      val bytes = ByteArray(200_000)
      var total = 0
      while (total < bytes.size) {
        val read = input.read(bytes, total, bytes.size - total)
        if (read < 0) break
        total += read
      }
      val text = String(bytes, 0, 200)
      assertTrue(text.startsWith("HTTP/1.1 200 OK"))
      assertTrue(text.contains("Content-Length: 4294967280"))
      assertTrue(text.contains("RIFF"))
      assertTrue("no sound in the stream", bytes.drop(100_000).any { it.toInt() != 0 })
    }
    Socket("127.0.0.1", url.port).use { client ->
      client.getOutputStream().write("GET /other.wav HTTP/1.1\r\n\r\n".toByteArray())
      assertTrue(client.getInputStream().readBytes().decodeToString().contains("404"))
    }
    server.close()
  }
}
