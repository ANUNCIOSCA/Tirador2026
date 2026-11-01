package com.reportalo24.app

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun MarcaScreen(state: AppState, showMessage: (String) -> Unit) {
    val context = LocalContext.current

    var nombreMedio by remember { mutableStateOf(state.perfil.nombreMedio) }
    var usuario by remember { mutableStateOf(state.perfil.usuario) }
    var franjaDefault by remember { mutableStateOf(state.perfil.franjaDefault) }
    var iaActiva by remember { mutableStateOf(state.config.iaActiva) }
    var groqKey by remember { mutableStateOf(state.config.groqApiKey) }
    var groqModel by remember { mutableStateOf(state.config.groqModel) }
    var nuevaFuenteNombre by remember { mutableStateOf("") }
    var nuevaFuenteUrl by remember { mutableStateOf("") }

    val logoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) ImageIO.persistImage(context, uri)?.let {
            state.updatePerfil(state.perfil.copy(logoUri = it)); showMessage("Logo actualizado")
        }
    }
    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val n = Backup.importJson(context, state, uri)
            showMessage("Restaurado: $n borradores nuevos")
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp)) {
                Text("🏷️ Marca", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(value = nombreMedio, onValueChange = { nombreMedio = it }, label = { Text("Nombre del medio") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = usuario, onValueChange = { usuario = it }, label = { Text("Usuario (@)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = franjaDefault, onValueChange = { franjaDefault = it }, label = { Text("Franja por defecto") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = { logoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (state.perfil.logoUri.isBlank()) "📁 Elegir logo" else "✓ Logo cargado — cambiar")
                }
                Spacer(Modifier.height(10.dp))
                Button(onClick = {
                    state.updatePerfil(state.perfil.copy(nombreMedio = nombreMedio, usuario = usuario, franjaDefault = franjaDefault))
                    showMessage("Marca guardada")
                }, modifier = Modifier.fillMaxWidth()) { Text("💾 Guardar marca") }
            }
        }
        Spacer(Modifier.height(12.dp))

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp)) {
                Text("📡 Fuentes de noticias (RSS)", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(8.dp))
                state.fuentes.forEach { f ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(f.nombre, fontWeight = FontWeight.SemiBold)
                            Text(f.url, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                        Switch(checked = f.activa, onCheckedChange = { state.toggleFuente(f) })
                        TextButton(onClick = { state.deleteFuente(f) }) { Text("✕", color = MaterialTheme.colorScheme.error) }
                    }
                    HorizontalDivider()
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(value = nuevaFuenteNombre, onValueChange = { nuevaFuenteNombre = it }, label = { Text("Nombre de la fuente") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(value = nuevaFuenteUrl, onValueChange = { nuevaFuenteUrl = it }, label = { Text("URL del RSS (…/feed/)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = {
                    if (nuevaFuenteNombre.isNotBlank() && nuevaFuenteUrl.isNotBlank()) {
                        state.saveFuente(FuenteRss(nombre = nuevaFuenteNombre.trim(), url = nuevaFuenteUrl.trim()))
                        nuevaFuenteNombre = ""; nuevaFuenteUrl = ""; showMessage("Fuente agregada")
                    }
                }, modifier = Modifier.fillMaxWidth()) { Text("➕ Agregar fuente") }
            }
        }
        Spacer(Modifier.height(12.dp))

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🤖 Redacción con IA (Groq)", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                    Switch(checked = iaActiva, onCheckedChange = { iaActiva = it })
                }
                Spacer(Modifier.height(4.dp))
                Text("Con la IA activada, al armar una placa la noticia se redacta sola en tono amarillista. La clave se guarda solo en tu teléfono.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (iaActiva) {
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(value = groqKey, onValueChange = { groqKey = it }, label = { Text("API Key de Groq (gsk_...)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(4.dp))
                    Text("Gratis en console.groq.com/keys", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(10.dp))
                    Text("Modelo", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row {
                        listOf("llama-3.3-70b-versatile" to "Mejor", "llama-3.1-8b-instant" to "Rápido").forEach { (m, etiqueta) ->
                            FilterChip(selected = groqModel == m, onClick = { groqModel = m }, label = { Text(etiqueta) }, modifier = Modifier.padding(end = 8.dp))
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Button(onClick = {
                    state.updateConfig(AppConfig(iaActiva = iaActiva, groqApiKey = groqKey.trim(), groqModel = groqModel))
                    showMessage(if (iaActiva && groqKey.isBlank()) "Activa la IA pero falta la clave de Groq" else "IA configurada")
                }, modifier = Modifier.fillMaxWidth()) { Text("💾 Guardar IA") }
            }
        }
        Spacer(Modifier.height(12.dp))

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp)) {
                Text("💾 Copia de seguridad", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(4.dp))
                Text("Respaldo local de tus borradores, fuentes y marca.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = {
                    val file = Backup.exportJson(context, state)
                    ImageIO.shareFile(context, file, "application/json")
                }, modifier = Modifier.fillMaxWidth()) { Text("⬆️ Guardar copia (JSON)") }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { importPicker.launch(arrayOf("application/json")) }, modifier = Modifier.fillMaxWidth()) { Text("⬇️ Restaurar desde archivo") }
                Spacer(Modifier.height(12.dp))
                Text("Repórtalo 24 · versión ${Backup.APP_VERSION}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
