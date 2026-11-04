package com.reportalo24.app

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Lee el texto de una captura de pantalla EN EL TELÉFONO (ML Kit, modelo incluido en la app).
 * No usa internet ni gasta nada del cupo de Groq.
 */
object Ocr {
    suspend fun leer(bitmap: Bitmap): String = suspendCancellableCoroutine { cont ->
        val reconocedor = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        reconocedor.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { resultado ->
                reconocedor.close()
                cont.resume(resultado.text)
            }
            .addOnFailureListener { e ->
                reconocedor.close()
                cont.resumeWithException(e)
            }
        cont.invokeOnCancellation { runCatching { reconocedor.close() } }
    }
}
