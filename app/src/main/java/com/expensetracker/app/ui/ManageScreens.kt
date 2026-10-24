@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.expensetracker.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.expensetracker.app.data.HOME_LEDGER
import com.expensetracker.app.data.Template
import com.expensetracker.app.data.Tx
import com.expensetracker.app.util.Currencies
import com.expensetracker.app.util.Dates
import com.expensetracker.app.util.FileNames
import java.time.LocalDate

// ============ shared sheets ============

@Composable
fun LedgerSheet(vm: AppViewModel, currentId: Long, onPick: (Long) -> Unit, onDismiss: () -> Unit, forForm: Boolean = false) {
    val d = vm.data.collectAsState().value
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = C.SheetBg) {
        Column {
            Text("Choose ledger", color = C.Fg, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            // Home is always selectable (FR-TR-05)
            LabelRow(
                title = "Home", sub = "${d.homeCurrency} · everyday book",
                icon = Icons.Rounded.Home, selected = currentId == HOME_LEDGER
            ) { onPick(HOME_LEDGER) }
            d.trips.forEach { t ->
                if (forForm && t.status != "open" && t.id != currentId) return@forEach
                LabelRow(
                    title = t.name,
                    sub = "${t.currency} · ${t.startDate ?: "…"} – ${t.endDate ?: "…"}",
                    icon = Icons.Rounded.FlightTakeoff,
                    selected = currentId == t.id
                ) { onPick(t.id) }
            }
            Row(Modifier.fillMaxWidth().clickable { onDismiss(); vm.push(Screen.ManageTrips) }
                .padding(vertical = 14.dp),
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Settings, null, tint = C.Pri, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Manage trips", color = C.Pri, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
fun CategoryPickerSheet(d: Data, ledgerId: Long, selected: Long?, onPick: (Long) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = C.SheetBg) {
        Column {
            Text("Select category", color = C.Fg, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            val cats = d.categories.filter { it.ledgerId == ledgerId }
            if (cats.isEmpty()) {
                EmptyState(Icons.Rounded.Category, "No categories defined — create one in Settings → Categories.")
            } else {
                cats.forEach { c ->
                    LabelRow(title = c.name, dotColor = parseColorHex(c.color), selected = selected == c.id) {
                        onPick(c.id)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
fun TemplatePickerSheet(d: Data, ledgerId: Long, onPick: (Long) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = C.SheetBg) {
        Column {
            Text("Use template", color = C.Fg, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            val tpls = d.templates.filter { it.ledgerId == ledgerId }
            if (tpls.isEmpty()) {
                EmptyState(Icons.Rounded.Bolt, "No templates defined — create them in Settings → Templates.")
            } else {
                tpls.forEach { t ->
                    Row(Modifier.fillMaxWidth().clickable { onPick(t.id) }.padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Icon(Icons.Rounded.Bolt, null, tint = C.Mut, modifier = Modifier.size(22.dp))
                        Column(Modifier.weight(1f)) {
                            Text(t.name, color = C.Fg, fontSize = 14.5.sp, fontWeight = FontWeight.Medium)
                            Text(templateSummary(d, t), color = C.Mut, fontSize = 11.5.sp)
                        }
                    }
                }
            }
            HintCard("Templates only pre-fill the form — they never create transactions, and the date is never touched.")
            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun templateSummary(d: Data, t: Template): String {
    val p = mutableListOf<String>()
    t.direction?.let { p.add(it) }
    t.amount?.let { p.add(Currencies.plain(it)) }
    t.categoryId?.let { id -> catOf(d, id)?.name?.let { p.add(it) } }
    t.note?.let { p.add("“$it”") }
    return if (p.isEmpty()) "No prefills" else p.joinToString(" · ")
}

// ============ categories ============

@Composable
fun ManageCategoriesScreen(vm: AppViewModel) {
    val d = vm.data.collectAsState().value
    var ledgerId by remember { mutableStateOf(HOME_LEDGER) }
    var showLedger by remember { mutableStateOf(false) }
    val cats = d.categories.filter { it.ledgerId == ledgerId }

    Column(Modifier.fillMaxSize()) {
        SubBar("Categories", { vm.pop() }) {
            TextButton(onClick = { vm.openCategoryForm(null, ledgerId) }) {
                Text("Add", color = C.Pri, fontWeight = FontWeight.Medium)
            }
        }
        Row(Modifier.padding(start = 16.dp, top = 4.dp)) {
            LedgerChipButton(ledgerLabel(d, ledgerId), ledgerCurrency(d, ledgerId), ledgerId == HOME_LEDGER) {
                showLedger = true
            }
        }
        if (cats.isEmpty()) {
            EmptyState(Icons.Rounded.Category, "No categories yet.\nTap Add to create one.")
        } else {
            LazyColumn(Modifier.weight(1f)) {
                items(cats, key = { it.id }) { c ->
                    CardRow(onClick = { vm.openCategoryForm(c.id, ledgerId) }) {
                        Box(Modifier.size(14.dp).clip(RoundedCornerShape(5.dp)).background(parseColorHex(c.color)))
                        Column(Modifier.weight(1f)) {
                            Text(c.name, color = C.Fg, fontSize = 14.5.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                            val n = d.txs.count { it.categoryId == c.id }
                            Text("$n transaction${if (n == 1) "" else "s"}", color = C.Mut, fontSize = 11.5.sp)
                        }
                        Icon(Icons.Rounded.ChevronRight, null, tint = C.Faint, modifier = Modifier.size(18.dp))
                    }
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }
    if (showLedger) LedgerSheet(vm, ledgerId, { ledgerId = it; showLedger = false }, { showLedger = false })
}

@Composable
fun CategoryFormScreen(vm: AppViewModel, editingId: Long?) {
    val d = vm.data.collectAsState().value
    val f = vm.catForm.collectAsState().value
    var askDelete by remember { mutableStateOf(false) }
    val palette = listOf("#F2A2FF", "#FF9EC4", "#FF8A80", "#FFAB8A", "#FFC27A", "#E8E27A", "#B6F27A",
        "#7CDE8B", "#63DFC0", "#7DD8FF", "#8FC7FF", "#A9B8FF", "#C9B9FF", "#E6CCFF")

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SubBar(if (editingId == null) "New category" else "Edit category", { vm.pop() }) {
            if (editingId != null) IconButtonCircle(Icons.Rounded.Delete, tint = C.ExpenseSoft) { askDelete = true }
        }
        FieldLabel("Name *")
        Field(value = f.name,
            onValueChange = { v -> vm.updateCatForm { it.copy(name = v) } },
            placeholder = "e.g. Groceries", error = f.errName || f.errDuplicate,
            modifier = Modifier.padding(horizontal = 16.dp))
        if (f.errName) FieldError("Name is mandatory.")
        if (f.errDuplicate) FieldError("Name must be unique (case-insensitive).")

        FieldLabel("Color *")
        Column(Modifier.padding(horizontal = 16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                palette.take(7).forEach { c ->
                    Box(Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(12.dp))
                        .background(parseColorHex(c))
                        .clickable { vm.updateCatForm { it.copy(color = c, errColor = false) } }
                        .then(if (f.color == c) Modifier.border(2.dp, Color.Black, RoundedCornerShape(12.dp))
                            .border(4.dp, C.Fg, RoundedCornerShape(12.dp)) else Modifier))
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                palette.drop(7).forEach { c ->
                    Box(Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(12.dp))
                        .background(parseColorHex(c))
                        .clickable { vm.updateCatForm { it.copy(color = c, errColor = false) } }
                        .then(if (f.color == c) Modifier.border(2.dp, Color.Black, RoundedCornerShape(12.dp))
                            .border(4.dp, C.Fg, RoundedCornerShape(12.dp)) else Modifier))
                }
            }
            Spacer(Modifier.height(10.dp))
            Text("Preview:", color = C.Faint, fontSize = 11.5.sp)
            Spacer(Modifier.height(6.dp))
            if (f.color != null && f.name.isNotBlank()) {
                CatChip(com.expensetracker.app.data.Category(id = 0, ledgerId = f.ledgerId, name = f.name, color = f.color))
            } else {
                Text("— pick a color", color = C.Faint, fontSize = 11.5.sp)
            }
        }
        if (f.errColor) FieldError("Pick a color.")

        if (editingId != null) {
            val n = d.txs.count { it.categoryId == editingId }
            val m = d.templates.count { it.categoryId == editingId }
            HintCard("Used by $n transaction${if (n == 1) "" else "s"} and $m template${if (m == 1) "" else "s"}. " +
                "Deleting re-labels them “Uncategorized” — nothing is removed.")
        }

        Spacer(Modifier.height(16.dp))
        AppButton("Save category", { vm.saveCategoryForm() },
            Modifier.padding(horizontal = 16.dp).fillMaxWidth())
        Spacer(Modifier.height(96.dp))
    }

    if (askDelete && editingId != null) {
        val n = d.txs.count { it.categoryId == editingId }
        val m = d.templates.count { it.categoryId == editingId }
        val body = if (n + m > 0)
            "$n transaction${if (n == 1) "" else "s"} and $m template${if (m == 1) "" else "s"} will become uncategorized. Their data is not modified or deleted."
        else "No transactions or templates use it. It will be removed silently."
        ConfirmDialog(
            title = "Delete “${d.categories.firstOrNull { it.id == editingId }?.name ?: ""}”?",
            body = body,
            confirmLabel = "Delete",
            onConfirm = { vm.deleteCategory(editingId); askDelete = false },
            onDismiss = { askDelete = false }
        )
    }
}

// ============ templates ============

@Composable
fun ManageTemplatesScreen(vm: AppViewModel) {
    val d = vm.data.collectAsState().value
    var ledgerId by remember { mutableStateOf(HOME_LEDGER) }
    var showLedger by remember { mutableStateOf(false) }
    val tpls = d.templates.filter { it.ledgerId == ledgerId }

    Column(Modifier.fillMaxSize()) {
        SubBar("Templates", { vm.pop() }) {
            TextButton(onClick = { vm.openTemplateForm(null, ledgerId) }) {
                Text("Add", color = C.Pri, fontWeight = FontWeight.Medium)
            }
        }
        Row(Modifier.padding(start = 16.dp, top = 4.dp)) {
            LedgerChipButton(ledgerLabel(d, ledgerId), ledgerCurrency(d, ledgerId), ledgerId == HOME_LEDGER) {
                showLedger = true
            }
        }
        if (tpls.isEmpty()) {
            EmptyState(Icons.Rounded.Bolt, "No templates yet.\nCreate one to pre-fill the transaction form.")
        } else {
            LazyColumn(Modifier.weight(1f)) {
                items(tpls, key = { it.id }) { t ->
                    CardRow(onClick = { vm.openTemplateForm(t.id, ledgerId) }) {
                        IconBox(Icons.Rounded.Bolt)
                        Column(Modifier.weight(1f)) {
                            Text(t.name, color = C.Fg, fontSize = 14.5.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                            Text(templateSummary(d, t), color = C.Mut, fontSize = 11.5.sp,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Icon(Icons.Rounded.ChevronRight, null, tint = C.Faint, modifier = Modifier.size(18.dp))
                    }
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }
    if (showLedger) LedgerSheet(vm, ledgerId, { ledgerId = it; showLedger = false }, { showLedger = false })
}

@Composable
fun TemplateFormScreen(vm: AppViewModel, editingId: Long?) {
    val d = vm.data.collectAsState().value
    val f = vm.tplForm.collectAsState().value
    var askDelete by remember { mutableStateOf(false) }
    var showCat by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SubBar(if (editingId == null) "New template" else "Edit template", { vm.pop() }) {
            if (editingId != null) IconButtonCircle(Icons.Rounded.Delete, tint = C.ExpenseSoft) { askDelete = true }
        }
        FieldLabel("Name *")
        Field(value = f.name,
            onValueChange = { v -> vm.updateTplForm { it.copy(name = v) } },
            placeholder = "e.g. Weekly groceries", error = f.errName,
            modifier = Modifier.padding(horizontal = 16.dp))
        if (f.errName && f.name.isBlank()) FieldError("Name is mandatory.")
        if (f.errDuplicate) FieldError("Name must be unique.")

        FieldLabel("Direction prefill")
        Seg(listOf(null as String? to "Any", "expense" to "Expense", "income" to "Income"), f.direction,
            { v -> vm.updateTplForm { it.copy(direction = v) } },
            Modifier.padding(horizontal = 16.dp))

        FieldLabel("Amount prefill (plain number)")
        Field(value = f.amount,
            onValueChange = { v -> vm.updateTplForm { it.copy(amount = v) } },
            placeholder = "Empty = no prefill", decimal = true, error = f.errAmount,
            modifier = Modifier.padding(horizontal = 16.dp))
        if (f.errAmount) FieldError("Amount must be a positive number (or empty).")

        FieldLabel("Category prefill")
        FieldShell(onClick = { showCat = true }) {
            if (f.categoryId != null) {
                CatChip(catOf(d, f.categoryId))
            } else {
                Text("No category prefill", color = C.Faint, fontSize = 14.5.sp, modifier = Modifier.weight(1f))
            }
            Icon(Icons.Rounded.KeyboardArrowDown, null, tint = C.Faint, modifier = Modifier.size(20.dp))
        }

        FieldLabel("Note prefill")
        Field(value = f.note,
            onValueChange = { v -> vm.updateTplForm { it.copy(note = v) } },
            placeholder = "Empty = no prefill",
            modifier = Modifier.padding(horizontal = 16.dp))

        Spacer(Modifier.height(16.dp))
        AppButton("Save template", { vm.saveTemplateForm() },
            Modifier.padding(horizontal = 16.dp).fillMaxWidth())
        Spacer(Modifier.height(96.dp))
    }

    if (showCat) CategoryPickerSheet(d, f.ledgerId, f.categoryId, { id ->
        vm.updateTplForm { it.copy(categoryId = id) }; showCat = false
    }, { showCat = false })
    if (askDelete && editingId != null) {
        ConfirmDialog(
            title = "Delete template?",
            body = "Existing transactions are not affected — templates only pre-fill the form.",
            confirmLabel = "Delete",
            onConfirm = { vm.deleteTemplate(editingId); askDelete = false },
            onDismiss = { askDelete = false }
        )
    }
}

// ============ trips ============

@Composable
fun ManageTripsScreen(vm: AppViewModel) {
    val d = vm.data.collectAsState().value
    Column(Modifier.fillMaxSize()) {
        SubBar("Trips", { vm.pop() }) {
            TextButton(onClick = { vm.openTripForm(null) }) {
                Text("Add", color = C.Pri, fontWeight = FontWeight.Medium)
            }
        }
        if (d.trips.isEmpty()) {
            EmptyState(Icons.Rounded.FlightTakeoff, "No trips yet.\nCreate one for your next travel.")
        } else {
            LazyColumn(Modifier.weight(1f)) {
                items(d.trips, key = { it.id }) { t ->
                    val n = d.txs.count { it.ledgerId == t.id }
                    CardRow(onClick = { vm.push(Screen.TripView(t.id)) }) {
                        Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(C.S3),
                            contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.FlightTakeoff, null, tint = C.Mut, modifier = Modifier.size(19.dp))
                        }
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(t.name, color = C.Fg, fontSize = 14.5.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                                StatusChip(t.status == "open")
                            }
                            Text("${t.currency} · ${t.startDate ?: "…"} – ${t.endDate ?: "…"} · $n transaction${if (n == 1) "" else "s"}",
                                color = C.Mut, fontSize = 11.5.sp)
                        }
                        IconButtonCircle(Icons.Rounded.Edit) { vm.openTripForm(t.id) }
                    }
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }
}

@Composable
fun TripFormScreen(vm: AppViewModel, editingId: Long?) {
    val d = vm.data.collectAsState().value
    val f = vm.tripForm.collectAsState().value
    var askDelete by remember { mutableStateOf(false) }
    var showStart by remember { mutableStateOf(false) }
    var showEnd by remember { mutableStateOf(false) }
    val hasTx = editingId != null && d.txs.any { it.ledgerId == editingId }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SubBar(if (editingId == null) "New trip" else "Edit trip", { vm.pop() }) {
            if (editingId != null) IconButtonCircle(Icons.Rounded.Delete, tint = C.ExpenseSoft) { askDelete = true }
        }
        FieldLabel("Name *")
        Field(value = f.name,
            onValueChange = { v -> vm.updateTripForm { it.copy(name = v) } },
            placeholder = "e.g. Vietnam 2025", error = f.errName || f.errDuplicate,
            modifier = Modifier.padding(horizontal = 16.dp))
        if (f.errName) FieldError("Name is mandatory.")
        if (f.errDuplicate) FieldError("Name must be unique.")

        FieldLabel("Currency *")
        FieldShell(error = f.errCurrency,
            onClick = { if (!hasTx) vm.openCurrencyPicker(AppViewModel.CurrencyCtx.TRIP_FORM) }) {
            if (f.currency != null) {
                Text(f.currency, color = C.Fg, fontSize = 14.5.sp, fontWeight = FontWeight.Bold)
                Text(" · ${Currencies.display(f.currency)}", color = C.Mut, fontSize = 14.5.sp)
            } else {
                Text("Select currency", color = C.Faint, fontSize = 14.5.sp)
            }
            Spacer(Modifier.weight(1f))
            if (hasTx) Icon(Icons.Rounded.Lock, null, tint = C.Faint, modifier = Modifier.size(18.dp))
            else Icon(Icons.Rounded.KeyboardArrowDown, null, tint = C.Faint, modifier = Modifier.size(20.dp))
        }
        if (f.errCurrency) FieldError("Pick a currency from the ISO list.")
        if (hasTx) LockNote("Currency locked — this trip already has transactions (amounts would silently relabel).")

        FieldLabel("Dates (optional)")
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            FieldShell(onClick = { showStart = true }, modifier = Modifier.weight(1f)) {
                Text(f.start ?: "Start", color = if (f.start == null) C.Faint else C.Fg, fontSize = 14.5.sp)
            }
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = C.Faint, modifier = Modifier.size(16.dp))
            FieldShell(onClick = { showEnd = true }, modifier = Modifier.weight(1f)) {
                Text(f.end ?: "End", color = if (f.end == null) C.Faint else C.Fg, fontSize = 14.5.sp)
            }
        }
        HintCard("Date range is informational — entries outside it are allowed.")

        Spacer(Modifier.height(16.dp))
        AppButton("Save trip", { vm.saveTripForm() },
            Modifier.padding(horizontal = 16.dp).fillMaxWidth())
        Spacer(Modifier.height(96.dp))
    }

    if (showStart) DatePickerDialogBox(
        initialIso = f.start ?: LocalDate.now().toString(),
        onPick = { iso -> vm.updateTripForm { it.copy(start = iso) } },
        onDismiss = { showStart = false })
    if (showEnd) DatePickerDialogBox(
        initialIso = f.end ?: LocalDate.now().toString(),
        onPick = { iso -> vm.updateTripForm { it.copy(end = iso) } },
        onDismiss = { showEnd = false })
    if (askDelete && editingId != null) {
        val n = d.txs.count { it.ledgerId == editingId }
        if (n > 0) {
            InfoDialog(
                title = "Can't delete “${d.trips.firstOrNull { it.id == editingId }?.name ?: ""}”",
                text = "This trip still has $n transaction${if (n == 1) "" else "s"}. " +
                    "Delete or move them first — a trip with data is never deleted silently.",
                onOk = { askDelete = false }
            )
        } else {
            ConfirmDialog(
                title = "Delete “${d.trips.firstOrNull { it.id == editingId }?.name ?: ""}”?",
                body = "The trip has no transactions and will be removed.",
                confirmLabel = "Delete",
                onConfirm = { vm.deleteTrip(editingId); askDelete = false },
                onDismiss = { askDelete = false }
            )
        }
    }
}

@Composable
fun TripViewScreen(vm: AppViewModel, tripId: Long) {
    val d = vm.data.collectAsState().value
    val trip = d.trips.firstOrNull { it.id == tripId }
    var search by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<Tx?>(null) }
    val ctx = LocalContext.current
    val tripExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            val text = vm.pendingExport
            if (text != null) { writeText(ctx, uri, text); vm.pendingExport = null }
        }
    }

    if (trip == null) {
        Column(Modifier.fillMaxSize()) {
            SubBar("Trip", { vm.pop() })
            EmptyState(Icons.Rounded.FlightTakeoff, "This trip no longer exists.")
        }
        return
    }
    val txs = d.txs.filter { it.ledgerId == tripId }
    val income = txs.filter { it.direction == "income" }.sumOf { it.amount }
    val expenses = txs.filter { it.direction == "expense" }.sumOf { it.amount }
    val net = income - expenses
    val filtered = if (search.isBlank()) txs
    else txs.filter { (it.note ?: "").lowercase().contains(search.lowercase()) }
    val grouped = filtered.groupBy { it.date }.toSortedMap(compareByDescending { it })

    Column(Modifier.fillMaxSize()) {
        SubBar(trip.name, { vm.pop() }) { StatusChip(trip.status == "open") }
        TotalsRow(income, expenses, net, trip.currency)
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TripActionButton(Icons.Rounded.Download, "Export CSV") {
                vm.pendingExport = vm.buildTripCsv(tripId)
                tripExportLauncher.launch(FileNames.trip(trip.name))
            }
            TripActionButton(Icons.Rounded.Edit, "Edit") { vm.openTripForm(tripId) }
            TripActionButton(if (trip.status == "open") Icons.Rounded.Lock else Icons.Rounded.LockOpen,
                if (trip.status == "open") "Close trip" else "Reopen") { vm.toggleTripStatus(tripId) }
        }
        SearchBarField(search, { search = it }, { search = "" })
        if (filtered.isEmpty()) {
            EmptyState(Icons.AutoMirrored.Rounded.ReceiptLong,
                if (search.isBlank()) "No transactions in this trip yet." else "No transactions match.")
        } else {
            LazyColumn(Modifier.weight(1f)) {
                grouped.forEach { (day, list) ->
                    stickyHeader(key = day) { GroupHeader(Dates.fmtDayLong(day)) }
                    items(list, key = { it.id }) { t ->
                        TxRow(t, catOf(d, t.categoryId), trip.currency, false,
                            { vm.openTxForm(t.id) }, { deleteTarget = t })
                    }
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }
    if (deleteTarget != null) {
        val t = deleteTarget!!
        ConfirmDialog(
            title = "Delete transaction?",
            body = "“${t.note ?: "No note"}” · ${Currencies.format(t.amount, trip.currency)} on ${t.date} will be permanently deleted.",
            confirmLabel = "Delete",
            onConfirm = { vm.deleteTx(t.id); deleteTarget = null },
            onDismiss = { deleteTarget = null }
        )
    }
}

@Composable
fun TripActionButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(C.PriC)
            .clickable(onClick = onClick).padding(horizontal = 13.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(icon, null, tint = C.OnPriC, modifier = Modifier.size(16.dp))
        Text(label, color = C.OnPriC, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

// ============ currency picker ============

@Composable
fun CurrencyPickerScreen(vm: AppViewModel) {
    var search by remember { mutableStateOf("") }
    val all = remember { com.expensetracker.app.util.Currencies.all }
    val filtered = all.filter {
        it.currencyCode.lowercase().contains(search.lowercase()) ||
            Currencies.display(it.currencyCode).lowercase().contains(search.lowercase())
    }

    Column(Modifier.fillMaxSize()) {
        SubBar("Select currency", { vm.pop() })
        SearchBarField(search, { search = it }, { search = "" })
        LazyColumn(Modifier.weight(1f)) {
            items(filtered, key = { it.currencyCode }) { cur ->
                Row(Modifier.fillMaxWidth().clickable { vm.pickCurrency(cur.currencyCode) }
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(cur.currencyCode, color = C.Fg, fontSize = 14.5.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(48.dp))
                    Text(Currencies.display(cur.currencyCode), color = C.Mut, fontSize = 13.sp,
                        modifier = Modifier.weight(1f))
                    Text(Currencies.symbol(cur.currencyCode), color = C.Mut, fontSize = 13.sp)
                }
            }
        }
    }
}
