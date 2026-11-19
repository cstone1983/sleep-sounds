package com.cstone.nightfall.audio

enum class Category(val label: String) { RAIN("Rain"), WATER("Water"), NATURE("Nature"), NOISE("Noise"), TONES("Tones") }

class Sound(val id: String, val name: String, val category: Category, val icon: String, val make: () -> Source)

private const val ICON_RAIN = "M7 15a4 4 0 0 1 .5-8A5 5 0 0 1 17 8a3.5 3.5 0 0 1 0 7H7ZM9 18v2M13 18v2M17 18v2"
private const val ICON_DROP = "M12 3s6 6.5 6 11a6 6 0 0 1-12 0c0-4.5 6-11 6-11Z"

val SOUNDS = listOf(
  Sound("heavy_rain", "Heavy rain", Category.RAIN, ICON_RAIN) { Rain(0.6f, 220f, 4_200f, 0.5f, 90f, 0.2f, 0.012f, 0.4f, density = 5_000f) * 0.9f },
  Sound("light_rain", "Light rain", Category.RAIN, "M7 15a4 4 0 0 1 .5-8A5 5 0 0 1 17 8a3.5 3.5 0 0 1 0 7H7ZM12 18v2") { Rain(0.3f, 500f, 4_800f, 0.08f, 30f, 0.09f, 0.008f, 0.35f, 1_800f, 4_000f, density = 1_600f) * 1.5f },
  Sound("thunder", "Thunder", Category.RAIN, "M13 3 6 14h5l-1 7 7-11h-5l1-7Z") { Thunder() * 0.4f },
  Sound("roof", "Rain on roof", Category.RAIN, "M3 13 12 6l9 7M6 11v8h12v-8") { Rain(0.9f, 120f, 750f, 0.6f, 40f, 0.45f, 0.03f, 0.93f, 170f, 420f, density = 2_500f) },
  Sound("tent", "Rain on tent", Category.RAIN, "M12 4 3 20h18L12 4ZM12 4v16M9 20l3-6 3 6") { Rain(0.22f, 300f, 3_200f, 0.1f, 120f, 0.5f, 0.012f, 0.72f, 300f, 700f, density = 2_200f) * 2.0f },
  Sound("window", "Rain on window", Category.RAIN, "M5 3h14v18H5V3ZM12 3v18M5 12h14") { Rain(0.5f, 150f, 1_100f, 0.3f, 28f, 0.25f, 0.005f, 0.2f, 2_200f, 4_200f, density = 1_400f) * 1.6f },
  Sound("ocean", "Ocean", Category.WATER, "M3 9c3-3 6-3 9 0s6 3 9 0M3 15c3-3 6-3 9 0s6 3 9 0") { Ocean() },
  Sound("creek", "Creek", Category.WATER, "M3 7c3-2 6-2 9 0s6 2 9 0M3 12c3-2 6-2 9 0s6 2 9 0M3 17c3-2 6-2 9 0s6 2 9 0") { Water(0.24f, 250f, 4_000f, 0.25f, 80f, 380f, 3_000f, 0.2f) * 2.6f },
  Sound("stream", "Babbling brook", Category.WATER, "M3 8c2-2 4-2 6 0s4 2 6 0 4-2 6 0M5 14c2-2 4-2 6 0s4 2 6 0M9 19c1-1 2-1 3 0s2 1 3 0") { Water(0.11f, 700f, 6_500f, 0.05f, 150f, 800f, 4_800f, 0.13f) * 4.5f },
  Sound("waterfall", "Waterfall", Category.WATER, "M6 3v8M10 3v11M14 3v11M18 3v8M4 18c2-2 4-2 6 0s4 2 6 0 2-1 4-1") { Water(1.3f, 90f, 3_500f, 1.2f, 0f, 0f, 0f, 0f) * 0.7f },
  Sound("drip", "Drip", Category.WATER, ICON_DROP) { Drips(1.3f, 0.9f, 0.3f, 700f, 1_900f) * 2.0f },
  Sound("cave", "Cave drips", Category.WATER, "M3 20c0-9 4-15 9-15s9 6 9 15M12 10v3M12 16v.5") { Drips(0.7f, 4.5f, 1.1f, 480f, 1_400f) * 1.4f },
  Sound("wind", "Wind", Category.NATURE, "M3 9h11a3 3 0 1 0-3-3M3 15h15a3 3 0 1 1-3 3") { Wind(0f) },
  Sound("trees", "Wind in trees", Category.NATURE, "M12 3 6 12h3l-4 6h14l-4-6h3L12 3ZM12 18v3") { Wind(1f) * 0.62f },
  Sound("fire", "Fire", Category.NATURE, "M12 3c1 4 6 6 6 11a6 6 0 0 1-12 0c0-2 1-4 3-5 0 2 1 3 2 3 1-3-1-6 1-9Z") { Fire() * 1.9f },
  Sound("crickets", "Crickets", Category.NATURE, "M12 6c3 0 4 3 4 6s-1 8-4 8-4-5-4-8 1-6 4-6ZM8 11l-4-2M16 11l4-2M8 15l-4 2M16 15l4 2M10 6 8 3M14 6l2-3") { Crickets() * 4.5f },
  Sound("frogs", "Frogs", Category.NATURE, "M5 14a7 5 0 0 0 14 0c0-2-1-4-3-5a2.5 2.5 0 1 0-4-1 2.5 2.5 0 1 0-4 1c-2 1-3 3-3 5ZM9 15c2 1.5 4 1.5 6 0") { Frogs() * 3.0f },
  Sound("birds", "Night birds", Category.NATURE, "M4 13c3-6 9-7 13-4l4-1-3 3c0 5-4 8-9 8H4l4-3c-2 0-3-1-4-3ZM15 10v.5") { NightBirds() * 2.0f },
  Sound("owls", "Owls", Category.NATURE, "M6 5c2 1 4 1 6 0 2 1 4 1 6 0v9a6 6 0 0 1-12 0V5ZM9.5 9.5a1.5 1.5 0 1 0 0 3 1.5 1.5 0 0 0 0-3ZM14.5 9.5a1.5 1.5 0 1 0 0 3 1.5 1.5 0 0 0 0-3ZM12 13v1.5") { Owls() },
  Sound("white", "White noise", Category.NOISE, "M3 12h3l2-7 3 14 3-11 2 7 2-3h3") { Noise(Colour.WHITE, 0.3f) * 0.8f },
  Sound("pink", "Pink noise", Category.NOISE, "M3 12h4l2-4 3 8 3-6 2 2h4") { Noise(Colour.PINK, 1f) * 0.8f },
  Sound("brown", "Brown noise", Category.NOISE, "M3 13c2-3 4-3 6 0s4 3 6 0 4-3 6 0") { Noise(Colour.BROWN, 0.85f) },
  Sound("fan", "Fan", Category.NOISE, "M12 10a2 2 0 1 0 0 4 2 2 0 0 0 0-4ZM12 10c-1-4 1-7 4-6 2 1 1 5-2 7M14 12c4-1 7 1 6 4-1 2-5 1-7-2M12 14c1 4-1 7-4 6-2-1-1-5 2-7M10 12c-4 1-7-1-6-4 1-2 5-1 7 2") { Fan() * 0.6f },
  Sound("train", "Train", Category.NOISE, "M7 3h10a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2ZM5 11h14M8 21l2-4M16 21l-2-4") { Train() * 1.15f },
  Sound("hum", "Low hum", Category.NOISE, "M3 12h3M9 8v8M13 5v14M17 8v8M21 12h-1") { Hum() * 0.6f },
  Sound("bowls", "Singing bowls", Category.TONES, "M4 11h16a8 8 0 0 1-16 0ZM9 19l-1 2h8l-1-2M15 3l3 5") { Bowls() },
  Sound("chimes", "Wind chimes", Category.TONES, "M4 4h16M7 4v9M12 4v13M17 4v7M7 13v2M12 17v2M17 11v2") { Chimes() * 2.0f },
  Sound("drone", "Soft drone", Category.TONES, "M3 12c2-5 4-5 6 0s4 5 6 0 4-5 6 0M3 17h18") { Drone() },
  Sound("heartbeat", "Heartbeat", Category.TONES, "M12 20s-7-4.5-7-10a4 4 0 0 1 7-2.5A4 4 0 0 1 19 10c0 5.5-7 10-7 10ZM7 12h3l1-2 2 4 1-2h3") { Heartbeat() * 2.8f },
)

/** Sets a sound's place in the mix, so every slider means about the same loudness. */
private operator fun Source.times(gain: Float): Source = Source { left, right, count ->
  render(left, right, count)
  for (i in 0 until count) {
    left[i] *= gain
    right[i] *= gain
  }
}

fun soundById(id: String) = SOUNDS.firstOrNull { it.id == id }
