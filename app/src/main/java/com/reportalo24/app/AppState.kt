package com.reportalo24.app

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Estado central de la app. Cada operación actualiza la lista en memoria
 * (para que la UI se recomponga) y guarda en disco inmediatamente.
 */
class AppState(private val context: Context) {
    val borradores = mutableStateListOf<Borrador>()
    val fuentes = mutableStateListOf<FuenteRss>()
    var perfil by mutableStateOf(PerfilMarca())
    var config by mutableStateOf(AppConfig())

    // Transitorio: resultados de la última búsqueda de noticias
    val noticias = mutableStateListOf<Noticia>()
    var cargandoNoticias by mutableStateOf(false)
    var mensajeNoticias by mutableStateOf<String?>(null)

    init { reloadAll() }

    fun reloadAll() {
        borradores.clear(); borradores.addAll(BorradorStore.load(context))
        fuentes.clear(); fuentes.addAll(FuenteStore.load(context))
        perfil = PerfilStore.load(context)
        config = ConfigStore.load(context)
    }

    private fun newId(): Long {
        val base = System.currentTimeMillis()
        val used = (borradores.map { it.id } + fuentes.map { it.id }).toHashSet()
        var id = base
        while (id in used) id++
        return id
    }

    // ------------------------------------------------------------- borradores

    private fun persistBorradores() {
        val sorted = borradores.sortedByDescending { it.updatedAtMillis }
        borradores.clear(); borradores.addAll(sorted)
        BorradorStore.save(context, sorted)
    }

    fun saveBorrador(borrador: Borrador): Borrador {
        val b = (if (borrador.id <= 0L) borrador.copy(id = newId()) else borrador)
            .copy(updatedAtMillis = System.currentTimeMillis())
        val idx = borradores.indexOfFirst { it.id == b.id }
        if (idx >= 0) borradores[idx] = b else borradores.add(b)
        persistBorradores()
        return b
    }

    fun deleteBorrador(borrador: Borrador) {
        borradores.removeAll { it.id == borrador.id }
        persistBorradores()
    }

    // ------------------------------------------------------------- fuentes

    fun saveFuente(fuente: FuenteRss): FuenteRss {
        val f = if (fuente.id <= 0L) fuente.copy(id = newId()) else fuente
        val idx = fuentes.indexOfFirst { it.id == f.id }
        if (idx >= 0) fuentes[idx] = f else fuentes.add(f)
        FuenteStore.save(context, fuentes.toList())
        return f
    }

    fun deleteFuente(fuente: FuenteRss) {
        fuentes.removeAll { it.id == fuente.id }
        FuenteStore.save(context, fuentes.toList())
    }

    fun toggleFuente(fuente: FuenteRss) {
        val idx = fuentes.indexOfFirst { it.id == fuente.id }
        if (idx >= 0) fuentes[idx] = fuente.copy(activa = !fuente.activa)
        FuenteStore.save(context, fuentes.toList())
    }

    // ------------------------------------------------------------- perfil

    fun updatePerfil(nuevo: PerfilMarca) {
        perfil = nuevo
        PerfilStore.save(context, nuevo)
    }

    fun updateConfig(nuevo: AppConfig) {
        config = nuevo
        ConfigStore.save(context, nuevo)
    }
}
