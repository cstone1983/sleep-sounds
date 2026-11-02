package com.cstone.nightfall.ui

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cstone.nightfall.connect.SpeakerSearch
import com.cstone.nightfall.data.Speaker
import com.cstone.nightfall.data.Tonight
import com.cstone.nightfall.playback.Player
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

private fun outputs(context: Context): List<AudioDeviceInfo> =
  context.getSystemService(AudioManager::class.java)
    .getDevices(AudioManager.GET_DEVICES_OUTPUTS)
    .filter {
      it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
        it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
        it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
        it.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
        it.type == AudioDeviceInfo.TYPE_BLE_HEADSET
    }

fun outputName(context: Context, id: Int): String =
  outputs(context).firstOrNull { it.id == id }?.productName?.toString() ?: "Phone speaker"

private val BLUETOOTH_NEEDS =
  if (Build.VERSION.SDK_INT >= 31) arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT) else emptyArray()

private fun bluetoothAllowed(context: Context) =
  BLUETOOTH_NEEDS.all { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }

private fun isAudio(device: BluetoothDevice) =
  device.bluetoothClass?.majorDeviceClass == BluetoothClass.Device.Major.AUDIO_VIDEO

@SuppressLint("MissingPermission")
private fun nameOf(device: BluetoothDevice): String = device.name.orEmpty()

/** Looks for Bluetooth audio devices while the screen is open. Android has no way to connect a paired one for us. */
@SuppressLint("MissingPermission")
@Composable
private fun BluetoothRows(connected: List<AudioDeviceInfo>) {
  val context = LocalContext.current
  val adapter = remember { context.getSystemService(BluetoothManager::class.java)?.adapter } ?: return
  var allowed by remember { mutableStateOf(bluetoothAllowed(context)) }
  var round by remember { mutableIntStateOf(0) }
  var searching by remember { mutableStateOf(false) }
  val nearby = remember { mutableStateListOf<BluetoothDevice>() }
  val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
    allowed = bluetoothAllowed(context)
  }
  val openSettings = { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }

  Heading("Bluetooth")
  if (!allowed) {
    Text("Nightfall needs permission to look for Bluetooth speakers.", style = style(14, color = Muted))
    TextAction("Find Bluetooth speakers") { ask.launch(BLUETOOTH_NEEDS) }
    return
  }
  DisposableEffect(allowed, round) {
    val receiver = object : BroadcastReceiver() {
      override fun onReceive(ignored: Context, intent: Intent) {
        when (intent.action) {
          BluetoothDevice.ACTION_FOUND -> {
            @Suppress("DEPRECATION")
            val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE) ?: return
            if (isAudio(device) && nameOf(device).isNotEmpty() && device.bondState == BluetoothDevice.BOND_NONE && nearby.none { it.address == device.address }) nearby.add(device)
          }
          android.bluetooth.BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> searching = false
          BluetoothDevice.ACTION_BOND_STATE_CHANGED -> nearby.removeAll { it.bondState != BluetoothDevice.BOND_NONE }
        }
      }
    }
    val filter = IntentFilter(BluetoothDevice.ACTION_FOUND).apply {
      addAction(android.bluetooth.BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
      addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
    }
    if (Build.VERSION.SDK_INT >= 33) context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED) else context.registerReceiver(receiver, filter)
    searching = adapter.startDiscovery()
    onDispose {
      adapter.cancelDiscovery()
      context.unregisterReceiver(receiver)
    }
  }
  val names = connected.map { it.productName.toString() }
  val paired = adapter.bondedDevices.orEmpty().filter { isAudio(it) && nameOf(it) !in names }
  for (device in paired) OutputRow(Icons.BLUETOOTH, nameOf(device), "Paired. Tap to connect in settings", false) { openSettings() }
  for (device in nearby) OutputRow(Icons.BLUETOOTH, nameOf(device), "Nearby. Tap to pair", false) { device.createBond() }
  if (paired.isEmpty() && nearby.isEmpty() && !searching) {
    Text("No Bluetooth speakers nearby. Put yours in pairing mode.", style = style(14, color = Muted))
  }
  Row(verticalAlignment = Alignment.CenterVertically) {
    Text(if (searching) "Searching for Bluetooth speakers" else "", Modifier.weight(1f), style = style(14, color = Muted))
    if (!searching) {
      TextAction("Search again") {
        nearby.clear()
        round++
      }
    }
  }
}

@Composable
fun OutputScreen(tonight: Tonight, onChange: (Tonight) -> Unit, onBack: () -> Unit) {
  val context = LocalContext.current
  val manager = remember { context.getSystemService(AudioManager::class.java) }
  var devices by remember { mutableStateOf(outputs(context)) }
  DisposableEffect(manager) {
    val callback = object : AudioDeviceCallback() {
      override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>) { devices = outputs(context) }
      override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>) { devices = outputs(context) }
    }
    manager.registerAudioDeviceCallback(callback, Handler(Looper.getMainLooper()))
    onDispose { manager.unregisterAudioDeviceCallback(callback) }
  }

  val speakers = remember { mutableStateListOf<Speaker>() }
  var searching by remember { mutableStateOf(true) }
  var round by remember { mutableIntStateOf(0) }
  LaunchedEffect(round) {
    searching = true
    val main = Handler(Looper.getMainLooper())
    withContext(Dispatchers.IO) {
      val scope = currentCoroutineContext()
      SpeakerSearch.search(stop = { !scope.isActive }) { speaker ->
        main.post { if (speakers.none { it.id == speaker.id }) speakers.add(speaker) }
      }
    }
    searching = false
  }

  fun pick(id: Int) {
    onChange(tonight.copy(outputId = id, speaker = null))
    Player.setSpeaker(null)
    Player.setOutput(if (id == 0) null else manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull { it.id == id })
  }
  fun pick(speaker: Speaker) {
    onChange(tonight.copy(outputId = 0, speaker = speaker))
    Player.setSpeaker(speaker)
  }

  val saved = tonight.speaker?.takeIf { mine -> speakers.none { it.id == mine.id } }
  Column(
    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding(),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    TopBar("Play sound on", onBack)
    Heading("This phone")
    OutputRow(Icons.PHONE, "Phone speaker", "Default", tonight.outputId == 0 && tonight.speaker == null) { pick(0) }
    if (devices.isNotEmpty()) Heading("Connected")
    for (device in devices) {
      OutputRow(Icons.BLUETOOTH, device.productName.toString(), "Connected", tonight.outputId == device.id) { pick(device.id) }
    }
    Heading("Wi-Fi speakers")
    if (saved != null) OutputRow(Icons.SPEAKER, saved.name, if (searching) "Looking for it" else "Not found right now", true) { pick(saved) }
    for (speaker in speakers) {
      OutputRow(Icons.SPEAKER, speaker.name, speaker.model.ifEmpty { "Wi-Fi speaker" }, tonight.speaker?.id == speaker.id) { pick(speaker) }
    }
    if (speakers.isEmpty() && saved == null && !searching) {
      Text("No Wi-Fi speakers found. Sonos and DLNA speakers on your network show here.", style = style(14, color = Muted))
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(if (searching) "Searching your Wi-Fi" else "", Modifier.weight(1f), style = style(14, color = Muted))
      if (!searching) TextAction("Search again") { round++ }
    }
    BluetoothRows(devices)
  }
}

@Composable
private fun OutputRow(icon: String, name: String, note: String, on: Boolean, onClick: () -> Unit) {
  Card(Modifier.fillMaxWidth().heightIn(min = 64.dp), border = if (on) Amber else Line, background = if (on) Raised else Panel, onClick = onClick) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      Glyph(icon, 24.dp)
      Column(Modifier.weight(1f)) {
        Text(name, style = style(16, FontWeight.Bold))
        Text(note, style = style(13, color = Muted))
      }
      if (on) Glyph(Icons.CHECK, 24.dp, Amber)
    }
  }
}
