package com.expensetracker.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** App palette modeled on the mockup (pure black, OLED friendly). */
object C {
    val Bg = Color(0xFF000000)
    val S2 = Color(0xFF151518)
    val S3 = Color(0xFF1E1E22)
    val S4 = Color(0xFF28282D)
    val Outline = Color(0xFF2E2E34)
    val Fg = Color(0xFFE7E3EA)
    val Mut = Color(0xFF9D98A4)
    val Faint = Color(0xFF6D6876)
    val Pri = Color(0xFFCFBCFF)
    val OnPri = Color(0xFF241A40)
    val PriC = Color(0xFF39295C)
    val OnPriC = Color(0xFFE9DDFF)
    val Income = Color(0xFF4FE084)
    val Expense = Color(0xFFFF5449)
    val ExpenseSoft = Color(0xFFFFB4AB)
    val Amber = Color(0xFFFFD97A)
    val DialogBg = Color(0xFF211F26)
    val SheetBg = Color(0xFF1E1D22)
}

private val Scheme = darkColorScheme(
    primary = C.Pri, onPrimary = C.OnPri,
    primaryContainer = C.PriC, onPrimaryContainer = C.OnPriC,
    background = C.Bg, onBackground = C.Fg,
    surface = C.Bg, onSurface = C.Fg,
    surfaceVariant = C.S3, onSurfaceVariant = C.Mut,
    outline = C.Outline, outlineVariant = C.Outline,
    error = C.Expense, onError = Color(0xFF17161A)
)

@Composable
fun ExpenseTrackerTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, content = content)
}
