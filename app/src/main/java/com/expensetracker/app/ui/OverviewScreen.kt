@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.expensetracker.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.DonutSmall
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.expensetracker.app.data.HOME_LEDGER
import com.expensetracker.app.data.Tx
import com.expensetracker.app.util.Currencies
import com.expensetracker.app.util.Dates

@Composable
fun OverviewScreen(vm: AppViewModel) {
    val d = vm.data.collectAsState().value
    val ui = vm.ui.collectAsState().value
    val ledgerId = ui.ledgerId
    val currency = ledgerCurrency(d, ledgerId)
    val month = ui.month
    val monthKey = month.toString()
    val monthTx = d.txs.filter { it.ledgerId == ledgerId && it.date.startsWith(monthKey) }
    val income = monthTx.filter { it.direction == "income" }.sumOf { it.amount }
    val expenses = monthTx.filter { it.direction == "expense" }.sumOf { it.amount }
    val net = income - expenses
    val last4 = monthTx.sortedWith(compareByDescending<Tx> { it.date }.thenByDescending { it.id }).take(4)

    var showLedger by remember { mutableStateOf(false) }
    var showMonthPicker by remember { mutableStateOf(false) }
    var dragAccum by remember { mutableFloatStateOf(0f) }

    // FR-OV-01: horizontal swipe changes month
    Box(Modifier.fillMaxSize().pointerInput(Unit) {
        detectHorizontalDragGestures(
            onDragEnd = {
                if (dragAccum <= -80f) vm.monthShift(-1)
                else if (dragAccum >= 80f) vm.monthShift(1)
                dragAccum = 0f
            },
            onDragCancel = { dragAccum = 0f }
        ) { _, delta -> dragAccum += delta }
    }) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Row(Modifier.padding(start = 16.dp, top = 10.dp)) {
                LedgerChipButton(ledgerLabel(d, ledgerId), currency, ledgerId == HOME_LEDGER) { showLedger = true }
            }
            if (ledgerId != HOME_LEDGER) TripNote()
            MonthNav(month, { vm.monthShift(-1) }, { vm.monthShift(1) }, { showMonthPicker = true })
            TotalsRow(income, expenses, net, currency)
            SectionTitle("Last transactions") {
                TextButton(onClick = { vm.setTab(Tabs.TRANSACTIONS) }) {
                    Text("All →", color = C.Pri, fontSize = 12.sp)
                }
            }
            if (last4.isEmpty()) {
                EmptyState(Icons.AutoMirrored.Rounded.ReceiptLong, "No transactions in ${month.format(Dates.MONTH_YEAR)}.")
            } else {
                last4.forEach { t ->
                    MiniRow(
                        date = Dates.fmtDayShort(t.date),
                        note = t.note,
                        amount = Currencies.format(t.amount, currency),
                        positive = t.direction == "income"
                    ) { vm.openTxForm(t.id) }
                }
            }
            DonutSection(vm, d, monthTx, currency)
            Spacer(Modifier.height(96.dp))
        }
    }

    if (showLedger) LedgerSheet(vm, ui.ledgerId, { vm.setLedger(it); showLedger = false }, { showLedger = false })
    if (showMonthPicker) {
        MonthPickerDialog(
            current = month,
            monthsWithData = d.txs.filter { it.ledgerId == ledgerId }.map { it.date.substring(0, 7) }.toSet(),
            onPick = { vm.setMonth(it); showMonthPicker = false },
            onDismiss = { showMonthPicker = false }
        )
    }
}

@Composable
fun DonutSection(vm: AppViewModel, d: Data, monthTx: List<Tx>, currency: String) {
    SectionTitle("Expenses by category")
    val expList = monthTx.filter { it.direction == "expense" }
    if (expList.isEmpty()) {
        EmptyState(Icons.Rounded.DonutSmall, "No expenses in this month — nothing to break down.")
        return
    }
    val byCat = expList.groupBy { it.categoryId }
        .map { (k, v) -> k to v.sumOf { it.amount } }
        .sortedByDescending { it.second }
    val total = byCat.sumOf { it.second }
    val pcts = largestRemainderPcts(byCat.map { it.second })
    Surface(shape = RoundedCornerShape(20.dp), color = C.S2, modifier = Modifier.padding(horizontal = 16.dp)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(126.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(126.dp)) {
                    val stroke = 22.dp.toPx()
                    val diameter = size.minDimension - stroke
                    val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
                    var start = -90f
                    byCat.forEach { (catId, v) ->
                        val sweep = (v / total).toFloat() * 360f
                        drawArc(
                            color = catOf(d, catId)?.let { parseColorHex(it.color) } ?: C.Faint,
                            startAngle = start,
                            sweepAngle = sweep,
                            useCenter = false,
                            topLeft = topLeft,
                            size = Size(diameter, diameter),
                            style = Stroke(width = stroke, cap = StrokeCap.Butt)
                        )
                        start += sweep
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(Currencies.short(total, currency), color = C.Fg, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                    Text("EXPENSES", color = C.Faint, fontSize = 7.5.sp, letterSpacing = 1.2.sp)
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                byCat.forEachIndexed { i, (catId, _) ->
                    val cat = catOf(d, catId)
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                            .clickable { vm.legendTap(catId) }
                            .padding(horizontal = 6.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(Modifier.size(9.dp).clip(RoundedCornerShape(3.dp))
                            .background(if (cat == null) C.Faint else parseColorHex(cat.color)))
                        Text(cat?.name ?: "Uncategorized", color = C.Fg, fontSize = 12.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Text("${pcts[i]}%", color = C.Mut, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
