package com.kirox.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kirox.app.ui.icons.KiroXIcon
import com.kirox.app.ui.theme.Fusion
import com.kirox.app.ui.theme.FusionSize

data class FusionNavItem(val title: String, val icon: KiroXIcon)

/**
 * Bottom navigation: Material 3 NavigationBar in the Fusion look — screen-black bar under a hairline,
 * the active tab on M3's pill indicator. Titles are shown on request and always kept for TalkBack.
 */
@Composable
fun FusionBottomBar(items: List<FusionNavItem>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier, showLabels: Boolean = false) {
    Column(modifier.fillMaxWidth()) {
        HorizontalDivider(thickness = FusionSize.hairline, color = Fusion.colors.outline)
        NavigationBar(containerColor = Fusion.colors.background, tonalElevation = 0.dp) {
            items.forEachIndexed { index, item ->
                NavigationBarItem(
                    selected = index == selected,
                    onClick = { onSelect(index) },
                    icon = { KiroXIcon(item.icon, item.title, Modifier.size(23.dp), LocalContentColor.current) },
                    label = if (showLabels) ({ Text(item.title, maxLines = 1) }) else null,
                    alwaysShowLabel = showLabels,
                    colors = navColors()
                )
            }
        }
    }
}

/** The same navigation as a Material 3 rail for wide windows (≥ 600 dp). */
@Composable
fun FusionNavigationRail(items: List<FusionNavItem>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxHeight()) {
        NavigationRail(containerColor = Fusion.colors.background) {
            Spacer(Modifier.weight(1f))
            items.forEachIndexed { index, item ->
                NavigationRailItem(
                    selected = index == selected,
                    onClick = { onSelect(index) },
                    icon = { KiroXIcon(item.icon, item.title, Modifier.size(23.dp), LocalContentColor.current) },
                    colors = NavigationRailItemDefaults.colors(
                        selectedIconColor = Fusion.colors.textPrimary,
                        unselectedIconColor = Fusion.colors.textTertiary,
                        indicatorColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                )
            }
            Spacer(Modifier.weight(1f))
        }
        VerticalDivider(thickness = FusionSize.hairline, color = Fusion.colors.outline)
    }
}

@Composable
private fun navColors() = NavigationBarItemDefaults.colors(
    selectedIconColor = Fusion.colors.textPrimary,
    selectedTextColor = Fusion.colors.textPrimary,
    unselectedIconColor = Fusion.colors.textTertiary,
    unselectedTextColor = Fusion.colors.textTertiary,
    indicatorColor = MaterialTheme.colorScheme.secondaryContainer
)
