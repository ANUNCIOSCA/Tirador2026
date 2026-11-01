package com.reportalo24.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/*
 * Almacenamiento local en SharedPreferences con JSON (mismo patrón que la Agenda).
 * Nada sale del teléfono.
 */

private inline fun <T> readList(context: Context, prefs: String, key: String, parse: (JSONObject) -> T): List<T> =
    runCatching {
        val raw = context.getSharedPreferences(prefs, Context.MODE_PRIVATE).getString(key, "[]") ?: "[]"
        val array = JSONArray(raw)
        buildList {
            for (i in 0 until array.length()) {
                runCatching { parse(array.getJSONObject(i)) }.getOrNull()?.let { add(it) }
            }
        }
    }.getOrDefault(emptyList())

private fun writeList(context: Context, prefs: String, key: String, items: List<JSONObject>) {
    val array = JSONArray()
    items.forEach { array.put(it) }
    context.getSharedPreferences(prefs, Context.MODE_PRIVATE).edit().putString(key, array.toString()).apply()
}

// ---------------------------------------------------------------------------

object BorradorStore {
    const val PREFS = "r24_borradores"
    const val KEY = "borradores"

    fun toJson(b: Borrador) = JSONObject().apply {
        put("id", b.id)
        put("franja", b.franja)
        put("mostrarFranja", b.mostrarFranja)
        put("titulo", b.titulo)
        put("subtitulo", b.subtitulo)
        put("categoria", b.categoria)
        put("copy", b.copy)
        put("hashtags", b.hashtags)
        put("fuente", b.fuente)
        put("link", b.link)
        put("imagenUri", b.imagenUri)
        put("formato", b.formato.name)
        put("estado", b.estado.name)
        put("createdAtMillis", b.createdAtMillis)
        put("updatedAtMillis", b.updatedAtMillis)
    }

    fun fromJson(o: JSONObject) = Borrador(
        id = o.getLong("id"),
        franja = o.optString("franja", "ÚLTIMA HORA"),
        mostrarFranja = o.optBoolean("mostrarFranja", true),
        titulo = o.optString("titulo"),
        subtitulo = o.optString("subtitulo"),
        categoria = o.optString("categoria", "NINGUNA"),
        copy = o.optString("copy"),
        hashtags = o.optString("hashtags"),
        fuente = o.optString("fuente"),
        link = o.optString("link"),
        imagenUri = o.optString("imagenUri"),
        formato = FormatoPlaca.from(o.optString("formato")),
        estado = EstadoBorrador.from(o.optString("estado")),
        createdAtMillis = o.optLong("createdAtMillis", System.currentTimeMillis()),
        updatedAtMillis = o.optLong("updatedAtMillis", System.currentTimeMillis()),
    )

    fun load(context: Context): List<Borrador> =
        readList(context, PREFS, KEY, ::fromJson).sortedByDescending { it.updatedAtMillis }

    fun save(context: Context, items: List<Borrador>) =
        writeList(context, PREFS, KEY, items.map(::toJson))
}

// ---------------------------------------------------------------------------

object FuenteStore {
    const val PREFS = "r24_fuentes"
    const val KEY = "fuentes"

    /** Fuentes RSS iniciales de medios venezolanos. */
    val defaults: List<FuenteRss> = listOf(
        FuenteRss(1, "Efecto Cocuyo", "https://efectococuyo.com/feed/"),
        FuenteRss(2, "La Patilla", "https://www.lapatilla.com/feed/"),
        FuenteRss(3, "Tal Cual", "https://talcualdigital.com/feed/"),
        FuenteRss(4, "Runrun.es", "https://runrun.es/feed/"),
        FuenteRss(5, "El Nacional", "https://www.elnacional.com/feed/"),
        FuenteRss(6, "El Pitazo", "https://elpitazo.net/feed/"),
    )

    fun toJson(f: FuenteRss) = JSONObject().apply {
        put("id", f.id)
        put("nombre", f.nombre)
        put("url", f.url)
        put("activa", f.activa)
    }

    fun fromJson(o: JSONObject) = FuenteRss(
        id = o.getLong("id"),
        nombre = o.optString("nombre", "Fuente"),
        url = o.optString("url"),
        activa = o.optBoolean("activa", true),
    )

    fun load(context: Context): List<FuenteRss> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.contains(KEY)) {
            save(context, defaults)
            return defaults
        }
        return readList(context, PREFS, KEY, ::fromJson).sortedBy { it.id }
    }

    fun save(context: Context, items: List<FuenteRss>) =
        writeList(context, PREFS, KEY, items.map(::toJson))
}

// ---------------------------------------------------------------------------

object ConfigStore {
    const val PREFS = "r24_config"

    fun load(context: Context): AppConfig {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return AppConfig(
            iaActiva = p.getBoolean("iaActiva", false),
            groqApiKey = p.getString("groqApiKey", "") ?: "",
            groqModel = p.getString("groqModel", "llama-3.3-70b-versatile") ?: "llama-3.3-70b-versatile",
        )
    }

    fun save(context: Context, c: AppConfig) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean("iaActiva", c.iaActiva)
            .putString("groqApiKey", c.groqApiKey)
            .putString("groqModel", c.groqModel)
            .apply()
    }
}

object PerfilStore {
    const val PREFS = "r24_perfil"

    fun load(context: Context): PerfilMarca {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return PerfilMarca(
            nombreMedio = p.getString("nombreMedio", "Repórtalo 24") ?: "Repórtalo 24",
            usuario = p.getString("usuario", "@reportalo24") ?: "@reportalo24",
            logoUri = p.getString("logoUri", "") ?: "",
            franjaDefault = p.getString("franjaDefault", "REPORTE DEL DÍA") ?: "REPORTE DEL DÍA",
        )
    }

    fun save(context: Context, m: PerfilMarca) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("nombreMedio", m.nombreMedio)
            .putString("usuario", m.usuario)
            .putString("logoUri", m.logoUri)
            .putString("franjaDefault", m.franjaDefault)
            .apply()
    }

    fun toJson(m: PerfilMarca) = JSONObject().apply {
        put("nombreMedio", m.nombreMedio)
        put("usuario", m.usuario)
        put("logoUri", m.logoUri)
        put("franjaDefault", m.franjaDefault)
    }

    fun fromJson(o: JSONObject) = PerfilMarca(
        nombreMedio = o.optString("nombreMedio", "Repórtalo 24"),
        usuario = o.optString("usuario", "@reportalo24"),
        logoUri = o.optString("logoUri", ""),
        franjaDefault = o.optString("franjaDefault", "REPORTE DEL DÍA"),
    )
}
