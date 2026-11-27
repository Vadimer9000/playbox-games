package com.playbox.games.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object PlayBoxColors {
    val CreamBg = Color(0xFFFCECD5)
    val CreamDarker = Color(0xFFF4DEC0)
    val HeaderSurface = Color(0xFFFFF8EE)
    val DarkInk = Color(0xFF232733)
    val MutedInk = Color(0xFF5B6370)
    val BoltGold = Color(0xFFFFC93C)
    val BoltBorder = Color(0xFFE5A91D)
    val OnlinePurple = Color(0xFF7B2CBF)
    val OnlineMagenta = Color(0xFFF72585)
    val Player1Color = Color(0xFFEF476F)
    val Player2Color = Color(0xFF118AB2)
    val SuccessGreen = Color(0xFF06D6A0)
    val CardWhite = Color(0xFFFFFFFF)
}

private val PlayBoxScheme = lightColorScheme(
    primary = PlayBoxColors.OnlinePurple,
    onPrimary = Color.White,
    secondary = PlayBoxColors.Player2Color,
    onSecondary = Color.White,
    background = PlayBoxColors.CreamBg,
    onBackground = PlayBoxColors.DarkInk,
    surface = PlayBoxColors.HeaderSurface,
    onSurface = PlayBoxColors.DarkInk
)

private val PlayBoxTypography = Typography(
    headlineMedium = TextStyle(
        fontWeight = FontWeight.ExtraBold,
        fontSize = 24.sp,
        letterSpacing = (-0.3).sp
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.ExtraBold,
        fontSize = 19.sp
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp
    )
)

@Composable
fun PlayBoxTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = PlayBoxScheme,
        typography = PlayBoxTypography,
        content = content
    )
}

fun Modifier.bounceClick(enabled: Boolean = true, onClick: () -> Unit): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.95f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "bounce"
    )
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled,
            onClick = onClick
        )
}
