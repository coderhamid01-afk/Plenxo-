package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object PlenxoColors {
    val Primary = PlenxoBluePrimary
    val PrimaryDark = PlenxoBlueDarkPrimary
    val Secondary = PlenxoBlueSecondary
    val Background = PlenxoNavyBackground
    val Surface = PlenxoNavySurface
    val SurfaceCard = PlenxoNavySurfaceVariant
    val TextPrimary = PlenxoTextPrimaryDark
    val TextSecondary = PlenxoTextSecondaryDark
    val Error = PlenxoError
    val Success = PlenxoSuccess
    val Warning = PlenxoWarning
    val Divider = PlenxoBorderDark
}

object PlenxoSpacing {
    val ExtraSmall: Dp = 4.dp
    val Small: Dp = 8.dp
    val Medium: Dp = 16.dp
    val Large: Dp = 24.dp
    val ExtraLarge: Dp = 32.dp
}

object PlenxoShapes {
    val Small = RoundedCornerShape(8.dp)
    val Medium = RoundedCornerShape(12.dp)
    val Large = RoundedCornerShape(16.dp)
    val ExtraLarge = RoundedCornerShape(24.dp)
    val Pill = RoundedCornerShape(50.dp)
}

object PlenxoElevation {
    val None: Dp = 0.dp
    val Low: Dp = 2.dp
    val Medium: Dp = 4.dp
    val High: Dp = 8.dp
}

object PlenxoTypography {
    val ScreenTitle = TextStyle(
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.2).sp
    )
    val SectionTitle = TextStyle(
        fontSize = 18.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.sp
    )
    val Title = TextStyle(
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold
    )
    val Body = TextStyle(
        fontSize = 15.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 22.sp
    )
    val BodyBold = TextStyle(
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        lineHeight = 22.sp
    )
    val Label = TextStyle(
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium
    )
    val Caption = TextStyle(
        fontSize = 12.sp,
        fontWeight = FontWeight.Normal
    )
    val Button = TextStyle(
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.2.sp
    )
}

