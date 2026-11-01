package com.reportalo24.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Lee titulares directamente de los feeds RSS (la app nativa no tiene el bloqueo
 * CORS del navegador, así que no hace falta servidor).
 */
object Rss {

    suspend fun buscar(fuentes: List<FuenteRss>, tema: String): List<Noticia> = withContext(Dispatchers.IO) {
        val activas = fuentes.filter { it.activa }
        val todas = activas.flatMap { runCatching { traer(it) }.getOrDefault(emptyList()) }
        val t = tema.trim()
        val filtradas = if (t.isBlank()) todas
        else todas.filter { (it.titulo + " " + it.resumen).contains(t, ignoreCase = true) }
        filtradas.sortedByDescending { it.fechaMillis }.take(40)
    }

    private fun traer(f: FuenteRss): List<Noticia> {
        val conn = (URL(f.url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 12000
            readTimeout = 12000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Reportalo24/1.0")
        }
        return try {
            if (conn.responseCode !in 200..299) return emptyList()
            val xml = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            parse(xml, f.nombre)
        } catch (e: Exception) {
            emptyList()
        } finally {
            conn.disconnect()
        }
    }

    private val formatosFecha = listOf(
        SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.ENGLISH),
        SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.ENGLISH),
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.ENGLISH),
    )

    private fun parseFecha(s: String): Long {
        if (s.isBlank()) return 0L
        for (f in formatosFecha) {
            runCatching { return f.parse(s)?.time ?: 0L }
        }
        return 0L
    }

    private fun limpiar(s: String): String =
        s.replace(Regex("<!\\[CDATA\\[|]]>"), "")
            .replace(Regex("<[^>]+>"), " ")
            .replace(Regex("&#8217;|&#8216;|&#039;|&apos;"), "'")
            .replace(Regex("&#8220;|&#8221;|&quot;"), "\"")
            .replace("&#8230;", "…")
            .replace("&amp;", "&")
            .replace("&nbsp;", " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun campo(seg: String, tag: String): String {
        val m = Regex("<$tag[^>]*>([\\s\\S]*?)</$tag>", RegexOption.IGNORE_CASE).find(seg)
        return if (m != null) limpiar(m.groupValues[1]) else ""
    }

    fun parse(xml: String, fuente: String): List<Noticia> {
        val items = xml.split(Regex("<item[\\s>]", RegexOption.IGNORE_CASE)).drop(1)
        val out = ArrayList<Noticia>()
        for (b in items) {
            val fin = b.indexOf("</item>")
            val seg = if (fin >= 0) b.substring(0, fin) else b
            val titulo = campo(seg, "title")
            if (titulo.isBlank()) continue
            val link = campo(seg, "link")
            val fecha = campo(seg, "pubDate")
            val desc = campo(seg, "description").ifBlank { campo(seg, "content:encoded") }
            out.add(
                Noticia(
                    titulo = titulo,
                    fuente = fuente,
                    fechaMillis = parseFecha(fecha),
                    link = link,
                    resumen = desc.take(320),
                )
            )
        }
        return out
    }
}
