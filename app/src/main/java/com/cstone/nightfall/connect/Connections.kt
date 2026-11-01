package com.cstone.nightfall.connect

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom

/** One place an event can be sent. Home Assistant and n8n both reduce to this. */
data class Target(val name: String, val url: String, val headers: Map<String, String> = emptyMap())

/** User-owned connection details. Nothing about a particular home lives in the app. */
object Connections {
  const val HA_URL = "ha_url"
  const val HA_WEBHOOK = "ha_webhook"
  const val N8N_URL = "n8n_url"
  const val N8N_HEADER = "n8n_header"
  const val N8N_VALUE = "n8n_value"
  private const val KEY = "automation_key"
  private const val LAST_START = "last_start"
  private const val REMOTE = "remote_control"

  private fun prefs(context: Context): SharedPreferences =
    context.getSharedPreferences("nightfall", Context.MODE_PRIVATE)

  fun get(context: Context, name: String): String = prefs(context).getString(name, "").orEmpty().trim()

  fun set(context: Context, name: String, value: String) {
    prefs(context).edit().putString(name, value.trim()).apply()
  }

  /** When off, automation commands do nothing. Controls in the app and notification still work. */
  fun remoteAllowed(context: Context): Boolean = prefs(context).getBoolean(REMOTE, true)

  fun setRemoteAllowed(context: Context, allowed: Boolean) {
    prefs(context).edit().putBoolean(REMOTE, allowed).apply()
  }

  /** Commands must carry this, so other apps on the phone can't trigger playback. */
  fun automationKey(context: Context): String =
    get(context, KEY).ifEmpty { newAutomationKey(context) }

  fun newAutomationKey(context: Context): String {
    val bytes = ByteArray(16).also { SecureRandom().nextBytes(it) }
    return bytes.joinToString("") { "%02x".format(it) }.also { set(context, KEY, it) }
  }

  /** The result of the most recent automation start, shown on the Routine screen. */
  fun lastStart(context: Context): String = get(context, LAST_START)

  fun recordStart(context: Context, result: String) = set(context, LAST_START, result)

  fun haTarget(context: Context): Target? {
    val url = get(context, HA_URL).trimEnd('/')
    val webhook = get(context, HA_WEBHOOK)
    if (url.isEmpty() || webhook.isEmpty()) return null
    return Target("Home Assistant", "$url/api/webhook/$webhook")
  }

  fun n8nTarget(context: Context): Target? {
    val url = get(context, N8N_URL)
    if (url.isEmpty()) return null
    val header = get(context, N8N_HEADER)
    val value = get(context, N8N_VALUE)
    return Target("n8n", url, if (header.isNotEmpty() && value.isNotEmpty()) mapOf(header to value) else emptyMap())
  }

  fun startCommand(context: Context): String {
    val pkg = context.packageName
    return """
      action: notify.mobile_app_YOUR_PHONE
      data:
        message: command_broadcast_intent
        data:
          intent_package_name: $pkg
          intent_action: $pkg.START
          intent_extras: key:${automationKey(context)}
    """.trimIndent()
  }
}

/** Sends every event to each configured target. No targets means nothing leaves the phone. */
object Reporter {
  fun send(context: Context, event: String, fields: Map<String, Any?> = emptyMap(), done: (() -> Unit)? = null) {
    val app = context.applicationContext
    val targets = listOfNotNull(Connections.haTarget(app), Connections.n8nTarget(app))
    if (targets.isEmpty()) {
      done?.invoke()
      return
    }
    val body = payload(app, event, fields)
    Thread({
      for (target in targets) {
        var code = post(target, body)
        if (code !in 200..299) {
          Thread.sleep(3_000)
          code = post(target, body)
        }
        Log.i("Nightfall", "report ${target.name} event=$event http=$code")
      }
      done?.invoke()
    }, "reporter").start()
  }

  fun test(context: Context, target: Target, result: (Int) -> Unit) {
    val body = payload(context.applicationContext, "test", emptyMap())
    Thread({ result(post(target, body)) }, "reporter-test").start()
  }

  private fun payload(context: Context, event: String, fields: Map<String, Any?>): String =
    JSONObject().apply {
      put("app", "nightfall")
      put("event", event)
      put("at", System.currentTimeMillis())
      put("device", Build.MODEL)
      put("target_sdk", context.applicationInfo.targetSdkVersion)
      fields.forEach { (name, value) -> put(name, value) }
    }.toString()

  private fun post(target: Target, body: String): Int = try {
    val connection = URL(target.url).openConnection() as HttpURLConnection
    connection.requestMethod = "POST"
    connection.connectTimeout = 10_000
    connection.readTimeout = 10_000
    connection.doOutput = true
    connection.setRequestProperty("Content-Type", "application/json")
    target.headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
    connection.outputStream.use { it.write(body.toByteArray()) }
    connection.responseCode.also { connection.disconnect() }
  } catch (e: Exception) {
    Log.w("Nightfall", "report failed: $e")
    -1
  }
}
