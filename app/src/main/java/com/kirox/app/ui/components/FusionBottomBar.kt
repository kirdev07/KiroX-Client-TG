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
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kirox.app.ui.icons.KiroXIcon
import com.kirox.app.ui.theme.Fusion
import com.kirox.app.ui.theme.FusionMotion
import com.kirox.app.ui.theme.FusionSize

data class FusionNavItem(val title: String, val icon: KiroXIcon)

/**
 * Fusion bottom navigation, concept 2 "Pill Indicator": top hairline only, a monochrome pill behind
 * the active icon, labels on every item, background painted under the system navigation bar.
 */
@Composable
fun FusionBottomBar(items: List<FusionNavItem>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().background(Fusion.colors.background)) {
        HorizontalDivider(thickness = FusionSize.hairline, color = Fusion.colors.outline)
        Row(
            Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).height(64.dp).selectableGroup(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEachIndexed { index, item ->
                val active = index == selected
                val pillWidth by animateDpAsState(if (active) 56.dp else 32.dp, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow), label = "pill_width")
                val pillColor by animateColorAsState(if (active) Fusion.colors.surface3 else Fusion.colors.background, tween(FusionMotion.NORMAL), label = "pill_color")
                val contentColor by animateColorAsState(if (active) Fusion.colors.textPrimary else Fusion.colors.textSecondary, tween(FusionMotion.NORMAL), label = "item_color")
                Column(
                    Modifier.weight(1f).fillMaxHeight().selectable(
                        selected = active,
                        role = Role.Tab,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(bounded = false, radius = 32.dp),
                        onClick = { onSelect(index) }
                    ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(Modifier.width(pillWidth).height(32.dp).clip(CircleShape).background(pillColor), contentAlignment = Alignment.Center) {
                        KiroXIcon(item.icon, null, Modifier.size(FusionSize.icon), contentColor)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(item.title, color = contentColor, fontSize = 11.sp, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium, maxLines = 1)
                }
            }
        }
    }
}

/** The same navigation as a left rail for wide windows (≥ 600 dp), with the same pill indicator. */
@Composable
fun FusionNavigationRail(items: List<FusionNavItem>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxHeight().background(Fusion.colors.background)) {
        Column(
            Modifier.fillMaxHeight().width(88.dp).windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Vertical + WindowInsetsSides.Start)).selectableGroup(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)
        ) {
            items.forEachIndexed { index, item ->
                val active = index == selected
                val pillWidth by animateDpAsState(if (active) 56.dp else 32.dp, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow), label = "rail_pill")
                val pillColor by animateColorAsState(if (active) Fusion.colors.surface3 else Fusion.colors.background, tween(FusionMotion.NORMAL), label = "rail_pill_color")
                val contentColor by animateColorAsState(if (active) Fusion.colors.textPrimary else Fusion.colors.textSecondary, tween(FusionMotion.NORMAL), label = "rail_color")
                Column(
                    Modifier.width(80.dp).heightIn(min = 64.dp).selectable(
                        selected = active, role = Role.Tab, interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(bounded = false, radius = 36.dp), onClick = { onSelect(index) }
                    ),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
                ) {
                    Box(Modifier.width(pillWidth).height(32.dp).clip(CircleShape).background(pillColor), contentAlignment = Alignment.Center) {
                        KiroXIcon(item.icon, null, Modifier.size(FusionSize.icon), contentColor)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(item.title, color = contentColor, fontSize = 11.sp, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium, maxLines = 1)
                }
            }
        }
        androidx.compose.material3.VerticalDivider(thickness = FusionSize.hairline, color = Fusion.colors.outline)
    }
}
