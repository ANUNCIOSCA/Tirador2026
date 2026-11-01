package com.reportalo24.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Redacción amarillista con IA vía la API de Groq (compatible con OpenAI).
 * La clave se guarda solo en el teléfono. Si falla, la pantalla cae a plantillas.
 * Regla sagrada: no inventa datos; solo pone con filo lo que trae el titular.
 */
object GroqRedactor {

    private const val ENDPOINT = "https://api.groq.com/openai/v1/chat/completions"

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

        Responde ÚNICAMENTE con un objeto JSON válido, sin texto adicional, con estas claves:
        - "franja": el TEMA en MAYÚSCULAS, idealmente 18 a 20 caracteres (ej: "APAGÓN EN OCCIDENTE").
        - "titulo": la SENTENCIA emocional de 4 a 8 palabras que JUZGA y señala al responsable
          (no describe), en MAYÚSCULAS, máximo 2 líneas; separa las líneas con \n.
        - "subtitulo": el dato que duele, 1 o 2 frases, con cifra o fecha atribuida; NO repite el
          título; envuelve la palabra o cifra clave entre **dobles asteriscos**.
        - "categoria": una sola de NACIONAL, ECONOMÍA, POLÍTICA, DEPORTE, INTERNACIONAL.
        - "copy": 2 a 4 líneas para Instagram, con indignación, 1 o 2 emojis y un cierre que
          invite a comentar y compartir.
        - "hashtags": arreglo de 8 a 12 hashtags en español de Venezuela.
        Todo en español de Venezuela. La ironía va por yuxtaposición (contraste), nunca por insulto.
    """.trimIndent()

    suspend fun redactar(
        config: AppConfig,
        titulo: String,
        resumen: String,
        fuente: String,
        usuario: String,
    ): Redactor.Resultado = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("model", config.groqModel)
            put("temperature", 0.85)
            put("response_format", JSONObject().put("type", "json_object"))
            put("messages", JSONArray().apply {
                put(JSONObject().put("role", "system").put("content", SYSTEM))
                put(JSONObject().put("role", "user").put(
                    "content",
                    "Titular: $titulo\nResumen: $resumen\nFuente: ${fuente.ifBlank { "no indicada" }}\nUsuario del medio: $usuario",
                ))
            })
        }

        val conn = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20000
            readTimeout = 30000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer ${config.groqApiKey}")
        }

        try {
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            if (conn.responseCode !in 200..299) {
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                throw RuntimeException("Groq ${conn.responseCode}: ${err.take(200)}")
            }
            val resp = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val content = JSONObject(resp)
                .getJSONArray("choices").getJSONObject(0)
                .getJSONObject("message").getString("content")
            parseResultado(content, titulo, resumen, fuente)
        } finally {
            conn.disconnect()
        }
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
