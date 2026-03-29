package com.example.hexapod_client.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hexapod_client.HexapodClientViewModel
import com.example.hexapod_client.ui.config.ConfigScreen
import com.example.hexapod_client.ui.controller.ControllerScreen
import com.example.hexapod_client.ui.files.MissionFilesScreen
import com.example.hexapod_client.ui.map.WaypointMapScreen
import com.example.hexapod_client.ui.theme.*
import kotlinx.coroutines.launch

private val TABS = listOf("MAP", "CONTROL", "CONFIG", "FILES")

@Composable
fun AppPager(vm: HexapodClientViewModel) {
    val pagerState = rememberPagerState(initialPage = 1, pageCount = { TABS.size })
    val scope      = rememberCoroutineScope()

    val navigateToConfig: () -> Unit = {
        scope.launch { pagerState.animateScrollToPage(2) }
    }

    Column(modifier = Modifier.fillMaxSize().background(BgColor)) {

        // Screens — swipe disabled; joystick gestures would steal touch events
        HorizontalPager(
            state           = pagerState,
            userScrollEnabled = false,
            modifier        = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) { page ->
            when (page) {
                0 -> WaypointMapScreen(vm = vm)
                1 -> ControllerScreen(vm = vm, onNavigateToConfig = navigateToConfig)
                2 -> ConfigScreen(vm = vm)
                3 -> MissionFilesScreen()
            }
        }

        // Navigation bar
        NavBar(
            selectedIndex = pagerState.currentPage,
            onSelect      = { scope.launch { pagerState.animateScrollToPage(it) } }
        )
    }
}

@Composable
private fun NavBar(
    selectedIndex: Int,
    onSelect:      (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(PanelColor)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        TABS.forEachIndexed { index, label ->
            val active = index == selectedIndex
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .height(34.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (active) AccentCyan.copy(alpha = 0.15f) else BgColor)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication        = null
                    ) { onSelect(index) }
            ) {
                Text(
                    text       = label,
                    color      = if (active) AccentCyan else LabelColor,
                    fontSize   = 11.sp,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                    letterSpacing = 1.2.sp
                )
            }
        }
    }
}
