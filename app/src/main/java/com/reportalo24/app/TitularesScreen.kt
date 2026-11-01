package com.reportalo24.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TitularesScreen(state: AppState, onArmar: (Borrador) -> Unit) {
    val scope = rememberCoroutineScope()
    var tema by remember { mutableStateOf("") }
    var manualTitulo by remember { mutableStateOf("") }
    var manualResumen by remember { mutableStateOf("") }

    fun armarDesde(titulo: String, resumen: String, fuente: String, link: String) {
        val res = Redactor.redactar(titulo, resumen, fuente, null, state.perfil.usuario)
        onArmar(
            Borrador(
                franja = res.franja,
                titulo = res.titulo,
                subtitulo = res.subtitulo,
                categoria = res.categoria,
                copy = res.copy,
                hashtags = res.hashtags.joinToString(" "),
                fuente = fuente,
                link = link,
            )
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
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
                                modifier = Modifier.fillMaxWidth().clickable { manualTitulo = s }.padding(vertical = 8.dp),
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
                    Text("✍️ Escribe tú el titular", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = manualTitulo, onValueChange = { manualTitulo = it }, placeholder = { Text("Titular...") }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = manualResumen, onValueChange = { manualResumen = it }, placeholder = { Text("Resumen o subtítulo (opcional)") }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = { if (manualTitulo.isNotBlank()) armarDesde(manualTitulo, manualResumen, "", "") },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("✨ Armar placa con mi texto") }
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
