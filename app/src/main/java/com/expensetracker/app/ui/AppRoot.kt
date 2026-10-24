@file:OptIn(ExperimentalMaterial3Api::class)

package com.expensetracker.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun AppRoot(vm: AppViewModel) {
    val stack = vm.stack.collectAsState().value
    val ui = vm.ui.collectAsState().value
    val error = vm.dialogError.collectAsState().value

    BackHandler(enabled = stack.isNotEmpty()) { vm.pop() }

    if (error != null) {
        AlertDialog(
            onDismissRequest = { vm.clearError() },
            confirmButton = { TextButton(onClick = { vm.clearError() }) { Text("OK", color = C.Pri) } },
            title = { Text("Error", color = C.Fg, fontSize = 17.sp, fontWeight = FontWeight.Bold) },
            text = { Text(error, color = C.Mut, fontSize = 13.sp) },
            containerColor = C.DialogBg,
            titleContentColor = C.Fg,
            textContentColor = C.Mut
        )
    }

    if (stack.isNotEmpty()) {
        val top = stack.last()
        Column(
            Modifier.fillMaxSize().background(Color.Black)
                .statusBarsPadding().navigationBarsPadding()
        ) {
            when (top) {
                is Screen.TxForm -> TxFormScreen(vm, top.id)
                is Screen.CategoryForm -> CategoryFormScreen(vm, top.id)
                is Screen.TemplateForm -> TemplateFormScreen(vm, top.id)
                is Screen.TripForm -> TripFormScreen(vm, top.id)
                is Screen.TripView -> TripViewScreen(vm, top.id)
                Screen.ManageTemplates -> ManageTemplatesScreen(vm)
                Screen.ManageCategories -> ManageCategoriesScreen(vm)
                Screen.ManageTrips -> ManageTripsScreen(vm)
                Screen.CurrencyPicker -> CurrencyPickerScreen(vm)
                Screen.CsvExport -> CsvExportScreen(vm)
                Screen.CsvImport -> CsvImportScreen(vm)
                Screen.RestorePreCheck -> RestorePreCheckScreen(vm)
            }
        }
    } else {
        Scaffold(
            containerColor = Color.Black,
            bottomBar = { BottomBar(ui.tab, vm::setTab) },
            floatingActionButton = {
                if (ui.tab != Tabs.SETTINGS) {
                    FloatingActionButton(
                        onClick = { vm.openTxForm(null) },
                        containerColor = C.PriC,
                        contentColor = C.OnPriC,
                        shape = RoundedCornerShape(18.dp)
                    ) { Icon(Icons.Rounded.Add, null) }
                }
            }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when (ui.tab) {
                    Tabs.OVERVIEW -> OverviewScreen(vm)
                    Tabs.TRANSACTIONS -> TransactionsScreen(vm)
                    Tabs.BALANCE -> BalanceScreen(vm)
                    Tabs.SETTINGS -> SettingsScreen(vm)
                }
            }
        }
    }
}

@Composable
fun BottomBar(tab: Int, onSelect: (Int) -> Unit) {
    NavigationBar(containerColor = Color.Black) {
        listOf(
            Triple(Tabs.OVERVIEW, "Overview", Icons.Rounded.DonutSmall),
            Triple(Tabs.TRANSACTIONS, "Transactions", Icons.AutoMirrored.Rounded.ReceiptLong),
            Triple(Tabs.BALANCE, "Balance", Icons.Rounded.AccountBalance),
            Triple(Tabs.SETTINGS, "Settings", Icons.Rounded.Settings)
        ).forEach { (t, label, icon) ->
            NavigationBarItem(
                selected = tab == t,
                onClick = { onSelect(t) },
                icon = { Icon(icon, null) },
                label = { Text(label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = C.Pri,
                    unselectedIconColor = C.Mut,
                    selectedTextColor = C.Pri,
                    unselectedTextColor = C.Mut,
                    indicatorColor = C.S3
                )
            )
        }
    }
}
