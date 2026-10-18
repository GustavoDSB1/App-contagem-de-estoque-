package com.estoque.contagem.ui.components

import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.estoque.contagem.consumo.Money
import com.estoque.contagem.data.PersonType
import com.estoque.contagem.data.PhotoChange
import com.estoque.contagem.data.Product
import com.estoque.contagem.data.ProductImages
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.estoque.contagem.ui.onlyDigits
import com.estoque.contagem.ui.theme.productColor

val ProductEmojis = listOf(
    "📦", "🥤", "🧃", "🍺", "🥛", "☕",
    "🍞", "🍚", "🍫", "🍬", "🍪", "🧀",
    "🍎", "🥩", "🥫", "🧴", "🧼", "🧻",
    "💊", "👕", "🔧", "💡", "🔋", "🖊️"
)

/** Carrega a foto do produto (com cache), sem travar a tela. */
@Composable
fun rememberProductImage(fileName: String?): ImageBitmap? {
    val context = LocalContext.current
    val image by produceState(initialValue = fileName?.let { ProductImages.cached(it) }, fileName) {
        value = if (fileName == null) null
        else ProductImages.cached(fileName) ?: withContext(Dispatchers.IO) { ProductImages.load(context, fileName) }
    }
    return image
}

@Composable
fun ProductAvatar(product: Product, size: Dp, onDark: Boolean = false) {
    val color = productColor(product.colorIndex)
    val photo = rememberProductImage(product.imageFile)
    if (photo != null) {
        PhotoCircle(photo, size, ringColor = if (onDark) Color.White else color.copy(alpha = 0.45f))
        return
    }
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(if (onDark) Color.White else color.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center
    ) {
        Text(product.emoji, fontSize = (size.value * 0.5f).sp)
    }
}

/** Foto redonda com contorno colorido, no mesmo tamanho dos ícones. */
@Composable
fun PhotoCircle(photo: ImageBitmap, size: Dp, ringColor: Color) {
    Image(
        bitmap = photo,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .border(if (size >= 64.dp) 3.dp else 2.dp, ringColor, CircleShape)
    )
}

@Composable
fun StatusPill(text: String, background: Color, content: Color) {
    Text(
        text = text,
        color = content,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(background)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

/**
 * Campo de dinheiro no estilo "caixa eletrônico": digitar 9 0 0 mostra R$ 9,00.
 * Não precisa digitar vírgula.
 */
@Composable
fun MoneyField(
    cents: Long,
    onChange: (Long) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    imeAction: ImeAction = ImeAction.Done
) {
    val text = Money.formatWithSymbol(cents)
    OutlinedTextField(
        value = TextFieldValue(text, selection = TextRange(text.length)),
        onValueChange = { v ->
            val digits = v.text.filter { it.isDigit() }.trimStart('0').take(9)
            onChange(digits.toLongOrNull() ?: 0L)
        },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = imeAction),
        textStyle = MaterialTheme.typography.titleLarge.copy(textAlign = TextAlign.Center),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier.fillMaxWidth()
    )
}

@Composable
private fun EmojiPicker(selected: String, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ProductEmojis.chunked(6).forEach { row ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                row.forEach { emoji ->
                    val isSelected = emoji == selected
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                            .border(
                                width = 2.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                shape = CircleShape
                            )
                            .clickable { onSelect(emoji) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(emoji, fontSize = 20.sp)
                    }
                }
            }
        }
    }
}

/**
 * Janela para criar ou editar um produto.
 * [onSave] retorna false quando o nome já existe.
 */
@Composable
fun ProductFormDialog(
    title: String,
    initialName: String = "",
    initialEmoji: String = ProductEmojis.first(),
    initialPriceCents: Long = 0,
    initialImageFile: String? = null,
    askInitialQuantity: Boolean,
    onDismiss: () -> Unit,
    onSave: (name: String, emoji: String, initialQuantity: Long, priceCents: Long, photo: PhotoChange) -> Boolean
) {
    var name by remember { mutableStateOf(initialName) }
    var emoji by remember { mutableStateOf(initialEmoji) }
    var quantity by remember { mutableStateOf("") }
    var priceCents by remember { mutableStateOf(initialPriceCents) }
    var duplicate by remember { mutableStateOf(false) }

    // Foto: mantém a atual, remove, ou usa uma nova (galeria/câmera)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var photo by remember { mutableStateOf<PhotoChange>(PhotoChange.Keep) }
    var processing by remember { mutableStateOf(false) }
    var photoError by remember { mutableStateOf(false) }
    val currentPhoto = rememberProductImage(initialImageFile)
    val newPhoto = (photo as? PhotoChange.New)?.bitmap
    val newPhotoImage = remember(newPhoto) { newPhoto?.asImageBitmap() }
    val shownPhoto: ImageBitmap? = when (photo) {
        is PhotoChange.New -> newPhotoImage
        PhotoChange.Keep -> currentPhoto
        PhotoChange.Remove -> null
    }
    fun clearPhoto() {
        photo = if (initialImageFile != null) PhotoChange.Remove else PhotoChange.Keep
    }
    fun useImage(uri: Uri) {
        scope.launch {
            processing = true
            photoError = false
            val bitmap = withContext(Dispatchers.IO) {
                runCatching { ProductImages.decodeIcon(context, uri) }.getOrNull()
            }
            processing = false
            if (bitmap != null) photo = PhotoChange.New(bitmap) else photoError = true
        }
    }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) useImage(uri)
    }
    val cameraUri = remember {
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", ProductImages.cameraTempFile(context))
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) useImage(cameraUri)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    when {
                        processing -> Box(
                            modifier = Modifier
                                .size(88.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) { CircularProgressIndicator(modifier = Modifier.size(32.dp)) }

                        shownPhoto != null -> PhotoCircle(shownPhoto, 88.dp, MaterialTheme.colorScheme.primary)

                        else -> Box(
                            modifier = Modifier
                                .size(88.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) { Text(emoji, fontSize = 44.sp) }
                    }
                }
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(60); duplicate = false },
                    label = { Text("Nome do produto") },
                    placeholder = { Text("Ex.: Arroz 5kg") },
                    singleLine = true,
                    isError = duplicate,
                    supportingText = if (duplicate) {
                        { Text("Já existe um produto com esse nome") }
                    } else null,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Next
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                Text("Foto do produto", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            galleryLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f)
                    ) { Text("🖼️ Galeria") }
                    OutlinedButton(
                        onClick = {
                            try {
                                cameraLauncher.launch(cameraUri)
                            } catch (_: ActivityNotFoundException) {
                                photoError = true
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f)
                    ) { Text("📷 Câmera") }
                }
                if (photoError) {
                    Text(
                        "Não foi possível usar essa imagem. Tente outra.",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp
                    )
                }
                if (shownPhoto != null) {
                    TextButton(onClick = { clearPhoto() }, modifier = Modifier.fillMaxWidth()) {
                        Text("Remover foto e usar ícone")
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    if (shownPhoto != null) "Ou use um ícone" else "Ou escolha um ícone",
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(8.dp))
                EmojiPicker(
                    selected = if (shownPhoto != null) "" else emoji,
                    onSelect = { emoji = it; clearPhoto() }
                )
                Spacer(Modifier.height(16.dp))
                MoneyField(
                    cents = priceCents,
                    onChange = { priceCents = it },
                    label = "Valor de venda (opcional)",
                    imeAction = if (askInitialQuantity) ImeAction.Next else ImeAction.Done
                )
                Text(
                    "Usado só na aba Consumo. Não vai para a planilha.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (askInitialQuantity) {
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = quantity,
                        onValueChange = { quantity = it.onlyDigits() },
                        label = { Text("Quantidade já contada (opcional)") },
                        placeholder = { Text("0") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done
                        ),
                        textStyle = MaterialTheme.typography.titleLarge.copy(textAlign = TextAlign.Center),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = name.isNotBlank() && !processing,
                onClick = {
                    val ok = onSave(name.trim(), emoji, quantity.toLongOrNull() ?: 0L, priceCents, photo)
                    if (!ok) duplicate = true
                },
                shape = RoundedCornerShape(12.dp)
            ) { Text("Salvar", fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@Composable
fun ConfirmDialog(
    emoji: String,
    title: String,
    message: String,
    confirmText: String,
    confirmColor: Color,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Text(emoji, fontSize = 40.sp) },
        title = { Text(title, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center) },
        text = { Text(message, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = confirmColor),
                shape = RoundedCornerShape(12.dp)
            ) { Text(confirmText, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

/** Cadastro rápido de funcionário ou diarista. [onSave] retorna false se o nome já existir. */
@Composable
fun PersonFormDialog(
    type: PersonType,
    onDismiss: () -> Unit,
    onSave: (name: String) -> Boolean
) {
    var name by remember { mutableStateOf("") }
    var duplicate by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Text(type.emoji, fontSize = 40.sp) },
        title = { Text("Cadastrar ${type.label}", fontWeight = FontWeight.Bold) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(40); duplicate = false },
                label = { Text("Nome") },
                placeholder = { Text(if (type == PersonType.EMPLOYEE) "Ex.: Ricardo" else "Ex.: Andressa") },
                singleLine = true,
                isError = duplicate,
                supportingText = if (duplicate) {
                    { Text("Esse nome já está cadastrado") }
                } else null,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Done
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                enabled = name.isNotBlank(),
                onClick = { if (!onSave(name.trim())) duplicate = true },
                shape = RoundedCornerShape(12.dp)
            ) { Text("Salvar", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
