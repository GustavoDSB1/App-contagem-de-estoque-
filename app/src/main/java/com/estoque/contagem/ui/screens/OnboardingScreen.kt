package com.estoque.contagem.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.estoque.contagem.ui.theme.Indigo
import com.estoque.contagem.ui.theme.Violet

private data class Step(val emoji: String, val title: String, val text: String)

private val STEPS = listOf(
    Step(
        "📦",
        "Bem-vindo!",
        "Cadastre os produtos do seu estoque com foto, unidade (un, kg, litros...) e categoria. " +
            "Se já tiver uma planilha, dá para importar."
    ),
    Step(
        "🔢",
        "Conte rapidinho",
        "Toque no produto ou leia o código de barras e digite quanto contou. O app soma tudo sozinho."
    ),
    Step(
        "📊",
        "Gere o relatório",
        "No fim, gere a planilha Excel ou o PDF com a data da contagem e envie por WhatsApp ou e-mail."
    )
)

/** Tela de boas-vindas em 3 passos (só na primeira vez). */
@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    val last = step == STEPS.lastIndex

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Indigo, Violet)))
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp)
    ) {
        if (!last) {
            TextButton(onClick = onFinish, modifier = Modifier.align(Alignment.TopEnd)) {
                Text("Pular", color = Color.White.copy(alpha = 0.85f), fontWeight = FontWeight.SemiBold)
            }
        }

        AnimatedContent(
            targetState = step,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "passo",
            modifier = Modifier.align(Alignment.Center)
        ) { index ->
            val s = STEPS[index]
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(160.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.18f))
                ) { Text(s.emoji, fontSize = 80.sp) }
                Spacer(Modifier.height(32.dp))
                Text(
                    s.title,
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    s.text,
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 17.sp,
                    lineHeight = 24.sp,
                    textAlign = TextAlign.Center
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                STEPS.indices.forEach { i ->
                    Box(
                        modifier = Modifier
                            .size(width = if (i == step) 24.dp else 8.dp, height = 8.dp)
                            .clip(RoundedCornerShape(50))
                            .background(if (i == step) Color.White else Color.White.copy(alpha = 0.4f))
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = { if (last) onFinish() else step++ },
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Indigo),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
            ) {
                Text(if (last) "Começar  🚀" else "Próximo", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            if (step > 0) {
                TextButton(onClick = { step-- }) {
                    Text("Voltar", color = Color.White.copy(alpha = 0.85f))
                }
            } else {
                Spacer(Modifier.width(1.dp).height(48.dp))
            }
        }
    }
}
