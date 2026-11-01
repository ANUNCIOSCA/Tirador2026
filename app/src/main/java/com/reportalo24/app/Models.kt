package com.reportalo24.app

/** Formato de la placa (mismas medidas que el generador web). */
enum class FormatoPlaca(val etiqueta: String, val w: Int, val h: Int) {
    STORY("Story / Reel (9:16)", 1080, 1920),
    CARRUSEL("Carrusel (1:1)", 1080, 1080),
    FEED("Feed vertical (4:5)", 1080, 1350);

    companion object {
        fun from(name: String?): FormatoPlaca = entries.firstOrNull { it.name == name } ?: STORY
    }
}

enum class EstadoBorrador(val etiqueta: String) {
    BORRADOR("Borrador"),
    LISTO("Listo"),
    PUBLICADO("Publicado");

    companion object {
        fun from(name: String?): EstadoBorrador = entries.firstOrNull { it.name == name } ?: BORRADOR
    }
}

/** Titular traído de un RSS (no se guarda en disco; es la lista de resultados). */
data class Noticia(
    val titulo: String,
    val fuente: String = "",
    val fechaMillis: Long = 0L,
    val link: String = "",
    val resumen: String = "",
)

/** La placa en construcción o guardada. Es el dato central editable y redactable. */
data class Borrador(
    val id: Long = 0L,
    val franja: String = "ÚLTIMA HORA",
    val mostrarFranja: Boolean = true,
    val titulo: String = "",
    val subtitulo: String = "",
    val categoria: String = "NINGUNA",
    val copy: String = "",
    val hashtags: String = "",
    val fuente: String = "",
    val link: String = "",
    val imagenUri: String = "",
    val formato: FormatoPlaca = FormatoPlaca.STORY,
    val estado: EstadoBorrador = EstadoBorrador.BORRADOR,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis(),
)

/** Fuente de noticias por RSS (editable por el usuario). */
data class FuenteRss(
    val id: Long = 0L,
    val nombre: String,
    val url: String,
    val activa: Boolean = true,
)

/** Identidad del medio: nombre, @usuario, logo y franja por defecto. */
data class PerfilMarca(
    val nombreMedio: String = "Repórtalo 24",
    val usuario: String = "@reportalo24",
    val logoUri: String = "",
    val franjaDefault: String = "REPORTE DEL DÍA",
)

object Categorias {
    val lista = listOf(
        "NINGUNA", "NACIONAL", "INTERNACIONAL", "POLÍTICA", "ECONOMÍA",
        "SUCESOS", "DEPORTE", "FARÁNDULA", "SALUD", "TECNOLOGÍA", "VIRAL", "OPINIÓN",
    )
}

/** Config de la redacción con IA (Groq). La clave se guarda solo en el teléfono. */
data class AppConfig(
    val iaActiva: Boolean = false,
    val groqApiKey: String = "",
    val groqModel: String = "llama-3.3-70b-versatile",
)
