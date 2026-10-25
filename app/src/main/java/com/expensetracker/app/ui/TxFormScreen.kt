@file:OptIn(ExperimentalMaterial3Api::class)

package com.expensetracker.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.expensetracker.app.data.HOME_LEDGER
import com.expensetracker.app.util.CalcEval
import com.expensetracker.app.util.Currencies
import com.expensetracker.app.util.Dates
import java.time.LocalDate

@Composable
fun TxFormScreen(vm: AppViewModel, editingId: Long?) {
    val d = vm.data.collectAsState().value
    val f = vm.txForm.collectAsState().value
    val currency = f.ledgerId?.let { ledgerCurrency(d, it) } ?: d.homeCurrency
    val sym = Currencies.symbol(currency)

    var showLedger by remember { mutableStateOf(false) }
    var showCat by remember { mutableStateOf(false) }
    var showTpl by remember { mutableStateOf(false) }
    var showCalc by remember { mutableStateOf(false) }
    var showDate by remember { mutableStateOf(false) }
    var askDelete by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SubBar(if (editingId == null) "Add transaction" else "Edit transaction", { vm.pop() }) {
            if (editingId != null) {
                IconButtonCircle(Icons.Rounded.Delete, tint = C.ExpenseSoft) { askDelete = true }
            }
        }

        // Ledger
        FieldLabel("Ledger *")
        FieldShell(error = f.errLedger, onClick = { showLedger = true }) {
            Icon(
                when {
                    f.ledgerId == null -> Icons.Rounded.SwapHoriz
                    f.ledgerId == HOME_LEDGER -> Icons.Rounded.Home
                    else -> Icons.Rounded.FlightTakeoff
                }, null, tint = C.Mut, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                if (f.ledgerId == null) "Choose ledger"
                else "${ledgerLabel(d, f.ledgerId)} · ${ledgerCurrency(d, f.ledgerId)}",
                color = if (f.ledgerId == null) C.Faint else C.Fg, fontSize = 14.5.sp,
                modifier = Modifier.weight(1f))
            Icon(Icons.Rounded.KeyboardArrowDown, null, tint = C.Faint, modifier = Modifier.size(20.dp))
        }
        if (f.errLedger) FieldError("Choose a ledger.")

        // Direction (default Expense per FR-TX-01 / decision 33)
        FieldLabel("Direction *")
        Seg(listOf("expense" to "Expense", "income" to "Income"), f.direction,
            { v -> vm.updateTxForm { it.copy(direction = v) } },
            Modifier.padding(horizontal = 16.dp))

        // Date
        FieldLabel("Date *")
        FieldShell(onClick = { showDate = true }) {
            Icon(Icons.Rounded.CalendarMonth, null, tint = C.Mut, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (f.date.isBlank()) "Pick a date" else Dates.fmtDayFull(f.date),
                color = if (f.date.isBlank()) C.Faint else C.Fg, fontSize = 14.5.sp,
                modifier = Modifier.weight(1f))
            Icon(Icons.Rounded.KeyboardArrowDown, null, tint = C.Faint, modifier = Modifier.size(20.dp))
        }

        // Amount + adjacent calculator button (FR-TX-08)
        FieldLabel("Amount *")
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Field(
                value = f.amount,
                onValueChange = { v -> vm.updateTxForm { it.copy(amount = v, errAmount = false) } },
                placeholder = "0.00", error = f.errAmount, decimal = true, bold = true, fontSize = 17.sp,
                modifier = Modifier.weight(1f),
                suffix = { Text(sym, color = C.Mut, fontWeight = FontWeight.Medium) }
            )
            Spacer(Modifier.width(8.dp))
            IconButtonCircle(Icons.Rounded.Calculate) { showCalc = true }
        }
        if (f.errAmount) FieldError("Enter a positive amount (0 and negatives are rejected).")

        // Category
        FieldLabel("Category *")
        FieldShell(error = f.errCategory, onClick = { showCat = true }) {
            if (f.categoryId != null) {
                CatChip(catOf(d, f.categoryId))
            } else {
                Text("Select category", color = C.Faint, fontSize = 14.5.sp, modifier = Modifier.weight(1f))
            }
            Icon(Icons.Rounded.KeyboardArrowDown, null, tint = C.Faint, modifier = Modifier.size(20.dp))
        }
        if (f.errCategory) FieldError("Select a category.")

        // Note
        FieldLabel("Note")
        Field(
            value = f.note,
            onValueChange = { v -> vm.updateTxForm { it.copy(note = v) } },
            placeholder = "Optional note",
            singleLine = false, minLines = 2,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(Modifier.height(16.dp))
        AppButton("Use template", { showTpl = true },
            Modifier.padding(horizontal = 16.dp).fillMaxWidth(), filled = false, tonal = true)
        Spacer(Modifier.height(4.dp))
        AppButton("Save transaction", { vm.saveTxForm() },
            Modifier.padding(horizontal = 16.dp).fillMaxWidth())
        Spacer(Modifier.height(96.dp))
    }

    if (showLedger) LedgerSheet(vm, f.ledgerId ?: -1L,
        { id -> vm.txFormSetLedger(id); showLedger = false },
        { showLedger = false }, forForm = true)
    if (showCat && f.ledgerId != null) {
        CategoryPickerSheet(d, f.ledgerId, f.categoryId, { id ->
            vm.updateTxForm { it.copy(categoryId = id, errCategory = false) }; showCat = false
        }, { showCat = false })
    }
    if (showTpl && f.ledgerId != null) {
        TemplatePickerSheet(d, f.ledgerId, { id ->
            vm.applyTemplate(id); showTpl = false
        }, { showTpl = false })
    }
    if (showCalc) CalculatorDialog(
        initialAmount = f.amount,
        currency = currency,
        onTransfer = { v ->
            vm.updateTxForm { it.copy(amount = Currencies.plain(v), errAmount = false) }
            showCalc = false
        },
        onDismiss = { showCalc = false }
    )
    if (showDate) DatePickerDialogBox(
        initialIso = if (f.date.isNotBlank()) f.date else LocalDate.now().toString(),
        onPick = { iso -> vm.updateTxForm { it.copy(date = iso) } },
        onDismiss = { showDate = false }
    )
    if (askDelete && editingId != null) {
        ConfirmDialog(
            title = "Delete transaction?",
            body = "This permanently removes it from its ledger. This cannot be undone.",
            confirmLabel = "Delete",
            onConfirm = { vm.deleteTx(editingId); askDelete = false },
            onDismiss = { askDelete = false }
        )
    }
}

/**
 * Embedded calculator modal (FR-TX-08).
 * - Pre-fills the existing valid positive amount as starting expression.
 * - Supports + - × ÷, parentheses, % (x% = x/100).
 * - Result shown live, formatted to the active currency's decimal precision.
 * - "Transfer" disabled on ≤ 0 results or evaluation errors.
 */
@Composable
fun CalculatorDialog(initialAmount: String?, currency: String, onTransfer: (Double) -> Unit, onDismiss: () -> Unit) {
    var expr by remember {
        val init = initialAmount?.let { parseAmountInput(it) }
        mutableStateOf(if (init != null) Currencies.plain(init) else "")
    }
    val result = remember(expr) { if (expr.isBlank()) null else CalcEval.evaluate(expr) }
    val rounded = result?.let { Currencies.roundTo(it, currency) }
    val transferEnabled = rounded != null && rounded > 0
    // Unfinished (not erroneous): ends with an operator / open paren, or has unbalanced parens.
    val incomplete = remember(expr) {
        val t = expr.trim()
        if (t.isEmpty()) true else {
            val last = t.last()
            val endsOperator = last == '+' || last == '-' || last == '*' || last == '/' ||
                last == '×' || last == '÷' || last == '('
            var depth = 0
            t.forEach { c -> if (c == '(') depth++ else if (c == ')') depth-- }
            endsOperator || depth > 0
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(28.dp), color = C.DialogBg) {
            Column(Modifier.padding(20.dp)) {
                // Display
                Column(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                    Text(expr.ifBlank { "0" }, color = C.Fg, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    val display = when {
                        expr.isBlank() -> ""
                        rounded != null -> Currencies.format(rounded, currency)
                        incomplete -> ""
                        else -> "Error"
                    }
                    Text(display, color = if (transferEnabled) C.Income else C.ExpenseSoft, fontSize = 14.sp)
                }
                // Keypad
                val rows = listOf(
                    listOf("AC" to "ac", "⌫" to "back", "(" to "(", ")" to ")"),
                    listOf("7" to "7", "8" to "8", "9" to "9", "÷" to "÷"),
                    listOf("4" to "4", "5" to "5", "6" to "6", "×" to "×"),
                    listOf("1" to "1", "2" to "2", "3" to "3", "-" to "-"),
                    listOf("0" to "0", "." to ".", "%" to "%", "+" to "+")
                )
                rows.forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { (label, _) ->
                            Box(
                                Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                                    .background(C.S2)
                                    .clickable {
                                        when (label) {
                                            "AC" -> expr = ""
                                            "⌫" -> expr = expr.dropLast(1)
                                            else -> expr += label
                                        }
                                    }
                                    .padding(vertical = 14.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(label, color = C.Fg, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                // "=" commits the current (intermediate) result into the expression,
                // so the calculation can be continued from there.
                Row(Modifier.fillMaxWidth()) {
                    Box(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .background(C.S2)
                            .clickable {
                                val r = rounded
                                if (r != null) expr = Currencies.plain(r)
                            }
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("=", color = if (rounded != null) C.Pri else C.Mut,
                            fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppButton("Cancel", onDismiss, Modifier.weight(1f), filled = false)
                    AppButton("Transfer", { onTransfer(rounded!!) }, Modifier.weight(1f), enabled = transferEnabled)
                }
            }
        }
    }
}
