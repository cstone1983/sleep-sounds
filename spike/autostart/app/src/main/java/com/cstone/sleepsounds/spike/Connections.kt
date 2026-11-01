package com.cstone.sleepsounds.spike

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
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

  private fun prefs(context: Context): SharedPreferences =
    context.getSharedPreferences("nightfall", Context.MODE_PRIVATE)

  fun get(context: Context, name: String): String = prefs(context).getString(name, "").orEmpty().trim()

  fun set(context: Context, name: String, value: String) {
    prefs(context).edit().putString(name, value.trim()).apply()
  }

  /** Commands must carry this, so other apps on the phone can't trigger playback. */
  fun automationKey(context: Context): String =
    get(context, KEY).ifEmpty { newAutomationKey(context) }

  fun newAutomationKey(context: Context): String {
    val bytes = ByteArray(16).also { SecureRandom().nextBytes(it) }
    return bytes.joinToString("") { "%02x".format(it) }.also { set(context, KEY, it) }
  }

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
          intent_extras: key:${automationKey(context)},secs:2700
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
        SpikeLog.add(app, "REPORT ${target.name} event=$event http=$code")
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
    Log.warn(e)
    -1
  }

  private object Log {
    fun warn(e: Exception) = android.util.Log.w("SleepSpike", "report failed: $e")
  }
}

/** Plain setup screen. Every connection is optional. */
class ConnectionsActivity : Activity() {
  private val fields = mutableMapOf<String, EditText>()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    val column = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      setPadding(48, 96, 48, 48)
    }
    fun heading(text: String) = column.addView(TextView(this).apply {
      this.text = text
      textSize = 18f
      setPadding(0, 32, 0, 8)
    })
    fun note(text: String) = column.addView(TextView(this).apply { this.text = text })
    fun field(name: String, label: String, secret: Boolean = false) {
      val input = EditText(this).apply {
        hint = label
        setText(Connections.get(this@ConnectionsActivity, name))
        isSingleLine = true
        inputType = InputType.TYPE_CLASS_TEXT or
          if (secret) InputType.TYPE_TEXT_VARIATION_PASSWORD else InputType.TYPE_TEXT_VARIATION_URI
      }
      fields[name] = input
      column.addView(input)
    }
    fun button(label: String, action: () -> Unit) = column.addView(Button(this).apply {
      text = label
      setOnClickListener { action() }
    })

    heading("Home Assistant")
    note("Optional. Create an automation with a Webhook trigger, then enter its webhook ID.")
    field(Connections.HA_URL, "Server URL, such as http://homeassistant.local:8123")
    field(Connections.HA_WEBHOOK, "Webhook ID", secret = true)
    button("Test Home Assistant") { test(Connections::haTarget) }

    heading("n8n or any webhook")
    note("Optional. Events are posted as JSON to this URL.")
    field(Connections.N8N_URL, "Webhook URL")
    field(Connections.N8N_HEADER, "Header name, if your webhook needs one")
    field(Connections.N8N_VALUE, "Header value", secret = true)
    button("Test n8n") { test(Connections::n8nTarget) }

    heading("Automation key")
    note("Commands sent to this app must include this key.")
    button("Copy start command") {
      copy("Nightfall start command", Connections.startCommand(this))
    }
    button("Copy key") { copy("Nightfall key", Connections.automationKey(this)) }
    button("Make a new key") {
      Connections.newAutomationKey(this)
      Toast.makeText(this, "New key made. Update your automations.", Toast.LENGTH_LONG).show()
    }
    setContentView(ScrollView(this).apply { addView(column) })
  }

  private fun test(target: (Context) -> Target?) {
    save()
    val chosen = target(this)
    if (chosen == null) {
      Toast.makeText(this, "Fill in the details first", Toast.LENGTH_SHORT).show()
      return
    }
    Reporter.test(this, chosen) { code ->
      runOnUiThread {
        val message = if (code in 200..299) "${chosen.name} connected" else "${chosen.name} failed (${if (code < 0) "no connection" else "HTTP $code"})"
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
      }
    }
  }

  private fun copy(label: String, text: String) {
    getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show()
  }

  private fun save() = fields.forEach { (name, input) -> Connections.set(this, name, input.text.toString()) }

  override fun onPause() {
    save()
    super.onPause()
  }
}
