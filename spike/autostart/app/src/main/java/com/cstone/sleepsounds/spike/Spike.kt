package com.cstone.sleepsounds.spike

import android.app.Activity
import android.app.ForegroundServiceStartNotAllowedException
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTimestamp
import android.media.AudioTrack
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.net.Uri
import android.os.BatteryManager
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

private const val TAG = "SleepSpike"
private const val ACTION_TOGGLE = "toggle"
private const val ACTION_ADD = "add15"
private const val ACTION_STOP = "stop"
private const val ADD_MS = 15 * 60_000L

/** Writes to logcat and to a file, so an overnight run can be read back in the morning. */
object SpikeLog {
  private val stamp = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)

  @Synchronized
  fun add(context: Context, message: String) {
    Log.i(TAG, message)
    file(context).appendText("${stamp.format(Date())} $message\n")
  }

  fun file(context: Context) = File(context.filesDir, "spike.log")
}

/** The entry point Home Assistant reaches through the Companion app's broadcast command. */
class StartReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    val stop = intent.action.orEmpty().endsWith(".STOP")
    SpikeLog.add(context, "RECEIVED ${if (stop) "stop" else "start"} target=${context.applicationInfo.targetSdkVersion}")
    val service = Intent(context, PlaybackService::class.java).putExtras(intent)
    if (stop) {
      context.stopService(service)
      return
    }
    try {
      context.startForegroundService(service)
      SpikeLog.add(context, "FGS_START_REQUESTED")
    } catch (e: ForegroundServiceStartNotAllowedException) {
      SpikeLog.add(context, "FAIL fgs_start_denied ${e.message}")
    }
  }
}

class PlaybackService : Service() {
  private val handler = Handler(Looper.getMainLooper())
  private lateinit var audio: AudioManager
  private var focus: AudioFocusRequest? = null
  private var track: AudioTrack? = null
  private var wakeLock: PowerManager.WakeLock? = null
  private var writer: Thread? = null
  @Volatile private var running = false
  @Volatile private var framesWritten = 0L
  @Volatile private var lastWrite = 0
  private var focusResult = -1
  private var session: MediaSession? = null
  @Volatile private var paused = false
  private var hasTimer = false
  private var totalMs = 0L
  private var remainingMs = 0L
  private var resumedAt = 0L
  private val endTimer = Runnable {
    SpikeLog.add(this, "TIMER_ENDED")
    stopSelf()
  }
  private val tick = object : Runnable {
    override fun run() {
      publish()
      handler.postDelayed(this, 30_000)
    }
  }

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    if (running) {
      when (intent?.action) {
        ACTION_TOGGLE -> togglePause()
        ACTION_ADD -> addTime()
        ACTION_STOP -> stopSelf()
      }
      return START_NOT_STICKY
    }
    if (intent?.action != null) {
      stopSelf()
      return START_NOT_STICKY
    }
    audio = getSystemService(AudioManager::class.java)
    val seconds = intent?.getIntExtra("secs", 30) ?: 30
    hasTimer = seconds > 0
    totalMs = seconds * 1_000L
    remainingMs = totalMs
    resumedAt = System.currentTimeMillis()
    session = buildSession()
    try {
      startForeground(1, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
      SpikeLog.add(this, "FOREGROUND ok")
    } catch (e: Exception) {
      SpikeLog.add(this, "FAIL start_foreground ${e.javaClass.simpleName} ${e.message}")
      stopSelf()
      return START_NOT_STICKY
    }

    val attributes = AudioAttributes.Builder()
      .setUsage(AudioAttributes.USAGE_MEDIA)
      .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
      .build()
    focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
      .setAudioAttributes(attributes)
      .setOnAudioFocusChangeListener { change ->
        SpikeLog.add(this, "FOCUS_CHANGE $change")
        if (change == AudioManager.AUDIOFOCUS_LOSS) stopSelf()
      }
      .build()
    focusResult = audio.requestAudioFocus(focus!!)
    SpikeLog.add(this, "FOCUS result=$focusResult (1=granted, 0=failed)")

    val gain = (intent?.getIntExtra("gain", 8) ?: 8).coerceIn(1, 100) / 100f
    startAudio(attributes, gain)

    wakeLock = getSystemService(PowerManager::class.java)
      .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$TAG:playback")
      .apply { acquire(12 * 60 * 60 * 1000L) }

    handler.postDelayed({ verify() }, 2_000)
    handler.postDelayed(object : Runnable {
      override fun run() {
        status()
        handler.postDelayed(this, 60_000)
      }
    }, 60_000)
    if (hasTimer) handler.postDelayed(endTimer, remainingMs)
    publish()
    handler.postDelayed(tick, 30_000)
    return START_NOT_STICKY
  }

  private fun startAudio(attributes: AudioAttributes, gain: Float) {
    val rate = 48_000
    val format = AudioFormat.Builder()
      .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
      .setSampleRate(rate)
      .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
      .build()
    val minimum = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT)
    val output = AudioTrack.Builder()
      .setAudioAttributes(attributes)
      .setAudioFormat(format)
      .setBufferSizeInBytes(minimum * 4)
      .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_POWER_SAVING)
      .setTransferMode(AudioTrack.MODE_STREAM)
      .build()
    track = output
    running = true
    output.play()
    writer = Thread({
      val buffer = FloatArray(4_800)
      var brown = 0f
      var ramp = 0f
      while (running) {
        if (paused) {
          Thread.sleep(50)
          continue
        }
        for (i in buffer.indices) {
          val white = Random.nextFloat() * 2f - 1f
          brown = (brown + 0.02f * white) / 1.02f
          ramp = minOf(1f, ramp + 0.00002f)
          buffer[i] = (brown * 3.5f * gain * ramp).coerceIn(-1f, 1f)
        }
        val written = output.write(buffer, 0, buffer.size, AudioTrack.WRITE_BLOCKING)
        lastWrite = written
        if (written < 0) {
          SpikeLog.add(this, "FAIL write=$written")
          break
        }
        framesWritten += written
      }
    }, "brown-noise").apply { start() }
  }

  /** Reports playing only with focus held and the output actually consuming frames. */
  private fun verify() {
    val output = track ?: return
    val timestamp = AudioTimestamp()
    val first = if (output.getTimestamp(timestamp)) timestamp.framePosition else -1L
    handler.postDelayed({
      val second = if (output.getTimestamp(timestamp)) timestamp.framePosition else -1L
      val advancing = second > first && first >= 0
      val ok = focusResult == AudioManager.AUDIOFOCUS_REQUEST_GRANTED && lastWrite > 0 && advancing
      SpikeLog.add(this, "${if (ok) "CONFIRMED" else "FAIL unconfirmed"} focus=$focusResult lastWrite=$lastWrite head=$first->$second playState=${output.playState}")
    }, 1_000)
  }

  private fun status() {
    val power = getSystemService(PowerManager::class.java)
    val battery = getSystemService(BatteryManager::class.java)
    val plugged = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
      ?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
    SpikeLog.add(
      this,
      "STATUS battery=${battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)} " +
        "currentMicroA=${battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)} plugged=$plugged " +
        "doze=${power.isDeviceIdleMode} screen=${power.isInteractive} frames=$framesWritten",
    )
  }

  private fun togglePause() {
    paused = !paused
    if (paused) {
      remainingMs = timeLeft(running = true)
      handler.removeCallbacks(endTimer)
      track?.pause()
    } else {
      resumedAt = System.currentTimeMillis()
      track?.play()
      if (hasTimer) handler.postDelayed(endTimer, remainingMs)
    }
    SpikeLog.add(this, "CONTROL ${if (paused) "pause" else "resume"} left=${timeLeft() / 1000}s")
    publish()
  }

  private fun addTime() {
    if (!hasTimer) return
    remainingMs = timeLeft() + ADD_MS
    totalMs += ADD_MS
    resumedAt = System.currentTimeMillis()
    handler.removeCallbacks(endTimer)
    if (!paused) handler.postDelayed(endTimer, remainingMs)
    SpikeLog.add(this, "CONTROL add15 left=${remainingMs / 1000}s")
    publish()
  }

  private fun timeLeft(running: Boolean = !paused): Long =
    if (running) (remainingMs - (System.currentTimeMillis() - resumedAt)).coerceAtLeast(0) else remainingMs

  private fun buildSession() = MediaSession(this, TAG).apply {
    setCallback(object : MediaSession.Callback() {
      override fun onPlay() { if (paused) togglePause() }
      override fun onPause() { if (!paused) togglePause() }
      override fun onStop() { stopSelf() }
      override fun onCustomAction(action: String, extras: Bundle?) {
        when (action) {
          ACTION_ADD -> addTime()
          ACTION_STOP -> stopSelf()
        }
      }
    })
    isActive = true
  }

  /** Pushes current state to the media session and the notification together. */
  private fun publish() {
    val active = session ?: return
    val left = timeLeft()
    active.setMetadata(
      MediaMetadata.Builder()
        .putString(MediaMetadata.METADATA_KEY_TITLE, "Brown noise")
        .putString(MediaMetadata.METADATA_KEY_ARTIST, statusText(left))
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
      val ends = SimpleDateFormat("h:mm a", Locale.US).format(Date(System.currentTimeMillis() + left))
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
    val channel = NotificationChannel("spike", "Spike playback", NotificationManager.IMPORTANCE_LOW)
    getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    val open = PendingIntent.getActivity(
      this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
    )
    return Notification.Builder(this, "spike")
      .setContentTitle("Brown noise")
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
    handler.removeCallbacksAndMessages(null)
    writer?.join(500)
    track?.run {
      stop()
      release()
    }
    focus?.let { audio.abandonAudioFocusRequest(it) }
    wakeLock?.takeIf { it.isHeld }?.release()
    session?.run {
      isActive = false
      release()
    }
    SpikeLog.add(this, "STOPPED frames=$framesWritten")
    super.onDestroy()
  }
}

/** Only used to launch the app once, start by hand, and read the log on the phone. */
class MainActivity : Activity() {
  private lateinit var logView: TextView
  private lateinit var batteryView: TextView

  private fun batteryLimitsOff() = getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)

  private fun askToRemoveBatteryLimits() {
    if (batteryLimitsOff()) return
    startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1)
    val column = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      setPadding(48, 96, 48, 48)
    }
    batteryView = TextView(this).apply { textSize = 16f }
    column.addView(batteryView)
    fun button(label: String, action: () -> Unit) = column.addView(Button(this).apply {
      text = label
      setOnClickListener { action() }
    })
    button("Start 30 seconds") {
      startForegroundService(Intent(this, PlaybackService::class.java).putExtra("secs", 30))
    }
    button("Stop") { stopService(Intent(this, PlaybackService::class.java)) }
    button("Allow background start") { askToRemoveBatteryLimits() }
    button("App battery settings") {
      startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
    }
    button("Refresh log") { showLog() }
    logView = TextView(this).apply { textSize = 12f }
    column.addView(ScrollView(this).apply { addView(logView) })
    setContentView(column)
  }

  // Runs after the notification prompt, so the two system dialogs never stack.
  override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    askToRemoveBatteryLimits()
  }

  override fun onResume() {
    super.onResume()
    batteryView.text = if (batteryLimitsOff()) "Background start: allowed" else "Background start: blocked by battery limits"
    showLog()
  }

  private fun showLog() {
    val file = SpikeLog.file(this)
    logView.text = if (file.exists()) file.readLines().takeLast(40).joinToString("\n") else "No events yet"
  }
}
