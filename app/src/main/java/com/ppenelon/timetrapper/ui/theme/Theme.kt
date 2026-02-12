package com.ppenelon.timetrapper.ui.theme

import android.os.Build
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.isSystemInDarkTheme

private val LightColorScheme = lightColorScheme(
    primary = androidx.compose.ui.graphics.Color(0xFF3F5F90),
    onPrimary = androidx.compose.ui.graphics.Color(0xFFFFFFFF),
    primaryContainer = androidx.compose.ui.graphics.Color(0xFFD8E2FF),
    onPrimaryContainer = androidx.compose.ui.graphics.Color(0xFF001A41),
    secondary = androidx.compose.ui.graphics.Color(0xFF565F71),
    onSecondary = androidx.compose.ui.graphics.Color(0xFFFFFFFF),
    secondaryContainer = androidx.compose.ui.graphics.Color(0xFFDAE2F9),
    onSecondaryContainer = androidx.compose.ui.graphics.Color(0xFF131C2B),
    tertiary = androidx.compose.ui.graphics.Color(0xFF705574),
    onTertiary = androidx.compose.ui.graphics.Color(0xFFFFFFFF),
    tertiaryContainer = androidx.compose.ui.graphics.Color(0xFFFBD7FC),
    onTertiaryContainer = androidx.compose.ui.graphics.Color(0xFF29132E)
)

private val DarkColorScheme = darkColorScheme(
    primary = androidx.compose.ui.graphics.Color(0xFFAFC6FF),
    onPrimary = androidx.compose.ui.graphics.Color(0xFF042B60),
    primaryContainer = androidx.compose.ui.graphics.Color(0xFF254777),
    onPrimaryContainer = androidx.compose.ui.graphics.Color(0xFFD8E2FF),
    secondary = androidx.compose.ui.graphics.Color(0xFFBEC6DD),
    onSecondary = androidx.compose.ui.graphics.Color(0xFF283141),
    secondaryContainer = androidx.compose.ui.graphics.Color(0xFF3E4758),
    onSecondaryContainer = androidx.compose.ui.graphics.Color(0xFFDAE2F9),
    tertiary = androidx.compose.ui.graphics.Color(0xFFDEBCDF),
    onTertiary = androidx.compose.ui.graphics.Color(0xFF402843),
    tertiaryContainer = androidx.compose.ui.graphics.Color(0xFF583E5B),
    onTertiaryContainer = androidx.compose.ui.graphics.Color(0xFFFBD7FC)
)

private val TimeTrapperTypography = Typography(
    headlineMedium = TextStyle(
        fontSize = 28.sp,
        lineHeight = 36.sp,
        fontWeight = FontWeight.SemiBold
    ),
    titleLarge = TextStyle(
        fontSize = 22.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.SemiBold
    ),
    titleMedium = TextStyle(
        fontSize = 16.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.SemiBold
    ),
    bodyLarge = TextStyle(
        fontSize = 16.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.Normal
    ),
    bodyMedium = TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Normal
    ),
    labelLarge = TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Medium
    )
)

@Immutable
private object TimeTrapperShapes {
    val shapes = androidx.compose.material3.Shapes(
        extraSmall = RoundedCornerShape(8.dp),
        small = RoundedCornerShape(12.dp),
        medium = RoundedCornerShape(16.dp),
        large = RoundedCornerShape(24.dp),
        extraLarge = RoundedCornerShape(28.dp)
    )
}

@Composable
fun TimeTrapperTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = TimeTrapperTypography,
        shapes = TimeTrapperShapes.shapes,
        content = content
    )
}
