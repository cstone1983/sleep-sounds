package com.cstone.nightfall.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class Layer(
  val soundId: String,
  val volume: Int = 50,
  val drift: Boolean = false,
  val soften: Boolean = false,
)

data class Mix(val id: String, val name: String, val layers: List<Layer>)

/** What Start plays tonight. minutes = 0 means no timer. */
data class Tonight(
  val mixId: String,
  val minutes: Int = 45,
  val fadeMinutes: Int = 10,
  val outputId: Int = 0,
  val gain: Int = 45,
)

private val STARTER_MIXES = listOf(
  Mix("tent", "Rain on the tent", listOf(Layer("heavy_rain", 70), Layer("thunder", 30, drift = true), Layer("brown", 45))),
  Mix("cabin", "Cabin fire", listOf(Layer("fire", 70), Layer("wind", 30, drift = true))),
  Mix("hum", "Deep hum", listOf(Layer("brown", 60), Layer("hum", 25))),
  Mix("coast", "Night coast", listOf(Layer("ocean", 70), Layer("wind", 20, drift = true), Layer("crickets", 15))),
)

/** Mixes and tonight's choices. Plain preferences: this is small, and it all fits in memory. */
class Store private constructor(context: Context) {
  private val prefs: SharedPreferences = context.getSharedPreferences("nightfall_store", Context.MODE_PRIVATE)
  private val _mixes = MutableStateFlow(readMixes())
  private val _tonight = MutableStateFlow(readTonight())
  val mixes: StateFlow<List<Mix>> = _mixes
  val tonight: StateFlow<Tonight> = _tonight

  fun mix(id: String) = _mixes.value.firstOrNull { it.id == id }
  fun tonightMix() = mix(_tonight.value.mixId) ?: _mixes.value.first()

  fun setTonight(value: Tonight) {
    _tonight.value = value
    prefs.edit()
      .putString("mix", value.mixId)
      .putInt("minutes", value.minutes)
      .putInt("fade", value.fadeMinutes)
      .putInt("output", value.outputId)
      .putInt("gain", value.gain)
      .apply()
  }

  /** Saves over an existing mix, or adds one when the id is new. */
  fun saveMix(mix: Mix) {
    val current = _mixes.value
    val next = if (current.any { it.id == mix.id }) current.map { if (it.id == mix.id) mix else it } else current + mix
    writeMixes(next)
  }

  fun newMixId(): String = UUID.randomUUID().toString().take(8)

  fun deleteMix(id: String) {
    val next = _mixes.value.filterNot { it.id == id }
    if (next.isEmpty()) return
    writeMixes(next)
    if (_tonight.value.mixId == id) setTonight(_tonight.value.copy(mixId = next.first().id))
  }

  private fun writeMixes(list: List<Mix>) {
    _mixes.value = list
    prefs.edit().putString("mixes", encode(list)).apply()
  }

  private fun readTonight() = Tonight(
    mixId = prefs.getString("mix", null) ?: _mixes.value.first().id,
    minutes = prefs.getInt("minutes", 45),
    fadeMinutes = prefs.getInt("fade", 10),
    outputId = prefs.getInt("output", 0),
    gain = prefs.getInt("gain", 45),
  )

  private fun readMixes(): List<Mix> =
    prefs.getString("mixes", null)?.let(::decode)?.takeIf { it.isNotEmpty() } ?: STARTER_MIXES

  companion object {
    @Volatile private var instance: Store? = null

    fun get(context: Context): Store =
      instance ?: synchronized(this) {
        instance ?: Store(context.applicationContext).also { instance = it }
      }

    fun encode(list: List<Mix>): String = JSONArray().apply {
      for (mix in list) {
        put(JSONObject().apply {
          put("id", mix.id)
          put("name", mix.name)
          put("layers", JSONArray().apply {
            for (layer in mix.layers) {
              put(JSONObject().apply {
                put("sound", layer.soundId)
                put("volume", layer.volume)
                put("drift", layer.drift)
                put("soften", layer.soften)
              })
            }
          })
        })
      }
    }.toString()

    fun decode(text: String): List<Mix> {
      val array = JSONArray(text)
      return List(array.length()) { index ->
        val item = array.getJSONObject(index)
        val layers = item.getJSONArray("layers")
        Mix(
          item.getString("id"),
          item.getString("name"),
          List(layers.length()) { position ->
            val layer = layers.getJSONObject(position)
            Layer(layer.getString("sound"), layer.getInt("volume"), layer.getBoolean("drift"), layer.getBoolean("soften"))
          },
        )
      }
    }
  }
}
