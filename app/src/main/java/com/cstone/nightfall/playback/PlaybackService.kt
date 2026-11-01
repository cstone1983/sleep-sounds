package com.cstone.nightfall.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.AudioTimestamp
import android.media.MediaMetadata
import android.media.MediaPlayer
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import com.cstone.nightfall.MainActivity
import com.cstone.nightfall.R
import com.cstone.nightfall.audio.MixEngine
import com.cstone.nightfall.connect.Connections
import com.cstone.nightfall.connect.Reporter
import com.cstone.nightfall.data.Layer
import com.cstone.nightfall.data.Mix
import com.cstone.nightfall.data.Store
import com.cstone.nightfall.data.Guided
import com.cstone.nightfall.data.guidedById
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val ACTION_TOGGLE = "toggle"
private const val ACTION_ADD = "add15"
private const val ACTION_STOP = "stop"
private const val ADD_MS = 15 * 60_000L
private const val CHANNEL = "playback"

class PlaybackService : Service() {
  private val handler = Handler(Looper.getMainLooper())
  private lateinit var audio: AudioManager
  private var focus: AudioFocusRequest? = null
  private var engine: MixEngine? = null
  private var session: MediaSession? = null
  private var wakeLock: PowerManager.WakeLock? = null

  private var running = false
  private var paused = false
  private var confirmed = false
  private var automation = false
  private var focusResult = -1
  private var stopReason = "stopped"

  private var title = ""
  private var layers = emptyList<Layer>()
  private var gain = 0.45f
  private var hasTimer = false
  private var totalMs = 0L
  private var remainingMs = 0L
  private var resumedAt = 0L
  private var fadeMs = 0L
  private var voice: MediaPlayer? = null
  private var guide: Guided? = null
  private var voiceLevel = 0.8f
  private lateinit var attributes: AudioAttributes

  private val tick = object : Runnable {
    override fun run() {
      applyFade()
      val left = timeLeft()
      if (hasTimer && !paused && left <= 0) {
        Log.i("Nightfall", "timer ended")
        stopReason = "timer_ended"
        stopSelf()
        return
      }
      publish()
      handler.postDelayed(this, 1_000)
    }
  }

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    if (running) {
      when (intent?.action) {
        ACTION_TOGGLE -> togglePause()
        ACTION_ADD -> addTime()
        ACTION_STOP -> stopFromUser()
      }
      return START_NOT_STICKY
    }
    if (intent?.action != null) {
      stopSelf()
      return START_NOT_STICKY
    }
    begin(intent)
    return START_NOT_STICKY
  }

  private fun begin(intent: Intent?) {
    audio = getSystemService(AudioManager::class.java)
    val store = Store.get(this)
    val tonight = store.tonight.value
    val mix = chooseMix(store, intent?.getStringExtra(EXTRA_MIX))
    automation = intent?.getBooleanExtra(EXTRA_AUTOMATION, false) ?: false
    val guided = guidedById(intent?.getStringExtra(EXTRA_SESSION))
    title = mix.name
    layers = if (guided != null && !tonight.mixUnderGuided) emptyList() else mix.layers
    voiceLevel = tonight.voiceLevel / 100f
    gain = (intent?.getIntExtra(EXTRA_GAIN, tonight.gain) ?: tonight.gain).coerceIn(1, 100) / 100f
    val chosen = intent?.getIntExtra(EXTRA_SECONDS, tonight.minutes * 60) ?: (tonight.minutes * 60)
    // Without a mix, the session ends with the voice. Otherwise the sounds carry on after it.
    val seconds = when {
      guided == null -> chosen
      !tonight.mixUnderGuided -> guided.minutes * 60 + 90
      chosen == 0 -> 0
      else -> maxOf(chosen, guided.minutes * 60 + 90)
    }
    hasTimer = seconds > 0
    totalMs = seconds * 1_000L
    remainingMs = totalMs
    fadeMs = minOf(tonight.fadeMinutes * 60_000L, totalMs)
    resumedAt = System.currentTimeMillis()
    session = buildSession()

    try {
      startForeground(1, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
    } catch (e: Exception) {
      Log.w("Nightfall", "foreground refused: $e")
      fail("foreground_denied")
      return
    }

    attributes = AudioAttributes.Builder()
      .setUsage(AudioAttributes.USAGE_MEDIA)
      .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
      .build()
    focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
      .setAudioAttributes(attributes)
      .setOnAudioFocusChangeListener(::onFocusChange)
      .build()
    focusResult = audio.requestAudioFocus(focus!!)

    engine = MixEngine(attributes).also {
      it.onError = { code -> Log.w("Nightfall", "audio write failed: $code") }
      it.core.setLayers(layers)
      it.core.master = gain
      preferredDevice(tonight.outputId)?.let(it::setOutput)
      it.start()
    }
    wakeLock = getSystemService(PowerManager::class.java)
      .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Nightfall:playback")
      .apply { acquire(14 * 60 * 60 * 1000L) }

    running = true
    Player.service = this
    guided?.let { playVoice(it) }
    publish()
    handler.post(tick)
    handler.postDelayed({ verify() }, 2_000)
  }

  private fun playVoice(session: Guided) {
    stopVoice()
    try {
      voice = MediaPlayer().apply {
        setAudioAttributes(attributes)
        assets.openFd(session.asset).use { setDataSource(it.fileDescriptor, it.startOffset, it.length) }
        prepare()
        setVolume(voiceLevel, voiceLevel)
        setOnCompletionListener { finishVoice() }
        start()
      }
      guide = session
      lastText = ""
    } catch (e: Exception) {
      Log.w("Nightfall", "guided audio failed: $e")
      stopVoice()
    }
  }

  fun startGuided(id: String) {
    val session = guidedById(id) ?: return
    if (paused) togglePause()
    if (hasTimer) {
      val needed = session.minutes * 60_000L + 90_000L
      val left = timeLeft()
      if (left < needed) {
        totalMs += needed - left
        remainingMs = needed
        resumedAt = System.currentTimeMillis()
      }
    }
    playVoice(session)
    publish()
  }

  private fun finishVoice() {
    stopVoice()
    lastText = ""
    Reporter.send(this, "guided_finished", report(true))
    publish()
  }

  private fun stopVoice() {
    voice?.run {
      setOnCompletionListener(null)
      runCatching { stop() }
      release()
    }
    voice = null
    guide = null
  }

  fun setSoundLevel(level: Int) {
    gain = level.coerceIn(0, 100) / 100f
    applyFade()
    Store.get(this).run { setTonight(tonight.value.copy(gain = level)) }
    publish()
  }

  fun setVoiceLevel(level: Int) {
    voiceLevel = level.coerceIn(0, 100) / 100f
    voice?.setVolume(voiceLevel, voiceLevel)
    Store.get(this).run { setTonight(tonight.value.copy(voiceLevel = level)) }
    publish()
  }

  private fun chooseMix(store: Store, wanted: String?): Mix {
    if (wanted.isNullOrEmpty()) return store.tonightMix()
    return store.mixes.value.firstOrNull { it.id == wanted || it.name.equals(wanted, ignoreCase = true) } ?: store.tonightMix()
  }

  private fun preferredDevice(id: Int): AudioDeviceInfo? =
    if (id == 0) null else audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull { it.id == id }

  private fun onFocusChange(change: Int) {
    Log.i("Nightfall", "focus change $change")
    if (change == AudioManager.AUDIOFOCUS_LOSS) stopFromUser()
  }

  /** "Started" means focus was granted and the output is consuming audio. */
  private fun verify() {
    val output = engine ?: return
    val stamp = AudioTimestamp()
    val first = if (output.track.getTimestamp(stamp)) stamp.framePosition else -1L
    handler.postDelayed({
      val current = engine ?: return@postDelayed
      val second = if (current.track.getTimestamp(stamp)) stamp.framePosition else -1L
      val advancing = second > first && first >= 0
      val ok = focusResult == AudioManager.AUDIOFOCUS_REQUEST_GRANTED && current.lastWrite > 0 && advancing
      Log.i("Nightfall", "verify ok=$ok focus=$focusResult head=$first->$second")
      if (ok) {
        confirmed = true
        if (automation) Connections.recordStart(this, "Started on time")
        Reporter.send(this, "started", report(true))
        publish()
      } else {
        // Android mutes without an error, and frames keep advancing, so refused focus is a failed start.
        fail("audio_not_confirmed")
      }
    }, 1_000)
  }

  private fun fail(reason: String) {
    if (automation) Connections.recordStart(this, "Failed to start")
    Reporter.send(this, "start_failed", report(false) + ("reason" to reason))
    stopReason = "start_failed"
    stopSelf()
  }

  private fun report(ok: Boolean): Map<String, Any?> = mapOf(
    "ok" to ok,
    "focus" to focusResult,
    "mix" to title,
    "seconds_left" to if (hasTimer) timeLeft() / 1000 else null,
    "ends_at" to if (hasTimer && !paused) System.currentTimeMillis() + timeLeft() else null,
  )

  fun togglePause() {
    if (!running) return
    paused = !paused
    if (paused) {
      remainingMs = timeLeft(counting = true)
      engine?.pause()
      voice?.pause()
    } else {
      resumedAt = System.currentTimeMillis()
      engine?.resume()
      voice?.start()
    }
    Reporter.send(this, if (paused) "paused" else "resumed", report(true))
    publish()
  }

  fun addTime() {
    if (!running || !hasTimer) return
    remainingMs = timeLeft() + ADD_MS
    totalMs += ADD_MS
    resumedAt = System.currentTimeMillis()
    Reporter.send(this, "time_added", report(true))
    publish()
  }

  fun stopFromUser() {
    stopReason = "stopped"
    stopSelf()
  }

  fun updateLayers(next: List<Layer>) {
    layers = next
    engine?.core?.setLayers(next)
    publish()
  }

  fun setOutput(device: AudioDeviceInfo?) {
    engine?.setOutput(device)
  }

  private fun timeLeft(counting: Boolean = !paused): Long =
    if (counting) (remainingMs - (System.currentTimeMillis() - resumedAt)).coerceAtLeast(0) else remainingMs

  private fun applyFade() {
    val level = if (hasTimer && fadeMs > 0) (timeLeft().toFloat() / fadeMs).coerceIn(0f, 1f) else 1f
    engine?.core?.master = gain * level
  }

  private fun buildSession() = MediaSession(this, "Nightfall").apply {
    setCallback(object : MediaSession.Callback() {
      override fun onPlay() { if (paused) togglePause() }
      override fun onPause() { if (!paused) togglePause() }
      override fun onStop() { stopFromUser() }
      override fun onCustomAction(action: String, extras: Bundle?) {
        when (action) {
          ACTION_ADD -> addTime()
          ACTION_STOP -> stopFromUser()
        }
      }
    })
    isActive = true
  }

  private var lastText = ""

  /** Updates the UI every tick, but the notification only when its words change. */
  private fun publish() {
    val left = timeLeft()
    val speaking = voice
    Player.publish(
      PlayState(
        active = true,
        paused = paused,
        title = title,
        layers = layers,
        hasTimer = hasTimer,
        leftMs = left,
        confirmed = confirmed,
        guideTitle = guide?.title.orEmpty(),
        voiceLeftMs = speaking?.let { (it.duration - it.currentPosition).toLong() } ?: 0,
        voiceTotalMs = speaking?.duration?.toLong() ?: 0,
        soundLevel = (gain * 100).toInt(),
        voiceLevel = (voiceLevel * 100).toInt(),
      ),
    )
    val text = statusText(left)
    if (text == lastText) return
    lastText = text
    val active = session ?: return
    active.setMetadata(
      MediaMetadata.Builder()
        .putString(MediaMetadata.METADATA_KEY_TITLE, guide?.title ?: title)
        .putString(MediaMetadata.METADATA_KEY_ARTIST, text)
        .putLong(MediaMetadata.METADATA_KEY_DURATION, if (hasTimer) totalMs else -1)
        .build(),
    )
    active.setPlaybackState(
      PlaybackState.Builder()
        .setActions(PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_STOP)
        .setState(
          if (paused) PlaybackState.STATE_PAUSED else PlaybackState.STATE_PLAYING,
          if (hasTimer) totalMs - left else PlaybackState.PLAYBACK_POSITION_UNKNOWN,
          if (paused) 0f else 1f,
        )
        .addCustomAction(ACTION_ADD, "+15 min", android.R.drawable.ic_input_add)
        .addCustomAction(ACTION_STOP, "Stop", android.R.drawable.ic_menu_close_clear_cancel)
        .build(),
    )
    getSystemService(NotificationManager::class.java).notify(1, buildNotification())
  }

  private fun statusText(left: Long): String = when {
    paused -> "Paused"
    !hasTimer -> "Playing, no timer"
    else -> {
      val minutes = (left + 59_999) / 60_000
      val ends = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(System.currentTimeMillis() + left))
      "$minutes min left, silent by about $ends"
    }
  }

  private fun controlIntent(action: String) = PendingIntent.getService(
    this,
    action.hashCode(),
    Intent(this, PlaybackService::class.java).setAction(action),
    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
  )

  private fun action(icon: Int, label: String, name: String) =
    Notification.Action.Builder(Icon.createWithResource(this, icon), label, controlIntent(name)).build()

  private fun buildNotification(): Notification {
    val channel = NotificationChannel(CHANNEL, "Playback", NotificationManager.IMPORTANCE_LOW)
    getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    val open = PendingIntent.getActivity(
      this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
    )
    return Notification.Builder(this, CHANNEL)
      .setContentTitle(guide?.title ?: title)
      .setContentText(statusText(timeLeft()))
      .setSmallIcon(R.drawable.ic_notification)
      .setContentIntent(open)
      .setOngoing(true)
      .setOnlyAlertOnce(true)
      .setVisibility(Notification.VISIBILITY_PUBLIC)
      .setCategory(Notification.CATEGORY_TRANSPORT)
      .setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
      .addAction(
        if (paused) action(android.R.drawable.ic_media_play, "Play", ACTION_TOGGLE)
        else action(android.R.drawable.ic_media_pause, "Pause", ACTION_TOGGLE),
      )
      .addAction(action(android.R.drawable.ic_input_add, "+15 min", ACTION_ADD))
      .addAction(action(android.R.drawable.ic_menu_close_clear_cancel, "Stop", ACTION_STOP))
      .setStyle(Notification.MediaStyle().setMediaSession(session?.sessionToken).setShowActionsInCompactView(0, 1, 2))
      .build()
  }

  override fun onDestroy() {
    running = false
    Player.service = null
    Player.publish(PlayState())
    handler.removeCallbacksAndMessages(null)
    stopVoice()
    engine?.release()
    focus?.let { audio.abandonAudioFocusRequest(it) }
    wakeLock?.takeIf { it.isHeld }?.release()
    session?.run {
      isActive = false
      release()
    }
    if (stopReason != "start_failed") Reporter.send(this, stopReason, mapOf("ok" to true, "mix" to title))
    super.onDestroy()
  }

  companion object {
    const val EXTRA_SECONDS = "secs"
    const val EXTRA_GAIN = "gain"
    const val EXTRA_MIX = "mix"
    const val EXTRA_SESSION = "session"
    const val EXTRA_AUTOMATION = "automation"
  }
}
