package com.reportalo24.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Error de la API de Groq con un mensaje corto, pensado para mostrarse en pantalla. */
class GroqError(val codigo: Int, mensaje: String, val cuerpo: String = "") : RuntimeException(mensaje)

/**
 * Redacción amarillista con IA vía la API de Groq (compatible con OpenAI).
 * La clave se guarda solo en el teléfono. Si falla, la pantalla cae a plantillas.
 * Regla sagrada: no inventa datos; solo pone con filo lo que trae el material.
 *
 * v1.6.1: modelos gpt-oss (los llama anteriores fueron apagados por Groq), reintento
 * automático cuando Groq responde 429 (límite) y modo simple si el modo JSON falla.
 */
object GroqRedactor {

    private const val ENDPOINT = "https://api.groq.com/openai/v1/chat/completions"

    // Tope de salida: el plan gratis de Groq limita los tokens por minuto, y el tope
    // pedido cuenta para ese límite. 1500 alcanza de sobra para el JSON de una pieza.
    private const val MAX_SALIDA = 1500

    private val SYSTEM = """
        Eres el editor de mesa de Repórtalo 24 (R24), un medio venezolano de DENUNCIA.
        No eres una agencia de cables: cada pieza sale con filo.

        LAS 3 REGLAS QUE NUNCA SE ROMPEN:
        1) TONO AMARILLISTA Y CRÍTICO, SIEMPRE. Indignación, dramatismo de la
           consecuencia real y señalamiento del responsable institucional. PROHIBIDO
           entregar una versión neutral o tibia: un titular sin carga emocional es
           contenido muerto.
        2) HUMANIZADO AL 100%. Nada con voz de IA. Escribe como una persona con rabia
           y calle, no como un boletín. Evita clichés, frases de relleno y palabras de IA.
        3) EL DATO ES SAGRADO. El adjetivo es libre, pero NO inventes hechos, cifras ni
           nombres: usa SOLO lo que está en el material. Lo no comprobable va atribuido
           ("se denuncia que…", "sin confirmación oficial") o se deja fuera. Nunca lo afirmes como hecho.

        FÓRMULA R24 PARA EL TITULAR (5 capas, de mayor a menor peso):
        a) ADVERSARIO: señala a alguien o algo responsable (gobierno, institución, empresa).
        b) PALABRA MORAL (traición, abandono, olvido, descaro).
        c) PALABRA NEGATIVA (colapso, tragedia, ruina, peligro).
        d) VACÍO ABIERTO: que el lector NECESITE saber el resto.
        e) ANCLA CONCRETA en el subtítulo (cifra o fecha).

        "EL DATO QUE DUELE": busca el contraste entre lo que algo fue/debería ser y la
        desgracia actual (promesa incumplida con fecha, tiempo acumulado "X años esperando",
        pasado glorioso, consecuencia concreta). Eso va en el subtítulo.

        EMOCIÓN SEGÚN EL TEMA: Política → ira/indignación · Economía → ansiedad/urgencia ·
        Sucesos → miedo/alerta (sin morbo gratuito) · Salud → empatía · Diáspora → esperanza+nostalgia.

        REGLAS DE LONGITUD (muy importante):
        - El TÍTULO debe ser CORTO, con gancho y preciso: 3 a 6 palabras. Si te sale largo, córtalo.
          Un título largo arruina la placa. Juzga y engancha, no describas ni cuentes todo.
        - La FRANJA y el TÍTULO dicen cosas DISTINTAS. La franja es solo una etiqueta de urgencia.
        - La FRANJA NO puede ser igual al nombre de la categoría (no pongas "INTERNACIONAL" de franja).

        Responde ÚNICAMENTE con un objeto JSON válido, sin texto adicional, con estas claves:
        - "franja": etiqueta de URGENCIA corta, 1 a 3 palabras, en MAYÚSCULAS, tipo "ÚLTIMA HORA",
          "ALERTA", "URGENTE", "EXCLUSIVA", "ATENCIÓN". Distinta del título y de la categoría.
        - "titulo": SENTENCIA corta y punzante de 3 a 6 palabras que JUZGA y señala al responsable,
          en MAYÚSCULAS, máximo 2 líneas cortas; separa las líneas con \n.
        - "subtitulo": el dato que duele, 1 o 2 frases, con cifra o fecha atribuida; NO repite el
          título; envuelve la palabra o cifra clave entre **dobles asteriscos**.
        - "categoria": una sola de NACIONAL, INTERNACIONAL, POLÍTICA, ECONOMÍA, SUCESOS, DEPORTE,
          FARÁNDULA, SALUD, TECNOLOGÍA, VIRAL, OPINIÓN.
        - "copy": 2 a 4 líneas para Instagram, con indignación, 1 o 2 emojis y un cierre que
          invite a comentar y compartir.
        - "hashtags": arreglo de 8 a 12 hashtags en español de Venezuela.
        Todo en español de Venezuela. La ironía va por yuxtaposición (contraste), nunca por insulto.
    """.trimIndent()

    /** Se suma al SYSTEM cuando Moreno entrega el material él mismo (texto o captura). */
    private val MODO_LIBRE = """
        MODO REDACTAR (orden directa de Moreno):
        - El material puede venir de una captura de pantalla leída por OCR. Ignora el ruido de la
          interfaz (nombres de usuario, "me gusta", botones, horas, "Seguir", "Responder") y
          quédate con la noticia.
        - Redacta TAL COMO Moreno lo pide. No verificas, no buscas, no cuestionas ni corriges sus
          datos: el material que entrega es tu verdad de trabajo.
        - Si Moreno da INSTRUCCIONES, mandan sobre el estilo por defecto (tono, largo, formato).
          Lo único que no cambia: no inventar hechos y no dejar marcas tipo [PENDIENTE] dentro del texto.
        - Lo que no esté en el material no se inventa: se omite.
    """.trimIndent()

    suspend fun redactar(
        config: AppConfig,
        titulo: String,
        resumen: String,
        fuente: String,
        usuario: String,
        evitarTitulo: String = "",
        instrucciones: String = "",
        modoLibre: Boolean = false,
    ): Redactor.Resultado = withContext(Dispatchers.IO) {
        val user = StringBuilder()
        if (modoLibre) {
            user.append("MATERIAL ENTREGADO POR MORENO:\n").append(resumen)
        } else {
            user.append("Titular: $titulo\nResumen: $resumen\nFuente: ${fuente.ifBlank { "no indicada" }}")
        }
        user.append("\nUsuario del medio: $usuario")
        if (instrucciones.isNotBlank()) {
            user.append("\nINSTRUCCIONES DE MORENO: ").append(instrucciones.trim())
        }
        if (evitarTitulo.isNotBlank()) {
            user.append("\nNo uses este título (dame uno DISTINTO y más corto): \"$evitarTitulo\"")
        }
        val system = if (modoLibre) SYSTEM + "\n\n" + MODO_LIBRE else SYSTEM
        val temperatura = if (evitarTitulo.isNotBlank()) 1.0 else 0.85
        val content = llamar(config, system, user.toString(), temperatura)
        parseResultado(extraerJson(content), titulo, resumen, fuente)
    }

    /**
     * Primero intenta con los parámetros optimizados (modo JSON, razonamiento bajo, tope de salida).
     * Si Groq rechaza algún parámetro (400), reintenta en modo simple y extrae el JSON del texto.
     */
    private fun llamar(config: AppConfig, system: String, user: String, temperatura: Double): String {
        var ultimo: GroqError? = null
        for (optimizado in listOf(true, false)) {
            try {
                return enviar(config, system, user, temperatura, optimizado)
            } catch (e: GroqError) {
                ultimo = e
                val modeloMalo = e.cuerpo.contains("decommission", true) || e.cuerpo.contains("model_not_found", true)
                val parametroRechazado = e.codigo == 400 && !modeloMalo
                if (!(optimizado && parametroRechazado)) break
            }
        }
        throw ultimo ?: GroqError(0, "sin respuesta de Groq")
    }

    private fun enviar(config: AppConfig, system: String, user: String, temperatura: Double, optimizado: Boolean): String {
        val body = JSONObject().apply {
            put("model", config.groqModel)
            put("temperature", temperatura)
            if (optimizado) {
                put("response_format", JSONObject().put("type", "json_object"))
                put("reasoning_effort", "low")
                put("max_completion_tokens", MAX_SALIDA)
            } else {
                put("max_tokens", MAX_SALIDA)
            }
            put("messages", JSONArray().apply {
                put(JSONObject().put("role", "system").put("content", system))
                put(JSONObject().put("role", "user").put("content", user))
            })
        }.toString().toByteArray(Charsets.UTF_8)

        var reintentos429 = 0
        while (true) {
            val conn = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 20000
                readTimeout = 45000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", "Bearer ${config.groqApiKey}")
            }
            try {
                conn.outputStream.use { it.write(body) }
                val code = conn.responseCode
                if (code in 200..299) {
                    val resp = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    val content = JSONObject(resp)
                        .getJSONArray("choices").getJSONObject(0)
                        .getJSONObject("message").optString("content", "")
                    if (content.isBlank() || content == "null") throw GroqError(code, "la IA no devolvió texto")
                    return content
                }
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                if (code == 429 && reintentos429 < 1) {
                    // Límite de Groq: espera lo que pide la cabecera retry-after (máx. 25 s) y reintenta una vez.
                    val espera = (conn.getHeaderField("retry-after")?.toDoubleOrNull() ?: 5.0).coerceIn(1.0, 25.0)
                    reintentos429++
                    Thread.sleep((espera * 1000).toLong())
                    continue
                }
                throw GroqError(code, mensajeAmigable(code, err), err)
            } finally {
                conn.disconnect()
            }
        }
    }

    private fun mensajeAmigable(code: Int, cuerpo: String): String = when {
        code == 401 -> "clave de Groq inválida"
        code == 404 || cuerpo.contains("decommission", true) || cuerpo.contains("model_not_found", true) ->
            "modelo apagado; cámbialo en Marca"
        code == 429 -> "límite de Groq; espera unos segundos"
        code == 413 -> "texto muy largo para Groq"
        else -> "Groq $code"
    }

    /** Saca el objeto JSON aunque el modelo lo envuelva en texto o en ```bloques```. */
    private fun extraerJson(s: String): String {
        val a = s.indexOf('{')
        val b = s.lastIndexOf('}')
        return if (a >= 0 && b > a) s.substring(a, b + 1) else s
    }

    private fun parseResultado(content: String, titulo: String, resumen: String, fuente: String): Redactor.Resultado {
        val j = JSONObject(content)
        val catRaw = j.optString("categoria", "NACIONAL").uppercase().trim()
        val categoria = Categorias.lista.firstOrNull { it == catRaw }?.takeIf { it != "NINGUNA" } ?: "NACIONAL"
        val hashtags = if (j.has("hashtags")) {
            val arr = j.optJSONArray("hashtags")
            if (arr != null) (0 until arr.length()).map { arr.optString(it) }.filter { it.isNotBlank() } else Redactor.hashtags(categoria)
        } else Redactor.hashtags(categoria)
        return Redactor.Resultado(
            franja = j.optString("franja", "ÚLTIMA HORA").uppercase(),
            titulo = j.optString("titulo", titulo.uppercase()),
            subtitulo = j.optString("subtitulo", resumen.ifBlank { titulo }),
            categoria = categoria,
            copy = j.optString("copy", ""),
            hashtags = hashtags.take(12),
        )
    }
}
