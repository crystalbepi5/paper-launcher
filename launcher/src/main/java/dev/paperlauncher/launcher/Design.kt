package dev.paperlauncher.launcher

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class Palette(val paper: Color, val shade: Color, val line: Color, val secondary: Color, val ink: Color)

val PaperColors = Palette(Color(0xFFFBF5EC), Color(0xFFEFE6D8), Color(0xFFD9CFBF), Color(0xFF5F5B54), Color(0xFF1C1B19))
val NightColors = Palette(Color(0xFF000000), Color(0xFF1C1B19), Color(0xFF3A3833), Color(0xFFA8A297), Color(0xFFD9D2C5))
val LocalPalette = compositionLocalOf { PaperColors }

val Sans = FontFamily(
    Font(R.font.dm_sans_regular, FontWeight.Normal),
    Font(R.font.dm_sans_medium, FontWeight.Medium),
    Font(R.font.dm_sans_semibold, FontWeight.SemiBold),
    Font(R.font.dm_sans_bold, FontWeight.Bold),
)
val Mono = FontFamily(Font(R.font.dm_mono_regular, FontWeight.Normal))

@Composable
fun Label(
    text: String,
    modifier: Modifier = Modifier,
    size: Int = 15,
    weight: FontWeight = FontWeight.Normal,
    color: Color = LocalPalette.current.ink,
    mono: Boolean = false,
    maxLines: Int = Int.MAX_VALUE,
) {
    BasicText(
        text,
        modifier,
        style = TextStyle(fontFamily = if (mono) Mono else Sans, fontSize = size.sp, fontWeight = weight, color = color),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

object Glyph {
    const val SEARCH = "M11 4a7 7 0 1 0 0 14 7 7 0 0 0 0-14zM16 16l5 5"
    const val BACK = "M15 5l-7 7 7 7"
    const val CLOSE = "M6 6l12 12M18 6L6 18"
    const val GRID = "M6 6h.01M12 6h.01M18 6h.01M6 12h.01M12 12h.01M18 12h.01M6 18h.01M12 18h.01M18 18h.01"
    const val MORE = "M5 12h.01M12 12h.01M19 12h.01"
    const val SETTINGS = "M4 7h10M18 7h2M14 5v4M4 17h2M10 17h10M6 15v4"
}

@Composable
fun Glyph(path: String, size: Dp = 22.dp, width: Float = 1.8f, description: String? = null) {
    val vector = remember(path, width) {
        ImageVector.Builder(defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
            .addPath(
                pathData = addPathNodes(path),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = width,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
            .build()
    }
    Image(
        rememberVectorPainter(vector),
        description,
        Modifier.size(size),
        colorFilter = ColorFilter.tint(LocalPalette.current.ink),
    )
}

@Composable
fun GlyphButton(path: String, description: String, width: Float = 1.8f, onClick: () -> Unit) {
    Box(
        Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .clickable(role = Role.Button, onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Glyph(path, width = width, description = description) }
}
