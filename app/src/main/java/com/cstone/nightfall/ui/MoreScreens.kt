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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.cstone.nightfall.connect.Connections
import com.cstone.nightfall.connect.Reporter
import com.cstone.nightfall.connect.Target
import com.cstone.nightfall.data.GUIDED
import com.cstone.nightfall.data.Guided
import com.cstone.nightfall.data.Mix
import com.cstone.nightfall.data.Tonight
import com.cstone.nightfall.playback.PlayState
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
fun MeditateScreen(tonight: Tonight, onBreathe: () -> Unit, onGuided: (Guided) -> Unit, onChange: (Tonight) -> Unit) {
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
    for (session in GUIDED) {
      Card(Modifier.fillMaxWidth().heightIn(min = 76.dp), onClick = { onGuided(session) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Column(Modifier.weight(1f)) {
            Text(session.title, style = style(16, FontWeight.Bold))
            Text("${session.minutes} min · ${session.note}", style = style(13, color = Muted))
          }
          Box(Modifier.size(44.dp).background(Raised, CircleShape), contentAlignment = Alignment.Center) {
            Glyph(Icons.PLAY, 20.dp, Amber, filled = true)
          }
        }
      }
    }
    Card(
      Modifier.fillMaxWidth().heightIn(min = 64.dp),
      onClick = { onChange(tonight.copy(mixUnderGuided = !tonight.mixUnderGuided)) },
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Keep tonight's mix playing underneath", Modifier.weight(1f), style = style(15, FontWeight.SemiBold))
        Text(if (tonight.mixUnderGuided) "On" else "Off", style = style(14, FontWeight.ExtraBold, if (tonight.mixUnderGuided) Amber else Faint))
      }
    }
    Text("Stored on this phone. Sessions play with no internet and no API use.", style = style(13, color = Muted))
  }
}

@Composable
fun GuidedPlayerScreen(state: PlayState, tonight: Tonight, onBack: () -> Unit) {
  var wasPlaying by remember { mutableStateOf(state.active) }
  LaunchedEffect(state.active) {
    if (state.active) wasPlaying = true else if (wasPlaying) onBack()
  }
  val speaking = state.guideTitle.isNotEmpty()
  Column(
    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
    verticalArrangement = Arrangement.spacedBy(18.dp),
  ) {
    TopBar("Guided", onBack)
    Column(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
      Text(if (speaking) state.guideTitle else "Session finished", style = style(18, FontWeight.Bold, Muted))
      Text(
        if (speaking) "${(state.voiceLeftMs + 59_999) / 60_000} min" else "Resting",
        style = style(62, FontWeight.ExtraBold, line = 72),
      )
      Text(
        when {
          state.paused -> "Paused"
          !state.active -> "Starting"
          speaking -> "Left in this session"
          state.layers.isEmpty() -> "Ending now"
          else -> "Your sounds keep playing"
        },
        style = style(16, color = Muted),
      )
    }
    if (speaking && state.voiceTotalMs > 0) {
      Box(Modifier.fillMaxWidth().height(8.dp).background(Line, RoundedCornerShape(4.dp))) {
        Box(
          Modifier
            .fillMaxWidth(((state.voiceTotalMs - state.voiceLeftMs).toFloat() / state.voiceTotalMs).coerceIn(0f, 1f))
            .fillMaxHeight()
            .background(Amber, RoundedCornerShape(4.dp)),
        )
      }
    }
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
      Box(
        Modifier
          .size(112.dp)
          .background(Amber, CircleShape)
          .semantics { contentDescription = if (state.paused) "Play" else "Pause" }
          .clickable(role = Role.Button) { Player.togglePause() },
        contentAlignment = Alignment.Center,
      ) {
        Glyph(if (state.paused) Icons.PLAY else Icons.PAUSE, 44.dp, OnAmber, filled = true)
      }
    }
    Level("Voice", if (state.active) state.voiceLevel else tonight.voiceLevel, Player::setVoiceLevel)
    Level("Sounds", if (state.active) state.soundLevel else tonight.gain, Player::setSoundLevel)
    Box(Modifier.fillMaxWidth().navigationBarsPadding(), contentAlignment = Alignment.Center) {
      TextAction("Stop", Muted) { Player.stop() }
    }
  }
}

/** Keeps the thumb under the finger while a change travels to the service and back. */
@Composable
private fun Level(label: String, value: Int, onChange: (Int) -> Unit) {
  var dragging by remember { mutableStateOf<Float?>(null) }
  Column {
    Text(label, style = style(15, FontWeight.Bold))
    Slider(
      value = dragging ?: value.toFloat(),
      onValueChange = {
        dragging = it
        onChange(it.toInt())
      },
      onValueChangeFinished = { dragging = null },
      valueRange = 0f..100f,
      modifier = Modifier.height(44.dp).semantics { contentDescription = "$label level" },
      colors = SliderDefaults.colors(thumbColor = Amber, activeTrackColor = Amber, inactiveTrackColor = Line),
    )
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
fun RoutineScreen(mix: Mix, tonight: Tonight) {
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
  val remote = remember(tick) { Connections.remoteAllowed(context) }
  val last = remember(tick) { Connections.lastStart(context) }
  Column(
    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Text("Routine", style = style(28, FontWeight.ExtraBold))
    Text("Home Assistant or n8n decides when sound starts. This is what it will play. Set up connections in Settings.", style = style(14, color = Muted))
    Card(Modifier.fillMaxWidth()) {
      Fact("Plays", mix.name)
      Fact("For", if (tonight.minutes == 0) "All night" else "${minutesLabel(tonight.minutes)}, ${tonight.fadeMinutes} min fade")
    }
    Heading("Ready check")
    Card(Modifier.fillMaxWidth()) {
      Check("Background start allowed", background)
      Check("Notifications allowed", notifications)
      Check("Connected to an automation", connected)
      Check("Remote control allowed", remote)
      Fact("Last automatic start", last.ifEmpty { "None yet" })
    }
    if (!background) PrimaryButton("Allow background start") { askToRemoveBatteryLimits(context) }
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

private enum class Help { HOME_ASSISTANT, N8N }

/** Connections live here so any setup can use them. Every field saves as it's typed. */
@Composable
fun SettingsScreen() {
  val context = LocalContext.current
  var haUrl by remember { mutableStateOf(Connections.get(context, Connections.HA_URL)) }
  var haWebhook by remember { mutableStateOf(Connections.get(context, Connections.HA_WEBHOOK)) }
  var n8nUrl by remember { mutableStateOf(Connections.get(context, Connections.N8N_URL)) }
  var n8nHeader by remember { mutableStateOf(Connections.get(context, Connections.N8N_HEADER)) }
  var n8nValue by remember { mutableStateOf(Connections.get(context, Connections.N8N_VALUE)) }
  var remote by remember { mutableStateOf(Connections.remoteAllowed(context)) }
  var help by remember { mutableStateOf<Help?>(null) }
  fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_LONG).show()
  fun test(target: Target?) {
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
    Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Text("Settings", style = style(28, FontWeight.ExtraBold))
    Text("Connections are optional. Nightfall works without them.", style = style(14, color = Muted))
    Card(
      Modifier.fillMaxWidth().heightIn(min = 64.dp),
      onClick = {
        remote = !remote
        Connections.setRemoteAllowed(context, remote)
      },
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Allow remote control", Modifier.weight(1f), style = style(15, FontWeight.SemiBold))
        Text(if (remote) "On" else "Off", style = style(14, FontWeight.ExtraBold, if (remote) Amber else Faint))
      }
    }
    Text("When off, Home Assistant and n8n can't start or control Nightfall.", style = style(13, color = Muted))
    SectionHeading("Home Assistant", "How to connect Home Assistant") { help = Help.HOME_ASSISTANT }
    Field("Server URL", haUrl, "http://homeassistant.local:8123") { haUrl = it; Connections.set(context, Connections.HA_URL, it) }
    Field("Webhook ID", haWebhook, secret = true) { haWebhook = it; Connections.set(context, Connections.HA_WEBHOOK, it) }
    OutlineButton("Test Home Assistant") { test(Connections.haTarget(context)) }
    SectionHeading("n8n", "How to connect n8n") { help = Help.N8N }
    Field("Webhook URL", n8nUrl, "https://example.com/webhook/nightfall") { n8nUrl = it; Connections.set(context, Connections.N8N_URL, it) }
    Field("Header name, if needed", n8nHeader) { n8nHeader = it; Connections.set(context, Connections.N8N_HEADER, it) }
    Field("Header value", n8nValue, secret = true) { n8nValue = it; Connections.set(context, Connections.N8N_VALUE, it) }
    OutlineButton("Test n8n") { test(Connections.n8nTarget(context)) }
    Heading("Automation key")
    Text("Commands sent to this app must include this key.", style = style(14, color = Muted))
    OutlineButton("Copy start command") { copy(Connections.startCommand(context)) }
    OutlineButton("Copy key") { copy(Connections.automationKey(context)) }
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
      TextAction("Make a new key", Muted) {
        Connections.newAutomationKey(context)
        toast("New key made. Update your automations.")
      }
    }
  }
  when (help) {
    Help.HOME_ASSISTANT -> HelpDialog(
      title = "Connect Home Assistant",
      onCopy = { copy(Connections.startCommand(context)) },
      onClose = { help = null },
    ) {
      HelpHeading("Start Nightfall from Home Assistant")
      Step(1, "Install the Home Assistant Companion app on this phone and sign in.")
      Step(2, "In Nightfall, allow background start on the Routine tab.")
      Step(3, "Tap Copy start command below, and paste it into a script or automation as a YAML action.")
      Step(4, "Replace YOUR_PHONE with your phone's name. Look for notify.mobile_app_ in Home Assistant's list of actions.")
      HelpHeading("See what Nightfall is doing (optional)")
      Step(5, "Create an automation with a Webhook trigger, and allow POST.")
      Step(6, "Copy the webhook ID into Webhook ID here, and enter your server URL.")
      Step(7, "If this phone leaves your home network, turn off \"Only accessible from the local network\" on the trigger, and use a URL that works away from home.")
      Step(8, "Tap Test Home Assistant. The automation should run, and the event name is in trigger.json.event.")
    }
    Help.N8N -> HelpDialog(
      title = "Connect n8n",
      onCopy = { copy(Connections.startCommand(context)) },
      onClose = { help = null },
    ) {
      HelpHeading("See what Nightfall is doing")
      Step(1, "Add a Webhook node to a workflow, and set the method to POST.")
      Step(2, "Publish the workflow, then copy the Production URL into Webhook URL here.")
      Step(3, "If your webhook checks a header, enter the header name and value here.")
      Step(4, "Tap Test n8n. The workflow should run, and the event name is in body.event.")
      HelpHeading("Start Nightfall from n8n")
      Step(5, "n8n can't reach this phone directly, so it sends the command through Home Assistant.")
      Step(6, "Install the Companion app on this phone and allow background start.")
      Step(7, "Add a Home Assistant node that calls the notify.mobile_app_ action for your phone.")
      Step(8, "Tap Copy start command below, and use its message and data values in that node.")
    }
    null -> Unit
  }
}

@Composable
private fun SectionHeading(text: String, helpLabel: String, onHelp: () -> Unit) {
  Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
    Text(text, Modifier.weight(1f), style = style(18, FontWeight.ExtraBold))
    IconAction(Icons.INFO, helpLabel, Amber, onHelp)
  }
}

@Composable
private fun HelpHeading(text: String) {
  Text(text, Modifier.padding(top = 8.dp), style = style(15, FontWeight.ExtraBold))
}

@Composable
private fun Step(number: Int, text: String) {
  Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
    Text("$number", style = style(14, FontWeight.ExtraBold, Amber, line = 21))
    Text(text, style = style(14, color = Muted, line = 21))
  }
}

@Composable
private fun HelpDialog(title: String, onCopy: () -> Unit, onClose: () -> Unit, steps: @Composable () -> Unit) {
  AlertDialog(
    onDismissRequest = onClose,
    containerColor = Panel,
    title = { Text(title, style = style(20, FontWeight.ExtraBold)) },
    text = {
      Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        steps()
      }
    },
    confirmButton = { TextAction("Close", onClick = onClose) },
    dismissButton = { TextAction("Copy start command", Muted, onCopy) },
  )
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
