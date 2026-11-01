package com.cstone.nightfall.data

/** A pre-rendered session. Audio is bundled, so playing one needs no network and no API. */
data class Guided(val id: String, val title: String, val note: String, val minutes: Int) {
  val asset get() = "guided/$id.ogg"
}

val GUIDED = listOf(
  Guided("slow_countdown", "Slow countdown", "Ten down to one, then silence", 6),
  Guided("letting_go", "Letting go of the day", "Set down what's on your mind", 8),
  Guided("body_scan", "Body scan", "Head-to-toe heaviness and ease", 11),
  Guided("deep_rest", "Deep rest", "A yoga nidra style rotation", 10),
)

fun guidedById(id: String?) = GUIDED.firstOrNull { it.id == id }
