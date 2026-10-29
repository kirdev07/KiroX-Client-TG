package com.kirox.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.Alignment
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
    val colors = Fusion.colors
    if (colors.backdrop != null) {
        // Fusion UI v2: a floating dock over the backdrop with labels, the active tab on a coloured pill.
        // Expressive: a full-width dark bar; Glass: a floating translucent dock. The active tab sits on
        // a wide rounded plate holding icon and label, as in the approved PNG.
        val shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp)
        val container = if (colors.glass) modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp).clip(shape).fusionPanel(colors, shape)
            else modifier.fillMaxWidth().background(colors.background).navigationBarsPadding()
        Row(container.selectableGroup().padding(horizontal = 8.dp, vertical = 6.dp)) {
            items.forEachIndexed { index, item ->
                val active = index == selected
                Box(Modifier.weight(1f).padding(horizontal = 3.dp), contentAlignment = Alignment.Center) {
                    Column(
                        Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(20.dp))
                            .background(if (active) colors.selectedPill else androidx.compose.ui.graphics.Color.Transparent)
                            .selectable(selected = active, onClick = { onSelect(index) }, role = androidx.compose.ui.semantics.Role.Tab)
                            .padding(horizontal = 2.dp, vertical = 7.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        KiroXIcon(item.icon, null, Modifier.size(24.dp), if (active) colors.onSelectedPill else colors.textSecondary)
                        Text(item.title, maxLines = 1, softWrap = false, style = MaterialTheme.typography.labelMedium, color = if (active) colors.onSelectedPill else colors.textSecondary)
                    }
                }
            }
        }
        return
    }
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
