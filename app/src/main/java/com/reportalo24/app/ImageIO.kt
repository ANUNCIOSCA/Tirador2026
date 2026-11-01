package com.reportalo24.app

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

/** Carga, guardado y compartido de imágenes. Todo local en el teléfono. */
object ImageIO {

    /** Copia la foto elegida a la carpeta interna de la app y devuelve su ruta. */
    fun persistImage(context: Context, uri: Uri): String? = runCatching {
        val dir = File(context.filesDir, "fotos").apply { mkdirs() }
        val dest = File(dir, "foto_${System.currentTimeMillis()}.jpg")
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(dest).use { output -> input.copyTo(output) }
        }
        dest.absolutePath
    }.getOrNull()

    /** Carga un bitmap desde una ruta interna o un content uri, con submuestreo. */
    fun loadBitmap(context: Context, path: String?, maxDim: Int = 2200): Bitmap? {
        if (path.isNullOrBlank()) return null
        return runCatching {
            val opened: () -> java.io.InputStream? = {
                if (path.startsWith("content:")) context.contentResolver.openInputStream(Uri.parse(path))
                else File(path).takeIf { it.exists() }?.inputStream()
            }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            opened()?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            val big = maxOf(bounds.outWidth, bounds.outHeight)
            while (big / sample > maxDim) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            opened()?.use { BitmapFactory.decodeStream(it, null, opts) }
        }.getOrNull()
    }

    private fun cachePng(context: Context, bmp: Bitmap, name: String): File {
        val dir = File(context.cacheDir, "compartir").apply { mkdirs() }
        val file = File(dir, "$name.png")
        FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return file
    }

    /** Guarda el PNG en la galería (Pictures/Repórtalo 24). */
    fun saveToGallery(context: Context, bmp: Bitmap, name: String): Boolean = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "$name.png")
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Reportalo24")
            }
            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: return false
            context.contentResolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            true
        } else {
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Reportalo24").apply { mkdirs() }
            FileOutputStream(File(dir, "$name.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            true
        }
    }.getOrDefault(false)

    fun shareImage(context: Context, bmp: Bitmap, name: String, texto: String? = null) {
        val file = cachePng(context, bmp, name)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            if (!texto.isNullOrBlank()) putExtra(Intent.EXTRA_TEXT, texto)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Compartir placa"))
    }

    fun shareFile(context: Context, file: File, mime: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Compartir"))
    }
}
