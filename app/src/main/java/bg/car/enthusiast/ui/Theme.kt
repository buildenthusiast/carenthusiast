package bg.car.enthusiast.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import bg.car.enthusiast.R
import bg.car.enthusiast.core.Car
import bg.car.enthusiast.core.Texts

/** „Нощно премиум“: графитен фон, тъмни карти и златисти акценти. */
val Ink = Color(0xFF0E0F12)
val CardBg = Color(0xFF17191E)
val CardHi = Color(0xFF1F2228)
val Line = Color(0xFF2C2F36)
val Muted = Color(0xFFA3A6AD)
val Gold = Color(0xFFF5B323)
val GoldBg = Color(0xFF1F1A0E)
val GoldLine = Color(0xFF5C4612)
val RedBg = Color(0xFF2A1416)
val RedLine = Color(0xFF6B2A2E)

private val colors = darkColorScheme(
    primary = Gold,
    onPrimary = Color(0xFF1A1300),
    primaryContainer = GoldBg,
    onPrimaryContainer = Color(0xFFFFE3A3),
    secondary = Gold,
    onSecondary = Color(0xFF1A1300),
    secondaryContainer = CardHi,
    onSecondaryContainer = Color(0xFFF2F2F3),
    background = Ink,
    onBackground = Color(0xFFF2F2F3),
    surface = Ink,
    onSurface = Color(0xFFF2F2F3),
    surfaceVariant = CardBg,
    onSurfaceVariant = Muted,
    surfaceContainerLowest = Ink,
    surfaceContainerLow = CardBg,
    surfaceContainer = CardBg,
    surfaceContainerHigh = CardBg,
    surfaceContainerHighest = CardBg,
    outline = Line,
    outlineVariant = Line,
    error = Color(0xFFFF6B6B),
)

private val shapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun CarTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, shapes = shapes, content = content)
}

/** Карта на колата със странична рисунка и златист отблясък отдолу. */
@Composable
fun CarHero(car: Car, modifier: Modifier = Modifier, extra: @Composable () -> Unit = {}) {
    Card(modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = CardBg)) {
        Column(Modifier.padding(20.dp)) {
            Text(car.name, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            val info = listOfNotNull(car.plate.ifBlank { null }, car.year?.toString(), car.odometer?.let { Texts.km(it) }).joinToString(" · ")
            if (info.isNotBlank()) Text(info, color = Muted, fontSize = 13.sp)
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .padding(top = 8.dp)
                    .drawBehind {
                        drawOval(
                            Brush.radialGradient(listOf(Gold.copy(alpha = 0.22f), Color.Transparent), center = Offset(size.width / 2, size.height - 10f), radius = size.width / 2),
                            topLeft = Offset(size.width * 0.06f, size.height - 26f),
                            size = Size(size.width * 0.88f, 30f),
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.car_side), null, Modifier.fillMaxWidth().height(104.dp), tint = Color.Unspecified)
            }
            extra()
        }
    }
}

/** Заглавие на раздел с малки главни букви. */
@Composable
fun SectionLabel(text: String) {
    Text(text.uppercase(), color = Muted, fontSize = 12.sp, letterSpacing = 1.5.sp, modifier = Modifier.padding(top = 4.dp))
}

@Composable
fun BrandHeader() {
    Row(Modifier.padding(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("CAR ENTHUSIAST", color = Muted, fontSize = 13.sp, letterSpacing = 2.sp)
    }
}
