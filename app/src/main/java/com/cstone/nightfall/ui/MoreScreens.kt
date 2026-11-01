package com.cstone.nightfall.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.cstone.nightfall.connect.Connections
import com.cstone.nightfall.connect.Reporter
import com.cstone.nightfall.connect.Target
import com.cstone.nightfall.data.Mix
import com.cstone.nightfall.data.Tonight
import com.cstone.nightfall.playback.Player
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun MixesScreen(
  mixes: List<Mix>,
  tonightId: String,
  onPick: (Mix) -> Unit,
  onEdit: (Mix) -> Unit,
  onNew: () -> Unit,
  onDelete: (Mix) -> Unit,
) {
  Column(
    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
      Text("Mixes", style = style(28, FontWeight.ExtraBold))
      TextAction("New mix", onClick = onNew)
    }
    Text("Tap a mix to make it tonight's. Your routine plays whichever one is set.", style = style(14, color = Muted))
    for (mix in mixes) {
      val on = mix.id == tonightId
      Card(Modifier.fillMaxWidth().heightIn(min = 84.dp), border = if (on) Amber else Line, background = if (on) Raised else Panel, onClick = { onPick(mix) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Column(Modifier.weight(1f)) {
            Text(mix.name, style = style(17, FontWeight.Bold))
            if (on) Text("Tonight", style = style(13, FontWeight.ExtraBold, Amber))
          }
          TextAction("Edit") { onEdit(mix) }
          if (mixes.size > 1) IconAction(Icons.TRASH, "Delete ${mix.name}", Faint) { onDelete(mix) }
        }
      }
    }
  }
}

@Composable
fun MeditateScreen(onBreathe: () -> Unit) {
  Column(
    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    Text("Meditate", style = style(28, FontWeight.ExtraBold))
    Heading("On your own")
    Card(Modifier.fillMaxWidth().heightIn(min = 92.dp), onClick = onBreathe) {
      Text("Slow breathing", style = style(16, FontWeight.Bold))
      Text("In for 4, out for 6. Plays over your mix.", style = style(13, color = Muted))
    }
    Heading("Guided")
    Card(Modifier.fillMaxWidth()) {
      Text("Guided sessions aren't recorded yet.", style = style(14, color = Muted))
    }
  }
}

private enum class Phase(val word: String, val seconds: Int, val size: Int) {
  IN("In", 4, 250),
  OUT("Out", 6, 120),
}

@Composable
fun BreatheScreen(onBack: () -> Unit) {
  var running by remember { mutableStateOf(false) }
  var phase by remember { mutableStateOf(Phase.IN) }
  LaunchedEffect(running) {
    if (!running) return@LaunchedEffect
    phase = Phase.IN
    while (isActive) {
      delay(phase.seconds * 1_000L)
      phase = if (phase == Phase.IN) Phase.OUT else Phase.IN
    }
  }
  val size by animateDpAsState(
    if (running) phase.size.dp else 120.dp,
    tween(phase.seconds * 1_000, easing = FastOutSlowInEasing),
    label = "breath",
  )
  Column(Modifier.fillMaxSize().padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
    TopBar("Slow breathing", onBack)
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
      Box(Modifier.size(290.dp).border(1.dp, Line, CircleShape), contentAlignment = Alignment.Center) {
        Box(
          Modifier.size(size).background(androidx.compose.ui.graphics.Color(0xFF3A2A18), CircleShape).border(2.dp, Amber, CircleShape),
          contentAlignment = Alignment.Center,
        ) {
          Text(if (running) phase.word else "Ready", style = style(22, FontWeight.ExtraBold))
        }
      }
      Text("In for 4, out for 6", Modifier.padding(top = 24.dp), style = style(16, color = Muted))
    }
    PrimaryButton(if (running) "Pause" else "Begin", Modifier.navigationBarsPadding().padding(bottom = 16.dp)) { running = !running }
  }
}

private fun batteryOpen(context: Context) =
  context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)

private fun notificationsOn(context: Context) =
  context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

fun askToRemoveBatteryLimits(context: Context) {
  if (batteryOpen(context)) return
  context.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}")))
}

@Composable
fun RoutineScreen(mix: Mix, tonight: Tonight, onConnections: () -> Unit) {
  val context = LocalContext.current
  val refresh = remember { mutableIntStateOf(0) }
  LaunchedEffect(Unit) {
    while (true) {
      delay(3_000)
      refresh.intValue++
    }
  }
  val tick = refresh.intValue
  val background = remember(tick) { batteryOpen(context) }
  val notifications = remember(tick) { notificationsOn(context) }
  val connected = remember(tick) { Connections.haTarget(context) != null || Connections.n8nTarget(context) != null }
  val last = remember(tick) { Connections.lastStart(context) }
  Column(
    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Text("Routine", style = style(28, FontWeight.ExtraBold))
    Text("Your automation decides when sound starts. This is what it will play.", style = style(14, color = Muted))
    Card(Modifier.fillMaxWidth()) {
      Fact("Plays", mix.name)
      Fact("For", if (tonight.minutes == 0) "All night" else "${minutesLabel(tonight.minutes)}, ${tonight.fadeMinutes} min fade")
    }
    Heading("Ready check")
    Card(Modifier.fillMaxWidth()) {
      Check("Background start allowed", background)
      Check("Notifications allowed", notifications)
      Check("Connected to an automation", connected)
      Fact("Last automatic start", last.ifEmpty { "None yet" })
    }
    if (!background) PrimaryButton("Allow background start") { askToRemoveBatteryLimits(context) }
    OutlineButton("Connections", onClick = onConnections)
    OutlineButton("Play a 15 second test") { Player.start(context, 15) }
  }
}

@Composable
private fun Fact(name: String, value: String) {
  Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
    Text(name, Modifier.weight(1f), style = style(15, color = Muted))
    Text(value, style = style(15, FontWeight.Bold))
  }
}

@Composable
private fun Check(text: String, ok: Boolean) {
  Row(Modifier.heightIn(min = 36.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
    Glyph(if (ok) Icons.CHECK else Icons.CROSS, 20.dp, if (ok) Amber else Faint)
    Text(text, style = style(14, FontWeight.SemiBold, if (ok) Ink else Muted))
  }
}

@Composable
fun ConnectionsScreen(onBack: () -> Unit) {
  val context = LocalContext.current
  var haUrl by remember { mutableStateOf(Connections.get(context, Connections.HA_URL)) }
  var haWebhook by remember { mutableStateOf(Connections.get(context, Connections.HA_WEBHOOK)) }
  var n8nUrl by remember { mutableStateOf(Connections.get(context, Connections.N8N_URL)) }
  var n8nHeader by remember { mutableStateOf(Connections.get(context, Connections.N8N_HEADER)) }
  var n8nValue by remember { mutableStateOf(Connections.get(context, Connections.N8N_VALUE)) }
  fun save() {
    Connections.set(context, Connections.HA_URL, haUrl)
    Connections.set(context, Connections.HA_WEBHOOK, haWebhook)
    Connections.set(context, Connections.N8N_URL, n8nUrl)
    Connections.set(context, Connections.N8N_HEADER, n8nHeader)
    Connections.set(context, Connections.N8N_VALUE, n8nValue)
  }
  fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_LONG).show()
  fun test(target: Target?) {
    save()
    if (target == null) {
      toast("Fill in the details first")
      return
    }
    Reporter.test(context, target) { code ->
      android.os.Handler(android.os.Looper.getMainLooper()).post {
        toast(if (code in 200..299) "${target.name} connected" else "${target.name} failed (${if (code < 0) "no connection" else "HTTP $code"})")
      }
    }
  }
  fun copy(text: String) {
    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Nightfall", text))
    toast("Copied")
  }
  Column(
    Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    TopBar("Connections", { save(); onBack() })
    Text("All optional. Nightfall works without any of these.", style = style(14, color = Muted))
    Heading("Home Assistant")
    Text("Create an automation with a Webhook trigger, then enter its ID.", style = style(14, color = Muted))
    Field("Server URL", haUrl, "http://homeassistant.local:8123") { haUrl = it }
    Field("Webhook ID", haWebhook, secret = true) { haWebhook = it }
    OutlineButton("Test Home Assistant") { test(run { save(); Connections.haTarget(context) }) }
    Heading("n8n or any webhook")
    Text("Events are posted as JSON to this URL.", style = style(14, color = Muted))
    Field("Webhook URL", n8nUrl, "https://example.com/webhook/nightfall") { n8nUrl = it }
    Field("Header name, if needed", n8nHeader) { n8nHeader = it }
    Field("Header value", n8nValue, secret = true) { n8nValue = it }
    OutlineButton("Test n8n") { test(run { save(); Connections.n8nTarget(context) }) }
    Heading("Automation key")
    Text("Commands sent to this app must include this key.", style = style(14, color = Muted))
    OutlineButton("Copy start command") { copy(Connections.startCommand(context)) }
    OutlineButton("Copy key") { copy(Connections.automationKey(context)) }
    Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 16.dp), contentAlignment = Alignment.Center) {
      TextAction("Make a new key", Muted) {
        Connections.newAutomationKey(context)
        toast("New key made. Update your automations.")
      }
    }
  }
}

@Composable
private fun Field(label: String, value: String, hint: String = "", secret: Boolean = false, onChange: (String) -> Unit) {
  OutlinedTextField(
    value = value,
    onValueChange = onChange,
    label = { Text(label, style = style(14, color = Muted)) },
    placeholder = { if (hint.isNotEmpty()) Text(hint, style = style(14, color = Faint)) },
    singleLine = true,
    textStyle = style(15),
    visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
    modifier = Modifier.fillMaxWidth(),
    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Amber, unfocusedBorderColor = Line, cursorColor = Amber),
  )
}
