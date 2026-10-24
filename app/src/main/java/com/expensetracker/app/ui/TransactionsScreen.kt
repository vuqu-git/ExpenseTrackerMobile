@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.expensetracker.app.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Label
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.expensetracker.app.data.HOME_LEDGER
import com.expensetracker.app.data.Tx
import com.expensetracker.app.util.Currencies
import com.expensetracker.app.util.Dates

@Composable
fun TransactionsScreen(vm: AppViewModel) {
    val d = vm.data.collectAsState().value
    val ui = vm.ui.collectAsState().value
    val filtered = filteredTx(d, ui)
    var showLedger by remember { mutableStateOf(false) }
    var showCatSheet by remember { mutableStateOf(false) }
    var showMonthPicker by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<Tx?>(null) }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(start = 16.dp, top = 10.dp)) {
            LedgerChipButton(ledgerLabel(d, ui.ledgerId), ledgerCurrency(d, ui.ledgerId),
                ui.ledgerId == HOME_LEDGER) { showLedger = true }
        }
        if (ui.ledgerId != HOME_LEDGER) TripNote()
        Seg(
            options = listOf(true to "Month", false to "All"),
            value = ui.monthMode,
            onSelect = { vm.setMode(it) },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        if (ui.monthMode) {
            MonthNav(ui.month, { vm.monthShift(-1) }, { vm.monthShift(1) }, { showMonthPicker = true })
        }
        SearchBarField(ui.search, { vm.setSearch(it) }, { vm.clearFilters() })
        FilterChipsRow(vm, d, ui, { showCatSheet = true })

        val hasFilter = ui.search.isNotBlank() || ui.dirFilter != null || ui.catFilter != null || ui.uncatFilter
        if (filtered.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                EmptyState(
                    if (hasFilter) Icons.Rounded.SearchOff else Icons.AutoMirrored.Rounded.ReceiptLong,
                    if (hasFilter) "No transactions match your filters." else "No transactions yet. Tap + to add one."
                )
            }
        } else {
            LazyColumn(Modifier.weight(1f)) {
                val groups: Map<String, List<Tx>> = if (ui.monthMode)
                    filtered.groupBy { it.date }.toSortedMap(compareByDescending { it })
                else
                    filtered.groupBy { it.date.substring(0, 7) }.toSortedMap(compareByDescending { it })
                groups.forEach { (key, list) ->
                    stickyHeader(key = key) {
                        GroupHeader(if (ui.monthMode) Dates.fmtDayLong(key) else Dates.fmtMonth(key))
                    }
                    items(list, key = { it.id }) { t ->
                        TxRow(
                            tx = t,
                            cat = catOf(d, t.categoryId),
                            currency = txCurrency(d, t),
                            showDate = !ui.monthMode,
                            onClick = { vm.openTxForm(t.id) },
                            onLongClick = { deleteTarget = t }
                        )
                    }
                }
                item { Spacer(Modifier.height(96.dp)) }
            }
        }
    }

    if (showLedger) LedgerSheet(vm, ui.ledgerId, { vm.setLedger(it); showLedger = false }, { showLedger = false })
    if (showMonthPicker) {
        MonthPickerDialog(
            current = ui.month,
            monthsWithData = d.txs.filter { it.ledgerId == ui.ledgerId }.map { it.date.substring(0, 7) }.toSet(),
            onPick = { vm.setMonth(it); showMonthPicker = false },
            onDismiss = { showMonthPicker = false }
        )
    }
    if (showCatSheet) CategoryFilterSheet(vm, d, ui) { showCatSheet = false }
    if (deleteTarget != null) {
        val t = deleteTarget!!
        ConfirmDialog(
            title = "Delete transaction?",
            body = "“${t.note ?: "No note"}” · ${Currencies.format(t.amount, txCurrency(d, t))} on ${t.date} will be permanently deleted.",
            confirmLabel = "Delete",
            onConfirm = { vm.deleteTx(t.id); deleteTarget = null },
            onDismiss = { deleteTarget = null }
        )
    }
}

@Composable
fun FilterChipsRow(vm: AppViewModel, d: Data, ui: UiState, onCat: () -> Unit) {
    val catLabel = when {
        ui.uncatFilter -> "Uncategorized"
        ui.catFilter != null -> (catOf(d, ui.catFilter)?.name ?: "Category")
        else -> "Category"
    }
    val dirLabel = when (ui.dirFilter) { "expense" -> "Expenses"; "income" -> "Income"; else -> "Direction" }
    val anyActive = ui.dirFilter != null || ui.catFilter != null || ui.uncatFilter
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChipBtn(icon = Icons.Rounded.FilterList, label = dirLabel, active = ui.dirFilter != null) { vm.cycleDir() }
        FilterChipBtn(icon = Icons.AutoMirrored.Rounded.Label, label = catLabel,
            active = ui.catFilter != null || ui.uncatFilter, onClick = onCat)
        if (anyActive || ui.search.isNotBlank()) {
            FilterChipBtn(icon = Icons.Rounded.Close, label = "Clear", active = false, ghost = true) { vm.clearFilters() }
        }
    }
}

@Composable
fun CategoryFilterSheet(vm: AppViewModel, d: Data, ui: UiState, dismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = dismiss, containerColor = C.SheetBg) {
        Column {
            Text("Filter by category", color = C.Fg, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            LabelRow(title = "All categories", icon = Icons.Rounded.Close) {
                vm.clearCatFilter(); dismiss()
            }
            d.categories.filter { it.ledgerId == ui.ledgerId }.forEach { c ->
                LabelRow(title = c.name, dotColor = parseColorHex(c.color), selected = ui.catFilter == c.id) {
                    vm.setCatFilter(c.id); dismiss()
                }
            }
            LabelRow(title = "Uncategorized", dotColor = C.Faint, selected = ui.uncatFilter) {
                vm.setUncatFilter(); dismiss()
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
