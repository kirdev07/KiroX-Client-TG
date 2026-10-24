package com.kirox.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kirox.app.ui.icons.KiroXIcon
import com.kirox.app.ui.theme.Fusion
import com.kirox.app.ui.theme.FusionMotion
import com.kirox.app.ui.theme.FusionSize

data class FusionNavItem(val title: String, val icon: KiroXIcon)

/**
 * Fusion Edge dock, as in the old KiroX: full-width bar in the screen background, a top hairline,
 * icons only on a 64×40 dp pill for the active tab. Titles stay in the semantics for TalkBack.
 */
@Composable
fun FusionBottomBar(items: List<FusionNavItem>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier, showLabels: Boolean = false) {
    Column(modifier.fillMaxWidth().background(Fusion.colors.background)) {
        HorizontalDivider(thickness = FusionSize.hairline, color = Fusion.colors.outline)
        Row(
            Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).height(72.dp).selectableGroup(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEachIndexed { index, item ->
                DockItem(item, index == selected, showLabels, Modifier.weight(1f).fillMaxHeight()) { onSelect(index) }
            }
        }
    }
}

/** The same dock as a left rail for wide windows (≥ 600 dp). */
@Composable
fun FusionNavigationRail(items: List<FusionNavItem>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxHeight().background(Fusion.colors.background)) {
        Column(
            Modifier.fillMaxHeight().width(88.dp).windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Vertical + WindowInsetsSides.Start)).selectableGroup(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)
        ) {
            items.forEachIndexed { index, item -> DockItem(item, index == selected, showLabel = false, modifier = Modifier.size(80.dp, 64.dp)) { onSelect(index) } }
        }
        VerticalDivider(thickness = FusionSize.hairline, color = Fusion.colors.outline)
    }
}

@Composable
private fun DockItem(item: FusionNavItem, active: Boolean, showLabel: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val pillWidth by animateDpAsState(if (active) 64.dp else 40.dp, spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow), label = "dock_pill")
    val pillColor by animateColorAsState(if (active) Fusion.colors.surface3 else Fusion.colors.background, tween(FusionMotion.NORMAL), label = "dock_pill_color")
    val contentColor by animateColorAsState(if (active) Fusion.colors.textPrimary else Fusion.colors.textTertiary, tween(FusionMotion.NORMAL), label = "dock_color")
    Column(
        modifier.semantics { contentDescription = item.title }.selectable(
            selected = active,
            role = Role.Tab,
            interactionSource = remember { MutableInteractionSource() },
            indication = ripple(bounded = false, radius = 36.dp),
            onClick = onClick
        ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(Modifier.width(pillWidth).height(40.dp).clip(CircleShape).background(pillColor), contentAlignment = Alignment.Center) {
            KiroXIcon(item.icon, null, Modifier.size(26.dp), contentColor)
        }
        if (showLabel) {
            Spacer(Modifier.height(4.dp))
            Text(item.title, color = contentColor, fontSize = 11.sp, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium, maxLines = 1)
        }
    }
}
