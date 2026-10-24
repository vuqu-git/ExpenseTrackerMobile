@file:OptIn(ExperimentalMaterial3Api::class)

package com.expensetracker.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.expensetracker.app.util.FileNames
import com.expensetracker.app.util.ImportType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate

// ============ CSV export ============

@Composable
fun CsvExportScreen(vm: AppViewModel) {
    val d = vm.data.collectAsState().value
    val ctx = LocalContext.current
    var from by remember { mutableStateOf(vm.ui.value.month.atDay(1).toString()) }
    var to by remember { mutableStateOf(LocalDate.now().toString()) }
    var pickedTrip by remember { mutableStateOf<Long?>(null) }
    var showFrom by remember { mutableStateOf(false) }
    var showTo by remember { mutableStateOf(false) }

    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) {
            val text = vm.pendingExport
            if (text != null) { writeText(ctx, uri, text); vm.pendingExport = null }
        }
    }
    val save: (String, String) -> Unit = { name, content ->
        vm.pendingExport = content
        csvLauncher.launch(name)
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SubBar("Export CSV", { vm.pop() })

        // Home ledger — date range
        SectionTitle("Home ledger — date range")
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            DateBoxButton(from, Modifier.weight(1f)) { showFrom = true }
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = C.Faint, modifier = Modifier.size(16.dp))
            DateBoxButton(to, Modifier.weight(1f)) { showTo = true }
        }
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PresetChip("This month") { from = vm.ui.value.month.atDay(1).toString(); to = LocalDate.now().toString() }
            PresetChip("This year") { from = "${LocalDate.now().year}-01-01"; to = "${LocalDate.now().year}-12-31" }
            PresetChip("All time") {
                from = d.txs.filter { it.ledgerId == com.expensetracker.app.data.HOME_LEDGER }
                    .minOfOrNull { it.date } ?: LocalDate.now().toString()
                to = LocalDate.now().toString()
            }
        }
        AppButton("Export home range", {
            save(FileNames.homeRange(from, to), vm.buildHomeRangeCsv(from, to))
        }, Modifier.padding(horizontal = 16.dp).fillMaxWidth())

        // Single trip — complete
        if (d.trips.isNotEmpty()) {
            SectionTitle("Single trip — complete")
            d.trips.forEach { t ->
                Row(Modifier.fillMaxWidth().clickable { pickedTrip = t.id }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = pickedTrip == t.id, onClick = { pickedTrip = t.id })
                    Spacer(Modifier.width(8.dp))
                    Text(t.name, color = C.Fg, fontSize = 14.sp)
                    Text(" · ${t.currency}", color = C.Mut, fontSize = 12.sp)
                }
            }
            AppButton("Export selected trip", {
                pickedTrip?.let { id ->
                    val trip = d.trips.first { it.id == id }
                    save(FileNames.trip(trip.name), vm.buildTripCsv(id))
                }
            }, Modifier.padding(horizontal = 16.dp).fillMaxWidth(), enabled = pickedTrip != null)
        }

        // Lifetime — all ledgers
        SectionTitle("Lifetime — all ledgers")
        AppButton("Export all ledgers", {
            save(FileNames.allLedgers(), vm.buildAllLedgersCsv())
        }, Modifier.padding(horizontal = 16.dp).fillMaxWidth())

        // Entity CSVs
        SectionTitle("Categories / templates / trips")
        AppButton("Export categories CSV", {
            save(FileNames.categories(), vm.buildCategoriesCsv())
        }, Modifier.padding(horizontal = 16.dp).fillMaxWidth(), filled = false, tonal = true)
        AppButton("Export templates CSV", {
            save(FileNames.templates(), vm.buildTemplatesCsv())
        }, Modifier.padding(horizontal = 16.dp).fillMaxWidth(), filled = false, tonal = true)
        AppButton("Export trips CSV", {
            save(FileNames.tripsCsv(), vm.buildTripsCsv())
        }, Modifier.padding(horizontal = 16.dp).fillMaxWidth(), filled = false, tonal = true)

        HintCard("Format: semicolon-separated · decimal point · ISO dates · header row · " +
            "RFC 4180 quoting for fields containing ; \" or line breaks. " +
            "All export files can be re-imported losslessly on the same device.")
        Spacer(Modifier.height(96.dp))
    }

    if (showFrom) DatePickerDialogBox(
        initialIso = from, onPick = { from = it }, onDismiss = { showFrom = false })
    if (showTo) DatePickerDialogBox(
        initialIso = to, onPick = { to = it }, onDismiss = { showTo = false })
}

@Composable
fun DateBoxButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(modifier.clip(RoundedCornerShape(14.dp)).background(C.S2).clickable(onClick = onClick).padding(13.dp)) {
        Text(label, color = C.Fg, fontSize = 14.5.sp)
    }
}

@Composable
fun PresetChip(label: String, onClick: () -> Unit) {
    Box(Modifier.clip(RoundedCornerShape(10.dp)).border(1.dp, C.Outline, RoundedCornerShape(10.dp))
        .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 7.dp)) {
        Text(label, color = C.Fg, fontSize = 12.5.sp)
    }
}

// ============ CSV import ============

@Composable
fun CsvImportScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val imp = vm.import.collectAsState().value

    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val appCtx = ctx.applicationContext
            scope.launch(Dispatchers.IO) {
                val text = readText(appCtx, uri)
                if (text == null) vm.showError("Could not read the selected file.")
                else vm.parseImport(text, uri.lastPathSegment ?: "selected.csv")
            }
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SubBar("Import CSV", { vm.pop() })

        if (imp.type == null) {
            // Intro: import guidance, visible BEFORE type/file selection (FR-CS-05)
            HintCard("Full CSV re-import order: Trips → Categories → Transaction Templates → Transactions. " +
                "Import these files in this order so referenced items exist before later files are imported.")
            HintCard("Recommended for a complete backup or device migration: use Export Full Backup (JSON) and " +
                "Restore Database (JSON). JSON includes all ledgers, categories, transaction templates, " +
                "transactions, and settings in one file. It provides a single-file, one-click export/import " +
                "entry point and avoids the multi-step CSV workflow.")

            SectionTitle("Choose import type")
            ImportType.entries.forEach { t ->
                SettingsItem(
                    icon = when (t) {
                        ImportType.TRIPS -> Icons.Rounded.FlightTakeoff
                        ImportType.CATEGORIES -> Icons.Rounded.Category
                        ImportType.TEMPLATES -> Icons.Rounded.Bolt
                        ImportType.TRANSACTIONS -> Icons.AutoMirrored.Rounded.ReceiptLong
                    },
                    title = t.label,
                    sub = when (t) {
                        ImportType.TRIPS -> "Append-only · name;currency;start_date;end_date;status"
                        ImportType.CATEGORIES -> "Append-only · name;color;ledger"
                        ImportType.TEMPLATES -> "Append-only · name;category;note;amount;direction;ledger"
                        ImportType.TRANSACTIONS -> "Append or replace · date;amount;category;note;direction;ledger"
                    }
                ) { vm.startImport(t); fileLauncher.launch(arrayOf("*/*")) }
            }
            Spacer(Modifier.height(96.dp))
        } else {
            // Pre-check screen
            SectionTitle("Pre-check — ${imp.type.label}")
            Text(imp.fileName ?: "", color = C.Mut, fontSize = 11.5.sp,
                modifier = Modifier.padding(horizontal = 16.dp))

            val check = imp.check
            if (check == null) {
                Text("Parsing…", color = C.Mut, modifier = Modifier.padding(16.dp))
            } else if (check.fileError != null) {
                ErrorCard(check.fileError ?: "Unknown error")
                AppButton("Discard", { vm.resetImportUi(); vm.pop() },
                    Modifier.padding(horizontal = 16.dp).fillMaxWidth(), filled = false, tonal = true, danger = true)
            } else if (check.errors.isNotEmpty()) {
                ErrorCard("File rejected — ${check.errors.size} of ${check.errors.size + check.validCount} rows invalid. " +
                    "The whole file is refused; nothing was imported.")
                check.errors.take(10).forEach { e ->
                    Text("Row ${e.row} — ${e.reason}", color = C.ExpenseSoft, fontSize = 12.sp,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp))
                }
                if (check.errors.size > 10) {
                    Text("…and ${check.errors.size - 10} more.", color = C.Mut, fontSize = 11.5.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                }
                HintCard("Fix the file and try again. Categories must exist before transactions; " +
                    "trips must exist before categories that reference them.")
                AppButton("Discard", { vm.resetImportUi(); vm.pop() },
                    Modifier.padding(horizontal = 16.dp).fillMaxWidth(), filled = false, tonal = true, danger = true)
            } else if (imp.applied) {
                SuccessCard("${check.validCount} row${if (check.validCount == 1) "" else "s"} imported successfully.")
                AppButton("Done", { vm.resetImportUi(); vm.pop() },
                    Modifier.padding(horizontal = 16.dp).fillMaxWidth())
            } else {
                SuccessCard("${check.validCount} row${if (check.validCount == 1) "" else "s"} valid — ready to import.")
                if (imp.type == ImportType.TRANSACTIONS) {
                    ModeCard("Append", "Add rows to existing data — identical rows are imported anyway (no deduplication).",
                        selected = !imp.replace) { vm.setImportReplace(false) }
                    ModeCard("Replace everything",
                        "Deletes all existing transactions in all ledgers first, then imports.",
                        selected = imp.replace, danger = true) { vm.setImportReplace(true) }
                    if (imp.replace) {
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Warning, null, tint = C.ExpenseSoft,
                                modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("This permanently deletes every transaction in the home ledger and in every trip.",
                                color = C.ExpenseSoft, fontSize = 11.5.sp)
                        }
                    }
                }
                var confirmReplace by remember { mutableStateOf(false) }
                AppButton("Import ${check.validCount} ${imp.type.label.lowercase()}",
                    {
                        if (imp.type == ImportType.TRANSACTIONS && imp.replace) confirmReplace = true
                        else vm.confirmImport()
                    },
                    Modifier.padding(horizontal = 16.dp).fillMaxWidth())
                AppButton("Discard", { vm.resetImportUi(); vm.pop() },
                    Modifier.padding(horizontal = 16.dp).fillMaxWidth(), filled = false)
                if (confirmReplace) {
                    ConfirmDialog(
                        title = "Replace ALL data?",
                        body = "Every transaction in all ledgers will be deleted, then ${check.validCount} rows imported. " +
                            "This cannot be undone.",
                        confirmLabel = "Replace everything",
                        onConfirm = { vm.confirmImport(); confirmReplace = false },
                        onDismiss = { confirmReplace = false }
                    )
                }
            }
            Spacer(Modifier.height(96.dp))
        }
    }
}

@Composable
fun ErrorCard(text: String) {
    Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth()
        .clip(RoundedCornerShape(16.dp)).background(Color(0x1AFF5449)).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(Icons.Rounded.Error, null, tint = C.ExpenseSoft, modifier = Modifier.size(20.dp))
        Text(text, color = C.ExpenseSoft, fontSize = 13.sp, lineHeight = 19.sp)
    }
}

@Composable
fun SuccessCard(text: String) {
    Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth()
        .clip(RoundedCornerShape(16.dp)).background(Color(0x1A4FE084)).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(Icons.Rounded.CheckCircle, null, tint = C.Income, modifier = Modifier.size(20.dp))
        Text(text, color = C.Income, fontSize = 13.sp, lineHeight = 19.sp)
    }
}

@Composable
fun ModeCard(title: String, sub: String, selected: Boolean, danger: Boolean = false, onClick: () -> Unit) {
    Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth()
        .clip(RoundedCornerShape(14.dp))
        .border(1.5.dp, if (selected) C.Pri else C.Outline, RoundedCornerShape(14.dp))
        .clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(18.dp).clip(CircleShape)
            .border(2.dp, if (selected) C.Pri else C.Mut, CircleShape),
            contentAlignment = Alignment.Center) {
            if (selected) Box(Modifier.size(8.dp).clip(CircleShape).background(C.Pri))
        }
        Column {
            Text(title, color = if (danger && selected) C.ExpenseSoft else C.Fg, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(sub, color = C.Mut, fontSize = 11.5.sp, lineHeight = 17.sp)
        }
    }
}

// ============ JSON restore pre-check ============

@Composable
fun RestorePreCheckScreen(vm: AppViewModel) {
    val b = vm.restore.collectAsState().value
    var confirm by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SubBar("Restore database", { vm.cancelRestore() })

        if (b == null) {
            EmptyState(Icons.Rounded.Archive, "No backup loaded.")
            Spacer(Modifier.height(96.dp))
        } else {
            SectionTitle("Backup contents")
            Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth()
                .clip(RoundedCornerShape(16.dp)).background(C.S2).padding(14.dp)) {
                InfoLine("Exported at", b.exportedAt)
                InfoLine("App version", b.appVersion)
                InfoLine("Schema version", b.schemaVersion.toString())
                InfoLine("Home currency", b.homeCurrency)
                Spacer(Modifier.height(8.dp))
                InfoLine("Trips", b.trips.size.toString())
                InfoLine("Categories", b.categories.size.toString())
                InfoLine("Templates", b.templates.size.toString())
                InfoLine("Transactions", b.transactions.size.toString())
            }

            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Warning, null, tint = C.ExpenseSoft, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Strict Replace — all existing data will be permanently erased.",
                    color = C.ExpenseSoft, fontSize = 12.sp)
            }

            AppButton("Restore now", { confirm = true },
                Modifier.padding(horizontal = 16.dp).fillMaxWidth(), filled = true, danger = true)
            AppButton("Cancel", { vm.cancelRestore() },
                Modifier.padding(horizontal = 16.dp).fillMaxWidth(), filled = false)
            Spacer(Modifier.height(96.dp))
        }
    }

    if (confirm) {
        ConfirmDialog(
            title = "Restore database?",
            body = "All existing data across all ledgers and local settings will be permanently erased and replaced " +
                "with the contents of this backup. This cannot be undone.",
            confirmLabel = "Replace everything",
            onConfirm = { vm.confirmRestore(); confirm = false },
            onDismiss = { confirm = false }
        )
    }
}

@Composable
fun InfoLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, color = C.Mut, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(value, color = C.Fg, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}
