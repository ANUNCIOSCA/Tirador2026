package com.reportalo24.app

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

/** Copia de seguridad completa en JSON (restaurable), igual que la Agenda. */
object Backup {
    const val APP_VERSION = "1.0.0"

    fun exportJson(context: Context, state: AppState): File {
        val root = JSONObject().apply {
            put("app", "Reportalo24")
            put("version", APP_VERSION)
            put("exportedAt", System.currentTimeMillis())
            put("borradores", JSONArray(state.borradores.map { BorradorStore.toJson(it) }))
            put("fuentes", JSONArray(state.fuentes.map { FuenteStore.toJson(it) }))
            put("perfil", PerfilStore.toJson(state.perfil))
            put("config", JSONObject().apply {
                put("iaActiva", state.config.iaActiva)
                put("groqApiKey", state.config.groqApiKey)
                put("groqModel", state.config.groqModel)
            })
        }
        val dir = File(context.cacheDir, "compartir").apply { mkdirs() }
        val file = File(dir, "reportalo24_backup.json")
        FileOutputStream(file).use { it.write(root.toString(2).toByteArray(Charsets.UTF_8)) }
        return file
    }

    /** Restaura desde un archivo JSON. Devuelve cuántos borradores entraron. */
    fun importJson(context: Context, state: AppState, uri: Uri): Int {
        val text = context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
            ?: return 0
        val root = JSONObject(text)

        root.optJSONObject("perfil")?.let { state.updatePerfil(PerfilStore.fromJson(it)) }

        root.optJSONObject("config")?.let {
            state.updateConfig(
                AppConfig(
                    iaActiva = it.optBoolean("iaActiva", false),
                    groqApiKey = it.optString("groqApiKey", ""),
                    // Las copias viejas traen modelos que Groq ya apagó: se pasan al vigente.
                    groqModel = it.optString("groqModel", ModelosGroq.DEFECTO).let { m ->
                        if (ModelosGroq.vigente(m)) m else ModelosGroq.DEFECTO
                    },
                )
            )
        }

        root.optJSONArray("fuentes")?.let { arr ->
            val fuentes = ArrayList<FuenteRss>()
            for (i in 0 until arr.length()) runCatching { fuentes.add(FuenteStore.fromJson(arr.getJSONObject(i))) }
            if (fuentes.isNotEmpty()) {
                FuenteStore.save(context, fuentes)
            }
        }

        var count = 0
        root.optJSONArray("borradores")?.let { arr ->
            val actuales = state.borradores.map { it.id }.toHashSet()
            val nuevos = ArrayList<Borrador>()
            for (i in 0 until arr.length()) runCatching {
                val b = BorradorStore.fromJson(arr.getJSONObject(i))
                if (b.id !in actuales) { nuevos.add(b); count++ }
            }
            if (nuevos.isNotEmpty()) {
                val todos = (state.borradores + nuevos).sortedByDescending { it.updatedAtMillis }
                BorradorStore.save(context, todos)
            }
        }

        state.reloadAll()
        return count
    }
}
