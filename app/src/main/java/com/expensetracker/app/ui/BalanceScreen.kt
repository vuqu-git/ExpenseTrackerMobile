@file:OptIn(ExperimentalMaterial3Api::class)

package com.expensetracker.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.expensetracker.app.data.HOME_LEDGER
import com.expensetracker.app.util.Currencies
import com.expensetracker.app.util.Dates

data class BalanceRowData(
    val ym: String, val income: Double, val expenses: Double,
    val net: Double, val cumulative: Double
)

@Composable
fun BalanceScreen(vm: AppViewModel) {
    val d = vm.data.collectAsState().value
    val homeTx = d.txs.filter { it.ledgerId == HOME_LEDGER }   // trips never appear here (FR-BA-01)
    val byMonth = HashMap<String, DoubleArray>()
    homeTx.forEach { t ->
        val ym = t.date.substring(0, 7)
        val arr = byMonth.getOrPut(ym) { DoubleArray(2) }
        if (t.direction == "income") arr[0] += t.amount else arr[1] += t.amount
    }
    var cum = 0.0
    val rows = byMonth.keys.sorted().map { ym ->
        val a = byMonth[ym]!!
        val net = a[0] - a[1]
        cum += net
        BalanceRowData(ym, a[0], a[1], net, cum)
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text("Balance", color = C.Fg, fontSize = 27.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 2.dp))
        if (rows.isEmpty()) {
            EmptyState(Icons.Rounded.AccountBalance, "No home transactions yet.\nTrip ledgers never affect this balance.")
        } else {
            val overall = rows.last().cumulative
            val cur = d.homeCurrency
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text("OVERALL BALANCE", color = C.Faint, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.9.sp)
                Text(Currencies.formatSigned(overall, cur), color = if (overall < 0) C.Expense else C.Income,
                    fontSize = 34.sp, fontWeight = FontWeight.Bold)
                Text("across ${rows.size} month${if (rows.size > 1) "s" else ""} · home ledger only — trips excluded",
                    color = C.Mut, fontSize = 11.5.sp)
            }
            Spacer(Modifier.height(14.dp))
            Surface(shape = RoundedCornerShape(16.dp), color = C.S2, modifier = Modifier.padding(horizontal = 16.dp)) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                    Row(Modifier.fillMaxWidth().padding(top = 9.dp, bottom = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("MONTH", color = C.Faint, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text("IN", color = C.Faint, fontSize = 9.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
                        Text("OUT", color = C.Faint, fontSize = 9.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
                        Text("NET", color = C.Faint, fontSize = 9.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
                        Text("BALANCE", color = C.Faint, fontSize = 9.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, modifier = Modifier.weight(1.1f))
                    }
                    rows.reversed().forEach { r -> BalanceRowItem(r, cur) }
                }
            }
            Spacer(Modifier.height(96.dp))
        }
    }
}

@Composable
fun BalanceRowItem(r: BalanceRowData, currency: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(Dates.fmtMonthShort(r.ym), color = C.Fg, fontSize = 11.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        Text(Currencies.format(r.income, currency), color = C.Income, fontSize = 10.5.sp, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
        Text(Currencies.format(r.expenses, currency), color = C.Expense, fontSize = 10.5.sp, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
        Text(Currencies.formatSigned(r.net, currency), color = if (r.net < 0) C.Expense else C.Income, fontSize = 10.5.sp, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
        Text(Currencies.formatSigned(r.cumulative, currency), color = if (r.cumulative < 0) C.Expense else C.Income, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, modifier = Modifier.weight(1.1f))
    }
}
