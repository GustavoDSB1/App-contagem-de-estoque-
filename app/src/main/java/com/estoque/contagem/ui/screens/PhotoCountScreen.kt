package com.estoque.contagem.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.estoque.contagem.data.CircleCounter
import com.estoque.contagem.data.CountMark
import com.estoque.contagem.data.ProductImages
import com.estoque.contagem.ui.theme.CardColor
import com.estoque.contagem.ui.theme.ExcelGreen
import com.estoque.contagem.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val ANALYSIS_MAX = 640 // lado maior da imagem analisada (rápido no celular)
private const val DISPLAY_MAX = 1600
private val Mark = Color(0xFF22C55E)
private val Sample = Color(0xFFF59E0B)

/**
 * Contar por foto: tampinhas no engradado, latas vistas de cima...
 * 1) foto de cima  2) toque numa tampinha e ajuste o tamanho  3) contagem automática
 * 4) toque para adicionar/remover marcações  5) "Usar" manda o total para a calculadora.
 */
@Composable
fun PhotoCountScreen(color: Color, onClose: () -> Unit, onResult: (Int) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    BackHandler(onBack = onClose)

    var display by remember { mutableStateOf<Bitmap?>(null) }
    var gray by remember { mutableStateOf<IntArray?>(null) }
    var aw by remember { mutableStateOf(0) } // tamanho da imagem analisada
    var ah by remember { mutableStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    var sample by remember { mutableStateOf<CountMark?>(null) } // tampinha de exemplo
    var radius by remember { mutableFloatStateOf(12f) }
    var sensitivity by remember { mutableFloatStateOf(0.5f) }
    var marks by remember { mutableStateOf<List<CountMark>>(emptyList()) }
    var counted by remember { mutableStateOf(false) } // já rodou a contagem automática

    fun load(uri: Uri) {
        scope.launch {
            loading = true
            error = null
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val big = ProductImages.decodeScaled(context, uri, DISPLAY_MAX)
                    val scale = ANALYSIS_MAX.toFloat() / maxOf(big.width, big.height)
                    val small = if (scale >= 1f) big else Bitmap.createScaledBitmap(
                        big, (big.width * scale).toInt().coerceAtLeast(8), (big.height * scale).toInt().coerceAtLeast(8), true
                    )
                    Triple(big, small, ProductImages.grayPixels(small))
                }
            }
            loading = false
            result.onSuccess { (big, small, pixels) ->
                display = big
                gray = pixels
                aw = small.width
                ah = small.height
                sample = null
                radius = small.width * 0.04f
                marks = emptyList()
                counted = false
            }.onFailure { error = "Não foi possível abrir a foto." }
        }
    }

    fun runCount() {
        val pixels = gray ?: return
        scope.launch {
            loading = true
            val found = withContext(Dispatchers.Default) {
                CircleCounter.detect(pixels, aw, ah, radius, sensitivity)
            }
            loading = false
            marks = found
            counted = true
        }
    }

    val cameraFile = remember { File(File(context.cacheDir, "camera").apply { mkdirs() }, "contagem.jpg") }
    val cameraUri = remember { FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", cameraFile) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) load(cameraUri) }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) load(uri) }
    fun takePhoto() = runCatching { camera.launch(cameraUri) }.onFailure { error = "Não foi possível abrir a câmera." }
    fun pickPhoto() = gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Topo: voltar, total e "usar"
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp)
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("📷 Contar por foto", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                if (display != null) {
                    Text("Total: ${marks.size}", fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, color = color)
                }
            }
            if (marks.isNotEmpty()) {
                Button(
                    onClick = { onResult(marks.size) },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ExcelGreen)
                ) { Text("Usar ${marks.size}", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
                Spacer(Modifier.width(8.dp))
            }
        }

        // Instrução do passo atual
        val instruction = when {
            display == null -> null
            !counted && sample == null -> "1️⃣ Toque no centro de UMA tampinha (ou lata)"
            !counted -> "2️⃣ Ajuste o tamanho do círculo laranja até cobrir a tampinha e toque em Contar"
            else -> "✅ Confira: toque numa marcação para tirar, ou num lugar vazio para adicionar"
        }
        instruction?.let {
            Text(
                it,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(CardColor)
                    .padding(10.dp)
            )
        }

        // Foto com as marcações
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            val bmp = display
            if (bmp == null) {
                EmptyPhotoHelp(color = color, onCamera = { takePhoto() }, onGallery = { pickPhoto() })
            } else {
                BoxWithConstraints(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    val density = LocalDensity.current
                    val boxW = with(density) { maxWidth.toPx() }
                    val boxH = with(density) { maxHeight.toPx() }
                    val fit = minOf(boxW / bmp.width, boxH / bmp.height)
                    val shownW = bmp.width * fit
                    val shownH = bmp.height * fit
                    val k = shownW / aw // pixels na tela por pixel analisado
                    val image = remember(bmp) { bmp.asImageBitmap() }
                    Box(
                        modifier = Modifier
                            .size(with(density) { shownW.toDp() }, with(density) { shownH.toDp() })
                            .pointerInput(bmp, counted, marks, radius) {
                                detectTapGestures { tap ->
                                    val x = tap.x / k
                                    val y = tap.y / k
                                    if (!counted) {
                                        sample = CountMark(x, y)
                                    } else {
                                        val near = CircleCounter.nearest(marks, x, y, radius)
                                        marks = if (near != null) marks.filterIndexed { i, _ -> i != near }
                                        else marks + CountMark(x, y)
                                    }
                                }
                            }
                    ) {
                        Image(image, contentDescription = "Foto", contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val r = radius * k
                            marks.forEach { m ->
                                val c = Offset(m.x * k, m.y * k)
                                drawCircle(Mark, radius = r, center = c, style = Stroke(width = 3.dp.toPx()))
                                drawCircle(Mark, radius = 4.dp.toPx(), center = c)
                            }
                            sample?.takeIf { !counted }?.let { s ->
                                drawCircle(
                                    Sample, radius = r, center = Offset(s.x * k, s.y * k),
                                    style = Stroke(width = 3.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 8f)))
                                )
                            }
                        }
                    }
                }
            }
            if (loading) CircularProgressIndicator(color = color)
        }

        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp)) }

        // Controles de baixo
        if (display != null) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                if (!counted) {
                    Text("Tamanho da tampinha", fontSize = 13.sp, color = TextSecondary)
                    Slider(
                        value = radius,
                        onValueChange = { radius = it },
                        valueRange = 4f..(minOf(aw, ah) / 4f).coerceAtLeast(5f)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { counted = true; marks = emptyList() },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f).height(50.dp)
                        ) { Text("👆 Contar tocando", fontWeight = FontWeight.SemiBold) }
                        Button(
                            onClick = { runCount() },
                            enabled = sample != null && !loading,
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = color),
                            modifier = Modifier.weight(1f).height(50.dp)
                        ) { Text("🔍 Contar", fontWeight = FontWeight.Bold) }
                    }
                } else {
                    Text("Achou de menos ou de mais? Ajuste e conte de novo:", fontSize = 13.sp, color = TextSecondary)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { sensitivity = (sensitivity - 0.1f).coerceAtLeast(0.1f); runCount() },
                            enabled = sample != null && !loading,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f)
                        ) { Text("Achou demais") }
                        OutlinedButton(
                            onClick = { sensitivity = (sensitivity + 0.1f).coerceAtMost(0.9f); runCount() },
                            enabled = sample != null && !loading,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f)
                        ) { Text("Achou de menos") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                        OutlinedButton(
                            onClick = { counted = false; marks = emptyList() },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f)
                        ) { Text("↺ Recomeçar") }
                        OutlinedButton(
                            onClick = { takePhoto() },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f)
                        ) { Text("📷 Outra foto") }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyPhotoHelp(color: Color, onCamera: () -> Unit, onGallery: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {
        Text("🍺", fontSize = 64.sp)
        Spacer(Modifier.height(12.dp))
        Text("Fotografe de CIMA", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(6.dp))
        Text(
            "Engradado ou fardo inteiro na foto, com boa luz e o celular paralelo às tampinhas. " +
                "Funciona com tampinhas de garrafa e latas vistas de cima.",
            textAlign = TextAlign.Center,
            color = TextSecondary
        )
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onCamera,
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = color),
            modifier = Modifier.fillMaxWidth().height(54.dp)
        ) { Text("📷 Tirar foto", fontSize = 17.sp, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = onGallery,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) { Text("🖼️ Escolher da galeria", fontWeight = FontWeight.SemiBold) }
    }
}
