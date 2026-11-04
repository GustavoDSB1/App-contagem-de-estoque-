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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
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
import com.estoque.contagem.data.ColorMatch
import com.estoque.contagem.data.CountMark
import com.estoque.contagem.data.ProductImages
import com.estoque.contagem.data.RadiusModel
import com.estoque.contagem.ui.theme.CardColor
import com.estoque.contagem.ui.theme.ExcelGreen
import com.estoque.contagem.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs

private const val ANALYSIS_MAX = 640 // lado maior da imagem analisada (rápido no celular)
private const val DISPLAY_MAX = 1600
private const val MAX_ZOOM = 5f
private val Mark = Color(0xFF22C55E)
private val Sample = Color(0xFFF59E0B)
private val SampleSelected = Color(0xFFEF4444)

/** Retângulo da área a contar, em coordenadas da imagem analisada. */
private data class Area(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    fun contains(x: Float, y: Float) = x in left..right && y in top..bottom

    companion object {
        fun of(a: Offset, b: Offset) = Area(minOf(a.x, b.x), minOf(a.y, b.y), maxOf(a.x, b.x), maxOf(a.y, b.y))
    }
}

/**
 * Contar por foto: tampinhas no engradado, latas vistas de cima...
 * 1) foto  2) toque numa tampinha da frente (e, se a foto for inclinada, numa do fundo)
 * 3) opcional: só a cor da tampinha e/ou só uma área  4) contar  5) conferir (zoom com dois dedos)
 * 6) "Usar" manda o total para a calculadora.
 */
@Composable
fun PhotoCountScreen(color: Color, onClose: () -> Unit, onResult: (Int) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    BackHandler(onBack = onClose)

    var display by remember { mutableStateOf<Bitmap?>(null) }
    var gray by remember { mutableStateOf<IntArray?>(null) }
    var argb by remember { mutableStateOf<IntArray?>(null) }
    var aw by remember { mutableIntStateOf(0) } // tamanho da imagem analisada
    var ah by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    // Tampinhas de exemplo (frente e, opcional, fundo), com o raio de cada uma
    var samples by remember { mutableStateOf<List<CountMark>>(emptyList()) }
    var selected by remember { mutableIntStateOf(0) }
    var useColor by remember { mutableStateOf(true) }
    var area by remember { mutableStateOf<Area?>(null) }
    var areaMode by remember { mutableStateOf(false) }
    var sensitivity by remember { mutableFloatStateOf(0.5f) }
    var marks by remember { mutableStateOf<List<CountMark>>(emptyList()) }
    var counted by remember { mutableStateOf(false) }

    // Zoom com dois dedos
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }

    val model = remember(samples) { RadiusModel.fromSamples(samples) }

    fun resetView() {
        zoom = 1f
        pan = Offset.Zero
    }

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
                    val pixels = IntArray(small.width * small.height)
                    small.getPixels(pixels, 0, small.width, 0, 0, small.width, small.height)
                    Triple(big, small, pixels)
                }
            }
            loading = false
            result.onSuccess { (big, small, pixels) ->
                display = big
                argb = pixels
                gray = ProductImages.grayPixels(small)
                aw = small.width
                ah = small.height
                samples = emptyList()
                selected = 0
                area = null
                areaMode = false
                marks = emptyList()
                counted = false
                sensitivity = 0.5f
                resetView()
            }.onFailure { error = "Não foi possível abrir a foto." }
        }
    }

    fun runCount() {
        val g = gray ?: return
        val colors = argb ?: return
        if (samples.isEmpty()) return
        val m = model
        val tolerance = sensitivity
        val sampleColor = if (useColor) {
            ColorMatch.average(samples.map { ColorMatch.meanColor(colors, aw, ah, it.x, it.y, it.r) })
        } else {
            null
        }
        val a = area
        scope.launch {
            loading = true
            val found = withContext(Dispatchers.Default) {
                CircleCounter.detect(g, aw, ah, m, sensitivity, accept = { x, y ->
                    (a == null || a.contains(x.toFloat(), y.toFloat())) &&
                        (sampleColor == null || ColorMatch.similar(
                            sampleColor,
                            ColorMatch.meanColor(colors, aw, ah, x.toFloat(), y.toFloat(), m.at(y.toFloat())),
                            tolerance
                        ))
                })
            }
            loading = false
            marks = found
            counted = true
            areaMode = false
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
                .padding(horizontal = 4.dp, vertical = 2.dp)
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("📷 Contar por foto", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                if (display != null && counted) {
                    Text("Total: ${marks.size}", fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, color = color)
                }
            }
            if (counted && marks.isNotEmpty()) {
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
            areaMode -> "▭ Arraste o dedo para marcar a área a contar"
            !counted && samples.isEmpty() -> "1️⃣ Toque no centro de uma tampinha da FRENTE (embaixo na foto)"
            !counted && samples.size == 1 ->
                "2️⃣ Foto inclinada? Toque também numa tampinha do FUNDO, da mesma marca (opcional). Depois, Contar"
            !counted -> "Ajuste o tamanho de cada círculo (toque nele para escolher) e toque em Contar"
            else -> "✅ Confira: toque numa marcação para tirar, ou num lugar vazio para adicionar. Dois dedos = zoom"
        }
        instruction?.let {
            Text(
                it,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(CardColor)
                    .padding(8.dp)
            )
        }

        // Foto com as marcações (zoom com dois dedos)
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(8.dp)
                .clip(RoundedCornerShape(12.dp))
        ) {
            val bmp = display
            if (bmp == null) {
                EmptyPhotoHelp(color = color, onCamera = { takePhoto() }, onGallery = { pickPhoto() })
            } else {
                BoxWithConstraints(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(areaMode) {
                            if (areaMode) return@pointerInput
                            detectTransformGestures { _, panChange, zoomChange, _ ->
                                val newZoom = (zoom * zoomChange).coerceIn(1f, MAX_ZOOM)
                                val maxX = size.width * (newZoom - 1f) / 2f
                                val maxY = size.height * (newZoom - 1f) / 2f
                                zoom = newZoom
                                pan = Offset(
                                    (pan.x + panChange.x).coerceIn(-maxX, maxX),
                                    (pan.y + panChange.y).coerceIn(-maxY, maxY)
                                )
                            }
                        }
                ) {
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
                            .graphicsLayer(scaleX = zoom, scaleY = zoom, translationX = pan.x, translationY = pan.y)
                            .pointerInput(areaMode, counted, marks, samples, model) {
                                if (areaMode) {
                                    var start = Offset.Zero
                                    detectDragGestures(
                                        onDragStart = { start = it / k; area = Area.of(start, start) },
                                        onDrag = { change, _ -> area = Area.of(start, change.position / k) },
                                        onDragEnd = {
                                            area = area?.takeIf { abs(it.right - it.left) > 10 && abs(it.bottom - it.top) > 10 }
                                            areaMode = false
                                        }
                                    )
                                } else {
                                    detectTapGestures { tap ->
                                        val x = tap.x / k
                                        val y = tap.y / k
                                        if (!counted) {
                                            val near = samples.indices.firstOrNull { i ->
                                                kotlin.math.hypot(samples[i].x - x, samples[i].y - y) <= samples[i].r
                                            }
                                            when {
                                                near != null -> selected = near // escolhe qual círculo ajustar
                                                samples.size < 2 -> {
                                                    // A 2ª tampinha começa do tamanho da 1ª (o usuário ajusta)
                                                    val r = samples.firstOrNull()?.r ?: (aw * 0.04f)
                                                    samples = samples + CountMark(x, y, r)
                                                    selected = samples.lastIndex
                                                }
                                                else -> {
                                                    samples = samples.mapIndexed { i, s -> if (i == selected) CountMark(x, y, s.r) else s }
                                                }
                                            }
                                        } else {
                                            val near = CircleCounter.nearest(marks, x, y, model.at(y))
                                            marks = if (near != null) marks.filterIndexed { i, _ -> i != near }
                                            else marks + CountMark(x, y, model.at(y))
                                        }
                                    }
                                }
                            }
                    ) {
                        Image(image, contentDescription = "Foto", contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val stroke = 3.dp.toPx() / zoom
                            // Escurece fora da área escolhida
                            area?.let { a ->
                                val shade = Color.Black.copy(alpha = 0.45f)
                                val l = a.left * k; val t = a.top * k; val r = a.right * k; val b = a.bottom * k
                                drawRect(shade, Offset(0f, 0f), Size(size.width, t))
                                drawRect(shade, Offset(0f, b), Size(size.width, size.height - b))
                                drawRect(shade, Offset(0f, t), Size(l, b - t))
                                drawRect(shade, Offset(r, t), Size(size.width - r, b - t))
                                drawRect(Color.White, Offset(l, t), Size(r - l, b - t), style = Stroke(width = stroke))
                            }
                            marks.forEach { m ->
                                val c = Offset(m.x * k, m.y * k)
                                val rr = (if (m.r > 0f) m.r else model.at(m.y)) * k
                                drawCircle(Mark, radius = rr, center = c, style = Stroke(width = stroke))
                                drawCircle(Mark, radius = 4.dp.toPx() / zoom, center = c)
                            }
                            if (!counted) {
                                samples.forEachIndexed { i, s ->
                                    drawCircle(
                                        if (i == selected && samples.size > 1) SampleSelected else Sample,
                                        radius = s.r * k,
                                        center = Offset(s.x * k, s.y * k),
                                        style = Stroke(width = stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 8f)))
                                    )
                                }
                            }
                        }
                    }
                }
                if (zoom > 1.01f) {
                    Text(
                        "1x",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Color.Black.copy(alpha = 0.55f))
                            .clickable { resetView() }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
            if (loading) CircularProgressIndicator(color = color)
        }

        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp)) }

        // Controles de baixo
        if (display != null) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
                // Opções: cor, área
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    OptionChip("🎨 Só esta cor", useColor, color) {
                        useColor = !useColor
                        if (counted) runCount()
                    }
                    OptionChip(if (area == null) "▭ Escolher área" else "▭ Refazer área", areaMode, color) {
                        areaMode = !areaMode
                        resetView()
                    }
                    if (area != null) {
                        OptionChip("✕ Foto toda", false, color) {
                            area = null
                            if (counted) runCount()
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                if (!counted) {
                    if (samples.isNotEmpty()) {
                        val label = if (samples.size > 1) {
                            if (selected == 0) "Tamanho da tampinha da FRENTE" else "Tamanho da tampinha do FUNDO"
                        } else {
                            "Tamanho da tampinha"
                        }
                        Text(label, fontSize = 12.sp, color = TextSecondary)
                        Slider(
                            value = samples.getOrNull(selected)?.r ?: 10f,
                            onValueChange = { v ->
                                samples = samples.mapIndexed { i, s -> if (i == selected) s.copy(r = v) else s }
                            },
                            valueRange = 3f..(minOf(aw, ah) / 4f).coerceAtLeast(5f),
                            modifier = Modifier.height(32.dp)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { counted = true; marks = emptyList(); areaMode = false },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f).height(48.dp)
                        ) { Text("👆 Contar tocando", fontWeight = FontWeight.SemiBold) }
                        Button(
                            onClick = { runCount() },
                            enabled = samples.isNotEmpty() && !loading,
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = color),
                            modifier = Modifier.weight(1f).height(48.dp)
                        ) { Text("🔍 Contar", fontWeight = FontWeight.Bold) }
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { sensitivity = (sensitivity - 0.1f).coerceAtLeast(0.1f); runCount() },
                            enabled = samples.isNotEmpty() && !loading,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f)
                        ) { Text("Achou demais") }
                        OutlinedButton(
                            onClick = { sensitivity = (sensitivity + 0.1f).coerceAtMost(0.9f); runCount() },
                            enabled = samples.isNotEmpty() && !loading,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f)
                        ) { Text("Achou de menos") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
private fun OptionChip(text: String, on: Boolean, color: Color, onClick: () -> Unit) {
    Text(
        text,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = if (on) Color.White else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (on) color else CardColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    )
}

@Composable
private fun EmptyPhotoHelp(color: Color, onCamera: () -> Unit, onGallery: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {
        Text("🍺", fontSize = 64.sp)
        Spacer(Modifier.height(12.dp))
        Text("Fotografe de CIMA", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(6.dp))
        Text(
            "Celular apontado para baixo, sobre o engradado ou o freezer, com as tampinhas aparecendo " +
                "como círculos. Evite a lâmpada do freezer na foto. Se der, conte uma marca por vez.",
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
