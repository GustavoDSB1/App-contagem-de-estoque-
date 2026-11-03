package com.estoque.contagem.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.estoque.contagem.data.CompanySettings
import com.estoque.contagem.data.PhotoChange
import com.estoque.contagem.data.ProductImages
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Logo da empresa inteiro (sem recorte), num quadrado branco de cantos arredondados. */
@Composable
fun CompanyLogoBox(logo: ImageBitmap?, size: Dp, placeholder: String = "🏢") {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(size / 4))
            .background(Color.White)
            .padding(size / 12)
    ) {
        if (logo != null) {
            Image(bitmap = logo, contentDescription = "Logo", contentScale = ContentScale.Fit)
        } else {
            Text(placeholder, fontSize = (size.value * 0.45f).sp)
        }
    }
}

/** Editar nome, CNPJ/CPF e logo da empresa. */
@Composable
fun CompanyDialog(
    company: CompanySettings,
    onDismiss: () -> Unit,
    onSave: (name: String, document: String, logo: PhotoChange) -> Unit
) {
    var name by remember { mutableStateOf(company.name) }
    var document by remember { mutableStateOf(company.document) }
    var logo by remember { mutableStateOf<PhotoChange>(PhotoChange.Keep) }
    var processing by remember { mutableStateOf(false) }
    var logoError by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentLogo = rememberProductImage(company.logoFile)
    val newLogo = (logo as? PhotoChange.New)?.bitmap
    val newLogoImage = remember(newLogo) { newLogo?.asImageBitmap() }
    val shownLogo = when (logo) {
        is PhotoChange.New -> newLogoImage
        PhotoChange.Keep -> currentLogo
        PhotoChange.Remove -> null
    }

    fun useImage(uri: Uri) {
        scope.launch {
            processing = true
            logoError = false
            val bitmap = withContext(Dispatchers.IO) {
                runCatching { ProductImages.decodeLogo(context, uri) }.getOrNull()
            }
            processing = false
            if (bitmap != null) logo = PhotoChange.New(bitmap) else logoError = true
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) useImage(uri)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("🏢 Sua empresa", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    if (processing) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(96.dp)
                                .clip(RoundedCornerShape(24.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) { CircularProgressIndicator(modifier = Modifier.size(32.dp)) }
                    } else {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(26.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .padding(2.dp)
                        ) { CompanyLogoBox(shownLogo, 96.dp) }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f)
                    ) { Text(if (shownLogo == null) "🖼️ Escolher logo" else "🖼️ Trocar logo") }
                    if (shownLogo != null) {
                        TextButton(
                            onClick = {
                                logo = if (company.logoFile != null) PhotoChange.Remove else PhotoChange.Keep
                            }
                        ) { Text("Remover") }
                    }
                }
                if (logoError) {
                    Text(
                        "Não foi possível usar essa imagem. Tente outra.",
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp
                    )
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(60) },
                    label = { Text("Nome da empresa") },
                    placeholder = { Text("Ex.: Mercadinho Bom Preço") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Next
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = document,
                    onValueChange = { document = it.filter { c -> c.isDigit() || c in "./-" }.take(20) },
                    label = { Text("CNPJ ou CPF (opcional)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "O nome e o logo aparecem no topo do app e no cabeçalho da planilha Excel.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                enabled = !processing,
                onClick = { onSave(name, document, logo) },
                shape = RoundedCornerShape(12.dp)
            ) { Text("Salvar", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
