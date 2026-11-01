package com.reportalo24.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Genera la placa de noticia como Bitmap (porta la lógica del generador web a
 * Canvas de Android). Produce PNG con foto o capa transparente para video.
 * Con la foto centrada y zoom 1 el resultado es equivalente al generador web.
 */
object PlacaRenderer {

    data class Opciones(
        val formato: FormatoPlaca,
        val foto: Bitmap? = null,
        val logo: Bitmap? = null,
        val titulo: String,
        val subtitulo: String,
        val franja: String = "",
        val mostrarFranja: Boolean = true,
        val categoria: String = "NINGUNA",
        val usuario: String = "@reportalo24",
        val posicion: Int = 50,
        val subSize: Int = 38,
        val imgZoom: Float = 1f,
        val imgOffX: Float = 0f,
        val imgOffY: Float = 0f,
        val conFoto: Boolean = true,
        val overrideW: Int? = null,
        val overrideH: Int? = null,
    )

    private data class Token(val text: String, val hot: Boolean, val isBreak: Boolean = false)

    private fun typefaceBold() = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    private fun typefaceNormal() = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)

    fun render(o: Opciones): Bitmap {
        val W = o.overrideW ?: o.formato.w
        val H = o.overrideH ?: o.formato.h
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)

        // 1. Foto a pantalla completa (solo con foto)
        if (o.conFoto) {
            if (o.foto != null) {
                drawCover(c, o.foto, W, H, o.imgZoom, o.imgOffX, o.imgOffY)
            } else {
                c.drawColor(Color.rgb(16, 32, 31))
            }
        }

        val padX = 110f
        val maxTextW = W - padX * 2
        val footerY = H - 70f
        val lift = (o.posicion / 100f) * (H * 0.33f)

        // --- Subtítulo (tokens con palabra clave en rojo) ---
        val subTokens = if (o.subtitulo.isNotBlank()) parseTokens(o.subtitulo) else emptyList()
        val subStart = o.subSize
        val subMin = max(18, subStart - 10)
        val subFit = if (subTokens.isNotEmpty())
            fitTokenLines(subTokens, subStart, subMin, 4, 16, maxTextW)
        else FitTokens(emptyList(), subStart.toFloat())
        val subLineH = (subFit.size * 0.90f).roundToInt()
        val subBottom = footerY - 45f - 40f - lift
        var subTop = subBottom - (subFit.lines.size - 1).coerceAtLeast(0) * subLineH

        // --- Título (máx 2 líneas, autoajustado) ---
        val titlePadX = 70f
        val titleMaxW = W - titlePadX * 2
        val tituloUp = o.titulo.trim().uppercase()
        val (l1Text, l2Text) = splitTitle(tituloUp, titleMaxW)
        val startTitle = if (W >= 1080 && H >= 1600) 92 else 78
        val l1 = fitSingle(l1Text, startTitle, 24, titleMaxW)
        val l2 = fitSingle(l2Text, startTitle, 24, titleMaxW)
        val hasL2 = l2Text.isNotBlank()

        var titleBottom = subTop - subLineH - 24f
        var titleTop = if (hasL2) titleBottom - ((l1.size + l2.size) / 2f * 0.90f) else titleBottom

        // Tope superior de seguridad: el texto nunca sube más del 30%
        val minTop = H * 0.30f
        if (titleTop < minTop) {
            val push = minTop - titleTop
            titleTop += push; titleBottom += push
            subTop += push
        }

        // --- Franja roja (arriba del título) ---
        val franjaText = o.franja.trim().uppercase()
        val franjaOn = o.mostrarFranja && franjaText.isNotEmpty()
        var tagH = 0f
        var tagSize = 40f
        var tagFinal = franjaText
        if (franjaOn) {
            val floor = 22f
            var fit = floor
            var s = 40f
            while (s >= floor) {
                val p = textPaint(s, true)
                if (p.measureText(franjaText) + (s * 0.94f) * 2 <= maxTextW) { fit = s; break }
                s -= 2f
            }
            if (fit == floor) {
                val p = textPaint(floor, true)
                var t = franjaText
                while (t.isNotEmpty() && p.measureText("$t…") + (floor * 0.94f) * 2 > maxTextW) t = t.dropLast(1)
                tagFinal = t.trimEnd() + "…"
            }
            tagSize = fit
            tagH = (fit * 1.83f)
        }
        val tagBottom = titleTop - l1.size - 30f
        val tagTop = tagBottom - tagH

        // 2. Degradado dinámico (teal de marca)
        val overlayTop = if (franjaOn) tagTop else (titleTop - l1.size)
        var gradStart = min(H * 0.62f, overlayTop - 60f)
        if (gradStart < 0f) gradStart = 0f
        val stops = floatArrayOf(0f, min(0.22f, 70f / max(1f, H - gradStart)), 0.45f, 0.82f, 1f)
        val colors = intArrayOf(
            Color.argb(0, 22, 162, 179),
            Color.argb((0.95f * 0.6f * 255).toInt(), 22, 150, 165),
            Color.argb((0.95f * 255).toInt(), 22, 150, 165),
            Color.argb(255, 15, 113, 125),
            Color.argb(255, 15, 113, 125),
        )
        val gradPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        gradPaint.shader = LinearGradient(0f, gradStart, 0f, H.toFloat(), colors, stops, Shader.TileMode.CLAMP)
        c.drawRect(0f, gradStart, W.toFloat(), H.toFloat(), gradPaint)

        // 3. Footer
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb((0.45f * 255).toInt(), 255, 255, 255); strokeWidth = 3f
        }
        c.drawLine(padX, footerY - 45f, W - padX, footerY - 45f, linePaint)
        val fecha = SimpleDateFormat("dd MMM, HH:mm", Locale("es", "VE")).format(Date())
        val footL = textPaint(30f, false).apply { textAlign = Paint.Align.LEFT; color = Color.argb(242, 255, 255, 255); setShadowLayer(6f, 0f, 0f, Color.argb(153, 0, 0, 0)) }
        c.drawText(fecha, padX, footerY, footL)
        val footR = textPaint(30f, true).apply { textAlign = Paint.Align.RIGHT; color = Color.argb(242, 255, 255, 255); setShadowLayer(6f, 0f, 0f, Color.argb(153, 0, 0, 0)) }
        c.drawText(o.usuario.uppercase(), W - padX, footerY, footR)

        // 4. Subtítulo (blanco, palabra clave en rojo), centrado a mano
        if (subFit.lines.isNotEmpty()) {
            val base = textPaint(subFit.size, false)
            base.setShadowLayer(14f, 0f, 3f, Color.argb(230, 0, 0, 0))
            for (i in subFit.lines.indices) {
                val line = subFit.lines[i]
                val full = line.joinToString(" ") { it.text }
                val lineW = base.measureText(full)
                var lx = W / 2f - lineW / 2f
                val ly = subTop + i * subLineH
                for (j in line.indices) {
                    val tok = line[j]
                    val piece = tok.text + if (j < line.size - 1) " " else ""
                    base.color = if (tok.hot) Color.rgb(255, 59, 48) else Color.argb(250, 255, 255, 255)
                    c.drawText(piece, lx, ly, base)
                    lx += base.measureText(piece)
                }
            }
        }

        // 5. Título (línea 1 y 2), con contorno + sombra
        drawTitleLine(c, l1.text, l1.size, W / 2f, titleTop)
        if (hasL2) drawTitleLine(c, l2.text, l2.size, W / 2f, titleBottom)

        // 6. Franja roja
        if (franjaOn) {
            drawPill(c, tagFinal, tagSize, W / 2f, tagTop, tagH, maxTextW, centered = true)
        }

        // 7. Categoría (pastilla arriba a la derecha)
        if (o.categoria.isNotBlank() && o.categoria != "NINGUNA") {
            drawCategoria(c, o.categoria, W)
        }

        // 8. Logo opcional (arriba centrado)
        if (o.logo != null) {
            val lw = (W * 0.16f)
            val ratio = o.logo.height.toFloat() / o.logo.width.toFloat()
            val lh = lw * ratio
            val lx = (W - lw) / 2f
            val ly = 60f
            c.drawBitmap(o.logo, null, RectF(lx, ly, lx + lw, ly + lh), Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        }

        return bmp
    }

    // ----------------------------------------------------------------- helpers

    private fun textPaint(size: Float, bold: Boolean) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        typeface = if (bold) typefaceBold() else typefaceNormal()
        color = Color.WHITE
    }

    private fun drawCover(c: Canvas, img: Bitmap, W: Int, H: Int, zoom: Float, offX: Float, offY: Float) {
        val iw = img.width.toFloat(); val ih = img.height.toFloat()
        val scale = max(W / iw, H / ih) * zoom
        val sw = W / scale; val sh = H / scale
        val freeX = iw - sw; val freeY = ih - sh
        var sx = freeX / 2f + offX * freeX / 2f
        var sy = freeY / 2f + offY * freeY / 2f
        sx = sx.coerceIn(0f, max(0f, freeX)); sy = sy.coerceIn(0f, max(0f, freeY))
        val src = Rect(sx.toInt(), sy.toInt(), (sx + sw).toInt(), (sy + sh).toInt())
        val dst = Rect(0, 0, W, H)
        c.drawBitmap(img, src, dst, Paint(Paint.FILTER_BITMAP_FLAG))
    }

    private fun parseTokens(text: String): List<Token> {
        val tokens = ArrayList<Token>()
        fun pushWords(str: String, hot: Boolean) {
            val parts = str.split("\n")
            for (p in parts.indices) {
                if (p > 0) tokens.add(Token("", false, isBreak = true))
                parts[p].split(Regex("\\s+")).filter { it.isNotEmpty() }.forEach { tokens.add(Token(it, hot)) }
            }
        }
        val regex = Regex("\\*\\*(.+?)\\*\\*")
        var last = 0
        for (m in regex.findAll(text)) {
            if (m.range.first > last) pushWords(text.substring(last, m.range.first), false)
            pushWords(m.groupValues[1], true)
            last = m.range.last + 1
        }
        if (last < text.length) pushWords(text.substring(last), false)
        return tokens
    }

    private fun wrapTokens(tokens: List<Token>, paint: Paint, maxW: Float): List<List<Token>> {
        val lines = ArrayList<MutableList<Token>>()
        var current = ArrayList<Token>()
        var currentText = ""
        for (tk in tokens) {
            if (tk.isBreak) { lines.add(current); current = ArrayList(); currentText = ""; continue }
            val test = if (currentText.isEmpty()) tk.text else "$currentText ${tk.text}"
            if (paint.measureText(test) > maxW && currentText.isNotEmpty()) {
                lines.add(current); current = arrayListOf(tk); currentText = tk.text
            } else {
                current.add(tk); currentText = test
            }
        }
        if (current.isNotEmpty() || lines.isEmpty()) lines.add(current)
        return lines
    }

    private data class FitTokens(val lines: List<List<Token>>, val size: Float)

    private fun fitTokenLines(tokens: List<Token>, start: Int, min: Int, maxLines: Int, absMin: Int, maxW: Float): FitTokens {
        fun lineText(l: List<Token>) = l.joinToString(" ") { it.text }
        fun tryFit(size: Int): FitTokens? {
            val p = textPaint(size.toFloat(), false)
            val lines = wrapTokens(tokens, p, maxW)
            if (lines.size <= maxLines && lines.all { p.measureText(lineText(it)) <= maxW }) return FitTokens(lines, size.toFloat())
            return null
        }
        var size = start
        while (size >= min) { tryFit(size)?.let { return it }; size -= 4 }
        size = min - 2
        while (size >= absMin) { tryFit(size)?.let { return it }; size -= 2 }
        val p = textPaint(absMin.toFloat(), false)
        var lines = wrapTokens(tokens, p, maxW).toMutableList()
        if (lines.size > maxLines) lines = lines.take(maxLines).toMutableList()
        return FitTokens(lines, absMin.toFloat())
    }

    private data class FitLine(val text: String, val size: Float)

    private fun fitSingle(text: String, chosen: Int, floor: Int, maxW: Float): FitLine {
        if (text.isBlank()) return FitLine("", chosen.toFloat())
        var s = chosen
        while (s >= floor) {
            val p = textPaint(s.toFloat(), true)
            if (p.measureText(text) <= maxW) return FitLine(text, s.toFloat())
            s -= 2
        }
        val p = textPaint(floor.toFloat(), true)
        var t = text
        while (t.isNotEmpty() && p.measureText("$t…") > maxW) t = t.dropLast(1)
        return FitLine(t.trimEnd() + "…", floor.toFloat())
    }

    private fun splitTitle(title: String, maxW: Float): Pair<String, String> {
        if (title.isEmpty()) return "" to ""
        if (title.contains("\n")) {
            val parts = title.split("\n")
            return parts[0].trim() to (parts.getOrNull(1) ?: "").trim()
        }
        // Corte automático a mitad por palabras
        val words = title.split(" ")
        if (words.size == 1) return title to ""
        val mitad = ceil(title.length / 2.0).toInt()
        var l1 = ""
        for (w in words) {
            if (l1.isNotEmpty() && (l1.length + 1 + w.length) > mitad) break
            l1 = if (l1.isEmpty()) w else "$l1 $w"
        }
        val l2 = title.substring(l1.length).trim()
        return l1 to l2
    }

    private fun drawTitleLine(c: Canvas, text: String, size: Float, cx: Float, baseline: Float) {
        if (text.isBlank()) return
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size; typeface = typefaceBold(); textAlign = Paint.Align.CENTER
            style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND
            strokeWidth = max(2f, size * 0.06f); color = Color.argb((0.55f * 255).toInt(), 0, 0, 0)
        }
        c.drawText(text, cx, baseline, stroke)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size; typeface = typefaceBold(); textAlign = Paint.Align.CENTER
            color = Color.WHITE; setShadowLayer(24f, 0f, 4f, Color.argb(242, 0, 0, 0))
        }
        c.drawText(text, cx, baseline, fill)
    }

    private fun drawPill(c: Canvas, text: String, size: Float, cx: Float, top: Float, h: Float, maxW: Float, centered: Boolean) {
        val p = textPaint(size, true)
        val tw = p.measureText(text)
        val padX = size * 0.94f
        val bw = min(tw + padX * 2, maxW)
        val bx = cx - bw / 2f
        val rr = 14f
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(214, 31, 44); setShadowLayer(10f, 0f, 0f, Color.argb(115, 0, 0, 0)) }
        c.drawRoundRect(RectF(bx, top, bx + bw, top + h), rr, rr, bg)
        val tp = textPaint(size, true).apply { color = Color.WHITE; textAlign = Paint.Align.CENTER }
        val fm = tp.fontMetrics
        val ty = top + h / 2f - (fm.ascent + fm.descent) / 2f
        c.drawText(text, cx, ty, tp)
    }

    private fun drawCategoria(c: Canvas, cat: String, W: Int) {
        val maxW = W * 0.42f
        val floor = 18; val baseSize = 30
        var fit = floor
        var s = baseSize
        while (s >= floor) {
            val p = textPaint(s.toFloat(), true)
            if (p.measureText(cat) + (s * 0.85f) * 2 <= maxW) { fit = s; break }
            s -= 2
        }
        val p = textPaint(fit.toFloat(), true)
        val padX = fit * 0.85f
        val h = fit * 1.9f
        val tw = p.measureText(cat)
        val bw = min(tw + padX * 2, maxW)
        val top = 160f; val right = 40f
        val bx = W - right - bw
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(214, 31, 44); setShadowLayer(10f, 0f, 0f, Color.argb(115, 0, 0, 0)) }
        c.drawRoundRect(RectF(bx, top, bx + bw, top + h), 12f, 12f, bg)
        val tp = textPaint(fit.toFloat(), true).apply { color = Color.WHITE; textAlign = Paint.Align.CENTER }
        val fm = tp.fontMetrics
        c.drawText(cat, bx + bw / 2f, top + h / 2f - (fm.ascent + fm.descent) / 2f, tp)
    }
}
