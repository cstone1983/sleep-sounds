package com.cstone.nightfall.ui

import android.app.Activity
import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.cstone.nightfall.audio.Category
import com.cstone.nightfall.audio.SOUNDS
import com.cstone.nightfall.audio.soundById
import com.cstone.nightfall.data.Layer
import com.cstone.nightfall.data.Mix
import com.cstone.nightfall.data.Tonight
import com.cstone.nightfall.playback.PlayState
import com.cstone.nightfall.playback.Player
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun clock(time: Long, pattern: String = "h:mm a") = SimpleDateFormat(pattern, Locale.getDefault()).format(Date(time))

private fun leftLabel(ms: Long): String = "${(ms + 59_999) / 60_000} min"

private fun soundList(mix: Mix) =
  mix.layers.mapNotNull { soundById(it.soundId)?.name }.joinToString(", ").ifEmpty { "No sounds yet" }

@Composable
fun TonightScreen(
  state: PlayState,
  mix: Mix,
  tonight: Tonight,
  onStart: () -> Unit,
  onOpenPlaying: () -> Unit,
  onMixer: () -> Unit,
  onTimer: () -> Unit,
  onOutput: () -> Unit,
  onRoutine: () -> Unit,
  onBreathe: () -> Unit,
  onMeditate: () -> Unit,
) {
  var now by remember { mutableStateOf(System.currentTimeMillis()) }
  LaunchedEffect(Unit) {
    while (true) {
      delay(20_000)
      now = System.currentTimeMillis()
    }
  }
  Column(
    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
      Text("Tonight", style = style(30, FontWeight.ExtraBold))
      Text(clock(now), style = style(16, FontWeight.SemiBold, Muted))
    }
    Card(padding = androidx.compose.foundation.layout.PaddingValues(14.dp, 10.dp), onClick = onRoutine, modifier = Modifier.fillMaxWidth()) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(Amber))
        Text("Your automation starts this", style = style(14, FontWeight.Bold))
      }
    }
    Card(Modifier.fillMaxWidth(), padding = androidx.compose.foundation.layout.PaddingValues(20.dp)) {
      Text(if (state.active) "Playing now" else "Tonight's mix", style = style(13, FontWeight.Bold, Amber))
      Text(
        if (state.active) state.title else mix.name,
        Modifier.padding(top = 6.dp),
        style = style(27, FontWeight.ExtraBold, line = 34),
      )
      Text(soundList(mix), Modifier.padding(top = 4.dp), style = style(15, color = Muted))
      Row(Modifier.padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Chip(minutesLabel(tonight.minutes), if (tonight.minutes == 0) "No timer" else "${tonight.fadeMinutes} min fade", Modifier.weight(1f), onTimer)
        Chip(outputName(LocalContext.current, tonight.outputId), "Change output", Modifier.weight(1f), onOutput)
      }
      Spacer(Modifier.height(18.dp))
      if (state.active) {
        PrimaryButton(if (state.paused) "Paused" else "${leftLabel(state.leftMs)} left", height = 88.dp, onClick = onOpenPlaying)
      } else {
        PrimaryButton("Start", height = 88.dp, onClick = onStart)
      }
      Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
        TextAction("Change the mix", onClick = onMixer)
      }
    }
    Heading("Wind down first")
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      Mini("Slow breathing", "5 min", Modifier.weight(1f), onBreathe)
      Mini("Meditate", "Breathing", Modifier.weight(1f), onMeditate)
    }
  }
}

@Composable
private fun Chip(title: String, note: String, modifier: Modifier, onClick: () -> Unit) {
  Card(modifier.heightIn(min = 64.dp), padding = androidx.compose.foundation.layout.PaddingValues(12.dp, 10.dp), onClick = onClick) {
    Text(title, style = style(15, FontWeight.Bold), maxLines = 1)
    Text(note, style = style(12, color = Muted), maxLines = 1)
  }
}

@Composable
private fun Mini(title: String, note: String, modifier: Modifier, onClick: () -> Unit) {
  Card(modifier.heightIn(min = 72.dp), background = Ground, onClick = onClick) {
    Text(title, style = style(15, FontWeight.Bold))
    Text(note, style = style(13, color = Muted))
  }
}

@Composable
fun MixerScreen(
  mix: Mix,
  state: PlayState,
  onChange: (Mix) -> Unit,
  onSave: () -> Unit,
  onBack: () -> Unit,
  onTimer: () -> Unit,
  onOutput: () -> Unit,
  onStart: () -> Unit,
) {
  var category by remember { mutableStateOf(Category.RAIN) }
  fun update(layer: Layer) = onChange(mix.copy(layers = mix.layers.map { if (it.soundId == layer.soundId) layer else it }))
  Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      TopBar(mix.name, onBack) { TextAction("Save", onClick = onSave) }
      Card(Modifier.fillMaxWidth().weight(1f, fill = false).heightIn(max = 320.dp), padding = androidx.compose.foundation.layout.PaddingValues(12.dp, 10.dp)) {
        Text("Playing now · ${mix.layers.size}".takeIf { state.active } ?: "In this mix · ${mix.layers.size}", style = style(13, FontWeight.Bold, Muted))
        if (mix.layers.isEmpty()) {
          Text("Tap a sound below to add it.", Modifier.padding(vertical = 8.dp), style = style(14, color = Muted))
        }
        Column(Modifier.verticalScroll(rememberScrollState())) {
          for (layer in mix.layers) LayerRow(layer, ::update)
        }
      }
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (option in Category.entries) {
          val on = option == category
          Box(
            Modifier
              .weight(1f)
              .height(44.dp)
              .clip(RoundedCornerShape(22.dp))
              .background(if (on) Raised else Color.Transparent)
              .border(1.dp, if (on) LineStrong else Line, RoundedCornerShape(22.dp))
              .clickable(role = Role.Tab) { category = option },
            contentAlignment = Alignment.Center,
          ) {
            Text(option.label, style = style(14, FontWeight.Bold, if (on) Ink else Muted))
          }
        }
      }
      LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxWidth().padding(bottom = 92.dp).height(198.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        items(SOUNDS.filter { it.category == category }, key = { it.id }) { sound ->
          val on = mix.layers.any { it.soundId == sound.id }
          Column(
            Modifier
              .height(94.dp)
              .clip(RoundedCornerShape(16.dp))
              .background(if (on) Amber else Panel)
              .border(1.dp, if (on) Amber else Line, RoundedCornerShape(16.dp))
              .clickable(role = Role.Button) {
                val next = if (on) mix.layers.filterNot { it.soundId == sound.id } else mix.layers + Layer(sound.id)
                onChange(mix.copy(layers = next))
              },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
          ) {
            Glyph(sound.icon, 30.dp, if (on) OnAmber else Ink)
            Text(
              sound.name,
              Modifier.padding(top = 6.dp, start = 4.dp, end = 4.dp),
              style = style(13, FontWeight.Bold, if (on) OnAmber else Ink, line = 16),
              textAlign = TextAlign.Center,
            )
          }
        }
      }
    }
    Row(
      Modifier
        .align(Alignment.BottomCenter)
        .navigationBarsPadding()
        .padding(bottom = 16.dp)
        .clip(RoundedCornerShape(36.dp))
        .background(Raised)
        .border(1.dp, LineStrong, RoundedCornerShape(36.dp))
        .padding(6.dp),
      horizontalArrangement = Arrangement.spacedBy(6.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      PillIcon(Icons.TIMER, "Sleep timer", onTimer)
      Box(
        Modifier
          .size(width = 76.dp, height = 56.dp)
          .clip(RoundedCornerShape(28.dp))
          .background(Amber)
          .semantics { contentDescription = "Start" }
          .clickable(onClick = onStart),
        contentAlignment = Alignment.Center,
      ) {
        Glyph(Icons.PLAY, 28.dp, OnAmber, filled = true)
      }
      PillIcon(Icons.PHONE, "Output", onOutput)
    }
  }
}

@Composable
private fun PillIcon(path: String, label: String, onClick: () -> Unit) {
  Box(
    Modifier.size(56.dp).semantics { contentDescription = label }.clickable(role = Role.Button, onClick = onClick),
    contentAlignment = Alignment.Center,
  ) {
    Glyph(path, 26.dp)
  }
}

@Composable
private fun LayerRow(layer: Layer, onChange: (Layer) -> Unit) {
  val sound = soundById(layer.soundId) ?: return
  Column {
    Row(Modifier.heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
      Glyph(sound.icon, 22.dp, Amber)
      Text(sound.name, Modifier.weight(1f).padding(start = 8.dp), style = style(15, FontWeight.Bold))
      TextAction("Drift", if (layer.drift) Amber else Faint) { onChange(layer.copy(drift = !layer.drift)) }
      Spacer(Modifier.size(10.dp))
      TextAction("Soften", if (layer.soften) Amber else Faint) { onChange(layer.copy(soften = !layer.soften)) }
    }
    Slider(
      value = layer.volume.toFloat(),
      onValueChange = { onChange(layer.copy(volume = it.toInt())) },
      valueRange = 0f..100f,
      modifier = Modifier.height(44.dp).semantics { contentDescription = "${sound.name} volume" },
      colors = SliderDefaults.colors(thumbColor = Amber, activeTrackColor = Amber, inactiveTrackColor = Line),
    )
  }
}

private val LENGTHS = listOf(15, 30, 45, 60, 90, 0)
private val FADES = listOf(2, 5, 10, 20)

@Composable
fun TimerScreen(tonight: Tonight, onChange: (Tonight) -> Unit, onDone: () -> Unit) {
  val ends = System.currentTimeMillis() + tonight.minutes * 60_000L
  Column(Modifier.fillMaxSize().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
    TopBar("Sleep timer", onDone)
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
      Text(minutesLabel(tonight.minutes), style = style(58, FontWeight.ExtraBold, line = 66))
      Text(
        if (tonight.minutes == 0) "Plays until you stop it" else "Silent by about ${clock(ends)}",
        style = style(16, color = Muted),
      )
    }
    Heading("Play for")
    OptionGrid(LENGTHS, 3, 72, tonight.minutes, ::minutesLabel) { onChange(tonight.copy(minutes = it)) }
    Heading("Fade out over")
    OptionGrid(FADES, 4, 64, tonight.fadeMinutes, { "$it min" }) { onChange(tonight.copy(fadeMinutes = it)) }
    Text(
      "This is the one timer, for every mix. It keeps running if the app closes.",
      style = style(14, color = Muted),
    )
    Spacer(Modifier.weight(1f))
    PrimaryButton("Done", Modifier.navigationBarsPadding().padding(bottom = 16.dp), onClick = onDone)
  }
}

@Composable
private fun OptionGrid(values: List<Int>, columns: Int, height: Int, selected: Int, label: (Int) -> String, onPick: (Int) -> Unit) {
  Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    for (row in values.chunked(columns)) {
      Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        for (value in row) {
          val on = value == selected
          Box(
            Modifier
              .weight(1f)
              .height(height.dp)
              .clip(RoundedCornerShape(16.dp))
              .background(if (on) Amber else Panel)
              .border(1.dp, if (on) Amber else Line, RoundedCornerShape(16.dp))
              .clickable(role = Role.Button) { onPick(value) },
            contentAlignment = Alignment.Center,
          ) {
            Text(label(value), style = style(16, FontWeight.ExtraBold, if (on) OnAmber else Ink))
          }
        }
      }
    }
  }
}

@Composable
fun NowPlayingScreen(state: PlayState, onBack: () -> Unit, onDim: () -> Unit, onOutput: () -> Unit, onMixer: () -> Unit) {
  val context = LocalContext.current
  // The session ended, so there's nothing left to show here.
  var seen by remember { mutableStateOf(state.active) }
  LaunchedEffect(state.active) {
    if (state.active) seen = true else if (seen) onBack()
  }
  Column(
    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
    verticalArrangement = Arrangement.spacedBy(18.dp),
  ) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
      IconAction(Icons.DOWN, "Back", onClick = onBack)
      TextAction(outputName(context, 0), onClick = onOutput)
    }
    Column(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
      Text(state.title.ifEmpty { "Starting" }, style = style(16, FontWeight.Bold, Muted))
      Text(
        when {
          !state.active -> "..."
          state.hasTimer -> leftLabel(state.leftMs)
          else -> "Playing"
        },
        style = style(62, FontWeight.ExtraBold, line = 72),
      )
      Text(
        when {
          state.paused -> "Paused. The timer waits for you."
          state.hasTimer -> "Silent by about ${clock(System.currentTimeMillis() + state.leftMs)}"
          else -> "No timer set"
        },
        style = style(16, color = Muted),
      )
    }
    Box(Modifier.fillMaxWidth().padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
      Box(
        Modifier
          .size(112.dp)
          .clip(CircleShape)
          .background(Amber)
          .semantics { contentDescription = if (state.paused) "Play" else "Pause" }
          .clickable(role = Role.Button) { Player.togglePause() },
        contentAlignment = Alignment.Center,
      ) {
        Glyph(if (state.paused) Icons.PLAY else Icons.PAUSE, 44.dp, OnAmber, filled = true)
      }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      Side("Add 15 min", Modifier.weight(1f)) { Player.addTime() }
      Side("Dim screen", Modifier.weight(1f), onDim)
    }
    Card(Modifier.fillMaxWidth(), onClick = onMixer) {
      for (layer in state.layers) {
        val sound = soundById(layer.soundId) ?: continue
        Row(Modifier.heightIn(min = 36.dp), verticalAlignment = Alignment.CenterVertically) {
          Text(sound.name, Modifier.weight(0.42f), style = style(15, FontWeight.SemiBold))
          Box(Modifier.weight(0.58f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(Line)) {
            Box(Modifier.fillMaxWidth(layer.volume / 100f).fillMaxHeight().background(Amber))
          }
        }
      }
    }
    Box(Modifier.fillMaxWidth().navigationBarsPadding(), contentAlignment = Alignment.Center) {
      TextAction("Stop for tonight", Muted) { Player.stop() }
    }
  }
}

@Composable
private fun Side(text: String, modifier: Modifier, onClick: () -> Unit) {
  Card(modifier.height(72.dp), onClick = onClick) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
      Text(text, style = style(15, FontWeight.Bold))
    }
  }
}

/** Lowers this window's brightness and keeps the clock on until the user wakes it. */
@Composable
private fun DimWindow() {
  val activity = LocalContext.current as? Activity ?: return
  DisposableEffect(Unit) {
    val window = activity.window
    val before = window.attributes.screenBrightness
    window.attributes = window.attributes.apply { screenBrightness = 0.01f }
    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    onDispose {
      window.attributes = window.attributes.apply { screenBrightness = before }
      window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
  }
}

@Composable
fun NightDimScreen(state: PlayState, onWake: () -> Unit) {
  DimWindow()
  var black by remember { mutableStateOf(false) }
  var now by remember { mutableStateOf(System.currentTimeMillis()) }
  LaunchedEffect(Unit) {
    while (true) {
      delay(10_000)
      now = System.currentTimeMillis()
    }
  }
  val background = if (black) Color.Black else Color(0xFF120A06)
  val ink = if (black) Color(0xFFB8703C) else Color(0xFFD08A52)
  val edge = if (black) Color(0xFF5A3A22) else Color(0xFF6B4426)
  Column(Modifier.fillMaxSize().background(background).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
      Text(clock(now, "h:mm"), style = style(92, FontWeight.Bold, ink, line = 100))
      Text(
        if (state.active) "${state.title} · ${leftLabel(state.leftMs)} left" else "Nothing playing",
        style = style(18, FontWeight.SemiBold, ink),
      )
    }
    Row(Modifier.navigationBarsPadding(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      DimButton("Wake screen", ink, edge, Modifier.weight(1f), onWake)
      DimButton(if (black) "Warm dark" else "True black", ink, edge, Modifier.weight(1f)) { black = !black }
    }
  }
}

@Composable
private fun DimButton(text: String, ink: Color, edge: Color, modifier: Modifier, onClick: () -> Unit) {
  Box(
    modifier
      .height(72.dp)
      .clip(RoundedCornerShape(20.dp))
      .border(1.dp, edge, RoundedCornerShape(20.dp))
      .clickable(role = Role.Button, onClick = onClick),
    contentAlignment = Alignment.Center,
  ) {
    Text(text, style = style(17, FontWeight.Bold, ink))
  }
}

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

@Composable
fun OutputScreen(tonight: Tonight, onChange: (Tonight) -> Unit, onBack: () -> Unit) {
  val context = LocalContext.current
  val devices = remember { outputs(context) }
  val manager = remember { context.getSystemService(AudioManager::class.java) }
  fun pick(id: Int) {
    onChange(tonight.copy(outputId = id))
    Player.setOutput(if (id == 0) null else manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull { it.id == id })
  }
  Column(Modifier.fillMaxSize().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    TopBar("Play sound on", onBack)
    Heading("This phone")
    OutputRow(Icons.PHONE, "Phone speaker", "Default", tonight.outputId == 0) { pick(0) }
    Heading("Connected")
    if (devices.isEmpty()) {
      Text("Connect Bluetooth or wired headphones and they'll show here.", style = style(14, color = Muted))
    }
    for (device in devices) {
      OutputRow(Icons.BLUETOOTH, device.productName.toString(), "Connected", tonight.outputId == device.id) { pick(device.id) }
    }
    Heading("Room speakers")
    Card(Modifier.fillMaxWidth()) {
      Text("Sonos and other room speakers aren't connected yet.", style = style(14, color = Muted))
    }
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
