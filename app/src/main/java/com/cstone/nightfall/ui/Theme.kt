package com.cstone.nightfall.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cstone.nightfall.R

val Ground = Color(0xFF12100E)
val Panel = Color(0xFF1C1916)
val Raised = Color(0xFF26211C)
val Line = Color(0xFF332C25)
val LineStrong = Color(0xFF6B5B48)
val Ink = Color(0xFFF2E9DC)
val Muted = Color(0xFFC9BBA6)
val Faint = Color(0xFFA89A86)
val Amber = Color(0xFFE8A860)
val OnAmber = Color(0xFF2A1C0C)

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun manrope(weight: FontWeight) = Font(
  R.font.manrope,
  weight = weight,
  variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

val Manrope = FontFamily(
  manrope(FontWeight.Normal),
  manrope(FontWeight.Medium),
  manrope(FontWeight.SemiBold),
  manrope(FontWeight.Bold),
  manrope(FontWeight.ExtraBold),
)

fun style(size: Int, weight: FontWeight = FontWeight.Normal, color: Color = Ink, line: Int = 0) = TextStyle(
  fontFamily = Manrope,
  fontSize = size.sp,
  fontWeight = weight,
  color = color,
  lineHeight = if (line > 0) line.sp else (size * 1.4f).sp,
)

@Composable
fun NightfallTheme(content: @Composable () -> Unit) {
  MaterialTheme(
    colorScheme = darkColorScheme(
      primary = Amber,
      onPrimary = OnAmber,
      background = Ground,
      surface = Panel,
      onSurface = Ink,
      onBackground = Ink,
      outline = Line,
    ),
    typography = Typography(bodyLarge = style(16)),
    content = content,
  )
}

object Icons {
  const val MOON = "M20 14A8 8 0 1 1 10 4a7 7 0 0 0 10 10Z"
  const val BOOKMARK = "M6 3h12v18l-6-4-6 4V3Z"
  const val MEDITATE = "M12 8.5a2.5 2.5 0 1 0 0-5 2.5 2.5 0 0 0 0 5ZM12 11c-3 0-5 2-6 5l3 1M12 11c3 0 5 2 6 5l-3 1M5 20h14"
  const val HOME = "M4 11 12 4l8 7v9H4v-9ZM10 20v-6h4v6"
  const val BACK = "m15 5-7 7 7 7"
  const val DOWN = "m5 9 7 7 7-7"
  const val TIMER = "M12 21a8 8 0 1 0 0-16 8 8 0 0 0 0 16ZM12 9v4l3 2M9 2h6"
  const val PHONE = "M9 2h6a2 2 0 0 1 2 2v16a2 2 0 0 1-2 2H9a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2ZM11 18h2"
  const val BLUETOOTH = "m7 7 10 10-5 4V3l5 4L7 17"
  const val SPEAKER = "M7 3h10a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2ZM12 18a4 4 0 1 0 0-8 4 4 0 0 0 0 8ZM12 7h.01"
  const val CHECK = "m5 12 5 5 9-10"
  const val CROSS = "M6 6l12 12M18 6 6 18"
  const val TRASH = "M4 7h16M9 7V4h6v3M6 7l1 13h10l1-13"
  const val SETTINGS = "M4 7h10M18 7h2M4 17h2M10 17h10M16 5v4M8 15v4"
  const val INFO = "M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18ZM12 11v5M12 8h.01"
  const val PLAY = "M7 4v16l13-8L7 4Z"
  const val PAUSE = "M7 4h4v16H7zM14 4h4v16h-4z"
  const val STOP = "M6 6h12v12H6z"
}

/** Draws one of the 24-unit stroke icons from the design. */
@Composable
fun Glyph(path: String, size: Dp = 24.dp, color: Color = Ink, filled: Boolean = false, modifier: Modifier = Modifier) {
  val shape = remember(path) { PathParser().parsePathString(path).toPath() }
  Canvas(modifier.size(size)) {
    val ratio = this.size.width / 24f
    scale(ratio, ratio, androidx.compose.ui.geometry.Offset.Zero) {
      if (filled) {
        drawPath(shape, color)
      } else {
        drawPath(shape, color, style = Stroke(width = 1.7f, cap = StrokeCap.Round, join = StrokeJoin.Round))
      }
    }
  }
}

private val CardShape = RoundedCornerShape(18.dp)

@Composable
fun Card(
  modifier: Modifier = Modifier,
  border: Color = Line,
  background: Color = Panel,
  padding: PaddingValues = PaddingValues(16.dp),
  onClick: (() -> Unit)? = null,
  content: @Composable ColumnScope.() -> Unit,
) {
  val base = modifier.clip(CardShape).background(background).border(1.dp, border, CardShape)
  Column(
    (if (onClick != null) base.clickable(role = Role.Button, onClick = onClick) else base).padding(padding),
    content = content,
  )
}

@Composable
fun PrimaryButton(text: String, modifier: Modifier = Modifier, height: Dp = 72.dp, onClick: () -> Unit) {
  Box(
    modifier
      .fillMaxWidth()
      .height(height)
      .clip(RoundedCornerShape(height / 2))
      .background(Amber)
      .clickable(role = Role.Button, onClick = onClick),
    contentAlignment = Alignment.Center,
  ) {
    Text(text, style = style(20, FontWeight.ExtraBold, OnAmber))
  }
}

@Composable
fun OutlineButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
  Box(
    modifier
      .fillMaxWidth()
      .height(64.dp)
      .clip(RoundedCornerShape(32.dp))
      .border(1.dp, Amber, RoundedCornerShape(32.dp))
      .clickable(role = Role.Button, onClick = onClick),
    contentAlignment = Alignment.Center,
  ) {
    Text(text, style = style(16, FontWeight.ExtraBold, Amber), textAlign = TextAlign.Center)
  }
}

@Composable
fun TextAction(text: String, color: Color = Amber, onClick: () -> Unit) {
  Box(
    Modifier
      .sizeIn(minWidth = 44.dp, minHeight = 44.dp)
      .clickable(role = Role.Button, onClick = onClick),
    contentAlignment = Alignment.Center,
  ) {
    Text(text, style = style(15, FontWeight.Bold, color))
  }
}

@Composable
fun IconAction(path: String, label: String, color: Color = Ink, onClick: () -> Unit) {
  Box(
    Modifier
      .size(44.dp)
      .semantics { contentDescription = label; role = Role.Button }
      .clickable(onClick = onClick),
    contentAlignment = Alignment.Center,
  ) {
    Glyph(path, 26.dp, color)
  }
}

@Composable
fun TopBar(title: String, onBack: () -> Unit, action: @Composable () -> Unit = {}) {
  Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
    IconAction(Icons.BACK, "Back", onClick = onBack)
    Text(title, Modifier.weight(1f).padding(start = 4.dp), style = style(20, FontWeight.ExtraBold), maxLines = 1)
    action()
  }
}

@Composable
fun Heading(text: String) {
  Text(text, style = style(14, FontWeight.Bold, Muted))
}

enum class Tab(val label: String, val icon: String) {
  TONIGHT("Tonight", Icons.MOON),
  MIXES("Mixes", Icons.BOOKMARK),
  MEDITATE("Meditate", Icons.MEDITATE),
  ROUTINE("Routine", Icons.HOME),
  SETTINGS("Settings", Icons.SETTINGS),
}

@Composable
fun BottomTabs(current: Tab, onPick: (Tab) -> Unit) {
  Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
    Box(Modifier.fillMaxWidth().height(1.dp).background(Line))
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
      for (tab in Tab.entries) {
        val color = if (tab == current) Amber else Muted
        Column(
          Modifier
            .weight(1f)
            .heightIn(min = 68.dp)
            .clickable(role = Role.Tab) { onPick(tab) },
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.Center,
        ) {
          Glyph(tab.icon, 24.dp, color)
          Text(tab.label, Modifier.padding(top = 4.dp), style = style(12, FontWeight.SemiBold, color))
        }
      }
    }
  }
}

fun minutesLabel(minutes: Int) = when {
  minutes == 0 -> "All night"
  minutes % 60 == 0 -> "${minutes / 60} hr"
  minutes > 60 -> "${minutes / 60.0} hr"
  else -> "$minutes min"
}
