package com.kirox.app.ui.demo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kirox.app.ui.start.BrandLogo
import com.kirox.app.ui.start.KiroXSecondary
import com.kirox.app.ui.start.KiroXWhite

@Composable
fun DemoHomeScreen(onExit: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().background(Color.Black).statusBarsPadding().navigationBarsPadding().padding(24.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            BrandLogo(Modifier.weight(1f))
            DemoBadge()
        }
        Spacer(Modifier.weight(1f))
        Text("KiroX", color = KiroXWhite, fontSize = 40.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Text("Демонстрационный главный экран", color = KiroXWhite, fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))
        Text("Реальный аккаунт, сообщения и TDLib здесь не используются.", color = KiroXSecondary, fontSize = 15.sp, lineHeight = 21.sp)
        Spacer(Modifier.weight(1f))
        ExitDemoButton(onExit)
        Spacer(Modifier.height(20.dp))
    }
}
