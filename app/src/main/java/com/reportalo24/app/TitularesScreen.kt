package com.reportalo24.app

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TitularesScreen(state: AppState, onArmar: (Borrador) -> Unit, showMessage: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var tema by remember { mutableStateOf("") }
    var franjaM by remember { mutableStateOf(state.perfil.franjaDefault) }
    var tituloM by remember { mutableStateOf("") }
    var subtituloM by remember { mutableStateOf("") }
    var catM by remember { mutableStateOf("NINGUNA") }
    var cargandoIA by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // Modo "Redactar": texto pegado o captura leída con OCR + instrucciones libres.
    var textoLibre by remember { mutableStateOf("") }
    var instrucciones by remember { mutableStateOf("") }
    var leyendoCaptura by remember { mutableStateOf(false) }
    var redactandoLibre by remember { mutableStateOf(false) }

    fun construir(res: Redactor.Resultado, fuente: String, link: String) = Borrador(
        franja = res.franja,
        titulo = res.titulo,
        subtitulo = res.subtitulo,
        categoria = res.categoria,
        copy = res.copy,
        hashtags = res.hashtags.joinToString(" "),
        fuente = fuente,
        link = link,
    )

    val capturaPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            leyendoCaptura = true
            val leido = runCatching {
                val bmp = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                    ?: error("no se pudo abrir la imagen")
                Ocr.leer(bmp)
            }
            leyendoCaptura = false
            leido.onSuccess { t ->
                if (t.isBlank()) {
                    showMessage("No encontré texto en la captura")
                } else {
                    textoLibre = if (textoLibre.isBlank()) t.trim() else textoLibre.trim() + "\n\n" + t.trim()
                    showMessage("✅ Texto leído. Revísalo antes de redactar")
                }
            }.onFailure { showMessage("No pude leer la captura") }
        }
    }

    fun redactarLibre() {
        val texto = textoLibre.trim()
        if (texto.isBlank()) { showMessage("Pega el texto o sube una captura"); return }
        val lineas = texto.lines().filter { it.isNotBlank() }
        val primera = lineas.firstOrNull().orEmpty().take(200)
        val resto = lineas.drop(1).joinToString(" ")
        val usaIA = state.config.iaActiva && state.config.groqApiKey.isNotBlank()
        if (!usaIA) {
            onArmar(construir(Redactor.redactar(primera, resto, "", null, state.perfil.usuario), "", ""))
            showMessage("IA apagada: usé la plantilla")
            return
        }
        scope.launch {
            redactandoLibre = true
            val res = runCatching {
                GroqRedactor.redactar(
                    state.config, "", texto.take(3500), "", state.perfil.usuario,
                    instrucciones = instrucciones, modoLibre = true,
                )
            }
            redactandoLibre = false
            val r = res.getOrElse {
                showMessage("IA no disponible (${it.message?.take(50) ?: "error"}). Usé plantilla.")
                Redactor.redactar(primera, resto, "", null, state.perfil.usuario)
            }
            onArmar(construir(r, "", ""))
        }
    }

    fun armarDesde(titulo: String, resumen: String, fuente: String, link: String) {
        val usaIA = state.config.iaActiva && state.config.groqApiKey.isNotBlank()
        if (!usaIA) {
            onArmar(construir(Redactor.redactar(titulo, resumen, fuente, null, state.perfil.usuario), fuente, link))
            return
        }
        scope.launch {
            cargandoIA = true
            showMessage("✨ Redactando con IA...")
            val res = runCatching { GroqRedactor.redactar(state.config, titulo, resumen, fuente, state.perfil.usuario) }
            cargandoIA = false
            val r = res.getOrElse {
                showMessage("IA no disponible (${it.message?.take(60) ?: "error"}). Usé plantilla.")
                Redactor.redactar(titulo, resumen, fuente, null, state.perfil.usuario)
            }
            onArmar(construir(r, fuente, link))
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (cargandoIA) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    CircularProgressIndicator()
                    Spacer(Modifier.width(12.dp))
                    Text("Redactando con IA...", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Tema o palabra clave (opcional)", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = tema,
                        onValueChange = { tema = it },
                        singleLine = true,
                        placeholder = { Text("Ej: dólar, Maduro, apagón...") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (tema.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Redactor.sugerencias(tema).forEach { s ->
                            Text(
                                "💡 $s",
                                modifier = Modifier.fillMaxWidth().clickable { tituloM = s }.padding(vertical = 8.dp),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            HorizontalDivider()
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = {
                            scope.launch {
                                state.cargandoNoticias = true
                                state.mensajeNoticias = null
                                val r = Rss.buscar(state.fuentes, tema)
                                state.noticias.clear(); state.noticias.addAll(r)
                                state.cargandoNoticias = false
                                if (r.isEmpty()) state.mensajeNoticias = "No se encontraron titulares (revisa tu internet o el tema)."
                            }
                        },
                        enabled = !state.cargandoNoticias,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(if (state.cargandoNoticias) "Buscando..." else "🔎 Buscar noticias") }
                    if (state.cargandoNoticias) {
                        Spacer(Modifier.height(10.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { CircularProgressIndicator() }
                    }
                    state.mensajeNoticias?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(16.dp)) {
                    Text("🧾 Redactar desde texto o captura", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                    Text("Pega el texto o sube una captura. La IA lo redacta tal como le pidas.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = textoLibre,
                        onValueChange = { textoLibre = it },
                        label = { Text("Texto de la noticia") },
                        minLines = 4,
                        maxLines = 10,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { capturaPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        enabled = !leyendoCaptura && !redactandoLibre,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(if (leyendoCaptura) "Leyendo captura..." else "📷 Subir captura") }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = instrucciones,
                        onValueChange = { instrucciones = it },
                        label = { Text("Instrucciones (opcional)") },
                        placeholder = { Text("Ej: más corto, tono sarcástico, para Instagram") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = { redactarLibre() },
                        enabled = !redactandoLibre && !leyendoCaptura,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(if (redactandoLibre) "Redactando..." else "✨ Redactar como te lo pido") }
                    Text("No busca ni verifica nada: redacta con lo que tú entregas.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(16.dp)) {
                    Text("✍️ Redactar yo mismo (formato R24)", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(value = franjaM, onValueChange = { franjaM = it }, label = { Text("Franja (el tema, MAYÚSCULAS)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = tituloM, onValueChange = { tituloM = it }, label = { Text("Título") }, modifier = Modifier.fillMaxWidth())
                    Text("Sentencia de 4 a 8 palabras que juzga y señala al responsable.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = subtituloM, onValueChange = { subtituloM = it }, label = { Text("Subtítulo") }, modifier = Modifier.fillMaxWidth())
                    Text("El dato que duele, con cifra o fecha. Usa **palabra** para resaltarla en rojo.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(10.dp))
                    Text("Categoría", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                        Categorias.lista.forEach { c ->
                            FilterChip(
                                selected = catM == c,
                                onClick = { catM = c },
                                label = { Text(if (c == "NINGUNA") "Ninguna" else c.lowercase().replaceFirstChar { it.uppercase() }) },
                                modifier = Modifier.padding(end = 8.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = {
                            if (tituloM.isBlank()) { showMessage("Escribe al menos el título"); return@OutlinedButton }
                            val cat = if (catM == "NINGUNA") Redactor.detectarCat("$tituloM $subtituloM") else catM
                            onArmar(
                                Borrador(
                                    franja = franjaM.ifBlank { state.perfil.franjaDefault },
                                    mostrarFranja = franjaM.isNotBlank(),
                                    titulo = tituloM,
                                    subtitulo = subtituloM,
                                    categoria = catM,
                                    copy = Redactor.copyManual(tituloM, subtituloM, catM, state.perfil.usuario),
                                    hashtags = Redactor.hashtags(cat).joinToString(" "),
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("🎨 Usar en el generador") }
                    Text("Tu texto pasa tal cual al generador, sin que la IA lo reescriba.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        if (state.noticias.isNotEmpty()) {
            item { Text("📰 Titulares encontrados", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground) }
        }

        items(state.noticias) { n ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(14.dp)) {
                    Text(n.titulo, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    val fecha = if (n.fechaMillis > 0) " · " + SimpleDateFormat("dd MMM", Locale("es", "VE")).format(Date(n.fechaMillis)) else ""
                    Text("📰 ${n.fuente}$fecha", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (n.resumen.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(n.resumen, style = MaterialTheme.typography.bodySmall, maxLines = 3)
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(onClick = { armarDesde(n.titulo, n.resumen, n.fuente, n.link) }, modifier = Modifier.fillMaxWidth()) {
                        Text("✨ Armar placa con esta")
                    }
                }
            }
        }
    }
}
