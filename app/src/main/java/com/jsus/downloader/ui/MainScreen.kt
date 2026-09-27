package com.jsus.downloader.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import com.jsus.downloader.download.DownloadRepo

@Composable
fun MainScreen(vm: MainViewModel) {
    var page by rememberSaveable { mutableStateOf(0) }
    val jobs by DownloadRepo.jobs.collectAsState()
    val active = jobs.count { it.status.active }

    Scaffold(
        containerColor = JC.Bg,
        bottomBar = {
            NavigationBar(containerColor = JC.Bg2) {
                val colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = JC.Cyan, selectedTextColor = JC.Cyan,
                    unselectedIconColor = JC.Text2, unselectedTextColor = JC.Text2, indicatorColor = JC.Bg4
                )
                NavigationBarItem(
                    selected = page == 0, onClick = { page = 0 }, colors = colors,
                    icon = { Text(if (active > 0) "⬇$active" else "⬇", fontSize = 18.sp) },
                    label = { Text("Descargar") }
                )
                NavigationBarItem(
                    selected = page == 1, onClick = { page = 1 }, colors = colors,
                    icon = { Text("🕘", fontSize = 18.sp) }, label = { Text("Historial") }
                )
                NavigationBarItem(
                    selected = page == 2, onClick = { page = 2 }, colors = colors,
                    icon = { Text("⚙", fontSize = 18.sp) }, label = { Text("Ajustes") }
                )
            }
        }
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            when (page) {
                0 -> DownloadScreen(vm)
                1 -> HistoryScreen(onAgain = { u -> page = 0; vm.url = u; vm.analyze(u) })
                else -> SettingsScreen()
            }
        }
    }
}
