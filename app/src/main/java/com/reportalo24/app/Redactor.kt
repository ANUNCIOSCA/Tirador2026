package com.reportalo24.app

/**
 * Redacción amarillista por PLANTILLAS (sin IA todavía — la IA llega en v2).
 * Regla de oro: el dato es sagrado. Toma el titular real y lo pone con filo,
 * sin inventar hechos, y lo atribuye a su fuente.
 */
object Redactor {

    data class Resultado(
        val franja: String,
        val titulo: String,
        val subtitulo: String,
        val categoria: String,
        val copy: String,
        val hashtags: List<String>,
    )

    private data class Cat(val cat: String, val kw: List<String>)

    private val CATS = listOf(
        Cat("ECONOMÍA", listOf("dólar", "dolar", "bcv", "inflación", "inflacion", "salario", "bolívar", "bolivar", "precio", "gasolina", "economía", "economia", "sueldo", "cesta")),
        Cat("DEPORTE", listOf("vinotinto", "béisbol", "beisbol", "fútbol", "futbol", "lvbp", "grandes ligas", "pelota", "magallanes", "leones")),
        Cat("INTERNACIONAL", listOf("eeuu", "estados unidos", "colombia", "onu", "trump", "brasil", "españa", "espana", "migración", "migracion", "frontera", "ue")),
        Cat("POLÍTICA", listOf("maduro", "gobierno", "chavismo", "oposición", "oposicion", "elecciones", "asamblea", "tsj", "psuv", "régimen", "regimen", "diosdado", "decreto", "gaceta", "machado")),
        Cat("NACIONAL", listOf("caracas", "maracaibo", "valencia", "apagón", "apagon", "hospital", "servicios", "agua", "luz", "sucesos", "detenido")),
    )

    // La franja es una etiqueta de URGENCIA, distinta del nombre de la categoría y del título.
    private val FRANJAS = mapOf(
        "POLÍTICA" to "ÚLTIMA HORA",
        "ECONOMÍA" to "ALERTA ECONÓMICA",
        "DEPORTE" to "LO ÚLTIMO",
        "INTERNACIONAL" to "URGENTE",
        "NACIONAL" to "REPORTE DEL DÍA",
        "SUCESOS" to "ALERTA ROJA",
        "FARÁNDULA" to "EXCLUSIVA",
        "SALUD" to "ATENCIÓN",
        "TECNOLOGÍA" to "LO NUEVO",
        "VIRAL" to "SE HIZO VIRAL",
        "OPINIÓN" to "OPINIÓN",
        "NINGUNA" to "ÚLTIMA HORA",
    )

    fun detectarCat(txt: String): String {
        val t = txt.lowercase()
        for (c in CATS) if (c.kw.any { t.contains(it) }) return c.cat
        return "NACIONAL"
    }

    /** Parte un texto en 2 líneas lo más parejas posible (para el título). */
    private fun dosLineas(s: String): String {
        val x = s.trim()
        if (x.length <= 24) return x
        val words = x.split(" ")
        val mitad = Math.ceil(x.length / 2.0).toInt()
        var l1 = ""
        for (w in words) {
            if (l1.isNotEmpty() && (l1.length + 1 + w.length) > mitad) break
            l1 = if (l1.isEmpty()) w else "$l1 $w"
        }
        val l2 = x.substring(l1.length).trim()
        return if (l2.isNotEmpty()) "$l1\n$l2" else l1
    }

    /** Resalta en **negrita roja** la cifra más llamativa del subtítulo. */
    private fun resaltar(s: String): String {
        val m = Regex("\\d[\\d.,]*\\s?(mil millones|millones|mil|%|dólares|dolares|bs|bolívares|bolivares|años|anos|personas|muertos|heridos|detenidos)?", RegexOption.IGNORE_CASE).find(s)
        if (m != null && m.value.trim().length > 1) {
            return s.replaceFirst(m.value, "**${m.value.trim()}**")
        }
        return s
    }

    fun hashtags(cat: String): List<String> {
        val base = listOf("#Venezuela", "#NoticiasVenezuela", "#Reportalo24", "#VenezuelaHoy", "#Noticias")
        val porCat = mapOf(
            "POLÍTICA" to listOf("#Política", "#Maduro", "#DDHHVenezuela"),
            "ECONOMÍA" to listOf("#Dólar", "#Economía", "#InflaciónVE"),
            "DEPORTE" to listOf("#Deportes", "#Vinotinto", "#Béisbol"),
            "INTERNACIONAL" to listOf("#Internacional", "#Migración", "#DiásporaVenezolana"),
            "NACIONAL" to listOf("#Caracas", "#ServiciosVE", "#Sucesos"),
        )
        return (base + (porCat[cat] ?: emptyList())).take(12)
    }

    private fun armarCopy(titulo: String, resumen: String, cat: String, fuente: String, usuario: String): String {
        val gancho = (if (resumen.isNotBlank()) resumen else titulo).replace(Regex("\\s+"), " ").take(220)
        val credito = if (fuente.isNotBlank()) "\n\n📰 Vía $fuente" else ""
        val tag = usuario.ifBlank { "@reportalo24" }
        return "🚨 $titulo\n\n$gancho$credito\n\n¿Qué opinas? Déjalo en los comentarios 👇\n📲 Sigue a $tag para más\n\n${hashtags(cat).joinToString(" ")}"
    }

    fun redactar(
        titulo: String,
        resumen: String,
        fuente: String,
        categoriaForzada: String?,
        usuario: String,
    ): Resultado {
        val cat = categoriaForzada?.takeIf { it.isNotBlank() && it != "NINGUNA" } ?: detectarCat("$titulo $resumen")
        val franja = FRANJAS[cat] ?: "ÚLTIMA HORA"
        val tituloPlaca = dosLineas(titulo.uppercase())
        var sub = (resumen.ifBlank { titulo }).replace(Regex("\\s+"), " ").trim()
        if (sub.length > 175) sub = sub.take(172).replace(Regex("\\s+\\S*$"), "") + "…"
        sub = resaltar(sub)
        return Resultado(
            franja = franja,
            titulo = tituloPlaca,
            subtitulo = sub,
            categoria = cat,
            copy = armarCopy(titulo, resumen, cat, fuente, usuario),
            hashtags = hashtags(cat),
        )
    }

    /** Arma el copy de Instagram a partir del texto que escribió el usuario, SIN reescribirlo. */
    fun copyManual(titulo: String, subtitulo: String, categoria: String, usuario: String): String {
        val t = titulo.replace("\n", " ").trim()
        val sub = subtitulo.replace("**", "").replace(Regex("\\s+"), " ").trim()
        val cat = if (categoria.isBlank() || categoria == "NINGUNA") detectarCat("$t $sub") else categoria
        val tag = usuario.ifBlank { "@reportalo24" }
        val cuerpo = if (sub.isNotBlank()) "$t\n\n$sub" else t
        return "🚨 $cuerpo\n\n¿Qué opinas? Déjalo en los comentarios 👇\n📲 Sigue a $tag para más\n\n${hashtags(cat).joinToString(" ")}"
    }

    /** Sugerencias de titulares amarillistas (autocompletar) a partir de un tema. */
    fun sugerencias(tema: String): List<String> {
        val t = tema.trim()
        if (t.isBlank()) return emptyList()
        val T = t.uppercase()
        return listOf(
            "CONFIRMADO: lo último sobre $T",
            "LO QUE NO TE CONTARON sobre $T",
            "ALERTA por $T en Venezuela",
            "La verdad sobre $T que no quieren que sepas",
            "$T: así golpea tu bolsillo",
        )
    }
}
