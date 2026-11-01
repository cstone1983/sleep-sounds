package com.cstone.nightfall.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.cstone.nightfall.data.Mix
import com.cstone.nightfall.data.Store
import com.cstone.nightfall.playback.PlayState
import com.cstone.nightfall.playback.Player

sealed interface Route {
  data class Main(val tab: Tab) : Route
  data object Mixer : Route
  data object Timer : Route
  data object Output : Route
  data object NowPlaying : Route
  data object NightDim : Route
  data object Breathe : Route
  data object Guided : Route
}

@Composable
fun NightfallApp(start: Route = Route.Main(Tab.TONIGHT), previewState: PlayState? = null) {
  val context = LocalContext.current
  val store = remember { Store.get(context) }
  val live by Player.state.collectAsState()
  val state = previewState ?: live
  val tonight by store.tonight.collectAsState()
  val mixes by store.mixes.collectAsState()
  val stack = remember { mutableStateListOf(start) }
  var draft by remember { mutableStateOf<Mix?>(null) }

  val route = stack.last()
  fun go(next: Route) { stack.add(next) }
  fun back() { if (stack.size > 1) stack.removeAt(stack.lastIndex) }
  fun pickTab(tab: Tab) {
    stack.clear()
    stack.add(Route.Main(tab))
  }
  fun openMixer(mix: Mix) {
    draft = mix
    go(Route.Mixer)
  }

  BackHandler(enabled = stack.size > 1) { back() }

  NightfallTheme {
    Box(Modifier.fillMaxSize().background(Ground), contentAlignment = Alignment.TopCenter) {
      Column(Modifier.fillMaxSize().widthIn(max = 600.dp).statusBarsPadding()) {
        when (route) {
          is Route.Main -> {
            Box(Modifier.weight(1f).fillMaxWidth()) {
              when (route.tab) {
                Tab.TONIGHT -> TonightScreen(
                  state = state,
                  mix = store.tonightMix(),
                  tonight = tonight,
                  onStart = { Player.start(context); go(Route.NowPlaying) },
                  onOpenPlaying = { go(Route.NowPlaying) },
                  onMixer = { openMixer(store.tonightMix()) },
                  onTimer = { go(Route.Timer) },
                  onOutput = { go(Route.Output) },
                  onRoutine = { pickTab(Tab.ROUTINE) },
                  onBreathe = { go(Route.Breathe) },
                  onMeditate = { pickTab(Tab.MEDITATE) },
                )
                Tab.MIXES -> MixesScreen(
                  mixes = mixes,
                  tonightId = tonight.mixId,
                  onPick = { store.setTonight(tonight.copy(mixId = it.id)) },
                  onEdit = ::openMixer,
                  onNew = { openMixer(Mix(store.newMixId(), "New mix", emptyList())) },
                  onDelete = { store.deleteMix(it.id) },
                )
                Tab.MEDITATE -> MeditateScreen(
                  tonight = tonight,
                  onBreathe = { go(Route.Breathe) },
                  onGuided = {
                    Player.startGuided(context, it.id)
                    go(Route.Guided)
                  },
                  onChange = store::setTonight,
                )
                Tab.ROUTINE -> RoutineScreen(mix = store.tonightMix(), tonight = tonight)
                Tab.SETTINGS -> SettingsScreen()
              }
            }
            BottomTabs(route.tab, ::pickTab)
          }
          Route.Mixer -> draft?.let { current ->
            MixerScreen(
              mix = current,
              state = state,
              onChange = { next ->
                draft = next
                if (state.active) Player.updateLayers(next.layers)
              },
              onSave = {
                store.saveMix(current)
                store.setTonight(tonight.copy(mixId = current.id))
                back()
              },
              onBack = ::back,
              onTimer = { go(Route.Timer) },
              onOutput = { go(Route.Output) },
              onStart = {
                store.saveMix(current)
                store.setTonight(tonight.copy(mixId = current.id))
                if (!state.active) Player.start(context)
                go(Route.NowPlaying)
              },
            )
          }
          Route.Timer -> TimerScreen(tonight, onChange = store::setTonight, onDone = ::back)
          Route.Output -> OutputScreen(tonight, onChange = store::setTonight, onBack = ::back)
          Route.NowPlaying -> NowPlayingScreen(
            state = state,
            onBack = ::back,
            onDim = { go(Route.NightDim) },
            onOutput = { go(Route.Output) },
            onMixer = { openMixer(store.tonightMix()) },
          )
          Route.NightDim -> NightDimScreen(state = state, onWake = ::back)
          Route.Breathe -> BreatheScreen(onBack = ::back)
          Route.Guided -> GuidedPlayerScreen(state = state, tonight = tonight, onBack = ::back)
        }
      }
    }
  }
}
