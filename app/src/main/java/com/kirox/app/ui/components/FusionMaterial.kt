package com.kirox.app.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kirox.app.ui.theme.Fusion

/**
 * Main action of a screen: Material 3 filled button, full width. While [busy] it keeps its colour,
 * shows progress and ignores taps.
 */
@Composable
fun FusionButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, busy: Boolean = false) {
    Button(
        onClick = { if (!busy) onClick() },
        enabled = enabled || busy,
        modifier = modifier.fillMaxWidth().heightIn(min = 52.dp),
        colors = ButtonDefaults.buttonColors(disabledContainerColor = Fusion.colors.surface2, disabledContentColor = Fusion.colors.textTertiary)
    ) {
        if (busy) CircularProgressIndicator(Modifier.size(22.dp), color = Fusion.colors.onAccent, strokeWidth = 2.dp)
        else Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

/** One choice out of a few: Material 3 segmented buttons in the monochrome palette. */
@Composable
fun <T> FusionSegmented(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    SingleChoiceSegmentedButtonRow(modifier.fillMaxWidth()) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == selected,
                onClick = { onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = Fusion.colors.surface3,
                    activeContentColor = Fusion.colors.textPrimary,
                    activeBorderColor = Fusion.colors.outlineStrong,
                    inactiveContainerColor = Fusion.colors.background,
                    inactiveContentColor = Fusion.colors.textSecondary,
                    inactiveBorderColor = Fusion.colors.outlineStrong
                ),
                label = { Text(label(option), maxLines = 1, overflow = TextOverflow.Ellipsis) }
            )
        }
    }
}

/**
 * Search field in the Material 3 search bar style: a full-height pill with a search icon and a clear
 * button, on the Fusion surface colour.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun FusionSearchField(
    value: String,
    onChange: (String) -> Unit,
    hint: String,
    modifier: Modifier = Modifier,
    focusRequester: androidx.compose.ui.focus.FocusRequester? = null
) {
    androidx.compose.material3.Surface(
        shape = androidx.compose.foundation.shape.CircleShape,
        color = Fusion.colors.surface1,
        modifier = modifier.fillMaxWidth()
    ) {
        androidx.compose.material3.SearchBarDefaults.InputField(
            query = value,
            onQueryChange = onChange,
            onSearch = {},
            expanded = false,
            onExpandedChange = {},
            modifier = Modifier.fillMaxWidth().let { if (focusRequester != null) it.focusRequester(focusRequester) else it },
            placeholder = { Text(hint, color = Fusion.colors.textTertiary) },
            leadingIcon = { com.kirox.app.ui.icons.KiroXIcon(com.kirox.app.ui.icons.KiroXIcon.Search, null, Modifier.size(20.dp), Fusion.colors.textSecondary) },
            trailingIcon = if (value.isNotEmpty()) ({
                androidx.compose.material3.IconButton(onClick = { onChange("") }) { Text("×", color = Fusion.colors.textSecondary, style = MaterialTheme.typography.titleLarge) }
            }) else null,
            colors = androidx.compose.material3.SearchBarDefaults.inputFieldColors(
                focusedTextColor = Fusion.colors.textPrimary, unfocusedTextColor = Fusion.colors.textPrimary, cursorColor = Fusion.colors.textPrimary
            )
        )
    }
}
