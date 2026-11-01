@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.reportalo24.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val sharedText = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)
        setContent { Reportalo24App(sharedText) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            intent.getStringExtra(Intent.EXTRA_TEXT)?.let { sharedText.value = it }
        }
    }
}

private data class Pestana(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

private val pestanas = listOf(
    Pestana("Titulares", Icons.Rounded.Search),
    Pestana("Generar", Icons.Rounded.Edit),
    Pestana("Borradores", Icons.Rounded.Style),
    Pestana("Marca", Icons.Rounded.Storefront),
)

@Composable
fun Reportalo24App(sharedText: MutableState<String?>) {
    val context = LocalContext.current
    val state = remember { AppState(context.applicationContext) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val showMessage: (String) -> Unit = { msg -> scope.launch { snackbar.currentSnackbarData?.dismiss(); snackbar.showSnackbar(msg) } }

    var tab by remember { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf(Borrador()) }
    var confirmDelete by remember { mutableStateOf<Borrador?>(null) }

    // Contenido compartido desde otras apps → arma una placa y abre el generador
    LaunchedEffect(sharedText.value) {
        val txt = sharedText.value ?: return@LaunchedEffect
        val lineas = txt.trim().lines().filter { it.isNotBlank() }
        val titulo = lineas.firstOrNull().orEmpty()
        val resumen = lineas.drop(1).joinToString(" ")
        val res = Redactor.redactar(titulo, resumen, "", null, state.perfil.usuario)
        editing = Borrador(
            franja = res.franja, titulo = res.titulo, subtitulo = res.subtitulo,
            categoria = res.categoria, copy = res.copy, hashtags = res.hashtags.joinToString(" "),
        )
        tab = 1
        sharedText.value = null
    }

    Reportalo24Theme {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text(if (tab == 0) "Repórtalo 24" else pestanas[tab].label, fontWeight = FontWeight.Bold) },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = androidx.compose.ui.graphics.Color.White,
                    ),
                )
            },
            bottomBar = {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    pestanas.forEachIndexed { index, p ->
                        NavigationBarItem(
                            selected = tab == index,
                            onClick = { tab = index },
                            icon = { Icon(p.icon, p.label) },
                            label = { Text(p.label, maxLines = 1) },
                        )
                    }
                }
            },
        ) { padding ->
            Box(Modifier.padding(padding).fillMaxSize()) {
                when (tab) {
                    0 -> TitularesScreen(state = state, onArmar = { b -> editing = b; tab = 1 }, showMessage = showMessage)
                    1 -> GeneradorScreen(
                        state = state,
                        inicial = editing,
                        onGuardar = { b -> editing = state.saveBorrador(b) },
                        showMessage = showMessage,
                    )
                    2 -> BorradoresScreen(
                        state = state,
                        onAbrir = { b -> editing = b; tab = 1 },
                        onEliminar = { confirmDelete = it },
                    )
                    3 -> MarcaScreen(state = state, showMessage = showMessage)
                }
            }
        }

        confirmDelete?.let { b ->
            AlertDialog(
                onDismissRequest = { confirmDelete = null },
                title = { Text("Eliminar borrador") },
                text = { Text("¿Eliminar \"${b.titulo.replace("\n", " ").ifBlank { "este borrador" }}\"? No se puede deshacer.") },
                confirmButton = {
                    TextButton(onClick = { state.deleteBorrador(b); confirmDelete = null; showMessage("Borrador eliminado") }) {
                        Text("Eliminar", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancelar") } },
            )
        }
    }
}
