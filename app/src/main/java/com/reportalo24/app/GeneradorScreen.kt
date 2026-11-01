package com.reportalo24.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun GeneradorScreen(
    state: AppState,
    inicial: Borrador,
    onGuardar: (Borrador) -> Unit,
    showMessage: (String) -> Unit,
) {
    val context = LocalContext.current

    // Estado editable, se reinicia cuando se carga otro borrador
    var titulo by remember(inicial.id) { mutableStateOf(inicial.titulo) }
    var subtitulo by remember(inicial.id) { mutableStateOf(inicial.subtitulo) }
    var franja by remember(inicial.id) { mutableStateOf(inicial.franja.ifBlank { state.perfil.franjaDefault }) }
    var mostrarFranja by remember(inicial.id) { mutableStateOf(inicial.mostrarFranja) }
    var categoria by remember(inicial.id) { mutableStateOf(inicial.categoria) }
    var formato by remember(inicial.id) { mutableStateOf(inicial.formato) }
    var posicion by remember(inicial.id) { mutableStateOf(50f) }
    var subSize by remember(inicial.id) { mutableStateOf(38f) }
    var imagenUri by remember(inicial.id) { mutableStateOf(inicial.imagenUri) }
    var imgZoom by remember(inicial.id) { mutableStateOf(1f) }

    val foto = remember(imagenUri) { ImageIO.loadBitmap(context, imagenUri.ifBlank { null }) }
    val logo = remember(state.perfil.logoUri) { ImageIO.loadBitmap(context, state.perfil.logoUri.ifBlank { null }) }

    val scope = rememberCoroutineScope()
    var procesandoVideo by remember { mutableStateOf(false) }
    var progresoVideo by remember { mutableStateOf(0f) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) ImageIO.persistImage(context, uri)?.let { imagenUri = it }
    }

    fun opciones(conFoto: Boolean) = PlacaRenderer.Opciones(
        formato = formato,
        foto = foto,
        logo = logo,
        titulo = titulo,
        subtitulo = subtitulo,
        franja = franja,
        mostrarFranja = mostrarFranja,
        categoria = categoria,
        usuario = state.perfil.usuario,
        posicion = posicion.toInt(),
        subSize = subSize.toInt(),
        imgZoom = imgZoom,
        conFoto = conFoto,
    )

    // Vista previa en segundo plano (no bloquea la UI)
    val preview by produceState<Bitmap?>(initialValue = null, titulo, subtitulo, franja, mostrarFranja, categoria, formato, posicion, subSize, imagenUri, imgZoom) {
        value = withContext(Dispatchers.Default) { runCatching { PlacaRenderer.render(opciones(true)) }.getOrNull() }
    }

    fun borradorActual() = inicial.copy(
        franja = franja, mostrarFranja = mostrarFranja, titulo = titulo, subtitulo = subtitulo,
        categoria = categoria, formato = formato, imagenUri = imagenUri,
        copy = inicial.copy.ifBlank { Redactor.redactar(titulo, subtitulo, inicial.fuente, categoria, state.perfil.usuario).copy },
    )

    fun guardarEnGaleria(conFoto: Boolean) {
        val bmp = runCatching { PlacaRenderer.render(opciones(conFoto)) }.getOrNull() ?: return showMessage("No se pudo generar la imagen.")
        val name = "R24_${formato.w}x${formato.h}_${System.currentTimeMillis()}"
        if (ImageIO.saveToGallery(context, bmp, name)) showMessage("✅ Guardada en la galería (Pictures/Reportalo24)")
        else showMessage("No se pudo guardar. Intenta compartir en su lugar.")
    }

    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            procesandoVideo = true; progresoVideo = 0f
            val salida = java.io.File(context.cacheDir, "r24_video_${System.currentTimeMillis()}.mp4")
            val ok = VideoComposer.procesar(
                context, uri, salida,
                { w, h -> PlacaRenderer.render(opciones(false).copy(overrideW = w, overrideH = h)) },
                { p -> progresoVideo = p },
            )
            procesandoVideo = false
            if (ok && salida.exists() && salida.length() > 0) {
                if (ImageIO.saveVideoToGallery(context, salida, "R24_video_${System.currentTimeMillis()}")) showMessage("✅ Video guardado en la galería (Movies/Reportalo24)")
                else { ImageIO.shareFile(context, salida, "video/mp4"); showMessage("Video listo — compártelo") }
            } else showMessage("No se pudo procesar el video. Prueba con un clip más corto (9:16).")
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        // Vista previa
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            preview?.let {
                Image(it.asImageBitmap(), contentDescription = "Vista previa", modifier = Modifier.width(200.dp), contentScale = ContentScale.FillWidth)
            } ?: Text("Escribe un título para ver la placa", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(40.dp))
        }
        Spacer(Modifier.height(16.dp))

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp)) {
                Text("📝 Título (máx. 2 líneas)", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(value = titulo, onValueChange = { titulo = it }, modifier = Modifier.fillMaxWidth(), placeholder = { Text("TERREMOTO 7.5 EN CARACAS") })
                Spacer(Modifier.height(12.dp))
                Text("✍️ Subtítulo (**palabra** sale en rojo)", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(value = subtitulo, onValueChange = { subtitulo = it }, modifier = Modifier.fillMaxWidth(), placeholder = { Text("Descripción breve...") })
            }
        }
        Spacer(Modifier.height(12.dp))

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🔴 Franja roja", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                    Switch(checked = mostrarFranja, onCheckedChange = { mostrarFranja = it })
                }
                if (mostrarFranja) {
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(value = franja, onValueChange = { franja = it }, modifier = Modifier.fillMaxWidth(), placeholder = { Text("ÚLTIMA HORA") })
                }
                Spacer(Modifier.height(12.dp))
                Text("🏷️ Categoría", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                    Categorias.lista.forEach { cat ->
                        FilterChip(
                            selected = categoria == cat,
                            onClick = { categoria = cat },
                            label = { Text(if (cat == "NINGUNA") "Ninguna" else cat.lowercase().replaceFirstChar { it.uppercase() }) },
                            modifier = Modifier.padding(end = 8.dp),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp)) {
                Text("📐 Formato", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                    FormatoPlaca.entries.forEach { f ->
                        FilterChip(selected = formato == f, onClick = { formato = f }, label = { Text(f.etiqueta) }, modifier = Modifier.padding(end = 8.dp))
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("📷 Foto", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(6.dp))
                OutlinedButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (imagenUri.isBlank()) "📁 Elegir imagen" else "✓ Imagen cargada — cambiar")
                }
                if (foto != null) {
                    Spacer(Modifier.height(12.dp))
                    Text("🔎 Acercar foto: ${(imgZoom * 100).toInt()}%", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    Slider(value = imgZoom, onValueChange = { imgZoom = it }, valueRange = 1f..2.6f)
                }
                Spacer(Modifier.height(8.dp))
                Text("↕️ Posición del texto: ${posicion.toInt()}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                Slider(value = posicion, onValueChange = { posicion = it }, valueRange = 0f..100f)
                Text("🔡 Tamaño del subtítulo: ${subSize.toInt()}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                Slider(value = subSize, onValueChange = { subSize = it }, valueRange = 24f..54f)
            }
        }
        Spacer(Modifier.height(12.dp))

        Button(onClick = { onGuardar(borradorActual()); showMessage("Borrador guardado") }, modifier = Modifier.fillMaxWidth()) { Text("💾 Guardar borrador") }
        Spacer(Modifier.height(8.dp))
        Button(onClick = { guardarEnGaleria(true) }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = R24Green)) { Text("⬇️ Descargar PNG (con foto)") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = { guardarEnGaleria(false) }, modifier = Modifier.fillMaxWidth()) { Text("🎬 PNG transparente (para video)") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = {
            val bmp = runCatching { PlacaRenderer.render(opciones(true)) }.getOrNull() ?: return@OutlinedButton showMessage("No se pudo generar.")
            ImageIO.shareImage(context, bmp, "R24_${System.currentTimeMillis()}", borradorActual().copy)
        }, modifier = Modifier.fillMaxWidth()) { Text("📤 Compartir placa + copy") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = {
                if (titulo.isBlank()) return@OutlinedButton showMessage("Escribe un título primero")
                videoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
            },
            enabled = !procesandoVideo,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("🎬 Poner sobre un video (subir)") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = {
            val copy = borradorActual().copy
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("copy", copy))
            showMessage("Copy copiado al portapapeles")
        }, modifier = Modifier.fillMaxWidth()) { Text("📋 Copiar copy") }
        Spacer(Modifier.height(24.dp))
    }

    if (procesandoVideo) {
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            title = { Text("🎬 Procesando video...") },
            text = {
                Column {
                    LinearProgressIndicator(progress = { progresoVideo }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    Text("${(progresoVideo * 100).toInt()}%  ·  no cierres la app")
                }
            },
        )
    }
}
