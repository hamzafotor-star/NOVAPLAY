package tv.novaplay.iptv.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import tv.novaplay.iptv.R

val ObsidianBackground = Color(0xFF060911)
val SurfaceDark = Color(0xFF0D1320)
val SurfaceCard = Color(0xFF151E30)
val SurfaceElevated = Color(0xFF1E2A42)
val ElectricCyan = Color(0xFF00E5FF)
val NeonViolet = Color(0xFF7C4DFF)
val EmeraldLive = Color(0xFF00E676)
val LiveRed = Color(0xFFFF2E4C)
val GoldRating = Color(0xFFFFD54F)

val DmSansFamily = FontFamily(
    Font(R.font.dm_sans, FontWeight.Normal),
    Font(R.font.dm_sans, FontWeight.Medium)
)

val ManropeBoldFamily = FontFamily(
    Font(R.font.manrope_bold, FontWeight.Bold)
)

private val NovaColorScheme = darkColorScheme(
    primary = ElectricCyan,
    secondary = NeonViolet,
    tertiary = GoldRating,
    background = ObsidianBackground,
    surface = SurfaceDark,
    surfaceVariant = SurfaceCard,
    onPrimary = Color(0xFF001F26),
    onSecondary = Color.White,
    onBackground = Color(0xFFF0F4F8),
    onSurface = Color(0xFFF0F4F8),
    onSurfaceVariant = Color(0xFF9FB1C6)
)

private val NovaTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = ManropeBoldFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = ManropeBoldFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 19.sp
    ),
    titleLarge = TextStyle(
        fontFamily = ManropeBoldFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 17.sp
    ),
    titleMedium = TextStyle(
        fontFamily = ManropeBoldFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = DmSansFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = DmSansFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp
    ),
    bodySmall = TextStyle(
        fontFamily = DmSansFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp
    ),
    labelSmall = TextStyle(
        fontFamily = DmSansFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp
    )
)

@Composable
fun NovaPlayTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = NovaColorScheme,
        typography = NovaTypography,
        content = content
    )
}
