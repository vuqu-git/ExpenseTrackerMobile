@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.expensetracker.app.ui

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.expensetracker.app.data.Category
import com.expensetracker.app.data.HOME_LEDGER
import com.expensetracker.app.data.Tx
import com.expensetracker.app.util.Currencies
import com.expensetracker.app.util.Dates
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

// ================= pure helpers =================

fun parseColorHex(hex: String): Color = Color(android.graphics.Color.parseColor(hex))

fun catOf(d: Data, id: Long?): Category? = id?.let { wanted -> d.categories.firstOrNull { it.id == wanted } }

fun ledgerCurrency(d: Data, id: Long): String =
    if (id == HOME_LEDGER) d.homeCurrency
    else d.trips.firstOrNull { it.id == id }?.currency ?: d.homeCurrency

fun ledgerLabel(d: Data, id: Long): String =
    if (id == HOME_LEDGER) "Home" else d.trips.firstOrNull { it.id == id }?.name ?: "—"

fun txCurrency(d: Data, t: Tx): String = ledgerCurrency(d, t.ledgerId)

fun filteredTx(d: Data, ui: UiState): List<Tx> {
    var list = d.txs.filter { it.ledgerId == ui.ledgerId }
    if (ui.monthMode) list = list.filter { it.date.startsWith(ui.month.toString()) }
    if (ui.dirFilter != null) list = list.filter { it.direction == ui.dirFilter }
    if (ui.catFilter != null) list = list.filter { it.categoryId == ui.catFilter }
    if (ui.uncatFilter) list = list.filter { catOf(d, it.categoryId) == null }
    if (ui.search.isNotBlank()) {
        val q = ui.search.trim().lowercase()
        list = list.filter { (it.note ?: "").lowercase().contains(q) }
    }
    return list.sortedWith(compareByDescending<Tx> { it.date }.thenByDescending { it.id })
}

fun parseAmountInput(s: String): Double? {
    val t = s.trim().replace(',', '.')
    if (!Regex("^\\d+(\\.\\d+)?$").matches(t)) return null
    val v = t.toDouble()
    return if (v > 0) v else null
}

fun largestRemainderPcts(values: List<Double>): List<Int> {
    val total = values.sum()
    if (total <= 0 || values.isEmpty()) return List(values.size) { 0 }
    val raw = values.map { it * 100.0 / total }
    val base = raw.map { kotlin.math.floor(it).toInt() }.toMutableList()
    var rem = 100 - base.sum()
    if (rem > 0) {
        val order = raw.mapIndexed { i, v -> i to (v - base[i]) }.sortedByDescending { it.second }
        var k = 0
        while (rem > 0) { base[order[k % order.size].first] += 1; rem--; k++ }
    }
    return base
}

fun readText(context: Context, uri: Uri): String? = try {
    context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText()
} catch (e: Exception) { null }

fun writeText(context: Context, uri: Uri, text: String, okMsg: String = "Export saved") {
    try {
        val os = context.contentResolver.openOutputStream(uri, "w")
            ?: throw IllegalStateException("Cannot open output stream")
        os.use { it.write(text.toByteArray(Charsets.UTF_8)) }
        Toast.makeText(context, okMsg, Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
    }
}

// ================= small components =================

@Composable
fun SectionTitle(title: String, trailing: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 16.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(title.uppercase(), color = C.Faint, fontSize = 11.5.sp, fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

@Composable
fun EmptyState(icon: ImageVector, text: String) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 34.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = C.Faint, modifier = Modifier.size(30.dp))
        Spacer(Modifier.height(10.dp))
        Text(text, color = C.Mut, fontSize = 13.sp, textAlign = TextAlign.Center, lineHeight = 18.sp)
    }
}

@Composable
fun CatChip(cat: Category?) {
    if (cat == null) {
        Row(Modifier.clip(RoundedCornerShape(50)).background(Color(0x1E9D98A4))
            .padding(horizontal = 9.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(C.Faint))
            Text("Uncategorized", color = C.Mut, fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        }
    } else {
        val col = parseColorHex(cat.color)
        Row(Modifier.clip(RoundedCornerShape(50)).background(col.copy(alpha = 0.11f))
            .padding(horizontal = 9.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(col))
            Text(cat.name, color = col, fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        }
    }
}

@Composable
fun StatusChip(open: Boolean) {
    Row(Modifier.clip(RoundedCornerShape(50)).background(if (open) Color(0x1A4FE084) else C.S2)
        .padding(horizontal = 9.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(if (open) C.Income else C.Faint))
        Text(if (open) "Open" else "Closed", color = if (open) Color(0xFF7BE495) else C.Mut,
            fontSize = 10.5.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun LedgerChipButton(label: String, sub: String, isHome: Boolean, onClick: () -> Unit) {
    Row(Modifier.clip(RoundedCornerShape(50)).background(C.S2)
        .border(1.dp, C.Outline, RoundedCornerShape(50))
        .clickable(onClick = onClick)
        .padding(start = 12.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(if (isHome) Icons.Rounded.Home else Icons.Rounded.FlightTakeoff, null,
            tint = C.Mut, modifier = Modifier.size(16.dp))
        Text(label, color = C.Fg, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        Text("· $sub", color = C.Mut, fontSize = 13.sp)
        Icon(Icons.Rounded.KeyboardArrowDown, null, tint = C.Mut, modifier = Modifier.size(16.dp))
    }
}

@Composable
fun TripNote() {
    Row(Modifier.padding(start = 18.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(Icons.Rounded.Info, null, tint = C.Amber, modifier = Modifier.size(14.dp))
        Text("Trip context — nothing here affects home totals or the Balance tab.",
            color = C.Amber.copy(alpha = 0.85f), fontSize = 11.5.sp)
    }
}

@Composable
fun MonthNav(month: YearMonth, onPrev: () -> Unit, onNext: () -> Unit, onLabel: () -> Unit) {
    Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButtonCircle(Icons.Rounded.ChevronLeft) { onPrev() }
        Box(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).clickable(onClick = onLabel).padding(10.dp),
            contentAlignment = Alignment.Center) {
            Text(month.format(Dates.MONTH_YEAR), color = C.Fg, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }
        IconButtonCircle(Icons.Rounded.ChevronRight) { onNext() }
    }
}

@Composable
fun TotalsRow(income: Double, expenses: Double, net: Double, currency: String) {
    Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TotalCard("Income", Currencies.format(income, currency), C.Income, Modifier.weight(1f))
        TotalCard("Expenses", Currencies.format(expenses, currency), C.Expense, Modifier.weight(1f))
        TotalCard("Net", Currencies.formatSigned(net, currency),
            if (net < 0) C.Expense else C.Income, Modifier.weight(1.15f), highlight = true)
    }
}

@Composable
fun TotalCard(label: String, value: String, color: Color, modifier: Modifier = Modifier, highlight: Boolean = false) {
    Surface(modifier, shape = RoundedCornerShape(16.dp), color = if (highlight) C.S3 else C.S2,
        border = if (highlight) BorderStroke(1.dp, C.Outline) else null) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 11.dp)) {
            Text(label.uppercase(), color = C.Faint, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.9.sp)
            Spacer(Modifier.height(6.dp))
            Text(value, color = color, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun MiniRow(date: String, note: String?, amount: String, positive: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(date, color = C.Faint, fontSize = 11.sp, modifier = Modifier.width(48.dp))
        Text(note ?: "No note", color = if (note == null) C.Faint else C.Fg, fontSize = 13.5.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text(amount, color = if (positive) C.Income else C.Expense, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun FieldLabel(text: String) {
    Text(text.uppercase(), color = C.Faint, fontSize = 11.sp, fontWeight = FontWeight.Bold,
        letterSpacing = 0.8.sp, modifier = Modifier.padding(start = 18.dp, top = 10.dp, bottom = 6.dp))
}

@Composable
fun FieldError(text: String) {
    Text(text, color = C.ExpenseSoft, fontSize = 11.5.sp, modifier = Modifier.padding(start = 18.dp, top = 5.dp))
}

@Composable
fun HintCard(text: String) {
    Text(text, color = C.Mut, fontSize = 11.5.sp, lineHeight = 17.sp,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth()
            .border(1.dp, Color(0xFF2C2C33), RoundedCornerShape(12.dp)).padding(13.dp))
}

@Composable
fun LockNote(text: String) {
    Row(Modifier.padding(start = 18.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(Icons.Rounded.Lock, null, tint = C.Mut, modifier = Modifier.size(14.dp))
        Text(text, color = C.Mut, fontSize = 11.5.sp)
    }
}

// ================= text fields =================

@Composable
fun Field(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    error: Boolean = false,
    singleLine: Boolean = true,
    minLines: Int = 1,
    decimal: Boolean = false,
    bold: Boolean = false,
    fontSize: TextUnit = 14.5.sp,
    shape: Shape = RoundedCornerShape(14.dp),
    leading: (@Composable () -> Unit)? = null,
    suffix: (@Composable () -> Unit)? = null
) {
    var focused by remember { mutableStateOf(false) }
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = singleLine,
        minLines = if (singleLine) 1 else minLines,
        textStyle = TextStyle(color = C.Fg, fontSize = fontSize,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal),
        keyboardOptions = if (decimal) KeyboardOptions(keyboardType = KeyboardType.Decimal) else KeyboardOptions.Default,
        modifier = modifier.onFocusChanged { focused = it.isFocused },
        decorationBox = { inner ->
            Row(
                Modifier.fillMaxWidth()
                    .background(C.S2, shape)
                    .border(1.5.dp, when {
                        error -> C.Expense
                        focused -> C.Pri
                        else -> Color.Transparent
                    }, shape)
                    .padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (leading != null) { leading(); Spacer(Modifier.width(8.dp)) }
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) Text(placeholder, color = C.Faint, fontSize = fontSize)
                    inner()
                }
                if (suffix != null) { Spacer(Modifier.width(8.dp)); suffix() }
            }
        }
    )
}

@Composable
fun FieldShell(
    modifier: Modifier = Modifier,
    error: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier
            .fillMaxWidth()
            .background(C.S2, RoundedCornerShape(14.dp))
            .border(1.5.dp, if (error) C.Expense else Color.Transparent, RoundedCornerShape(14.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

// ================= buttons =================

@Composable
fun AppButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = true,
    danger: Boolean = false,
    tonal: Boolean = false,
    enabled: Boolean = true
) {
    val bg = when {
        !enabled -> C.S3
        filled && danger -> C.Expense
        filled -> C.Pri
        tonal && danger -> Color(0x1FFF5449)
        tonal -> C.PriC
        else -> Color.Transparent
    }
    val fg = when {
        !enabled -> C.Faint
        filled && danger -> Color(0xFFFFEBE9)
        filled -> C.OnPri
        tonal && danger -> C.ExpenseSoft
        tonal -> C.OnPriC
        else -> C.Pri
    }
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text, color = fg, fontSize = 13.5.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun <T> Seg(options: List<Pair<T, String>>, value: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.clip(RoundedCornerShape(50)).border(1.dp, C.Outline, RoundedCornerShape(50))) {
        options.forEachIndexed { i, (v, label) ->
            val on = v == value
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(50))
                    .background(if (on) C.S3 else Color.Transparent)
                    .clickable { onSelect(v) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(label, color = if (on) C.Pri else C.Mut, fontSize = 12.5.sp,
                    fontWeight = if (on) FontWeight.Medium else FontWeight.Normal, maxLines = 1)
            }
        }
    }
}

@Composable
fun FilterChipBtn(icon: ImageVector, label: String, active: Boolean, ghost: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(10.dp))
            .border(1.dp, if (active) C.Pri else C.Outline, RoundedCornerShape(10.dp))
            .background(if (active) Color(0x1ACFBCFF) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (!ghost) Icon(icon, null, tint = if (active) C.Pri else C.Mut, modifier = Modifier.size(15.dp))
        Text(label, color = when { ghost -> C.Mut; active -> C.Pri; else -> C.Fg }, fontSize = 12.5.sp, maxLines = 1)
    }
}

// ================= bars / rows / cards =================

@Composable
fun SubBar(title: String, onBack: () -> Unit, trailing: (@Composable RowScope.() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(start = 6.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, tint = C.Fg)
        }
        Text(title, color = C.Fg, fontSize = 16.5.sp, fontWeight = FontWeight.SemiBold,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 8.dp).weight(1f))
        trailing?.invoke(this)
    }
}

@Composable
fun IconButtonCircle(icon: ImageVector, tint: Color = C.Fg, onClick: () -> Unit) {
    Box(Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
    }
}

@Composable
fun CardRow(onClick: (() -> Unit)? = null, content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp).fillMaxWidth()
            .clip(RoundedCornerShape(16.dp)).background(C.S2)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        content = content
    )
}

@Composable
fun IconBox(icon: ImageVector) {
    Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(C.PriC), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = C.OnPriC, modifier = Modifier.size(19.dp))
    }
}

@Composable
fun SettingsItem(icon: ImageVector, title: String, sub: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(C.S2), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = C.Mut, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = C.Fg, fontSize = 14.5.sp)
            Text(sub, color = C.Mut, fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.Rounded.ChevronRight, null, tint = C.Faint, modifier = Modifier.size(18.dp))
    }
}

@Composable
fun LabelRow(
    title: String,
    sub: String? = null,
    dotColor: Color? = null,
    icon: ImageVector? = null,
    selected: Boolean = false,
    onClick: () -> Unit
) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        when {
            dotColor != null -> Box(Modifier.size(10.dp).clip(CircleShape).background(dotColor))
            icon != null -> Icon(icon, null, tint = C.Mut, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = C.Fg, fontSize = 14.5.sp, fontWeight = FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (sub != null) Text(sub, color = C.Mut, fontSize = 11.5.sp)
        }
        if (selected) Icon(Icons.Rounded.Check, null, tint = C.Pri, modifier = Modifier.size(20.dp))
    }
}

// ================= dialogs =================

@Composable
fun ConfirmDialog(title: String, body: String, confirmLabel: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(confirmLabel, color = C.ExpenseSoft, fontWeight = FontWeight.Medium) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = C.Pri) } },
        title = { Text(title, color = C.Fg, fontSize = 17.sp, fontWeight = FontWeight.Bold) },
        text = { Text(body, color = C.Mut, fontSize = 13.sp, lineHeight = 19.sp) },
        containerColor = C.DialogBg,
        titleContentColor = C.Fg,
        textContentColor = C.Mut
    )
}

@Composable
fun InfoDialog(title: String, text: String, onOk: () -> Unit) {
    AlertDialog(
        onDismissRequest = onOk,
        confirmButton = { TextButton(onClick = onOk) { Text("OK", color = C.Pri) } },
        title = { Text(title, color = C.Fg, fontSize = 17.sp, fontWeight = FontWeight.Bold) },
        text = { Text(text, color = C.Mut, fontSize = 13.sp, lineHeight = 19.sp) },
        containerColor = C.DialogBg,
        titleContentColor = C.Fg,
        textContentColor = C.Mut
    )
}

@Composable
fun MonthPickerDialog(current: YearMonth, monthsWithData: Set<String>, onPick: (YearMonth) -> Unit, onDismiss: () -> Unit) {
    var year by remember { mutableIntStateOf(current.year) }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(28.dp), color = C.DialogBg) {
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButtonCircle(Icons.Rounded.ChevronLeft) { year-- }
                    Text("$year", color = C.Fg, fontSize = 17.sp, fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                    IconButtonCircle(Icons.Rounded.ChevronRight) { year++ }
                }
                Spacer(Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (r in 0 until 4) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            for (c in 0 until 3) {
                                val m = r * 3 + c + 1
                                val ym = "$year-${"%02d".format(m)}"
                                val selected = current.year == year && current.monthValue == m
                                val hasData = monthsWithData.contains(ym)
                                Box(
                                    Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                                        .background(if (selected) C.PriC else C.S2)
                                        .clickable { onPick(YearMonth.of(year, m)) }
                                        .padding(vertical = 11.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(Dates.MONTHS_SHORT[m - 1], color = if (selected) C.OnPriC else C.Fg,
                                            fontSize = 13.sp,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                                        if (hasData) Box(Modifier.size(4.dp).clip(CircleShape).background(C.Pri))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DatePickerDialogBox(initialIso: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val init = runCatching {
        LocalDate.parse(initialIso).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    }.getOrDefault(-1L)
    val state = rememberDatePickerState(initialSelectedDateMillis = if (init >= 0) init else null)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let {
                    onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString())
                }
                onDismiss()
            }) { Text("OK", color = C.Pri) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = C.Pri) } }
    ) { DatePicker(state = state) }
}

// ================= list pieces =================

@Composable
fun GroupHeader(text: String) {
    Box(Modifier.fillMaxWidth().background(Color.Black)
        .padding(start = 16.dp, top = 13.dp, end = 16.dp, bottom = 6.dp)) {
        Text(text, color = C.Mut, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.3.sp)
    }
}

@Composable
fun TxRow(tx: Tx, cat: Category?, currency: String, showDate: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(tx.note ?: "No note", color = if (tx.note == null) C.Faint else C.Fg,
                fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CatChip(cat)
                if (showDate) Text(Dates.fmtDayShort(tx.date), color = C.Faint, fontSize = 11.sp)
            }
        }
        Text(Currencies.format(tx.amount, currency),
            color = if (tx.direction == "income") C.Income else C.Expense,
            fontSize = 14.5.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun SearchBarField(value: String, onChange: (String) -> Unit, onClear: () -> Unit) {
    Field(
        value = value,
        onValueChange = onChange,
        placeholder = "Search notes",
        shape = RoundedCornerShape(50),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth(),
        leading = { Icon(Icons.Rounded.Search, null, tint = C.Faint, modifier = Modifier.size(18.dp)) },
        suffix = {
            if (value.isNotEmpty()) {
                Box(Modifier.size(30.dp).clip(CircleShape).clickable(onClick = onClear),
                    contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Close, null, tint = C.Faint, modifier = Modifier.size(16.dp))
                }
            }
        }
    )
}
