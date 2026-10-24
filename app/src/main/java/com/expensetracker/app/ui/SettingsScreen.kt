@file:OptIn(ExperimentalMaterial3Api::class)

package com.expensetracker.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.expensetracker.app.util.Currencies
import com.expensetracker.app.util.FileNames
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(vm: AppViewModel) {
    val d = vm.data.collectAsState().value
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            val text = vm.pendingExport
            if (text != null) {
                writeText(ctx, uri, text)
                vm.pendingExport = null
            }
        }
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val appCtx = ctx.applicationContext
            scope.launch(Dispatchers.IO) {
                val text = readText(appCtx, uri)
                if (text == null) vm.showError("Could not read the selected file.")
                else {
                    val err = vm.parseBackupFile(text)
                    if (err != null) vm.showError(err)
                }
            }
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text("Settings", color = C.Fg, fontSize = 27.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 2.dp))

        SectionTitle("Ledger & content")
        SettingsItem(Icons.Rounded.Payments, "Home currency",
            "${d.homeCurrency} · ${Currencies.display(d.homeCurrency)}") {
            vm.openCurrencyPicker(AppViewModel.CurrencyCtx.HOME)
        }
        SettingsItem(Icons.Rounded.Bolt, "Transaction templates", "${d.templates.size} defined") {
            vm.push(Screen.ManageTemplates)
        }
        SettingsItem(Icons.Rounded.Category, "Categories", "${d.categories.size} defined") {
            vm.push(Screen.ManageCategories)
        }
        val open = d.trips.count { it.status == "open" }
        SettingsItem(Icons.Rounded.FlightTakeoff, "Trips", "$open open · ${d.trips.size - open} closed") {
            vm.push(Screen.ManageTrips)
        }

        SectionTitle("Data management")
        SettingsItem(Icons.Rounded.FileDownload, "Export CSV", "Home date range · whole trips · all ledgers") {
            vm.push(Screen.CsvExport)
        }
        SettingsItem(Icons.Rounded.FileUpload, "Import CSV", "Transactions, categories, templates, trips") {
            vm.push(Screen.CsvImport)
        }
        SettingsItem(Icons.Rounded.Archive, "Export Full Backup (JSON)",
            "Single file: all ledgers, categories, templates, transactions & settings") {
            vm.pendingExport = vm.buildJsonBackup()
            backupLauncher.launch(FileNames.backup())
        }
        SettingsItem(Icons.Rounded.SettingsBackupRestore, "Restore Database (JSON)",
            "Strict replace · pre-check & confirmation") {
            restoreLauncher.launch(arrayOf("*/*"))
        }
        Spacer(Modifier.height(96.dp))
    }
}
