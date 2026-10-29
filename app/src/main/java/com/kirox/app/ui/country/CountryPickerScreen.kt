package com.kirox.app.ui.country

import com.kirox.app.ui.components.fusionBackground
import com.kirox.app.ui.theme.Fusion
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kirox.app.phone.Country
import com.kirox.app.phone.PhoneEntryState
import com.kirox.app.ui.start.*
import com.kirox.app.ui.icons.KiroXIcon
import com.kirox.app.ui.icons.KiroXIcon as IconGlyph
import java.util.Locale

@Composable
fun CountryPickerScreen(state: PhoneEntryState, onBack: () -> Unit) {
    var query by remember { mutableStateOf("") }
    // Locale from the configuration, so a language change recomposes.
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val normalized = query.trim().lowercase(locale).removePrefix("+")
    val filtered by remember(query, locale) {
        derivedStateOf {
            if (normalized.isBlank()) state.resolver.countries else state.resolver.countries.filter { country ->
                country.name.lowercase(locale).contains(normalized) ||
                    country.regionCode.lowercase(Locale.ROOT).contains(normalized) ||
                    country.callingCode.toString().startsWith(normalized)
            }
        }
    }
    val recent = remember(state.country) { state.resolver.recentCountries() }

    BackHandler(onBack = onBack)
    Box(
        Modifier.fillMaxSize().fusionBackground(Fusion.colors).statusBarsPadding().navigationBarsPadding()
    ) {
        Column(
            Modifier.fillMaxSize().padding(top = 8.dp).clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)).background(Fusion.colors.background)
        ) {
            Box(Modifier.fillMaxWidth().height(34.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.width(46.dp).height(5.dp).clip(RoundedCornerShape(99.dp)).background(Fusion.colors.textTertiary))
            }
            Row(
                Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Выбор страны", color = KiroXWhite, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
            SearchField(query, { query = it })
            Spacer(Modifier.height(10.dp))
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                item {
                    AutoDetectRow(state.resolver.automaticCountry()) {
                        if (state.detectAutomatically()) onBack()
                    }
                }
                if (normalized.isBlank() && recent.isNotEmpty()) {
                    item { SectionTitle("Недавние") }
                    items(recent, key = { "recent_${it.regionCode}" }) { CountryRow(it, state.country, state::selectCountry, onBack) }
                }
                item { SectionTitle(if (normalized.isBlank()) "Все страны" else "Результаты") }
                items(filtered, key = { it.regionCode }) { CountryRow(it, state.country, state::selectCountry, onBack) }
                if (filtered.isEmpty()) {
                    item { Text("Ничего не найдено", color = KiroXSecondary, fontSize = 15.sp, modifier = Modifier.padding(24.dp)) }
                }
            }
        }
    }
}

@Composable
private fun SearchField(value: String, onValueChange: (String) -> Unit) {
    com.kirox.app.ui.components.FusionSearchField(value, onValueChange, "Название, ISO или код", Modifier.padding(horizontal = 20.dp))
}

@Composable
private fun AutoDetectRow(detected: Country?, onClick: () -> Unit) {
    Row(
        Modifier.padding(horizontal = 20.dp, vertical = 8.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(KiroXSurface).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val glyph = KiroXWhite
        Canvas(Modifier.size(22.dp)) {
            drawCircle(glyph, radius = size.minDimension * .36f, style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx()))
            drawLine(glyph, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height * .2f), 1.5.dp.toPx())
            drawLine(glyph, Offset(size.width / 2, size.height * .8f), Offset(size.width / 2, size.height), 1.5.dp.toPx())
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text("Определить автоматически", color = KiroXWhite, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            if (detected != null) Text("${detected.name} (${detected.dialCode})", color = KiroXSecondary, fontSize = 13.sp)
        }
        ChevronRight()
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text.uppercase(androidx.compose.ui.platform.LocalConfiguration.current.locales[0]), color = KiroXSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp, modifier = Modifier.padding(start = 24.dp, top = 20.dp, bottom = 8.dp))
}

@Composable
private fun CountryRow(country: Country, selected: Country, onSelect: (Country) -> Unit, onBack: () -> Unit) {
    val active = country.regionCode == selected.regionCode
    Row(
        Modifier.fillMaxWidth().background(if (active) Fusion.colors.surface1 else Color.Transparent).clickable { onSelect(country); onBack() }.padding(horizontal = 24.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(country.flag, fontSize = 24.sp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(country.name, color = KiroXWhite, fontSize = 16.sp, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1)
            Text(country.regionCode, color = KiroXSecondary, fontSize = 12.sp)
        }
        Text(country.dialCode, color = if (active) KiroXWhite else KiroXSecondary, fontSize = 15.sp)
        if (active) {
            Spacer(Modifier.width(12.dp))
            IconGlyph(KiroXIcon.Check, "Выбрано", Modifier.size(18.dp), KiroXWhite)
        }
    }
}

@Composable
private fun BackButton(onClick: () -> Unit) {
    val glyph = KiroXWhite
    Canvas(Modifier.size(32.dp).clickable(onClick = onClick).padding(6.dp)) {
        drawLine(glyph, Offset(size.width * .68f, size.height * .1f), Offset(size.width * .28f, size.height * .5f), 2.dp.toPx())
        drawLine(glyph, Offset(size.width * .28f, size.height * .5f), Offset(size.width * .68f, size.height * .9f), 2.dp.toPx())
    }
}
